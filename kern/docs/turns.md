# Runs — the turn machine, the drills, and listening

Three pure machines: immutable state plus `reduce(state, intent) -> state + effects`.
The platform owns the field, the keyboard, focus, timers and playback,
and text reaches a machine only inside an intent — never as state.
Engine contract: `../README.md`.

## The vocabulary turn

- **A turn is a machine, not a screen** (`session.TurnMachine`, state `TurnState`):
  one produce/recognize turn is immutable state plus `reduce(state, intent, nowEpochMillis)` —
  `SessionRun`'s shape, one step further in.
  It is opened with a card, its role, its produce prompt, the prompted form,
  whether this is the first exposure and whether the word has settled;
  it answers with the next state plus `TurnEffect`s —
  `Answer` (the rating leaves for the run), `ArmAdvance`/`CancelAdvance`,
  `PrimeField`, `Tone` and `ReleaseFocus`.
  The learner's TEXT is never in the state:
  the platform owns the field, the keyboard, focus, animation and playback,
  and hands text in through intents.
  Every rule about what that text is worth is here,
  because it lived twice before and drifted both ways —
  a pickable Easy on one platform, no retype after a miss on the other.
  - **What each branch earns**: a clean answer is `Match.Exact.producedRating()`;
    a typo and a heard-instead are `TurnFeedback.Almost`, holding the rating grading decided
    until the owed form has been seen; finishing the retype after a miss is
    recalled-with-help (Hard); giving up on it is an honest Again;
    a self-grade is `SelfGrading` over the recall span and the prompt length.
  - **The beats belong to the engine** (`ADVANCE_LIVE_MS` 450, `ADVANCE_EXPLICIT_MS` 1200,
    carried by `AdvanceTier`): finishing the word IS the answer, so a live-typed exact gets
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
  - **Asked by ear**, the answer grades against `spokenOnly`,
    but a form the card itself lists (`alsoAccepts`, compared by `speechKey`)
    is almost rather than wrong — the reveal teaches those forms, it simply was not what played.
    Being exactly what played wins over that:
    a card that also lists its own spoken form was still answered exactly.

## Listening

- **Listening is a playlist over the learner's own words** (`net.spross.kern.listen`).
  Each turn says the target word, waits, says its meaning in the SOURCE language, then says
  the target again — so it reaches the hours a language is actually available in, the walk and
  the washing-up, where every other way in asks for a typed answer or a tap.
  `ListeningPool.report(catalog, box, source, target, hasTargetVoice, hasSourceVoice)` is the
  one gate, shaped like `LetterDrillAvailability.report` and disciplined the same way: the only
  platform facts are the two `hasVoice` booleans, one per side, and kern caches nothing.
  **Both halves must be sayable** — a turn that plays a word and then silence teaches nothing,
  so the shared `catalog.audible` predicate is applied to the target form AND the source form.
  **Suspended cards stay in the pool.** The leech rule auto-suspends at two lapses (`../README.md` §5), so the
  words that stick worst are exactly the ones `Inventory.active` drops; suspension takes a word
  out of the box's queue and never said stop meeting the word.
  Unseen words always get a seat, in seed order and through `Growth.isIntroducible`: a
  settled pool carries `LISTENING_POOL_FRESH` of them, and a thin one is filled out to
  `LISTENING_POOL_FLOOR` — the `SessionComposer.fillOut` move, so a learner three words in
  does not hear those three words for the whole walk. Hearing one does not introduce it:
  introduction is the first answer, and listening answers nothing.
  `listeningWeight` is `dictationWeight` minus its spelling term — a floor of 1 no word ever
  loses, plus capped lapses and above-midpoint difficulty. A **suspended** card keeps the
  bare floor: the box has already decided the leech is being pushed outward. An **unscheduled**
  card takes the flat `LISTENING_FRESH_WEIGHT` instead — its 0.0 difficulty is an absence,
  not a measurement — so a new word outdraws the familiar ones and stays under the leeches:
  the hour leans on what is not sticking, then on what has not been met.
  `ListeningRun` is the pure machine (`Start`/`Advance`/`Skip`/`Repeat`/`TogglePause`/`Close`,
  one injected `Random`), and it holds **no `BoxState` at all** — that is what makes "listening
  books nothing" structural rather than promised. Its `ListeningEffect` says `Play`/`Stop`
  because `Repeat` leaves the state identical and must still make the sound fire.
  Repetition over time is a **recency ring**: the last `RECENCY_WINDOW` card ids are held out
  of the draw, at most `pool − 1` of them, so a pool smaller than the window laps instead of
  running dry and no word is ever said twice in a row.
  `ListeningTurn` carries both forms, the article, and all three beats
  (`RECALL_GAP_HELD_MS` 1200 / `RECALL_GAP_FRESH_MS` 600, with `ECHO_GAP_MS` the fresh gap and
  `TURN_GAP_MS` the held one), so neither platform decides any of it — the recall gap is the
  only beat that varies, long for a word the learner has answered before and short for one
  with nothing yet to recall, and the echo and the breath between turns just reuse those two.
  Every beat is armed off the previous word ACTUALLY ENDING plus its gap, and a word that never
  reports a finish is walked past after `LISTENING_WATCHDOG_MS`, so a run cannot stall on a
  silent engine.
  A run can be given a **bedtime**: every tap on the one chip adds `LISTENING_TIMER_STEP_MIN`
  minutes from 0 (= off, the default), and a long press jumps straight back to off — and
  `listeningGainDb(msRemaining, totalMs)` fades the WHOLE run down to
  `LISTENING_FADE_FLOOR_DB` rather than cutting — a hard stop is a change loud enough to
  wake the listener, which is the opposite of what a bedtime is for.
  The gain is applied ON TOP of a recording's `Playback.gainDb`, and clamped to its own floor
  rather than `GAIN_LIMIT_DB`, which bounds how far a MEASUREMENT may be trusted and not a
  level kern chose. `listeningExpired(msRemaining)` is where the run is over.
  The remaining milliseconds are the APP's to track and hand in, like every other clock read — the run state holds no deadline, so kern still reads no clock.

## Trainer & drill runs   (package `net.spross.kern.trainer`)

- A drill run is a **pure machine** shaped like the turn machine above:
  `open(mode, rng) → state`, `reduce(state, intent, rng) → state + effects`,
  `close(state, …) → summary + bookings`.
  `TrainerRun` drives the numbers/clock/forms/phrases trainer, `LetterDrillRun` the letter drill;
  platforms keep field, keyboard, focus, timers and audio, and text reaches the machine only inside intents —
  never as state.
- **One injected `Random` per run** feeds every draw — task, variant, phrase frame, direction flip —
  so a seeded run is reproducible end to end and identical on both platforms.
- Feedback and cues reuse the turn machine's vocabulary
  (`TurnFeedback`, `AlmostReason`, `AnswerOutcome`, `AdvanceTier`, `ToneKind`);
  nothing new is minted where kern already names a rule.
  `StreakTier` names the summary ladder (≥10 / ≥5 / ≥2 / else);
  which glyph a tier wears is chrome.
  `DrillTally` names the counter for all three drills at once — clean wins over the answers
  judged either way, with almost in neither half for `DrillRamp.step`'s reason;
  the "2/3" string is rendering.
- **Storage contract**: the streak record under `trainer.record.<key>`,
  per-variant rung progress under `trainer.level.<key>`
  (`TrainerMode.RECORD_PREFIX` / `PROGRESS_PREFIX`, keys byte-identical across the two stores).
  `close` returns only bookings that beat the standing value (strictly greater);
  the platform writes blindly.
  Pinned quirk: a non-null `phraseSource` suffixes the record language with the
  `<source>-<target>` pair even when the run asks no sentence,
  because the overview passes the source whenever the pair realizes frames.
- **Closing books exactly as Weiter would** — a pending answer keeps its earned outcome,
  never upgraded (a hint-assisted clean answer closes almost) and never lost;
  a revealed-but-unconfirmed answer books nothing.
- `LetterDrillAvailability.report(catalog, box, language, hasVoice)` is the one gate for
  whether the letter drill exists, what it may prompt, and where a learner enters the ladder.
  `hasVoice` is a plain Boolean — every call is single-language and it crosses ObjC free —
  and kern caches nothing: rebuild triggers (voices arriving, foregrounding) stay platform-side.
