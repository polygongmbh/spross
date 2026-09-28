# Audio quality tools: a survey of established no-reference methods
Established no-reference speech-quality tools tried against a listener's verdicts, and why DNSMOS was adopted.
Neighbors: the hand-built attempt `2026-09-28-audio-quality-measure.md`, the verdicts `audio-verdicts.tsv`, the fields `../catalog/audio/README.md`.

Research spike: does an established no-reference speech-quality tool agree with a human
listener better than the hand-rolled measures in `scripts/audio_measure.py`
(`noise_margin`, 51% pairwise; `quality`, 70% pairwise — `docs/2026-09-28-audio-quality-measure.md`)?
Code: `/private/tmp/claude-501/-Users-tj-IT-duolernen-app/6a5f5b76-f842-46e7-869f-4c463a491230/scratchpad/quality2/`
(`run_all.py` and `voice_scan.py` produced the numbers below; `wada_snr.py` is a direct port
of the reference WADA-SNR implementation).
Nothing here is wired into the app or the catalog tooling; the venv lives entirely under that
scratchpad path and touches nothing under `catalog/` or `data/`.

## Dataset

`docs/audio-verdicts.tsv` has 65 rows.
63 resolved to real bytes under `catalog/audio/` or `data/review/` (recursive sha256, no
Commons downloads); 2 skipped, both Vion Nicolas French takes ("couverture", "crème") whose
bytes have since been replaced in the catalog.

## Survey

| tool | measures | license | install weight | offline, arm64 | run? |
|---|---|---|---|---|---|
| **DNSMOS** (Microsoft, via `speechmos` pip package) | overall/signal/background MOS (P.835) + a P.808 overall score, from raw waveform | MIT (code and bundled ONNX weights) | light — `onnxruntime` + `librosa` + `numpy`, ~150 MB venv; the two ONNX files it actually uses are 1.3 MB combined | yes, CPU ONNX | **yes** |
| **torchaudio SQUIM** (Meta) | objective: reference-free PESQ/STOI/SI-SDR; subjective: MOS via non-matching references (NMR) | BSD-3 (torchaudio) | heavy — `torch`+`torchaudio` wheels, ~600 MB, plus ~400 MB of downloaded pipeline weights on first use | yes, CPU | **yes** |
| **NISQA** (TU Berlin) | overall MOS + 4 dimensions: noisiness, coloration, discontinuity, loudness | code MIT; **model weights CC BY-NC-SA 4.0** (non-commercial) | needs `torch` (already paid for by SQUIM); the weight files themselves are ~1 MB each | yes, CPU | **yes** |
| **WADA-SNR** (Kim & Stern 2008) | single blind SNR number from the waveform's amplitude distribution | no license on the reference port; algorithm itself is a public formula | none — numpy only | yes | **yes** |
| Brouhaha (pyannote) | VAD + SNR + C50 reverberation, multi-task | MIT code; model hosted as `pyannote/brouhaha` on Hugging Face, which gates most `pyannote` checkpoints behind account + token acceptance | heavy — pulls in `pyannote-audio`'s full dependency chain | uncertain — likely needs an HF account/token, which the brief rules out | no — skipped over the account/token risk before spending install time on it |
| UTMOS / SpeechMOS (SSL-backbone MOS predictors) | subjective MOS from a wav2vec2-style backbone | code MIT-ish, weights research-license | heavy — a full self-supervised speech backbone, comparable to or bigger than SQUIM | yes | no — redundant with DNSMOS + SQUIM's own MOS predictor for the budget available |
| ITU-T P.563 | single-ended MOS per the ITU recommendation | the maintained ports state "solely for evaluation of ITU-T P.563 ... any other use requires a licence" — not a clean open-source license | C source, needs compiling | yes, in principle | no — licensing rules out shipping it, so not worth the build time |
| Homebrew | — | — | — | — | **no CLI tool found.** The only speech-flavored formula (`speech`) is an on-device ASR/TTS/VAD/diarization toolkit, not a quality scorer; nothing on `formulae.brew.sh` does no-reference SNR/MOS estimation. The candidates above are Python packages/models, not CLIs — DNSMOS could be wrapped as one, cheaply, but nothing ships as one today. |

## Evaluation against both baselines

63 rows, Spearman rho against the ordinal verdict (great=4…bad=0), pairwise ordering accuracy
over label pairs with different verdicts, and bad-vs-rest AUC.
Higher is better for every metric below (all already point the "better recording" direction).

| metric | n | rho | pairwise acc | bad-vs-rest AUC |
|---|---:|---:|---:|---:|
| `noise_margin` (shipped baseline) | 62 | 0.05 | 0.52 | 0.63 |
| `quality` (hand-rolled baseline, 70%) | 63 | 0.42 | 0.69 | 0.79 |
| DNSMOS `ovrl_mos` | 63 | 0.24 | 0.61 | 0.69 |
| DNSMOS `sig_mos` | 63 | 0.15 | 0.56 | 0.66 |
| DNSMOS `bak_mos` | 63 | 0.23 | 0.60 | 0.66 |
| DNSMOS `p808_mos` | 63 | 0.06 | 0.52 | 0.63 |
| WADA-SNR | 63 | −0.19 | 0.42 | 0.44 |
| SQUIM objective STOI | 63 | 0.26 | 0.62 | 0.69 |
| SQUIM objective PESQ | 63 | 0.33 | 0.65 | 0.75 |
| SQUIM objective SI-SDR | 63 | 0.02 | 0.50 | 0.56 |
| SQUIM subjective MOS (3-NMR average) | 63 | 0.13 | 0.56 | 0.71 |
| NISQA `mos_pred` | 63 | −0.11 | 0.45 | 0.54 |
| NISQA `noi_pred` (noisiness) | 63 | 0.31 | 0.64 | 0.79 |
| NISQA `dis_pred` (discontinuity) | 63 | −0.42 | 0.31 | 0.33 |
| NISQA `col_pred` (coloration) | 63 | −0.24 | 0.40 | 0.41 |
| NISQA `loud_pred` (loudness) | 63 | −0.24 | 0.39 | 0.36 |
| unweighted 3-way MOS ensemble (DNSMOS ovrl + SQUIM MOS + NISQA mos, no fitting) | 63 | 0.09 | 0.54 | 0.72 |
| DNSMOS ovrl + SQUIM PESQ (z-score average, LOO-checked) | 63 | 0.36 (LOO 0.36) | 0.66 (LOO 0.66) | 0.78 |

No new tool beats the hand-rolled `quality` combo on this narrow pairwise benchmark.
NISQA's `dis_pred`, `col_pred`, and `loud_pred` are all *negatively* correlated with the
verdict — on 63 mostly noise/breathiness-labeled rows they are measuring something these
labels don't track, not a useful discontinuity/coloration/loudness signal for this data.
WADA-SNR is worse than the shipped `noise_margin` it was meant to improve on; on these tightly
trimmed, silence-free clips it saturates at its 100 dB ceiling for several genuinely-fine files
and inherits the same floor-vs-peak misreading of smooth delivery as noise.

## Voice-level sanity check

The 63 labeled rows include only 3 Vion Nicolas takes, 2 of which were deliberately picked as
his *worst*-scoring takes in an earlier consolidation pass — not a fair sample of the "mostly
very fine" voice-level claim.
So this check scores each named voice's **actual shipped catalog** instead: all files for the
small voices, a fixed random sample of 60 for the three voices with more (Natschoba, Jeuwre,
Vion Nicolas — `voice_scan.py`, seed 42).

| voice | n | `quality` (baseline) | `noise_margin` (baseline) | DNSMOS `ovrl_mos` | SQUIM MOS |
|---|---:|---:|---:|---:|---:|
| de Holunder Gabriel (gold standard) | 4 | 0.27 | 50.65 | **3.14** | 4.09 |
| de Jeuwre (mostly clean) | 60 | 0.09 | 104.05 | **3.11** | 4.23 |
| de Natschoba (decent, less pristine) | 60 | 0.03 | 74.80 | **2.93** | 4.20 |
| fr Vion Nicolas (mostly fine) | 60 | −0.57 | 64.48 | **2.89** | 4.02 |
| de MiER (a bit noisy) | 7 | −0.19 | 49.68 | **2.87** | 4.21 |
| de Jakob.scholbach (noisy, echoey) | 1 | −0.47 | 47.78 | **2.78** | 2.33 |

**DNSMOS `ovrl_mos` is the only measure tried, including both baselines, that gets every
voice into its stated bucket**: Holunder Gabriel and Jeuwre on top, Natschoba next, Vion
Nicolas above the two voices actually flagged noisy, MiER and Jakob.scholbach at the bottom.
`quality` reproduces its known failure exactly and worse than the single-row estimate that
first flagged it: Vion Nicolas's *full-catalog* median (−0.57) is now the single worst of any
voice checked, below even Jakob.scholbach — the bias generalizes across his whole ~265-file
catalog, not just the 3 labeled rows.
`noise_margin` still under-ranks Holunder Gabriel below Natschoba/Jeuwre, its own known bug.
SQUIM MOS gets Vion Nicolas and Jakob.scholbach right but rates MiER (4.21) as high as the
clean voices, missing "a bit noisy" entirely.
NISQA's `mos_pred` inverts hardest of all: Holunder Gabriel medians 2.70, second-worst of the
six voices, behind MiER — plausibly a domain-mismatch artifact, since NISQA was trained on
6–12 second communication-channel clips, not sub-second silence-free single words.
The DNSMOS+SQUIM-PESQ combo that scored well on the pairwise benchmark does *worse* here than
DNSMOS alone: it ranks MiER above Natschoba, reintroducing a voice-level miss — a reminder
that a higher pairwise number is not the same claim as an unbiased one.

## Runtime per file (this Mac, single file, warm model)

| tool | ms/file | 7,000-file estimate |
|---|---:|---|
| `noise_margin` (shipped) | ~40 | ~5 min (already parallel, 10 workers) |
| `quality` (shipped) | ~210 | ~25 min single-threaded |
| DNSMOS (`speechmos`) | ~280 serial, ~225 at 4 threads | ~26–33 min |
| NISQA (batched via `predict_csv`) | ~15–25 after model load | ~2–3 min |
| SQUIM (objective + subjective, 3 NMR) | ~265 | ~31 min |
| WADA-SNR | <1 | negligible |

NISQA's batched CPU inference is by far the fastest of the new tools — a lightweight CNN +
self-attention model, not a large SSL backbone — but its accuracy numbers above rule it out on
its own. DNSMOS threads only modestly (ONNX Runtime already parallelizes internally per
session), so a 4-worker run buys roughly 20%, not 4x.

## Recommendation

**DNSMOS `ovrl_mos`, via the `speechmos` pip package** (or the two vendored ONNX files it
actually needs: `dnsmos_models/sig_bak_ovr.onnx` and `dnsmos_models/model_v8.onnx`, 1.3 MB
combined, MIT-licensed, no pip dependency at all if vendored and re-run through `onnxruntime`
directly).

This is a real trade-off, stated plainly: on the 63-row pairwise benchmark alone, the existing
hand-rolled `quality` combo still wins (69% vs. 61%).
But `quality`'s specific, named failure — scoring Vion Nicolas as the worst voice in the
catalog because of his recording setup rather than his actual sound — is exactly what this
research was commissioned to fix, and DNSMOS is the only candidate, tool or baseline, that
gets every named voice into its correct relative bucket.
An established, peer-published, MIT-licensed model that is merely *good* and unbiased beats a
hand-rolled score that is sharper on 63 labels but demonstrably confounded by whose equipment
recorded the file.

**How it would be run from `scripts/audio_measure.py`:**
- dependency: `onnxruntime` (CPU) + `librosa` (for the mel-spectrogram front end) — both new
  to the project, which currently does all of this with `ffmpeg` + `numpy` alone; that's a
  real convention change worth a maintainer decision, not something to slip in quietly.
- model files: the two ONNX files above, vendored into the repo (1.3 MB) rather than pulled
  in via the full `speechmos` package (which also bundles AECMOS/PLCMOS models this project
  doesn't need).
- call shape: decode to 16 kHz mono float32 (already how `noise_margin` decodes), run the two
  ONNX sessions once per ~9 second window (our clips are shorter, so one window covers the
  whole file), read off `ovrl_mos`.
- runtime: ~0.28 s/file single-threaded; a 7,000-file catalog pass is 26–33 minutes, in the
  same range as the existing `quality` measure, not the multi-hour range the budget worried
  about.

**Honest caveat.** DNSMOS's pairwise accuracy (61%) is a real, not dramatic, improvement over
the shipped `noise_margin` (51%) and is below the hand-rolled `quality` (69%).
If within-batch ranking sharpness matters more than cross-voice fairness for a given use (for
example, picking the best of several takes from the *same* speaker), `quality` remains the
better tool for that narrower job.
Nothing tried here clears both bars — sharp *and* unbiased — at once; combining DNSMOS with a
second orthogonal feature (bandwidth/rolloff, per the earlier spike) is the most promising
next step but wasn't in scope to fit and re-validate here without risking exactly the kind of
overfit-to-65-labels result this brief was written to avoid.
