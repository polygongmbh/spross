#!/usr/bin/env python3
"""ffmpeg measurement of the shipped recordings: how loud they are, when they start, how
close to full scale they already run.

The mechanics behind `audio-catalog.py`'s analysis stage, which turns these numbers into
the manifest's `gain`/`lead`. NOTHING here writes audio — every run decodes to the null
muxer, so the mp3 that ships is byte-for-byte the mp3 that was measured, the "unmodified
Commons transcode" claim stays true, and the sha256 gate keeps its meaning.
"""
import concurrent.futures
import re
import subprocess

import numpy as np

# -40 dB is where a Commons transcode's room tone sits below its speech; 20 ms is long
# enough that a plosive's own gap is not read as silence, short enough to catch a lead-in.
SILENCE_THRESHOLD_DB = -40
SILENCE_MIN_SECONDS = 0.02

# why: the catalog is ~1100 files and each pass is a full decode — measuring them ten at a
# time keeps the analysis stage off the converter's wall clock (seconds, not minutes).
WORKERS = 10

# The SPEAKER LENS: the same loudness, measured through what a phone can actually radiate.
# R128 weights a word's energy at 150 Hz nearly like its energy at 2 kHz; a phone speaker
# reproduces almost none of the first. So two words that measure level flat are heard many
# dB apart on the device — sw `karibu`, all sharp open vowels, against `nakupenda`, built
# on nasals and back vowels: 0.1 dB apart flat, and 16 apart through this. Which is the
# whole of what "the words are not balanced" turned out to be.
#
# The curve is a phone's roll-off, gradual rather than a hard high-pass — a real speaker
# loses low end across the low-mids, not at one frequency: −20 dB below 450 Hz, −8 dB at
# 800 Hz, flat past 1.2 kHz. A hard high-pass at 500 Hz still treated 800 Hz as full
# output, and the word that lived there (a low, bassy voice like Jeuwre's) came back dull
# and under-levelled. This only became possible once the phone plane split from the
# full-range one (audio-catalog.py's TWO PLANES): the route split means the lens is never
# heard on headphones, so nothing here has to be forgiven elsewhere.
#
# firequalizer is an FIR with `gain_entry` anchor points and linear interpolation between
# them; the anchors are the curve, so a future phone with a different response is a one-line
# change. The gains are dB at 0 dB reference, so the flat region above 1.2 kHz passes
# unchanged and the whole thing is a measurement weight, never an edit to the bytes.
SPEAKER_LENS = (
    r'firequalizer=gain_entry=entry(450\,-20)\;entry(800\,-8)\;entry(1200\,0)'
    r':gain=gain_interpolate(f)'
)

INTEGRATED = re.compile(r'^\s+I:\s+(-?[\d.]+|-inf) LUFS', re.M)
SILENCE_START = re.compile(r'silence_start:\s*(-?[\d.]+)')
SILENCE_END = re.compile(r'silence_end:\s*(-?[\d.]+)')
PEAK_LEVEL = re.compile(r'Peak level dB:\s*(-?[\d.]+|-inf)')

# NOISE is estimated by minimum statistics: per frequency bin, the quietest moment across
# short frames. Speech never fills every band at once, while hiss sits under all of them the
# whole time, so this finds the noise even in a word trimmed to the file's edges — where the
# quietest WINDOW, which is what ffmpeg's astats calls the noise floor, lands inside the
# word and reads its own dynamics as hiss. That misread refused recordings a listener heard
# as clean. Settled against a listening pass: three files heard as noisy measured 38–39,
# four heard as clean 44–59, with these exact parameters.
NOISE_RATE = 22050
NOISE_FRAME = 256
NOISE_HOP = 64
NOISE_PERCENTILE = 5
NOISE_BAND_HZ = (300, 4000)


def version(binary):
    """`ffmpeg version 8.1.2` — numbers only reproduce against the build that measured them."""
    banner = subprocess.run([binary, '-version'], capture_output=True, text=True, check=True)
    return ' '.join(banner.stdout.split('\n', 1)[0].split()[:3])


def measure(binary, path):
    """(integrated LUFS, the same through SPEAKER_LENS, leading silence in seconds, sample
    peak dBFS, dB of speech above its noise); a silent file measures None for the levels.

    Two decodes: the plain one below, and one more through the lens — which is what the
    gain is actually derived from, the flat number staying as the figure the packs are
    described by. Two passes rather than a split graph because an `I:` line says which
    loudness it is only by which instance printed it.

    One decode, three filters. `ebur128` reports EBU R128 INTEGRATED loudness, which is
    gated — the pause a single word sits in does not drag its level down, so two packs can
    be compared on it. `silencedetect` opens a run at 0 exactly when the file starts with
    dead air; a first run starting anywhere else means it starts speaking. `astats` reports
    the loudest DECODED sample, which is the ceiling a player's gain stage runs into — the
    mp3's own headroom says nothing, the decoder's output is what gets amplified. The noise
    is a decode of its own (`noise_margin`).
    """
    run = subprocess.run(
        [binary, '-hide_banner', '-nostats', '-i', path, '-af',
         'silencedetect=noise=%ddB:d=%s,'
         'astats=measure_overall=Peak_level:measure_perchannel=none,'
         'ebur128=peak=none' % (SILENCE_THRESHOLD_DB, SILENCE_MIN_SECONDS), '-f', 'null', '-'],
        capture_output=True, text=True)
    if run.returncode != 0:
        raise RuntimeError('%s: ffmpeg failed\n%s' % (path, run.stderr[-800:]))
    loudness = INTEGRATED.findall(run.stderr)
    peak = PEAK_LEVEL.findall(run.stderr)
    start = SILENCE_START.search(run.stderr)
    end = SILENCE_END.search(run.stderr)
    opens_silent = start is not None and abs(float(start.group(1))) < 1e-6
    return (
        float(loudness[-1]) if loudness and loudness[-1] != '-inf' else None,
        lensed_loudness(binary, path),
        float(end.group(1)) if opens_silent and end else 0.0,
        float(peak[-1]) if peak and peak[-1] != '-inf' else None,
        noise_margin(binary, path),
    )


def lensed_loudness(binary, path):
    """Integrated loudness of what a phone speaker can radiate of `path` (SPEAKER_LENS)."""
    run = subprocess.run(
        [binary, '-hide_banner', '-nostats', '-i', path, '-af',
         '%s,ebur128=peak=none' % SPEAKER_LENS, '-f', 'null', '-'],
        capture_output=True, text=True)
    if run.returncode != 0:
        raise RuntimeError('%s: ffmpeg failed\n%s' % (path, run.stderr[-800:]))
    lensed = INTEGRATED.findall(run.stderr)
    return float(lensed[-1]) if lensed and lensed[-1] != '-inf' else None


def noise_margin(binary, path):
    """How far the loudest frame stands above the noise under the word, in dB (see NOISE_*);
    None where the estimate finds no noise at all — digital silence, the cleanest there is."""
    raw = subprocess.run(
        [binary, '-v', 'error', '-i', path, '-ac', '1', '-ar', str(NOISE_RATE), '-f', 'f32le', '-'],
        capture_output=True, check=True).stdout
    x = np.frombuffer(raw, dtype=np.float32)
    if len(x) < NOISE_FRAME:
        return None
    frames = np.lib.stride_tricks.sliding_window_view(x, NOISE_FRAME)[::NOISE_HOP]
    power = np.abs(np.fft.rfft(frames * np.hanning(NOISE_FRAME), axis=1)) ** 2
    hz = np.fft.rfftfreq(NOISE_FRAME, 1 / NOISE_RATE)
    power = power[:, (hz >= NOISE_BAND_HZ[0]) & (hz <= NOISE_BAND_HZ[1])]
    noise = np.percentile(power, NOISE_PERCENTILE, axis=0).sum()
    loudest = power.sum(axis=1).max()
    if noise <= 0 or loudest <= 0:
        return None
    return float(10 * np.log10(loudest / noise))


def measure_all(binary, paths):
    """{path: [measure]} for many files at once — keyed by path, so the order never leaks in."""
    with concurrent.futures.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        return dict(zip(paths, pool.map(lambda path: measure(binary, path), paths)))
