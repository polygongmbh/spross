# Portability — what belongs in kern before Android reaches par

Audit of 2026-08-03, sweeping `App/`, `Watch/`, `Widgets/`, `Shared/` against `kern/` and `android/`.
Delete this file once the moves it lists have shipped.

**Shipped:** moves 1 and 3, and every "smaller" bullet except the ones still listed below.
The four drifts are closed — the run, the offer, the covered languages, the calibration,
the article table, the streak walk, the area buckets and the day helpers are kern's,
and both apps read them. `android/SessionFlow.kt` is gone.
What is left is moves 2, 4, 5, 6 and the remainder of the small list.

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
2. **`session/Turn`** — the produce/recognize turn: feedback state × revealed × typo × heardInstead × otherWord × retry,
   and which rating each branch fires (`SessionView+Produce.swift:26-316`, ~180 lines).
   Folds in `SelfGrading` + `CatalogAnswerGrader`; the view keeps input, focus, animation, sound.
   Includes the copy-step predicate (`SessionView+Copy.swift:122-127`) and the recall-timing capture
   (`SessionView.swift:409-428`).
3. ~~**Profile activation + `BoxConfig.product()`**~~ — shipped as `coveredSources()`/`defaultSource()`
   plus `BoxConfig.product()` and `BoxState.withProductCalibration()`. `availableTargets` keeps its
   `require` deliberately: the safe query is now the one a launch reaches for, and an unknown source
   stays a programming error rather than an empty answer. The rest of `activate` — bundle paths,
   `UserDefaults`, the observable plumbing — stayed iOS, correctly.
4. **`trainer/LetterDrillRun` + `TrainerRun`** — the run drivers around kern's existing ramp
   (`LetterDrillView.swift:54-284`, `TrainerSessionView.swift:59-276`).
   The trainer's adaptive ramp (two clean wins up, a miss down, amber neutral) exists *only* in Swift —
   `LetterDrill.kt:94` already has the same shape, `Trainer.kt` does not.
5. **`trainer/LetterDrillAvailability`** behind an audio-capability port
   (`LetterDrillAvailability.swift:16-131`) — deletes 176 hand-ported Kotlin lines.
6. **`snapshot/WatchRun` + public snapshot DTOs** — the watch queue/ranking/recycling engine
   (`WatchModel.swift:96-289`, ~150 lines, untested), the practice-lap ordering by remaining-span-over-stability,
   `applyRemoteAnswers` idempotent replay (`PhoneConnectivity.swift:73-155`),
   and `WatchSnapshotDoc`/`WatchEntryDto` made public so no consumer re-declares them
   (`Shared/WatchSnapshot.swift:8-114`).

## Smaller — what is left

Shipped from this list: `session/SessionOffer` (its FNV now hashes UTF-8 bytes, so a round may pick a
different one of its three phrasings than Swift's per-process hash did — intended),
`model/Article.kt` (`articleGender` + `shownArticle`), `box/Statistics` (`streakWindow`, `learningCount`,
the area buckets), `box/Time` (`dayKey`/`endOfTomorrow` public).

`Watch/`, `Widgets/` and `WatchWidgets/` keep their gender-table and streak-walk copies — those targets
do not link Kotlin, so the duplication is forced there and only there. Their comments should point at
`model/Article.kt` and `box/Statistics.kt` as the canonical version. On Android, where widgets are
in-process, no such copy may exist.

- `catalog` — `LanguagePicker.choices/apply` (the swap rule is written twice in Swift,
  `OnboardingView.swift:69-119` and `BoxSettingsSection.swift:207-248`, and a third, lossier time in Kotlin),
  `LanguageInfo.pickerRow/pickerLabel` (`DisplayText.swift:7-47`; Android's picker was showing exonyms
  only and is fixed in place, so this is now a two-file agreement rather than a bug),
  `chromeLanguage(source)` (`AppModel.swift:259-276`),
  audio resolution: `clampedGain` / `headMs` (the `20.0 dB` limit and the lead-validity rule exist
  in three places: `PronunciationPlayer.swift:26,102-106`, `AudioManifest.kt:110,113`, `android/audio/PlaybackIndex.kt`),
  `VoiceSelection.preferredTag` (the `es ⇒ es-ES` distinción rule, `Speaker.swift:69-86`, already re-stated in Kotlin).
- `model` — plural sentinel resolution and the alternates-minus-shown-form rule (`DisplayText.swift:67-104`).
  Android's `CardDisplay.kt` had omitted the exclusion, the empty-plural guard and the canonical form
  itself; fixed in place, so the two now agree — but they agree in two files, which is the thing to close.
- `session/MultipleChoice.question` — the watch samples and shuffles kern's ranked shortlist in Swift
  (`WatchPracticeQuestion.swift:24-49`); `RecognitionGrading` — latency→rating
  (`WatchGrading.swift:14-29`), the sibling of `SelfGrading.kt:33-51`.
- `box` — `enqueueableCount` (`AppModel+Queries.swift`, which restates half of
  `BoxEngine.enqueue`'s skip rules), `CardRowState` (`BoxCardRow.swift:59-98`),
  and the `PhaseBadge` invariant that the seal follows `consolidated`, not phase (`ProgressComponents.swift`) —
  a domain rule currently stated only in a view.
  The browser's grouping and fold state are gone with the Box screen; area ORDER is still
  Swift's (`areaNames`), and the forest's growth rungs went straight into kern
  (`box/GrowthStage.kt`) rather than being derived from schedules app-side.
- `box/TodayReport` — which summary parts appear and the done-vs-caught-up choice
  (`HeuteView.swift:99-217`, `SessionCompletionView.swift:43-49`).
  The strings stay platform-side; the rule choosing the key does not.
- Duplicated-in-iOS-already, so kern by default: `alsoAccepted`
  (`SessionView+Produce.swift:261-264` = `LetterDrillView+Grading.swift:106-109`, byte-identical),
  `summaryEmoji` thresholds 10/5/2 (`TrainerSessionView+Drill.swift:267-274` = `LetterDrillView+Stages.swift:268-275`),
  the drill normalizer's strictness triple (`TrainerHubView.swift:85-92` = `LetterDrillView+Grading.swift:117-118`).

## Stays native

SwiftUI/Compose layout, animation and transitions; `@FocusState` and focus ordering;
haptics and sound playback; `AVAudioEngine`/`AVSpeechSynthesizer`/`AVAudioSession` and their Android counterparts;
WidgetKit timelines and WatchConnectivity transport (the *event shape* is portable, the transport is not);
App Group / file container paths; `Bundle`/assets resolution behind kern's `CatalogSource` port;
accessibility flag reads (VoiceOver, Switch Control) — though the *policy* they gate is portable;
`UserDefaults`/`SharedPreferences` (the keys and defaults are shared contract and are listed above);
the theme's spacing, type ramp and hex pairs; localized string tables.
