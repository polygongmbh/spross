# Audio evaluations, October 2026: quality measures, pops, noise, and swaps by ear
Every measure and picker change tried against a listener's verdicts in October 2026, one dated section each, with its data, its result, and what was adopted.
Neighbors: the September measures `2026-09-28-audio-quality-tools.md` and `2026-09-28-audio-quality-measure.md`, the verdicts `audio-verdicts.tsv`, the bars `../catalog/audio/README.md`, the open decision `plans/audio-measure.md`.

Study scripts and per-take numbers live in the workspace `data/reference/audio/`:
`measure-study-2026-10-25/`, `pop-study-2026-10-28/`, `noise-study-2026-10-28/` and `measure-study-2026-10-28/`.
Rank AUC is the chance a measure scores a take heard okay or better over one heard bad or mediocre (0.5 is chance);
intervals are 95%, bootstrapped over voices.
Verdicts before 2026-10-28 carry no `heard_on`;
some were heard on laptop speakers, which hide noise and one-sidedness,
so an okay-or-better verdict is weak evidence of a clean take.
Most rated takes were drawn as the lowest scorers of what ships, so the score itself chose the sample.

## DNSMOS sub-scores and signal features (2026-10-25)

369 rated takes; `mos` (DNSMOS OVRL) against DNSMOS SIG and BAK and about twenty signal features
(noise margin, speech overlong for its letters, decay after the word, clicks, clipping, spectral rolloff and flatness, side balance).
`mos` reached AUC 0.71 held out by speaker, 0.66 held out by date;
within one speaker it ranks near chance, and for de and en near chance overall.
At the 2.8 bar, 19 of 70 bad or mediocre takes passed while 81 of 183 good or great were sent for reconsideration.
The best fit, `mos` + BAK + the overlong ratio, gained +0.03 to +0.06 AUC with every interval spanning zero, and was not adopted.
BAK alone caught noise best (AUC 0.90 on noisy notes) but ranked Holunder Gabriel below Jeuwre.
Echo was untestable: 15 of 17 echo labels were Marreromarco's; clicks had 1–2 labels.
About 10 clean takes heard as mediocre (en take, wake up, fr maillots de bain, de wie) escaped every feature tried;
four of them end very abruptly (decay under 0.06 s), too few for a gate.
The 18 takes where `mos` and BAK disagreed most were set aside for the ear (`data/review/2026-10-25-mos-vs-background/`);
the background-noise study below and the re-run at the end settled BAK without them.

## Consolidation by ear (2026-10-27)

Five swaps a consolidation made onto an established voice, heard blind before and after:
the established voice won kommen, take and jeter, lost lose (reverted),
and de lieben's earlier take was heard bad.
A challenger a listener rated under the incumbent now never wins.

## Plosive pops (2026-10-27/28)

**First measure, reverted.**
The loudest 10 ms of 20–80 Hz bass inside the speech, against the loudest 100 ms of speech.
de lieben (Leni Berlin, heard popping) ranked first at +2.7 dB,
but 13 of the 15 shipped takes it ranked worst were heard okay to great:
an aspirated German p thumps as low as a breath pop (de park, Jeuwre: +10.1 dB, "within normal range").

**Second measure, adopted** (`audio_measure.pop`).
A 10 ms frame counts where its 20–300 Hz bass stands 6 dB over the speech's own bass,
6 dB over the frame's 300 Hz–8 kHz, and the 30 ms around it barely repeats (autocorrelation ≤ 0.26):
breath is turbulent, a plosive's thump is a smooth pulse under 80 Hz.
The value is the worst such frame, A-weighted, against the speech's loudest 100 ms.

| take | heard | pop dB |
|---|---|---|
| de lieben (Leni Berlin) | bad, popping | +5.9 |
| fr nom de famille (Pamputt) | bad, "very noisy, solid pop" | +5.2 |
| de ich liebe dich (Leni Berlin) | bad, "very noisy, mild pop" | +3.0 |
| fr imprimer | popping | +1.5 |
| de die Brücke | popping, "on the line" | turbulence 0.33, not counted |
| sw mboga | popping | −1.9 |
| fr enseignante, sw samaki | clean | +0.4 |
| fr payer | clean | −1.1 |
| fr petite-fille, en fee | clean | smooth, not counted |
| sw kwaheri, de pullover | popping | smooth, not counted |

Every take at +1 dB or over pops; the measure misses about half the pops heard.
A flag at +1 dB was replaced by a cost on the score, a point per 4 dB over 0 (`audio_voices.heard_mos`),
so a popping take ranks under a clean one the score puts level with it.
Of 9,540 shipped takes, 10 hold a breath burst at all, and none within 60 ms of the speech's edges:
the speech span cannot trim a pop away, so the cost stays on the score.

## Background noise (2026-10-28)

DNSMOS's overall score (`mos`) folds speech distortion and background noise into one number;
its background-noise output (BAK) was tried alone, on 414 rated takes (rank AUC, 0.5 is chance):

| measure | noisy note vs clean | bad+mediocre vs okay+ | bad vs rest |
|---|---|---|---|
| overall (`mos`) | 0.725 | 0.686 | 0.686 |
| overall less pops (the score) | 0.771 | 0.703 | 0.730 |
| BAK | 0.877 | 0.749 | 0.820 |
| BAK, played span only | 0.849 | 0.746 | 0.776 |
| noise margin (`audio_measure.noise`) | 0.933 | 0.708 | — |

BAK ranks noisy takes better, and it was not adopted:
it misses most noisy takes still above the floor (de ich liebe dich at 4.01, es Arca's countries, uk твій),
at the floor it refuses no more bad takes than the score for the same share of good ones,
and it ranks Holunder Gabriel under weaker voices, the voice ordering the overall score was chosen for.
The noise margin flags quiet, clean recordings as noisy.
Ten unheard shipped takes with the lowest BAK, heard on headphones:
2 bad (en Japanese, it secondo), 2 mediocre (es febrero, es disponible), 1 okay, 3 good, 2 great (en friend, sink, Dvortygirl).
Measured on the played span alone, their BAK barely moves: trimming does not explain the disagreement.

## Sweep swaps by ear (2026-10-28)

A dry-run sweep after verdicts stopped shielding takes proposed 451 swaps, median +0.44 score.
Thirteen at random, heard blind on headphones: the replacement won 6, lost 4, tied 3,
and the score's gain did not predict which.
196 of the 451 replaced Dvortygirl (en), whom the score puts at 2.3–2.7;
her heard takes range from great (friend, sink, under) to mediocre (election).
Only the six swaps the listener preferred were shipped.

## Established no-reference models, re-run on every verdict (2026-10-28)

All 450 rated takes whose bytes are at hand (92 bad or mediocre, 206 voices), against the score `heard_mos` (`mos` less pops):
DNSMOS SIG, BAK and P.808, UTMOS22 (strong learner), UTMOSv2, torchaudio SQUIM (objective STOI, PESQ and SI-SDR; subjective MOS against one clean reference),
NISQA (overall, noise, discontinuity, coloration, loudness), each alone, with the pop cost, as an equal-weight z-sum with the score, and as logistic fits held out by voice.
NISQA's weights are non-commercial, so it can only inform.
Three views, weighted for what the score is for, ranking voices and catching outliers:
the take AUC, the rank correlation of each voice's mean against the listener's mean verdict (28 voices with 4 or more rated takes),
and agreement on the 15 decided blind A/B pairs of the same word (consolidation and sweep sets, 3 ties left out).

| measure | AUC | Δ vs score | bad vs rest | voice ρ | Holunder Gabriel / Jeuwre / Dvortygirl rank | A/B of 15 |
|---|---|---|---|---|---|---|
| **score** (`heard_mos`) | 0.689 | — | 0.722 | 0.50 | 5 / 3 / 22 | 8 |
| `mos` | 0.673 | −0.04…+0.00 | 0.682 | 0.49 | 5 / 4 / 22 | 7 |
| DNSMOS BAK | 0.746 | −0.01…+0.14 | 0.818 | 0.48 | 15 / 2 / 27 | 9 |
| DNSMOS SIG | 0.585 | −0.17…−0.05 | 0.567 | 0.21 | 4 / 9 / 16 | 8 |
| DNSMOS P.808 | 0.602 | −0.18…+0.03 | 0.673 | 0.19 | 9 / 3 / 24 | 8 |
| UTMOS22 | 0.690 | −0.06…+0.08 | 0.651 | 0.33 | 10 / 6 / 5 | 8 |
| UTMOSv2 | 0.624 | −0.15…+0.05 | 0.602 | 0.14 | 8 / 11 / 17 | 12 |
| SQUIM MOS | 0.638 | −0.19…+0.12 | 0.688 | 0.45 | 7 / 11 / 15 | 8 |
| SQUIM PESQ | 0.762 | +0.00…+0.16 | 0.759 | 0.52 | 5 / 6 / 17 | 9 |
| SQUIM STOI | 0.746 | −0.01…+0.15 | 0.739 | 0.56 | 3 / 8 / 13 | 10 |
| SQUIM SI-SDR | 0.682 | −0.08…+0.08 | 0.619 | 0.38 | 5 / 12 / 15 | 11 |
| NISQA overall | 0.679 | −0.10…+0.11 | 0.707 | 0.32 | 23 / 5 / 24 | 10 |
| NISQA noise | 0.734 | −0.08…+0.19 | 0.860 | 0.42 | 22 / 9 / 27 | 8 |
| score + BAK | 0.733 | +0.01…+0.09 | 0.788 | 0.49 | 10 / 3 / 25 | 8 |
| score + UTMOS22 | 0.719 | −0.01…+0.08 | 0.715 | 0.40 | 6 / 3 / 16 | 10 |
| score + UTMOSv2 | 0.696 | −0.04…+0.06 | 0.692 | 0.30 | 9 / 7 / 20 | 11 |
| score + NISQA noise | 0.776 | +0.03…+0.17 | 0.868 | 0.56 | 16 / 4 / 26 | 10 |
| **score + SQUIM PESQ** | 0.759 | **+0.03…+0.12** | 0.787 | 0.54 | **4 / 3 / 20** | 10 |
| fit: score + all four SQUIM | 0.766 | +0.02…+0.15 | 0.769 | 0.57 | 3 / 5 / 20 | 10 |
| fit: score + every model | 0.798 | +0.03…+0.22 | 0.857 | 0.51 | 12 / 7 / 24 | 9 |

The listener ranks Holunder Gabriel 1st, Jeuwre 4th and Dvortygirl 7th of the 28.
The pop cost moves no model by more than 0.02 AUC.

**No model replaces the score.**
The MOS predictors (P.808, UTMOS22, UTMOSv2, SQUIM MOS, NISQA overall) rank takes no better and voices worse;
UTMOSv2 agrees on 12 of 15 A/B pairs (interval clear of the score's 8) yet ranks voices near chance (ρ 0.14), so a sweep on it would trade good voices for bad ones.
The noise measures (BAK, NISQA noise) catch bad takes best and drop Holunder Gabriel to 15th–22nd, as BAK did on 2026-10-25.
Fits over many models gain the most AUC, and every one of them demotes Holunder Gabriel or Jeuwre.

**One candidate clears the bar: the score plus SQUIM's PESQ estimate**, as an equal-weight sum of the two standardized.
PESQ is SQUIM's estimate of speech distortion, correlated 0.25 with BAK and 0.47 with the score.
- Take AUC +0.07 (0.689 → 0.759), the interval clear of zero even at 99.9%, and higher in every language: de 0.57 → 0.73, en 0.57 → 0.69, fr 0.72 → 0.82, sw 0.74 → 0.93.
- On the 38 takes heard on AirPods 0.52 → 0.67; fitted on the verdicts before 2026-10-27 and tested on the 92 since, 0.63 → 0.75.
- Voice ranking holds: ρ 0.54 against 0.50, Holunder Gabriel 4th and Jeuwre 3rd.
  Over every shipped take (99 voices with 4 or more), it lifts Holunder Gabriel from 37th to 12th, Jeuwre from 6th to 2nd and the underrated Dvortygirl from 69th to 28th.
- With 10% of okay-or-better takes refused, it refuses 47% of the bad or mediocre against the score's 36% (+0.02…+0.15).
- At the 2.8 reconsider bar it changes little: for the same 240 takes sent back, 70 of the 92 bad or mediocre instead of 66.

It was not shipped yet: it is the best of about 45 combinations tried on a sample the score itself drew,
it would bring a new model into `audio_measure` and move every take's score and bar,
and the A/B pairs (10 of 15) do not separate it from the score.
The 18 shipped takes where it and the score disagree most decide it (`plans/audio-measure.md`).
