# KMP project & Apple integration
Gradle and Kotlin pins, targets, and the framework hand-off to Xcode, Android and the web.
Neighbors: the engine contract `../README.md`, the trainer packs `trainer.md`.

- Gradle root is the repo root (wrapper committed); module `:kern` at `kern/`,
  package `net.spross.kern`.
  Pins (probe-proven, Xcode 26.6): Kotlin **2.4.10** (SKIE 0.10.14's ceiling —
  bump only as a pair; comment in the version catalog), serialization 1.11.0,
  datetime 0.8.0, Gradle 9.6.1, JDK 21 toolchain.
  Configuration cache on.
  Toolchain auto-provisioning is off: JDK 21 must be installed, and the Homebrew keg
  path is named in `gradle.properties` because Gradle cannot auto-detect it.
- Targets: `jvm()` (the fast gate), `iosArm64`, `iosSimulatorArm64` — static framework **SprossKern**.
  No watchOS targets: nothing links Kotlin on the watch,
  and three unused slice builds cost ~40–60 % of every kern-edit rebuild.
- Xcode: the app target links the framework directly (`FRAMEWORK_SEARCH_PATHS`, no SwiftPM
  binaryTarget — wrong build ordering + clean-checkout deadlock).
  An in-target xcodegen `preBuildScripts` phase branches on `$CONFIGURATION`/`$SDK_NAME`,
  runs the matching `linkDebug/ReleaseFramework<Target>` Gradle task,
  and copies the framework to a configuration-neutral search path.
  `scripts/bootstrap.sh` for fresh clones; a Release archive smoke check joins the gates.
  Only the APP target links Kotlin; widget/watch/complication are decode-only Swift (`snapshots.md`).
- Swift ergonomics: UI-crossing Kotlin types are data classes;
  `App/Sources/KernBridge.swift` adds `Date ↔ epochMillis` helpers and `Identifiable`/`Equatable` conformances;
  Kotlin `Int` surfaces as `Int32` — bridge there, not at call sites.
  Engine boundary time is `nowEpochMillis: Long` + `tzId: String`
  (kotlinx-datetime has no Swift-Date bridging; `Instant`/`TimeZone` are constructed inside);
  the time zone is the device's current one, per call,
  and day keys are ISO whatever the device calendar.
  A Kotlin default value does NOT cross: adding a parameter to a UI-crossing type keeps
  every Kotlin caller compiling and breaks every Swift construction site,
  so the jvm and Android gates stay green and only an app build reports it.
  SKIE hands Swift every top-level function as a global, yet its `<File>Kt` facade stays callable,
  so a Swift call spelled through the facade (`CardKt.kindEmoji`) pins that function to its file.
- Android: `androidLibrary` KMP target
  (`com.android.kotlin.multiplatform.library`, AGP 9.3.1, compileSdk 36 / minSdk 26);
  androidMain's NFC actual mirrors jvmMain, and `:android` consumes the same facades.
  Gate: `./gradlew :kern:compileAndroidMain`.
- Web: `js { browser() }` target feeds the spross.net drill (`../../docs/plans/website.md`).
  `binaries.executable()` → one webpack bundle, `:kern:jsBrowserDistribution` →
  `kern/build/dist/js/productionExecutable/kern.js` (UMD global `kern`).
  The page-facing surface is the `@JsExport` facade `net.spross.kern.web`
  (`NumbersDrill`, `WebTrainer`) — JS-clean types only, `Long` never crosses,
  and the drill grades through the same `AnswerNormalizer` the app builds;
  jsMain's NFC actual is `String.prototype.normalize("NFC")`.
  Gradle provisions Node/Yarn on first build (network) and pins
  `kotlin-js-store/yarn.lock` (committed).
  Gate: `./gradlew :kern:jsBrowserDistribution`.
