# Runs — the turn machine, the drills, and listening
The three pure run machines (the vocabulary turn, the drills, listening) and where they end and the platform begins.
Neighbors: the engine contract `../README.md`, what a drill is made of `trainer.md`.

Three pure machines: immutable state plus `reduce(state, intent) -> state + effects`.
The platform owns the field, the keyboard, focus, timers, animation and playback,
and text reaches a machine only inside an intent — never as state.

## The vocabulary turn

- **A turn is a machine, not a screen** (`session.TurnMachine`, state `TurnState`):
  one produce/recognize turn is immutable state plus `reduce(state, intent, nowEpochMillis)` —
  `SessionRun`'s shape, one step further in.
  It is opened with a card, its role, its produce prompt, the prompted form,
  whether this is the first exposure and whether the word has settled;
  it answers with the next state plus `TurnEffect`s —
  `Answer` (the rating leaves for the run), `ArmAdvance`/`CancelAdvance`,
  `PrimeField`, `Tone` and `ReleaseFocus`.
  The one text it holds is the answer it takes out of the field itself (`rejectedAnswer`, below).
  Every rule about what that text is worth is here.
  - **What each branch earns**: a clean answer is `Match.Exact.producedRating()`;
    a typo and a borrowed meaning are `TurnFeedback.Almost`, holding the rating grading decided
    until the owed form has been seen; finishing the retype after a miss is
    recalled-with-help (Hard); giving up on it is an honest Again;
    a self-grade is `SelfGrading` over the recall span and the prompt length.
  - **The beats belong to the engine** (`ADVANCE_LIVE_MS` 450, `ADVANCE_EXPLICIT_MS` 1200,
    carried by `AdvanceBeat`): finishing the word IS the answer, so a live-typed exact gets
    the short beat and an explicit Check the longer one, while an almost hold gets none at all.
    WHETHER a timer may run is the platform's fact — a screen reader makes a timed change
    hostile — but that an explicit button REPLACES it, and books exactly what the beat would
    have booked, is the rule (`TurnIntent.ConfirmPending`).
  - **Live approval is exact-only where an explicit submit forgives a slip**:
    the typo budget would fire a letter early and grade a word before it was finished,
    and a real slip has to pause on its correction anyway.
    Backing out of a finished word takes the acceptance and its parked rating with it.
  - **A miss keeps the field open**: the retype IS the answer, primed to the whole words
    already right (`AnswerNormalizer.matchingPrefixWordCount`),
    so nothing already correct is typed twice.
    The refused word is kept whole (`TurnState.rejectedAnswer`) because that priming drops it
    from the only place it stood, and `answerForReport(fieldText)` is what a report about the
    card carries: the word the catalog refused where there was one, else what stands written.
    A synonym marked wrong IS the report, so it may not be lost to the field it was typed in.
  - **The write-out** (`CopyStep`): a missed word is typed once with the answer in view.
    Only Again asks for it, only for a word that has not settled,
    and only where writing it is more than copying it off the prompt —
    production, or the first exposure, where the word is being taught.
    A later recognition miss does not qualify:
    the target has stood in the prompt since the first frame.
    The rating is HELD and applied unchanged — encoding, never a grade —
    and a produce retry that was given up on never opens one,
    because that field already was the one write-out the word gets.
  - **The recall span** is prompt-shown until the learner asks to see the answer, closed once;
    a typed answer never closes it, because it never reaches self-grading.
  - **Asked by ear**, the answer is the MEANING (`meaningSide`, graded by the source
    language's own normalizer) — a word heard and written back down has been transcribed,
    not understood. `presentation.md` owns that rule; what the turn adds is that the
    answer side reaches every caller through `TurnState.answerText`/`answerLang`,
    and that `ShowPromptText` puts the word on screen for a learner who cannot listen
    without changing anything the answer is worth.

## Listening

- **Listening is a playlist over the learner's own words** (`net.spross.kern.listen`):
  the target word, its meaning in the SOURCE language, then the target again —
  so it reaches the hours a language is available in and nothing can be typed or tapped.
- `ListeningPool.report(catalog, box, source, target, hasTargetVoice, hasSourceVoice, seed)` is the one gate,
  disciplined like `LetterDrillAvailability.report`:
  the only platform facts are the two `hasVoice` booleans, and kern caches nothing.
  It is asked ONCE PER RUN, when the learner opens one, never on the way past the entry card;
  the card itself stands on `listeningOffered`: words in the box and something that can say each language.
  `seed` only salts the order's tiebreak; kern never reads a clock or cares what the number means.
- **The pool is the sayable join short of the settled words, not a composed subset.**
  **Both halves must be sayable** (`audible` on both forms),
  or a turn plays a word and then silence.
  Scheduled and unseen words alike are in it,
  so a learner a few words in hears a stream of new words rather than lapping the handful they hold;
  unseen words enter through `Growth.isIntroducible`, and hearing one does not introduce it.
  **A settled word is out** (`Statistics.hasSettled`) until a lapse takes it under that bar:
  left in, a well-used box would open on the words it trusts most.
  **Suspended cards stay in**: suspension takes a word out of the box's rotation,
  and this is the surface that can still reach it — at the growing priority, never leading.
- **The ladder is the box's own bar** (`listeningPriority`):
  a word short of `GROWING_STABILITY` is SHAKY, one past it GROWING.
  **Nothing on it reads a due date** —
  a due term would make listening a second scheduler, need a clock the run does not take,
  and pin the same word first every run, which listening cannot resolve since it books nothing.
- **The deal** (`listeningOrder`, what `ListeningPool.report` returns):
  the shaky words play first, all of them, then share the turns with the growing ones,
  each lane starting over at its own head when it runs out,
  so no word returns before the rest of its lane has, and a recent word waits a floor of turns.
  Never-answered words are a fixed slice from the first turn rather than a priority,
  so breadth rides alongside the shaky words and three hundred unseen words cannot crowd out the twenty that are slipping;
  queued words lead that slice, most recently queued first,
  and the catalog's earliest concepts lead the rest, with a soft lean toward earlier words past them.
  Within a lane the order is hashed by card id salted with `seed`,
  so catalog neighbors are not heard in sequence and a fresh seed reshuffles.
  Only the ordered list crosses the ObjC boundary.
- **`ListeningRun` holds no `BoxState` at all** —
  that is what makes "listening books nothing" structural rather than promised.
  It walks the playlist it was handed by position and laps at its end;
  repeats are the deal's business, never the run's.
  `ListeningEffect` says `Play`/`Stop` because `Repeat` leaves the state identical and must still make the sound fire.
- **Every beat is kern's** (`ListeningTurn.sayings`): target, meaning, target again, the article on the target sayings only,
  and the meaning shown from its own saying on.
  The recall gap is long for a word answered before and short for a new one,
  and the echo and the gap between turns reuse those two.
  Each beat is armed off the previous word ACTUALLY ENDING plus its gap,
  and a word that never reports a finish is walked past after `LISTENING_WATCHDOG_MS`.
- **A bedtime fades the whole run rather than cutting it** (`listeningTimerStepMs`, `listeningGainDb`, `fadedGainDb`):
  a hard stop is loud enough to wake the listener.
  The deadline ends the run at the seam between turns (`listeningSeam`),
  and a PAUSED run is left parked — a bedtime ends a run nobody is attending, not one somebody just touched.
  The floor holds the SUM of a recording's level and the ramp, because that is what a listener hears.
  The remaining milliseconds are the APP's to track and hand in; the run state holds no deadline.
  The chip reads whole minutes rounded up and wakes only when one turns (`listeningTimerMinutes`, `listeningTimerWakeMs`).

## Trainer & drill runs   (package `net.spross.kern.trainer`)

- A drill run is a **pure machine** shaped like the turn machine above:
  `open(mode, rng) → state`, `reduce(state, intent, rng) → state + effects`,
  `close(state, …) → summary + bookings`.
  `NumbersRun` drives the numbers/clock/forms/phrases trainer, `LetterDrillRun` the letter drill,
  `CountryDrillRun` the atlas, `DateDrillRun` the calendar,
  `WordScrambleRun` the spelling scramble, `SentenceScrambleRun` the word-order one
  and `OppositesRun` the opposites drill.
  Each keeps its own CONCRETE draw type: they cross to Swift, where a generic arrives opaque,
  so there is no shared `ScrambleRun<T>` however alike two of them read.
  What never crosses is shared instead: every run but `NumbersRun` books an answer onto its ladder,
  carries it to the Sprosse the next question was drawn at and books a pending answer on close
  through one internal `LadderStanding`, read out of its own state and copied back.
- **One injected `Random` per run** feeds every draw — task, exercise, phrase frame, direction flip,
  the letters a word scramble mixes and the atoms a sentence scramble deals out —
  so a seeded run is reproducible end to end and identical on both platforms —
  which is the whole of what a challenge code needs to hand two phones one run (`NumbersChallenge`).
  A scramble that comes back reading as the answer is rolled again, boundedly:
  "tap them left to right" is not the question either drill asks.
- **A prompt is asked once, and a Sprosse with nothing left is climbed past** (`DrillSolved`).
  A run keeps what it has answered RIGHT and every draw skips that set;
  only a clean answer joins it, because a slip, a look-up and a reveal leave a prompt in the pool —
  which is the ramp's own reading of an almost (`DrillRamp.step` moves nothing on one).
  A Sprosse answered out is climbed past rather than repeated, and the Sprosse it climbs to is booked
  like any other, since answering a Sprosse out is standing on it; the wins banked below stay behind.
  A whole ladder answered out ends the run on its summary.
  What a key names is each drill's own question: a word scramble carries its Sprosse in the key,
  because the letters a Sprosse leaves standing make the same word a different ask,
  while a phrase carries none — its word order does not change with the Sprosse it was drawn at.
  The atlas and the letter drill can ENUMERATE a Sprosse and filter it;
  the slot drill draws values rather than picking them out of a list, so there
  `DrillSolved.SPENT_ATTEMPTS` repeats in a row is what "spent" can honestly mean,
  and in a mixed run an exercise that has run out hands the turn to the next one.
  The set itself lives and dies with the run,
  because a prompt answered on Tuesday is worth asking again on Friday
  and keeping that kind of score is the growing box's job;
  what outlives it is the Sprosse, as `clearedSprossen` on the close, and there are two ways to earn one.
  The atlas and the calendar ENUMERATE a Sprosse and check it off (`DrillSolved.cleared`);
  a drawn Sprosse is never cleared that way.
  The two scrambles and the opposites draw out of a pool that grows with the box, so what they book is the CLIMB,
  and so does the letter drill, whose formats ask thirty prompts where its ramp climbs on two:
  `DrillSprossen` clears a Sprosse the run left UPWARD,
  whether on the wins the ladder asks for or by being answered out.
  Both ways share one rule: only a run with no slip yet clears a Sprosse (`DrillRunCore.slipped`).
  Its first miss, typo, look-up or reveal ANYWHERE ends its clearing for good —
  what it cleared before stays cleared, nothing it answers out or climbs off after does
  (the atlas and the calendar check against `DrillRunCore.solvedClean`, the prompts solved before that slip).
  That ledger sits BESIDE `DrillRamp` rather than tightening it:
  an almost still banks nothing, costs nothing and breaks no answer streak,
  it only takes the rest of the run out of the running for the store.
  Either way the next run opens on the lowest Sprosse the stored mask does not hold.
- Feedback and cues reuse the turn machine's vocabulary
  (`TurnFeedback`, `AlmostReason`, `AnswerOutcome`, `AdvanceBeat`, `ToneKind`);
  nothing new is minted where kern already names a rule.
  `AnswerStreakMilestone` names the summary ladder (≥10 / ≥5 / ≥2 / else);
  which glyph a milestone wears is chrome.
  `DrillTally` names the counter for every drill at once — clean wins over the answers
  judged either way, with almost in neither half for `DrillRamp.step`'s reason;
  the "2/3" string is rendering.
- **Storage contract**: the answer-streak record under `trainer.record.<key>` —
  a timed run's is its score instead (`DrillRunSummary.recordFigure`), on a key its own modifier already separates —
  per-exercise Sprosse progress under `trainer.level.<key>`,
  the most answers one run took under `trainer.answers.<key>` (`DrillRunSummary.done`, right or wrong),
  and the cleared Sprossen as a bitmask under `trainer.cleared.<key>` —
  filed per DIRECTION, a reversed run's key ending `.rev`,
  because a row means a different question either way round
  (`NumbersMode.RECORD_PREFIX` / `PROGRESS_PREFIX` / `ANSWERS_PREFIX` / `CLEARED_PREFIX`,
  keys byte-identical across the two stores).
  Every closed run of a drill also stamps its epoch millis under `trainer.lastRun.<drill>.<language>`,
  one key per DRILL whatever its selection (`DrillSuggestion.LAST_RUN_PREFIX`).
  The atlas, the calendar, both scrambles, the opposites and the letter drill (tile and typed Sprossen only) all keep that mask;
  the scrambles and the opposites keep NOTHING ELSE — no answer-streak record, so their `newRecord` is always false.
  `close` returns only bookings that beat the standing value (strictly greater);
  the platform writes blindly — except the cleared set, which it ORs into the mask it holds.
  Where a typed run OPENS is kern's too: the lowest Sprosse the mask does not hold
  (`NumbersMode.entrySprosse`), or the one the learner tapped — which may be that entry or
  anything below it, or a Sprosse some run reached, never one they have not been on
  (`NumbersMode.openable`).
  The scrambles and the opposites take the stored mask as a plain `cleared` on their run config and offer no tap at all:
  a ladder nobody can see named is a ladder nobody needs to override.
  They open at 1 instead and FAST-CLIMB the mask:
  a Sprosse it holds passes on one clean answer until the run's first miss or almost,
  every other Sprosse, and every one after that slip, on the usual count (`DrillSprossen.winsRequired`).
  A Sprosse passed fast is one the store already holds, so it is nothing new to a pause.
  The letter drill opens on the lowest Sprosse its mask does not hold,
  and passes a held one above it, or one a miss steps it back to, on the same rule.
  Pinned quirk: a non-null `phraseSource` suffixes the record language with the
  `<source>-<target>` pair even when the run asks no sentence,
  because the overview passes the source whenever the pair realizes frames.
- **Closing books exactly as Weiter would** — a pending answer keeps its earned outcome,
  never upgraded (a hint-assisted clean answer closes almost) and never lost;
  a revealed-but-unconfirmed answer books nothing.
  A drill with no STREAK record of its own — the letter drill, both scrambles and the opposites — closes
  `newRecord` false, which drops the record line and the celebration with it.
- **No drill books an FSRS review, and none touches a schedule.**
  Transcription is not recall, and neither is arrangement: a word typed back from a hearing, a
  spelling written out of its own letters and a phrase tapped back into order are all easier
  than the retrieval the box grades, so feeding one into FSRS would inflate the stability the
  scheduler then spaces on. The box is READ — for the words a drill may practice and the
  figures that pace it — and never written.
- `LetterDrillAvailability.report(catalog, box, language, hasVoice)` is the one gate for
  whether the letter drill exists, what it may prompt, and where a learner enters the ladder.
  `hasVoice` is a plain Boolean — every call is single-language and it crosses ObjC free —
  and kern caches nothing: rebuild triggers (voices arriving, foregrounding) stay platform-side.
- `WordScrambleAvailability.report(box)` and `SentenceScrambleAvailability.report(box)` are the
  same gate for the two scrambles, and take no capability port at all: neither drill plays or
  hears anything, so nothing about the device can decide what it may ask.
  Each walks the whole join and is built ONCE per run, its `drillExists` the hub-chip predicate.
  The two read DIFFERENT bars on purpose: the word scramble wants a word already past the
  settled bar (`BoxEngine.hasSettled`), since mixed letters cue nothing a learner cannot
  already produce, while the sentence scramble reads every phrase in the join past no bar,
  since its words are given and only their order is asked.
  What each hands the run is not the card either — it is what the question is made OF, and the
  rules for cutting that are on the two types: which SPELLINGS a word may be asked through
  (`WordScrambleAvailability.spellings`) and which CHIPS a phrase becomes, marks and leading
  capital included (`ScrambleTokenizer`, `ScrambleCapitals`).
  Their LADDERS are opposite on purpose, and the report owns both.
  A word-scramble Sprosse raises a length FLOOR one letter at a time (`Report.lettersAt`) —
  spelling a four-letter word back stops being a question once ten-letter ones are being spelled —
  and its ceiling is read off the pool: the highest Sprosse `POOL_FLOOR` of the learner's own words
  still clear, so no Sprosse exists that their box cannot fill,
  while `WordScrambleMasking.MAX_SPROSSE` only names the last Sprosse that changes the CUE
  (the opening letter anchored, then nothing) and every Sprosse above it goes on
  lengthening the word with nothing anchored.
  The mix itself owes two things beyond the same letters:
  it never reads as the spelling, and it is not one adjacent swap off it either —
  a word that admits nothing better gets the swap, since the alternative is the word itself.
  A sentence-scramble Sprosse is a BAND that overlaps no other (`Report.phrasesAt`):
  the phrases ordered easiest first — fewer words, then fewer letters (`Report.byDifficulty`) —
  and cut into even bands of at most `BAND_SIZE`,
  so a miss drops to genuinely easier phrases and the draw is flat across the band.
  Both ladders STOP at their ceiling rather than counting on past it the way the other drills' do
  (`DrillRamp.step`'s `top`),
  since a number there would promise words or phrases that do not exist; answering the top Sprosse out ends the run.
- `OppositesAvailability.report(box, pairs)` is the opposites drill's gate, over the catalog's
  `oppositePairs`: a pair is askable once BOTH its words have arrived (`BoxEngine.arrivedCardIds`),
  and one prompt stands per FORM the target writes, so concepts written alike merge
  and every opposite of every one of them answers it, held or not.
  Its Sprossen are bands that overlap nothing, like the sentence scramble's:
  adjectives and adverbs, then verbs and nouns, then every prompt with more than one opposite.
  `OppositesRun.grade` refuses the prompt itself before the typo budget can read it as a slip of its opposite.

## The question on screen

- **One `Question` per card on screen** (`session.Question`), a review card and a drill task alike:
  its prompt and answer `Side`s, the caption (`QuestionAsk`), the picture and its cue,
  a first-sight `QuestionHint`, the closing lines and `opens` — whether the card carries its answer.
  Each app draws every card from it with one component, so what a face shows is decided once.
  Nothing in it is worded or placed: asks, hints, plural sentinels and closing labels are structures the apps word.
- **The review card** is `TurnState.question`.
  Grammar is target-side only and rides the cited form alone, so a rotated synonym stands bare
  and the citation it displaced closes the card as an alternate;
  the cited form carries its plural whether or not the language writes an article.
  The area is named over an ambiguous produce prompt and never over a recognition one,
  where any cue precise enough to disambiguate would hand over the answer.
  A card asked by ear asks with a sound until the learner cannot listen or the card opens,
  and then shows the word it played.
- **A drill's card is `DrillRunProgress.question`**, which every run state declares with no default,
  as it declares its sayings, so a new drill cannot ship without ruling on its card.
  The drills' first-sight words are `QuestionHint`s (a numbers place or form, a calendar pattern word),
  the atlas and letter captions `QuestionAsk`s;
  a flag that alone asks the question is `emojiIsQuestion`, a flag that would answer it waits for the reveal.
  A sentence scramble's bank is its prompt, so its prompt side holds no words.
- **A card opens on a miss or a reveal only** (`Question.opens`; a drill's `showsAnswer`, one rule on `DrillRunProgress`),
  never on an accepted answer, which already stands in the learner's own text.
  Where the meaning never stood on screen — the scrambles, the opposites, a letter heard —
  an accepted answer keeps the card closed and grows the meaning alone (`growsNote`).
- **What stands under the card is `AnswerControls`** — `TurnState.controls`, and `DrillRunProgress.controls`,
  again with no default: the slot the answer is given with (a field, tiles, an arrangement, the self-grade verdicts, the write-out),
  the field's own verdict, the one primary action, the confirm tap, the give-up, the way out and the can't-listen.
  A review miss keeps the field editable for the retype beside a quiet skip, and a miss asked by ear drops it for one Next;
  a drill's miss holds the field as written, since a drill has nothing to retype.
  Every drill wears one rule around its slot (`answerControls`): a submit only while a written answer is owed,
  a near miss and a miss held until tapped, a clean answer only where no beat may run, the way out where the run offers it.
- **A speaker is a `Side.saying`**, the tap; `Reading` stays the autoplay,
  and every target form a review card reads aloud also stands on a side with the speaker that says it.
- **How a card moves is `design.CardMotion`**: the switch to the next question turns the outgoing card out
  and the incoming one in about the vertical axis, each showing only its front half (`flipAngle`, `flipShows`),
  and a reveal settles before the shortest beat can move the card on; each app keeps its native easing.
