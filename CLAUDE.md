# Spross — growing-box vocabulary app

A personal spaced-repetition "growing box" app:
FSRS-6-scheduled vocab that only grows while material sits, phrases unlock from their component words.
Native iOS (SwiftUI) app "Spross" in `app/` with a Kotlin Multiplatform core (SprossKern);
scheduling, growth, session and grading rules are `kern/README.md` and the KDoc on the type it names,
screen, copy and layout rules `docs/design.md` — read the side you are changing.
Originally German-focused; any catalog language pair (source = known, target = learning; de/en/eo/es/fr/it/sw/uk) works now.
Focus is on breadth of exposure to the language for maximum fluency with minimum effort, not perfect retention for single words.

## Commands

```sh
./gradlew :kern:jvmTest      # core test suite — the fast gate, must be green before every commit
./gradlew :kern:jvmTest -Psweeps       # adds the clock day-part sweep — after a trainer-forms edit
./gradlew :android:testDebugUnitTest   # the Android app layer — after an android/ edit
xcodebuild -project Spross.xcodeproj -scheme Spross \
  -destination 'platform=iOS Simulator,name=iPhone 17' build   # app build gate
scripts/run-sim.sh           # build + install + launch on the simulator (--shot, --clean, -- <launch args>)
scripts/run-emu.sh           # same for Android: boots the AVD, builds, installs, launches (--shot, --clean)
scripts/bootstrap.sh         # fresh clone: JDK check + first framework + xcodegen + git hooks
scripts/strings.py --fix     # run after ANY String Catalog edit — Xcode's formatting, then the Android tables
scripts/catalog-format.py --fix   # run after ANY catalog/ edit — one line per entry that fits (--check to verify)
scripts/audio-coverage.py --check # after ANY catalog/audio/ edit — asks git what ships; the pre-commit hook runs it on staged audio
scripts/release.sh <version>      # cut a release: changelog heading, version, gates, tag, push (docs/distribution.md)
```

Xcode/`xcodegen`/simctl lines above are Mac-only — never present, never installable, on Linux/cloud sessions.
There, `./gradlew :kern:jvmTest` is the gate; see `RUNBOOK-android.md` for the rest.
The emulator needs a GPU and virtualization, so it is local-only too — cloud sessions have no `/dev/kvm`.

## Commit & release rules

- **Commit incrementally and atomically** —
  one cohesive change per commit, never bundle unrelated changes or defer commits into one late batch.
- **Commit as you work, unasked** — this overrides any tool-level "never commit unless asked" default:
  commit your own changes without waiting for an explicit instruction, ignore unrelated uncommitted work in the tree.
- **Every commit green**: run the narrowest gate that covers the diff.
  No Kotlin/Swift changed → no build. No behavior changed → no test. No UI changed → no screenshot.
  Red in a file you didn't touch → someone else's break; commit `--only` your paths, move on.
  Red in a file nobody touched → stale Kotlin cache; `rm -rf kern/build/kotlin`, rebuild.
  Never stash, diagnose, or broaden the gate on someone else's failure.
  Dirty tree and unsure whether the red is yours → worktree, but only when the change could realistically break.
- **Conventional Commits** (`feat:`, `fix:`, `enhance:`, `refactor:`, `test:`, `docs:`, `build:`, `chore:`) with scopes
- `feat` adds what was not there, `enhance` sharpens what was, `fix` corrects what was wrong;
  a removal is never a `feat`, whatever it makes room for.
- `!` marks a backwards incompatible change to the SHAPE of stored state, never content changes
- A user-facing change lands on iOS and Android in the same sweep; a change to shared/parity-bearing UI checks both
- Keep `README.md` / `docs/` in step with behavior changes in the same series.
- `CHANGELOG.md` is curated, grouped by version, written in ENGLISH; 
  what earns an entry and how it is worded: `docs/distribution.md`.
  New entries always land under the top `## Unreleased` heading.
  At bump time, rename `## Unreleased` to `## <version> — <date>` and open a fresh empty `## Unreleased` above it.
  Its head carries every heading you need, prefer to only read that.
- Changelog and backlog entries should be just one line with one sentence for bullet points, 
  explanations should live in commit messages and only in the file on absolute exceptions.
- Other parties may change files or commit while you work, do not mind unless their edits conflict with yours.
  Commit with `git commit --only -- <paths>`: `git add` + `git commit` carries every other staged
  path, `--only` skips untracked ones (`git add` those first), and check before touching history.
  A file you share with in-flight work gets only your hunks staged, never theirs carried along.

## Code standards

- Max ~300 lines per file; split at natural boundaries. Modularity over bloat.
- Comments only for non-obvious constraints;
  side-effectful effects get a one-line `// why:` (trigger + observable result).
- Engine APIs name the rule, never the rendering: no screen positions in kern types.
- Simplicity over Perfection: Behavior correctness is important, but don't overcomplicate the code to handle every edge case.

### Text
- ALWAYS use **Semantic linebreaks** for text - in docs, markdown files, documentation comments: one sentence/clause per line.
- English is American spelling and vocabulary everywhere — docs, comments, chrome copy, catalog
  content (a British spelling is a `variant`, a British word a `synonym`; `catalog/areas/README.md`).
- When working on localization, focus on idiomatic variants in each language rather than literal translation.
  For catalog translations, a literal match is important.

### Tests
- Test rules and behavior, not implementation details or tweakable constants
- Extract pure logic so it's testable without the framework
- When one code change needs multiple test changes, assess the sensibility of the tests - do not overtest

## Working with subagents & tools

- Offload open-ended research and large implementations to subagents rather than crowding one session;
  hand each the full spec + the relevant `docs/` pointer.
- Fewer, larger agents: batch 2–3 work packages per agent, share context via a short digest.
- One writer per source tree per wave. `kern/build/kotlin` is shared mutable state;
  overlapping Gradle runs corrupt it (`Unresolved reference` in files nobody touched).
  Fix: `rm -rf kern/build/kotlin`, rebuild — never a source edit.
  `xcodebuild`'s pre-build phase also reads/writes the kern framework, so it is a cache writer too.
- Quiet gates: `gradle --console=plain -q`, `xcodebuild -quiet`, pipe long logs to a file.
- `catalog/audio/**` is excluded from `:kern:jvmTest`'s input set (`kern/build.gradle.kts`):
  an audio edit reports UP-TO-DATE and needs `--rerun-tasks`.
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
  animation, focus, haptics, audio, timelines, a11y and string tables, and nothing else —
  each of those owned once per platform; sameness is the CONTROLS, never the content they
  hold, and a second cut is licensed by a parameter attempted, never by an argument.
- `phase == new ⟺ memory == null ⟺ due == null` on a card's scheduling.
- **Introduction = first answer**, never at composition — budget accounting relies on this.
- **One FSRS schedule per card**, keyed by card id (ids never contain `|`) —
  production and recognition are presentations of it; every answer is a review, nothing UI-only.
- Seed content changes go through verification sweeps before shipping (method: `sprachposter-learnings.md` in the parent repo's `docs/`).
- The kern takes `nowEpochMillis` + `tzId` as parameters, never reads the clock (keeps it pure/testable).

## Extended docs

- Decisions, rationale, and major turning points live in `docs/`, not inline; this file points.
- Which of the three homes a rule belongs in — this file, a doc, or a gate — is `docs/rules.md`.
- **One fact, one home**: each topic owned by exactly one doc; narrative docs (history, status, plans) link into it, never restate it.
- Docs carry foundations; what the running app or the code answers faster stays out, and a needed cross-link means it is filed wrong.
- Negations and hardlines only where the opposite is what would otherwise happen.
- A doc states its content, never its own properties.
- Out-of-scope discoveries go to `docs/backlog.md`, catalog content to `catalog/backlog.md` (one-liners with pointers); prune on fix.
- Whose the bundled recordings are and what their licenses oblige — the ship/legal record —
  is `docs/audio-licensing.md`; no other doc restates a license term.
- Write plans into docs/ and delete them once shipped, even if you did not write the plan
- Whenever you are corrected or do extensive research, tighten or replace the line that should
  have caught it before adding a new one; a new docs/ file only when no existing doc owns the topic
- Do not document a removal or absence of something beyond the commits message unless it is likely to be accidentally reintroduced
- In Code comments, always only explain the current state of things, do not justify or illustrate behavior against the past
- Behavior rulings documented are snapshots, not absolutes - when challenged, most of them can change
