# Drill runs — what a drill is made of and how a run plays it
The language packs, frames and letter drill that generate a drill's prompts, the run machines that play them, the ladders, and what a run stores.
Neighbors: the hub and the Sprosse `drills.md`, each drill `drills-tables.md` and `drills-words.md`, per-language readings `number-forms.md`, grading `grading.md`.

## What a drill is made of

- One `:kern` module, package `net.spross.kern.trainer`,
  with `Long` cardinals everywhere (Kotlin `Int` is 32-bit on all platforms).
  The registry (`trainerPacks`) holds de/en/es/sw/uk/eo/fr/it;
  a language outside it has no drills (the hub's handling of that is an app rule).
- `Catalog.phraseTemplates(source, target)` is the frames' half of the card join:
  one `PhraseTemplate` per frame realized in BOTH languages, directional like a `Card`,
  with `count`/`masculineNumeral`/`note` riding along from the ANSWER realization.
  Nothing pair-shaped is stored, so authoring one language file lights up every pair it makes.
  Availability gate: **empty unless `Numbers.supports(target)`** —
  sampling generates the answer side's number words,
  so a language without a pack can only ever supply prompts.
  A frame whose slot the target cannot fill drops out on the same rule (`Numbers.supportsSlot`):
  a cardinal, a year and a clock come with every pack,
  a `fraction` needs the pack to READ one, a `phone` its `PhonePlan` (`phone-readings.md`).
  Reverse mode is the same template read the other way, for any pair.
- A `NumbersTask` carries two prompt strings:
  **`prompt` is the machine form** (`"347"`, `"14:35"`) that callers parse —
  `PhraseSlots` does `prompt.toLong()`, and a Kotlin throw crossing the ObjC boundary crashes the app —
  while **`promptDisplay` is what the UI shows**, defaulting to `prompt`.
  Cardinals group their digits with **U+202F narrow no-break space** (`groupDigits`):
  dot and comma are inverted between German and English,
  so a neutral mark is the only one that teaches neither as the truth.
  A sentence slot's grouped digits are accepted alongside the plain ones.
- `NumbersReading.Form` asks the other ways a number is written over a ladder
  where each Sprosse keeps everything below it;
  its own `internal` model (`NumberValue`, `FormLimits`) never reaches the ObjC header.
  The Sprosse's forms are intersected with the language's,
  so a pack that cannot read one never draws it,
  and a pack that authors none offers no Forms drill at all (`Numbers.supportsForms`).
  **A Forms prompt is the one language-dependent prompt**:
  German shows `3,7` where English shows `3.7`, because the reading names the mark
  (`Komma` · `point`) and a shared prompt would lie about the answer it grades,
  and a price wears its language's own tag (`FormLimits.currency`);
  everything else stays neutral, including the ordinal mark `20.` and the `45 %` thin space.
  Fractions are drawn REDUCED: `2/4` would legitimately read both "zwei Viertel" and "ein halb",
  and no pack should carry that equivalence to grade its own drill.
- **`NumbersExercise` is what a RUN offers, `NumbersReading` is what fills a SLOT** —
  a Phrases run draws tasks whose reading is Cardinal, Year or Clock —
  and the two must not be collapsed, because progress is kept per exercise.
  `DrillUnlocks` is the unlock ladder and `DrillRamp.step` the Sprosse ramp every drill shares;
  both read progress the APP persists, and kern stores nothing.
  `PhraseSlots` samples at a Sprosse on the same per-kind ramp tables as the plain drills
  (a template's slot kind clamps the Sprosse).
- `Numbers.reference(language)` generates the numbers page from the same packs,
  so the table cannot drift from what the drill grades.
  `irregulars` (16–30) is offered only to a language whose readings there
  are not what its own siblings predict, so a band count varies.
- **`LetterDrill` is a separate facade, not a `NumbersReading` case**:
  its registry is alphabet file presence in the catalog (adding a language edits no Kotlin),
  and its ramp is stateless and kern-owned (`Report.openingSprosse`, `WINS_TO_ADVANCE`, then `DrillRamp.step`),
  so two platforms cannot drift.
  Sampling takes an injected `Random` and an app-computed promptable set
  (device voices are an app fact).
  A gap row draws its word from a POOL (`Catalog.alphabetExamples`, rules in
  `../catalog/alphabet/README.md`), the app narrowing it to what the device can say
  and flagging what the box already holds;
  kern favors the known words while at least three stand,
  and spends no randomness where a row offers one word.
  Dictation draws only `BoxEngine.arrivedCardIds` through `dictationGradingCard`,
  weighted by `LetterDictation.weight`, whose difficulty figure rides in on `DictationCandidate` —
  kern reads no state.

## The run

- A drill run is a **pure machine** shaped like the turn machine above:
  `open(mode, rng) → state`, `reduce(state, intent, rng) → state + effects`,
  `close(state, …) → summary + bookings`.
  `NumbersRun` drives the numbers/clock/forms/phrases trainer, `LetterDrillRun` the letter drill, `CountryDrillRun` the atlas, `DateDrillRun` the calendar,
  `WordScrambleRun` the spelling scramble, `SentenceScrambleRun` the word-order one and `OppositesRun` the opposites drill.
  The atlas and the calendar are one machine, `PairDrillRun<T>`, typed by the question they hold:
  a generic CLASS crosses to Swift typed, each drill's run an object of its own on it,
  while a generic FUNCTION arrives opaque —
  so a shared reducer is a class, never a function over a type parameter.
  Each drill's config supplies what is its own: the draw, the grade and the card.
  What never crosses is shared instead:
  every run but `NumbersRun` books an answer onto its ladder, carries it to the Sprosse the next question was drawn at and books a pending answer on close through one internal `LadderStanding`, read out of its own state and copied back.
- **One injected `Random` per run** feeds every draw —
  task, exercise, phrase frame, direction flip, the letters a word scramble mixes and the atoms a sentence scramble deals out —
  so a seeded run is reproducible end to end and identical on both platforms —
  which is the whole of what a challenge code needs to hand two phones one run (`NumbersChallenge`).
  A scramble that comes back reading as the answer is rolled again, boundedly:
  "tap them left to right" is not the question either drill asks.
- **A prompt is asked once, and a Sprosse with nothing left is climbed past** (`DrillSolved`).
  A run keeps what it has answered RIGHT and every draw skips that set;
  only a clean answer joins it, because a slip, a look-up and a reveal leave a prompt in the pool —
  which is the ramp's own reading of an almost (`DrillRamp.step` moves nothing on one).
  A Sprosse answered out is climbed past rather than repeated,
  and the Sprosse it climbs to is booked like any other,
  since answering a Sprosse out is standing on it;
  the wins banked below stay behind.
  A whole ladder answered out ends the run on its summary.
  What a key names is each drill's own question:
  a scrambled word and a phrase are one question at every Sprosse, so neither key carries the Sprosse it was drawn at.
  The atlas and the letter drill can ENUMERATE a Sprosse and filter it;
  the slot drill draws values rather than picking them out of a list,
  so there `DrillSolved.SPENT_ATTEMPTS` repeats in a row is what "spent" can honestly mean,
  and in a mixed run an exercise that has run out hands the turn to the next one.
  The set itself lives and dies with the run,
  because a prompt answered on Tuesday is worth asking again on Friday and keeping that kind of score is the growing box's job;
  what outlives it is the Sprosse, as `clearedSprossen` on the close, and there are two ways to earn one.
  The atlas and the calendar ENUMERATE a Sprosse and check it off (`DrillSolved.cleared`);
  a drawn Sprosse is never cleared that way.
  The two scrambles and the opposites draw out of a pool that grows with the box,
  so what they book is the CLIMB, and so does the letter drill, whose formats ask thirty prompts where its ramp climbs on two:
  `DrillSprossen` clears a Sprosse the run left UPWARD,
  whether on the wins the ladder asks for or by being answered out.
  Both ways share one rule: only a run with no slip yet clears a Sprosse (`DrillRunCore.slipped`).
  Its first miss, typo, look-up or reveal ANYWHERE ends its clearing for good —
  what it cleared before stays cleared, nothing it answers out or climbs off after does (the atlas and the calendar check against `DrillRunCore.solvedClean`, the prompts solved before that slip).
  That ledger sits BESIDE `DrillRamp` rather than tightening it:
  an almost still banks nothing, costs nothing and breaks no answer streak,
  it only takes the rest of the run out of the running for the store.
  Either way the next run opens on the lowest Sprosse the stored mask does not hold.
- Feedback and cues reuse the turn machine's vocabulary (`TurnFeedback`, `AlmostReason`, `AnswerOutcome`, `AdvanceBeat`, `ToneKind`);
  nothing new is minted where kern already names a rule.
  `AnswerStreakMilestone` names the summary ladder (≥10 / ≥5 / ≥2 / else);
  which glyph a milestone wears is chrome.
  `DrillTally` names the counter for every drill at once —
  clean wins over the answers judged either way, with almost in neither half for `DrillRamp.step`'s reason;
  the "2/3" string is rendering.
- **Storage contract**: the answer-streak record under `trainer.record.<key>`,
  per-exercise Sprosse progress under `trainer.level.<key>`,
  the most answers one run took under `trainer.answers.<key>` (`DrillRunSummary.done`, right or wrong),
  and the cleared Sprossen as a bitmask under `trainer.cleared.<key>` —
  filed per DIRECTION, a reversed run's key ending `.rev`,
  because a row means a different question either way round (`NumbersMode.RECORD_PREFIX` / `PROGRESS_PREFIX` / `ANSWERS_PREFIX` / `CLEARED_PREFIX`, keys byte-identical across the two stores).
  Every closed run of a drill also stamps its epoch millis under `trainer.lastRun.<drill>.<language>`, one key per DRILL whatever its selection (`DrillSuggestion.LAST_RUN_PREFIX`).
  The atlas, the calendar, both scrambles, the opposites and the letter drill (tile and typed Sprossen only) all keep that mask;
  the scrambles and the opposites keep NOTHING ELSE —
  no answer-streak record, so their `newRecord` is always false.
  `close` returns only bookings that beat the standing value (strictly greater);
  the platform writes blindly — except the cleared set, which it ORs into the mask it holds.
  Where a typed run OPENS is kern's too:
  the lowest Sprosse the mask does not hold (`NumbersMode.entrySprosse`), or the one the learner tapped —
  which may be that entry or anything below it,
  or a Sprosse some run reached, never one they have not been on (`NumbersMode.openable`).
  The scrambles and the opposites take the stored mask as a plain `cleared` on their run config and offer no tap at all:
  a ladder nobody can see named is a ladder nobody needs to override.
  They open at 1 instead and FAST-CLIMB the mask:
  a Sprosse it holds passes on one clean answer until the run's first miss or almost,
  every other Sprosse, and every one after that slip, on the usual count (`DrillSprossen.winsRequired`).
  A Sprosse passed fast is one the store already holds, so it is nothing new to a pause.
  The letter drill opens on the lowest Sprosse its mask does not hold,
  and passes a held one above it, or one a miss steps it back to, on the same rule.
  Pinned quirk: a non-null `phraseSource` suffixes the record language with the `<source>-<target>` pair even when the run asks no sentence,
  because the overview passes the source whenever the pair realizes frames.
- **Closing books exactly as Weiter would** —
  a pending answer keeps its earned outcome, never upgraded (a hint-assisted clean answer closes almost) and never lost;
  a revealed-but-unconfirmed answer books nothing.
  A drill with no STREAK record of its own —
  the letter drill, both scrambles and the opposites —
  closes `newRecord` false, which drops the record line and the celebration with it.
- **No drill books an FSRS review, and none touches a schedule.**
  Transcription is not recall, and neither is arrangement:
  a word typed back from a hearing, a spelling written out of its own letters and a phrase tapped back into order are all easier than the retrieval the box grades,
  so feeding one into FSRS would inflate the stability the scheduler then spaces on.
  The box is READ —
  for the words a drill may practice and the figures that pace it —
  and never written.
- `LetterDrillAvailability.report(catalog, box, language, hasVoice)` is the one gate for whether the letter drill exists, what it may prompt,
  and where a learner enters the ladder.
  `hasVoice` is a plain Boolean —
  every call is single-language and it crosses ObjC free —
  and kern caches nothing: rebuild triggers (voices arriving, foregrounding) stay platform-side.
- `WordScrambleAvailability.report(box)` and `SentenceScrambleAvailability.report(box)` are the same gate for the two scrambles,
  and take no capability port at all:
  neither drill plays or hears anything, so nothing about the device can decide what it may ask.
  Each walks the whole join and is built ONCE per run, its `drillExists` the hub-chip predicate.
  The two read DIFFERENT bars on purpose:
  the word scramble wants a word already past the settled bar (`BoxEngine.hasSettled`),
  since mixed letters cue nothing a learner cannot already produce,
  while the sentence scramble reads every phrase in the join past no bar,
  since its words are given and only their order is asked.
  What each hands the run is not the card either —
  it is what the question is made OF, and the rules for cutting that are on the two types:
  which SPELLINGS a word may be asked through (`WordScrambleAvailability.spellings`) and which CHIPS a phrase becomes, marks and leading capital included (`ScrambleTokenizer`, `ScrambleCapitals`).
  Their LADDERS are opposite on purpose, and the report owns both.
  A word-scramble Sprosse raises a length FLOOR one letter at a time (`Report.lettersAt`) —
  spelling a four-letter word back stops being a question once ten-letter ones are being spelled —
  and its ceiling is read off the pool:
  the highest Sprosse `POOL_FLOOR` of the learner's own words still clear,
  so no Sprosse exists that their box cannot fill,
  and every Sprosse keeps the opening letter anchored (`WordScrambleMasking`).
  The mix itself owes two things beyond the same letters:
  it never reads as the spelling, and it is not one adjacent swap off it either —
  a word that admits nothing better gets the swap, since the alternative is the word itself.
  A sentence-scramble Sprosse is a BAND that overlaps no other (`Report.phrasesAt`):
  the phrases ordered easiest first —
  fewer words, then fewer letters (`Report.byDifficulty`) —
  and cut into even bands of at most `BAND_SIZE`,
  so a miss drops to genuinely easier phrases and the draw is flat across the band.
  Both ladders STOP at their ceiling rather than counting on past it the way the other drills' do (`DrillRamp.step`'s `top`),
  since a number there would promise words or phrases that do not exist;
  answering the top Sprosse out ends the run.
- `OppositesAvailability.report(box, pairs)` is the opposites drill's gate, over the catalog's `oppositePairs`:
  a pair is askable once BOTH its words have arrived (`BoxEngine.arrivedCardIds`),
  and one prompt stands per FORM the target writes,
  so concepts written alike merge and every opposite of every one of them answers it, held or not.
  Its Sprossen are bands that overlap nothing, like the sentence scramble's:
  adjectives and adverbs, then verbs and nouns, then every prompt with more than one opposite.
  `OppositesRun.grade` refuses the prompt itself before the typo budget can read it as a slip of its opposite.
