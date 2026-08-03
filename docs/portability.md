# Portability — what belongs in kern before Android reaches par

Audit of 2026-08-03, sweeping `App/`, `Watch/`, `Widgets/`, `Shared/` against `kern/` and `android/`.
Delete this file once the moves it lists have shipped.

The native layers should own aesthetics and device facts only:
layout, animation, focus, haptics, audio engines, widget timelines, accessibility flags, string tables.
Everything below is currently Swift but is a *rule*, and Android has to re-derive it or copy it.

## The evidence: drift is not hypothetical

`android/` already re-implements the app layer (~3.5k LOC main, ~750 test).
Four behavioral deltas exist today, each in logic that was left platform-side:

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

Every one is a rule that exists twice.

## Pattern: there is none on iOS; Android already picked MVVM

`AppModel` is a 1229-line `@Observable` Store/Controller hybrid — 96 public members,
20 stored state fields (12 of them `session*`), ~45 uncached derivations,
plus a `mutate(_:)` escape hatch on the aggregate (`AppModel.swift:329`).
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

1. **`session/SessionRun`** — queue, endless refill, delta fold, stale recompose, summary tallies,
   `canPracticeMore` / `streakIsRecord` (`AppModel+Session.swift:14-239`, ~200 lines;
   Android's `SessionFlow.kt`, 105). All four drifts collapse into one file with one test suite.
2. **`session/Turn`** — the produce/recognize turn: feedback state × revealed × typo × heardInstead × otherWord × retry,
   and which rating each branch fires (`SessionView+Produce.swift:26-316`, ~180 lines).
   Folds in `SelfGrading` + `CatalogAnswerGrader`; the view keeps input, focus, animation, sound.
   Includes the copy-step predicate (`SessionView+Copy.swift:122-127`) and the recall-timing capture
   (`SessionView.swift:409-428`).
3. **Profile activation + `BoxConfig.product()`** — join → decode → calibrate → bootstrap → persist
   (`AppModel.swift:93-252`), and the calibration constants restated in Swift (`KernBridge.swift:117-148`)
   that already exist as kern defaults (`Config.kt:13-48`). Closes drift 4.
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

## Smaller, mostly cheap

- `session/SessionOffer` — round classification, the `reviewsLeadFrom = 3` threshold, and the FNV-1a-64
  headline pick (`AppModel+Queries.swift:28-107`). kern already has `fnv1a64` (`Presentation.kt:147`, `internal`).
  Cross-platform determinism is the stated requirement, so it cannot stay in Swift.
- `model` — `articleGender(tint)`: the article→gender table is copied five times
  (`Theme.swift:81-97`, `WatchTheme.swift:28-44`, `WordWidgetView.swift:147-162`,
  `WatchWordWidgetView.swift:63-72`, `android/ui/Theme.kt:14-26`). The colors stay per-surface.
- `box/Statistics` — the streak walk is written three times (`Statistics.kt:90-108`,
  `WidgetSnapshot.swift:53-75` which labels itself a deliberate duplicate, `ActivityStripView.swift:81-103`);
  add `streakWindow(days)` and `learningCards` (`max(0, active - consolidated)`, computed in four places).
- `box/Time` — `isoDayKey` (`KernBridge.swift:21-26`) and `tomorrowDueCount` (`AppModel+Queries.swift:119-125`)
  re-implement `Time.kt:19,26-29`; expose those instead.
- `catalog` — `LanguagePicker.choices/apply` (the swap rule is written twice in Swift,
  `OnboardingView.swift:69-119` and `BoxSettingsSection.swift:207-248`, and a third, lossier time in Kotlin),
  `LanguageInfo.pickerRow/pickerLabel` (`DisplayText.swift:7-47`),
  `chromeLanguage(source)` (`AppModel.swift:259-276`),
  audio resolution: `clampedGain` / `headMs` (the `20.0 dB` limit and the lead-validity rule exist
  in three places: `PronunciationPlayer.swift:26,102-106`, `AudioManifest.kt:110,113`, `android/audio/PlaybackIndex.kt`),
  `VoiceSelection.preferredTag` (the `es ⇒ es-ES` distinción rule, `Speaker.swift:69-86`, already re-stated in Kotlin).
- `model` — plural sentinel resolution and the alternates-minus-shown-form rule (`DisplayText.swift:67-104`);
  Android's `CardDisplay.kt:14-29` omits the exclusion, so its "auch:" line repeats the word on screen.
- `session/MultipleChoice.question` — the watch samples and shuffles kern's ranked shortlist in Swift
  (`WatchPracticeQuestion.swift:24-49`); `RecognitionGrading` — latency→rating
  (`WatchGrading.swift:14-29`), the sibling of `SelfGrading.kt:33-51`.
- `box` — browser grouping/ordering/`enqueueableCount` (`AppModel+Queries.swift:233-307`,
  which restates half of `BoxEngine.enqueue`'s skip rules), `CardRowState` (`BoxCardRow.swift:59-98`),
  the `AreaChip` bucket split (`ProgressComponents.swift:112-122`),
  and the `PhaseBadge` invariant that the seal follows `consolidated`, not phase (`ProgressComponents.swift:206-217`) —
  a domain rule currently stated only in a view.
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
