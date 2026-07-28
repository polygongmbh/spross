#!/usr/bin/env python3
"""Synthesize the three review-loop feedback sounds.

    scripts/sounds.py            # (re)write App/Resources/Sounds/*.wav

The app must NOT use Apple's built-in UISounds ids: on Taptic iPhones those
ids drag the system alert haptic along with them, and it cannot be suppressed
per call. Bundled files play silent to the touch, so the sounds are ours to
author — see App/Sources/Design/Sounds.swift.

Design grammar, from how the space already trains people:
  correct — ASCENDING major third; the standard positive confirmation
            (Duolingo's chime, Apple Pay's approval tone).
  wrong   — DESCENDING minor third; direction says "down" while the interval
            stays consonant, so it reads as mild "aw" and not as a buzzer.
            The textbook error intervals (tritone, minor second) are harsh
            on purpose and we do not want that here.
  reveal  — one neutral note, no direction: revealing is not a verdict.

Warmth is timbre and envelope, not the interval. A near-pure tone with an
instant onset is what makes a sound read as "beep": hence a triangle-ish
spectrum, a soft attack, an exponential ring-off, and a second note that
enters while the first still sounds so the two blend into a chord.

Everything below is meant to be re-tuned by ear — edit, re-run, afplay.
"""
import math
import os
import struct
import wave

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, 'App/Resources/Sounds')

SAMPLE_RATE = 44100
PEAK = 0.30          # headroom: these play over the ring/silent switch, not loud
ATTACK = 0.010       # s — long enough to kill the onset click, short enough to feel instant
END_FADE = 0.005     # s — no click when the tail is truncated by the buffer

# Odd harmonics falling ~1/n² (triangle-like: soft, bodied, no saw/square buzz),
# plus a touch of 2nd harmonic for warmth. (multiple, amplitude).
HARMONICS = ((1, 1.0), (2, 0.12), (3, -1 / 9), (5, 1 / 25), (7, -1 / 49))

# Equal temperament, A4 = 440.
E5, G_SHARP5 = 659.26, 830.61   # ascending major third
G4, E4 = 392.00, 329.63         # descending minor third
B4 = 493.88

# name: (decay time constant in s, [(freq, start s, length s, gain), ...])
SOUNDS = {
    'correct': (0.090, [
        (E5,         0.000, 0.34, 1.00),
        (G_SHARP5,   0.085, 0.33, 1.00),
    ]),
    'wrong': (0.120, [
        (G4,         0.000, 0.40, 0.95),
        (E4,         0.115, 0.40, 0.95),
    ]),
    'reveal': (0.045, [
        (B4,         0.000, 0.16, 0.55),
    ]),
}


def voice(freq, length, tau):
    """One note: harmonic stack under a soft-attack, exponential-decay envelope."""
    out = []
    for i in range(int(length * SAMPLE_RATE)):
        t = i / SAMPLE_RATE
        # raised cosine in, exponential out — the ring-off is what reads as
        # marimba rather than as a cut-off beep
        attack = 1.0 if t >= ATTACK else 0.5 - 0.5 * math.cos(math.pi * t / ATTACK)
        env = attack * math.exp(-t / tau)
        sample = sum(amp * math.sin(2 * math.pi * freq * mult * t)
                     for mult, amp in HARMONICS)
        out.append(sample * env)
    return out


def render(tau, notes):
    total = max(int((start + length) * SAMPLE_RATE) for _, start, length, _ in notes)
    buf = [0.0] * total
    for freq, start, length, gain in notes:
        offset = int(start * SAMPLE_RATE)
        for i, sample in enumerate(voice(freq, length, tau)):
            buf[offset + i] += sample * gain
    fade = int(END_FADE * SAMPLE_RATE)
    for i in range(min(fade, total)):
        buf[total - 1 - i] *= i / fade
    return buf


def write_wav(path, buf, scale):
    frames = bytearray()
    for sample in buf:
        value = max(-1.0, min(1.0, sample * scale))
        frames += struct.pack('<h', int(value * 32767))
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        w.writeframes(bytes(frames))


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    rendered = {name: render(tau, notes) for name, (tau, notes) in SOUNDS.items()}
    # why: one shared scale factor across all three files — normalizing each on
    # its own peak would drag the quiet reveal tick up to chime volume.
    loudest = max(max(abs(s) for s in buf) for buf in rendered.values())
    scale = PEAK / loudest
    for name, buf in rendered.items():
        path = os.path.join(OUT_DIR, name + '.wav')
        write_wav(path, buf, scale)
        peak = max(abs(s) for s in buf) * scale
        print(f'{name}.wav  {len(buf) / SAMPLE_RATE:.3f}s  peak {peak:.2f}')


if __name__ == '__main__':
    main()
