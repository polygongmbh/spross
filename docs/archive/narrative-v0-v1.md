# Development log — how DuoLernen got here

Orientation for future sessions.
For rules and invariants read `CLAUDE.md` (root) and `app/docs/design.md` first;
this file is the narrative: what exists, how it was built, what's open.

## One-paragraph summary

DuoLernen is a native iOS(+watchOS) "growing box" vocabulary app
(German ↔ Swahili / Ukrainian), built 2026-07-16/17 largely by orchestrated subagent teams:
DuoKern Swift package (FSRS-5 golden-vector port, deterministic seed importer,
box growth engine, session composer, slot trainers, phrase slot templates — 111 tests)
under a SwiftUI app, an iOS widget, a watch companion, and a Photos-watch-face renderer CLI.
Content: 343 verified cards per pair across 12 areas.
Versions v0.1.0 → v0.6.0 in `app/` (own git repo); the user builds to device via Xcode
(DEVELOPMENT_TEAM is set at project level in `project.yml`).

## Timeline of major turns

1. **Origins**: sprachposter session (July 5–11) produced verified DE–SW/DE–UK poster
   vocabulary → extracted to `data/vocab-de-*.json` (`docs/sprachposter-learnings.md`
   holds the content-QA methodology). A Lovable web prototype (`duolernen/`) contributed
   UX findings (typed-first recall, retry loops, trainers) — inspiration only, superseded.
2. **v0.1.0**: Kern + app built by parallel agents against `app/docs/design.md`
   (the build contract; hardened by an adversarial design review before implementation —
   14 findings, 4 blockers, all folded in). App-layer review found 3 bugs, fixed.
3. **v0.2.0**: typed-first both directions, Extra-Runde (enqueued cards bypass the
   daily budget — user agency), normalizer into Kern.
4. **v0.3.0**: slot trainers (numbers/years/clock de/sw/uk — de/sw golden-vectored by
   executing the prototype's own JS; uk newly written + language-verified) and the
   word-of-the-moment widget (App Group).
5. **v0.4.0**: watchOS companion (snapshot down / answer events up over WatchConnectivity;
   phone reschedules with real event timestamps), facegen CLI (`app/tools/FaceGen`,
   `app/docs/facegen.md`), phrase slot templates ("Der Zug fährt um {slot} Uhr ab.").
6. **v0.5.0**: mixed-direction practice (ONE memory state per card, presentation
   alternates per review — `BoxEngine.presentationDirection`), honest settings
   ("Ich lerne …" + "Beide Richtungen üben"), endless drills with streaks,
   reverse Sätze for German learners, typo tolerance (Damerau-Levenshtein ~10%,
   min word length 5), review-feel batch (instant keyboard, compact card, card flip,
   sounds+haptics, single morphing Aufdecken/Prüfen button, emoji hidden on sitting cards).
7. **v0.6.0**: user's own restructure (single-screen Heute root, Box pushed,
   Fortschritt inlined) + segmented answer-colored progress bar + full content
   integration (basics/amt/arzt/arbeit merged into the seed;
   `BoxEngine.reconcileSeed` lets existing boxes absorb content updates).

## The content pipeline (use it for ALL new sw/uk content)

German master (one agent) → per-language translation agents →
independent adversarial verification agents (explicit anti-Russism / anti-calque /
agreement lenses, review-only, corrections-with-reasons) → apply → merge.
Every single sweep in this project found real errors (tense, false friends,
Russisms, run-on composition, feminine-numeral leaks) — never skip it.
Untranslatable concepts (Bescheid, Feierabend) become PHRASES, not vocab cards.
Literal glosses ("wörtl. …") are the best-loved content device — keep adding them.

## Working agreements observed (beyond CLAUDE.md)

- Subagent teams for parallel module work; the root session owns `Types.swift`,
  `project.yml` coordination, integration, commits, and reads agents' screenshots itself.
- Agents verify their own work on the simulator (screenshots into the scratchpad)
  before reporting; the root session spot-checks at least one screenshot per wave.
- User feedback arrives in batches and is implemented completely
  (see CHANGELOG for the mapping); user edits land directly on main — always
  `git log`/`git status` before editing, they code too (e.g. the v0.6.0 restructure).

## Flutter port (2026-07-18)

`/Users/tj/IT/duolernen/flutter/` (own repo, tag v0.1.0, 13 commits) — a Fable
orchestrator + 9 Opus/Sonnet subagent runs ported the app cross-platform:
pure-Dart `kern/` mirrors DuoKern 1:1 (145 tests vs Swift's 131; FSRS +
trainer golden vectors byte-identical; card IDs pinned identical, so a
Swift-written BoxState document loads in Dart — proven by fixture), plus a
Flutter app (Heute/session/drills/Box/onboarding, 22 screenshots verified).
The SWIFT APP STAYS CANONICAL — engine changes land in app/Kern first, then
mirror to flutter/kern (the big-number extension was mirrored same-day).
2026-07-18 bridge: the native watch app, complication, and WordWidget are ATTACHED to the
Flutter Runner (vendored copies under flutter/app/ios/native/ with PROVENANCE.md; reproducible
target surgery via scripts/add_native_targets.rb; bundle ids under gmbh.polygon.duolernen).
Architecture: Dart owns engine+store (now in the App Group container — SHARED with the native
app on a same-device install; use only one actively); native NativeBridge.swift is transport-only
(container path, WidgetCenter reload, WCSession snapshot push / answer-event drain). Dart
WatchSnapshot encodes BYTE-IDENTICALLY to the Swift encoder (tools/snapshot_parity, golden fixture).
Known Flutter gaps: Android configured but unlaunched,
drill counter cosmetics, and any Swift feature landed after 2026-07-18
(e.g. the place-value hint in the number drill) until explicitly mirrored.

## Content catalog (2026-07-18)

`data/catalog/` + `tools/catalog.py` + `docs/content-format.md`: language-
agnostic factored content (shared word concepts + per-language realizations;
phrases stay pair-authored BY DESIGN — the German side is pair-tuned) with
review/provenance metadata for future crowdsourcing (draft→machine→reviewed→
native). Round-trip to the legacy seeds is guaranteed (`catalog.py check`);
legacy seeds remain the app-facing format until importers migrate.

## Importer migration — catalog canonical (2026-07-18)

Two Opus agents migrated BOTH importers to read `data/catalog/` directly,
each behind a frozen-fixture parity gate (343 cards/pair field-for-field,
pinned ids incl. the Fleisch-grillen pair override). The catalog is referenced
by COMMITTED SYMLINKS (app/catalog, flutter/app/assets/catalog) — one editable
master, zero sync; both toolchains verified to materialize real files into the
built bundles. tools/catalog.py authority flipped (`build` now refuses without
--force; `sync` checks symlink health). Repos are no longer standalone-cloneable
(deliberate, local-first). App renamed to "Spross" (user); user also added a
watch multiple-choice practice mode and split DuoKern/DuoKernTrainer.

## KMP rewrite — SprossKern engine + Spross iOS wiring (2026-07-19 → 22)

Branch `content-catalog-v2` (app repo) carried two coupled moves, landed together:
**catalog v2** (factored concepts + per-language realizations, now IN-REPO at
`app/catalog/` with its format spec — the `data/` symlink era is over) and a
**full engine rewrite in Kotlin Multiplatform**: `app/kmp/kern` = SprossKern
(`net.spross.kern`), jvm + iOS targets, 251 jvmTest green as the new fast gate
(`./gradlew :kern:jvmTest`). FSRS-6 replaces the FSRS-5 port (21 weights,
decay learnable; golden vectors COPIED from ts-fsrs v5.4.1 / py-fsrs v6.3.1
with provenance). Engine contract: `app/kern/README.md` — it superseded
and replaced `kmp-rewrite-brief.md` (plan doc deleted per convention).
Swift DuoKern/DuoKernTrainer are gone.

Presentation-model rulings (user, 2026-07-22 — recorded in kern-design.md):

1. **One schedule per card** — production/recognition are alternating
   presentations of one memory (v1 `mixedDirections` as the ONLY mode);
   the per-role/unit scheduling design was dropped.
2. **Recognition is reveal + self-grade, never typed** — and phrases alternate
   too (only TYPED phrase recognition was absurd).
3. **No in-session lapse retry** — relearning = reference `[10m]`; a lapsed
   card returns next session (breadth over depth).
4. **`variantOf` dropped** — the 4 near-duplicate phrase twins were unified
   onto their base slugs instead; synonyms/variants became a
   display-vs-accept distinction.

Plus: first exposure is always recognition WITH emoji (teaching moment),
second review always production; switching source language keeps every schedule.

iOS wiring: app renamed **Spross** (`net.spross.app` ids, `Spross.xcodeproj`,
scheme Spross); the app target links the SprossKern framework via a pre-build
Gradle phase (`scripts/build-kern.sh`; `scripts/bootstrap.sh` for fresh clones);
app layer moved to source/target profiles (onboarding pickers with concept
counts, `box-<target>.json` store, concept-denominated counts); watch + widgets
are decode-only Swift over phone-built snapshots (WatchSnapshot v2,
WidgetSnapshot — no Kotlin on watch, no arm64_32 problem).
FLAG: the 4 adapted uk twin realizations still need their native sweep
(see open threads below).

Open after this arc: **Android stage next** (engine jvm-ready, AGP wiring
pending); **en trainer content unauthored** (hub hides it); **sw/uk UI chrome
missing** (those sources read English until authored).

## Android stage (2026-07-24/25)

`:android` in the app repo — a Jetpack Compose app on the same SprossKern engine.
Kern grew an `androidLibrary` KMP target
(AGP 9.3.0 built-in Kotlin, `com.android.kotlin.multiplatform.library`,
compileSdk 36 / minSdk 26; androidMain NFC actual mirrors jvmMain);
plugins moved to a root `build.gradle.kts` (`apply false`)
so sibling modules share one KGP classloader.
The catalog is bundled by a per-variant Gradle sync task from `catalog/`
(single source holds); store = `box-<target>.json` in the app files dir,
written atomically after every answer.
Core loop shipped per `design.md` (§ Android companion):
onboarding pickers with concept counts, Heute, alternating sessions
(typed produce via the kern normalizer — Exact→Good / Typo→Hard / Wrong→Again;
recognize = reveal + self-grade), extra round, endless, summary; chrome de/en.
Gates all green locally: `:android:assembleDebug`,
`:android:testDebugUnitTest` (14), `:kern:compileAndroidMain`, jvmTest 251;
`RUNBOOK-linux.md` documents the Mac-free build.
Not ported: Box browse, trainers, widget;
the app is build-gated only — never launched on an emulator or device yet.

## Improvements round (2026-07-25)

User-driven polish on top of the rewrite, in per-feature commits:
languages.json grew `flag` + `englishName` (kern `LanguageInfo`, linted);
onboarding chrome went English with "🇩🇪 German" picker rows,
and pickers swap the pair instead of excluding the other side.
German clock accepts 24-hour readings; phrase-slot drills sample level-aware
(same ramp tables as plain drills); drill answers grade through the kern
normalizer (typo budget, never bridges distinct numbers).
Extra round composes endless-FIRST with review-ahead fallback
(`canPracticeExtra` — the user hand-refined the fallback mid-round).
Box areas grouped under areas.json groups.
Concurrently the user rewrote the watch into one response-time-graded
multiple-choice loop (drops "watch never touches FSRS") and committed
kitchen content splits.
Process lesson recorded: one commit per feature — a workflow stage that
bundled four features got split post-hoc (`git commit --only` commits
working-tree content, which silently absorbed a user refinement; amended).

## Release 1.0.0 (2026-07-25)

Tag v1.0.0; main fast-forwarded to the release commit.
The final stretch: three adversarial audits (engine vs contract, app vs design.md,
release readiness) surfaced and fixed real flaws before the fold —
"eins Uhr"→"ein Uhr" apocope, drills accepting wrong articles as clean,
mangled typo corrections, missing iOS first-exposure flip, the promised
feminine base-word-as-typo grading, and a typo budget that could bridge
long number words (now capped, digits exact-only, German impossibility
test-proven; sw/uk near-twins gated → backlog).
Repo restructure: kern module flattened to `app/kern`, engine contract
recast as `app/kern/README.md`, design.md pruned of engine restatements,
`app/docs/backlog.md` created as the out-of-scope-discovery home.
CHANGELOG truth-checked entry-by-entry against the code before folding.
Android stays 0.1.0 (core loop only). The user's homonym/promptAmbiguous
work was in flight (uncommitted) at release and is not part of 1.0.0.

## Current state & open threads

- **Device testing**: user deploys via Xcode (team set).
- **Android**: build-gated only (no emulator/device run yet);
  versioned independently as 0.1.0 until the next repo version bump aligns it.
- **Couple mode** (partner-as-trainer, from the web prototype) — deliberately post-v1.
- Session-discovered, out-of-scope issues now live in `app/docs/backlog.md`
  (watch pairing untested, complication rendering unverified, uk native sweep,
  phrase→component linking gaps, FSRS parameter optimization, …) — pruned there on fix.
- **facegen** is manual (render → AirDrop → album); no automation.
- Trainer "Sätze" templates: Ukrainian dropped «о + Lokativ» time frames
  (couldn't compose grammatically) — revisit only with a case-transform table.

## Gotchas that cost time once

- xcodegen: new files need `xcodegen generate` (use `sh scripts/gen.sh`;
  `--no-watch` variant exists for device deploys without watch signing).
- Extension bundle ids MUST be prefixed by the host app id.
- SwiftPM/simulator concurrent builds by parallel agents: use distinct
  `-derivedDataPath` per agent.
- `git reset --soft` keeps everything staged — unstage before selective recommits.
- The kern never reads the clock (tests depend on injected
  `nowEpochMillis`/`tzId`); kern test fixtures include a frozen mini-catalog —
  the REAL catalog is linted by `CatalogLintTest` on every jvmTest run, so
  catalog edits that break format rules fail the fast gate, not the app.
