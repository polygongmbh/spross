"""Where an `audio-packs.py` run writes what it prints: `$W/logs/<date>-<command>/<lang>-<section>.log`
per pack and one `summary.log` line per finished pack, while the console sees all of it too."""
import contextlib
import datetime
import os
import sys

from audio_routes import WORKSPACE


class Tee:
    def __init__(self, *streams):
        self.streams = streams

    def write(self, text):
        for stream in self.streams:
            stream.write(text)
            stream.flush()
        return len(text)

    def flush(self):
        for stream in self.streams:
            stream.flush()


class RunLog:
    """One run's log directory; [packs] are the `(lang, section)` it will run, created up front
    so the one `tail -f` line printed first follows every one of them."""

    def __init__(self, command, packs):
        self.dir = os.path.join(WORKSPACE, 'logs', '%s-%s' % (datetime.date.today().isoformat(), command))
        os.makedirs(self.dir, exist_ok=True)
        for lang, section in packs:
            open(self.path(lang, section), 'a').close()
        print('tail -f %s/*.log' % self.dir, flush=True)

    def path(self, lang, section):
        return os.path.join(self.dir, '%s-%s.log' % (lang, section))

    def summary(self, line):
        with open(os.path.join(self.dir, 'summary.log'), 'a', encoding='utf-8') as f:
            f.write('%s %s\n' % (datetime.datetime.now().strftime('%H:%M'), line))

    @contextlib.contextmanager
    def pack(self, lang, section):
        """Everything printed meanwhile goes to the console and to this pack's log."""
        out, err = sys.stdout, sys.stderr
        with open(self.path(lang, section), 'a', encoding='utf-8') as log:
            sys.stdout, sys.stderr = Tee(out, log), Tee(err, log)
            try:
                yield
            finally:
                sys.stdout, sys.stderr = out, err
