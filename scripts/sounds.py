#!/usr/bin/env python3
"""Synthesize the review-loop feedback sounds and the finish-screen cheer.

    scripts/sounds.py            # (re)write App/Resources/Sounds/*.wav

The app must NOT use Apple's built-in UISounds ids: on Taptic iPhones those
ids drag the system alert haptic along with them, and it cannot be suppressed
per call. Bundled files play silent to the touch, so the sounds are ours to
author — see App/Sources/Design/Sounds.swift.

Design grammar, from how the space already trains people:
  correct — ASCENDING major third; the standard positive confirmation
            (Duolingo's chime, Apple Pay's approval tone).
  wrong   — DESCENDING minor third; direction says "down" while the interval
            stays consonant, so it reads as a mild "aw".
  reveal  — one neutral note, no direction: revealing is not a verdict.
  cheer   — the same ascending major third, carried on up through the triad to
            the octave: the finish is the correct sound arriving somewhere.

The cheer is the one exception to everything the paragraph below asks for. It
plays once a day, at the end, with nothing after it to interrupt — so it may
ring longer and brighter than the three that punctuate a session.

Every interval is two STRUCK notes, never a glide between them. A glide was
tried at length and always sounded synthetic, for a reason no amount of
tuning could reach: struck and plucked things cannot bend pitch — a bar or a
string fixes its pitch the instant it is excited — so a percussive attack
that then slides describes no object that has ever existed, and the ear knows
it. Two notes also read as deliberate where a glide slides past unnoticed.

Low profile is the point: these play many times per session, often in public.
Nothing loud and nothing that outstays the moment —
length is what turns a chime into a doorbell.
Direction and register carry the meaning.

Floor on how deep it can go: phone speakers roll off below ~250 Hz,
where the fundamental survives only through its harmonics. E4 (330) is about the bottom.

Everything below is meant to be re-tuned by ear — edit, re-run, afplay.
Levels are the one thing ear alone got wrong, so re-check them against the words after a re-tune:

    ffmpeg -i App/Resources/Sounds/correct.wav \
      -af 'adelay=500|500,apad=pad_dur=0.5,ebur128=peak=none' -f null -   # read `I:`

(the padding is not decoration: R128 integrates 400 ms blocks, and a tenth-of-a-second file
fills none of them — unpadded it reads -70 LUFS, which is not quiet, it is unmeasured.)
"""
import math
import os
import struct
import wave

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, 'App/Resources/Sounds')

SAMPLE_RATE = 44100
END_FADE = 0.005     # s — no click when the tail is truncated by the buffer

# Written STEREO, both channels identical. Mono sounds the same through either speaker
# path, but R128 sums channels: a mono file measures 3 dB under the stereo word it is
# being levelled against, and a number that needs a correction remembered is a number
# that will be compared without it.
CHANNELS = 2

# Odd harmonics falling ~1/n² (triangle-like: soft, bodied, no saw/square buzz),
# plus a touch of 2nd harmonic for warmth. (multiple, amplitude). Each sound
# scales these by its own `bright`; the fundamental never moves, so turning
# brightness down rounds a sound off instead of muffling it.
HARMONICS = ((1, 1.0), (2, 0.12), (3, -1 / 9), (5, 1 / 25), (7, -1 / 49))

DECAY_SPREAD = 0.65  # partial n rings off n**SPREAD times faster than the
                     # fundamental, so the tone darkens as it decays. This is
                     # what keeps a two-note chime off the front door: a doorbell
                     # is a long ring that holds ONE bright colour to the end.

# Equal temperament, A4 = 440. Nothing goes below ~E4 — phone speakers roll off
# under ~250 Hz, where the fundamental survives only through its harmonics.
E5, G_SHARP5 = 659.26, 830.61   # ascending major third
G4, E4 = 392.00, 329.63         # descending minor third — G4 doubles as the tick
B5, E6 = 987.77, 1318.51        # the cheer carries E–G# on up: triad, then octave

# `peak` is the finished sample peak, applied after each sound is normalized on its own —
# two overlapping notes sum to a taller waveform than one, and without this the wrong
# answer would come out the loudest of the three. `lufs` is what that peak was MEASURED
# to arrive at (the command in the docstring), and it is the number that matters: a peak
# is not a loudness — a struck note is a spike over a decay — and peak-matching these
# against speech left every chime a few dB down, `reveal` nearly ten.
#
# The words land at -16.7 LUFS (scripts/audio-catalog.py boosts every recording there),
# and all four chimes now sit a little ABOVE that. Not a louder design: K-weighting counts
# energy, and a near-sine spends all of its in one critical band where speech spreads
# across many, so a chime metered level with a word is heard under it. Peaks stay under
# the -1 dBFS the recordings' own gains respect, for the same reason.
# Notes are (Hz, start s, length s, gain-within-this-sound); the second note is
# struck while the first still rings, so they overlap into an interval instead
# of arriving as two separate events.
SOUNDS = {
    # the most-heard of the three that carries a verdict, so the least present
    # of them: rounder and shorter than the sound of getting it wrong, which is
    # rarer and has to be noticed
    'correct': dict(tau=0.060, attack=0.010, bright=0.10, peak=0.708, lufs=-14.2, notes=[
        (E5,         0.000, 0.19, 1.00),
        (G_SHARP5,   0.070, 0.19, 1.00),
    ]),
    # keeps more edge than the other two: it is the rarest of the three and the
    # one that has to register without being looked at
    'wrong': dict(tau=0.090, attack=0.012, bright=0.55, peak=0.776, lufs=-13.7, notes=[
        (G4,         0.000, 0.26, 0.95),
        (E4,         0.100, 0.26, 0.95),
    ]),
    # plays on every card whatever the outcome, so the least of everything:
    # quietest, roundest, shortest, and low enough not to pierce. Still the
    # quietest of the four, but only just — at a tenth of a second it is the one
    # sound short enough to be missed rather than merely soft, and the old
    # peak-matched spread put it far enough under the words to be missed.
    'reveal': dict(tau=0.036, attack=0.018, bright=0.10, peak=0.676, lufs=-16.2, notes=[
        (G4,         0.000, 0.11, 1.00),
    ]),
    # heard once, at the end: the correct interval kept climbing, with a long
    # ring-off under it. The strikes accelerate (85, 75, 65 ms apart) so it
    # arrives rather than marches, and the last two notes ring twice as long as
    # the run-up — that held octave is the "landed" part.
    'cheer': dict(tau=0.150, attack=0.008, bright=0.22, peak=0.767, lufs=-13.7, notes=[
        (E5,         0.000, 0.40, 0.70),
        (G_SHARP5,   0.085, 0.45, 0.80),
        (B5,         0.160, 0.90, 0.90),
        (E6,         0.225, 0.95, 1.00),
    ]),
}


def voice(freq, length, tau, attack_s, bright):
    """One struck note: a harmonic stack under a soft-attack, exponential-decay
    envelope, each partial ringing off on its own clock so the tone darkens as
    it goes. Pitch is fixed for the note's life, as it is on anything struck."""
    partials = [(amp if mult == 1 else amp * bright,
                 mult ** DECAY_SPREAD / tau,
                 2 * math.pi * freq * mult / SAMPLE_RATE)
                for mult, amp in HARMONICS]
    out = []
    for i in range(int(length * SAMPLE_RATE)):
        t = i / SAMPLE_RATE
        # raised cosine in, exponential out — the ring-off is what reads as
        # marimba rather than as a cut-off beep
        attack = 1.0 if t >= attack_s else 0.5 - 0.5 * math.cos(math.pi * t / attack_s)
        sample = sum(amp * math.exp(-t * rate) * math.sin(step * i)
                     for amp, rate, step in partials)
        out.append(sample * attack)
    return out


def render(tau, attack_s, bright, notes):
    total = max(int((start + length) * SAMPLE_RATE) for _, start, length, _ in notes)
    buf = [0.0] * total
    for freq, start, length, gain in notes:
        offset = int(start * SAMPLE_RATE)
        for i, sample in enumerate(voice(freq, length, tau, attack_s, bright)):
            buf[offset + i] += sample * gain
    fade = int(END_FADE * SAMPLE_RATE)
    for i in range(min(fade, total)):
        buf[total - 1 - i] *= i / fade
    return buf


def write_wav(path, buf, scale):
    frames = bytearray()
    for sample in buf:
        value = max(-1.0, min(1.0, sample * scale))
        frames += struct.pack('<h', int(value * 32767)) * CHANNELS
    with wave.open(path, 'wb') as w:
        w.setnchannels(CHANNELS)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        w.writeframes(bytes(frames))


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for name, spec in SOUNDS.items():
        buf = render(spec['tau'], spec['attack'], spec['bright'], spec['notes'])
        scale = spec['peak'] / max(abs(s) for s in buf)
        write_wav(os.path.join(OUT_DIR, name + '.wav'), buf, scale)
        print(f'{name}.wav  {len(buf) / SAMPLE_RATE:.3f}s  '
              f'peak {spec["peak"]:.3f}  {spec["lufs"]:+.1f} LUFS')


if __name__ == '__main__':
    main()
