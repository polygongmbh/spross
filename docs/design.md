# Spross — app design (v2)

The product thesis and phase plan live in `../../docs/roadmap.md` (repo-external, project-level).
This doc is the build contract for the APP layer: screens, review UX, profile, persistence wiring.
The engine (scheduling, growth, sessions, grading, snapshots) is owned by `../kern/README.md` (the kern contract) —
link, don't restate.
Product frame: any source (known) / target (learning) language pair from the catalog;
no user-facing direction concept; progress tracked per target language.

## North star

Every screen answers "What do I do right now?" with zero ambiguity.
The app composes the work; the user never browses for what to study.

## Architecture

Strict dependency direction (App → SprossKern, never the reverse):

```
kern   SprossKern — Kotlin Multiplatform engine (contract: kern/README.md)
App/       SwiftUI iOS app — views, observable AppModel, file-backed store actor
Shared/ Watch/ Widgets/ WatchWidgets/   decode-only Swift snapshot surfaces
android/   Jetpack Compose app — core loop on the same engine (§ Android below)
```

- ONLY the app target links the Kotlin framework;
  watch/widget targets are pure Swift over phone-built snapshots (see below).
- `App/Sources/KernBridge.swift` is the boundary file:
  `Date ↔ epochMillis`, `tzId`, `Identifiable`/`Equatable` conformances, `Int32` bridging.
- **Time discipline**: the engine never reads the clock —
  every call passes `nowEpochMillis` + `tzId` (kern README §7).
- Persistence: `BoxStore` actor, **one JSON document per TARGET language**
  (`box-<target>.json`) in App Group `group.net.spross.app`,
  encoded/decoded by the kern store facade (schema: kern README §7).
  Atomic writes; save debounced ≥ 5 s after answers,
  immediate at session end, config mutations, and scene-background
  (which also folds partial session stats and pushes snapshots).
- Swift 6 strict concurrency.

## Profile & onboarding

- Profile = (source, target) catalog languages;
  targets come from `Catalog.availableTargets(source)` (≥ 50 joinable concepts);
  both pickers show concept counts ("… terms").
- Onboarding chrome is ENGLISH (it renders before the user's language is known);
  language picker rows everywhere are "⟨flag⟩ ⟨englishName⟩" from languages.json
  ("🇩🇪 German") — one neutral form on both sides.
- Neither picker excludes the other side's language:
  choosing the language the other side holds SWAPS the pair
  (target list offers the current source with the swapped pair's count).
- Default source = device language when covered, else en.
- UI chrome renders in the KNOWN language when chrome exists (de/en today), otherwise en;
  the immersion subtitle (learned word beneath the main button label)
  appears only when chrome exists for the target.
- **Switching source keeps every schedule** (scheduling keys are card ids — kern README §3);
  the settings picker says so.
- Area titles come from the catalog per source language; the emoji map stays app-side.

## Presentation model in the UI

Roles, synonym rotation, and the emoji matrix are engine rules (kern README §3);
the app renders them:

- One FSRS schedule per card; the role of each review comes from the engine
  (`presentationRole`), alternating produce/recognize.
- **PRODUCE**: typed answer in the target language; placeholder "In ⟨target⟩ …";
  grading via the kern answer normalizer
  (articles optional, verb prefixes optional, typo budget, article-mismatch = typo).
  "Aufdecken" remains the no-typing fallback that self-grades.
- **RECOGNIZE**: reveal + self-grade ONLY (Again/Hard/Good/Easy) — no input field.
  The prompt shows the engine's rotated form;
  the reveal shows the source meaning plus the full synonym family ("auch: …").
- First exposure is ALWAYS recognition WITH emoji (teaching moment);
  emoji visibility elsewhere follows the engine matrix.
- ♀ is a labeled badge, never graded.
- Card rendering: article inline in its color and the plural line on the TARGET side only;
  suffix plurals dictionary-style ("Lehrerin, -nen") with localized sentinel strings;
  prompt/answer styling is role-based (prompt neutral, reveal accent), not per-language.

## Review UX rules (carried from v1 refinement, treat as spec)

- Wrong answer reveals **inline**, expanding the card DOWNWARD (animated);
  no space is reserved pre-reveal.
- A typo counts as correct but does NOT auto-advance:
  the typed text stays visible with the proper spelling and a "Weiter" tap.
- A clean correct answer auto-advances after ~1.2 s; Enter advances when revealed.
- "Aufdecken" fills the answer field with the correct answer.
- Answer-colored progress bar: one segment per answer — green right, amber tough, brick wrong.
- Never punishing: no red flashes; streak survives one missed day.

## Counts & sessions

- Every user-facing count (due ring, "x neu", active cards, widget stats) is
  **concept-denominated** — one schedule per card makes cards ≡ concepts (kern README §4).
- Sessions are composed, never configured:
  plan from `BoxEngine`, drain loop, extra round, endless mode — semantics in kern README §6.
  The done-card extra round composes endless-FIRST (due + NEW vocab within budget/gate),
  falling back to review-ahead when endless is empty,
  so it renders in every done state with active cards.
  Session end = summary ("x neu · x gefestigt · x wiederholt") with confetti and streak;
  "Weiter üben" → endless.
- A lapsed review card returns after 10 minutes — typically next session;
  the drain loop does not wait for it (kern README §5).

## App structure (single screen)

- **Heute** is the only root screen:
  session card (due-count ring + streak flame, or done state),
  trainer hub, condensed Fortschritt section (14-day strip, active count, retention).
- **Box** (pushed via the 📦 toolbar icon): browse areas/cards —
  areas grouped under their areas.json groups
  (source-language titles, en fallback, manifest order; empty groups drop out);
  rows lead with the TARGET realization; phase/stability, pack-into-box,
  suspended cards surface for revive; settings
  (source/target pickers with flag + English name — picking the other side swaps —
  learning-pool size, reset, `feedback@spross.net` + version footer).
- **Trainers**: registry-driven from kern — de/sw/uk authored,
  the hub hides languages with no content (en trainer unauthored).
  Slot drills are stateless; phrase templates are keyed (source, target),
  reverse mode when target == de.
  Drill answers grade through the kern normalizer against every accepted variant
  (same typo budget as reviews; accepted-with-typo pauses with the proper spelling;
  articles/verb-prefix leniency stay off).
  All drills ramp: two-wins-up / one-miss-down levels,
  the sentence drill drawing leveled slot values (kern README §9).

Design language: warm, card-centric, emoji as illustration, article color coding
der=blue / die=pink-red / das=green — degrades to neutral for languages without
gendered articles.

## Android companion (core loop)

`android/` renders THIS contract with Compose — same engine facades,
same review UX rules; deltas only where the platform differs:

- Catalog: bundled as APK assets by a per-variant Gradle sync task from `catalog/`
  (single-source rule); read through an `AssetManager`-backed `CatalogSource`.
- Store: `box-<target>.json` in the app-private files dir (no App Group);
  persisted after every answer (atomic temp-then-rename, non-cancellable write)
  instead of iOS's debounce.
- Typed produce grading maps Exact → Good, Typo → Hard, Wrong → Again;
  "Aufdecken" self-grades — identical to the intent above.
- Not ported yet: Box browse, trainers, widget, 14-day strip, confetti/haptics;
  settings = language switch only.

## Watch & widgets (decode-only)

- The phone precomputes **WatchSnapshot v2**
  (both sides pre-resolved per card, `nextRole` + `promptForm` baked in,
  due-first ranking, ≤ 60 entries) and **WidgetSnapshot** on every persist;
  wire formats in kern README §7.
- Watch: one graded **multiple-choice** loop (the watch never types) — role-aware
  per card (recognize → tap the source meaning; produce → tap the target word),
  distractors ranked by shape (length + part-count) so option length can't give
  the answer away. Drains due cards, then review-ahead by soonest-due.
  No self-grading: correctness + **response time** derive the FSRS rating —
  wrong → Again, correct → Easy (very fast) / Good (fast) / Hard (slow), with a
  length-scaled "fast" budget (`WatchGrading`). Recognition on a keyboard-less
  device is a deliberate concession to the phone's recall-first rule; the latency
  curve compensates, and Easy stays reachable on purpose (breadth of exposure
  over perfect single-word retention). Answers return as events; the phone
  reschedules with real timestamps and re-pushes.
- iOS widget: pure Swift decode; the retrievability power curve is duplicated
  Swift-side with the w20 constant (documented duplication).

## Content pipeline

- `catalog/` is the in-repo master (v2 format, spec in `catalog/README.md`):
  shared word concepts + per-language realizations + pair-authored phrases;
  bundled as a folder resource.
  Cards derive at load from the (source, target) join and are never persisted (kern README §2).
- `CatalogLintTest` guards format rules on every kern test run.
- Content changes go through verification sweeps before shipping
  (`../../docs/sprachposter-learnings.md`).
  Current size: 356 concepts; joins de-sw 346, de-uk 350, de-en 351.

## Testing & gates

- `./gradlew :kern:jvmTest` — must be green before every commit.
- App builds via XcodeGen (`project.yml`) +
  `xcodebuild -scheme Spross -destination 'iPhone 17 Pro' build`;
  a Release archive smoke check guards the framework linkage.
- Behavioral engine tests live in kern (inventory: kern README §10).

## Not yet

Couple mode, accounts/sync, Android beyond the core loop
(Box browse / trainers / widget — see § Android companion),
sw/uk UI chrome (sources fall back to en), en trainer content.
