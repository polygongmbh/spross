"""Where each language's recordings are on Commons — the one route table every audio script reads.

The route is per language AND per kind, and a wrong one looks like "Commons has no recordings"
rather than like a mistake: sw is filed `Sw-ke-*.flac`, so the plain convention finds 0 of 19
calendar names, and uk toponyms are not in Shtooka at all (2 of 140 country names by convention,
70 through Lingua Libre) while everyday uk vocabulary is the other way round.

  · `words` — `{word}` filename conventions for the words and articles packs, whose numbered
    siblings (`De-Tier2.ogg`) are also every sweep's challengers;
  · `lingualibre` — the Lingua Libre category code beside them;
  · `convention_first` — the convention resolves before Lingua Libre and Lingua Libre fills what
    it missed, which keeps a pack on the voice that recorded most of a language; otherwise the
    other way round;
  · `dump` — a kaikki word→file TSV in the workspace, asked before either;
  · `forms` — `{text}` conventions for the calendar, countries and texts packs, `forms_lingualibre`
    the category that fills what they miss;
  · `letters` — the alphabet's conventions, over `{upper}`, `{glyph}` and `{name}`.

`AUDIO_WORKSPACE` points every script at another workspace — a scratch copy to try a run on.
"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WORKSPACE = os.environ.get('AUDIO_WORKSPACE') or os.path.normpath(
    os.path.join(ROOT, '..', 'data', 'reference', 'audio'))
SECTIONS = ('words', 'letters', 'texts', 'articles', 'calendar', 'countries')
# A pack is resolved per kind; `articles` rides the words route with the article said.
KINDS = ('words', 'articles', 'letters', 'texts', 'calendar', 'countries')

ROUTES = {
    'de': dict(words=['De-{word}.ogg', 'De-{word}.wav', 'De-{word}.oga'], lingualibre='deu',
               convention_first=True, forms=['De-{text}.ogg', 'De-{text}.wav'],
               letters=['De-{upper}.ogg', 'De-{name}.ogg']),
    'uk': dict(words=['Uk-{word}.ogg', 'Uk-{word}.flac'], lingualibre='ukr', convention_first=True,
               forms=['Uk-{text}.ogg'], forms_lingualibre='ukr',
               letters=['{upper}{glyph} – ukrainian.ogg']),
    'sw': dict(words=['Sw-ke-{word}.flac', 'Sw-ke-{word}.ogg'], lingualibre='swa', convention_first=True,
               dump='sw-audio-kaikki.tsv',
               forms=['Sw-ke-{text}.flac', 'Sw-ke-{text}.ogg', 'Sw-{text}.flac']),
    'fr': dict(words=['Fr-{word}.ogg'], lingualibre='fra', forms=['Fr-{text}.ogg'], forms_lingualibre='fra',
               letters=['LL-Q150 (fra)-Sartus85-{glyph} (lettre).wav']),
    'it': dict(words=['It-{word}.ogg'], lingualibre='ita', forms=['It-{text}.ogg'], forms_lingualibre='ita'),
    'es': dict(words=['Es-{word}.ogg'], lingualibre='spa', forms=['Es-{text}.ogg'], forms_lingualibre='spa'),
    'eo': dict(words=['Eo-{word}.ogg'], lingualibre='epo', forms=['Eo-{text}.ogg'], forms_lingualibre='epo',
               letters=['LL-Q143 (epo)-Lepticed7-{name}.wav']),
}


def route(lang):
    if lang not in ROUTES:
        raise SystemExit('%s: no route in scripts/audio_routes.py' % lang)
    return ROUTES[lang]


def pack_dir(lang, kind):
    return os.path.join(WORKSPACE, 'pack-%s-%s' % (lang, kind))
