# The kern boundary — what crosses to Swift, Android and the web
What changes when a kern type or function crosses to Swift, Android or the browser; pins, targets and the build wiring are the version catalog's, `kern/build.gradle.kts`'s and `project.yml`'s.
Neighbors: the engine contract `../kern/README.md`, what may run when `performance.md`.

- Only the app target links Kotlin; the widgets, the watch and its complications decode snapshots in plain Swift (`snapshots.md`).
- UI-crossing Kotlin types are data classes, so Swift sees value equality.
- Kotlin `Int` surfaces as `Int32`, and `Date` crosses as epoch milliseconds:
  `App/Sources/KernBridge.swift` bridges both, never a call site.
- Engine boundary time is `nowEpochMillis: Long` + `tzId: String`,
  since kotlinx-datetime has no Swift `Date` bridging; `Instant` and `TimeZone` are constructed inside.
  The time zone is the device's current one, per call,
  and day keys are ISO whatever the device calendar.
- A Kotlin default value does NOT cross:
  adding a parameter to a UI-crossing type keeps every Kotlin caller compiling and breaks every Swift construction site,
  so the jvm and Android gates stay green and only an app build reports it.
- SKIE hands Swift every top-level function as a global, yet its `<File>Kt` facade stays callable,
  so a Swift call spelled through the facade (`CardKt.kindEmoji`) pins that function to its file.
- androidMain's NFC actual mirrors jvmMain, and `:android` consumes the same facades as Swift.
- The browser sees only the `@JsExport` facade `net.spross.kern.web`:
  JS-clean types only, `Long` never crosses,
  and the page grades through the same `AnswerNormalizer` the apps build.
