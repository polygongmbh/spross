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
  - **Asked by ear**, the answer is the MEANING (`meaningSide`, graded by the source
    language's own normalizer) — a word heard and written back down has been transcribed,
    not understood. `presentation.md` owns that rule; what the turn adds is that the
    answer side reaches every caller through `TurnState.answerText`/`answerLang`,
    and that `ShowPromptText` puts the word on screen for a learner who cannot listen
    without changing anything the answer is worth.

## Listening

- **Listening is a playlist over the learner's own words** (`net.spross.kern.listen`).
  Each turn says the target word, waits, says its meaning in the SOURCE language, then says
  the target again — so it reaches the hours a language is actually available in, the walk and
  the washing-up, where every other way in asks for a typed answer or a tap.
  `ListeningPool.report(catalog, box, source, target, hasTargetVoice, hasSourceVoice, seed)`
  is the one gate, shaped like `LetterDrillAvailability.report` and disciplined the same way:
  the only platform facts are the two `hasVoice` booleans, one per side, and kern caches
  nothing. `seed` is opaque to kern — it only salts the scheduled lanes' own tiebreak (below),
  never reads a clock, and does not care that both apps happen to hand it the current instant.
  **Both halves must be sayable** — a turn that plays a word and then silence teaches nothing,
  so the shared `catalog.audible` predicate is applied to the target form AND the source form.
  **Suspended cards stay in the pool.** The leech rule auto-suspends at two lapses (`../README.md` §5), so the
  words that stick worst are exactly the ones `Inventory.active` drops; suspension takes a word
  out of the box's queue and never said stop meeting the word.
  **The pool is the whole sayable join, not a composed subset** — every joined card that
  both halves of a turn can say, scheduled and unseen alike. So a learner a few words in hears
  a STREAM of new words rather than lapping the handful they hold, and a learner with a full
  vocabulary hears their own words in it. Unseen words enter through
  `Growth.isIntroducible`: a phrase whose components have not landed is not ready to be heard
  either. Hearing one does not introduce it: introduction is the first answer, and listening
  answers nothing.
  `listeningPriority` is one ladder in STABILITY, and the pool is DEALT down it rather than
  drawn from it — higher means earlier, and the same box gives the same run.
  A scheduled word starts at `LISTENING_MAX_STABILITY_PRIORITY` (6) and loses a Sprosse per
  ceiling of `LISTENING_STABILITY_BAND_CEILINGS` (2, 5, 10, 20 d, then `MATURED_STABILITY`)
  it has passed, clamped to 1..6: just learned or just lapsed leads, the not-quite-settled
  rotate in the middle, and the consolidated ones sit at the floor — still worth hearing,
  never what the hour is about. The steps widen on the way up rather than staying fixed: a
  flat per-day step put the floor at 10 days, which a reviewed-for-a-while box clears easily,
  piling almost everything into that one slowest-dealt lane; the floor now waits for
  `MATURED_STABILITY` (30 d) — kern's own "a month out from the next review" bar — instead of
  a second number invented for the same idea.
  A **packed** card (`BoxState.enqueued`) takes `LISTENING_QUEUED_PRIORITY` (5) and every
  other **unscheduled** one `LISTENING_NEW_PRIORITY` (4): neither has a stability to read, so
  those figures are deal-rates rather than measurements — packing is the learner saying
  *these words next*, and a first hearing is the mode's cheapest breadth.
  A **suspended** card keeps its stability's Sprosse and pays `LISTENING_SUSPENDED_PENALTY` (2)
  down to the floor of 1, rather than being sent to the floor outright: the leech rule takes a
  word out of the box's rotation, and this is the surface that can still reach it, so a shaky
  leech lands at Sprosse 2 to 4 — it comes in, it does not lead.
  The ladder that falls out: 6 is stability 0–2 d; 5 is 2–5 d and the packed words; 4 is
  5–10 d and every other unseen word, and a leech at 0–2 d; 3 is 10–20 d and a leech at
  2–5 d; 2 is 20–30 d and a leech at 5–10 d; 1 is 30 d (`MATURED_STABILITY`) and up, and a
  leech at 10 d or more.
  **Nothing on that ladder reads a due date.** A word the box wants back is a word whose
  stability is low, so it rises on the Sprossen it already has; a due term would make listening a
  second scheduler, need a clock the run does not take, and pin the same word first every
  run — which listening cannot resolve, since it books nothing.
  **The pool is dealt across the run, not sorted by Sprosse.** A plain sort would empty Sprosse 6,
  then Sprosse 5, then spend the rest of the run inside a Sprosse-4 block of every unseen word in
  the catalog, and Sprossen 3, 2 and 1 would never be reached in a session at all. So the pool is
  split into **lanes** — `(kind, priority)`, kind being scheduled / new / packed — and each
  lane is dealt evenly across the whole run: the n-th candidate of a lane whose priority is p
  is placed at `(n + 0.5) / p`, and everything sorts by that placement. A lane of priority 6
  advances six times faster than one of priority 1, so the mix is the old weighted draw's
  proportions made deterministic — every lane reaches the ear, the high ones simply reach it
  more often. Lanes rather than shared Sprossen, because the two unscheduled kinds' figures are
  rates and not measurements: three hundred unseen words must not crowd out twenty
  mid-stability ones that happened to score the same.
  **Within a lane the order depends on what the lane is.** New and packed words run in strict
  catalog order (`seedIndex`, then id — `Inventory.seedOrder`'s own tiebreak): an empty box is
  ONE lane, so a learner new to a language hears the catalog from its very first word, which
  is what the order exists for. Packed words lead the rest of the unseen ones and are out
  within the first handful of turns, in catalog order among themselves rather than in pack
  order — `Growth.newCandidates` honors the queue's own order because it spends a budget
  against it, and a run has no budget to spend. Scheduled words are hashed by card id
  (`fnv1a64`, the hash `Inventory.dueOrder` already uses) salted with `seed`, so catalog seed
  neighbors — often related concepts, and a word half-learned from its neighbor is what that
  hash exists to prevent — are not heard in the same sequence every run, AND the same box
  dealt with a different seed reshuffles rather than replaying: both apps happen to hand in
  the current instant, and re-sweep the pool on every foreground, so a learner who listens
  more than once a day hears a fresh order each time — kern itself never reads a clock, or
  cares what the number means, only that a new one showed up. The whole deal is
  `listeningOrder`, pure and private in its lane key, and it is what `ListeningPool.report`
  returns — nothing but the ordered list crosses the ObjC boundary.
  `ListeningRun` is the pure machine (`Start`/`Advance`/`Skip`/`Repeat`/`TogglePause`/`Close`),
  and it holds **no `BoxState` at all** — that is what makes "listening books nothing"
  structural rather than promised. Its `ListeningEffect` says `Play`/`Stop`
  because `Repeat` leaves the state identical and must still make the sound fire.
  It **walks the order it was handed and laps**: the state carries the ids played since the
  last lap, the next turn is the first candidate not among them, and when none are left the
  lap clears and the walk restarts at the head. So no word repeats before the whole pool has
  lapped, a pool smaller than the run laps cleanly instead of running dry, a one-word pool
  keeps saying its word, and a long run rotates the shaky and packed ones back through rather
  than being a front-loaded ten minutes followed by fifty of settled words.
  `ListeningTurn` carries both forms, the article, and all three beats
  (`RECALL_GAP_HELD_MS` 1200 / `RECALL_GAP_FRESH_MS` 600, with `ECHO_GAP_MS` the fresh gap and
  `TURN_GAP_MS` the held one), so neither platform decides any of it — the recall gap is the
  only beat that varies, long for a word the learner has answered before and short for one
  with nothing yet to recall, and the echo and the breath between turns just reuse those two.
  Every beat is armed off the previous word ACTUALLY ENDING plus its gap, and a word that never
  reports a finish is walked past after `LISTENING_WATCHDOG_MS`, so a run cannot stall on a
  silent engine.
  A run can be given a **bedtime**: every tap on the one chip adds `LISTENING_TIMER_STEP_MIN`
  minutes to what is LEFT of it (`listeningTimerStepMs(msRemaining, steps)`) from 0
  (= off, the default), and a long press jumps straight back to off — and
  `listeningGainDb(msRemaining, totalMs)` fades the WHOLE run down to
  `LISTENING_FADE_FLOOR_DB` rather than cutting — a hard stop is a change loud enough to
  wake the listener, which is the opposite of what a bedtime is for.
  For the same reason the deadline ends the run at the SEAM BETWEEN TURNS rather than at the
  moment it falls: the turn in the air finishes, at the floor it has already reached.
  A PAUSED run has no seam coming and is left parked — the learner stopped it themselves,
  and a bedtime is there to end a run nobody is attending, not one somebody just touched.
  The ramp is applied ON TOP of a recording's `Playback.gainDb` and the SUM is what
  `LISTENING_FADE_FLOOR_DB` holds (`fadedGainDb(gainDb, fadeDb)`) — its own floor rather than
  `GAIN_LIMIT_DB`, which bounds how far a MEASUREMENT may be trusted and not a level kern chose.
  The floor is on the sum because that is the number a listener hears: the packs share no
  loudness, so one uniform ramp reaches the room's noise floor far sooner for a word the index
  already turned 12 dB down (sw, on the phone plane) than for one playing as recorded, and the
  ramp read as singling that pack out. A word whose index is already under the floor takes no
  ramp at all — the ramp may deepen an attenuation, never undo one.
  The same call hands back the recording's `cap`: the converter holds a boost to the headroom
  the file's own peak leaves, and the ramp attenuates ahead of that boost and opens exactly
  that headroom again, so as much of the deficit as the ramp has taken off comes back and no
  more. Levels that used to drift APART by pack over a bedtime now converge.
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
- **A prompt is asked once, and a Sprosse with nothing left is climbed past** (`DrillSolved`).
  A run keeps what it has answered RIGHT and every draw skips that set;
  only a clean answer joins it, because a slip, a look-up and a reveal leave a prompt in the pool —
  which is the ramp's own reading of an almost (`DrillRamp.step` moves nothing on one).
  A Sprosse answered out is climbed past rather than repeated, and the Sprosse it climbs to is booked
  like any other, since answering a Sprosse out is standing on it; the wins banked below stay behind.
  A whole ladder answered out ends the run on its summary — where the letter drill's
  "nothing left to ask" already went, now the rule for all three.
  The atlas and the letter drill can ENUMERATE a Sprosse and filter it;
  the slot drill draws values rather than picking them out of a list, so there
  `DrillSolved.SPENT_ATTEMPTS` repeats in a row is what "spent" can honestly mean,
  and in a mixed run a variant that has run out hands the turn to the next one.
  Nothing of this is persisted: the set lives and dies with the run,
  because a prompt answered on Tuesday is worth asking again on Friday
  and keeping that kind of score is the growing box's job.
- Feedback and cues reuse the turn machine's vocabulary
  (`TurnFeedback`, `AlmostReason`, `AnswerOutcome`, `AdvanceTier`, `ToneKind`);
  nothing new is minted where kern already names a rule.
  `StreakTier` names the summary ladder (≥10 / ≥5 / ≥2 / else);
  which glyph a tier wears is chrome.
  `DrillTally` names the counter for all three drills at once — clean wins over the answers
  judged either way, with almost in neither half for `DrillRamp.step`'s reason;
  the "2/3" string is rendering.
- **Storage contract**: the streak record under `trainer.record.<key>`,
  per-variant Sprosse progress under `trainer.level.<key>`
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
