#!/usr/bin/env python3
"""Give the bundled Nunito an anchor for the stress mark on every Ukrainian vowel.

    scripts/font-stress-anchors.py [--check]

The catalog marks Ukrainian stress with U+0301 after the vowel (`пі́вніч`), and the
font places that mark through its `mark` feature. Nunito anchors the acute on most
Cyrillic vowels but not on і є ю я Ю Я, so Android's shaper sets it beside the letter
instead of over it. This adds the missing anchors to `android/src/main/res/font/nunito.ttf`:
centered over the glyph, at the height its neighbors use (over the dot for і, as ї has it).
The font is OFL-1.1, which permits the change. `--check` exits 1 while an anchor is missing.
"""
import argparse
import os
import sys

from fontTools.otlLib.builder import buildAnchor
from fontTools.ttLib import TTFont

FONT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'android', 'src', 'main', 'res', 'font',
                    'nunito.ttf')
VOWELS = 'аеєиіїоуюяАЕЄИІЇОУЮЯ'
# Each missing vowel takes its height from a vowel of the same shape that has one.
HEIGHT_FROM = {'і': 'ї', 'є': 'е', 'ю': 'о', 'я': 'а', 'Ю': 'О', 'Я': 'А'}


def acute_lookup(font):
    """The mark-to-base subtable that places `acutecomb`."""
    for lookup in font['GPOS'].table.LookupList.Lookup:
        for table in lookup.SubTable:
            table = table.ExtSubTable if lookup.LookupType == 9 else table
            if hasattr(table, 'BaseCoverage') and 'acutecomb' in table.MarkCoverage.glyphs:
                return table
    sys.exit('nunito.ttf: no mark-to-base lookup places acutecomb')


def anchor_of(table, mark_class, glyph):
    bases = table.BaseCoverage.glyphs
    return table.BaseArray.BaseRecord[bases.index(glyph)].BaseAnchor[mark_class] if glyph in bases else None


def missing(font, table, mark_class):
    cmap = font.getBestCmap()
    return [vowel for vowel in VOWELS if anchor_of(table, mark_class, cmap[ord(vowel)]) is None]


def add(font, table, mark_class, vowels):
    cmap, glyf = font.getBestCmap(), font['glyf']
    order = font.getReverseGlyphMap()
    for vowel in vowels:
        glyph = cmap[ord(vowel)]
        outline = glyf[glyph]
        y = anchor_of(table, mark_class, cmap[ord(HEIGHT_FROM[vowel])]).YCoordinate
        anchor = buildAnchor((outline.xMin + outline.xMax) // 2, y)
        bases = table.BaseCoverage.glyphs
        if glyph not in bases:
            record = type(table.BaseArray.BaseRecord[0])()
            record.BaseAnchor = [None] * table.ClassCount
            at = sum(1 for name in bases if order[name] < order[glyph])
            bases.insert(at, glyph)
            table.BaseArray.BaseRecord.insert(at, record)
            table.BaseArray.BaseCount = len(table.BaseArray.BaseRecord)
        table.BaseArray.BaseRecord[bases.index(glyph)].BaseAnchor[mark_class] = anchor


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--check', action='store_true', help='report missing anchors, write nothing')
    args = parser.parse_args()
    font = TTFont(FONT)
    table = acute_lookup(font)
    mark_class = table.MarkArray.MarkRecord[table.MarkCoverage.glyphs.index('acutecomb')].Class
    gaps = missing(font, table, mark_class)
    print('nunito.ttf: %s' % ('no stress anchor on ' + ' '.join(gaps) if gaps else 'every Ukrainian vowel is anchored'))
    if args.check or not gaps:
        sys.exit(1 if gaps else 0)
    add(font, table, mark_class, gaps)
    font.save(FONT)


if __name__ == '__main__':
    main()
