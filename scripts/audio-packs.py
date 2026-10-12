#!/usr/bin/env python3
"""The workspace's packs: resolved out of Commons, swept for better takes, consolidated onto established voices.

    scripts/audio-packs.py resolve <kind>... [lang...] [--out DIR] [--full] [--append TSV]
    scripts/audio-packs.py sweep [lang...] [--sections S...] [--only FORMS] [--apply]
    scripts/audio-packs.py consolidate [lang...] [--sections S...] [--only FORMS] [--apply]

`resolve` finds a recording for every form the catalog shows that a pack lacks (`audio_resolve.py`),
by the language's route (`audio_routes.py`), and fetches its transcode; nothing is judged or shipped —
`audio-catalog.py --fill` does that. `sweep` and `consolidate` stage swaps of what ships into the
journal (`audio_sweep.py`), which `audio-catalog.py --ship` applies; without `--apply` they only print.
Every run tees itself to `$W/logs/<date>-<command>/` and prints the `tail -f` line first.
One run at a time, languages in sequence: Commons throttles parallel fetches.
"""
import audio_env  # noqa: F401 — before numpy: re-runs under the repo .venv
import argparse
import os
import sys
import traceback

import audio_resolve
import audio_sweep
from audio_forms import load_catalog
from audio_log import RunLog
from audio_manifest import AUDIO
from audio_pick import PICKED, Measures
from audio_routes import KINDS, ROUTES


def run(log, packs, step):
    """[step] over every `(lang, section)` in [packs], each into its own log; the count of failures."""
    failed = 0
    for lang, section in packs:
        with log.pack(lang, section):
            print('== %s %s' % (lang, section))
            try:
                done, errors = step(lang, section)
            except Exception:
                traceback.print_exc()
                done, errors = 'crashed', 1
        log.summary('%s %s: %s%s' % (lang, section, done, ', %d failed' % errors if errors else ''))
        failed += errors
    return failed


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest='command', required=True)
    resolve = commands.add_parser('resolve', help='resolve what a pack lacks and fetch it')
    resolve.add_argument('targets', nargs='+', metavar='KIND|LANG',
                         help='kinds (%s) and languages (all with a route if none)' % ', '.join(KINDS))
    resolve.add_argument('--out', help='the pack directory, for one kind of one language')
    resolve.add_argument('--full', action='store_true', help='re-resolve every form, not only what the pack lacks')
    resolve.add_argument('--append', metavar='TSV', help='add hand-named rows `text file [author]` instead')
    for name, about in (('sweep', 'stage a better take for what ships poor, squashed or one-sided'),
                        ('consolidate', "stage an established voice's take for a voice with few")):
        picker = commands.add_parser(name, help=about)
        picker.add_argument('langs', nargs='*', metavar='LANG', help='all that ship and have a route if none')
        picker.add_argument('--sections', nargs='+', choices=PICKED, default=list(PICKED))
        picker.add_argument('--only', metavar='FORMS', help='comma-separated forms, reconsidered whatever they score')
        picker.add_argument('--apply', action='store_true', help='stage the swaps; otherwise only print them')
    args = parser.parse_args()

    if args.command == 'resolve':
        kinds = [t for t in args.targets if t in KINDS]
        langs = [t for t in args.targets if t not in KINDS] or sorted(ROUTES)
        unknown = [lang for lang in langs if lang not in ROUTES]
        if not kinds or unknown:
            parser.error('no kind given' if not kinds else 'no route for %s' % ', '.join(unknown))
        if args.out and (len(kinds) > 1 or len(langs) > 1):
            parser.error('--out names one pack: one kind, one language')
        packs = [(lang, kind) for lang in langs for kind in kinds]
        failed = run(RunLog('resolve', packs), packs, lambda lang, kind: ('resolved', (
            audio_resolve.append(lang, kind, args.append) if args.append
            else audio_resolve.resolve(lang, kind, args.out, args.full))))
    else:
        langs = args.langs or sorted(lang for lang in ROUTES if os.path.isdir(os.path.join(AUDIO, lang)))
        unknown = [lang for lang in langs if lang not in ROUTES]
        if unknown:
            parser.error('no route for %s' % ', '.join(unknown))
        only = {form.strip() for form in (args.only or '').split(',') if form.strip()}
        packs = [(lang, section) for lang in langs for section in args.sections]
        log, catalog, measures = RunLog(args.command, packs), load_catalog(), Measures()
        picker = audio_sweep.sweep if args.command == 'sweep' else audio_sweep.consolidate

        def step(lang, section):
            swaps, errors = picker(lang, section, catalog, measures, only, args.apply)
            return '%d swap(s)%s' % (swaps, '' if args.apply else ' (dry run)'), errors
        try:
            failed = run(log, packs, step)
        finally:
            measures.close()
    if failed:
        sys.exit('%d items failed — they keep what they had; rerun to retry' % failed)


if __name__ == '__main__':
    main()
