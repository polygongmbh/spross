# Portability — what belongs in kern before Android reaches par

Audit of 2026-08-03, sweeping `App/`, `Watch/`, `Widgets/`, `Shared/` against `kern/` and `android/`.
Delete this file once the moves it lists have shipped.

**Shipped (2026-08-08):** moves 1, 2 and 3, and the whole vocab-loop small list —
the run, the turn, the offer and its summary, the covered languages, the calibration,
the article table, the streak walk, the area buckets, the day helpers, the box browser,
the today report and the chrome-language rule are kern's, and both apps read them.
`android/SessionFlow.kt` is gone.
**Also shipped (2026-08-08, later the same day):** moves 4 and 5 — both drill runs and
the letter-drill availability sweep are kern machines, and both apps drive them.
What is left is watch territory only: move 6 (deferred per user 2026-08-08)
and the watch bullets at the end of the small list.

The native layers should own aesthetics and device facts only:
layout, animation, focus, haptics, audio engines, widget timelines, accessibility flags, string tables.
Everything below is currently Swift but is a *rule*, and Android has to re-derive it or copy it.

## The evidence: drift was not hypothetical

All four below are closed — kept here because they are the argument for finishing the list,
not a status board. Each was a rule left platform-side and written a second time.

`android/` re-implements the app layer (~3.5k LOC main, ~750 test):

1. **Extra round composes differently.**
   `AppModel+Session.swift:36-51` calls `composeExtraSession` directly;
   its `why:` records that trying `composeEndless` first was a bug — endless is rarely empty,
   so the mixing round almost never composed and the extra round came back all first sights.
   `android/SessionFlow.kt:14-23` still tries `composeEndless` first, in a docstring claiming it mirrors iOS.
2. **Session summaries count different things.**
   iOS buckets new / crossed-into-consolidated / review (`AppModel+Session.swift:110-125`),
   with a `why:` rejecting the phase edge as the signal.
   Android counts `phase == New` and `rating != Again` (`SessionFlow.kt:60-74`) — the rejected heuristic.
3. **Android loses the day's fold.** iOS folds a partial session on background (`AppModel.swift:289` → `+Session.swift:218`);
   `SprossActivity.kt` has no `onPause` path, so an evicted Android app drops streak-bearing reviews.
4. **Android crashes on an uncovered device locale.**
   `android/AppModel.kt:133` calls `Catalog.availableTargets(device)`, which does `require(source in languages)`
   (`Catalog.kt:154`). iOS intersects with `catalog.languages.keys` first (`AppModel.swift:102-105`).
   A French or Italian device throws at launch.

Every one was a rule that existed twice.
Android's own `SessionFlowTest` *asserted* three of them, the extra-round ordering by name —
a test suite pinning the drift as intended behavior is what a second implementation buys you.

## Pattern: there is none on iOS; Android already picked MVVM

As audited, before the first moves landed —
`AppModel` was a 1229-line `@Observable` Store/Controller hybrid — 96 public members,
20 stored state fields (12 of them `session*`), ~45 uncached derivations,
plus a `mutate(_:)` escape hatch on the aggregate.
The twelve session fields are now one `SessionRunState`; the rest stands.
`todayPlan` recomposes the whole session on every read (`AppModel+Queries.swift:12-17`).

Three screens are their own uncontrolled view models — 47 `@State` between them:
`SessionView` (16, including a parked un-applied `Rating`), `LetterDrillView` (15, the whole drill run,
with a reducer named `advance` living inside a `View`), `TrainerSessionView` (16, and it writes persistent records).
There is no Swift test target, so none of it is testable.
The two drill screens now each hold one kern run state (`TrainerRunState` / `LetterDrillRunState`)
plus input and focus; the rest stands.

`android/` did better: `AndroidViewModel` + a `SessionUi` state DTO + flow logic extracted
into Android-free testable classes (`SessionFlow`, `LetterDrillFlow`, `CardDisplay`, `LanguagePicker`).
Composables are pure functions of that state.

kern is a stateless pure-function library — and `BoxEngine.answer(state, id, rating, now, tz) -> Outcome`
is already a reducer signature with no runner around it.
`Presentation.kt:128` records the precedent: a rule moved into kern precisely because both apps had re-derived it.

## Decision: kern owns the machines, platforms own the screens

Not "kern as view-model layer" — exposing observable per-screen state over the ObjC bridge
needs coroutines/SKIE that kern has deliberately never taken, and would drag copy selection,
locale resolution, focus ordering and accessibility gating into Kotlin.

Not "MVVM per platform over a shared model" — that is what exists, and it produced four drifts.

Instead: **immutable run state + pure `reduce(state, intent, now, tzId)` in kern**;
each platform holds the returned value in `@Observable` / `mutableStateOf` and renders it.
No new kern dependency; data classes already cross the boundary.

## Moves, highest value first

1. ~~**`session/SessionRun`**~~ — shipped. `canPracticeMore`/`canPracticeExtra`/`sessionAvailable`
   landed in `SessionOffer.kt` rather than `SessionRun.kt`: they are box queries, not run state.
2. ~~**`session/Turn`**~~ — shipped as `session/TurnMachine.kt` + `TurnWriteOut.kt`:
   feedback state × revealed × typo × heardInstead × otherWord × retry,
   which rating each branch fires, the copy-step predicate and the recall-timing capture,
   folding in `SelfGrading` + `CatalogAnswerGrader`.
   Both apps dispatch every turn intent through it and keep only input, focus, animation
   and cues (iOS `SessionView+Turn.swift`, Android `TurnFlow.kt`);
   Android's four divergences (pickable Easy, no write-out, no retry,
   answer shown in the field on reveal) disappeared by construction on adoption.
3. ~~**Profile activation + `BoxConfig.product()`**~~ — shipped as `coveredSources()`/`defaultSource()`
   plus `BoxConfig.product()` and `BoxState.withProductCalibration()`. `availableTargets` keeps its
   `require` deliberately: the safe query is now the one a launch reaches for, and an unknown source
   stays a programming error rather than an empty answer. The rest of `activate` — bundle paths,
   `UserDefaults`, the observable plumbing — stayed iOS, correctly.
4. ~~**`trainer/LetterDrillRun` + `TrainerRun`**~~ — shipped as
   `trainer/TrainerRun.kt` + `TrainerRunState.kt` and `LetterDrillRun.kt` + `LetterDrillRunState.kt`
   over the shared `DrillRun.kt` effects; both apps dispatch intents and render the returned state,
   keeping only input, focus, audio and the auto-advance timers.
5. ~~**`trainer/LetterDrillAvailability`**~~ — shipped behind a has-voice flag
   (`LetterDrillAvailability.report(catalog, box, language, hasVoice)`);
   Android's 176 hand-ported lines are gone and iOS keeps a thin adapter.
6. **`snapshot/WatchRun` + public snapshot DTOs** — the watch queue/ranking/recycling engine
   (`WatchModel.swift:96-289`, ~150 lines, untested), the practice-lap ordering by remaining-span-over-stability,
   `applyRemoteAnswers` idempotent replay (`PhoneConnectivity.swift:73-155`),
   and `WatchSnapshotDoc`/`WatchEntryDto` made public so no consumer re-declares them
   (`Shared/WatchSnapshot.swift:8-114`).

## Smaller — what is left

Shipped from this list, both apps reading kern:

- `session/SessionOffer` (its FNV now hashes UTF-8 bytes, so a round may pick a
  different one of its three phrasings than Swift's per-process hash did — intended),
  including `summaryParts()` — the reviews+ahead merge / Auffrischer / fresh-append rule;
  its empty list is deliberately each surface's own fallback phrase, the `tallyParts` contract.
- `model/Article.kt` (`articleGender` + `shownArticle`), `box/Statistics` (`streakWindow`,
  `learningCount`, the area buckets), `box/Time` (`dayKey`/`endOfTomorrow` public).
- `catalog/LanguageChoices` — the swap rule (`targetChoices`/`pickSource`/`pickTarget`),
  `pickerRow`/`pickerLabel`, and `chromeLanguage`/`hasChrome`:
  all three copies are gone (`android/LanguagePicker.kt` deleted;
  iOS `AppModel` and Android `Chrome.forSource` both read kern for the en fallback).
- `catalog/Playback` (`gainDb`/`headMs`) and `catalog/VoiceSelection`
  (`select` on iOS, `preferredTag` on Android — two halves of the same object).
- `model` plural sentinel and alternates-minus-shown as `pluralForm`/`alternates`;
  `Card.alsoAccepts` as `session/SpokenAnswer.kt` (iOS keeps a one-line wrapper).
- `box/BoxBrowser` — grouping/ordering/`enqueueableCount`/`enqueueableCardIds`
  (the count and the enqueue share one predicate) and `CardRowState`,
  whose `Standing` owns the seal-follows-consolidated invariant `PhaseBadge` used to state.
- `box/TodayReport` — `worked`, `tallyParts`, `tomorrowNote`, `completionTallyParts`.

`Watch/`, `Widgets/` and `WatchWidgets/` keep their gender-table and streak-walk copies — those targets
do not link Kotlin, so the duplication is forced there and only there. Their comments should point at
`model/Article.kt` and `box/Statistics.kt` as the canonical version. On Android, where widgets are
in-process, no such copy may exist.

Still without the kern home the audit asked for (all watch-scoped, plus one drill remainder):

- `session/MultipleChoice.question` — the watch samples and shuffles kern's ranked shortlist in Swift
  (`WatchPracticeQuestion.swift:24-49`); `RecognitionGrading` — latency→rating
  (`WatchGrading.swift:14-29`), the sibling of `SelfGrading.kt:33-51`.
- ~~The drill normalizer's strictness triple~~ — shipped as `AnswerNormalizer.drill`,
  and all four platform sites call it. The streak-tier ladder itself is closed:
  kern owns it (`DrillRun.kt` `StreakTier`), Android reads it, and only the iOS tile
  still hand-codes the thresholds (backlog).

## Stays native

SwiftUI/Compose layout, animation and transitions; `@FocusState` and focus ordering;
haptics and sound playback; `AVAudioEngine`/`AVSpeechSynthesizer`/`AVAudioSession` and their Android counterparts;
WidgetKit timelines and WatchConnectivity transport (the *event shape* is portable, the transport is not);
App Group / file container paths; `Bundle`/assets resolution behind kern's `CatalogSource` port;
accessibility flag reads (VoiceOver, Switch Control) — though the *policy* they gate is portable;
`UserDefaults`/`SharedPreferences` (the keys and defaults are shared contract and are listed above);
the theme's spacing, type ramp and hex pairs; localized string tables.
