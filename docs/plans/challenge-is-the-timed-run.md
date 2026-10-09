# Plan — the challenge is the only timed run
Working plan for folding the Timed pick into challenges and giving a challenge a strict turn; delete once shipped.
Neighbors: the shipped rules land in `../drills-tables.md` (numbers page) and `../../kern/docs/turns.md`.

## Why

A timed run and a challenge were two names for one race,
and a challenge played the practice turn: a miss opened a reveal, glosses and hints showed, the answer was read out.
A clock paused on that reveal would be a breather after every mistake, so the race needs a turn with nothing to linger on.

## Decisions (from the user)

- No separate Timed pick: a challenge (started from the picks, or entered as a code) is the only timed run.
- A challenge books every answer the moment it is graded, with only the right/almost/wrong tone, then asks the next question.
- A typo inside the budget books almost (scores 0, no Sprosse drop) and moves on.
- "Skip" stands where "Reveal" stood (and an empty Enter): an immediate miss.
- No glosses, no first-sight hints, no other-word note, no answer read aloud in a challenge.
- The clock pause on a shown miss is reverted (nothing is shown any more).
- The score line keeps no answer streak in a challenge (already shipped, `f846012`).

## Steps

- [x] Drop the streak from a timed run's score line — `f846012`
- [x] Revert the clock pause — `f2d1542`
- [x] A: remove `DrillModifier.Timed`; `NumbersRunState.timed` = a challenge; drop its unlock, storage tag,
      iOS/Android modifier naming and the overview's screen-reader filter, the `trainer.modifier.timed*` strings,
      the `-uitest-modifiers timed` hook; tests via a challenge; docs and changelog
- [x] B: kern strict turn for a challenge (`NumbersRun.submit/typed/reveal` book at once; `question` without hint, closing note or other word);
      the "Skip" label (`session.skip`, reused) via `AnswerControls.Primary.SubmitOrSkip` on iOS and Android; tests; docs and changelog
- [ ] Prune this plan

## Not verifiable in a cloud session

Swift and Android are not compiled here (`./gradlew :kern:jvmTest` is the only gate);
the user builds both locally.
