#!/usr/bin/env python3
"""Synthesize the three review-loop feedback sounds.

    scripts/sounds.py            # (re)write App/Resources/Sounds/*.wav

The app must NOT use Apple's built-in UISounds ids: on Taptic iPhones those
ids drag the system alert haptic along with them, and it cannot be suppressed
per call. Bundled files play silent to the touch, so the sounds are ours to
author — see App/Sources/Design/Sounds.swift.

Design grammar, from how the space already trains people:
  correct — ASCENDING perfect fifth; the standard positive confirmation
            (Duolingo's chime, Apple Pay's approval tone) taken wider.
  wrong   — DESCENDING minor third; direction says "down" while the interval
            stays consonant, so it reads as mild "aw" and not as a buzzer.
  reveal  — one neutral note, no direction: revealing is not a verdict.

Every interval is two STRUCK notes, never a glide between them. A glide was
tried at length and always sounded synthetic, for a reason no amount of
tuning could reach: struck and plucked things cannot bend pitch — a bar or a
string fixes its pitch the instant it is excited — so a percussive attack
that then slides describes no object that has ever existed, and the ear knows
it. Two notes also read as deliberate where a glide slides past unnoticed.

Low profile is the point. These play many times per session, often in public,
and a wrong answer is the productive event in retrieval practice — not a
failure to punish. So no dissonance on wrong (the textbook error intervals,
tritone and minor second, are harsh on purpose), and nothing up in the bright
register that gets grating on repeat. Direction and register carry the
meaning; loudness and harshness do not have to.

Floor on how deep it can go: phone speakers roll off below ~250 Hz, where the
fundamental survives only through its harmonics. E4 (330) is about the bottom.

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

# A body resonance, fixed in Hz and NOT transposed with the note. Every real
# instrument has a box, bar or bore that resonates at frequencies of its own,
# so the same object struck high sounds different from the same object struck
# low. Transposing one timbre across the keyboard is the sampler's tell, and
# with two notes a fifth apart it would be audible.
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
# Notes are (Hz, start s, length s, gain-within-this-sound); the second note is
# struck while the first still rings, so they overlap into an interval instead
# of arriving as two separate events.
SOUNDS = {
    'correct': dict(tau=0.125, attack=0.012, bright=1.00, level=1.00, notes=[
        (A4,         0.000, 0.28, 1.00),
        (E5,         0.090, 0.28, 0.95),
    ]),
    # quieter than correct on purpose — this is the one that must not punish
    'wrong': dict(tau=0.100, attack=0.016, bright=0.90, level=0.90, notes=[
        (F_SHARP4,   0.000, 0.28, 1.00),
        (D4,         0.090, 0.28, 1.00),
    ]),
    # heard most often of the three, so the roundest and the quietest: slow
    # attack, partials pulled right down, and short enough to stay out of the way
    'reveal': dict(tau=0.045, attack=0.022, bright=0.25, level=0.30, notes=[
        (G4,         0.000, 0.13, 1.00),
    ]),
}


def voice(freq, length, tau, attack_s, bright):
    """One struck note: a harmonic stack under a soft-attack, exponential-decay
    envelope, each partial ringing off on its own clock so the tone darkens as
    it goes. Pitch is fixed for the note's life, as it is on anything struck."""
    partials = []
    for n, (mult, amp, spread, phase0) in enumerate(HARMONICS):
        f = freq * mult
        # the fixed body peak: where this partial falls against the resonance
        # decides its weight, so a high note is coloured differently from a low
        # one — the same bar sounds different struck at different places
        tilt = math.log(f / BODY_HZ) / BODY_OCTAVES
        body = 1.0 + BODY_GAIN * math.exp(-0.5 * tilt * tilt)
        partials.append(((amp if n == 0 else amp * bright) * body,
                         spread / tau, phase0, 2 * math.pi * f / SAMPLE_RATE))
    out = []
    rng, noise = int(freq) * 7919 + 12345, 0.0   # fixed seed: same file every run
    for i in range(int(length * SAMPLE_RATE)):
        t = i / SAMPLE_RATE
        # raised cosine in, exponential out — the ring-off is what reads as
        # marimba rather than as a cut-off beep
        attack = 1.0 if t >= attack_s else 0.5 - 0.5 * math.cos(math.pi * t / attack_s)
        sample = sum(amp * math.exp(-t * rate) * math.sin(phase0 + step * i)
                     for amp, rate, phase0, step in partials)
        burst = math.exp(-t / NOISE_TAU)
        if burst > 0.002:
            rng = (rng * 1103515245 + 12345) & 0x7FFFFFFF
            noise += ((rng / 0x3FFFFFFF) - 1.0 - noise) * NOISE_DAMP
            sample += noise * NOISE_GAIN * burst
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
