#!/usr/bin/env python3
"""The workspace's packs: resolved out of Commons, swept for better takes, consolidated onto established voices.

    scripts/audio-packs.py resolve <kind>... [lang...] [--out DIR] [--full] [--append TSV]

`resolve` finds a recording for every form the catalog shows that a pack lacks (`audio_resolve.py`),
by the language's route (`audio_routes.py`), and fetches its transcode; nothing is judged or shipped —
`audio-catalog.py --fill` does that. One language at a time: Commons throttles parallel fetches.
"""
import audio_env  # noqa: F401 — before numpy: re-runs under the repo .venv
import argparse
import sys

import audio_resolve
from audio_routes import KINDS, ROUTES


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest='command', required=True)
    resolve = commands.add_parser('resolve', help='resolve what a pack lacks and fetch it')
    resolve.add_argument('targets', nargs='+', metavar='KIND|LANG',
                         help='kinds (%s) and languages (all with a route if none)' % ', '.join(KINDS))
    resolve.add_argument('--out', help='the pack directory, for one kind of one language')
    resolve.add_argument('--full', action='store_true', help='re-resolve every form, not only what the pack lacks')
    resolve.add_argument('--append', metavar='TSV', help='add hand-named rows `text file [author]` instead')
    args = parser.parse_args()

    kinds = [t for t in args.targets if t in KINDS]
    langs = [t for t in args.targets if t not in KINDS] or sorted(ROUTES)
    unknown = [lang for lang in langs if lang not in ROUTES]
    if not kinds or unknown:
        parser.error('no kind given' if not kinds else 'no route for %s' % ', '.join(unknown))
    if args.out and (len(kinds) > 1 or len(langs) > 1):
        parser.error('--out names one pack: one kind, one language')
    failed = 0
    for lang in langs:
        for kind in kinds:
            print('=== %s %s ===' % (lang, kind), file=sys.stderr)
            failed += (audio_resolve.append(lang, kind, args.append) if args.append
                       else audio_resolve.resolve(lang, kind, args.out, args.full))
    if failed:
        sys.exit('%d transcodes not fetched — rerun to retry' % failed)


if __name__ == '__main__':
    main()
