#!/usr/bin/env python3
"""Rates listening sets by ear into `docs/audio-verdicts.tsv`, and draws new ones."""
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
# (key, in the legend, in --help)
KEYS = [
    ('1-5', '1 bad  2 mediocre  3 okay  4 good  5 great', 'rate it bad, mediocre, okay, good, great — and move on'),
    ('r', 'r replay', 'play it again (space too)'),
    ('d', 'd defect', 'tag a defect first (noisy, reverb, hum, one-sided, breathy, extra words …)'),
    ('n', 'n note', 'add a note first'),
    ('s', 's skip', 'skip it'),
    ('b', 'b back', 'go back one row, withdrawing its rating — across runs too'),
    ('q', 'q quit', 'stop; a later run resumes at the first take not yet rated in this context'),
]


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


def from_voices(shipped, heard):
    """Each voice's lowest scorer, for every voice no take of which was heard in its language, the
    voices shipping most first."""
    voices = {}
    for take in from_shipped(shipped):
        if (take['lang'], take['speaker']) not in heard:
            voices.setdefault((take['lang'], take['speaker']), []).append(take)
    for held in voices.values():
        held[0]['why'] = 'voice of %d, %s' % (len(held), held[0]['why'])
    return [held[0] for held in sorted(voices.values(), key=lambda held: (-len(held), held[0]['lang']))]


def sample(argv):
    parser = argparse.ArgumentParser(
        prog='audio-listen.py sample',
        description='Writes a listening set to ../data/review/<date>-NAME/ out of takes nobody rated yet.')
    parser.add_argument('name')
    parser.add_argument('--from', dest='source', choices=('shipped', 'journal', 'voices'), required=True,
                        help='shipped: the lowest scorers of what ships; journal: the swaps staged or shipped '
                             '($W/staged.tsv, $W/shipped.tsv) most likely to be wrong, a voice new to its language '
                             'first, then the smallest gains; voices: one take per voice nobody heard yet in its '
                             'language, its lowest scorer, the voices shipping most first')
    parser.add_argument('--lang')
    parser.add_argument('--section')
    parser.add_argument('--voice')
    parser.add_argument('--mos-below', type=float, metavar='MOS')
    parser.add_argument('-n', type=int, default=15,
                        help='takes in the set (default 15); out of shipped or journal takes from more than one '
                             'language, at most %d come from each' % PER_LANGUAGE)
    args = parser.parse_args(argv)
    verdicts = rows(VERDICTS)
    heard = {row['sha256'] for row in verdicts}
    shipped = shipped_entries()
    drawn = from_voices(shipped, {(row['lang'], row['speaker']) for row in verdicts}) \
        if args.source == 'voices' else (from_shipped if args.source == 'shipped' else from_journal)(shipped)
    takes = [take for take in drawn
             if take['sha256'] not in heard
             and (not args.lang or take['lang'] == args.lang)
             and (not args.section or take['section'] == args.section)
             and (not args.voice or take['speaker'] == args.voice)
             and (args.mos_below is None or (take['mos'] is not None and take['mos'] < args.mos_below))]
    capped = args.source != 'voices' and len({take['lang'] for take in takes}) > 1
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


def withdraw(sha256, context):
    """Drops every verdict on [sha256] in [context]."""
    with open(VERDICTS, encoding='utf-8', newline='') as handle:
        lines = handle.readlines()
    kept = lines[:1] + [line for line in lines[1:]
                        if (lambda fields: (fields[5], fields[-1]))(next(csv.reader([line], delimiter='\t')))
                        != (sha256, context)]
    with open(VERDICTS, 'w', encoding='utf-8', newline='') as handle:
        handle.writelines(kept)


def named(rows, wanted):
    """Indices of the rows [wanted] names: a row number, else every row whose word contains it."""
    if wanted.isdigit():
        return [int(wanted) - 1] if 0 < int(wanted) <= len(rows) else []
    return [at for at, row in enumerate(rows)
            if wanted.lower() in row['word'].lower() or wanted == row['file']]


def main():
    if len(sys.argv) > 1 and sys.argv[1] == 'sample':
        return sample(sys.argv[2:])
    parser = argparse.ArgumentParser(
        prog='audio-listen.py', formatter_class=argparse.RawDescriptionHelpFormatter,
        description='Plays each take of a set and rates it with one key. A new rating replaces the take\'s '
                    'earlier one in its context. `audio-listen.py sample -h` draws a new set.',
        epilog='keys, each take playing on arrival:\n' + '\n'.join('  %-4s %s' % (key, text) for key, _, text in KEYS))
    parser.add_argument('set', help='a TSV beside its mp3s (or naming absolute paths), columns file lang word '
                                    'speaker source sha256 mos and an optional context (default: its folder name)')
    parser.add_argument('row', nargs='?', help='re-rate just the takes it names, rated already or not: a row number '
                                               'as the prompt shows it, else every take whose word contains it')
    args = parser.parse_args()
    path = args.set
    base = os.path.dirname(os.path.abspath(path))
    listed = list(csv.DictReader(open(path, encoding='utf-8'), delimiter='\t'))
    default_context = os.path.basename(base)
    context = lambda row: row.get('context') or default_context
    done = {(row['sha256'], row['context']) for row in rows(VERDICTS)}
    rated = lambda row: (row['sha256'], context(row)) in done
    picking = args.row is not None
    if picking:
        picked = named(listed, args.row)
        if not picked:
            sys.exit('%s names no row in %s' % (args.row, path))
    else:
        picked = list(range(len(listed)))

    def onward(at):
        """The next position to play: every pick, else the next take not rated yet."""
        while at < len(picked) and not picking and rated(listed[picked[at]]):
            at += 1
        return at

    print('%d of %d takes to rate\n  %s\n'
          % (len(picked) if picking else sum(1 for row in listed if not rated(row)), len(listed),
             KEYS[0][1] + '  ·  ' + '  '.join(legend for _, legend, _ in KEYS[1:])))
    player = None
    at = onward(0)
    while at < len(picked):
        row = listed[picked[at]]
        audio = row['file'] if os.path.isabs(row['file']) else os.path.join(base, row['file'])
        defect = note = ''
        print('%2d/%d  %s  %s' % (picked[at] + 1, len(listed), row['lang'], row['word']), end='  ', flush=True)
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
                at = onward(at + 1)
                break
            elif pressed == 'b':
                if at == 0:
                    continue
                print('back')
                at -= 1
                previous = listed[picked[at]]
                if rated(previous):
                    withdraw(previous['sha256'], context(previous))
                    done.discard((previous['sha256'], context(previous)))
                break
            elif pressed == 'q':
                player.terminate()
                print('stopped')
                return
            elif pressed in RATINGS:
                print(RATINGS[pressed] + (' (%s)' % defect if defect else ''))
                if rated(row):
                    withdraw(row['sha256'], context(row))
                with open(VERDICTS, 'a', encoding='utf-8', newline='') as handle:
                    csv.writer(handle, delimiter='\t', lineterminator='\n').writerow([
                        datetime.date.today().isoformat(), row['lang'], row['word'],
                        row['speaker'], row['source'], row['sha256'], RATINGS[pressed], defect,
                        note, row.get('mos', ''), context(row)])
                done.add((row['sha256'], context(row)))
                at = onward(at + 1)
                break
    if player:
        player.terminate()
    print('\nall rated')


if __name__ == '__main__':
    main()
