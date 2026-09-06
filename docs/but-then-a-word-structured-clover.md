# Split the "has landed" gate from the "Grown" badge bar

## Context

The box badge and the area progress bar both currently key off ONE 6-day
stability bar (`BoxConfig.consolidatedStability`).
That bar does two unrelated jobs at once: it gates real product behavior
(phrase unlock, drill-pool eligibility, presentation-prompt withdrawal,
emoji-cue support), and it decides the sealed "Grown" badge/jade color/progress-bar
segment/day tallies a learner actually sees.
The user expected the visible "Grown" mark to track a much later bar
(around 30 days, kern's existing `MATURED_STABILITY`) and was surprised a word
gets sealed after only 6.
Across several rounds of clarification the fix grew from a pure rename into a
small, coherent badge/progress-bar redesign — this plan covers all of it.

Decisions locked in across the conversation (do not re-litigate these):
- Gate behavior (phrase unlock, drills, prompt, emoji) stays at 6 days, unchanged — only renamed.
- `GrowthStage.Matured`/`MATURED_STABILITY` keep their names.
- The badge becomes a 4-way split: Fresh (amber) / Growing (green) / Grown (jade) / Shaky (amber, Relearning).
- The progress bar's colors follow the badge's colors exactly, plus a clay segment for queued-only words.
- An area that is fully packed AND fully consolidated swaps its green "All packed" checkmark for a jade one and hides its counts; short of that (fully packed only, or partway consolidated) today's behavior is unchanged.

---

## Part A — kern: split the gate from the display bar

| Symbol | Change |
|---|---|
| `GrowthStage.kt:50` `GrowthStage.Consolidated` | rename → `GrowthStage.Growing` (same 6–30d range) |
| `GrowthStage.kt:53` `GrowthStage.Matured` | unchanged |
| `Config.kt:50` `BoxConfig.consolidatedStability` (6.0) | rename → `BoxConfig.growingStability`, same value, same gate role |
| `Statistics.kt:234-236` `Statistics.isConsolidated` | **kept name, redefined**: `phase == Review && stability >= MATURED_STABILITY` (was `>= consolidatedStability`) — now the display-bucket predicate |
| new, beside it | `Statistics.isGrowing` — `phase == Review && stability >= state.config.growingStability` (the old `isConsolidated` math, new name) |
| `BoxEngine.kt:393-394` `BoxEngine.isConsolidated` | kept, redefined (facade over the above) |
| new | `BoxEngine.isGrowing` — facade over `Statistics.isGrowing` |
| `BoxEngine.kt:412-417` `BoxEngine.consolidatedCardIds` | rename → `BoxEngine.growingCardIds`, repoint body to `isGrowing` (drill pool — gate (a), unchanged threshold) |
| `Growth.kt:24-27` `isComponentStable` | repoint call → `Statistics.isGrowing` (phrase unlock is gate (a)) |
| `Inventory.kt:68` `DueKey` | repoint call → `Statistics.isGrowing`, rename field `consolidated`→`growing` (due-order tie-break is gate (a)) |
| `Answer.kt:44,48-49,77`, `TodayReport.kt:166`, `SessionRun.kt:197,204`, `WatchSnapshotBuilder.kt:92`, `WidgetSnapshotBuilder.kt:105,132,169` | **no code change** — these call `Statistics.isConsolidated`/`BoxEngine.isConsolidated` for display (day tallies, "still fresh", widget/watch counts) and inherit the new 30-day meaning automatically |
| `Turn.kt:107-108` `TurnState.consolidated` | rename → `TurnState.growing` |
| `TurnMachine.kt:44,52` `begin(consolidated:)` | rename param → `begin(growing:)` |
| `TurnWriteOut.kt:30` | update to `!state.growing` |
| `Presentation.kt:69-78` `producePrompt(..., consolidated, ...)` | rename param → `growing` |
| `Presentation.kt:138-146` `emojiCue(role, consolidated)` | rename param → `growing` |
| `LetterDrillAvailability.kt:49,53,67,73,87-89,101,115` | rename `consolidatedCards`/local `consolidated` → `growingCards`/`growing` |
| `LetterDrill.kt:35,38,67,68,75` | rename `CONSOLIDATED_PER_LEVEL`/`CONSOLIDATED_FOR_SHORT_STAGES`/params → `GROWING_*`/`growingCards` |
| `Listening.kt:6,225,231` | **no change** — reuses `MATURED_STABILITY` untouched |
| `BoxDocument.kt:81,150,249` `ConfigDto.consolidatedStability` | rename → `growingStability` (wire shape change — see below) |
| `BoxDocument.kt:36,115,129,170,207` `consolidatedCrossed`/`DayStatsDto.consolidated` | **unchanged** — no live number this silently changes meaning for (see wire-format note) |

**Wire format**: per `CLAUDE.md`'s "pre-production, no live user data" invariant and the
existing precedent in `BoxDocument.kt` (the earlier `settledStability`→`consolidatedStability`
rename dropped the old key via `ignoreUnknownKeys` rather than migrating it), rename
`ConfigDto.consolidatedStability` → `growingStability` the same way — old key silently
dropped on load, default re-applied. This one commit carries Conventional Commits' `!`.

**KDoc rewrites** (current-state-only, no history):
- `Config.kt:30-49` — drop the stats-display/tally claim; this bar now backs only phrase
  unlock, drill pools, and the prompt/emoji-cue rules.
- `GrowthStage.kt:6-14` (`MATURED_STABILITY` doc) — currently says it "gates NOTHING,
  reporting-only." Now false: rewrite to say it backs the Grown badge, the progress-bar
  jade segment, the area-complete mark, and the day tallies.
- `GrowthStage.kt:46,49` — `Fresh`'s doc → "still under `growingStability`"; `Growing`'s doc
  → point at `Statistics.isGrowing`.
- `Statistics.kt:227-233` `isConsolidated`'s KDoc — split into two: `isConsolidated` backs
  the stats display/badge/day tallies; new `isGrowing`'s KDoc backs phrase unlock, drill
  pools, and the presentation support/withdrawal rules.
- `BoxEngine.kt:387-411` — same split for the two facades' KDocs.
- `kern/README.md` §5 (~lines 176-213) — replace the single "ONE has this word landed
  threshold" bullet with two: `growingStability`/`isGrowing` (phrase unlock, drill pools,
  emoji/sound-prompt support) and `MATURED_STABILITY`/`isConsolidated` (progress-UI split,
  badge, day tallies). Retarget the phrase-unlock cross-reference (~line 257).
- `kern/README.md` ~line 298 ("No surface derives...") — update to `Standing.stage:
  GrowthStage` (Part B) instead of `(phase, consolidated)`.
- `kern/README.md` ~line 333 — `consolidatedCardIds` → `growingCardIds`.
- `kern/docs/reports.md:40-47` — ladder list: rename `consolidated` case to `growing`.
- `kern/docs/reports.md:71-81` — rewrite for `Standing(stage)` (Part B); `consolidatedStability`→`growingStability`.
- `kern/docs/presentation.md:15,19,59` — `producePrompt`/`emojiCue` params → `growing`; drop
  the "STRICTER" historical comparison, just state `growingStability`.
- `kern/docs/build.md:107` — `consolidatedCardIds` → `growingCardIds`.
- `docs/design.md:203-208` — reword "consolidated"/"landed bar" language to "cleared the
  growing bar" (still describes the unchanged 6-day gate; "consolidated" is now reserved
  for the 30-day display concept).
- `docs/design.md:388-398` — reword "drawn as nothing until it consolidated" away from
  "consolidated" for the same reason; the bud→leaf/leaf→blossom stability ranges
  themselves are unchanged (see `AreaTrees.swift` note in Part F).

---

## Part B — the badge: Fresh / Growing / Grown / Shaky

**Colors** (final): `Learning`, `Fresh` (<6d Review), `Relearning` → **amber**
(`Palette.amber` — already Learning/Relearning's color today; Fresh moves from
green to join them). `Growing` (6–30d Review, renamed from `Consolidated`) →
**green** (`Palette.success` — this is what Fresh used to have). `Matured`
(≥30d) → **jade** (`Palette.grown`, unchanged color, only its trigger moves
from 6d to 30d). No new Palette color is introduced.

**Labels**: no string-catalog changes. `box.phase.learning` ("Fresh"/"Frisch")
now serves both `Learning` and `Fresh`. `box.phase.settled` ("Growing"/"Wächst")
moves from `Fresh` to `Growing`. `box.phase.consolidated` ("Grown"/"Steht")
keeps its text, only re-triggered at the 30-day bar. `box.phase.relearning`
("Shaky"/"Wackelt") is unchanged.

**Structural change — `CardRowState.Standing` carries the raw stage, not a
collapsed boolean**, since three Review-phase labels can no longer be told
apart from one boolean:

```kotlin
// kern/src/commonMain/kotlin/net/spross/kern/box/BoxBrowser.kt:75
data class Standing(val stage: GrowthStage) : CardRowState()
```
`cardRowState` (`BoxBrowser.kt:226-241`) simplifies:
```kotlin
val stage = stageOf(state, sched)
return if (stage == GrowthStage.Suspended) CardRowState.Sleeping else CardRowState.Standing(stage)
```
(`stageOf` only returns `{Learning, Relearning, Fresh, Growing, Matured, Suspended}` here,
since `sched != null` is already guarded above — note this invariant in `Standing`'s KDoc.)

`CardRowSwatch.kt:12-18` rewrites to:
```kotlin
val CardRowState.Standing.swatch: Swatch
    get() = when (stage) {
        GrowthStage.Matured -> Palette.grown
        GrowthStage.Growing -> Palette.success
        else -> Palette.amber // Learning, Fresh, Relearning
    }
```

**iOS** (`App/Sources/Design/ProgressComponents.swift:248-307` `PhaseBadge`,
`App/Sources/Screens/BoxCardRow.swift:254-271`): replace the `Phase{new,learning,review,relearning}`
+ `consolidated: Bool` pair with one flat enum matching the four labels
(`new, fresh, growing, relearning, grown`); `badgePhase(_ stage: GrowthStage)`
maps `Learning/Fresh → .fresh`, `Growing → .growing`, `Matured → .grown`,
`Relearning → .relearning`. Drop the `consolidated:` argument from the call site.

**Android** (`android/src/main/kotlin/net/spross/app/ui/Components.kt:132-218`
`PhaseBadge`): switch on `standing.stage` directly — `Matured` seals with the
existing glyph, `Growing` uses `chrome.boxPhaseSettled`, else (`Learning`,
`Fresh`) uses `chrome.boxPhaseLearning`, `Relearning` uses
`chrome.boxPhaseRelearning`. No chrome string changes.

**Tests**: `BoxBrowserTest.kt:264-306` (`theConsolidatedFlagFollowsTheBarAndNeverThePhase`,
`theSprossenColorFollowsTheBarAndTheTwoAmberPhasesShareIt`) rewrite to construct
`Standing(stage)` and move their stability fixtures so the split is
Growing-vs-Matured (e.g. 3.0→Fresh/amber, 9.0→Growing/green, 99.0→Matured/jade),
each asserting its own stage and swatch. `GrowthStageTests.kt:44-45,63-64` —
rename the `Consolidated` case references to `Growing`.

---

## Part C — the progress bar: a two-way split (matches `learningCount`), plus queued

**Correction (final): the bar stays a two-way split, not four-way — amber
merges into green.** This matches `BoxStatistics`'s own existing two-way
shape (`consolidatedCount` vs. computed `learningCount = active - consolidatedCount`,
`Statistics.kt:19,37`): the bar's colored buckets are the same binary, plus queued.
The badge (Part B) still reads four ways (Fresh/Growing/Grown/Shaky) — the bar
deliberately does not mirror that fine a grain.

Today's `AreaStatistics`/`BoxStatistics` (`Statistics.kt:40-67,15-38`) track
`consolidated`/`settling`/`learning`/`notIntroduced`, and the bar
(`ProgressComponents.swift:143-156`, `Components.kt:196-218`) colors them
jade/green/amber/gray. The buckets become:

- **jade** = `Matured` count (was: `consolidated`, ≥6d; now ≥30d) — matches `BoxStatistics.consolidatedCount`
- **green** = everything else active: `Learning` + `Fresh` + `Growing` + `Relearning`
  combined (was: `settling` + `learning-settling` as two separate segments;
  now one) — matches `BoxStatistics.learningCount`
- **clay** = **queued-only** count — cards packed but not yet introduced
  (a genuinely new bucket: today's `notIntroduced` conflates queued and
  never-packed; per the user's decision, never-packed cards stay blank/uncounted
  on the bar, only queued ones get the clay segment)
- everything never packed at all stays blank/uncounted, as decided

No amber segment appears on the bar at all — amber stays a badge-only color
(Part B), distinguishing Fresh/Learning/Shaky from Growing at the per-card
level without the bar needing to track that distinction.

This needs a new `AreaStatistics.queued: Int` field, sourced the same way
`BoxBrowser.dequeueableCount`/`shelfCounts` (`BoxBrowser.kt:169-175,195-209`)
already computes it (cards in `state.enqueued` belonging to the area) — reuse
that logic rather than re-deriving it in `Statistics.areaStatistics`
(`Statistics.kt:311-341`). `settling`/`learning`/`notIntroduced` collapse: drop
`settling` as its own field (no caller needs the Fresh-only sub-count once the
bar doesn't draw it separately — confirm no other consumer reads it before
removing), keep `learning` computed as today (`active - consolidated`), add
`queued`. `BoxStatistics.consolidatedCount`/`learningCount` (`Statistics.kt:15-38`)
are unchanged in shape — only `consolidatedCount`'s value moves to the 30-day bar.

**iOS**: `ProgressComponents.swift:143-156` `AreaChip.segments` — three
`(count, color)` pairs (jade/green/clay); `KernBridge.swift`'s `AreaProgress`
bridge gains a `queued` field, drops `settling` if unused elsewhere.
**Android**: `Components.kt:196-218` `AreaProgressBar` — same three-stretch list.

**Tests**: `StatisticsBucketsTests.kt` — `settlingIsTheReviewCardsStillShortOfTheBar`
and friends rewrite or drop for the new two-way-plus-queued shape (a `Growing`-stage
card now counts toward `learningCount`/green same as a `Fresh` one; a queued
card now counts toward its own clay bucket instead of `notIntroduced`).
`BoxStatisticsTests.kt`'s `consolidatedCountsOnlyReviewCardsAtOrAboveTheConsolidatedThreshold`/
`areaBreakdownTotalsConsolidatedAndPhraseLocks` — stability fixtures move to ≥30/<30.

---

## Part D — area-complete indicator: jade checkmark, counts hidden

Today's green "All packed" checkmark (`BoxView.swift:274-279`,
`BoxSections.kt:168-178`, in the `packControl`/`PackControl` slot) triggers
whenever `enqueueableCount == 0 && dequeueableCount == 0` — nothing left to
pack or unpack, regardless of consolidation. Per the final decision, this
same single checkmark (not a second indicator) also needs a **further**
condition: when the area is additionally **100% consolidated** (every one of
its scheduled cards is `Matured`), it turns **jade** instead of green, and the
`AreaChip`'s counts/bar (Part C) are hidden for that area, leaving just the
jade mark in the header.

- `BoxSections.kt:146-179` `PackControl` / `BoxView.swift:255-280` `packControl`
  — add the fully-consolidated check (all-packed AND every scheduled card in
  the area is `Matured`) alongside the existing all-packed check; branch to a
  jade `SEAL`/checkmark instead of the green one when both hold.
- The area header composition (`BoxView.swift:234-245` `header(_:)`,
  `BoxSections.kt:99-123`) needs to conditionally omit `AreaChip`'s progress
  display when this state is reached, showing only the emoji/name/jade mark.
- This condition is naturally expressed off the new `AreaStatistics` fields
  from Part C (`consolidated == active`, i.e. every active/scheduled card in
  the area is `Matured`, combined with the existing pack/unpack emptiness the
  screen already computes) — no new kern predicate needed beyond what Part C
  already introduces.

---

## Part E — pack/unpack action-button colors and the unpack threshold

Two small, lower-stakes items the user raised but didn't fully pin down; the
recommendation below is a reasonable default — flag for confirmation before
or during implementation rather than blocking on another round:

- **Colors**: today "pack" has no explicit ladder color (default icon tint);
  "unpack" is `Theme.colors.success` (green) on both platforms
  (`BoxView.swift:264-272`, `BoxSections.kt:161-167`, `BoxRows.kt:206-218`,
  `BoxCardRow.swift:229-251`). Now that green is a ladder color (Growing),
  **recommend both pack and unpack become clay** (`Theme.colors.accent`) —
  matching the "Sown" pill's existing "not on the ladder yet" language
  (`box.card.queued`'s own comment, `BoxCardRow.swift:229-251`), rather than
  reusing amber (now a ladder color too, Fresh/Shaky) for an unrelated action.
- **Unpack visibility threshold**: per the user, the shelf-level bulk-unpack
  button should show only when `dequeueableCount > 2` (not `> 0`) — for 1–2
  queued words, rely on the existing per-word unpack control instead
  (`BoxRowMenu.swift:55-56`, `CardRowState.Packed.removalOffered`). Recommend:
  for `dequeueableCount` in 1–2 with nothing packable, show nothing in the
  `packControl`/`PackControl` slot (blank) rather than the misleading
  "All packed" checkmark, since the area isn't actually free of queued words.

---

## Part F — confirm-only (inherits new behavior with no code change)

**iOS**: `AppModel+Queries.swift:51-56` rename `isConsolidated`→`isGrowing`
(repoint to `BoxEngine.shared.isGrowing`), prune the dead `consolidatedCards()`
(already flagged in `docs/backlog.md:343` — remove that line too);
`AppModel+Queries.swift:67-72` `emojiCue(for:)`, `AppModel+Audio.swift:58-61`
`producePrompt(for:)`, `SessionView+Turn.swift:98-108` — rename param usage to
`growing`. `KernBridge.swift:118,120` `entryLevel`/`winsToAdvance` — call the
renamed kern functions. `AreaTrees.swift:73` `case .consolidated: leaves += 1`
→ `case .growing:` (mechanical rename only — the bud/leaf/blossom/fruit
stability ranges are unchanged, confirmed by reading the full switch: this file
never touches `Standing`/the badge, it reads raw `GrowthStage` + stability
directly). `Widgets/Sources/WidgetSnapshot.swift`/`WordWidget.swift` — inherit
automatically.

**Android**: `AppModel.kt:113-136,769-825` rename `consolidated`/`isConsolidated`
→ `growing`/`isGrowing`; `TurnFlow.kt:257` follows. `ui/SessionSummary.kt`,
`ui/HomeStanding.kt`, `ChromeEn.kt`/`ChromeDe.kt` — no changes, inherit the new
30-day meaning for the day tallies automatically.

---

## Commit sequence

1. `refactor(kern): rename GrowthStage.Consolidated to Growing` — pure rename, `GrowthStageTests.kt`. Green: `:kern:jvmTest`.
2. `refactor(kern)!: rename consolidatedStability to growingStability` — `Config.kt`, `BoxDocument.kt`'s `ConfigDto`, `CalibrationTest.kt`, `StoreCodecTests.kt`. Green: `:kern:jvmTest`.
3. `refactor(kern): introduce Statistics.isGrowing / BoxEngine.isGrowing` — additive only, both facades identical at this point. Green: `:kern:jvmTest`.
4. `refactor(kern): repoint gate-(a) call sites to isGrowing` — `Growth`, `Inventory`, `growingCardIds`, `LetterDrill*`, `Turn*`, `Presentation`. Still zero behavior change. Green: `:kern:jvmTest`.
5. `feat(kern): move the Grown badge/display bucket to the 30-day bar` — redefine `isConsolidated`; land `Standing(stage: GrowthStage)` + `CardRowSwatch` (Part B). Green: `:kern:jvmTest`.
6. `feat(kern): redo AreaStatistics/BoxStatistics buckets to match the badge, add queued` — Part C. Green: `:kern:jvmTest`.
7. `feat(kern): jade area-complete mark when fully packed and fully consolidated` — Part D's kern-side predicate support, if any is needed beyond existing fields.
8. `refactor(app): apply the badge/bar/area-mark redesign across iOS` — Parts B–F, iOS. Build gate: `xcodebuild ... build` (Mac-only, skip on this cloud session).
9. `refactor(app): apply the badge/bar/area-mark redesign across Android` — Parts B–F, Android. Green: `:android:testDebugUnitTest`.
10. `refactor(app): recolor pack/unpack and gate the unpack threshold` — Part E, both platforms together.
11. `docs(kern): rewrite the split-bar story` — every KDoc/doc rewrite listed above. No code.
12. `docs: changelog` — one `## Unreleased` line (learner-facing, e.g. "A word now stays 'Growing' longer before it's marked fully grown, and the area shelf and progress bar show the difference").

Commits 1–7 are kern-only and independently verifiable; 8–10 are the two
platforms' mechanical sweeps and must land together per `CLAUDE.md`'s
"a user-facing change lands on iOS and Android in the same sweep" rule (this
is shared/parity-bearing UI — check both side by side before closing).

---

## Verification

- `./gradlew :kern:jvmTest` after every kern commit (1–7). No `-Psweeps` needed (no catalog/trainer-forms content touched).
- `./gradlew :android:testDebugUnitTest` after the Android commit (9).
- iOS build (`xcodebuild ... build`) after commit 8 — Mac-only, skip on this Linux/cloud session; instead grep the touched Swift files for compile-obvious label mismatches, since Kotlin/Native bridges argument labels literally.
- The badge/bar/area-mark visual change (Parts B–D) is genuinely user-visible — worth a screenshot pass on both platforms once implemented, per the "shared/parity-bearing UI checked side by side" rule, but this is a visual spot-check, not a new automated gate.
