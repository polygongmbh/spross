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
# 500 Hz at 24 dB/oct is a phone speaker, not a compromise: a real iPhone gives up around
# 500 Hz and rolls off steeply, so the lens rolls off steeply too. That only became possible
# once the phone plane split from the full-range one (audio-catalog.py's TWO PLANES) — the
# route split means this filter is never heard on headphones, so nothing here has to be
# forgiven elsewhere. A bassy word still plays the level a phone radiates of it, and a
# bright one the level a phone radiates of it; the player corrects each to the same number.
SPEAKER_LENS = 'highpass=f=500:poles=2,highpass=f=500:poles=2'

INTEGRATED = re.compile(r'^\s+I:\s+(-?[\d.]+|-inf) LUFS', re.M)
SILENCE_START = re.compile(r'silence_start:\s*(-?[\d.]+)')
SILENCE_END = re.compile(r'silence_end:\s*(-?[\d.]+)')
PEAK_LEVEL = re.compile(r'Peak level dB:\s*(-?[\d.]+|-inf)')
NOISE_FLOOR = re.compile(r'Noise floor dB:\s*(-?[\d.]+|-inf)')


def version(binary):
    """`ffmpeg version 8.1.2` — numbers only reproduce against the build that measured them."""
    banner = subprocess.run([binary, '-version'], capture_output=True, text=True, check=True)
    return ' '.join(banner.stdout.split('\n', 1)[0].split()[:3])


def measure(binary, path):
    """(integrated LUFS, the same through SPEAKER_LENS, leading silence in seconds, sample
    peak dBFS, noise floor dBFS); a silent file measures None for the levels.

    Two decodes: the plain one below, and one more through the lens — which is what the
    gain is actually derived from, the flat number staying as the figure the packs are
    described by. Two passes rather than a split graph because an `I:` line says which
    loudness it is only by which instance printed it.

    One decode, three filters. `ebur128` reports EBU R128 INTEGRATED loudness, which is
    gated — the pause a single word sits in does not drag its level down, so two packs can
    be compared on it. `silencedetect` opens a run at 0 exactly when the file starts with
    dead air; a first run starting anywhere else means it starts speaking. `astats` reports
    the loudest DECODED sample, which is the ceiling a player's gain stage runs into — the
    mp3's own headroom says nothing, the decoder's output is what gets amplified — and the
    NOISE FLOOR, which is what stands between a word and the hiss under it.
    """
    run = subprocess.run(
        [binary, '-hide_banner', '-nostats', '-i', path, '-af',
         'silencedetect=noise=%ddB:d=%s,'
         'astats=measure_overall=Peak_level+Noise_floor:measure_perchannel=none,'
         'ebur128=peak=none' % (SILENCE_THRESHOLD_DB, SILENCE_MIN_SECONDS), '-f', 'null', '-'],
        capture_output=True, text=True)
    if run.returncode != 0:
        raise RuntimeError('%s: ffmpeg failed\n%s' % (path, run.stderr[-800:]))
    loudness = INTEGRATED.findall(run.stderr)
    peak = PEAK_LEVEL.findall(run.stderr)
    floor = NOISE_FLOOR.findall(run.stderr)
    start = SILENCE_START.search(run.stderr)
    end = SILENCE_END.search(run.stderr)
    opens_silent = start is not None and abs(float(start.group(1))) < 1e-6
    return (
        float(loudness[-1]) if loudness and loudness[-1] != '-inf' else None,
        lensed_loudness(binary, path),
        float(end.group(1)) if opens_silent and end else 0.0,
        float(peak[-1]) if peak and peak[-1] != '-inf' else None,
        float(floor[-1]) if floor and floor[-1] != '-inf' else None,
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


def measure_all(binary, paths):
    """{path: [measure]} for many files at once — keyed by path, so the order never leaks in."""
    with concurrent.futures.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        return dict(zip(paths, pool.map(lambda path: measure(binary, path), paths)))
