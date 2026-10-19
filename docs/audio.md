# Audio — how a word is spoken
Which sound plays and when, how the mutes and the audio setting interact, what is spoken and how a recording is matched to a form, and how loud it plays.
Neighbors: the recordings' format `audio-format.md` and licenses `audio-licensing.md`, the manifest's file format `../catalog/audio/README.md`, listening `listening.md`.

## When a word is spoken

- **Words are read aloud; a recording is only played for the word it actually says.**
  Kern matches recordings by the form on screen, never by concept,
  so a rotated synonym is never answered with the canonical word.
  One exception: a verb with no recording of its citation form plays one of its bare stem
  (sw `piga simu` under `kupiga simu`, `LanguageInfo.optionalVerbPrefixes`).
  Unmatched forms fall to the device voice;
  a target with neither stays silent.
  The drills' generated readings ("dreihundertsiebenundvierzig") use the voice.
  The calendar's weekday/month names (`calendar{}`) and
  the atlas' country/nationality names (`countries{}`) are recorded.
- **Voice quality matters.**
  iOS bundles only the compact voice;
  enhanced and premium are a free download under
  Settings > Accessibility > Spoken Content > Voices.
  The app checks the answering quality (`Speaker.voiceQuality`)
  and points at the download while the compact voice is active:
  a line in the audio setting and one dismissible notice on Home.
  The voice table is dropped on every foreground.
- **A review card says both its sides, each once: the word and its meaning.**
  Hearing them paired binds them, and each side is said where the card shows it.
  The meaning is the learner's own language, on until the audio setting's "Say the meaning too" turns it off.
- **The target language is spoken with its article; the learner's own language is not.**
  The voice says "das Brot"; kern decides whether there is an article (`shownArticle`).
  Where a pack recorded the article too, the recording says it
  (`scripts/audio-coverage.py --credits` lists which packs carry an `articles{}` section).
  Badge, plural line and alternates stay unspoken.
- **Audio may never give the answer away**:
  recognition speaks the word at once, produce says the meaning it asks by and waits for the reveal to say the word.
  A review card and a drill task read aloud through ONE mechanism:
  kern hands each question a `Reading` (its prompt saying and its answer saying),
  and one reader per app fires it (iOS `Reader`, Android `rememberReadAloud`).
  Every drill state must rule on both sayings (`DrillRunProgress`), so no screen decides what is heard.
- **Autoplay fires only where the card holds the learner.**
  A clean correct answer says the word too, and its flip waits for the saying to end, up to a ceiling (`READING_CEILING_MS`);
  a word cut off teaches nothing.
  A drill says every graded answer, right or wrong, and its beat waits for the reading to end;
  a timed run leaves its clean answers unsaid, since the clock is running,
  a reversed numbers task says its reading as the prompt and nothing after,
  and the letter drill says nothing more, since its question already was the sound.
  Produce fires wait for the feedback chime (`READING_CHIME_CLEARANCE_MS`);
  chimes are never ducked.
  One prompt fire and one answer fire per card, and one per drill task.

| on screen | speaks? | what is said |
|---|---|---|
| recognition prompt | yes, at once | the prompted form (rotated synonym, not canonical) |
| recognition reveal | yes, after chime | the meaning |
| write-it-out | no | already said once |
| produce prompt, asked by meaning | yes, at once | the meaning |
| produce prompt, asked by ear | yes, at once | the target word |
| produce correct | yes, after chime; the flip waits for it | the target word, or the meaning on a card asked by ear |
| near miss (typo, other form) | yes, after chime | the correction box form |
| produce revealed (Aufdecken/wrong/other word) | yes, after chime | the bare target word, or the meaning on a card asked by ear |
| trainer drill prompt (numeral, clock, date) | no | the reading IS the answer |
| reversed numbers prompt (the reading) | yes, at once; not again after the verdict | the reading |
| drill prompt in learning language (reversed run, opposites) | yes, at once | the form on the card |
| drill prompt in known language (forward run) | no | the reveal carries the voice |
| trainer drill graded answer (right, slip, miss, reveal) | yes, after chime | the answer in the learned language: the reading, the name, the word, the authored phrase (usually voice; weekday/month/country/nationality recorded) |
| listening mode (between two sayings) | yes, unattended | the meaning in the known language |
| drill answer in known language (reversed atlas) | no | prompt carries the voice instead |

- **Tapping a word says it again, past both mutes.**
  A tap is a request, so it plays under `.playback`.
  App-fired sounds stay `.ambient` (`AudioSession`).
  The audio setting's hint line names the gesture once.
- **Speaker icon follows the surface.**
  Card/drill: the speaker beside the word is the control;
  a form with no audio drops the icon; the card holds its height regardless.
  Reference pages: content is the control (tap a row to hear it),
  one hint line under the heading, shown only where the device can say the language.
  Credits: a row plays the recording it credits (never a voice in its place),
  and each row carries a small trailing link to its Commons page, the one per-row icon on any list.
  Alphabet sheet keeps glyphs (rows disagree about what they hold).
  Drill prompts in the learning language draw a speaker (same rule as a card).
  Revealed letter: no speaker (the glyph is not a form);
  dictated word: speaker (it is a word in the learning language).
- **The letter drill is the one autoplay no mute reaches.**
  Entering a screen whose only content is a sound is the request;
  plays under `.playback`. VoiceOver holds it back.
- **Reading aloud is on by default, and the silent switch silences it.**
  The top-bar switch turns it into a decision:
  OFF silences autoplay; ON lifts autoplay past a silenced phone.
  Three states, one setting for the device (not per language, not in the box).
  The middle state is iOS's: Android's media stream ignores the ringer, so there it is ON.
  The silent switch cannot be read back (no API), so it is followed by deferring to it.
  A choice made with the switch holds for the current launch;
  every launch starts back at following the phone, on both phones.
- **A card whose only content is a sound is dealt whenever the app itself is not muted.**
  The device's volume holds nothing back;
  iOS's ring/silent switch cannot be read at all, so sound-prompted cards carry
  "Can't listen right now?" on screen (`design.md`).
- **A screen about to play words at a very low volume says so.**
  Review cards and drills while reading aloud is on, the letter drill and Listening always:
  a line under the top bar asks for the volume to come up, and goes once it has.
  The threshold is kern's (`isVolumeLow`, a tenth of the range);
  iOS reads `outputVolume`, Android the media stream's volume and mute, once a second.
  Android's ringer mode is not read (silencing notifications leaves media playing).
- **Audio setting: three-way row -- No audio, Recordings, Speech.**
  "Recordings" (default): bundled recording where available, voice for the rest.
  "Speech": voice preferred, recording only where no voice exists (`soundBranch`).
  The top-bar button is the mute only; it never changes the source.
  Choosing a voice lifts autoplay past a silenced phone.
- **Source remembered per learning language; the mute is remembered nowhere.**
  A source is offered only where it can answer,
  and a stored source that cannot reads as the other one
  (`AudioCapability.preference`, over `Catalog.hasRecordings` and the device voice table).
- **Feedback chimes**: not silenced by the read-aloud switch,
  but play under whatever category it left standing.
  Chimes and words share one volume (one audio session).
  Rendered full scale (`scripts/sounds.py`), each played at its kern `Chime` level.
- VoiceOver: no autoplay talking over it (`PronounceTrigger.held`); headword labeled with its language;
  replay is an action on the word.

## What is spoken, and which recording

- **When audio may play** — `PronunciationCue { Upfront, OnReveal }`,
  declared beside `EmojiCue` in `model/Presentation.kt` because it is the same kind of rule:
  what may be shown (heard) without giving the answer away.
  `pronunciationCue(role, prompt)` is `Upfront` iff the role is Recognize —
  the target form stands on the card from frame one —
  or the produce prompt IS the sound;
  `OnReveal` for a produce card that asks for that very form.
  `meaningCue(role, prompt)` is its mirror for the learner's own side —
  `Upfront` only where the produce prompt IS the meaning —
  so each side is said once and every card pairs the word with its meaning.
  `TurnState.reading` (`session/Reading.kt`) turns the two cues into the `Saying`s a card says as it goes up and once it has `settled` —
  its answer out, or given clean, target side with its article.
  Every drill state hands over the same `Reading` (`DrillRunProgress.reading`) off the two sayings it must declare.
  Both apps CONSUME these;
  neither re-derives `role == Recognize` for audio.
  Which transitions actually fire, and how autoplay sits beside the auto-advance timers, is `design.md`'s.
- **How quiet is too quiet** — `isVolumeLow(fraction)` (`catalog/OutputVolume.kt`):
  at or below `LOW_VOLUME_FRACTION`, a tenth of the device's volume range,
  a screen about to play words asks for the volume to come up.
  Each platform reads its own volume as a fraction of its range;
  the line is kern's.
  It never holds a sound or a card back.
- **What is spoken is the headword, and on the TARGET side its article with it** —
  never the rest of the rendering:
  the form marker, the plural line and the area cue are grammar decoration and reach neither a synthesizer nor a lookup.
  `spokenTargetForm(article, shownForm, targetText)` builds that string once, for both branches:
  the synthesizer is handed it, and an `articles{}` recording is FOUND by it,
  so "die Adresse" plays where one was recorded and the bare file plays where none was.
  The source side takes no article — its grammar is not what is being taught —
  and `shownArticle` withholds one from any synonym the card rotated in,
  which is what keeps a synonym's own gender from being mislabeled by the canonical word's;
  a tagged form says the article it was authored with (`die Lehrerin`, `TaggedForm.article`).
- **Two normalizations, both normative** (`catalog/Pronunciation.kt`):
  `speechKey(form)` —
  trim whitespace, strip ONE leading `-` (the Swahili adjective stem citation `-zuri`), strip leading/trailing sentence punctuation and quote marks —
  `¡`/`¿` among them, because Spanish writes them and no one says them —, NFC, lowercase,
  and fold the INNER apostrophe class (U+0027, U+2019, U+02BC) to U+02BC —
  the class the alphabet grading already folds,
  because Commons titles French elision with `’` while the catalog writes `'`,
  and the two must key one sound.
  `utterance(form)` —
  what a synthesizer is handed:
  the leading `-` gone (it gets vocalized as "minus"), terminal punctuation KEPT, because it carries prosody.
  `speechKey` is applied identically to a manifest's `matches` and to the visible form;
  nothing else folds.
- **Lookup is keyed by the MATCHED SPOKEN FORM, never by the slug.**
  `audio/<lang>/manifest.json` keys every section but the letters by the form its recording actually speaks (`matches`),
  and files it as `<section>/<ascii stem>.mp3`,
  so no recording names the concept it was fetched for, and a merge, rename or move of one never touches audio.
  `AudioManifest` builds two indices — the exact NFC form, then the `speechKey` — and exact wins.
  Four sections feed them: `words`, the alphabet's slugless `texts`, and the two drills' own vocabularies —
  `calendar`, the weekday and month names no concept covers, and `countries`, the atlas' country and nationality names.
  One index, so a drill hears a recorded `Montag` or `Deutschland` through the very lookup a card uses,
  and no surface needs a route of its own.
  The manifest's `articles{}` section indexes TWICE, by the speech key of the whole spoken form ("die Adresse") and by the bare `word` inside it ("Adresse"),
  and `Catalog.pronunciation(lang, form, article)` tries them in that order around the bare index:
  the article form where the card shows one,
  then an exact bare recording, then the article file as a recording of its own word.
  So a word with no article recording still plays bare, one with only an article recording still plays,
  and neither route can reach a file that says a different word —
  which is what keeps the canonical article off a rotated synonym,
  where 33 of the catalog's 90 `teaches` would disagree with it (`data/reference/audio/README.md`, external workspace).
  A rotated synonym nobody recorded simply misses, and the app speaks it live:
  a card never plays a word it does not show.
- **Collision rule.** Entries sharing a `speechKey` whose bytes are IDENTICAL are one recording in two sections (a word and an alphabet text),
  and resolve;
  inside one section such a recording ships once.
  Entries whose bytes differ (de `husten` = cough / to cough) have no right answer,
  so the lookup returns null and the visible form is spoken live instead of guessed at.
  That state may not ship: `CatalogAudioLintTest.noAmbiguousMatchedForm` fails the build,
  and the converter resolves collisions when it generates the manifest.
- **Kern returns paths and strings, never bytes.**
  Manifests are JSON text read through `CatalogSource` like every other catalog file;
  recording paths come back catalog-relative (`audio/de/words/hund.mp3`),
  and every player, synthesizer and voice table stays app-side.
- **Two predicates, one per FORM and one per LANGUAGE.**
  `audible(form, lang, catalog, hasVoice)` is what a pool filters on —
  a recording of that very form, or a voice.
  `audioCapability(catalog, lang, hasVoice)` is the coarser question a surface asks before there is any form yet:
  whether a pack ships (`Catalog.hasRecordings`, a map lookup —
  `audio/<lang>/manifest.json` having been there at load) and whether the device has a voice, named apart as `RecordingsOnly` / `VoiceOnly` / `Both` / `None` because the audio setting offers one segment per source.
  A pack answers for the forms it recorded and no others, so the second never stands in for the first.
  `hasVoice` is the caller's in both, and cached in neither.
- **The analysis index is measurement data, never an edit** (user ruling 2026-08-01).
  An entry may carry `gain` (dB from the analysis target, one for every output route), `cap` (what the converter's peak ceiling held back from it, 0 where the loudness number stood) and `lead` (dead air at its head, ms),
  and `Pronunciation`/`LetterRecording` carry them on as `AudioIndex` —
  0/0 for `gain`/`lead` where a field is absent or nothing plays.
  A cap is spent ONLY under a fade, which is the one thing that opens the headroom it was taken for (`fadedGainDb`);
  at full volume nothing reads it.
  A platform applies it as given and never mints a number of its own.
  The mp3 bytes stay the untouched Commons transcode (`audio-licensing.md` §3);
  the packs share no loudness and the uk letters open a second late,
  so what corrects them is a MEASUREMENT of the shipped bytes which only the player applies.
  A third measurement, `mos` (how good the take sounds, a predicted mean opinion score), corrects nothing and reaches no player:
  it exists so lint can hold a pack's median and bad tail, and refuse a rebuild that reintroduces removed hiss.
  A fourth, `gate` (the file's noise level plus a margin, dBFS of the raw bytes, null where none was measured), rides on `AudioIndex.gate` to a downward expander that quiets that hiss at playback.
  What was measured, against which target and under which scheme is `scripts/audio_index.py`'s `ANALYSIS`;
  the sha256 gate is untouched by any of it.
- **Audio is exempt from the fingerprint.**
  `Catalog.load` reads the manifests through the RAW source, outside `FingerprintingSource`:
  recordings cannot change the join,
  so a refreshed pack must never restamp a `JoinStamp` and recompose a session that is already running.
- Surface: `Catalog.pronunciation` -> `Pronunciation`;
  `Catalog.letterRecording` -> `LetterRecording?` for the letter drill,
  and `Catalog.letterRecordingPath` for the callers that only ask whether a letter can be played at all (the recording speaks the letter's NAME —
  the name string itself is the alphabet file's,
  and the manifest's `letters` section is the only home of letter audio and its attribution);
  `Catalog.audioCredits` -> `[AudioCredit]`, grouped per (language, author, license) with per-file rows, each carrying the `Pronunciation` of its own file so the row can play exactly what it credits.
  BY and BY-SA cannot share one notice, so the groups ARE the credit rows,
  and they derive from the shipped manifests, so the screen can never credit what is not bundled.
- Lint (`CatalogAudioLintTest`, `CatalogAudioProvenanceTest`, real catalog):
  every entry is keyed by the form it speaks, and that form stands on some card of its language —
  any realization's `text`, `teaches`, `accepts` or tagged form, or a verb's bare stem —
  an article entry's article being one a card shows in front of its word;
  no ambiguous speech key, ASCII-stem-named form files and codepoint-named letter files (glyph filenames decompose under NFD on APFS), every file ships and is referenced exactly once, each sha256 re-hashed against the committed bytes —
  Commons transcodes ship untouched (`audio-licensing.md` §3) —
  every `authors` and `licenses` row is used by some recording, and no author is a placeholder.
- **Playback trusts the index only so far** (`catalog/Playback.kt`).
  `Playback.GAIN_LIMIT_DB` is the converter's own clamp and the single home of the number:
  the manifest parser rejects a gain outside ±it and `Playback.gainDb(measured)` clamps into it,
  which are one rule about what a measurement may claim, not two that happen to agree.
  `Playback.OUTPUT_DB` is the app's one output level against the loudness target the recordings are indexed and the chimes leveled to:
  recordings (`Playback.levelDb`), synthesized speech and chimes all play at it.
  `Playback.headMs(leadMs, durationMs)` answers the lead only where `0 < leadMs < durationMs`, else 0:
  a lead that would swallow the whole recording is a broken measurement,
  and the recording is still worth playing whole —
  as is one whose duration the platform will not report.
  `Playback.gateThresholdDb(gate, appliedGainDb)` is the threshold for a gate placed after the gain stage (gate + applied gain);
  a gate before the gain takes `gate` as it stands.
  The expander's shape is `Playback`'s too —
  `GATE_EXPANSION_RATIO` (gentle, so a tail fades rather than cuts), `GATE_ATTACK_MS`, `GATE_RELEASE_MS` —
  and the parser rejects a `gate` outside `GATE_FLOOR_DB..0`.
  Everything in device units — linear volume, millibels, sample frames — stays app-side.
- **Which voice speaks a language** (`catalog/VoiceSelection.kt`).
  `preferredTag(lang)` widens "es" to "es-ES" and leaves every other code as it is:
  Spanish is taught in the peninsular variety (distinción), and a Latin-American voice would teach seseo.
  `select(lang, candidates)` is the same rule where an inventory can be searched —
  candidates are the voices whose tag IS the code or a region of it ("de" takes "de-AT"), the Spanish pool narrows to peninsular voices wherever the device has any (the variety outranks quality),
  and within the pool the highest quality wins with ties going to the lower identifier,
  so one device picks the same voice every time.
  Enumerating voices, and mapping a platform's quality scale onto `Candidate.quality`, stays app-side.

## Evidence

- Audio alone trains the ear, and the written form beside it carries form and meaning (Sydorenko 2010, *Language Learning & Technology* 14(2)) — why a card says each side where it shows it.
