# Spross — growing-box vocabulary app

A personal spaced-repetition "growing box" app:
FSRS-6-scheduled vocab that only grows while material sits, phrases unlock from their component words.
Native iOS (SwiftUI) app "Spross" in `app/` with a Kotlin Multiplatform core (SprossKern);
scheduling, growth, session and grading rules are `app/kern/README.md` and the KDoc on the type it names,
screen, copy and layout rules `app/docs/design.md` — read the side you are changing.
Originally German-focused; any catalog language pair (source = known, target = learning; de/en/eo/es/fr/it/sw/uk) works now.
Focus is on breadth of exposure to the language for maximum fluency with minimum effort, not perfect retention for single words.

## Commands

```sh
./gradlew :kern:jvmTest      # core test suite — the fast gate, must be green before every commit
./gradlew :kern:jvmTest -Psweeps       # adds the corpus sweeps — after a catalog or trainer-forms edit
./gradlew :android:testDebugUnitTest   # the Android app layer — after an android/ edit
xcodebuild -project Spross.xcodeproj -scheme Spross \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build   # app build gate
scripts/run-sim.sh           # build + install + launch on the simulator (--shot, --clean, -- <launch args>)
scripts/run-emu.sh           # same for Android: boots the AVD, builds, installs, launches (--shot, --clean)
scripts/bootstrap.sh         # fresh clone: JDK check + first framework + xcodegen
scripts/strings.py --fix     # run after ANY String Catalog edit — Xcode's formatting, then the Android tables
scripts/catalog-format.py --fix   # run after ANY catalog/ edit — one line per entry that fits (--check to verify)
```

Xcode/`xcodegen`/simctl lines above are Mac-only — never present, never installable, on Linux/cloud sessions.
There, `./gradlew :kern:jvmTest` is the gate; see `RUNBOOK-android.md` for the rest.
The emulator needs a GPU and virtualization, so it is local-only too — cloud sessions have no `/dev/kvm`.

## Commit & release rules

- **Commit incrementally and atomically** —
  one cohesive change per commit, never bundle unrelated changes or defer commits into one late batch.
- **Commit as you go, unasked**: unrelated uncommitted work in the tree is never a reason to hold yours back.
- **Every commit green**: tests + app build clean at each commit, not just at session end.
  Green means YOUR commit's content — with other work in flight, scope the gate to what you touched.
  Also stay with tests appropriate to your change - a docs or copy change needs no full rebuild, a minor algorithm adjustment no emulator run.
  Read another party's red as theirs, not as a blocker, only take time to test your changes in isolation if they have major chance of breakage.
- **On red, attribute before escalating**: `git status`/`diff` the failing file first — if it's not
  one you touched, that's someone else's break. Don't rerun the same broad gate or reach for a
  bigger one hoping for a different answer; narrow instead (targeted tests, `compileKotlinJvm` over
  `jvmTest` when kern main is untouched) and fall back to reading your own diff when no gate isolates it.
  A red in a file nobody edited is the shared Kotlin cache; `../CLAUDE.md` carries that remedy.
- **Conventional Commits** (`feat:`, `fix:`, `enhance:`, `test:`, `docs:`, `build:`) with scopes
- Keep `README.md` / `docs/` in step with behavior changes in the same series.
- `CHANGELOG.md` is curated, grouped by version, written in ENGLISH; what earns an entry
  and how it is worded: `docs/distribution.md`.
  New entries always land under the top `## Unreleased` heading. At bump time, rename `## Unreleased` to `## <version> — <date>` and open a fresh empty `## Unreleased` above it.
  Its head carries every heading you need — read that, never the whole file.
- Other parties may change files or commit while you work, do not mind unless their edits conflict with yours.
  Stage only your changes for commits ideally using pathspecs and check before touching history.
  A file you share with in-flight work gets only your hunks staged, never theirs carried along.

## Code standards

- Max ~300 lines per file; split at natural boundaries. Modularity over bloat.
- Comments only for non-obvious constraints;
  side-effectful effects get a one-line `// why:` (trigger + observable result).
- English is American spelling and vocabulary everywhere — docs, comments, chrome copy, catalog
  content (a British spelling is a `variant`, a British word a `synonym`; `catalog/README.md`).
- ALWAYS use **Semantic linebreaks** for text - in docs, markdown files, documentation comments: one sentence/clause per line.
- Tests: behavior over implementation detail; extract pure logic so it's testable without the framework.
- A rule that can be checked gets the check too (`scripts/hooks/pre-commit`), never the sentence alone.
- Engine APIs name the rule, never the rendering: no screen positions in kern types.

## Working with subagents & tools

- Offload open-ended research and large implementations to subagents rather than crowding one session;
  hand each the full spec + the relevant `docs/` pointer.
- Search with `rg`. Bare `grep` is ugrep here, and given a subdirectory it drops the repo's
  `.gitignore` and walks `kern/build/` — 11 MB where `rg` answers in 448 bytes.
- For "where does X live" questions, read the module docs — never grow this file.
- Large mechanical refactors go through a codemod, not hand edits — write it, run it, review the diff.
  `ast-grep -l kotlin|swift -p '<pattern>'` matches the tree rather than the line.

## Invariants

> **Pre-production — no live user data.** Byte-exact encoding and scheduling-history
> continuity are not constraints; migrations need only behavioral (golden-vector) parity.
> The notes below are current design, not data-preservation vows.

- Inner → outer: App depends on the kern (SprossKern framework), never the reverse;
  only the app target links Kotlin (`kern/docs/build.md`).
- A behavioral rule lives in kern once — a platform may READ a kern decision, never MINT one
  (`LayerBoundaryTest`, waivable per line with `// layer-ok: <reason>`); platforms own layout,
  animation, focus, haptics, audio, timelines, a11y and string tables, and nothing else.
- `phase == new ⟺ memory == null ⟺ due == null` on a card's scheduling.
- **Introduction = first answer**, never at composition — budget accounting relies on this.
- **One FSRS schedule per card**, keyed by card id (ids never contain `|`) —
  production and recognition are presentations of it; every answer is a review, nothing UI-only.
- Seed content changes go through verification sweeps before shipping (`docs/sprachposter-learnings.md`).
- The kern takes `nowEpochMillis` + `tzId` as parameters, never reads the clock (keeps it pure/testable).

## Extended docs

- Decisions, rationale, and major turning points live in `docs/`, not inline; this file points.
- Which of the three homes a rule belongs in — this file, a doc, or a gate — is `docs/rules.md`.
- **One fact, one home**: each topic owned by exactly one doc; narrative docs (history, status, plans) link into it, never restate it.
- Docs carry foundations; what the running app or the code answers faster stays out, and a needed cross-link means it is filed wrong.
- Negations and hardlines only where the opposite is what would otherwise happen.
- A doc states its content, never its own properties.
- Out-of-scope discoveries go to `docs/backlog.md` (one-liners with pointers); prune on fix.
- Whose the bundled recordings are and what their licenses oblige — the ship/legal record —
  is `docs/audio-licensing.md`; no other doc restates a license term.
- Write plans into docs/ and delete them once shipped, even if you did not write the plan
- Whenever you are corrected or do extensive research, find or create an appropriate docs/ file to note insights
- Do not document a removal or absence of something beyond the commits message unless it is likely to be accidentally reintroduced
