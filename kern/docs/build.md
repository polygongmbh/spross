# KMP project & Apple integration

Gradle/Kotlin pins, targets, the framework hand-off to Xcode, and the trainer packs.
Engine contract: `../README.md`.

- Gradle root `app/` (wrapper committed; `.gitignore` += `build/`, `.gradle/`, `.kotlin/`,
  `local.properties`); module `:kern` at **`app/kern`** (`kern/` at root is the same
  APFS inode as Swift `Kern/` — never create it). Package `net.spross.kern`
  (+ `.trainer`). Pins (probe-proven, Xcode 26.6): Kotlin **2.4.10** (SKIE 0.10.14's ceiling —
  bump only as a pair; comment in the version catalog), serialization 1.11.0,
  datetime 0.8.0, Gradle 9.6.1, JDK 21 toolchain. Configuration cache on.
  Toolchain auto-provisioning is off: JDK 21 must be installed, and the Homebrew keg
  path is named in `gradle.properties` because Gradle cannot auto-detect it.
- Targets: `jvm()` (fast gate + Android-ready), `iosArm64`, `iosSimulatorArm64` — static
  framework **SprossKern**. No watchOS targets (nothing links Kotlin on watch; 3 unused
  slice builds cost ~40–60 % of every kern-edit rebuild, measured 23.7 s → target ≈ 10 s).
- Xcode: the app target links the framework directly (`FRAMEWORK_SEARCH_PATHS`, no SwiftPM
  binaryTarget — wrong build ordering + clean-checkout deadlock). An in-target xcodegen
  `preBuildScripts` phase branches on `$CONFIGURATION`/`$SDK_NAME`, runs the matching
  `linkDebug/ReleaseFramework<Target>` Gradle task, and copies the framework to a
  configuration-neutral search path. `scripts/bootstrap.sh` for fresh clones; a Release
  archive smoke check joins the gates. Only the APP target links Kotlin; widget/watch/
  complication are decode-only Swift (`snapshots.md`).
- Swift ergonomics: UI-crossing Kotlin types are data classes; a small Swift bridge file in
  App/Sources adds `Date ↔ epochMillis` helpers and `Identifiable`/`Equatable`
  conformances; Kotlin `Int` surfaces as `Int32` — bridge there, not at call sites.
- Trainer: single `:kern` module, `Long` cardinals everywhere (Kotlin `Int` is 32-bit on
  all platforms). Trainer registry: de/en/es/sw/uk
  authored; a language outside it has no drills (the hub's handling of that is an app rule).
  `Catalog.phraseTemplates(source, target)` is the frames' half of the card join:
  one `PhraseTemplate` per frame realized in BOTH languages, directional like a `Card`,
  with `count`/`masculineNumeral`/`note` riding along from the ANSWER realization.
  Nothing pair-shaped is stored, so authoring one language file lights up every pair it
  makes. Availability gate: **empty unless `Trainer.supports(target)`** — sampling generates
  the answer side's number words, so a language without a pack can only ever supply prompts.
  A frame whose slot the target cannot fill drops out on the same rule
  (`Trainer.supportsSlot`): a cardinal, a year and a clock come with every pack,
  a `fraction` needs the pack to READ one.
  Reverse mode is the same template read the other way, for any pair, not only `target == de`.
  German clock ACCEPTS 24-hour readings ("achtzehn Uhr fünfunddreißig", "null/vierundzwanzig
  Uhr" at midnight) alongside the colloquial display forms; display stays 12-hour.
  An hour word directly before "Uhr" apocopates: "ein Uhr", never "eins Uhr";
  bare "eins" stays ("um eins", "halb eins").
  A `TrainerTask` carries two prompt strings: **`prompt` is the machine form**
  (`"347"`, `"14:35"`) that callers parse — `PhraseSlots` does `prompt.toLong()`, and a
  Kotlin throw crossing the ObjC boundary crashes the app — while **`promptDisplay` is what
  the UI shows**, defaulting to `prompt`.
  Cardinals group their digits in threes with **U+202F narrow no-break space, from five
  digits up** (`9999` unbroken, `12 345`, `4 072 918 300`):
  dot and comma are inverted between German and English, so a neutral mark is the only one
  that teaches neither as the truth.
  Years and clock times are never grouped — they keep the default by setting nothing.
  `TrainerKind.Forms` asks the other ways a number is written — negatives, decimals,
  percentages, multiplicatives, fractions, ordinals — over a ten-rung ladder where each
  rung keeps everything below it, and its own `internal` model (`NumberValue`, `FormLimits`)
  never reaches the ObjC header. The rung's forms are intersected with the language's, so a
  pack that cannot read one never draws it, and a pack that authors none offers no Forms
  drill at all (`Trainer.supportsForms`). **A Forms prompt is the one language-dependent
  prompt**: German shows `3,7` where English shows `3.7`, because the reading names the mark
  (`Komma` · `point`) and a shared prompt would lie about the answer it grades — everything
  else stays neutral, including the ordinal mark `20.` and the `45 %` thin space.
  Fractions are drawn REDUCED: `2/4` would legitimately read both "zwei Viertel" and
  "ein halb", and no pack should carry that equivalence to grade its own drill.
  What each language reads for each form, with its source and its exclusions,
  is `../../docs/number-forms.md`.
  A sentence slot's grouped digits are accepted alongside the plain ones.
  **`DrillVariant` is what a RUN offers, `TrainerKind` is what fills a SLOT** — a Phrases
  run draws tasks whose kind is Numbers, Years or Clock — and the two must not be
  collapsed, because progress is kept per variant. `DrillUnlocks` holds the whole ladder
  as two tables of variant → level reached (empty = always available), reading a
  progress map the APP persists; kern stores nothing. `DrillRamp.step` is the rung ramp
  every drill shares (clean wins up, a miss down, floor 1, amber moves nothing), with how
  long a rung is left to the caller — `Trainer.winsToAdvance(fast)` reads the Fast
  modifier, `LetterDrill.winsToAdvance` counts a held vocabulary.
  `Trainer.reversed(task)` inverts the direction (words shown, the value typed) for any
  kind, so the app stays direction-agnostic: it always shows the prompt and grades
  against `accepted`.
  `Trainer.reference(language)` generates the numbers page from those same packs —
  bands keyed `ones`/`teens`/`tens`/`twenties`/`compounds`/`hundreds`/`places`,
  each key a stable identifier the app localizes into a heading — so the table cannot
  drift from what the drill grades.
  `PhraseSlots` samples level-aware — same per-kind ramp tables as the plain drills
  (a template's slot kind clamps the level).
  The unleveled `sample` overload keeps the prototype's biased full-difficulty draws
  (numbers favor 2–3 digits, years cluster 1950–2050);
  only Clock's unleveled draw coincides with the leveled ceiling.
  **`LetterDrill` is a separate facade, not a `TrainerKind` case**: its registry is
  alphabet file presence in the catalog (adding a language edits no Kotlin), its ramp is
  stateless and kern-owned (`entryLevel`/`winsToAdvance`, then the `DrillRamp.step` every
  drill shares — both D11 halves in one place so two platforms cannot drift),
  sampling takes an injected `Random` and an
  app-computed promptable set (device voices are an app fact).
  A gap row draws its word from a POOL (`Catalog.alphabetExamples`, rules in
  `catalog/README.md` § Alphabet), the app narrowing it to what the device can say and
  flagging what the box already holds; kern favours the known words while at least three
  stand, and spends no randomness where a row offers one word.
  Dictation weighs its draw (`dictationWeight`): a floor of one that shuts nothing out,
  plus how many of the language's own hard graphemes the word carries (`Alphabet.trickyGlyphs`),
  its lapses, and FSRS difficulty above the midpoint — each capped, so one leech cannot take
  a rung over, and all three zero on a clean plain word, where the draw is bit-for-bit the
  uniform one. The two schedule figures ride in on `DictationCandidate`; kern reads no state.
  Dictation draws only
  `BoxEngine.consolidatedCardIds` through `dictationGradingCard` — it never books a
  review (transcription is not recall; drills are stateless).
  Android: landed — `androidLibrary` KMP target
  (`com.android.kotlin.multiplatform.library`, AGP 9.3.0, compileSdk 36 / minSdk 26),
  androidMain NFC actual mirrors jvmMain; `:android` consumes the same facades.
  Gate: `./gradlew :kern:compileAndroidMain`.
- Web: `js { browser() }` target feeds the spross.net drill (`docs/website.md`).
  `binaries.executable()` → one webpack bundle, `:kern:jsBrowserDistribution` →
  `kern/build/dist/js/productionExecutable/kern.js` (UMD global `kern`).
  The page-facing surface is the `@JsExport` facade `net.spross.kern.web`
  (`NumbersDrill`, `WebTrainer`) — JS-clean types only, `Long` never crosses;
  jsMain's NFC actual is `String.prototype.normalize("NFC")`.
  Gradle provisions Node/Yarn on first build (network) and pins
  `kotlin-js-store/yarn.lock` (committed). Gate: `./gradlew :kern:jsBrowserDistribution`.
