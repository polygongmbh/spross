#!/usr/bin/env python3
"""ffmpeg measurement of the shipped recordings: how loud they are, where their speech starts and
ends, how close to full scale they already run.

The mechanics behind `audio-catalog.py`'s analysis stage, which turns these numbers into
the manifest's `gain`/`lead`/`end`. NOTHING here writes audio — every run decodes to the null
muxer, so the mp3 that ships is byte-for-byte the mp3 that was measured, the "unmodified
Commons transcode" claim stays true, and the sha256 gate keeps its meaning.
"""
import audio_env  # noqa: F401 — before numpy: re-runs under the repo .venv
import concurrent.futures
import os
import re
import subprocess
import sys

import numpy as np

# SPEECH SPAN — where a take's speech starts and ends, read off 10 ms frames' sample peaks.
# -40 dBFS is where a Commons transcode's room tone sits below its speech. A file whose edge
# holds a steady noise well under its loudest frame (it frigorifero: two seconds of hum at -24
# before the word) raises the bar to just over that noise instead. A loud frame with no other
# within ISOLATED_MS is a click, as is a group at the head under HEAD_CLICK_MS or at the tail under
# TAIL_CLICK_MS standing MERGE_MS or more off the speech: the head limit stays under the shortest
# word (it "i genitori"), the tail one can be wider because the player keeps time after the speech.
SPAN_FRAME_MS = 10
SPAN_FLOOR_DB = -40
SPAN_EDGE_MS = 300
SPAN_STEADY_DB = 6
SPAN_UNDER_LOUDEST_DB = 12
SPAN_OVER_NOISE_DB = 6
SPAN_MERGE_MS = 200
SPAN_ISOLATED_MS = 50
SPAN_HEAD_CLICK_MS = 40
SPAN_TAIL_CLICK_MS = 100
SPAN_RATE = 16000

# why: the catalog is ~1100 files and each pass is a full decode — measuring them ten at a
# time keeps the analysis stage off the converter's wall clock (seconds, not minutes).
WORKERS = 10

INTEGRATED = re.compile(r'^\s+I:\s+(-?[\d.]+|-inf) LUFS', re.M)
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
    """(integrated LUFS, `speech_span`, sample peak dBFS, dB of speech above its noise, loudest
    frame RMS dBFS, MOS); a silent file measures None for the levels.

    `ebur128` reports EBU R128 INTEGRATED loudness, which is gated — the pause a single word sits
    in does not drag its level down, so two packs can be compared on it. `astats` reports the
    loudest DECODED sample, which is the ceiling a player's gain stage runs into — the mp3's own
    headroom says nothing, the decoder's output is what gets amplified. The span, the noise and
    the MOS are decodes of their own.
    """
    run = subprocess.run(
        [binary, '-hide_banner', '-nostats', '-i', path, '-af',
         'astats=measure_overall=Peak_level:measure_perchannel=none,ebur128=peak=none', '-f', 'null', '-'],
        capture_output=True, text=True)
    if run.returncode != 0:
        raise RuntimeError('%s: ffmpeg failed\n%s' % (path, run.stderr[-800:]))
    loudness = INTEGRATED.findall(run.stderr)
    peak = PEAK_LEVEL.findall(run.stderr)
    return (
        float(loudness[-1]) if loudness and loudness[-1] != '-inf' else None,
        speech_span(binary, path),
        float(peak[-1]) if peak and peak[-1] != '-inf' else None,
        *noise(binary, path),
        mos(binary, path),
    )


def _span_bar(edge):
    """The level speech has to pass at one edge of a file, [edge] its frames read from that edge."""
    frames = SPAN_EDGE_MS // SPAN_FRAME_MS
    if len(edge) < frames:
        return SPAN_FLOOR_DB
    noise = np.median(edge[:frames])
    steady = np.percentile(edge[:frames], 75) - np.percentile(edge[:frames], 25) < SPAN_STEADY_DB
    if steady and edge.max() - noise >= SPAN_UNDER_LOUDEST_DB:
        return max(SPAN_FLOOR_DB, noise + SPAN_OVER_NOISE_DB)
    return SPAN_FLOOR_DB


def speech_span(binary, path):
    """(start, end, duration) in seconds: where the speech in `path` starts and ends, past clicks
    and edge noise (SPEECH SPAN), and the file's length; the whole file where nothing is loud."""
    raw = subprocess.run([binary, '-v', 'error', '-i', path, '-ac', '1', '-ar', str(SPAN_RATE), '-f', 'f32le', '-'],
                         capture_output=True, check=True).stdout
    width = SPAN_RATE * SPAN_FRAME_MS // 1000
    x = np.frombuffer(raw, dtype=np.float32)
    frames = x[:len(x) // width * width].reshape(-1, width).astype(np.float64)
    duration = len(x) / SPAN_RATE
    if not len(frames):
        return 0.0, duration, duration
    db = 20 * np.log10(np.abs(frames).max(axis=1) + 1e-10)
    middle = len(db) // 2
    loud = np.concatenate([db[:middle] > _span_bar(db), db[middle:] > _span_bar(db[::-1])])
    near = np.convolve(loud, np.ones(2 * (SPAN_ISOLATED_MS // SPAN_FRAME_MS) + 1), 'same')
    hits = np.flatnonzero(loud & (near > 1))
    if not len(hits):
        return 0.0, duration, duration
    groups = [[hits[0]]]
    for hit in hits[1:]:
        if (hit - groups[-1][-1]) * SPAN_FRAME_MS > SPAN_MERGE_MS:
            groups.append([])
        groups[-1].append(hit)
    while len(groups) > 1 and len(groups[0]) * SPAN_FRAME_MS < SPAN_HEAD_CLICK_MS:
        groups.pop(0)
    while len(groups) > 1 and len(groups[-1]) * SPAN_FRAME_MS < SPAN_TAIL_CLICK_MS:
        groups.pop()
    return (float(groups[0][0] * SPAN_FRAME_MS) / 1000, float((groups[-1][-1] + 1) * SPAN_FRAME_MS) / 1000, duration)


def peak_and_loudness(binary, path):
    """(sample peak dBFS, integrated LUFS) of `path` alone — `measure`'s two levels without
    its slower passes, for asking whether a take is squashed (`audio_voices.is_squashed`)."""
    run = subprocess.run(
        [binary, '-hide_banner', '-nostats', '-i', path, '-af',
         'astats=measure_overall=Peak_level:measure_perchannel=none,ebur128=peak=none',
         '-f', 'null', '-'],
        capture_output=True, text=True)
    if run.returncode != 0:
        raise RuntimeError('%s: ffmpeg failed\n%s' % (path, run.stderr[-800:]))
    loudness = INTEGRATED.findall(run.stderr)
    peak = PEAK_LEVEL.findall(run.stderr)
    return (float(peak[-1]) if peak and peak[-1] != '-inf' else None,
            float(loudness[-1]) if loudness and loudness[-1] != '-inf' else None)


# Four cascaded 2nd-order sections ≈ 48 dB/octave: a single `highpass` leaks so much of the band
# below it that a 7 kHz-lopped recording and a full-band one measure alike.
HIGHPASS_6K = ','.join(['highpass=f=6000:poles=2'] * 4)
RMS_LEVEL = re.compile(r'RMS level dB:\s*(-?[\d.]+)')


def brightness(binary, path):
    """dB the energy above 6 kHz stands under the file's own overall level, where sibilants and stop
    releases live — relative, so a quiet take is not mistaken for a dull one; None for silence."""
    def rms(filters):
        run = subprocess.run([binary, '-hide_banner', '-nostats', '-i', path, '-af', filters, '-f', 'null', '-'],
                             capture_output=True, text=True)
        found = RMS_LEVEL.findall(run.stderr)
        return float(found[-1]) if found else None

    overall = rms('astats=metadata=1:reset=0')
    high = rms(HIGHPASS_6K + ',astats=metadata=1:reset=0')
    return None if overall is None or high is None else high - overall


def side_balance(binary, path):
    """dB the louder side stands over the quieter one; 0.0 for a mono file. A take whose
    one side is near silent plays in one ear on headphones (`audio_voices.is_one_sided`)."""
    raw = subprocess.run([binary, '-v', 'error', '-i', path, '-ac', '2', '-f', 'f32le', '-'],
                         capture_output=True, check=True).stdout
    sides = np.frombuffer(raw, dtype=np.float32).reshape(-1, 2).astype(np.float64)
    if not len(sides):
        return 0.0
    left, right = (10 * np.log10(max((sides[:, i] ** 2).mean(), 1e-20)) for i in (0, 1))
    return float(abs(left - right))


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
    """{path: measure + (N5 phon,)} for many files at once — keyed by path, so the order never
    leaks in. The perceived loudness runs in processes: ISO 532-1's decay is a Python loop."""
    with concurrent.futures.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        measured = dict(zip(paths, pool.map(lambda path: measure(binary, path), paths)))
    with concurrent.futures.ProcessPoolExecutor(max_workers=WORKERS) as pool:
        n5 = pool.map(perceived_loudness, [binary] * len(paths), paths,
                      [measured[path][0] for path in paths], chunksize=8)
        return {path: measured[path] + (level,) for path, level in zip(paths, n5)}


# PERCEIVED LOUDNESS — what a word's gain is set from. ISO 532-1:2017 time-varying loudness
# (Zwicker), read as N5: the loudness the word exceeds 5% of the time, as phon. EBU R128 is
# a broadcast meter of energy; for single words of different voices the loudness models are
# what listening tests back (Zorila et al. 2016, Schlittenlacher et al. 2017), and they hear
# a band-limited or limited voice the way the ear does where K-weighted energy does not.
# The model is nonlinear in level, so every clip is first brought to the same listening level
# (PERCEIVED_REF_LUFS, ~65 dB SPL at PERCEIVED_SPL_DB full scale) and the shift taken back off
# afterwards, at one phon per dB.
PERCEIVED_RATE = 48000
PERCEIVED_REF_LUFS = -26.0
PERCEIVED_SPL_DB = 91.0


def perceived_loudness(binary, path, loudness):
    """N5 of `path` in phon at unity gain (ISO 532-1); None for a silent file."""
    if loudness is None:
        return None
    from mosqito.sq_metrics import loudness_zwtv
    raw = subprocess.run(
        [binary, '-v', 'error', '-i', path, '-ac', '1', '-ar', str(PERCEIVED_RATE), '-f', 'f32le', '-'],
        capture_output=True, check=True).stdout
    shift = PERCEIVED_REF_LUFS - loudness
    x = np.frombuffer(raw, dtype=np.float32).astype(float) * 10 ** ((shift + PERCEIVED_SPL_DB - 94) / 20)
    N, _, _, _ = loudness_zwtv(x, PERCEIVED_RATE, field_type='free')
    sones = float(np.percentile(N, 95))
    phon = 40 + 33.22 * np.log10(sones) if sones >= 1 else 40 * (sones + 0.0005) ** 0.35
    return phon - shift
