#!/usr/bin/env python3
"""Rate a listening set by ear, one key per take, straight into `docs/audio-verdicts.tsv`.

    scripts/audio-listen.py <set.tsv>

A set is a TSV beside its mp3s (or naming absolute paths) with the columns `file lang word
speaker source sha256 mos` and an optional `context`; the set's folder name stands in for a
missing context. Each take plays on arrival:

    1-5  rate it bad, mediocre, okay, good, great — and move on
    r    play it again (space too)
    d    tag a defect first (noisy, reverb, hum, one-sided, breathy, extra words …)
    n    add a note first
    s    skip it
    q    stop; a later run resumes at the first take not yet rated in this context

Sets stay small, about 15 takes, so one sitting finishes one.
"""
import csv
import datetime
import os
import subprocess
import sys
import termios
import tty

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VERDICTS = os.path.join(ROOT, 'docs', 'audio-verdicts.tsv')
RATINGS = {'1': 'bad', '2': 'mediocre', '3': 'okay', '4': 'good', '5': 'great'}


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
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    path = sys.argv[1]
    base = os.path.dirname(os.path.abspath(path))
    rows = list(csv.DictReader(open(path, encoding='utf-8'), delimiter='\t'))
    default_context = os.path.basename(base)
    with open(VERDICTS, encoding='utf-8') as handle:
        fields = handle.readline().rstrip('\n').split('\t')
        done = {(row['sha256'], row['context'])
                for row in csv.DictReader(handle, fieldnames=fields, delimiter='\t')}
    todo = [row for row in rows
            if (row['sha256'], row.get('context') or default_context) not in done]
    print('%d of %d takes to rate — 1-5 rate, r replay, d defect, n note, s skip, q quit\n'
          % (len(todo), len(rows)))
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
