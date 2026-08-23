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
- **What is spoken is the headword, and on the TARGET side its article with it** —
  never the rest of the rendering: the ♀ badge, the plural line and the area cue are grammar decoration
  and reach neither a synthesizer nor a lookup.
  `spokenTargetForm(article, shownForm, targetText)` builds that string once, for both branches:
  the synthesizer is handed it, and an `articles{}` recording is FOUND by it,
  so "die Adresse" plays where one was recorded and the bare file plays where none was.
  The source side takes no article — its grammar is not what is being taught —
  and `shownArticle` withholds one from any form the card rotated in, which is what keeps
  a synonym's own gender from being mislabeled by the canonical word's.
- **Two normalizations, both normative** (`catalog/Pronunciation.kt`):
  `speechKey(form)` — trim whitespace, strip ONE leading `-` (the Swahili adjective stem citation `-zuri`),
  strip leading/trailing sentence punctuation and quote marks — `¡`/`¿` among them, because Spanish writes them and no one says them —, NFC, lowercase,
  and fold the INNER apostrophe class (U+0027, U+2019, U+02BC) to U+02BC — the class the alphabet grading already folds,
  because Commons titles French elision with `’` while the catalog writes `'`, and the two must key one sound.
  `utterance(form)` — what a synthesizer is handed: the leading `-` gone (it gets vocalized as "minus"),
  terminal punctuation KEPT, because it carries prosody.
  `speechKey` is applied identically to a manifest's `matches` and to the visible form; nothing else folds.
- **Lookup is keyed by the MATCHED SPOKEN FORM, never by the slug.**
  `audio/<lang>/manifest.json` records, per slug, the form the recording actually speaks (`matches`).
  `AudioManifest` builds two indices — the exact NFC form, then the `speechKey` — and exact wins.
  The manifest's `articles{}` section indexes TWICE, by the speech key of the whole spoken form
  ("die Adresse") and by the bare `word` inside it ("Adresse"), and `Catalog.pronunciation(lang,
  form, article)` tries them in that order around the bare index: the article form where the card
  shows one, then an exact bare recording, then the article file as a recording of its own word.
  So a word with no article recording still plays bare, one with only an article recording still
  plays, and neither route can reach a file that says a different word —
  which is what keeps the canonical article off a rotated synonym, where 33 of the catalog's 90
  synonyms would disagree with it (`data/reference/audio/README.md`).
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
  An entry may carry `gain` (dB from the full-range analysis target), `gainPhone`
  (the same loudness through the phone-speaker plane, null where none was measured — letters and texts),
  `cap`/`capPhone` (what the converter's peak ceiling held back from each of those, 0 where the loudness number stood)
  and `lead` (dead air at its head, ms), and `Pronunciation`/`LetterRecording` carry them on as `AudioIndex` —
  0/0 for `gain`/`lead` where a field is absent or nothing plays, `gainPhone`/`capPhone` null where no phone plane was measured.
  A cap is spent ONLY under a fade, which is the one thing that opens the headroom it was taken for (`fadedGainDb`);
  at full volume nothing reads it.
  A platform picks the plane by its output route and never mints a number of its own.
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
- Surface: `Catalog.pronunciation(lang, visibleForm) -> Pronunciation(form, utterance, lang, recordingPath?, gain, gainPhone?, leadMs)`;
  `Catalog.letterRecording(lang, glyph) -> LetterRecording(path, gain, gainPhone?, leadMs)` for the letter drill,
  and `Catalog.letterRecordingPath` for the callers that only ask whether a letter can be played at all
  (the recording speaks the letter's NAME — the name string itself is the alphabet file's, and the manifest's
  `letters` section is the only home of letter audio and its attribution);
  `Catalog.audioCredits() -> [AudioCredit]`, grouped per (language, author, license) with per-file rows.
  BY and BY-SA cannot share one notice, so the groups ARE the credit rows,
  and they derive from the shipped manifests, so the screen can never credit what is not bundled.
- Lint (`CatalogAudioLintTest`, real catalog, vacuously green while `catalog/audio/` is empty):
  entries name slugs their language realizes, every `matches` is reachable from a visible form,
  no ambiguous speech key, slug-named word files and codepoint-named letter files
  (glyph filenames decompose under NFD on APFS), every file ships and is referenced exactly once,
  each sha256 re-hashed against the committed bytes — Commons transcodes ship untouched,
  because re-encoding is an adaptation under BY-SA —
  every `authors` and `licenses` row is used by some recording, and no author is a placeholder.
- The manifest's own schema (fields, naming rules, provenance) is `catalog/audio/README.md`'s:
  this section owns the engine rule, not the file format.
- **Playback trusts the index only so far** (`catalog/Playback.kt`).
  `Playback.GAIN_LIMIT_DB = 20.0` is the converter's own clamp and now the single home of the number:
  the manifest parser rejects a gain outside ±it and `Playback.gainDb(measured)` clamps into it,
  which are one rule about what a measurement may claim, not two that happen to agree.
  `Playback.headMs(leadMs, durationMs)` answers the lead only where `0 < leadMs < durationMs`, else 0:
  a lead that would swallow the whole recording is a broken measurement,
  and the recording is still worth playing whole — as is one whose duration the platform will not report.
  Everything in device units — linear volume, millibels, sample frames — stays app-side.
- **Which voice speaks a language** (`catalog/VoiceSelection.kt`).
  `preferredTag(lang)` widens "es" to "es-ES" and leaves every other code as it is:
  Spanish is taught in the peninsular variety (distinción), and a Latin-American voice would teach seseo.
  `select(lang, candidates)` is the same rule where an inventory can be searched —
  candidates are the voices whose tag IS the code or a region of it ("de" takes "de-AT"),
  the Spanish pool narrows to peninsular voices wherever the device has any (the variety outranks quality),
  and within the pool the highest quality wins with ties going to the lower identifier,
  so one device picks the same voice every time.
  Enumerating voices, and mapping a platform's quality scale onto `Candidate.quality`, stays app-side.
