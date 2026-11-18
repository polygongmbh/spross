# Audio leveling: what the literature says about short speech items
Why sw words sound quiet, what standards and loudness research say about leveling single words on headphones and phone speakers, and a proposed method; sections run findings → what is wrong → method → expected medians → next steps.
Neighbors: the manifest fields `../../catalog/audio/README.md`, how the engine plays them `../../kern/docs/audio.md`, when they play `../read-aloud.md`.

## Question

Swahili words sound much quieter than every other language, most of all on the phone speaker.
Median shipped gains: sw `gain` -6.1, `gainPhone` -11.4, where every other language sits between -1.3 and +5.2.
The measurement in `scripts/audio-catalog.py` (`ANALYSIS`, `playback_index`) and `scripts/audio_measure.py` (`SPEAKER_LENS`) was reasoned out in-house.
This plan checks it against the standards and the loudness literature,
and re-measures all 7097 shipped word, calendar, country and article recordings with each standard descriptor.

Prototype, data and listening set (not in the repo): `$SCRATCH/leveling/`
(`measures.py` descriptors, `level.py` full run into `levels.json`, `render.py` and `play.sh` listening set).

## Findings

### 1. Integrated LUFS does not over-read the squashed sw takes

- Moore, Glasberg & Stone (2003), "Why are commercials so loud?", JAES 51(12):1123–1132:
  at EQUAL rms, amplitude-compressed speech sounds LOUDER than uncompressed speech.
  BS.1770 is an energy measure, so if anything it under-reads a limited voice.
  It never over-reads one, so the limiter cannot explain a voice that sounds too quiet.
- Measured: Waithera Were's raw takes are the loudest voice in the catalog by every descriptor
  (median -11.7 LUFS raw, peak-to-loudness 10.6 dB against 14–19 dB for every other main voice).
  The cut she gets is what every model asks for.

### 2. The 400 ms block is not the problem for 0.5–1.5 s words

- ITU-R BS.1770-5 integrates K-weighted 400 ms blocks (75 % overlap) behind a -70 LUFS absolute and a -10 LU relative gate.
  A 0.8 s word yields about 5 blocks, so integrated loudness is the mean of the word's blocks, as intended.
- EBU Tech 3341 max momentary loudness (the 400 ms window's maximum) tracks integrated loudness on this catalog with a per-file SD of 0.7 dB.
  ITU-T P.56 active speech level (method B: 30 ms envelope, 200 ms hangover, 15.9 dB margin) tracks it with an SD of 0.8 dB.
  Per voice, the medians of all three agree within about 1 dB.
- EBU R 128 s1 (short-form content) adds a cap on Max Short-term loudness (a 3 s window, -18 LUFS).
  EBU Tech 3342 loudness range is built from 3 s values.
  Neither means anything for clips shorter than 3 s.
  No EBU or ITU document gives a separate rule for items under one second.

### 3. Loudness models for time-varying sounds give the same verdict, with more scatter

- ISO 532-1:2017 (Zwicker, time-varying method) names N5, the loudness exceeded 5 % of the time, as the overall loudness of a time-varying sound.
  Some instrument guidance gives N7 for speech.
- Glasberg & Moore (2002), JAES 50(5):331–342, define short-term loudness as the loudness of "a single word".
  Zorila, Stylianou, Flanagan & Moore (2016), JASA 140:402–408, matched the loudness of processed and unprocessed sentences best with the PEAK of long-term loudness.
  Moore-Glasberg is standardized as ISO 532-2 (stationary) and ISO 532-3:2023 (time-varying).
- Schlittenlacher, Hashimoto, Kuwano & Namba (2017), JASA 142(4):1841, find an energy mean of loudness level (LLP) predicts overall judgments better than N5 for long sounds,
  and that listeners weight a sound's prominent portions.
  For a single word the two land close together.
- Measured (MOSQITO's ISO 532-1 implementation, every clip at about 65 dB SPL):
  N5, Nmax and LLP each move single files by about 2 dB against LUFS (SD 1.9–2.1 dB).
  Per voice they move medians by up to ±2.5 dB.
  For Waithera they ask for 2.0 dB MORE cut on headphones than LUFS does.

### 4. A phone-speaker plane is legitimate, but there is no standard curve, and the lens is half a model

- A loudness model of what the speaker radiates is the standard way to predict loudness through a band-limited device.
  So a separate phone-plane gain is sound in principle.
- No standard weights media playback through a phone loudspeaker.
  The nearest ones are telephony masks: ITU-T P.341 wideband send/receive, 3GPP TS 26.131 hands-free receive, ITU-T P.48 IRS.
  ITU-T P.863 (POLQA) and P.56 level speech for telephony tests at a fixed active level (commonly -26 dBov).
  They prescribe no weighting for loudspeaker playback.
- Micro-speaker physics: a sealed back volume puts the resonance near 800–1000 Hz, with output falling 12 dB per octave below it.
  The system F3 is about 800 Hz for a 1 cm³ enclosure.
  The current lens (-20 dB below 450 Hz, -8 dB at 800 Hz, flat from 1.2 kHz) is close to this, though shallower below 450 Hz.
- The lens is half a model.
  It shapes what the speaker emits, then hands that to K-weighting.
  K-weighting does not depend on level, and it ignores critical-band summation:
  the ear's half that ISO 532 supplies.
  The chimes' own KDoc (`Playback.kt`, `Chime`) already notes K-weighting hearing a near-sine under a word at equal LUFS.
- Measured: ISO 532-1 N5 through a textbook sealed micro-speaker
  (2nd-order high-pass at 800 Hz, Q 0.9, gentle 10 kHz low-pass)
  gives sw a median phone gain of -11.6, against today's -11.4.
  The physics agrees with the current phone plane too.

### 5. Target: the pack median is relative, the standards are absolute

- BS.1770 and R 128 (-23 LUFS), AES streaming guidance and P.56 (-26 dBov) all level to an ABSOLUTE target.
  The median of the packs moves whenever a pack is added or re-voiced,
  and it ties nothing to the synthesized speech and the chimes that play in the same session.
- Measured on the ISO 532-1 scale at the same calibration, before the shared `OUTPUT_DB`:
  - the word target is 86.1 phon, and 80.6 through the phone model;
  - macOS synthesized voices (`say`, a proxy; the iOS voices are unmeasured) reach 84–86 phon, and 76–83 through the phone model;
  - at their shipped gains the chimes reach 75–87 phon (`correct` 77.7, `almost` 74.7, `wrong` 83.2, `reveal` 78.9, `cheer` 87.0);
  - through the phone model, `reveal` drops to 67.6.

## What is wrong with the current method

The descriptor is not what is wrong.
Every standard measure, from BS.1770 to P.56 to ISO 532-1, puts Waithera's words at or above the target under today's gains.
None of them predicts the quietness the ear reports.
What the method does lack:

- **No ground truth.**
  Nothing in the pipeline was ever checked against a listener matching two voices' loudness,
  which is how BS.1770 itself was fitted (Soulodre 2004, AES 116th Convention, paper 6161).
  A model with a 2 dB scatter per file needs that check per voice.
- **The phone curve is a guess.**
  The extra 5.3 dB that `gainPhone` takes off Waithera on top of `gain` depends entirely on how much low-mid the assumed speaker drops.
  Real phones run DSP: bass extension, multiband limiting and speaker protection.
  If the user's phone radiates more low-mid than the model,
  the bass-heavy voices (Jeuwre, Галя Раптова) come out louder than predicted,
  and the bright Waithera comes out relatively quieter.
  This is the most likely physical reason the gap is worst on the phone.
- **Half a loudness model on the phone plane.**
  A filter plus K-weighting does not model level, critical bands or a near-sine chime.
- **A relative target.**
  It does not anchor the words to the TTS or the chimes.
  (`OUTPUT_DB` is -8 in `Playback.kt`.
  The `Chime` KDoc's "words land at -16.7 LUFS" matches no current constant.)

Candidates outside loudness, worth one listening check each:
Waithera's takes are band-limited (almost nothing below 250 Hz) and limiter-distorted (DNSMOS median 2.75).
A thin, lo-fi voice can read as "far away", and a listener may report that as "quiet".
No gain fixes that; replacing the recordings does.

## Proposed method

1. **Descriptor: ISO 532-1 time-varying loudness N5, in phon**, for every recording, TTS reference phrase and chime, on one scale.
   It is the standardized descriptor for time-varying sounds.
   It models level dependence, critical bands and temporal integration,
   and it ranks words and near-sine chimes on one axis.
   Moore-Glasberg peak long-term loudness (ISO 532-3) is equally grounded (Zorila 2016) and can replace it once an open implementation is vendored.
   The peak cap at -1 dBFS stays as it is.
2. **Absolute target, anchored to the synthesized speech**:
   one phon value per plane, set to the median N5 of a fixed set of TTS reference phrases per language.
   The chimes are re-leveled to sit a stated distance from that value on the same scale.
3. **Phone plane: the same model, run on the output of a MEASURED speaker response.**
   Measure it once for the reference iPhone and Android phone (a swept sine through the speaker, recorded with a measurement mic at 30 cm),
   and keep the curve as data beside the analysis.
   Until then, use the sealed micro-speaker model above, never a hand-drawn lens.
   If the listening check (step 4) prefers `noplane`, drop the plane.
4. **Ear check before shipping: per-voice loudness matching.**
   Use the method of adjustment against one reference voice, for each voice with at least 15 takes (about 35 voices, 3 words each).
   Where the ear differs from the model by more than its 2 dB per-voice scatter, ship a per-voice trim.
   That trim is measurement data like `gain`, carried per author rather than per file.

Cost: MOSQITO (Apache-2.0) runs ISO 532-1's nonlinear decay as a Python loop.
The full catalog takes about 40 minutes on 10 cores (the prototype batches clips by rows), against seconds for `ebur128`.
That is acceptable for a `--reindex` and too slow for every build.

## Expected per-language medians

The prototype's targets keep today's catalog-wide median gain, so only the split between voices moves.

| lang | `gain` now | proposed | `gainPhone` now | proposed |
|---|---|---|---|---|
| de | -1.3 | -1.4 | -0.7 | -1.5 |
| eo | +5.2 | +3.5 | +2.8 | +2.5 |
| es | +2.9 | +1.9 | -0.3 | +0.9 |
| fr | +1.5 | +1.1 | +2.2 | +1.0 |
| it | +3.3 | +2.1 | +2.9 | +1.9 |
| sw | -6.1 | -7.9 | -11.4 | -11.6 |
| uk | -1.2 | -0.8 | -0.1 | -0.3 |

Within sw: Waithera Were -6.3 → -8.3 (phone -11.7 → -12.0),
Goethe-Institut Cameroon -4.1 → -3.5 (-2.6 → -3.5),
Ismail Ibn Ahmed +6.1 → +4.8 (+5.5 → +3.4).
The grounded method does NOT lift sw.
It tightens the other packs by 1–2 dB, and it would lower sw a little further.
If the ear still hears sw as quiet after this, the cause is the device curve or the recordings, not the loudness descriptor.

## Next steps

1. Listen: `$SCRATCH/leveling/play.sh`.
   - `flat`: `raw` · `current` · `proposed`.
   - `phone` (micro-speaker model): `current` · `proposed` · `noplane`.
   - `listen/real/`: the phone-gain renders WITHOUT the filter, to AirDrop and play on the phone's own speaker.
2. If `noplane` or `real/` beats `current` on the phone, measure the device curve (step 3 above) before any re-index.
3. Run the per-voice matching (step 4), then decide between the descriptor change and per-voice trims.
