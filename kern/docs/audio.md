# Pronunciation

When audio may play, what is spoken, and how a recording is matched to a form.
Engine contract: `../README.md`.

- **When audio may play** — `PronunciationCue { Upfront, OnReveal }`,
  declared beside `EmojiCue` in `model/Presentation.kt` because it is the same kind of rule:
  what may be shown (heard) without giving the answer away.
  `pronunciationCue(role, prompt)` is `Upfront` iff the role is Recognize — the target form stands on the card from frame one —
  or the produce prompt IS the sound; `OnReveal` for a produce card that asks for that very form.
  Both apps CONSUME the cue; neither re-derives `role == Recognize` for audio.
  Which transitions actually fire, and how autoplay sits beside the auto-advance timers, is `../docs/design.md`'s.
- **What is spoken is the bare headword** — the form the card teaches, never its rendering.
  The inline article, the ♀ badge, the plural line and the area cue are grammar decoration and never reach a synthesizer;
  gender is taught by the article-colour device, not by audio.
  The recordings speak bare headwords, so this is the only rule that holds for the recorded and the synthesized branch alike.
- **Two normalizations, both normative** (`catalog/Pronunciation.kt`):
  `speechKey(form)` — trim whitespace, strip ONE leading `-` (the Swahili adjective stem citation `-zuri`),
  strip leading/trailing sentence punctuation and quote marks — `¡`/`¿` among them, because Spanish writes them and no one says them —, NFC, lowercase.
  `utterance(form)` — what a synthesizer is handed: the leading `-` gone (it gets vocalized as "minus"),
  terminal punctuation KEPT, because it carries prosody.
  `speechKey` is applied identically to a manifest's `matches` and to the visible form; nothing else folds.
- **Lookup is keyed by the MATCHED SPOKEN FORM, never by the slug.**
  `audio/<lang>/manifest.json` records, per slug, the form the recording actually speaks (`matches`).
  `AudioManifest` builds two indices — the exact NFC form, then the `speechKey` — and exact wins.
  A rotated synonym nobody recorded simply misses, and the app speaks it live:
  a card never plays a word it does not show.
- **Collision rule.** Entries sharing a `speechKey` whose bytes are IDENTICAL are one recording fetched under two slugs, and resolve.
  Entries whose bytes differ (de `husten` = cough / to cough) have no right answer,
  so the lookup returns null and the visible form is spoken live instead of guessed at.
  That state may not ship: `CatalogAudioLintTest.noAmbiguousMatchedForm` fails the build,
  and the converter resolves collisions when it generates the manifest.
- **Kern returns paths and strings, never bytes.**
  Manifests are JSON text read through `CatalogSource` like every other catalog file;
  recording paths come back catalog-relative (`audio/uk/office.mp3`),
  and every player, synthesizer and voice table stays app-side.
- **The analysis index is measurement data, never an edit** (user ruling 2026-08-01).
  An entry may carry `gain` (dB from the catalog's analysis target) and `lead` (dead air at its head, ms),
  and `Pronunciation`/`LetterRecording` carry both on as `AudioIndex` — 0/0 where the field is absent or nothing plays.
  The mp3 bytes stay the untouched Commons transcode, because re-encoding is an adaptation under BY-SA;
  the packs share no loudness and the uk letters open a second late, so what corrects them is a MEASUREMENT of the shipped bytes
  which only the player applies.
  A third measurement, `snr` (peak minus noise floor), corrects nothing and reaches no player:
  it exists so lint can hold a pack's median and bad tail, and refuse a rebuild that reintroduces removed hiss.
  What was measured, against which target and under which scheme is `scripts/audio-catalog.py`'s `ANALYSIS`;
  the sha256 gate is untouched by any of it.
- **Audio is exempt from the fingerprint.**
  `Catalog.load` reads the manifests through the RAW source, outside `FingerprintingSource`:
  recordings cannot change the join, so a refreshed pack must never restamp a `JoinStamp`
  and recompose a session that is already running.
- Surface: `Catalog.pronunciation(lang, visibleForm) -> Pronunciation(form, utterance, lang, recordingPath?, gain, leadMs)`;
  `Catalog.letterRecording(lang, glyph) -> LetterRecording(path, gain, leadMs)` for the letter drill,
  and `Catalog.letterRecordingPath` for the callers that only ask whether a letter can be played at all
  (the recording speaks the letter's NAME — the name string itself is the alphabet file's, and the manifest's
  `letters` section is the only home of letter audio and its licence data);
  `Catalog.audioCredits() -> [AudioCredit]`, grouped per (language, author, licence) with per-file rows.
  BY and BY-SA cannot share one notice, so the groups ARE the credit rows,
  and they derive from the shipped manifests, so the screen can never credit what is not bundled.
- Lint (`CatalogAudioLintTest`, real catalog, vacuously green while `catalog/audio/` is empty):
  entries name slugs their language realizes, every `matches` is reachable from a visible form,
  no ambiguous speech key, slug-named word files and codepoint-named letter files
  (glyph filenames decompose under NFD on APFS), every file ships and is referenced exactly once,
  each sha256 re-hashed against the committed bytes — Commons transcodes ship untouched,
  because re-encoding is an adaptation under BY-SA — and no author is a placeholder.
- The manifest's own schema (fields, naming rules, provenance) is `catalog/README.md`'s:
  this section owns the engine rule, not the file format.
