# Audio evaluations, October 2026: pops, noise, and swaps by ear
Measures and picker changes tried against a listener's verdicts on 2026-10-27/28, with what was adopted and why.
Neighbors: the measure survey `2026-09-28-audio-quality-tools.md`, the verdicts `audio-verdicts.tsv`, the bars `../catalog/audio/README.md`.

Study scripts and per-take numbers live in the workspace, `data/reference/audio/pop-study-2026-10-28/` and `noise-study-2026-10-28/`.
Verdicts before 2026-10-28 carry no `heard_on`;
some were heard on laptop speakers, which hide noise and one-sidedness,
so an okay-or-better verdict is weak evidence of a clean take.

## Consolidation by ear (2026-10-27)

Five swaps a consolidation made onto an established voice, heard blind before and after:
the established voice won kommen, take and jeter, lost lose (reverted),
and de lieben's earlier take was heard bad.
A challenger a listener rated under the incumbent now never wins.

## Plosive pops

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

## Background noise

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

## Sweep swaps by ear

A dry-run sweep after verdicts stopped shielding takes proposed 451 swaps, median +0.44 score.
Thirteen at random, heard blind on headphones: the replacement won 6, lost 4, tied 3,
and the score's gain did not predict which.
196 of the 451 replaced Dvortygirl (en), whom the score puts at 2.3–2.7;
her heard takes range from great (friend, sink, under) to mediocre (election).
Only the six swaps the listener preferred were shipped.
