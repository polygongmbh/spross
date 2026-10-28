# Spross — curated vocabulary app
The rules every session pays for on every edit (commands, commit rules, code and text standards), one terse line each; a topic's own rules live in the `docs/` page that owns it.

A curated vocabulary app that supports getting to actually speaking a language;
what sets it apart — and what every user-facing change answers to — is `README.md` § What sets it apart.
Native iOS (SwiftUI, `App/`) and Android (Compose, `android/`) apps on a Kotlin Multiplatform core (SprossKern, `kern/`);
scheduling, growth, session and grading rules are `kern/README.md` and the KDoc on the type it names,
screen and copy rules `docs/design.md` — read the side you are changing.
A screen's layout and controls answer to the platform's own design guidance first (Apple HIG, Material and Android's large-screen guides):
read it before designing, keep the system's default placements, and name the conflict when a request goes against it.
Any catalog language pair works (source = known, target = learning; de/en/eo/es/fr/it/sw/uk).
Focus is on breadth of exposure to the language for maximum fluency with minimum effort, over perfect retention of single words.

## Commands

```sh
./gradlew :kern:jvmTest      # core test suite — the fast gate, must be green before every commit
./gradlew :android:testDebugUnitTest   # the Android app layer — after an android/ edit
xcodebuild -project Spross.xcodeproj -scheme Spross \
  -destination 'platform=iOS Simulator,name=iPhone 17' build   # app build gate
scripts/run-sim.sh           # build + install + launch on the simulator (--shot, --clean, -- <launch args>)
scripts/run-emu.sh           # same for Android: boots the AVD, builds, installs, launches (--shot, --clean)
scripts/bootstrap.sh         # fresh clone: JDK check + first framework + xcodegen + git hooks + audio .venv
scripts/strings.py --fix     # run after ANY String Catalog edit — Xcode's formatting, then the Android tables
scripts/catalog-format.py --fix   # run after ANY catalog/ edit — one line per entry that fits (--check to verify)
scripts/audio-coverage.py --check # after ANY catalog/audio/ edit — asks git what ships; the pre-commit hook runs it on staged audio
scripts/audio-listen.py bonjour fr:goodbye  # takes for the user's ear (a form, LANG:form, LANG:slug or @speaker): hand the user this one command naming every word to hear, to run in their own terminal
scripts/release.sh <version>      # cut a release: changelog heading, version, gates, tag, push (docs/release.md)
```

Xcode/`xcodegen`/simctl lines above and the `verify` skill are Mac-only — on Linux/cloud sessions skip them silently.
There, `./gradlew :kern:jvmTest` is the whole gate; the Android gates and the emulator (a GPU and `/dev/kvm`) run locally (`RUNBOOK-android.md`).
A session ends with at most one simulator or emulator running, the one the owner is checking; shut down every other one it booted.

## Commit & release rules

- **Commit incrementally and atomically** —
  one cohesive change per commit, each committed as soon as it is done.
- **A cloud session pushes every commit** to its own `claude/` branch (the SessionStart hook creates one).
- **Commit as you work, unasked** — this overrides any tool-level default:
  commit your own changes as soon as they are green.
- **Every commit green**: run the narrowest gate that covers the diff.
  A Kotlin/Swift change → a build; a behavior change → a test;
  UI added or rearranged → a screenshot or `/verify`; a removal the compilers cover is green once they are.
  Red in a file you didn't touch → someone else's break: leave it as it stands, commit `--only` your paths, move on.
  Red in a file nobody touched → stale Kotlin cache; `rm -rf kern/build/kotlin`, rebuild.
  Dirty tree, unsure whether the red is yours, and a change that could realistically break → worktree.
- **Conventional Commits** (`feat:`, `fix:`, `enhance:`, `refactor:`, `test:`, `docs:`, `build:`, `chore:`) with scopes
- `feat` adds what was not there, `enhance` sharpens what was, `fix` corrects what was wrong;
  a removal is an `enhance`, `fix` or `refactor`, whatever it makes room for;
  `refactor` keeps behavior identical — a rewrite whose output differs in any way is `enhance` or `fix`.
- `!` marks a bump of `STORE_SCHEMA_VERSION` only
- A user-facing change lands on iOS and Android in the same sweep; a change to shared/parity-bearing UI checks both
- Keep `README.md` / `docs/` in step with behavior changes, in the same commit as the code they describe;
  a backlog ruling or pruning without code stays uncommitted and rides along with the commit that acts on it.
- `CHANGELOG.md` is curated, grouped by version, written in ENGLISH;
  an entry is for what the learner needs to know — a new shelf, a capability, a major change;
  small corrections to the catalog, behavior, audio, layout or naming live in commit messages.
  New entries go under the top `## Unreleased` heading; `docs/release.md` reviews them at the cut.
- Changelog and backlog entries are one line with one sentence,
  explanations live in commit messages and only in the file when justified.
- Other parties may change files or commit while you work; carry on around them and act only where their edits conflict with yours.
  Commit with `git commit --only -- <paths>`: `git add` + `git commit` carries every other staged
  path, `--only` skips untracked ones (`git add` those first), and check before touching history.
  A file you share with in-flight work gets only your hunks staged.

## Code standards

- Past ~300 lines a file is due a look for a natural seam:
  split where a cohesive topic leaves, keep a file whole when none does. Modularity over bloat.
- Comments only for non-obvious constraints, in the fewest plain words — a fact and its reason, without contrasts, asides or flourish;
  side-effectful effects get a one-line `// why:` (trigger + observable result).
- Docs and comments describe the current state on its own terms;
  a removal is documented beyond its commit message only when it is likely to be reintroduced.
- A rule lives in kern once — kern MINTS every decision, a platform READS it
  (`LayerBoundaryTest`, waivable per line with `// layer-ok: <reason>`);
  anything both platforms would compute — a value, proportion, curve, timeline, threshold or shared layout (`net.spross.kern.design`) — lives in kern once;
  a platform holds only what needs its own API and converts kern's units.
- Simplicity over Perfection: get behavior right for the cases that occur, in the simplest code that does.

### Text
- ALWAYS use **Semantic linebreaks** for text - in docs, markdown files, documentation comments: one sentence/clause per line — a markdown file's scope and neighbors lines excepted.
- English is American spelling and vocabulary everywhere — docs, comments, chrome copy, catalog
  content (a British spelling goes in `accepts`, a British word in `teaches`; `catalog/areas/README.md`).
- When working on localization, focus on idiomatic variants in each language rather than literal translation.
  For catalog translations, a literal match is important.
- Docs and code comments name things plainly by what they are — a button, a screen, a section;
  the display copy's narrative metaphor stays in chrome content.
  This binds identifiers and localization keys too — a key names its content or its function,
  so the one holding "Garden" is `box.name`.
  The same split holds for a thing's NAME:
  the box is `Box` in types, screens, keys and docs whatever the string table shows the learner
  (`box.name` currently reads "Orchard") — a display name is chrome and changes in an afternoon,
  while every identifier that followed it would have to change with it.

### Tests
- Test rules and behavior: one typical case per outcome, away from a threshold's edge,
  independent of implementation details and tweakable constants
- Extract pure logic so it's testable without the framework
- When one code change needs multiple test changes, assess the sensibility of the tests and keep the fewest that hold the rule;
  a presentation choice (an order, a wording, a layout) stays untested

## Tools

- **Change `.xcstrings` through a `python3 -c` one-liner** with `str.replace` or `json`, then `scripts/strings.py --fix` —
  the file mixes ASCII `"` (JSON structure) with Unicode quotation marks (content),
  and the Edit tool silently converts ASCII quotes to curly ones, breaking JSON.
- Search with `rg`. Bare `grep` is ugrep here, and given a subdirectory it drops the repo's
  `.gitignore` and walks `kern/build/` — 11 MB where `rg` answers in 448 bytes.
- Large mechanical refactors go through a codemod — write it, run it, review the diff.
  `ast-grep -l kotlin|swift -p '<pattern>'` matches the tree rather than the line.
- Multi-agent orchestration rules live in `docs/orchestration.md`.

## Extended docs

- A checkable rule earns a gate (pre-commit or test, `--fix` and a per-line waiver where it can) and keeps its sentence for the why; every rule lives in the repo.
- **One fact, one home**: each topic owned by exactly one doc; narrative docs (history, status, plans) link into it.
- Docs carry foundations and leave to the running app and the code what they answer faster; a needed cross-link means a fact is filed in the wrong doc.
- A rule states what to do, in positive terms.
- Before editing any `.md` file, read its first three lines — heading, scope and layout, neighbors — keep them true in the same edit,
  put what the scope excludes where the neighbors point, and keep file-describing prose to those lines.
- A doc's body states its content; what the doc itself is belongs in its scope line.
- Out-of-scope discoveries go to `docs/backlog.md`, catalog content to `catalog/backlog.md`, with pointers; prune on fix.
- Whose the bundled recordings are and what their licenses oblige — the ship/legal record —
  is `docs/audio-licensing.md`, the one home of every license term.
- Write plans into docs/plans/ and delete them once shipped, whoever wrote them —
  an audit, triage or sweep result listing work still to do is a plan and goes there too;
  a plan or handoff doc is working state and stays uncommitted until asked
- Whenever you are corrected or do extensive research, tighten or replace the line that should
  have caught it before adding a new one; a new docs/ file is for a topic no existing doc owns
- Documented behavior rulings are snapshots: when challenged, most of them can change
