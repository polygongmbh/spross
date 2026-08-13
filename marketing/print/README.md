# Print materials

Flyers, handbills and business cards for Spross, in German and English,
each carrying the app icon and a QR code to spross.net.
Sheets to print at home are imposed on A4; the shop exports are single-up with bleed.

Everything in `out/` is generated. Edit the sources, never the PDFs:

| file | what it holds |
| --- | --- |
| `copy.json` | every string, per language (`de`, `en`) |
| `print.css` | the design system: palette, type scale, each piece's layout |
| `build.py` | trim sizes, imposition, and the run through headless Chrome |
| `check.py` | the gate: geometry, fonts, resolution, and whether the codes still scan |

```sh
python3 build.py             # everything, into out/
python3 build.py --lang de   # one language
python3 build.py --html      # stop at html/, skip Chrome
python3 check.py             # verify out/ before sending anything to a printer
python3 check.py --self-test # confirm the gate still catches a broken file
```

`build.py` needs `qrencode` and Chrome, and nothing from PyPI — the trim and bleed boxes
are appended to Chrome's output as a PDF incremental update, so a checkout with a stock
Python still produces the file a shop expects.
`check.py` needs `poppler` (`pdfinfo`, `pdffonts`, `pdfimages`), ImageMagick, and Swift
for the decoder.

## What comes out

**To print at home**, per language:

| file | sheet | holds |
| --- | --- | --- |
| `spross-flyer-a4-<lang>` | A4 | one A4 flyer |
| `spross-flyer-a4-<lang>-duplex` | A4, 2 pages | the same flyer, front and back |
| `spross-flyer-a5-2up-<lang>` | A4 | two A5 flyers — one cut across the middle |
| `spross-flyer-a5-2up-<lang>-duplex` | A4, 2 pages | the same, both sides |
| `spross-handbill-a6-4up-<lang>` | A4 | four A6 handbills — quarter the sheet |
| `spross-handbill-a6-4up-<lang>-duplex` | A4, 2 pages | the same, both sides |
| `spross-cards-<lang>` | A4 | ten single-sided cards |
| `spross-cards-<lang>-duplex` | A4, 2 pages | ten cards, brand front and QR back |
| `spross-cards-personal-<lang>` | A4 | ten single-sided cards with the imprint |
| `spross-cards-personal-<lang>-duplex` | A4, 2 pages | ten cards, brand front and imprint back |

**To send to a shop**, per language: `-shop` files, single-up at trim + 3mm bleed,
with trim marks outside the bleed and TrimBox/BleedBox declared —
`spross-flyer-a4-<lang>-shop`, `spross-flyer-a4-<lang>-duplex-shop`,
`spross-flyer-a5-<lang>-duplex-shop`, `spross-handbill-a6-<lang>-duplex-shop`,
`spross-card-<lang>-duplex-shop`, `spross-card-personal-<lang>-duplex-shop`.

The single-sided card is its own design: it carries the code and the address on the same
face, because a card printed on one side still has to reach the site.
The two-sided cards share a brand front; the product card's back carries only the QR and
the site, the personal card's back carries the imprint and nothing invented for it.

## Printing

**At home.** Print at 100% — "actual size", scaling off.
Any "fit to page" setting shrinks the sheet and the cut marks stop being where they say.
For the two-sided files choose long-edge binding (the usual "flip on long edge"):
every sheet is portrait, so a long-edge flip mirrors left to right, which is what the
backs are laid out for. Print one sheet as a registration test before a run.
Cut along the ticks in the sheet margin; the A5 sheet is one cut across the middle,
the A6 sheet is quartered, and those two carry no ticks because a full sheet of pieces
leaves no margin to put them in.

Color is held clear of every cut on these sheets, so a crooked cut or a printer that
cannot reach the paper edge shows paper, never a slice of the neighboring piece.

**At a shop.** Send the `-shop` files and ask them not to scale.
They are sRGB with no output intent; a shop converting to CMYK is expected, and it is worth
asking for black text to convert to K only so small type does not print in four colors.

## The parts

- **The QR** encodes `https://spross.net` at error-correction level Q with a four-module
  quiet zone — the address fits a 25×25 symbol there, so the printed module stays at or
  above 0.64mm on the smallest piece. `check.py` decodes every code back out of a
  rasterized page, one piece at a time, the way a phone meets it.
- **The icon** is the app's own `icon-1024.png`, placed so it stays at or above 300dpi.
- **The type** is Nunito, bundled in `assets/fonts/` and embedded in each PDF.
  The app's own SF Pro Rounded is licensed for interfaces, not for print; Nunito is the
  rounded face the site already names next to it, and its license (`assets/fonts/OFL.txt`)
  permits embedding. The three static weights are instances cut from the variable font,
  because Chrome cannot subset-embed a variable face and falls back to Type 3 glyphs,
  which shop preflight rejects.

## Imposition

Pieces halve A4 exactly — A5 is 148.5mm, A6 is 105×148.5mm — so a cut sheet leaves no
waste. That is 0.5mm over the nominal ISO sizes, inside the tolerance of a hand cut,
and it is why the home sheets are not offered to a shop under those names.

A portrait sheet flipped on its long edge mirrors left to right. The A6 and card backs
therefore swap columns; the A5 slots span the full width, so their order stays, but each
back rotates the opposite way — the sheet turns about its vertical axis while the cut
flyer turns about its own, and reusing the front's rotation prints every back upside down.

The A5 pieces are rotated 90° into the two halves of the sheet. Chrome's print pipeline
clips against the page box in *pre-transform* coordinates, so each piece is laid out at the
sheet origin and only the transform moves it into place — laying it out in the lower half
instead silently drops whatever hangs past the page edge.
