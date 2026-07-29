# Spross — growing-box vocabulary app

A personal spaced-repetition "growing box" app:
FSRS-6-scheduled vocab that only grows while material sits, phrases unlock from their component words.
Native iOS (SwiftUI) app "Spross" in `app/` with a Kotlin Multiplatform core (SprossKern);
read `app/docs/design.md` (app layer) and `app/kern/README.md` (engine contract) before changing any behavior.
Originally German-focused; any catalog language pair (source = known, target = learning; de/en/sw/uk) works now.
Focus is on breadth of exposure to the language for maximum fluency with minimum effort, not perfect retention for single words.

## Commands

```sh
./gradlew :kern:jvmTest      # core test suite — the fast gate, must be green before every commit
xcodegen generate            # after adding/removing app source files
xcodebuild -project Spross.xcodeproj -scheme Spross \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build   # app build gate
scripts/bootstrap.sh         # fresh clone: JDK check + first framework + xcodegen
scripts/strings.py --fix     # clear the stale flags Xcode writes into the String Catalog
```

`extractionState: "stale"` in `Localizable.xcstrings` is cosmetic —
Xcode's index-based extractor misses keys the compiler finds
(computed `LocalizedStringKey` properties, our own `LocalizedStringKey` parameters,
`Label`/`accessibilityLabel`), and flagged keys still compile into every `.lproj`.
Clear the flags rather than deleting the keys.
`scripts/strings.py --built` diffs the catalog against what the compiler emitted —
the check that catches real drift — after a build with `SWIFT_EMIT_LOC_STRINGS=YES`.

## Commit & release rules

- **Commit incrementally and atomically** —
  one cohesive change per commit, never bundle unrelated changes or defer commits into one late batch.
- **Every commit green**: tests + app build clean at each commit, not just at session end.
- **Conventional Commits** (`feat:`, `fix:`, `enhance:`, `test:`, `docs:`, `build:`) with scopes
- Keep `README.md` / `docs/` in step with behavior changes in the same series.
- **`CHANGELOG.md` is curated, not per-commit**:
  user-observable deltas only, grouped by version.
  New entries always land under the top `## Unreleased` heading.
  At bump time, rename `## Unreleased` to `## <version> — <date>` and open a fresh empty `## Unreleased` above it.
- Other parties may change files or commit while you work, do not mind unless their edits conflict with yours.
  Stage only your changes for commits ideally using pathspecs and check before touching history.

## Code standards

- Max ~300 lines per file; split at natural boundaries. Modularity over bloat.
- Comments only for non-obvious constraints;
  side-effectful effects get a one-line `// why:` (trigger + observable result).
- **Semantic linebreaks** in Markdown/docs: one clause per line.
- Tests: behavior over implementation detail; extract pure logic so it's testable without the framework.
- Engine APIs name the rule, never the rendering: no screen positions in kern types.

## Working with subagents & tools

- Offload open-ended research and large implementations to subagents rather than crowding one session;
  hand each the full spec + the relevant `docs/` pointer (`app/docs/design.md` for app work).
- For "where does X live" questions, prefer a code-graph tool over growing Architecture below.
- Large mechanical refactors go through a codemod, not hand edits — write it, run it, review the diff.

## Architecture

Inner → outer, App depends on the kern (SprossKern framework), never the reverse.
Kern modules under `kern/src/commonMain/kotlin/net/spross/kern/`:

- `model` — domain types; `Card` derives from the catalog join, never persisted.
- `catalog` — catalog v2 parser + (source, target) join, deterministic card ids.
- `fsrs` — FSRS-6 scheduler (golden vectors copied from ts-fsrs v5.4.1 / py-fsrs v6.3.1).
- `box` — growth engine: budgets, health gate, phrase unlock, leeches (`BoxEngine` facade).
- `session` — session composer + drain loop + answer normalizer + multiple-choice options.
- `trainer` — number/clock/phrase drills (de/sw/uk authored).
- `snapshot` / `store` — watch/widget snapshot builders + persisted-document facade.
- `App/Sources/Design` — kern-free SwiftUI component library (poster-derived theme).
- `App/Sources/Store` — file-backed `BoxStore` actor (one JSON document per target language).
- `App/Sources/{Model,Screens}` — observable AppModel + single-screen Heute root (Box pushes from it).
- `Shared/`, `Watch/`, `Widgets/`, `WatchWidgets/` — decode-only Swift snapshot surfaces; only the app target links Kotlin.

## Invariants

> **Pre-production — no live user data yet.** Byte-exact JSON encoding and
> scheduling-history continuity are NOT constraints: a format or language
> migration (e.g. a KMP engine port) needs only *behavioral* (golden-vector)
> parity, and the schema/ids are free to be cleaned up on the way through.
> The id/encoding notes below are current design, not data-preservation vows.

- `phase == new ⟺ memory == null ⟺ due == null` on a card's scheduling.
- **Introduction = first answer**, never at composition — budget accounting relies on this.
- **One FSRS schedule per card**, keyed by card id (ids never contain `|`):
  production and recognition are alternating presentations of the same memory,
  and every answer event is an FSRS review — nothing is UI-only.
- Seed content changes go through verification sweeps before shipping (`docs/sprachposter-learnings.md`).
- The kern takes `nowEpochMillis` + `tzId` as parameters, never reads the clock (keeps it pure/testable).

## Extended docs

- Decisions, rationale, and major turning points live in `docs/`, not inline; this file points.
- **One fact, one home**: each topic owned by exactly one doc; narrative docs (history, status, plans) link into it, never restate it.
- Out-of-scope discoveries go to `docs/backlog.md` (one-liners with pointers); prune on fix.
- **Delete plan docs once shipped** — don't leave a stale third copy.
