#!/usr/bin/env python3
"""Gate the rendered PDFs before anyone sends them to a printer.

Checks what a renderer can silently get wrong: page geometry, page counts, font embedding,
image resolution, the trim/bleed declaration on the shop exports, and — by decoding the
rasterized artwork rather than the source SVG — that every QR code still scans.

    python3 check.py           # all of out/
    python3 check.py --dpi 200 # rasterize the QR check at a chosen resolution
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
TOL = 0.3                        # mm; Chrome quantizes the page box to 1/300 inch


def run(cmd: list[str]) -> str:
    return subprocess.run(cmd, capture_output=True, text=True).stdout


def page_boxes(pdf: Path) -> list[dict]:
    """Every declared box, per page, as (x0, y0, x1, y1) in mm.

    pdfinfo prints a TrimBox for every page whether or not the file declares one — it
    falls back to the CropBox — so the boxes are compared numerically further down rather
    than tested for presence.
    """
    out = run(["pdfinfo", "-box", "-f", "1", "-l", "99", str(pdf)])
    pages: dict[int, dict] = {}
    for m in re.finditer(r"Page +(\d+) (\w+Box): +([\d.-]+) +([\d.-]+) +([\d.-]+) +([\d.-]+)",
                         out):
        page = pages.setdefault(int(m.group(1)), {})
        page[m.group(2)] = tuple(float(m.group(i)) / PT for i in (3, 4, 5, 6))
    if not pages:  # a one-page file reports without the "Page N" prefix
        for m in re.finditer(r"(\w+Box): +([\d.-]+) +([\d.-]+) +([\d.-]+) +([\d.-]+)", out):
            pages.setdefault(1, {})[m.group(1)] = tuple(
                float(m.group(i)) / PT for i in (2, 3, 4, 5))
    return [pages[k] for k in sorted(pages)]


def box_size(box: tuple) -> tuple[float, float]:
    return box[2] - box[0], box[3] - box[1]


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
    trim = TRIM[next(k for k in ("a4", "a5", "a6", "card")
                     if f"-{k}-" in name or f"-{k}4up" in name)] if shop else None
    for i, page in enumerate(boxes, 1):
        media = page.get("MediaBox")
        if not media:
            bad.append(f"page {i} has no MediaBox")
            continue
        mw, mh = box_size(media)
        if abs(mw - w) > TOL or abs(mh - h) > TOL:
            bad.append(f"page {i} is {mw:.2f}×{mh:.2f}mm, expected {w}×{h}")
        if not shop:
            continue
        # A shop export has to say where to cut and how far the art runs past it. pdfinfo
        # echoes the MediaBox when a box is undeclared, so equality with it is the failure.
        for label, want in (("TrimBox", trim),
                            ("BleedBox", (trim[0] + 2 * 3.0, trim[1] + 2 * 3.0))):
            got = page.get(label)
            if not got or box_size(got) == box_size(media):
                bad.append(f"page {i} declares no {label} — a shop cannot find the trim")
            elif any(abs(a - b) > TOL for a, b in zip(box_size(got), want)):
                gw, gh = box_size(got)
                bad.append(f"page {i} {label} is {gw:.2f}×{gh:.2f}mm, expected "
                           f"{want[0]}×{want[1]}")

    lines = run(["pdffonts", str(pdf)]).splitlines()
    emb = lines[0].index("emb") if lines and "emb" in lines[0] else None
    for line in (f for f in lines[2:] if f.strip()):
        if "Type 3" in line:
            bad.append(f"Type 3 font, which preflight rejects: {line.split()[0]}")
        elif emb is None or line[emb:emb + 3].strip() != "yes":
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


UNEMBEDDED_FONT_PDF = """%PDF-1.4
1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 286.08 200.88]
 /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >> endobj
4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj
5 0 obj << /Length 44 >> stream
BT /F1 12 Tf 40 100 Td (unembedded) Tj ET
endstream endobj
trailer << /Root 1 0 R /Size 6 >>
"""


def self_test() -> int:
    """Prove the gate rejects what it claims to reject.

    A check that reads the wrong column or matches a regex that cannot match passes
    everything in silence, which is worse than no check — so each one is shown a file
    that breaks exactly it.
    """
    cases = []
    with tempfile.TemporaryDirectory() as tmp:
        d = Path(tmp)

        # Boxes: rename the keys in a real shop export to something no reader knows.
        # Same byte length, so every offset in the file stays valid.
        src = next(OUT.glob("*card*-duplex-shop.pdf"), None)
        if src:
            blind = d / src.name
            blind.write_bytes(src.read_bytes()
                              .replace(b"/TrimBox", b"/TrimBoy")
                              .replace(b"/BleedBox", b"/BleedBoy"))
            cases.append(("undeclared trim and bleed", blind, "declares no"))

        loose = d / "spross-flyer-a4-xx.pdf"
        loose.write_text(UNEMBEDDED_FONT_PDF)
        cases.append(("a font that is not embedded", loose, "font not embedded"))

        failed = 0
        for label, pdf, want in cases:
            said = check_pdf(pdf, dpi=72)
            if any(want in line for line in said):
                print(f"✓ caught {label}")
            else:
                failed += 1
                print(f"✗ MISSED {label} — the gate said: {said or 'nothing'}")
    if not cases:
        print("nothing to test against — run build.py first", file=sys.stderr)
        return 1
    return 1 if failed else 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dpi", type=int, default=200,
                    help="rasterization for the scan test (default: a middling office print)")
    ap.add_argument("--self-test", action="store_true",
                    help="check that the checks still catch a broken file")
    args = ap.parse_args()

    if args.self_test:
        return self_test()

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
