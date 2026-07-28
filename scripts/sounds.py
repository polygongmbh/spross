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
  reveal  — one neutral note, no direction: revealing is not a verdict.

Low profile is the point. These play many times per session, often in public,
and a wrong answer is the productive event in retrieval practice — not a
failure to punish. So no dissonance on wrong (the textbook error intervals,
tritone and minor second, are harsh on purpose), nothing up in the bright
register that gets grating on repeat, and the interval is a GLIDE rather than
two struck notes: one soft attack instead of two, so it registers without
announcing itself. Direction and register carry the meaning; loudness and
harshness do not have to.

Floor on how deep it can go: phone speakers roll off below ~250 Hz, where the
fundamental survives only through its harmonics. E4 (330) is about the bottom.

Warmth is timbre and envelope, not the interval. A near-pure tone with an
instant onset is what makes a sound read as "beep": hence a triangle-ish
spectrum (kept mild — fewer, quieter partials than a real triangle), a soft
attack, and an exponential ring-off.

Everything below is meant to be re-tuned by ear — edit, re-run, afplay.
"""
import math
import os
import struct
import wave

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, 'App/Resources/Sounds')

SAMPLE_RATE = 44100
PEAK = 0.26          # headroom: these play over the ring/silent switch, not loud
END_FADE = 0.005     # s — no click when the tail is truncated by the buffer

# Mostly fundamental, with quiet partials for body: enough to carry on a phone
# speaker, far short of a full triangle's edge. (multiple, amplitude). Each
# sound scales the partials by its own `bright` — the fundamental never moves,
# so turning brightness down rounds a sound off instead of muffling it.
HARMONICS = ((1, 1.0), (2, 0.10), (3, -1 / 12), (5, 1 / 40))

# Equal temperament, A4 = 440. The bottom of the usable range is ~E4: phone
# speakers roll off below ~250 Hz, where only the harmonics still carry.
G4, B4 = 392.00, 493.88         # ascending major third
F_SHARP4, D4 = 369.99, 293.66   # descending minor third — lands under correct's start

# `level` is the finished peak relative to PEAK, applied after each sound is
# normalized on its own — two overlapping notes sum to a taller waveform than
# one, and without this the wrong answer would come out the loudest of the three.
# Notes are (from Hz, to Hz, glide s, start s, length s, gain-within-this-sound).
SOUNDS = {
    # one eased glide, not two struck notes: a single soft attack registers
    # without announcing itself
    'correct': dict(tau=0.105, attack=0.014, bright=1.00, level=1.00, notes=[
        (G4, B4,             0.100, 0.000, 0.32, 1.00),
    ]),
    # two distinct notes here — a wrong answer is the one event worth being
    # unambiguous about, and two articulated pitches read as deliberate where
    # a glide can slide past unnoticed. Quieter than correct on purpose.
    'wrong': dict(tau=0.135, attack=0.016, bright=0.90, level=0.90, notes=[
        (F_SHARP4, F_SHARP4, 0.000, 0.000, 0.34, 1.00),
        (D4, D4,             0.000, 0.110, 0.34, 1.00),
    ]),
    # heard most often of the three, so the roundest: slow attack and the
    # partials pulled right down, or it turns into a pointy little beep
    'reveal': dict(tau=0.075, attack=0.030, bright=0.35, level=0.45, notes=[
        (G4, G4,             0.000, 0.000, 0.20, 1.00),
    ]),
}


def voice(start_hz, end_hz, glide, length, tau, attack_s, bright):
    """One note: harmonic stack, gliding start→end, under a soft-attack,
    exponential-decay envelope. Phase is integrated sample by sample — the
    naive sin(2πft) with a moving f would smear the pitch it claims to play."""
    partials = tuple((mult, amp if mult == 1 else amp * bright)
                     for mult, amp in HARMONICS)
    out = []
    phase = 0.0
    for i in range(int(length * SAMPLE_RATE)):
        t = i / SAMPLE_RATE
        # smoothstep so the glide eases in and out; a linear ramp sounds mechanical
        if glide <= 0 or t >= glide:
            freq = end_hz
        else:
            x = t / glide
            freq = start_hz + (end_hz - start_hz) * x * x * (3 - 2 * x)
        # raised cosine in, exponential out — the ring-off is what reads as
        # marimba rather than as a cut-off beep
        attack = 1.0 if t >= attack_s else 0.5 - 0.5 * math.cos(math.pi * t / attack_s)
        env = attack * math.exp(-t / tau)
        sample = sum(amp * math.sin(phase * mult) for mult, amp in partials)
        out.append(sample * env)
        phase += 2 * math.pi * freq / SAMPLE_RATE
    return out


def render(tau, attack_s, bright, notes):
    total = max(int((start + length) * SAMPLE_RATE) for *_, start, length, _ in notes)
    buf = [0.0] * total
    for start_hz, end_hz, glide, start, length, gain in notes:
        offset = int(start * SAMPLE_RATE)
        for i, sample in enumerate(
                voice(start_hz, end_hz, glide, length, tau, attack_s, bright)):
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
    for name, spec in SOUNDS.items():
        buf = render(spec['tau'], spec['attack'], spec['bright'], spec['notes'])
        scale = PEAK * spec['level'] / max(abs(s) for s in buf)
        path = os.path.join(OUT_DIR, name + '.wav')
        write_wav(path, buf, scale)
        print(f'{name}.wav  {len(buf) / SAMPLE_RATE:.3f}s  '
              f'peak {PEAK * spec["level"]:.2f}')


if __name__ == '__main__':
    main()
