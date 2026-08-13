#!/usr/bin/env python3
"""Render the Spross print materials: flyers, handbills and business cards, imposed on A4.

Every piece is HTML at its true trim size; sheets place pieces on A4 and headless Chrome
prints them to PDF, so type and the sprout mark stay vector. Copy lives in copy.json,
the artwork constants live here, the visual design lives in print.css.

    ./build.py            # render everything into out/
    ./build.py --lang de  # one language
    ./build.py --html     # stop after writing html/, skip Chrome
"""

from __future__ import annotations

import argparse
import base64
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent
HTML = ROOT / "html"
OUT = ROOT / "out"
ASSETS = ROOT / "assets"

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
URL = "https://spross.net"
ICON = REPO / "App/Resources/Assets.xcassets/AppIcon.appiconset/icon-1024.png"

# Trim sizes in mm. A5/A6 halve A4 exactly (148.5 / 105 x 148.5) so a cut sheet leaves
# no waste; that is 0.5mm over nominal ISO, well inside hand-cut tolerance.
# `base` is the design unit: the piece's font-size, from which every internal em derives.
# `edge` is how far coloured ink stays clear of a trim line on the home sheets: pieces there
# abut, cutting is by hand and no home printer reaches the paper edge, so a mis-cut shows
# paper rather than a sliver of a neighbour's colour. The shop exports set it to 0 and bleed.
# `qr` is the code's whole footprint including its four-module quiet zone; at 37 modules
# across, these all keep a module at or above 0.59mm, which scans off an inkjet by hand.
SIZES = {
    "a4": dict(w=210.0, h=297.0, base=4.3, qr=36.0, edge=5.0),
    "a5": dict(w=148.5, h=210.0, base=3.05, qr=26.0, edge=5.0),
    "a6": dict(w=105.0, h=148.5, base=2.9, qr=22.0, edge=4.0),
    "card": dict(w=85.0, h=55.0, base=2.4, qr=23.0, edge=4.0),
}

A4_W, A4_H = 210.0, 297.0
BLEED = 3.0          # bleed on the shop exports
MARK_LEN = 5.0       # trim marks fill the ring outside the bleed, never the piece itself
TICK_LEN = 3.0       # cut ticks in the margin of a home sheet
HAIRLINE = 0.25
CARD_TOP = 8.0       # the card grid sits high on the sheet: a classic inkjet cannot print
CARD_BOTTOM = 14.0   # the last 12.7mm at the bottom, ticks and all

# The sprout from the site header, as flat print colours (viewBox 20 16 68 72).
MARK = """<svg class="mark" viewBox="20 16 68 72" xmlns="http://www.w3.org/2000/svg">
<path fill="#8A6F4D" d="M54,78 C54,66 54,58 54,50 C54,42 50,38 44,36 C50,40 52,46 52,52 C52,58 52,66 52,78 Z"/>
<path fill="#6FA659" d="M53,52 C46,52 38,48 36,40 C34,34 36,30 36,30 C42,30 50,32 53,40 C55,45 53,52 53,52 Z"/>
<path fill="#4C8A3F" d="M55,46 C62,46 70,42 72,34 C74,28 72,24 72,24 C66,24 58,26 55,34 C53,39 55,46 55,46 Z"/>
<path fill="#8A6F4D" d="M40,78 L68,78 C68,82 64,85 60,85 L48,85 C44,85 40,82 40,78 Z"/>
</svg>"""

LEAF = ('<svg class="leaf" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">'
        '<path fill="#6FA659" d="M4,20 C4,11 11,4 20,4 C20,13 13,20 4,20 Z"/></svg>')

LANGS_LINE = ("<b>Deutsch</b> · <b>English</b> · <b>Español</b> · "
              "<b>Kiswahili</b> · <b>Українська</b>")

IMPRINT = dict(
    name="Janek Janetzko",
    company="Polygon GmbH",
    street="Bamberger Str. 43",
    city="96215 Lichtenfels",
    mail="feedback@spross.net",
)


# --------------------------------------------------------------------------- assets

def data_uri(path: Path, mime: str) -> str:
    return f"data:{mime};base64," + base64.b64encode(path.read_bytes()).decode()


def fonts_css() -> str:
    """Nunito, embedded. The site already names it in the brand stack, and unlike the macOS
    system faces its OFL licence permits embedding in a PDF sent to a printer.

    Static instances, not the variable font: Chrome cannot subset-embed a variable face and
    falls back to dumping every glyph as a Type 3 charproc, which shop preflight rejects.
    """
    faces = [("Nunito-Regular.ttf", 400), ("Nunito-SemiBold.ttf", 600), ("Nunito-Bold.ttf", 700)]
    return "\n".join(f"""@font-face {{
  font-family: "Nunito";
  src: url({data_uri(ASSETS / 'fonts' / f, 'font/ttf')}) format("truetype");
  font-weight: {w};
  font-style: normal;
}}""" for f, w in faces)


def qr_svg() -> str:
    """A quiet-zone-correct QR for the site, in ink that matches the body text."""
    out = ASSETS / "qr-spross.svg"
    # -m 4 is the quiet zone ISO/IEC 18004 asks for. Level Q holds the address in a 25×25
    # symbol where H needs 29×29, so the same printed square buys a 12% wider module —
    # and 25% recovery is ample for a code nothing is laid over.
    subprocess.run(
        ["qrencode", "-t", "SVG", "-l", "Q", "-m", "4", "-s", "10", "-o", str(out), URL],
        check=True,
    )
    svg = out.read_text()
    svg = svg.replace("#000000", "#1E2620").replace('fill="#ffffff"', 'fill="#FFFFFF"')
    # Chrome scales the SVG by its intrinsic cm size unless we hand it the box.
    svg = re.sub(r'width="[^"]+" height="[^"]+"', 'width="100%" height="100%"', svg, count=1)
    out.write_text(svg)
    return svg


def qr_block(mm: float, cls: str = "qr") -> str:
    return (f'<span class="{cls}" style="width:{mm}mm;height:{mm}mm;display:inline-block">'
            f"{QR}</span>")


# --------------------------------------------------------------------------- pieces

def cta(c: dict, size: str) -> str:
    return f"""<div class="cta">
  {qr_block(SIZES[size]['qr'])}
  <div>
    <div class="url">spross.net</div>
    <div class="invite">{c['invite']}</div>
  </div>
</div>"""


def flyer_front(c: dict, size: str) -> str:
    """A4 / A5 front: wordmark, the growing-box promise, the icon, the address band."""
    return f"""<div class="flyer">
  <div class="top">
    <div class="wordmark">{MARK}<span class="word">Spross</span></div>
    <div class="strapline">{c['strapline']}</div>
  </div>
  <div class="lede">
    <h1 class="headline">{c['headline']}</h1>
    <p class="sub">{c['sub']}</p>
  </div>
  <div class="art"><img class="tile" src="{TILE}" alt=""></div>
  <div class="foot">
    {cta(c, size)}
    <div class="langs">{LANGS_LINE}</div>
  </div>
</div>"""


def flyer_back(c: dict, size: str) -> str:
    feats = "\n".join(
        f"<li><h3>{f['title']}</h3><p>{f['body']}</p></li>" for f in c["features"]
    )
    d = c["dict"]
    return f"""<div class="back">
  <div class="head">
    <div class="wordmark">{MARK}<span class="word">Spross</span></div>
    <div class="strapline">{c['strapline']}</div>
  </div>
  <dl class="dict">
    <div class="entry"><dt>{d['term_a']}</dt> <dd>{d['gloss_a']}</dd></div>
    <div class="entry"><dt>{d['term_b']}</dt> <dd>{d['gloss_b']}</dd></div>
    <p class="moral">{d['moral']}</p>
  </dl>
  <p class="sub">{c['positioning']}</p>
  <hr class="rule">
  <h2 class="back-title">{c['back_title']}</h2>
  <ul class="features">{feats}</ul>
  <div class="art grow"><img class="tile" src="{TILE}" alt=""></div>
  <hr class="rule">
  <div class="foot-plain">
    {cta(c, size)}
    <p class="contact"><b>{c['contact_line']}</b><br>{IMPRINT['mail']}</p>
  </div>
  <p class="closing"><span>{c['closing_line']}</span></p>
</div>"""


def mini_front(c: dict) -> str:
    return f"""<div class="mini">
  <div class="top">
    <div class="wordmark">{MARK}<span class="word">Spross</span></div>
    <div class="strapline">{c['strapline']}</div>
  </div>
  <h1 class="headline">{c['mini_headline']}</h1>
  <p class="sub">{c['mini_sub']}</p>
  <div class="art"><img class="tile" src="{TILE}" alt=""></div>
  <div class="foot">
    {cta(c, 'a6')}
  </div>
</div>"""


def mini_back(c: dict) -> str:
    pts = "\n".join(f"<li>{LEAF}{p}</li>" for p in c["mini_points"])
    d = c["dict"]
    return f"""<div class="mini-back">
  <div class="wordmark">{MARK}<span class="word">Spross</span></div>
  <p class="sub">{c['positioning']}</p>
  <ul class="points">{pts}</ul>
  <div class="art grow"><img class="tile" src="{TILE}" alt=""></div>
  <p class="moral-mini">{d['moral']}</p>
  <div class="langs">{LANGS_LINE}</div>
  <div class="foot-mini">
    {cta(c, 'a6')}
  </div>
</div>"""


def card_front(c: dict) -> str:
    return f"""<div class="card front">
  <div class="edge"></div>
  <div class="inner">
    <div class="wordmark">{MARK}<span class="word">Spross</span></div>
    <div class="strapline">{c['card_strapline']}</div>
    <div class="card-langs">{LANGS_LINE}</div>
  </div>
</div>"""


def card_solo(c: dict, personal: bool) -> str:
    """The single-sided card. A card printed on one side still has to reach the site, so the
    code and the address share the face with the name instead of living on a back."""
    qr = qr_block(SIZES["card"]["qr"] - 2)
    if personal:
        left = f"""<div class="wordmark small">{MARK}<span class="word">Spross</span></div>
      <div class="who">{IMPRINT['name']}</div>
      <div class="role">{c['role']} · {IMPRINT['company']}</div>
      <div class="addr">{IMPRINT['street']} · {IMPRINT['city']}</div>
      <p class="mail">{IMPRINT['mail']}</p>"""
    else:
        left = f"""<div class="wordmark">{MARK}<span class="word">Spross</span></div>
      <div class="strapline">{c['card_strapline']}</div>
      <div class="grow"></div>
      <p class="mail">{IMPRINT['mail']}</p>"""
    return f"""<div class="card front solo">
  <div class="edge"></div>
  <div class="inner">
    <div class="left">{left}</div>
    <div class="right">
      {qr}
      <div class="url">spross.net</div>
    </div>
  </div>
</div>"""


def card_back(c: dict, personal: bool) -> str:
    invite = c["card_invite"].replace(" / ", "<br>")
    if not personal:
        return f"""<div class="card back">
  <div class="row">
    {qr_block(SIZES['card']['qr'])}
    <div>
      <div class="url">spross.net</div>
      <div class="invite">{invite}</div>
    </div>
  </div>
  <p class="mail">{IMPRINT['mail']}</p>
</div>"""
    return f"""<div class="card back personal">
  <div class="who-block">
    <div class="who">{IMPRINT['name']}</div>
    <div class="role">{c['role']} · {IMPRINT['company']}</div>
    <div class="addr">{IMPRINT['street']}<br>{IMPRINT['city']}</div>
  </div>
  <div class="row">
    {qr_block(SIZES['card']['qr'] - 1)}
    <div>
      <div class="url">spross.net</div>
      <p class="mail">{IMPRINT['mail']}</p>
    </div>
  </div>
</div>"""


# --------------------------------------------------------------------------- sheets

def piece(size: str, inner: str, extra: str = "", bleed: bool = False) -> str:
    """One cut-size rectangle. In bleed mode it grows by the bleed on every side and its
    edge-hugging colour runs out to the new edge, so the trim can land anywhere in the ring."""
    s = SIZES[size]
    b = BLEED if bleed else 0.0
    cls = "piece bleed" if bleed else "piece"
    return (f'<div class="{cls}" style="width:{s["w"] + 2 * b}mm;height:{s["h"] + 2 * b}mm;'
            f'font-size:{s["base"]}mm;--edge:{0 if bleed else s["edge"]}mm;--bleed:{b}mm;'
            f'{extra}">{inner}</div>')


def document(body: str, page_css: str) -> str:
    return f"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<link rel="stylesheet" href="fonts.css">
<link rel="stylesheet" href="../print.css">
<style>{page_css}</style>
</head><body>{body}</body></html>"""


def sheet(children: str, w: float = A4_W, h: float = A4_H) -> str:
    return f'<div class="sheet" style="width:{w}mm;height:{h}mm">{children}</div>'


def tick(x: float, y: float, length: float, vertical: bool) -> str:
    d = "v" if vertical else "h"
    span = f"height:{length}mm" if vertical else f"width:{length}mm"
    return (f'<div class="tick {d}" style="left:{x - (HAIRLINE / 2 if vertical else 0)}mm;'
            f'top:{y - (0 if vertical else HAIRLINE / 2)}mm;{span}"></div>')


def ticks(xs: list[float], ys: list[float], x0: float, y0: float,
          gw: float, gh: float, sw: float, sh: float) -> str:
    """Cut ticks in the sheet margin at every cut line — never inside a piece.

    A tick is only drawn where there is margin to hold it, and it is pulled clear of the
    sheet edge, which no home printer can reach.
    """
    out = []
    top, bottom = y0 - TICK_LEN, y0 + gh
    left, right = x0 - TICK_LEN, x0 + gw
    for x in xs:
        if y0 >= TICK_LEN + 1:
            out.append(tick(x, top, TICK_LEN, True))
        if sh - y0 - gh >= TICK_LEN + 1:
            out.append(tick(x, bottom, TICK_LEN, True))
    for y in ys:
        if x0 >= TICK_LEN + 1:
            out.append(tick(left, y, TICK_LEN, False))
        if sw - x0 - gw >= TICK_LEN + 1:
            out.append(tick(right, y, TICK_LEN, False))
    return "".join(out)


def grid_sheet(size: str, cells: list[str], cols: int, rows: int, back: bool = False) -> str:
    """Abutting pieces on a grid, horizontally centred, with ticks in the margins.

    A portrait sheet flipped on its long (vertical) edge mirrors left to right, so a back
    page has to swap its columns. The grid stays centred horizontally, which is what makes
    that swap land exactly on the fronts.
    """
    s = SIZES[size]
    gw, gh = s["w"] * cols, s["h"] * rows
    x0 = (A4_W - gw) / 2
    y0 = CARD_TOP if size == "card" else (A4_H - gh) / 2
    if back:
        cells = [c for r in range(rows) for c in reversed(cells[r * cols:(r + 1) * cols])]
    body = [f'<div class="grid" style="left:{x0}mm;top:{y0}mm;width:{gw}mm;height:{gh}mm;'
            f'grid-template-columns:repeat({cols},{s["w"]}mm);'
            f'grid-template-rows:repeat({rows},{s["h"]}mm)">']
    body += [piece(size, c) for c in cells]
    body.append("</div>")
    body.append(ticks([x0 + i * s["w"] for i in range(cols + 1)],
                      [y0 + i * s["h"] for i in range(rows + 1)],
                      x0, y0, gw, gh, A4_W, A4_H))
    return sheet("".join(body))


def a5_sheet(inner: str, back: bool = False) -> str:
    """Two A5 portraits rotated into the two landscape halves of a portrait A4.

    Each slot spans the full sheet width, so a long-edge flip leaves the top/bottom order
    alone. The rotation is the part that has to flip: the sheet turns about its vertical
    axis while the cut flyer turns about its own, and those differ by 180°. The back
    therefore rotates the other way — reusing the front's rotate(90deg) prints every back
    upside down.
    """
    # why: Chrome's print pipeline clips against the page box in PRE-transform coordinates,
    # so a piece laid out below the page edge loses whatever hangs over — even though the
    # rotation would have brought it back inside. Every piece is therefore laid out at the
    # sheet origin and only the transform moves it into its half.
    half = A4_H / 2
    slots = []
    for i in range(2):
        move = (f"translate(0,{half * (i + 1)}mm) rotate(-90deg)" if back
                else f"translate({A4_W}mm,{half * i}mm) rotate(90deg)")
        slots.append(piece("a5", inner,
                           f"position:absolute;left:0;top:0;transform-origin:0 0;transform:{move};"))
    # one cut, straight across the middle; the guides sit in from the edge so they print
    slots += [tick(5, half, 7, False), tick(A4_W - 12, half, 7, False)]
    return sheet("".join(slots))


def shop_page(size: str, inner: str) -> str:
    """One piece, single-up, for a print shop: real bleed on all four sides, and trim marks
    outside the bleed so an on-target cut removes them and a drifting one never prints them."""
    s = SIZES[size]
    ring = BLEED + MARK_LEN
    w, h = s["w"] + 2 * ring, s["h"] + 2 * ring
    body = [f'<div style="position:absolute;left:{MARK_LEN}mm;top:{MARK_LEN}mm">'
            + piece(size, inner, bleed=True) + "</div>"]
    # each mark fills the outer ring exactly, so it starts at the bleed edge — the gap a
    # shop expects between the trim corner and the mark is the bleed itself
    for x in (ring, ring + s["w"]):
        body.append(tick(x, 0, MARK_LEN, True))
        body.append(tick(x, h - MARK_LEN, MARK_LEN, True))
    for y in (ring, ring + s["h"]):
        body.append(tick(0, y, MARK_LEN, False))
        body.append(tick(w - MARK_LEN, y, MARK_LEN, False))
    return sheet("".join(body), w, h)


PAGE = "@page {{ size: {w}mm {h}mm; margin: 0 }}"


# --------------------------------------------------------------------------- render

def typeset(value):
    """Bind the space before an em dash, so a dash never starts a line."""
    if isinstance(value, str):
        return value.replace(" — ", " — ").replace(" · ", " · ")
    if isinstance(value, list):
        return [typeset(v) for v in value]
    if isinstance(value, dict):
        return {k: typeset(v) for k, v in value.items()}
    return value


def write(name: str, html: str) -> Path:
    p = HTML / f"{name}.html"
    p.write_text(html)
    return p


PT = 72 / 25.4


_warned_boxes = False


def set_boxes(pdf: Path, trim: tuple[float, float]) -> None:
    """Declare where the trim and the bleed sit on a shop export.

    Chrome writes a MediaBox and nothing else, so without this a shop cannot tell which
    210×297 of the sheet is the flyer, and preflight fails the file on that alone.
    Ghostscript's pdfmark route is not usable here — pdfwrite answers it by setting every
    box to the MediaBox, which would tell the shop to trim at the marks.
    """
    global _warned_boxes
    try:
        import pikepdf
    except ImportError:
        if not _warned_boxes:
            print("  (no pikepdf: shop exports carry trim marks but no TrimBox — "
                  "pip install pikepdf)", file=sys.stderr)
            _warned_boxes = True
        return
    ring, b = (BLEED + MARK_LEN) * PT, BLEED * PT
    w, h = trim[0] * PT, trim[1] * PT
    with pikepdf.open(pdf, allow_overwriting_input=True) as doc:
        for page in doc.pages:
            page.TrimBox = [ring, ring, ring + w, ring + h]
            page.BleedBox = [ring - b, ring - b, ring + w + b, ring + h + b]
        doc.save(pdf)


def to_pdf(name: str, trim: tuple[float, float] | None = None) -> None:
    src = HTML / f"{name}.html"
    dst = OUT / f"{name}.pdf"
    subprocess.run(
        [CHROME, "--headless", "--disable-gpu", "--no-pdf-header-footer",
         "--virtual-time-budget=4000", f"--print-to-pdf={dst}", src.as_uri()],
        check=True, capture_output=True,
    )
    if trim:
        set_boxes(dst, trim)
    print(f"  {dst.relative_to(ROOT)}")


def build_lang(lang: str, c: dict, render: bool) -> list[str]:
    names: list[tuple[str, tuple[float, float] | None]] = []

    def emit(name: str, sheets: list[str], page: tuple[float, float] = (A4_W, A4_H),
             trim: tuple[float, float] | None = None):
        css = PAGE.format(w=page[0], h=page[1])
        write(name, document("".join(sheets), css))
        names.append((name, trim))

    front_a4, back_a4 = flyer_front(c, "a4"), flyer_back(c, "a4")
    front_a5, back_a5 = flyer_front(c, "a5"), flyer_back(c, "a5")
    m_front, m_back = mini_front(c), mini_back(c)

    def shop(name: str, size: str, pages: list[str]):
        s = SIZES[size]
        ring = 2 * (BLEED + MARK_LEN)
        emit(name, [shop_page(size, p) for p in pages],
             (s["w"] + ring, s["h"] + ring), trim=(s["w"], s["h"]))

    # A4 flyer — one per sheet at home, single-up with bleed for a shop
    emit(f"spross-flyer-a4-{lang}", [sheet(piece("a4", front_a4))])
    emit(f"spross-flyer-a4-{lang}-duplex",
         [sheet(piece("a4", front_a4)), sheet(piece("a4", back_a4))])
    shop(f"spross-flyer-a4-{lang}-shop", "a4", [front_a4])
    shop(f"spross-flyer-a4-{lang}-duplex-shop", "a4", [front_a4, back_a4])

    # A5 flyer, 2-up on A4 (cut once across the middle)
    emit(f"spross-flyer-a5-2up-{lang}", [a5_sheet(front_a5)])
    emit(f"spross-flyer-a5-2up-{lang}-duplex",
         [a5_sheet(front_a5), a5_sheet(back_a5, back=True)])
    shop(f"spross-flyer-a5-{lang}-duplex-shop", "a5", [front_a5, back_a5])

    # A6 handbill, 4-up on A4 — the sheet is quartered, so there is no margin for ticks
    emit(f"spross-handbill-a6-4up-{lang}", [grid_sheet("a6", [m_front] * 4, 2, 2)])
    emit(f"spross-handbill-a6-4up-{lang}-duplex",
         [grid_sheet("a6", [m_front] * 4, 2, 2),
          grid_sheet("a6", [m_back] * 4, 2, 2, back=True)])
    shop(f"spross-handbill-a6-{lang}-duplex-shop", "a6", [m_front, m_back])

    # Business cards, 10-up on A4
    for kind, personal in (("", False), ("-personal", True)):
        f, b = card_front(c), card_back(c, personal)
        solo = card_solo(c, personal)
        emit(f"spross-cards{kind}-{lang}", [grid_sheet("card", [solo] * 10, 2, 5)])
        emit(f"spross-cards{kind}-{lang}-duplex",
             [grid_sheet("card", [f] * 10, 2, 5),
              grid_sheet("card", [b] * 10, 2, 5, back=True)])
        shop(f"spross-card{kind}-{lang}-duplex-shop", "card", [f, b])

    if render:
        for n, trim in names:
            to_pdf(n, trim)
    return [n for n, _ in names]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--lang", action="append", help="de / en (default: both)")
    ap.add_argument("--html", action="store_true", help="write html only, skip Chrome")
    args = ap.parse_args()

    if not args.html and not Path(CHROME).exists():
        print(f"Chrome not found at {CHROME}", file=sys.stderr)
        return 1

    copy = typeset(json.loads((ROOT / "copy.json").read_text()))
    langs = args.lang or sorted(copy)

    for d in (HTML, OUT):
        shutil.rmtree(d, ignore_errors=True)
        d.mkdir(parents=True)
    (HTML / "fonts.css").write_text(fonts_css())

    for lang in langs:
        print(f"{lang}:")
        build_lang(lang, copy[lang], render=not args.html)
    return 0


QR = qr_svg()
TILE = data_uri(ICON, "image/png")

if __name__ == "__main__":
    raise SystemExit(main())
