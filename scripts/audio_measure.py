#!/usr/bin/env python3
"""ffmpeg measurement of the shipped recordings: how loud they are, when they start, how
close to full scale they already run.

The mechanics behind `audio-catalog.py`'s analysis stage, which turns these numbers into
the manifest's `gain`/`lead`. NOTHING here writes audio — every run decodes to the null
muxer, so the mp3 that ships is byte-for-byte the mp3 that was measured, the "unmodified
Commons transcode" claim stays true, and the sha256 gate keeps its meaning.
"""
import concurrent.futures
import os
import re
import subprocess
import sys

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

# NOISE — where a playback gate sits, never a verdict on the take (that is MOS) — is
# estimated by minimum statistics: per frequency bin, the quietest moment across short
# frames. Speech never fills every band at once, while hiss sits under all of them the whole
# time, so this finds the noise even in a word trimmed to the file's edges, where the
# quietest WINDOW (ffmpeg astats' noise floor) lands inside the word itself.
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
    peak dBFS, dB of speech above its noise, loudest frame RMS dBFS, MOS); a silent file
    measures None for the levels.

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
    and the MOS are decodes of their own (`noise`, `mos`).
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
        *noise(binary, path),
        mos(binary, path),
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


def noise(binary, path):
    """(dB the loudest frame stands above the noise, its RMS in dBFS) — one decode for both,
    since the noise LEVEL a playback gate sits on is the loudest frame less the margin;
    (None, None) where no noise was found, digital silence."""
    raw = subprocess.run(
        [binary, '-v', 'error', '-i', path, '-ac', '1', '-ar', str(NOISE_RATE), '-f', 'f32le', '-'],
        capture_output=True, check=True).stdout
    x = np.frombuffer(raw, dtype=np.float32)
    if len(x) < NOISE_FRAME:
        return None, None
    frames = np.lib.stride_tricks.sliding_window_view(x, NOISE_FRAME)[::NOISE_HOP]
    rms = np.sqrt((frames.astype(np.float64) ** 2).mean(axis=1)).max()
    power = np.abs(np.fft.rfft(frames * np.hanning(NOISE_FRAME), axis=1)) ** 2
    hz = np.fft.rfftfreq(NOISE_FRAME, 1 / NOISE_RATE)
    power = power[:, (hz >= NOISE_BAND_HZ[0]) & (hz <= NOISE_BAND_HZ[1])]
    noise = np.percentile(power, NOISE_PERCENTILE, axis=0).sum()
    loudest = power.sum(axis=1).max()
    if noise <= 0 or loudest <= 0 or rms <= 0:
        return None, None
    return float(10 * np.log10(loudest / noise)), float(20 * np.log10(rms))


# MOS: how good a take sounds, as the DNSMOS P.835 overall score (Microsoft's no-reference
# speech-quality model, MIT, `dnsmos/`): 1 to 5, like a listener's mean opinion score.
# Of every measure tried against a listener's verdicts (`docs/audio-verdicts.tsv`), this is
# the one that ranks every voice where the ear did (`docs/2026-09-28-audio-quality-tools.md`);
# single files still stray by a few tenths, so it compares takes and refuses the clearly bad.
# The model scores 9.01 s windows, so a shorter take is doubled onto itself until it fills
# one — the reference implementation's own padding, kept so the scores match it.
MOS_MODEL = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'dnsmos', 'sig_bak_ovr.onnx')
MOS_RATE = 16000
MOS_WINDOW_SECONDS = 9.01
MOS_OVERALL = np.poly1d([-0.06766283, 1.11546468, 0.04602535])
_mos_session = None


def _decode(binary, path, rate):
    raw = subprocess.run([binary, '-v', 'error', '-i', path, '-ac', '1', '-ar', str(rate),
                          '-f', 'f32le', '-'], capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.float32)


def _session():
    global _mos_session
    if _mos_session is None:
        try:
            import onnxruntime
        except ImportError:
            sys.exit('audio_measure: MOS needs onnxruntime — '
                     'python3 -m pip install --user --break-system-packages onnxruntime')
        options = onnxruntime.SessionOptions()
        # why: files are scored WORKERS at a time, one core each, instead of one file at a
        # time across every core — the model is too small to spread a single window well.
        options.intra_op_num_threads = 1
        _mos_session = onnxruntime.InferenceSession(MOS_MODEL, options)
    return _mos_session


def mos(binary, path):
    """DNSMOS overall score of one file (see MOS), higher is better; None for an empty decode."""
    x = _decode(binary, path, MOS_RATE)
    if not len(x):
        return None
    window = int(MOS_WINDOW_SECONDS * MOS_RATE)
    while len(x) < window:
        x = np.append(x, x)
    session, scores = _session(), []
    for hop in range(int(np.floor(len(x) / MOS_RATE) - MOS_WINDOW_SECONDS) + 1):
        segment = x[hop * MOS_RATE:hop * MOS_RATE + window]
        if len(segment) < window:
            continue
        _, _, overall = session.run(None, {'input_1': segment[np.newaxis, :]})[0][0]
        scores.append(MOS_OVERALL(overall))
    return float(np.mean(scores)) if scores else None


def measure_all(binary, paths):
    """{path: [measure]} for many files at once — keyed by path, so the order never leaks in."""
    with concurrent.futures.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        return dict(zip(paths, pool.map(lambda path: measure(binary, path), paths)))
