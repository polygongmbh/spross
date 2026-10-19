#!/usr/bin/env python3
"""Rate a listening set by ear, one key per take, straight into `docs/audio-verdicts.tsv`.

    scripts/audio-listen.py <set.tsv> [FILE]
    scripts/audio-listen.py sample NAME --from shipped|journal [--lang L] [--section S] [--voice V]
                                        [--mos-below X] [-n 15]

A set is a TSV beside its mp3s (or naming absolute paths), columns `file lang word speaker
source sha256 mos` and an optional `context` (default: the set's folder name). [FILE] re-rates
just that one take, rated already or not. Each take plays on arrival:

    1-5  rate it bad, mediocre, okay, good, great — and move on
    r    play it again (space too)
    d    tag a defect first (noisy, reverb, hum, one-sided, breathy, extra words …)
    n    add a note first
    s    skip it
    q    stop; a later run resumes at the first take not yet rated in this context

`sample` writes one to `../data/review/<date>-NAME/` out of takes nobody rated yet: `--from shipped`
the lowest scorers of what ships, `--from journal` the swaps staged or shipped (`$W/staged.tsv`,
`$W/shipped.tsv`) most likely to be wrong — a voice new to its language first, then the smallest
gains. With more than one language in play, at most three takes come from each.
"""
import argparse
import csv
import datetime
import json
import os
import shutil
import subprocess
import sys
import termios
import tty

from audio_gates import takes
from audio_routes import WORKSPACE
from audio_voices import ESTABLISHED_TAKES

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VERDICTS = os.path.join(ROOT, 'docs', 'audio-verdicts.tsv')
RATINGS = {'1': 'bad', '2': 'mediocre', '3': 'okay', '4': 'good', '5': 'great'}
AUDIO = os.path.join(ROOT, 'catalog', 'audio')
REVIEW = os.path.normpath(os.path.join(ROOT, '..', 'data', 'review'))
PER_LANGUAGE = 3


def rows(path):
    if not os.path.exists(path):
        return []
    with open(path, encoding='utf-8', newline='') as handle:
        return list(csv.DictReader(handle, delimiter='\t'))


def shipped_entries():
    """{lang: [(section, key, take)]} over every take of every shipped manifest."""
    out = {}
    for lang in sorted(os.listdir(AUDIO)):
        path = os.path.join(AUDIO, lang, 'manifest.json')
        if os.path.isfile(path):
            with open(path, encoding='utf-8') as handle:
                manifest = json.load(handle)
            out[lang] = [(section, key, take) for section, entries in manifest.items()
                         if section not in ('language', 'authors', 'licenses')
                         for key, item in entries.items() for take in takes(item)]
    return out


def from_shipped(shipped):
    """The takes that ship, lowest score first."""
    takes = [dict(lang=lang, section=section, word=key, speaker=item['author'], source=item['source'],
                  sha256=item['sha256'], mos=item.get('mos'), path=os.path.join(AUDIO, lang, item['file']),
                  why='mos %s' % item.get('mos', '-'))
             for lang, entries in shipped.items() for section, key, item in entries]
    return sorted(takes, key=lambda take: take['mos'] if take['mos'] is not None else 9)


def from_journal(shipped):
    """The swaps staged or shipped whose bytes are at hand, a new voice first, then the smallest gain."""
    weight = {lang: {} for lang in shipped}
    for lang, entries in shipped.items():
        for _, _, item in entries:
            weight[lang][item['author']] = weight[lang].get(item['author'], 0) + 1
    at = {item['sha256']: os.path.join(AUDIO, lang, item['file'])
          for lang, entries in shipped.items() for _, _, item in entries}
    takes = []
    for row in rows(os.path.join(WORKSPACE, 'shipped.tsv')) + rows(os.path.join(WORKSPACE, 'staged.tsv')):
        staged = os.path.join(WORKSPACE, 'staged', '%s.mp3' % row['to_sha256'])
        path = at.get(row['to_sha256']) or (staged if os.path.exists(staged) else None)
        if not path:
            continue
        mos_to = float(row['mos_to']) if row['mos_to'] else None
        gain = mos_to - float(row['mos_from']) if mos_to is not None and row['mos_from'] else 0.0
        new = weight.get(row['lang'], {}).get(row['author'], 0) < ESTABLISHED_TAKES
        takes.append(dict(lang=row['lang'], section=row['section'], word=row['key'], speaker=row['author'],
                          source=row['to_source'], sha256=row['to_sha256'], mos=mos_to, path=path, new=new,
                          gain=gain, why='%s %+.2f%s' % (row['picker'], gain, ', new voice' if new else '')))
    return sorted(takes, key=lambda take: (not take['new'], take['gain']))


def sample(argv):
    parser = argparse.ArgumentParser(prog='audio-listen.py sample', description='write a listening set')
    parser.add_argument('name')
    parser.add_argument('--from', dest='source', choices=('shipped', 'journal'), required=True)
    parser.add_argument('--lang')
    parser.add_argument('--section')
    parser.add_argument('--voice')
    parser.add_argument('--mos-below', type=float)
    parser.add_argument('-n', type=int, default=15)
    args = parser.parse_args(argv)
    with open(VERDICTS, encoding='utf-8') as handle:
        heard = {row['sha256'] for row in csv.DictReader(handle, delimiter='\t')}
    shipped = shipped_entries()
    takes = [take for take in (from_shipped if args.source == 'shipped' else from_journal)(shipped)
             if take['sha256'] not in heard
             and (not args.lang or take['lang'] == args.lang)
             and (not args.section or take['section'] == args.section)
             and (not args.voice or take['speaker'] == args.voice)
             and (args.mos_below is None or (take['mos'] is not None and take['mos'] < args.mos_below))]
    capped = len({take['lang'] for take in takes}) > 1
    picked, per, seen = [], {}, set()
    for take in takes:
        if len(picked) == args.n:
            break
        if take['sha256'] in seen or (capped and per.get(take['lang'], 0) >= PER_LANGUAGE):
            continue
        seen.add(take['sha256'])
        per[take['lang']] = per.get(take['lang'], 0) + 1
        picked.append(take)
    if not picked:
        sys.exit('nothing unrated matches')
    out = os.path.join(REVIEW, '%s-%s' % (datetime.date.today().isoformat(), args.name))
    if os.path.exists(os.path.join(out, 'set.tsv')):
        sys.exit('%s already holds a set' % out)
    os.makedirs(out, exist_ok=True)
    context = '%s %s' % (datetime.date.today().isoformat(), args.name.replace('-', ' '))
    with open(os.path.join(out, 'set.tsv'), 'w', encoding='utf-8', newline='') as handle:
        writer = csv.writer(handle, delimiter='\t', lineterminator='\n')
        writer.writerow(['file', 'lang', 'word', 'speaker', 'source', 'sha256', 'mos', 'context'])
        for number, take in enumerate(picked, 1):
            name = '%02d-%s.mp3' % (number, take['lang'])
            shutil.copyfile(take['path'], os.path.join(out, name))
            writer.writerow([name, take['lang'], take['word'], take['speaker'], take['source'], take['sha256'],
                             '' if take['mos'] is None else '%.2f' % take['mos'],
                             '%s (%s, %s)' % (context, take['section'], take['why'])])
            print('%02d %s %-20s %-22s %s' % (number, take['lang'], take['word'][:20], take['speaker'][:22], take['why']))
    print('scripts/audio-listen.py %s' % os.path.join(out, 'set.tsv'))


def key():
    fd = sys.stdin.fileno()
    saved = termios.tcgetattr(fd)
    try:
        tty.setcbreak(fd)
        return sys.stdin.read(1)
    finally:
        termios.tcsetattr(fd, termios.TCSADRAIN, saved)


def play(path, player):
    if player:
        player.terminate()
    return subprocess.Popen(['afplay', path], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def main():
    if len(sys.argv) > 1 and sys.argv[1] == 'sample':
        return sample(sys.argv[2:])
    if len(sys.argv) not in (2, 3):
        sys.exit(__doc__)
    path = sys.argv[1]
    base = os.path.dirname(os.path.abspath(path))
    rows = list(csv.DictReader(open(path, encoding='utf-8'), delimiter='\t'))
    default_context = os.path.basename(base)
    if len(sys.argv) == 3:
        rerate = sys.argv[2]
        todo = [row for row in rows if row['file'] == rerate]
        if not todo:
            sys.exit('%s names no row in %s' % (rerate, path))
    else:
        with open(VERDICTS, encoding='utf-8') as handle:
            fields = handle.readline().rstrip('\n').split('\t')
            done = {(row['sha256'], row['context'])
                    for row in csv.DictReader(handle, fieldnames=fields, delimiter='\t')}
        todo = [row for row in rows
                if (row['sha256'], row.get('context') or default_context) not in done]
    print('%d of %d takes to rate\n  1 bad  2 mediocre  3 okay  4 good  5 great'
          '  ·  r replay  d defect  n note  s skip  q quit\n' % (len(todo), len(rows)))
    player = None
    for number, row in enumerate(todo, 1):
        audio = row['file'] if os.path.isabs(row['file']) else os.path.join(base, row['file'])
        defect = note = ''
        print('%2d/%d  %s  %s' % (number, len(todo), row['lang'], row['word']), end='  ', flush=True)
        player = play(audio, player)
        while True:
            pressed = key()
            if pressed in ('r', ' '):
                player = play(audio, player)
            elif pressed == 'd':
                defect = input('\n      defect: ').strip()
                print('      ', end='', flush=True)
            elif pressed == 'n':
                note = input('\n      note: ').strip()
                print('      ', end='', flush=True)
            elif pressed == 's':
                print('skipped')
                break
            elif pressed == 'q':
                player.terminate()
                print('stopped')
                return
            elif pressed in RATINGS:
                print(RATINGS[pressed] + (' (%s)' % defect if defect else ''))
                with open(VERDICTS, 'a', encoding='utf-8', newline='') as handle:
                    csv.writer(handle, delimiter='\t', lineterminator='\n').writerow([
                        datetime.date.today().isoformat(), row['lang'], row['word'],
                        row['speaker'], row['source'], row['sha256'], RATINGS[pressed], defect,
                        note, row.get('mos', ''), row.get('context') or default_context])
                break
    if player:
        player.terminate()
    print('\nall rated')


if __name__ == '__main__':
    main()
