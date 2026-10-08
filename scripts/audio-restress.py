#!/usr/bin/env python3
"""Re-key shipped word recordings onto the stress-marked form their card now shows.

    scripts/audio-restress.py [--check] [--lang uk ...]

A recording is filed under the form it speaks (`speechKey`), and the speech key keeps the
stress mark (U+0301) a catalog writes on uk `пі́вніч`, so marking a card's stress strands the
recording filed under the plain spelling. Where a shipped `words` entry is displayed by no card
(a plain spelling left in `accepts` for grading displays nothing) and exactly ONE stress-marked
form a card shows has its plain spelling, the entry moves onto that form: key, `matches` and
file name follow it, while bytes, `sha256`, credit and the measured index stay. Two differently stressed forms sharing the plain spelling are two
readings, and which one a take says is a judgement this script never makes: those are printed
and left alone. `--check` prints what would move and exits 1 if anything would.

After a run, `$W/sync-from-shipped.py <lang>` carries the new keys into the pack.
"""
import argparse
import glob
import importlib.util
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from audio_gates import speech_key, stress_free  # noqa: E402

_spec = importlib.util.spec_from_file_location('audio_catalog', os.path.join(HERE, 'audio-catalog.py'))
_catalog = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_catalog)


def restressed(key, stressed):
    """[key] as it is spelled, with the stress marks of its [stressed] speech key put in:
    the apostrophe the key was written with stays, where the speech key folded it."""
    out, rest = [], iter(key)
    for char in stressed:
        out.append(char if char == '\u0301' else next(rest))
    return ''.join(out) + ''.join(rest)


def displayed_forms(lang):
    """Speech keys of every form a card in [lang] DISPLAYS — text, teaches, tagged forms.
    `accepts` are typed and never shown, so a plain spelling kept there for grading
    holds no recording back from its stressed card."""
    keys = set()
    for path in glob.glob(os.path.join(_catalog.CATALOG, 'areas', '*', '%s.json' % lang)):
        for word in _catalog.read_json(path).get('words', {}).values():
            forms = [word['text']] + word.get('teaches', [])
            for value in word.get('forms', {}).values():
                forms += value if isinstance(value, list) else [value]
            keys.update(speech_key(form) for form in forms)
    return keys


def moves(shown, displayed, words):
    """(plain key, stressed key) per shipped entry to re-key, and the plain keys left ambiguous."""
    readings = {}
    for key in shown:
        if stress_free(key) != key:
            readings.setdefault(stress_free(key), set()).add(key)
    found, ambiguous = [], []
    for key in sorted(words):
        spoken = speech_key(key)
        if spoken in displayed:
            continue
        said = readings.get(stress_free(spoken), set())
        if len(said) == 1:
            found.append((key, restressed(key, next(iter(said)))))
        elif said:
            ambiguous.append((key, sorted(said)))
    return found, ambiguous


def restress(lang, shown, check):
    out_dir = os.path.join(_catalog.CATALOG, 'audio', lang)
    manifest = _catalog.read_manifest(out_dir)
    words = manifest.get('words', {})
    found, ambiguous = moves(shown.get(lang, set()), displayed_forms(lang), words)
    for key, said in ambiguous:
        print('  %s %s: two readings %s — left as it is' % (lang, key, ' / '.join(said)))
    for key, stressed in found:
        print('  %s %s -> %s' % (lang, key, stressed))
    if check or not found:
        return len(found)
    for key, stressed in found:
        item = words.pop(key)
        moved = _catalog.form_file('words', stressed)
        os.rename(os.path.join(out_dir, item['file']), os.path.join(out_dir, moved))
        words[stressed] = dict(item, file=moved, matches=stressed)
    _catalog.write_manifest(lang, out_dir, words, manifest.get('letters', {}),
                            manifest.get('texts', {}), manifest.get('articles', {}),
                            manifest.get('calendar', {}), manifest.get('countries', {}))
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--lang', action='append', help='only this language (repeatable)')
    parser.add_argument('--check', action='store_true', help='report what would move, write nothing')
    args = parser.parse_args()
    shown, _ = _catalog.load_catalog()
    audio = os.path.join(_catalog.CATALOG, 'audio')
    langs = args.lang or sorted(name for name in os.listdir(audio) if os.path.isdir(os.path.join(audio, name)))
    pending = sum(restress(lang, shown, args.check) for lang in langs)
    sys.exit(1 if args.check and pending else 0)


if __name__ == '__main__':
    main()
