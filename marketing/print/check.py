#!/usr/bin/env python3
"""Gate the rendered PDFs before anyone sends them to a printer.

Checks what a renderer can silently get wrong: page geometry, page counts, font embedding,
image resolution, the trim/bleed declaration on the shop exports, and — by decoding the
rasterised artwork rather than the source SVG — that every QR code still scans.

    python3 check.py           # all of out/
    python3 check.py --dpi 200 # rasterise the QR check at a chosen resolution
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "out"
URL = "https://spross.net"
PT = 72 / 25.4

TRIM = {"a4": (210.0, 297.0), "a5": (148.5, 210.0), "a6": (105.0, 148.5), "card": (85.0, 55.0)}
SHOP_RING = 2 * (3.0 + 5.0)      # bleed + marks on every side
MIN_PPI = 200
TOL = 0.3                        # mm; Chrome quantises the page box to 1/300 inch


def run(cmd: list[str]) -> str:
    return subprocess.run(cmd, capture_output=True, text=True).stdout


def page_boxes(pdf: Path) -> list[dict]:
    """MediaBox/TrimBox per page, in mm."""
    out = run(["pdfinfo", "-box", "-f", "1", "-l", "99", str(pdf)])
    boxes: list[dict] = []
    for m in re.finditer(r"Page +(\d+) size: +([\d.]+) x ([\d.]+)", out):
        boxes.append({"w": float(m.group(2)) / PT, "h": float(m.group(3)) / PT})
    if not boxes:  # single-page files report without the "Page N" prefix
        m = re.search(r"Page size: +([\d.]+) x ([\d.]+)", out)
        if m:
            boxes = [{"w": float(m.group(1)) / PT, "h": float(m.group(2)) / PT}]
    has_trim = "TrimBox" in out and not re.search(
        r"TrimBox: +0\.00 +0\.00 +([\d.]+) +([\d.]+)\n.*MediaBox", out)
    for b in boxes:
        b["trim"] = has_trim
    return boxes


def expected(name: str) -> tuple[float, float, int, bool]:
    """(width, height, pages, is_shop) the file's name promises."""
    shop = name.endswith("-shop")
    pages = 2 if "-duplex" in name else 1
    if shop:
        size = next(k for k in ("a4", "a5", "a6", "card") if f"-{k}-" in name or f"-{k}4up" in name)
        w, h = TRIM[size]
        return w + SHOP_RING, h + SHOP_RING, pages, True
    return 210.0, 297.0, pages, False


def grid_of(name: str) -> tuple[int, int]:
    """The sheet's piece grid (cols, rows). Each piece is scanned on its own, because a
    phone is held over one card, never over a sheet of ten."""
    if name.endswith("-shop"):
        return 1, 1
    if "cards" in name:
        return 2, 5
    if "4up" in name:
        return 2, 2
    if "2up" in name:
        return 1, 2
    return 1, 1


def check_pdf(pdf: Path, dpi: int) -> list[str]:
    name = pdf.stem
    bad: list[str] = []
    w, h, pages, shop = expected(name)

    boxes = page_boxes(pdf)
    if len(boxes) != pages:
        bad.append(f"page count {len(boxes)}, expected {pages}")
    for i, b in enumerate(boxes, 1):
        if abs(b["w"] - w) > TOL or abs(b["h"] - h) > TOL:
            bad.append(f"page {i} is {b['w']:.2f}×{b['h']:.2f}mm, expected {w}×{h}")
        if shop and not b["trim"]:
            bad.append(f"page {i} declares no TrimBox — a shop cannot find the trim")

    fonts = run(["pdffonts", str(pdf)]).splitlines()[2:]
    for line in (f for f in fonts if f.strip()):
        if "Type 3" in line:
            bad.append(f"Type 3 font, which preflight rejects: {line.split()[0]}")
        elif " no " in f" {line[40:60]} ":
            bad.append(f"font not embedded: {line.split()[0]}")

    for m in re.finditer(r"\d+\s+\d+\s+\w+\s+\d+\s+\d+\s+\S+\s+\S+\s+\S+\s+\S+\s+(\d+)\s+(\d+)",
                         run(["pdfimages", "-list", str(pdf)])):
        ppi = min(int(m.group(1)), int(m.group(2)))
        if ppi < MIN_PPI:
            bad.append(f"image placed at {ppi}ppi, under {MIN_PPI}")

    # A card front carries no code — its back does — so the rule is per file, not per page:
    # some face must be scannable, no code may point elsewhere, and where a sheet does carry
    # codes, every piece cut from it must decode on its own.
    cols, rows = grid_of(name)
    total = 0
    with tempfile.TemporaryDirectory() as tmp:
        stem = Path(tmp) / "p"
        subprocess.run(["magick", "-density", str(dpi), str(pdf), "-background", "white",
                        "-alpha", "remove", "-alpha", "off", f"{stem}-%d.png"], check=True)
        for png in sorted(Path(tmp).glob("p-*.png")):
            cells = Path(tmp) / f"cell-{png.stem}"
            subprocess.run(["magick", str(png), "-crop", f"{cols}x{rows}@", "+repage",
                            f"{cells}-%d.png"], check=True)
            found = 0
            for cell in sorted(Path(tmp).glob(f"cell-{png.stem}-*.png")):
                decoded = run(["swift", str(ROOT / "tools" / "qrcheck.swift"), str(cell)])
                codes = [ln.split("\t")[1] for ln in decoded.splitlines() if "\t" in ln]
                for c in codes:
                    if c not in (URL, "<none>"):
                        bad.append(f"page {png.stem[2:]} has a code pointing at {c}")
                found += sum(1 for c in codes if c == URL)
            if found and found < cols * rows:
                bad.append(f"page {png.stem[2:]}: {found} of {cols * rows} pieces scanned "
                           f"at {dpi}dpi — the rest would not")
            total += found
    if not total:
        bad.append(f"no QR code decoded anywhere at {dpi}dpi — nothing to scan")
    return bad


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dpi", type=int, default=200,
                    help="rasterisation for the scan test (default: a middling office print)")
    args = ap.parse_args()

    pdfs = sorted(OUT.glob("*.pdf"))
    if not pdfs:
        print("nothing in out/ — run build.py first", file=sys.stderr)
        return 1

    failed = 0
    for pdf in pdfs:
        bad = check_pdf(pdf, args.dpi)
        if bad:
            failed += 1
            print(f"✗ {pdf.name}")
            for b in bad:
                print(f"    {b}")
        else:
            print(f"✓ {pdf.name}")
    print(f"\n{len(pdfs) - failed}/{len(pdfs)} clean")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
