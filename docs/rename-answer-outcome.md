# Plan — `AnswerTone` becomes `AnswerOutcome`

Rename only: no behavior changes, no CHANGELOG entry, no screenshots.
Delete this file in the last commit of the series.

## Why

`kern/src/commonMain/kotlin/net/spross/kern/session/SessionRun.kt:20` declares:

```kotlin
/** How an answer reads back: the grouping is the rule, the color each tone wears is the platform's. */
enum class AnswerTone { Right, Tough, Wrong }
```

Four reasons, each sufficient on its own:

1. **The type is named after the rendering.**
   Its own docstring separates the rule from the color and then names the type after the color.
   `CLAUDE.md` § Invariants: engine APIs name the rule, never the rendering.
2. **The property already carries the right word** — every holder declares `outcomes: List<AnswerTone>`.
3. **`Tough` is already taken, inside kern.**
   `SelfGrading.Verdict { Unknown, Tough, Knew }` (`session/SelfGrading.kt:26`) is what the LEARNER
   reports about difficulty; `AnswerTone.Tough` is what the GRADER decided about a typo.
   Two unrelated concepts, one word, one module.
4. **kern already names this state properly elsewhere** — `TurnFeedback.Almost(correctForm, reason)`
   and `AlmostReason { Typo, Heard }` (`session/Turn.kt:26,37`).
   So the engine named it once correctly, then again as a color and again as `Tough`.

`Almost` rather than `Typo`, because `AlmostReason` shows a typo is only one way in:
a heard-but-different accepted form, a revealed hint, and reading the reference while owing
an answer all land in the same state.
Android's `ui/AlmostCorrection.kt` already uses the word.

## The change

`AnswerTone` → `AnswerOutcome`, and its `Tough` member → `Almost`.
`Right` and `Wrong` keep their names.
69 sites across 19 files; `kern/src` 13, `android/src` 3, `App/Sources` 2, `kern/docs` 1.

Rewrite the declaration's docstring so it states the rule instead of conceding the color —
what the three cases mean, not what they look like.

Rename the carrier names that followed the type: `TrainerRun.kt` passes `tone =` and
`TrainerRunState.kt:159` returns one; those read `outcome` afterwards.

## What must NOT change

- **`SelfGrading.Verdict.Tough`** — `session/SelfGrading.kt:26,46`, `ui/SessionTurn.kt:164`,
  `session/TurnTest.kt:188`, `session/SelfGradingTests.kt:19`.
  A bare `Tough` → `Almost` substitution corrupts these.
  Every `AnswerTone.Tough` in the tree is qualified, so rename the pair `AnswerTone.Tough`
  together and treat the declaration line as the one place a bare `Tough` is yours.
- **`ToneKind { Correct, Wrong, Reveal }`** (`session/Turn.kt:170`) — a different Tone entirely,
  an audible cue rather than a color. Untouched.
- **"amber" outside kern** — `App/Sources/Design/AnswerInputView.swift`, `android/.../ui/Theme.kt`,
  `ui/Components.kt` and their siblings.
  There the word is literally true.
  `ui/Components.kt:70` is the shape the whole rename is for:
  `AnswerTone.Tough -> palette.amber` becomes `AnswerOutcome.Almost -> palette.amber`,
  the platform mapping a rule to a color, which is the platform's job.

## "amber" inside kern

58 occurrences, all prose — comments, KDoc and test names describing a rule by the color a
platform paints it.
Reword to `almost` (or to what the sentence actually means) per site; do not blind-replace,
since a few are unrelated (`FrenchNumbers.kt`, `EnglishClockRegisters.kt`,
`design/PaletteParityTest.kt` — that last one is genuinely about the palette).

Heaviest: `trainer/LetterDrillRunTest.kt` 10, `trainer/TrainerRunTest.kt` 7,
`trainer/TrainerCloseTest.kt` 7, `trainer/CountryDrillRunTest.kt` 6,
`trainer/TrainerRun.kt` 4, `trainer/LetterDrillRun.kt` 4.

## Order

Rename and prose are separable; commit them apart so the mechanical diff stays reviewable.

1. `kern/src` main + tests: the type, its member, the docstring, the `tone` carriers.
   Gate: `./gradlew --console=plain -q :kern:jvmTest`.
2. `android/src`: `ui/Components.kt`, `ui/DrillChrome.kt`, `AppModel.kt`.
   Gate: `./gradlew --console=plain -q :android:compileDebugKotlin`.
3. `App/Sources`: `KernBridge.swift`, `Model/AppModel+Session.swift` — the SKIE-generated Swift
   name follows the Kotlin one.
   Gate: `xcodebuild -project Spross.xcodeproj -scheme Spross \
   -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build -quiet`.
4. `kern/docs/turns.md` and the kern prose sweep.
5. Delete this file.

Scale is hand-editable — 69 sites, and the `Tough` collision makes a blind codemod the
riskier tool. If you reach for one, `ast-grep` over `AnswerTone.Tough` as a unit, and read
the diff before staging.

## Gates

All three must be green, `:kern:jvmTest` last since it reads `App/Sources` and `android/src`
as declared inputs.
`LayerBoundaryTest` derives its decision list from kern's declared enums, so confirm it still
reports 2 tests 0 failures rather than silently finding nothing.

`:android:testDebugUnitTest` is red for an unrelated reason: `Chrome` is a 247-parameter data
class whose generated `copy$default` exceeds the JVM's 255-slot method limit, so 7 tests die
with `ClassFormatError` at class load. Not this series' problem.
