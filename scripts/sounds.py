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
GLIDE_CURVE = 3.0    # >1 accelerates into the target; 1.0 is a flat linear sweep

# What separates a struck instrument from a beep is not the harmonic recipe but
# what the recipe DOES over time. Three physical facts, and each one is audible:
PARTIALS = 9         # a beep is a sine; a played note has a stack
ROLLOFF = 1.7        # amplitude ~ 1/n**ROLLOFF — higher is mellower
DECAY_SPREAD = 0.75  # partial n decays n**SPREAD times faster than the fundamental,
                     # so the tone darkens as it rings, the way struck things do.
                     # This is the single strongest cue; a spectrum frozen for the
                     # whole note is what makes additive synthesis sound electronic.
INHARMONICITY = 4e-4  # real strings/bars are slightly stretched, never exact
                      # integer multiples — exact ratios beat too perfectly
DETUNE = 0.0016      # a hair of per-partial drift, so the stack beats slowly
                     # instead of locking into one motionless timbre

# A body resonance, fixed in Hz and NOT moving with the note. This is what makes
# a glide sound played rather than swept: on any real instrument the box, bar or
# bore resonates at frequencies of its own, so partials brighten and dim as the
# pitch slides them through the peak. A synthesizer's portamento carries its
# timbre along unchanged — that invariance is the giveaway.
BODY_HZ = 1150.0
BODY_GAIN = 0.9      # boost at the peak
BODY_OCTAVES = 0.55  # width, in natural-log units of frequency ratio

# Contact noise. Nothing is set in motion silently: a mallet, a pluck or a
# breath puts a burst of inharmonic energy in before the tone settles. It is
# nearly inaudible on its own and the strongest single "not a synth" cue there is.
NOISE_GAIN = 0.10
NOISE_TAU = 0.008    # s — gone before the note has properly started
NOISE_DAMP = 0.40    # one-pole lowpass on the burst; undamped it is just hiss

# Each sound scales the partials by its own `bright`; the fundamental never
# moves, so turning brightness down rounds a sound off instead of muffling it.
HARMONICS = tuple(
    (n * (1 + INHARMONICITY * (n * n - 1) + DETUNE * math.sin(n * 2.399)),
     n ** -ROLLOFF,
     float(n ** DECAY_SPREAD),
     # partials starting in lockstep give the onset an electronic spike; spread
     # them by the golden angle so the waveform starts messy, as a real one does
     (n * 0.6180339887 % 1.0) * 2 * math.pi)
    for n in range(1, PARTIALS + 1)
)

# Equal temperament, A4 = 440. The bottom of the usable range is ~E4: phone
# speakers roll off below ~250 Hz, where only the harmonics still carry.
A4, E5 = 440.00, 659.26         # ascending perfect fifth — wide enough to read
F_SHARP4, D4 = 369.99, 293.66   # descending minor third — lands under correct's start
G4 = 392.00

# `level` is the finished peak relative to PEAK, applied after each sound is
# normalized on its own — two overlapping notes sum to a taller waveform than
# one, and without this the wrong answer would come out the loudest of the three.
# Notes are (from Hz, to Hz, glide s, start s, length s, gain-within-this-sound).
SOUNDS = {
    # The glide must still finish well inside the decay — while the pitch is
    # moving the sound has not ARRIVED anywhere, and a glide that is still
    # travelling through its loudest part is the unsatisfying kind. Hence a
    # wide interval read slowly at first, then a rush onto the target with
    # most of the ring left to land in.
    'correct': dict(tau=0.125, attack=0.012, bright=1.00, level=1.00, notes=[
        (A4, E5,             0.115, 0.000, 0.31, 1.00),
    ]),
    # two distinct notes here — a wrong answer is the one event worth being
    # unambiguous about, and two articulated pitches read as deliberate where
    # a glide can slide past unnoticed. Quieter than correct on purpose.
    'wrong': dict(tau=0.100, attack=0.016, bright=0.90, level=0.90, notes=[
        (F_SHARP4, F_SHARP4, 0.000, 0.000, 0.28, 1.00),
        (D4, D4,             0.000, 0.090, 0.28, 1.00),
    ]),
    # heard most often of the three, so the roundest and the quietest: slow
    # attack, partials pulled right down, and short enough to stay out of the way
    'reveal': dict(tau=0.045, attack=0.022, bright=0.25, level=0.30, notes=[
        (G4, G4,             0.000, 0.000, 0.13, 1.00),
    ]),
}


def voice(start_hz, end_hz, glide, length, tau, attack_s, bright):
    """One note: harmonic stack, gliding start→end, under a soft-attack,
    exponential-decay envelope. Phase is integrated sample by sample — the
    naive sin(2πft) with a moving f would smear the pitch it claims to play."""
    partials = [[mult, amp if n == 0 else amp * bright, spread / tau, phase0]
                for n, (mult, amp, spread, phase0) in enumerate(HARMONICS)]
    out = []
    rng, noise = int(start_hz) * 7919 + 12345, 0.0   # fixed seed: same file every run
    for i in range(int(length * SAMPLE_RATE)):
        t = i / SAMPLE_RATE
        # Accelerating glide: half the glide time covers an eighth of the
        # distance, so the departure note is held long enough to be heard and
        # the interval reads as an interval — then it rushes into the target,
        # arriving at full speed, which is what makes the landing feel like one.
        # (Smoothstep would ease OUT instead and dissolve the arrival; a linear
        # ramp just sounds mechanical.) Interpolated in semitones, not Hz —
        # pitch is logarithmic, and a linear Hz sweep drags at the bottom.
        if glide <= 0 or t >= glide:
            freq = end_hz
        else:
            x = (t / glide) ** GLIDE_CURVE
            freq = start_hz * (end_hz / start_hz) ** x
        # raised cosine in, exponential out — the ring-off is what reads as
        # marimba rather than as a cut-off beep. Each partial rings off on its
        # own clock, so the note starts bright and darkens instead of holding
        # one frozen colour to the end.
        attack = 1.0 if t >= attack_s else 0.5 - 0.5 * math.cos(math.pi * t / attack_s)
        sample = 0.0
        for partial in partials:
            mult, amp, rate, phase = partial
            f = freq * mult
            # the fixed body peak, evaluated at where this partial IS right now
            tilt = math.log(f / BODY_HZ) / BODY_OCTAVES
            body = 1.0 + BODY_GAIN * math.exp(-0.5 * tilt * tilt)
            sample += amp * body * math.exp(-t * rate) * math.sin(phase)
            partial[3] = phase + 2 * math.pi * f / SAMPLE_RATE
        burst = math.exp(-t / NOISE_TAU)
        if burst > 0.002:
            rng = (rng * 1103515245 + 12345) & 0x7FFFFFFF
            noise += ((rng / 0x3FFFFFFF) - 1.0 - noise) * NOISE_DAMP
            sample += noise * NOISE_GAIN * burst
        out.append(sample * attack)
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
