# Unification review — open findings

Five read-only reviewers (session/box, trainer/listening/watch, kern, platform hygiene, docs/gates) ran on 8.7.0.
Shipped: doc truth fixes, backlog refresh, release lint parity, strings.py drift fix, dead codemod, hook path, platform comments.
Full evidence (file:line, both sides) is in the five reports beside this file: `session-box.md`, `trainer-listening-watch.md`, `kern.md`, `platform-tooling.md`, `docs-gates.md`.
They describe the tree at 8.7.0, before the fixes this branch shipped.

## Kern only — `:kern:jvmTest` covers it, runnable in a cloud session

- One `internal` booking helper under the six drill reducers (`WordScrambleRun`, `SentenceScrambleRun`, `OppositesRun`, `LetterDrillRun`, `CountryDrillRun`, `DateDrillRun`): the `close`/`confirm`/`booked`/`paced`/`advanced` block is written six times, `WINS_TO_ADVANCE = 3` six times; public types stay put so the ObjC header does not move.
- Then thin the per-drill copies of the shared rule's tests (ladder-out ×3, pending-books-on-close ×3, resample-once ×5, same-seed ×2) to one helper test plus one wiring case per drill.
- Past-justifying KDoc: `SessionRun`, `TurnMachine`, `NumbersRun`, `SessionComposer`, `SlotDraws`, `StabilityBars`, `BoxBrowser`, the "v1" mentions (`Time`, `Config`, `Language`, `TrainerLanguagePack`, `WidgetSnapshotBuilder`), stale copy counts (`DrillSolved`, `DrillRunCore`, `DrillRunProgress`, `DrillProgression`), `Presentation.kt` (backlog).
- `scripts/arch-status.py` prints a wrong survey: suffix stripping misses `NumbersRunView+*.swift`, and `DrillRun.kt`/`TimedRun.kt` count as machines.

## Kern rule plus platform callers — needs a Mac and the Android gate

- `Realization.article` in kern; nine sites spell `grammar["gender"]`, two of them platforms.
- Rules both phones mint: summary hero share `0.45`, reset-export gate `allSettledCount > 0`, backup file name (three sites), Commons credit URL (two encoders), a report's carried answer, own-word row texts, prompt line limits per form (Name already differs, 3 vs 2), coach caption.
- Trainer: `LetterDrillAvailability.Report.formatOpen`, reference-sheet captions, lock-screen now-playing lines, milestone glyphs beside `DrillPauseReason.emoji`, numbers run title rule.
- Widgets: display sort, strip bar width and gutter, the 🗂️ fallback; Android's widget recomputes the chrome language the snapshot already carries (`WidgetSnapshotView` lacks the accessor).
- Card geometry still written twice (emoji slot, reveal rule, replay target, verdict tile, card reserves, activity strip gutter): a `design/` home, after a ruling on where native spacing ends.
- `TurnEffect` and `DrillEffect` repeat four cue cases, so each app dispatches the cue contract twice.
- Layout data in `trainer/` that belongs in `design/`: `Drill.chipRows`, `ReferenceColumns.PAIRED_MIN_WIDTH`, `DrillUnlockMark` timings.
- `CardType.LISTENING_REVEAL_DIVIDED` is a flag nothing flips; drop it and both `divided` parameters.

## Platform only — Mac or Android gate

- iOS: five Design twin types (`SessionOutcome`, `AnswerInputView.Feedback`, `Theme.Gender`, `AreaProgress`, `StageBadge.Stage`) and their `KernBridge` mappings; `DrillRunResult` copies `DrillRunSummary`; dead `SessionView.currentCardID`, `askedByEar`, three `KernBridge` drill extensions, two `hushAnswer()`; the uitest streak seed pasted at seven sites; `0.25`/`0.2` animation literals where `CardMotion` has the value; `controlSurface()` cut by hand at five sites; `Watch/Fixtures` ships in Release.
- Android: dead `AppModel.pronounceTarget`; `TypedDrillView` mirrors `DrillRunProgress` with a dead `bestAnswerStreak`; `errorUnknownProfile`/`errorResetFailed` chrome fields read by nothing; the learner name field and the growable sheet cut by hand; the box hint walks every card where iOS short-circuits on `AudioCapability`.

## Needs the owner

- The letter drill's score line shows a Sprosse on iOS and none on Android (`LetterDrillView+Formats.swift`, `ui/LetterDrillScreen.kt`).
- Kern's `PressKind` is read by iOS only, since 8.6.0 ruled Android ripple-only; its KDoc should say so, or the two should match.
- The iOS summary burst has geometry and timing Android does not draw.
- The v1 store reader (`LegacyStore`, `LegacyDocument`, `LoadedBox.converted`) contradicts the pre-production invariant.
- Emoji as chrome or content: `Drill.emoji` and `DrillPauseReason.emoji` live in kern while the milestone KDoc calls glyphs chrome.
- The three dated audio research docs, `docs/audio-verdicts.tsv` under docs, and `docs/plans/website.md` (the site is on main).
- `web/site.js` runs its own numbers ramp beside kern's `NumbersRun`.
- Semantic linebreaks and the 300-line cap are stated as absolutes with no gate.
