# Audio quality measure: an alternative to `noise_margin`
A hand-built recording-quality score tried against a listener's verdicts, and why it was not adopted.
Neighbors: the survey that replaced it `2026-09-28-audio-quality-tools.md`, the verdicts `audio-verdicts.tsv`.

Research spike: does a different automated score agree with a human listener better than
`noise_margin` (`scripts/audio_measure.py`)?
Prototype and evaluation code: `/private/tmp/claude-501/-Users-tj-IT-duolernen-app/6a5f5b76-f842-46e7-869f-4c463a491230/scratchpad/quality/`
(`quality_score.py` is the deliverable; `extract_features.py` and `evaluate.py` are the
harness that produced the numbers below).
Nothing here is wired into the app or the catalog tooling.

## Dataset

`docs/audio-verdicts.tsv` has 62 rows.
Every row's sha256 resolved to real bytes under `catalog/audio/` or `data/review/`
(recursive hash of both trees, no Commons downloads) — 62/62, no skipped rows.

Verdict distribution: okay 24, mediocre 17, bad 12, great 6, good 3.
Defect labels are sparse: noisy 16, breathy 3, "echoey, noisy" 1, accented 1, quiet 1;
40 rows carry no defect label at all, mostly ordinal judgment only.
With this little data, per-defect claims (breathiness, reverb specifically) are weak;
aggregate ordinal correlation is the load-bearing number.

## Why `noise_margin` disagrees with the listener

`noise_margin` contrasts the loudest 256-sample frame's in-band (300–4000 Hz) power against
the 5th-percentile in-band power across all frames of the file.
Two failure modes came out of diagnosing the flagged cases directly (recomputing the same
frames `audio_measure.py` uses, printing their per-frame RMS and band power):

- **Smooth, continuous delivery reads as noise.**
  Holunder Gabriel's takes have almost no true silence within the trimmed word — every frame
  carries speech — so the 5th-percentile frame is itself a quiet moment of speech, not
  background hiss.
  A professional, evenly paced delivery has *less* internal dynamic range than a rougher one,
  so this measure systematically scores it lower, exactly backwards from what the listener
  hears (42.9–58.8 for a voice called "gold standard").
- **Digital silence outscores real defects.**
  When a file has a run of exact- or near-zero samples (silence padding, an untrimmed
  lead-in), the 5th-percentile band power is 0 for at least one bin, the margin is undefined,
  and `audio_measure.py` treats `None` as the cleanest possible file.
  Natschoba's "der Arzt" hits this — the shipped manifest has no `snr` for it at all — and so
  does "der Finger" (`catalog/audio/de/articles/finger.mp3`, also Natschoba, flagged
  separately during this spike as "a bit meh"): both have real defects in their speech that
  the measure never gets to look at.

Across the 62 labels, `noise_margin` is close to uncorrelated with the human verdict
(Spearman rho 0.03, pairwise ordering barely better than a coin flip).
That is a real finding, not a rounding error: the shipped score is not currently doing the
job it is used for.

## Approaches tried

All candidates decode with ffmpeg to float32 PCM and compute in numpy/scipy — no
pip-installs, `librosa`/`soundfile` unavailable.
Metrics: Spearman rho against the ordinal verdict (great=4 … bad=0), pairwise ordering
accuracy over all label pairs with different verdicts, and an AUC-style separation of "bad"
from everything better.
"n" is the labeled rows scored (every candidate scored all 62; digital silence or too-short
files are handled explicitly rather than dropped).

| candidate | idea | rho | pairwise acc | bad-vs-rest AUC |
|---|---|---:|---:|---:|
| `noise_margin` (shipped) | loudest frame vs. 5th-pctile frame, 300–4000 Hz | 0.03 | 0.51 | 0.63 |
| harmonic-to-noise ratio | autocorrelation periodicity of the loudest (voiced) frames | **−0.41** | 0.32 | 0.18 |
| high-band noise margin | noise read from 5.5–9.5 kHz instead of the word's own band | −0.26 | 0.39 | 0.35 |
| VAD-flavored SNR | percentile floor/peak, true-zero frames excluded | −0.21 | 0.41 | 0.46 |
| envelope modulation depth | 2–20 Hz envelope modulation, as a reverb/echo proxy | −0.02 | 0.49 | 0.60 |
| spectral rolloff (bandwidth) | frequency holding 95% of energy in the loudest frames | 0.21 | 0.59 | 0.74 |
| **flatness of the quiet frames** | spectral flatness of the quietest 20% of frames | 0.25 | 0.62 | 0.52 |
| flatness-gated `noise_margin` | `noise_margin`'s floor, built only from flat-spectrum frames | 0.05 | 0.52 | 0.64 |
| equal-weight combo (3 features below) | unweighted z-score average | 0.44 | 0.70 | 0.81 |
| **recommended: weighted combo** | see below | **0.45** (LOO 0.46) | **0.70** (LOO 0.71) | 0.70 (LOO 0.71) |

Three approaches came back negatively or not-at-all correlated and are reported as
not working, per the brief:

- **Harmonic-to-noise ratio** (autocorrelation peak in the pitch range, on the loudest 20 dB
  of frames) was the most promising idea on paper — breathiness and background hiss both
  reduce periodicity — but it inverted on this data.
  Manual inspection of individual frames suggests it is dominated by phonetic content
  (which vowel, how it coarticulates within a 40 ms window) more than by recording quality
  at this sample size; it would need pitch-synchronized analysis and far more labels to be
  trustworthy.
- **High-band noise margin** and **VAD-flavored SNR** are both still floor-vs-peak measures
  under the hood and inherited the same "quiet speech misread as noise" problem as the
  shipped one, just relocated.
- **Envelope modulation depth**, meant to catch reverb/echo (reflections smooth the
  syllable-rate envelope), showed no correlation at all.
  With only one "echoey" label in the set (Jakob.scholbach's "passieren"), this is as much a
  data problem as a method problem — it is not ruled out, just unvalidated.

**Spectral rolloff** (bandwidth) was the strongest single feature not built on flatness: it
catches dull/muffled/lowpassed takes directly and had the best standalone bad-vs-rest
separation (0.74).

## Recommended measure

A weighted combination of three features, two of them built on one idea — **is the quietest
part of the recording spectrally flat (broadband, noise-like) or still spectrally structured
(quiet speech)** — plus bandwidth as an orthogonal check:

```
score = ( 2·z(−flatness_quiet)
        + 1·z(flatness_gated_margin)
        + 1·z(rolloff_hz) ) / 4
```

where `z(x) = (x − mean) / std` over the reference 62-row set (constants baked into
`quality_score.py`'s `CALIBRATION`; recompute once meaningfully more labels exist).
Higher score = better recording.

**`flatness_quiet`**: take the quietest 20% of 256-sample frames (22050 Hz, 64-sample hop,
Hann window, restricted to 300–4000 Hz — the same frame geometry as `noise_margin`, for
comparability).
For each such frame, spectral flatness = geometric mean of in-band power / arithmetic mean
of in-band power (0 = one pure tone, 1 = perfectly flat/white).
The feature is the median flatness across those frames.
Real hiss is close to flat even where the word is quiet; a quiet moment that is still speech
(a soft consonant, a smoothly delivered word with little dynamic range) has a peaky,
formant-shaped spectrum.
This is the feature that actually catches der Arzt and der Finger (see below); it gets
double weight in the combination for that reason.

**`flatness_gated_margin`**: the same computation as `noise_margin`, except the
minimum-statistics floor is built only from frames whose in-band flatness is ≥ 0.35 (falling
back to the plain 5th percentile if fewer than 5 frames qualify, so a genuinely noisy file
with no flat-looking frames is still measured).
Exact-/near-zero (digital-silence) frames are dropped before any percentile is taken, so
padding can never again stand in for "no noise found."
Weight 1.

**`rolloff_hz`**: at the full decoded rate (44100 Hz, 1024-sample frames, 256-sample hop),
take the loudest quarter of frames by RMS and find the frequency below which 95% of their
summed spectral energy sits.
Weight 1.

Missing components (too short, digitally silent throughout) are dropped from the average
rather than failing the score.

**Honesty about the numbers.** rho 0.45 and pairwise accuracy 0.70 are a real improvement
over the baseline's 0.03 / 0.51, confirmed out-of-sample by leave-one-out
re-normalization (rho 0.46, pairwise 0.71, computed by re-fitting each feature's mean/std on
the other 61 rows before scoring the held-out one).
They are not a strong measure in an absolute sense — with 62 labels across five ordinal
levels and only two features with real orthogonal information (flatness and bandwidth), this
is close to the ceiling reachable without more labels or a feature that directly targets
reverb or breathiness.
The feature-selection itself (choosing to combine these three, at this weighting) was guided
by the same 62 rows, so treat the LOO numbers as a check on the normalization step, not a
guarantee the exact weighting generalizes — re-run `evaluate.py` once the label set grows.

## The flagship cases

**Holunder Gabriel vs. Natschoba.**
Recommended-measure voice medians: Holunder Gabriel 0.5, Natschoba 0.3, MiER −0.3,
Jakob.scholbach −0.3 — Holunder above Natschoba, both clearly above MiER and
Jakob.scholbach, matching every voice-level judgment given.
Under `noise_margin` Holunder Gabriel actually scored *below* Natschoba's median (50.6 vs.
61.0) despite being called the more pristine voice; that inversion is gone.

**Natschoba's "der Arzt."**
`noise_margin`: `None` — digital silence, read as the cleanest file in the catalog.
`flatness_quiet` alone: 0.63, the single highest (most noise-like) value in the entire
62-row set — a direct, strong catch.
Combined score: −0.24, below the median take and correctly below every "great"-rated
Holunder Gabriel sample.

**Natschoba's "der Finger"** (flagged during this spike, not in the 62-row set;
`catalog/audio/de/articles/finger.mp3`, sha256 `5d3024cee0a7...`).
Same bug, more extreme: `noise_margin` is again `None` (missing from `catalog/audio/de/manifest.json`
too — no `snr` on that entry), while `flatness_quiet` reads 0.9999996, essentially perfectly
flat.
Combined score: −0.83, one of the worse scores in the whole comparison set — consistent with
"a bit meh," and a second real example of the same failure mode independently confirming the
fix.

## What more labels would help most

- **Echo/reverb.** Only one "echoey" label exists (Jakob.scholbach's "passieren").
  The one candidate aimed at it (envelope modulation depth) showed nothing; more echoey
  examples, ideally spanning several voices, are needed before a reverb-specific feature can
  be trusted either way.
- **Breathiness.** Three labels, one voice (Kampy).
  Harder to separate from generic noise with this few examples.
- **More digitally-padded-but-defective files** like der Arzt/der Finger, to check whether
  0.35 is the right flatness gate threshold or just fit to two examples.
- **More "great" and "good" examples** (6 and 3 respectively) — the top of the ordinal scale
  is the thinnest part of the current set, and it is exactly where the baseline's
  inversion showed up.
