# Spross

A personal "growing box" vocabulary app:
pick the language you know (source) and the one you learn (target)
from the in-repo catalog (Deutsch · English · Kiswahili · Українська).
Native iOS (SwiftUI, iOS 17+) + watchOS companion, fully offline,
plus an Android core-loop app (Jetpack Compose) on the same engine.

The box only grows while your material sits:
new cards enter on a load-based budget behind a health gate,
phrases unlock once their component words are stable,
and everything is scheduled by a golden-vector-tested FSRS-6 engine.
Each card keeps ONE memory — reviews alternate between typed production
and self-graded recognition of the same schedule.

## Structure

- `kern/` — **SprossKern**, the Kotlin Multiplatform core (`net.spross.kern`):
  domain model, catalog join, FSRS-6, box engine, session composer,
  answer normalizer, trainers, snapshot builders, store facade.
  Pure logic, time injected (`nowEpochMillis`/`tzId`), fully unit-tested.
  Engine contract: `kern/README.md`.
- `App/` — SwiftUI app: design system (poster-derived theme), file-backed store
  (one document per target language), screens (Heute / Box / Fortschritt).
  The only target that links the Kotlin framework.
- `Shared/`, `Watch/`, `Widgets/`, `WatchWidgets/` — decode-only Swift surfaces
  reading phone-built snapshots; no Kotlin linkage.
- `android/` — Jetpack Compose app (core loop: onboarding, Heute, sessions)
  on the same engine; catalog bundled by a Gradle sync task.
- `catalog/` — the content catalog, in-repo: shared word concepts +
  per-language realizations + pair-authored phrases.
  Format spec: `catalog/README.md`. Bundled as a folder resource.
- `docs/design.md` — the app-layer build contract; read before changing behavior.

## Build

```sh
brew install xcodegen        # once
scripts/bootstrap.sh         # fresh clone: JDK check, first SprossKern framework, xcodegen
xcodebuild -project Spross.xcodeproj -scheme Spross \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
```

Tests (the fast gate): `./gradlew :kern:jvmTest`

Android (no Mac needed — see `RUNBOOK-linux.md`):

```sh
./gradlew :android:assembleDebug     # APK: android/build/outputs/apk/debug/android-debug.apk
./gradlew :android:testDebugUnitTest :kern:compileAndroidMain   # android gates
```

Framework mechanism: the app target's pre-build phase runs `scripts/build-kern.sh`,
which maps `$CONFIGURATION`/`$SDK_NAME` to the matching Gradle
`linkDebug|ReleaseFramework<slice>` task and stages `SprossKern.framework`
at `kern/build/xcode/<config>/`, where `FRAMEWORK_SEARCH_PATHS` points.
After adding/removing Swift source files: `xcodegen generate`.
