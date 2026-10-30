# Spross
What Spross is, how to build and run it, and where each part is documented; the rules themselves live in `docs/` and `kern/`.

A curated vocabulary app that supports getting to actually speaking a language:
pick the language you know (source) and the one you learn (target)
from the in-repo catalog (eight languages, declared in `catalog/languages.json`).
Native iOS (SwiftUI, iOS 17+) + watchOS companion, fully offline,
plus an Android app (Jetpack Compose) on the same engine.

## What sets it apart

Against Anki: curation and the mini games.
Against Duolingo: the learner sets the curriculum.

- **Curated for fluency** —
  the catalog is hand-built around the situations a speaker meets,
  with a literal match across every language.
- **The learner's curriculum** —
  they choose which situations to be ready for, and nothing forces a path.
- **A full card per word** —
  audio and a small illustration on each,
  ways of answering that get harder as the word settles,
  and a reveal that teaches its synonyms.
- **Known words are shelved fast** —
  the way a word is introduced lets one already known step aside after a single quick answer.
- **Mini games** (the code's drills) for what single cards cannot teach:
  numbers, the clock, dates, letters, countries, word order, opposites.
- **A companion to real speaking** —
  beside a course, a native speaker or a chat assistant,
  it expands and solidifies the vocabulary those conversations need.
- **On the wrist** — the Apple Watch reviews the box by multiple choice.
- **One box, any direction** —
  words are shared concepts rather than courses,
  so any catalog language can be learned from any other,
  and switching the known language keeps every word's progress.
- **Every learning element refined with research** — the evidence lives in each part's own doc.

Underneath, FSRS-6 schedules ONE memory per card,
new words are bounded by the round alone (`docs/growth-evidence.md`),
and phrases unlock once their component words are stable (`kern/README.md`).

## What it looks like

One engine, one palette, two native surfaces — the same word on both,
because both asked the same box for it.

| | Today | A card | The box |
|---|---|---|---|
| **iOS** | ![Spross on iOS — the Today screen](docs/screenshots/ios-home.png) | ![Spross on iOS — a review card](docs/screenshots/ios-session.png) | ![Spross on iOS — the box](docs/screenshots/ios-box.png) |
| **Android** | ![Spross on Android — the Today screen](docs/screenshots/android-home.png) | ![Spross on Android — a review card](docs/screenshots/android-session.png) | ![Spross on Android — the box](docs/screenshots/android-box.png) |

The drills stand on both phones too, each run on kern's rules.

## Install

**iPhone and Apple Watch** — [**Spross on the App Store**](https://apps.apple.com/app/id6810577722) (`net.spross.app`).
Android is not in a store yet, so it is sideloaded.

**Android** — [**Add Spross to Obtainium**](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https%3A%2F%2Fgithub.com%2Fpolygongmbh%2Fspross),
opened **on the phone**.
[Obtainium](https://obtainium.imranr.dev/) watches a repo's releases and installs them,
so with it already there the link opens its Add-App screen on this repo, ready to confirm;
without it, the same page offers Obtainium's own download
([`app-release.apk`](https://github.com/ImranR98/Obtainium/releases/latest/download/app-release.apk)
is the universal build).
Every release from here on is then offered as it appears,
and the version printed at the foot of the box opens the same link from inside the app.

Neither of those is required:
`spross-<version>.apk` from the
[latest release](https://github.com/polygongmbh/spross/releases/latest)
is the same file, installed by hand and updated the same way.

**iPhone test builds.**
Registered test devices only, and updates arrive as new builds rather than in place.
A release whose ad-hoc build came out carries an `itms-services://` link to open in Safari **on the device**;
internal TestFlight testers get the same build minutes after the tag, without review.

How a release is cut is `docs/release.md`;
how it is signed and published is `docs/distribution.md`.

## Structure

- `kern/` — **SprossKern**, the Kotlin Multiplatform core (`net.spross.kern`):
  domain model, catalog join, FSRS-6, box engine, session composer,
  answer normalizer, trainers, snapshot builders, store facade.
  Pure logic, time injected (`nowEpochMillis`/`tzId`), fully unit-tested.
  Engine contract: `kern/README.md`.
- `App/` — SwiftUI app: design system (poster-derived theme), file-backed store
  (one document per target language), screens (Home, Box and Settings as tabs;
  sessions and drills open over them).
  The only target that links the Kotlin framework.
- `Shared/`, `Watch/`, `Widgets/`, `WatchWidgets/` — decode-only Swift surfaces
  reading phone-built snapshots; no Kotlin linkage.
- `android/` — Jetpack Compose app on the same engine;
  catalog bundled by a Gradle sync task.
- `catalog/` — the in-repo content catalog (format spec: `catalog/README.md`);
  bundled as a folder resource.
- `docs/design.md` — the app-layer build contract; read before changing behavior.

## Build

```sh
brew install xcodegen        # once
scripts/bootstrap.sh         # fresh clone: JDK check, first SprossKern framework, xcodegen, git hooks
xcodebuild -project Spross.xcodeproj -scheme Spross \
  -destination 'platform=iOS Simulator,name=iPhone 17' build
```

Tests (the fast gate): `./gradlew :kern:jvmTest`

## Run it

`scripts/run-sim.sh` builds, installs and launches on the iPhone 17 simulator;
its header lists the flags and the DEBUG launch arguments.

Physical devices: `scripts/deploy-devices.sh` — Release, or `--debug` while iterating.

Android builds Mac-free — commands, SDK setup, emulator, install steps: `RUNBOOK-android.md`.
`scripts/run-emu.sh` is the emulator counterpart of `run-sim.sh`.

Framework mechanism: a pre-build phase stages `SprossKern.framework`
via `scripts/build-kern.sh` (integration detail: `docs/build.md`).
After adding/removing Swift source files or switching commits: `scripts/gen.sh` (`run-sim.sh` runs it on every build).
