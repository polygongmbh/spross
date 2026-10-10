# Review C — the kern (`kern/src/*`, build glue, arch gates)

Read-only review, ranked by value within each section. Every file:line was read; every "unused" claim was
confirmed with `rg` across `kern/src/*Main App/Sources android/src/main Shared Watch Widgets WatchWidgets web`
(tests excluded). Items the backlog already names are marked **already in backlog** and listed only where
this review adds something.

Gate legend — **jvmTest**: `./gradlew :kern:jvmTest` alone covers it (Kotlin-internal or test-only change).
**Mac**: the change moves the ObjC header or an Android/Swift call site, so an app build is needed too.
**none**: docs/comments only.

---

## 1. `kern/src/commonMain` — duplication and cruft inside kern

### 1.1 Six drill reducers carry the same ~90-line booking block; four of them are not in the backlog
**Evidence.** `close` / `confirm` / `elapsed` / `booked` / `paced` / `advanced` are written once per drill:
- `trainer/WordScrambleRun.kt:108-228`
- `trainer/SentenceScrambleRun.kt:92-232`
- `trainer/OppositesRun.kt:76-161`
- `trainer/LetterDrillRun.kt:96-243`
- `trainer/CountryDrillRun.kt:103-225`, `trainer/DateDrillRun.kt:105-245` (**already in backlog** as the Country/Date pair)

The four scramble-family copies are line-for-line identical apart from the state type, e.g.
`WordScrambleRun.kt:108-120` vs `OppositesRun.kt:76-84`:
```kotlin
val pending = TypedDrillVerdicts.pending(state.feedback)
    ?.let { advanced(state, it.correct, it.clean) }
    ?: state
val ended = pending.copy(feedback = TurnFeedback.Neutral, finished = true)
val summary = if (ended.done == 0) null else DrillRunSummary(ended.done, ended.bestAnswerStreak, newRecord = false)
return …Close(ended, summary, ended.bestSprosse, ended.clearedSprossen, effects)
```
and `booked` (`WordScrambleRun.kt:164-194`, `SentenceScrambleRun.kt:160-198`, `OppositesRun.kt:123-142`,
`LetterDrillRun.kt:179-217`) and `advanced` (`WordScrambleRun.kt:202-228` vs `SentenceScrambleRun.kt:205-232`,
`OppositesRun.kt:144-161`) differ only in the `top` argument and one extra field.
Around them the same shape is repeated as well:
- `WordScrambleClose` / `SentenceScrambleClose` / `OppositesClose` / `LetterDrillClose` have the same five fields and
  the same `bookings(target) = DrillBookings.masked(...)` body, with the KDoc pasted verbatim
  (`WordScrambleRunState.kt:48-69`, `SentenceScrambleRunState.kt:47-68`, `OppositesRunState.kt:41-53`,
  `LetterDrillRunState.kt:40-56`).
- `internal val newSprossen: Int get() = (clearedSprossen - config.cleared).size` three times
  (`WordScrambleRunState.kt:126`, `SentenceScrambleRunState.kt:173`, `OppositesRunState.kt:89`).
- `const val WINS_TO_ADVANCE = 3` declared six times, each KDoc pointing at another drill's copy
  (`LetterDrill.kt:37`, `SentenceScrambleRun.kt:35`, `CountryDrill.kt:47`, `DateDrill.kt:51`, `OppositesRun.kt:22`,
  `WordScrambleRun.kt:28`).

**Why it matters.** CLAUDE.md: "a behavioral rule lives in kern once". `DrillRunCore`'s own KDoc (line 13) says "four
copies of one piece of arithmetic is how two streaks come to disagree" — the arithmetic around it is still four to six
copies. The ruling in `kern/docs/turns.md:128-129` ("no shared `ScrambleRun<T>`… a generic arrives opaque in Swift")
covers the PUBLIC draw/state types, not this private block, which never crosses the boundary.
**Fix.** An `internal` helper (e.g. `MaskedLadderBooking`) taking the three per-drill lambdas (ramp step, draw,
copy-with-ladder) and returning the next ladder figures; keep every public `*RunState`/`*Close` concrete so the ObjC
header is untouched. Fold the six `WINS_TO_ADVANCE` into one `DrillRamp.USUAL_WINS` (the `fast` variants already
compute off a boolean). Collapse the four `*Close` KDocs to one reference.
**Size** M. **Gate** jvmTest (internal-only extraction) — or Mac if the `*Close` records are merged into one type.
**Confidence** verified.

### 1.2 The v1 store reader is past its life (pre-production invariant says so)
**Evidence.** `store/LegacyStore.kt:17-18`: "Delete this file, and the v1 reader beside it, once no device can still be
holding a v1 box". `store/LegacyDocument.kt:24` "this file exists only to leave it behind". `StoreCodec.kt:39-43` routes
on `LEGACY_SCHEMA_VERSION` and `LoadedBox.converted` exists only to tell the app to rewrite
(`App/Sources/Store/BoxStore.swift:69`, `android/.../BoxStore.kt:62-63`). CLAUDE.md Invariants: "Pre-production — no
live user data… migrations need only behavioral parity." `StoreGoldenTest.kt:13` still says "the store is the one place
real user data lives".
Footprint: `LegacyStore.kt` 55 + `LegacyDocument.kt` 205 + `StoreCodec.load`/`LoadedBox` ~15 lines;
`LegacyStoreTests.kt` 134; `StoreGoldenTest.aRealV1DocumentConvertsToTheSameBox` + `resources/store-legacy-v1-box.json`;
`kern/docs/snapshots.md:30-31` paragraph; two app lines.
Same family, **already in backlog**: `VerbSlugRekey.kt` ("delete at 7.0+", still wired at `BoxEngine.kt:36-37` with a
`TODO`).
**Fix.** Delete the v1 branch: `StoreCodec.load` becomes `decode` (or stays and returns `converted = false` forever),
drop `LegacyStore`/`LegacyDocument`/`LEGACY_SCHEMA_VERSION`, their tests and the resource, and the `snapshots.md`
paragraph; do VerbSlugRekey in the same sweep.
**Size** M. **Gate** jvmTest if `load`/`LoadedBox` are kept as a thin shell; Mac if they are removed (header + two app
call sites). **Confidence** verified.

### 1.3 Read-model fields computed on every call that nothing reads (extends the backlog's "only tests read" line)
**Evidence** (platform refs counted with `rg -w` over all app trees; kern-internal refs checked too):
- `BoxStatistics.longestStreak` (`box/Statistics.kt:24`, computed at `:77` by `longestStreak()` `:150-168`, which walks
  every calendar day from the first answer to today on **every** `BoxEngine.statistics` call) — 0 platform refs,
  0 kern readers, 5 test refs. Documented as a read model in `kern/docs/reports.md:136`.
- `BoxStatistics.suspendedCount` (`Statistics.kt:18`, `:74`, via `Inventory.suspendedCount` `:74`) — 0 platform refs,
  0 kern readers, 1 test ref.
- `BoxBrowser.queueableCount` / `unqueueableCount` (`box/BoxBrowser.kt:159`, `:176`) — public, test-only (4 and 2 refs).
- `WebTrainer.reference` + `WebRefBand` + `WebRefRow` (`jsMain/.../web/WebDrill.kt:94-99`, `:110-114`) — `@JsExport`ed,
  `web/site.js` calls only `spellNumber`, `placeValueHint`, `tensReference`, `NumbersDrill.sample/grade`; no `website`
  branch exists in this clone to check further.
- **Already in backlog**: `AreaStatistics.notIntroduced`, `CatalogArea.conceptsBySlug`,
  `WordScrambleMasking.fullyScrambled`, `Catalog.dateNames`.
**Why it matters.** Dead public surface on the ObjC header, and `longestStreak` is the one with a real cost (an
O(days-since-first-answer) walk per statistics call; `WatchSnapshotBuilder`/`WidgetSnapshotBuilder` already compute their
own streak timelines separately).
**Fix.** Drop the four fields/functions and their tests, or wire `longestStreak` into a surface if the product wants
it (then it is a design item, not kern's). Drop `WebTrainer.reference` or have `web/` use it.
**Size** S. **Gate** Mac for `BoxStatistics` fields and `BoxBrowser` (data-class/header change); the JS facade is
gated only by `:kern:jsBrowserDistribution`, which this session cannot run. **Confidence** verified.

### 1.4 Two parallel effect hierarchies: `TurnEffect` and `DrillEffect` repeat four of five cases
**Evidence.** `session/Turn.kt:261-282` (`Answer`, `ArmAdvance(beat)`, `CancelAdvance`, `PrimeField`, `Tone(kind)`,
`ReleaseFocus`) and `trainer/DrillRun.kt:18-36` (`ArmAdvance(beat)`, `CancelAdvance`, `Tone(kind)`, `ReleaseFocus`,
`Silence`). Both platforms therefore dispatch the shared four twice: `App/Sources/Screens/DrillRunning.swift:153-161`
vs `SessionView+Turn.swift:31-46`; `android/.../DrillEffects.kt:81-85` vs `TurnFlow.kt:188-194`.
**Why it matters.** The beat/tone/focus contract is one rule with two spellings; a platform handler added for one
(e.g. a reduced-motion rule on `ArmAdvance`) silently misses the other.
**Fix.** One `CueEffect` sealed interface (`ArmAdvance`, `CancelAdvance`, `Tone`, `ReleaseFocus`, `Silence`) that
`TurnEffect` and `DrillEffect` both carry (or `TurnEffect` = `CueEffect` + `Answer` + `PrimeField`), so each platform
has one cue handler.
**Size** M. **Gate** Mac (header and both apps' `switch`es). **Confidence** verified.

### 1.5 KDoc and comments that justify against the past (beyond the backlog's `Presentation.kt` entry)
**Evidence** (CLAUDE.md: "always only explain the current state of things"):
- `session/SessionRun.kt:112` "the machine both apps used to re-derive"; `:153` "it used to be composed by rules of its
  own…"; `:230` "Cards coming due… used to be pulled straight in, so '12/30' quietly became '12/37'".
- `session/TurnMachine.kt:12`, `trainer/NumbersRun.kt:12` — same "used to re-derive" sentence.
- `session/SessionComposer.kt:109` "they used to have their own, which is…".
- `trainer/SlotDraws.kt:9` "The drill used to recover its value from the rendered prompt".
- `box/StabilityBars.kt:19` "A separate, faster `settledStability` of 2.0 used to gate…".
- `box/BoxBrowser.kt:182` "Grouping once costs what a single shelf used to."
- `box/Time.kt:53` "(fixes v1's latent non-Gregorian bug)"; `model/Config.kt:4` "v1 calibration";
  `model/Language.kt:13` "the v1 choice"; `trainer/TrainerLanguagePack.kt:15` "the v1 arm64_32 guard";
  `snapshot/WidgetSnapshotBuilder.kt:28` "v1 widget timeline depth".
- `store/LegacyStore.kt:46` "the leech rule removed on 2026-09-01" (goes with 1.2).
- Stale self-counts: `trainer/DrillSolved.kt:4-5` "shared by all three drills" (it keys seven);
  `DrillRunCore.kt:13` "four copies"; `DrillRunProgress.kt:16` "seven copies"; `DrillProgression.kt:176` "what lived
  four times before"; `CountryDrillRun.kt:9` "the third sibling of…"; `jvmTest/arch/LayerBoundaryTest.kt:15-16`
  "for thirteen days iOS called it Good while Android called it Hard".
- `box/BoxEngine.kt:36` `// rekeyingPrefixedVerbs: TODO remove once the app is past 7.0.` (**already in backlog**).
**Fix.** Rewrite each to state the rule as it stands; drop the counts.
**Size** S. **Gate** none. **Confidence** verified.

### 1.6 Layout and animation data living in `trainer/` rather than `design/`
**Evidence.** `kern/README.md` names `net.spross.kern.design` as the home of "layout data both platforms draw from".
Three such tables sit in `trainer/`:
- `trainer/Drill.kt:24-35` `Drill.chipRows(count)` — "How many chips stand on each line of the hub card"
  (consumed by `TrainerHubView.swift:195`, `TrainerHubCard.kt:94`).
- `trainer/NumberReference.kt:35-39` `ReferenceColumns.PAIRED_MIN_WIDTH = 288.0` "in points" (consumed by
  `NumberReferenceTable.swift:55`; Android reads `count()`).
- `trainer/DrillUnlockMark.kt:26-37` `HOLD_MS`, `FADE_MS`, `PADLOCK_SHRINK`, `WASH_ALPHA`, `ANNOUNCE_DELAY_MS`
  (consumed by `android/.../UnlockMark.kt:36-105`).
Related inconsistency: `trainer/DrillRun.kt:55-59` says "which glyph wears a milestone is the platform's chrome", yet
`DrillPauseReason.emoji` (`DrillPacing.kt:21-26`) and `Drill.emoji` (`Drill.kt:14-21`) carry glyphs in kern.
**Why it matters.** The README's "same test applies" rule is file placement; a reader looking for every shared geometry
in `design/` misses these.
**Fix.** Move the three into `design/` (`HubChipRows`, `ReferenceColumns`, `UnlockMark`); decide once whether emoji
are chrome (then `Drill.emoji`/`DrillPauseReason.emoji` move to the string tables) or content (then fix the
`AnswerStreakMilestone` KDoc).
**Size** S. **Gate** Mac (Swift call sites name the objects). **Confidence** verified for placement; the emoji rule is a
decision.

### 1.7 Smaller items
- `trainer/NumbersRun.kt:166-189`, `:209-217` re-spell `TypedDrillVerdicts.submit`/`reveal` by hand, differing only in
  the `DrillEffect.Silence` entry; the KDoc at `TypedDrillVerdicts.kt:29-30` calls this deliberate. Worth one
  `silence: Boolean` parameter instead of a second ladder. S, jvmTest, likely.
- `design/PressKind.kt` is read by iOS alone (`Theme+Styles.swift:58-61`); Android has no press-scale at all. Kern side
  is fine; a parity pointer for the app reviewers.
- `expect`/`actual`: one pair (`model/Nfc.kt` ↔ four actuals), every actual has a consumer; `jsMain` is consumed by
  `web/site.js` and `scripts/build-web.sh`. No dead platform source sets.
- `design/` carries only the geometry the README licenses; no screen positions leaked elsewhere beyond 1.6.

---

## 2. `kern/src/commonTest` + `kern/src/jvmTest` — overtesting

### 2.1 A test that pins the shipped calibration verbatim
**Evidence.** `fsrs/FsrsBehavioralTest.kt:23-31` `theProductShipsAnAlternatingTenMinuteToOneMonthLadder`:
```kotlin
assertEquals(listOf(600L, 86_400L, 600L, 259_200L, 600L, 604_800L, 600L, 2_592_000L), productParameters.stepsSeconds)
assertEquals(0.85, productParameters.desiredRetention)
assertEquals(365, productParameters.maximumIntervalDays)
```
The comment above calls it "the tripwire that catches a change to the numbers". `:34-41`
`retentionPointEightFiveSchedulesRoughlyTwiceStability` asserts `assertEquals(10, interval)` on the product value as well.
**Why it matters.** CLAUDE.md Tests: "Test rules and behavior, not implementation details or tweakable constants"; the
ladder's RULE (alternating, climbs on Again, caps at the last step) is already `FsrsStepLadderTest`.
**Fix.** Delete the first test; keep only the ratio assertion of the second.
**Size** S. **Gate** jvmTest. **Confidence** verified.

### 2.2 Byte-pinned store golden against the pre-production invariant
**Evidence.** `jvmTest/store/StoreGoldenTest.kt:31-39` `encodedBoxMatchesApprovedFixture` and
`approvedBoxIsByteStableAcrossDecodeEncode` pin exact bytes of `resources/store-golden.json`; KDoc `:11-13` justifies it
with "the store is the one place real user data lives and silent drift there misreads previously written boxes".
CLAUDE.md Invariants: "Byte-exact encoding… are not constraints; migrations need only behavioral (golden-vector)
parity." `StoreCodecTests.aBoxRoundTripsBackToTheStateItHeld`/`encodingDoesNotDependOnInsertionOrder` already cover
the behavioral rules (round-trip, determinism).
**Fix.** Drop the two byte-pins and the fixture (the v1 test goes with 1.2); keep `StoreCodecTests`.
**Size** S. **Gate** jvmTest. **Confidence** verified.

### 2.3 Large generated vectors with no regenerator
**Evidence.** `resources/ukrainian-clock-golden.tsv` — 1440 lines, 246 KB, read by `UkrainianClockGoldenTest.kt`
whose KDoc (`:11-12`) says "a deliberate change to a reading regenerates the file in the same commit" — no script
anywhere produces it (`rg` over `scripts/`, `kern/docs`, `docs`: none). `resources/trainer-golden.json` — 775 lines,
"copied VERBATIM from the v1 Swift suite (originally generated by running the prototype's extracted JS functions with
node)" (`NumbersGoldenTests.kt:10-12`), covering de/sw numbers, years and clock that `NumbersGermanNumberTests`,
`NumbersGermanClockTests` and `NumbersSwahiliClockTests` test by rule.
**Why it matters.** A vector nobody can regenerate is re-approved by hand or deleted when a reading changes; the first
is the inversion `kern/README.md` §7 warns against.
**Fix.** Either add `scripts/clock-golden.py`-style regeneration (one `--write` flag) or replace the TSV with the
rule tests `ClockRevealTests`/`NumbersUkrainianTests` already hold and keep a handful of representative minutes; retire
`trainer-golden.json` where the per-language tests already pin the same readings.
**Size** M. **Gate** jvmTest. **Confidence** likely (the duplication with the per-language tests is partial, not total).

### 2.4 The same shared rule tested once per drill (follows 1.1)
**Evidence** (test names, `commonTest/trainer`): `aLadderAnsweredOutEndsTheRun` in `CountryDrillRunTest`,
`LetterDrillRunTest`, `SentenceScrambleRunTest`; `aPendingAnswerBooksOnTheWayOutExactlyAsTheTapWould` in
`CountryDrillRunTest:481` and `DateDrillRunTest:163` plus `LetterDrillRunTest.closingBooksAPendingAnswerAndKeepsNoRecord:410`;
`aSprosseWithNothingLeftToAskIsClimbedPast` in `CountryDrillRunTest:372` and `NumbersRunTest`; the "resampled once"
rule (`DrillLadder.pickAvoiding`) five times (`CountryDrillTests:361`, `DateDrillTests:181`, `LetterDrillTests:98`,
`:137`, `LetterDrillDictationTests:82`); `theSameSeedDrawsTheSameRun` in `CountryDrillTests:350` and
`DateDrillTests:168`. **Already in backlog**: `TypedDrillVerdicts` pinned only through `CountryDrillRunTest`.
**Fix.** With the shared booking helper from 1.1, one `DrillBookingTest` holds these; each drill test keeps one wiring
case.
**Size** S (after 1.1). **Gate** jvmTest. **Confidence** verified.

### 2.5 Duplicate coverage of `StoredBoxes.restoring`
**Evidence.** `store/StoredBoxesTests.kt:25-34` `restoringReplacesWhatItCarriesAndKeepsTheRest` and
`store/BoxBackupTests.kt:63-72` `aRestoreReplacesWhatItCarriesAndKeepsTheRest` assert the same rule on the same
function (the second only routes the import through `encode`/`decode`, which `anExportReadsBackAsTheBoxItCarried`
already covers).
**Fix.** Drop the `BoxBackupTests` copy. **Size** S. **Gate** jvmTest. **Confidence** verified.

### 2.6 A layout outcome pinned on real-catalog bands
**Evidence.** `trainer/ReferenceColumnsTest.kt:16-19` asserts the de `base`/`tens` bands pair at 296 pt, `:27-33`
that no uk band does. That is "which bands look right in two columns on a 360-pt phone" — a presentation choice over
real content, both of which CLAUDE.md says get no test (and README §7 says never pin real-catalog values).
`largeTextOrANarrowPanelKeepsOneColumn` and `aCombiningAccentAddsNoLetter` are the rule tests and stay.
**Fix.** Replace the two with one synthetic case (entries of 10 vs 11 letters). **Size** S. **Gate** jvmTest.
**Confidence** likely.

### 2.7 Four ways to find the repo root in `jvmTest`
**Evidence.** `RepoTree.kt:7-14` `repoRoot` (walks to `kern/build.gradle.kts`); `arch/LayerBoundaryTest.kt:164-171`
a second private `repoRoot` (walks to `kern/src/commonMain` + `App/Sources`); `catalog/RealCatalogTestSupport.kt:11-20`
`RealCatalog.root` (walks to `catalog/areas.json`); `arch/ChromeTableShapeTest.kt:21`, `:101` use
`File("../android/…")` relative to the Gradle working directory.
**Fix.** All four read `net.spross.kern.repoRoot`. **Size** S. **Gate** jvmTest. **Confidence** verified.

### 2.8 Copied fixtures and helpers
- Eight test files hand-build `Card(id=…, kind=…, area=…, emoji=null, seedIndex=…, components=emptyList(), source=…,
  target=…)` under a private `card(...)` (`box/BoxSearchTests.kt:12`, `box/CatalogMatchesTests.kt:17`,
  `box/HarvestTests.kt:15`, `model/PromptFormTests.kt:11`, `session/AnswerNormalizerTests.kt:63`,
  `session/CatalogAnswerGraderTests.kt:27`, `session/FormAgreementGradingTests.kt:20`,
  `trainer/LetterDrillAvailabilityTest.kt:71`) beside `Box.word(...)` in `box/BoxTestSupport.kt:40`. One
  `Box.card(source: Realization, target: Realization, …)` would serve all eight. Likely, S, jvmTest.
- `FsrsPropertyTest.retrievabilityAtDueIsNinetyPercent` (`:39-45`) and
  `FsrsIntervalGranularityTest.rawIntervalEqualsStabilityAtNinetyPercent` (`:29-37`) assert the one identity
  I(0.9, S) = S from both ends. Minor, S.
- Past-justifying comments in tests (same rule as 1.5), e.g. `session/ExtraSessionTests.kt:14` "They used to have a
  composer each", `session/SessionRunTests.kt:140`, `SessionComposerTests.kt:135`, `:200`,
  `listen/ListeningPoolTests.kt:244`, `:351`, `model/DisplayTextTest.kt:22`, `:46`, `trainer/RealFrames.kt:8-9`
  "Frames are content now… instead of a Kotlin table", `box/DayKeyTests.kt:42`, `trainer/CountryDrillRunTest.kt:33`.
  S, none.

Checked and fine: the catalog threshold test (`CatalogFixtureTest:185`) and the real-join coverage tests use a loose
floor, not the edge; `DrillPacingTest`, `SelfGradingTests`, `TimedRunTest`, `DrillRunSummaryTest`, `GrowthStageTests`
test one typical case per outcome and name constants rather than restating them.

---

## 3. Build and platform glue

### 3.1 `scripts/arch-status.py` reports the wrong columns (the `--check` gate still works)
**Evidence.** Running it today prints `Numbers 605 101 —` and `Drill 106 184 — yes`. Both are wrong:
- iOS holds `App/Sources/Screens/NumbersRunView*.swift` (7 files, 558 lines), but `STEM` (`:21`) strips ONE suffix, so
  `NumbersRunView+Run.swift` → `NumbersRun`, which never equals the kern stem `Numbers` from `NumbersRun.kt`. Same for
  `DrillRunView*.swift` (264 lines) → `DrillRun`.
- The `Drill` row pairs `trainer/DrillRun.kt` (the summary/effects/tally file — not a machine) with
  `android/.../DrillFlow.kt` (the generic shell every drill screen stands on) and prints "yes".
- `TimedRun.kt` (constants and `TimedOutcome`) is counted as a kern machine because of its name.
The gate (`scripts/hooks/pre-commit:98`) fails only on a machine with no kern home, so it is not broken; the survey a
session "opens with" (its own words, `:65`) is.
**Fix.** Strip suffixes repeatedly (`View`, then `Run`), pair on the kern file's stem before `Run`, and exclude
`DrillRun.kt`/`TimedRun.kt` by requiring a `reduce(` in the file.
**Size** S. **Gate** none (python). **Confidence** verified.

### 3.2 `kern/docs/build.md` names a gate nobody runs
**Evidence.** `kern/docs/build.md:35` "Gate: `./gradlew :kern:compileAndroidMain`" — not in `CLAUDE.md`,
`scripts/hooks/pre-commit`, `scripts/release.sh:88`, `.github/workflows/release.yml:51` or any script; every one of
them runs `:android:testDebugUnitTest`, which compiles `androidMain` on the way. `:44` "Gate: `./gradlew
:kern:jsBrowserDistribution`" is run only by `scripts/build-web.sh:6` (fine, but it is a build step, not a gate).
**Fix.** State the Android gate as `:android:testDebugUnitTest` and the web one as `scripts/build-web.sh`.
**Size** S. **Gate** none. **Confidence** verified.

### 3.3 The gates point at real things
- `kern/build.gradle.kts` `inputs.files` roots (`App/Sources`, `android/src/main`, `Shared/Sources`, `Watch/Sources`,
  `Widgets/Sources`, `WatchWidgets/Sources`, `catalog`, `web/site.css`, `marketing/print/print.css`) all exist.
- `PaletteParityTest` reads `Watch/Sources/WatchTheme.swift`, `Widgets/Sources/WordWidgetView.swift`,
  `WatchWidgets/Sources/WatchWordWidgetView.swift`, `web/site.css`, `marketing/print/print.css`, both `colors.xml`;
  `repoText` fails loudly on a missing file. Its 27-token shape list is deliberate (KDoc `:35-37`).
- `LayerBoundaryTest` derives its decision list from `enum class` declarations and guards vacuity (`:43-50`);
  `ChromeTableShapeTest` reads `android/.../Chrome.kt` and `scripts/chrome.py` exists. Nothing they guard is gone.
- `settings.gradle.kts`, root `build.gradle.kts`, `gradle/libs.versions.toml`: every declared library is used
  (`kotlin-test-junit` by `android/build.gradle.kts:165`); the Kotlin/SKIE pair comment is current; `compileSdk 36`
  is **already in backlog**. `gradle.properties` names a Homebrew JDK path with a `// why` that covers Linux.
- `kern/docs` backticked identifiers all resolve to a declaration (3 false positives: an Xcode setting, a file name,
  an `APP_GROUP_ID` from `project.yml`); every `docs/*.md` path cited from kern sources exists.

---

## Digest

1. Six drill reducers carry the same ~90-line booking block; the four scramble-family copies (Word, Sentence, Opposites, Letter) are new beyond the backlog's Country/Date pair, with identical `*Close` records and `WINS_TO_ADVANCE = 3` declared six times — an `internal` helper fixes it without touching the ObjC header (M, jvmTest).
2. The v1 store reader (`LegacyStore`/`LegacyDocument`/`StoreCodec.load`, ~270 lines + 134 test lines + a golden) contradicts the pre-production invariant and should go with `VerbSlugRekey` (M, Mac if `LoadedBox` is removed).
3. `BoxStatistics.longestStreak` (a per-call walk over every day since the first answer) and `suspendedCount`, `BoxBrowser.queueableCount/unqueueableCount`, and `WebTrainer.reference` are computed/exported and read by nothing but tests (S, Mac/JS gate).
4. `TurnEffect` and `DrillEffect` repeat `ArmAdvance`/`CancelAdvance`/`Tone`/`ReleaseFocus`, so both apps dispatch the cue contract twice (M, Mac).
5. Fifteen-plus KDocs justify against the past ("used to re-derive", "v1", stale "three/four/seven copies" counts) beyond the backlog's `Presentation.kt` entry (S, none).
6. `Drill.chipRows`, `ReferenceColumns.PAIRED_MIN_WIDTH` and `DrillUnlockMark`'s timings are layout data living in `trainer/` instead of the `design/` home the README names (S, Mac).
7. Tests pin tweakable constants or bytes: `FsrsBehavioralTest.theProductShipsAnAlternatingTenMinuteToOneMonthLadder` and `StoreGoldenTest`'s two byte-pins (S, jvmTest).
8. Two generated vectors have no regenerator — a 1440-line Ukrainian clock TSV and a v1-era de/sw `trainer-golden.json` that overlaps the per-language rule tests (M, jvmTest).
9. The same shared rule is tested once per drill (ladder-out, pending-books-on-close, resample-once ×5), `StoredBoxes.restoring` twice, a 360-pt layout outcome on real bands, and four repo-root walkers in jvmTest (S each, jvmTest).
10. `scripts/arch-status.py` prints a wrong survey (iOS "—" for Numbers despite 558 lines of `NumbersRunView`, a false "yes" for `DrillRun.kt`↔`DrillFlow.kt`) though its `--check` gate still holds; `kern/docs/build.md` names a `:kern:compileAndroidMain` gate nothing runs (S, none). The Gradle inputs, palette/layer/chrome gates and version catalog all point at things that exist.
