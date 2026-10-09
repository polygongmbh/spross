# Review B — trainer/drills, listening/audio, watch/widgets: parity leftovers and cruft

Read-only review of iOS ↔ Android ↔ kern parity after the kern logic unification, confined to the trainer, listening/audio and watch/widget packages.
Every finding was checked against both sides' source (file:line quoted); "verified" means both sides were read in full and `rg` confirmed usage, "likely" means the rule is clear but one side's helper was not traced to its definition.
Gates: `kern` = `./gradlew :kern:jvmTest` runs here; `Mac` = Swift change, needs Xcode; `Android` = `:android:testDebugUnitTest`, local only.
Findings already in `docs/backlog.md` are marked and only kept where this adds something.

Ranking inside each section is by value (parity risk × spread).

---

## A. Rules/values computed on both platforms (or on one where kern already has it)

### A1. Letter drill score line: iOS prints a Sprosse, Android prints none — a parity break, not a leftover
- Evidence: `App/Sources/Screens/LetterDrillView+Formats.swift:41-44`
  `DrillStreakLine(sprosse: Text("trainer.sprosse \(Int(run.sprosse).formatted())"), answerStreak: …)`
  vs `android/src/main/kotlin/net/spross/app/ui/LetterDrillScreen.kt:53-54`
  `// One Sprosse, mapped to formats by kern — there is no Sprosse to name.` / `sprosse = null,`.
  Kern's `LetterDrillRunState.sprosse` (`LetterDrillRunState.kt:91`) runs 1..9 (`LetterDrillAvailability.Report.maxSprosse`), and `docs/drills-words.md` says the drill "shares the slot drill's chrome — the endless scaffold, the answer-streak line".
- Why it matters: the two phones show different chrome for the same run state; whichever is right, the other is a lie about the ladder.
- Fix: rule on it once (kern: e.g. `DrillRunProgress.showsSprosse` or a KDoc line on `LetterDrillRunState.sprosse`) and align the other platform; the Android comment and the iOS line cannot both stand.
- Size: S. Gate: Mac + Android (kern test if a flag is added). Confidence: verified.

### A2. Letters overview: the format ladder's order and the "format reachable" rule are minted on both phones
- Evidence: iOS `LettersOverview+Practice.swift:33-35` hard-codes `[.choiceEasy, .choiceConfusable, .typed, .dictation]`; Android `LettersOverviewScreen.kt:64` reads `LetterFormat.entries`.
  iOS `LettersOverview+Practice.swift:113-116` `reachable(format)`: `drillAvailable && (format != .dictation || dictationAvailable)`; Android `LettersOverviewScreen.kt:140-143` `formatOpen(format, report)`: the same predicate.
  Kern's `LetterDrillAvailability.Report` (`LetterDrillAvailability.kt:45-83`) exposes `drillAvailable`, `dictationAvailable`, `openingFormat`, `formatCleared` — but no `formatOpen(format)`.
- Why: an ordering written down beside kern's enum, and a padlock rule each phone re-derives; a fifth format or a second priced format would have to be re-minted twice.
- Fix: `Report.formatOpen(format: LetterFormat): Boolean` in kern (one line + one test); iOS reads `LetterFormat.allCases`.
- Size: S. Gate: kern test (rule) + Mac + Android (callers). Confidence: verified.

### A3. Reference-sheet caption lines joined with " · " on both phones (dates and atlas)
- Evidence: iOS `DatesReference.swift:73-79` `otherForms`: `[abbr] + teaches + [dateForm]` joined `" · "`; Android `DatesReference.kt:47-50` identical.
  iOS `CountriesReference.swift:62` `([nationality] + languages).joined(separator: " · ")`; Android `CountriesReference.kt:30-33` identical for both sides.
  Kern `DateReferenceRow`/`CountryReferenceRow` ship the parts, not the caption.
- Why: what stands under a name on a reference row is a content rule (which forms, in which order), and it is written twice.
- Fix: `DateReferenceRow.otherForms: List<String>` and `CountryReferenceRow.sourceUnder/targetUnder: List<String>` (or a joined string) in kern; the separator can stay chrome.
- Size: S. Gate: kern test + Mac + Android. Confidence: verified.

### A4. Lock-screen "now playing" title and language line composed on both phones, brand constant minted twice
- Evidence: iOS `App/Sources/Model/AppModel+Listening.swift:35` `private static let brand = "Spross"`, `:156-157` `"\(Self.brand) · \(mode)"` and `"\(source) – \($0)"`;
  Android `listen/ListeningBridge.kt:48` `const val SPROSS_BRAND = "Spross"`, `listen/ListeningDriver.kt:216` `"$SPROSS_BRAND · ${chrome.listenTitle}"`, `:234` `"${…source} – ${…target}"`.
- Why: a formatting rule (brand · mode / known – learned, en dash, middle dot) written twice; `kern` already owns `Legal` (the app's own addresses) and would own its name the same way.
- Fix: kern `ListeningNowPlaying.title(mode: String)` and `languages(source, target)` (or at least `const val BRAND`) under `net.spross.kern.listen`; both drivers call it.
- Size: S. Gate: kern test + Mac + Android. Confidence: verified.

### A5. Answer-streak milestone glyphs live on both phones while the sibling table lives in kern
- Evidence: iOS `Design/DrillChrome.swift:187-194` `.trophy→🏆 .cheer→🎉 .effort→💪 .sprout→🌱`; Android `ui/DrillChrome.kt:167-172` the same four.
  Kern already puts the pause glyphs on the enum: `DrillPacing.kt:20-26` `DrillPauseReason.emoji` (🎉 ☕️ 💪), and the roster's faces on `Drill.emoji` (`Drill.kt:14-21`). `DrillRun.kt:55-57` says "which glyph wears a milestone is the platform's chrome" — the opposite ruling from the pause enum two files away.
- Why: two rulings for the same kind of value; the glyph table is a value both phones compute, which the unification says lives in kern once.
- Fix: `AnswerStreakMilestone.emoji` in kern, mirroring `DrillPauseReason.emoji`; delete both platform switches and the KDoc sentence.
- Size: S. Gate: kern (no test needed, a table) + Mac + Android. Confidence: verified.

### A6. Widget word order: "shortest pair first, then shortest word" sorted on both phones
- Evidence: iOS `Widgets/Sources/WordWidget.swift:186-192` `sortedForDisplay`: `(word.count + meaning.count, word.count)` ascending; Android `widget/WidgetLayouts.kt:95-97` `SHORTEST_FIRST = compareBy({ word.length + meaning.length }, { word.length })`.
- Why: an ordering rule written twice; the iOS widget links no Kotlin, but kern can still ship the rows pre-sorted for display or expose a comparator Android uses and iOS mirrors with a `layer-ok` the way `WidgetRotation.window` is waived (`WordWidget.swift:160-161`).
- Fix: `WidgetExposure.displayOrder` (or a `WidgetSnapshotView.sortedForDisplay`) in kern; Android reads it, iOS keeps a waived mirror or the snapshot carries a `displayRank`.
- Size: S. Gate: kern test + Android (+ Mac if the mirror moves). Confidence: verified.

### A7. Widget activity-strip geometry (bar 3 pt, gutter 1.5 pt) minted on both phones
- Evidence: iOS `Widgets/Sources/WidgetActivityStrip.swift:12-13` `barWidth: CGFloat = 3`, `spacing: CGFloat = 1.5`; Android `widget/WidgetStrip.kt:29-30` `BAR_WIDTH = 3.dp`, `GUTTER = 1.5.dp`.
  Kern `ActivityScale.widget = ActivityScale(16.0, 3.0, 1.5)` (`design/ActivityBars.kt:49`) carries max/min/stub HEIGHT only — the 3/1.5 there are heights, not width/gutter, which makes the coincidence easy to misread.
- Why: "a shape's geometry … lives in kern once" (`CLAUDE.md` invariants); the strip is the one piece of shared layout data here still cut by hand on both sides.
- Fix: add `barWidth`/`gutter` to `ActivityScale` (the snapshot already carries `activityHeight`; carry the two beside it so the Kotlin-free iOS extension reads them off the JSON).
- Size: S. Gate: kern test + Mac + Android. Confidence: verified.

### A8. Three copies of the widget's fallback picture 🗂️
- Evidence: `Widgets/Sources/WordWidget.swift:150` `$0.emoji ?? "🗂️"`, `WatchWidgets/Sources/WatchWordWidget.swift:69` `entry.emoji ?? "🗂️"`, `android/…/widget/WidgetFace.kt:66` `FALLBACK_PICTURE = "🗂️"`.
- Why: one glyph, three homes, no gate; a change lands on one tile and not the others.
- Fix: bake the fallback into the snapshot entry (`WidgetSnapshotBuilder.doc` writes `emoji = card.emoji ?: FALLBACK`), or name it once in kern and let Android read it while the two Kotlin-free targets keep a commented mirror.
- Size: S. Gate: kern test. Confidence: verified.

### A9. Numbers run title rule (single exercise → its name, else hub title; challenge → challenge title) on both phones
- Evidence: iOS `NumbersRunView+Mode.swift:48-50` `titleKey`: `exercises.count == 1 ? exercises[0].trainerTitleKey : "trainer.hub.title"`, `NumbersRunView+Run.swift:31-33` challenge override; Android `ui/NumbersRunScreen.kt:47-52` the same three-way rule.
- Why: which name a tile wears is chrome, but WHICH of three the run earns is a rule both compute.
- Fix: `NumbersMode.titleExercise: NumbersExercise?` (null = several) in kern, plus `challenge != null` already visible; both sides keep only the key lookup.
- Size: S. Gate: kern + Mac + Android. Confidence: verified.

### A10. Small values minted on both phones around listening
- Low-volume hint polling period 1 s: `App/Sources/Audio/VolumeHint.swift:36` `.seconds(1)` and `android/…/ui/VolumeHint.kt:39` `delay(1_000)`.
- Transport button sizes 56/72: `ListeningView.swift:78,86,92` and `ui/ListeningScreen.kt:184`.
- Bedtime progress `elapsed = total − remaining`: `Model/ListeningBedtime.swift:70-74` and `listen/ListeningDriver.kt:219-220`.
- Why: each is a value both compute; none is risky alone, but they are the kind of leftover the sweep was for and `kern/listen` already owns every other listening number.
- Fix: `VOLUME_HINT_POLL_MS`, `ListeningTransport` sizes under `kern.design`, `listeningBedtimeElapsedMs` — or an explicit ruling that these stay platform (in which case say so once in `docs/surfaces.md`).
- Size: S each. Gate: kern + Mac + Android. Confidence: verified (values), ruling needed.

### A11. iOS reveal/insert animations write `0.25` where `Animation.cardReveal` already reads kern's `CardMotion.REVEAL_MS`
- Evidence: `App/Sources/Design/CardFlip.swift:44` `static let cardReveal = Animation.easeOut(duration: Double(CardMotion.shared.REVEAL_MS) / 1000)`; but `DrillOverview.swift:84`, `NumbersOverview.swift:74`, `LettersOverview.swift:59` (`withAnimation(.easeOut(duration: 0.25)) { lastRun = result }`) and `DrillOverviewPage.swift:53` (scroll-to-tile) each spell `0.25`.
  Likewise the reduce-motion switch `reduceMotion ? .easeOut(duration: 0.2) : .cardFlip` at `DrillRunning.swift:139`, `Design/QuestionStage.swift:24`, `Screens/SessionView.swift:223`, `SessionView+Turn.swift:60`; `DrillChoiceGrid.swift:41` `.easeOut(duration: 0.2)` and `DrillChrome.swift:53` `.easeOut(duration: 0.2)` where Android's grid eases on its shared `turnTween()`.
- Why: a timing kern names (`CardMotion.kt:20`) re-typed as a literal at four sites; the reduce-motion duration is a fifth timing nobody named.
- Fix: use `.cardReveal` at the four sites; add `CardMotion.REDUCED_MS` (or a Swift `Animation.cardSwitchReduced`) for the 0.2.
- Size: S. Gate: Mac (kern test if a constant is added). Confidence: verified for the 0.25 sites; likely for the Android grid comparison (`turnTween` not traced).

---

## B. Rules minted on one platform only that kern should name

### B1. Android widget re-derives the chrome language from the profile prefs instead of reading kern's shipped answer
- Evidence: kern writes `chromeLanguage` into the widget document (`snapshot/WidgetSnapshotBuilder.kt:100`, `SnapshotSupport.kt:29-30`), and iOS reads it (`Widgets/Sources/WidgetSnapshot.swift:51`, `WordWidget.swift:177`). Android's `WidgetSnapshotView` (`WidgetSnapshotBuilder.kt:124-150`) exposes no `chromeLanguage`, so `widget/WidgetFace.kt:101-104` reads `ProfileStore(prefs).source` and recomputes `Chrome.forSource(...)` (`Chrome.kt:558-559`), which calls the same `LanguageChoices.chromeLanguage`.
- Why: the two can disagree whenever the stored profile source and the last-written snapshot differ (a profile switched before the next save), and the tile then speaks one language about a box written in another.
- Fix: `val chromeLanguage: String get() = doc.chromeLanguage` on `WidgetSnapshotView`; Android's `WidgetFaces.load` builds `Chrome.forSource(view.chromeLanguage)` and `chrome(context)` keeps only the no-snapshot fallback.
- Size: S. Gate: kern (one accessor) + Android. Confidence: verified.

### B2. Watch-only timings and floors beside kern cousins (no Android twin — for the record)
- `Watch/Sources/WatchModel.swift:49` `roundFloor = 5`, `:151` `hasPool >= 2`, `:60` `flashMillis = 260`, `:220` auto-advance `900 / 2000` ms, `WatchCelebrationView.swift:20` linger 2000 ms; kern's beats are `ADVANCE_LIVE_MS = 450`, `ADVANCE_EXPLICIT_MS = 1200` (`session/Turn.kt:56,62`). `Shared/Sources/WatchPracticeQuestion.swift:24` `optionCount = 4` beside kern `MultipleChoice.PER_QUESTION = 3` (`session/MultipleChoice.kt:38`).
- Why: the watch links no Kotlin, so these cannot be read; but the snapshot could carry what the phone decided (`WatchSnapshotBuilder` already ships `distractors`, cue, role), and the round floor is a rule the phone's Home card would want to agree with.
- Fix: carry `roundFloor` and the option count in the snapshot document (kern names them, the watch reads); leave the haptic/flash timings as wrist chrome. Time-based grading itself is already a backlog decision (`WatchGrading`).
- Size: S. Gate: kern test + Mac. Confidence: verified (values).

---

## C. Platform code re-implementing a kern read model

### C1. Android `TypedDrillView` mirrors `DrillRunProgress` field by field (the iOS `DrillSnapshot` twin is in the backlog)
- Evidence: `android/…/TypedDrill.kt:62-72` carries `index, sprosse, answerStreak, bestAnswerStreak, outcomes, tally, question`; every one but `sprosse` is already on `DrillRunProgress` (`kern/…/DrillRunProgress.kt:22-110`). `ui/TypedDrillScreen.kt:71-82` hands them one by one to the manual `DrillRunScaffold` overload although `RunScaffold.kt:247-273` has the `progress:` overload every other drill screen uses. `bestAnswerStreak` is never read (`rg bestAnswerStreak android/src/main` → only the three construction/declaration sites + `summary.bestAnswerStreak` in `DrillChrome.kt:112`).
  iOS: `DrillFace.swift:150-172` `DrillSnapshot` — same shape; `bestAnswerStreak` unread there too (`rg` → declaration `DrillFace.swift:155` and two constructions only).
- Why: the unification's point was that the shell reads `DrillRunProgress`; the typed drills on BOTH phones still read a hand-cut copy, and the copy already carries a dead field on each side. Backlog names only the iOS half.
- Fix: Android — `TypedDrill` exposes `sprosse: Int` only; `TypedDrillScreen` uses `DrillRunScaffold(progress = flow.progress, sprosse = …)`; delete `TypedDrillView`. iOS — the backlog item (`DrillRunView` via `runScreen`), dropping `DrillSnapshot.bestAnswerStreak` on the way.
- Size: S (Android), M (iOS). Gate: Android + Mac. Confidence: verified. (iOS half already in backlog; the Android twin and the two dead fields are new.)

### C2. iOS `DrillRunResult` is a hand copy of kern's `DrillRunSummary`
- Evidence: `App/Sources/Design/DrillChrome.swift:97-115` re-declares `doneCount, bestAnswerStreak, newRecord, milestone, timed, worthReporting, celebrated` and `KernBridge.swift:186-195` copies them across from `DrillRunSummary`; every reader (`DrillResultTile`, `TrainerHubView.report`, `DrillOverviewPage`) reads the copy. Android renders `DrillRunSummary` directly (`ui/DrillChrome.kt:91`, `TrainerStore.kt:219`).
- Why: a second declaration of kern's read model that has to be kept in step by hand (the KDoc on each field says "kern's"); the only thing it adds is the `title`.
- Fix: `struct DrillRunResult { let summary: DrillRunSummary; let title: LocalizedStringKey }` (Kotlin data classes are `Equatable` through `isEqual`, so `.onChange(of:)` keeps working).
- Size: S. Gate: Mac. Confidence: verified.

---

## D. Historical cruft

### D1. Comments that justify against the past (CLAUDE.md § Text forbids these)
All verified by `rg`; each line names what used to be, not what is.
- `App/Sources/Audio/PronunciationPlayer.swift:55` "Recovery used to be the NEXT word; it is this one."
- `App/Sources/Design/Sounds.swift:20-25` "Nothing noticed while the app only ever chimed … since the words got a voice", `:30` "the way the system-sound route used to guarantee".
- `App/Sources/Screens/NumbersRunView+Audio.swift:20-22` "It began to matter here when "Aufdecken" started REMOVING the field rather than disabling it".
- `App/Sources/Screens/TrainerHubView+Destinations.swift:147` "Clock, phrases and the alphabet are no longer surfaces of their own".
- `App/Sources/Screens/NumbersRunView+Mode.swift:91-92` "the standalone years drill was dropped as redundant, and years live on only as a phrase slot".
- `Watch/Sources/WatchModel.swift:204-205` "a silent correct tap used to feel the same as no tap at all".
- `android/…/audio/PronunciationPlayer.kt:31-33` "they used to run on the main thread at every reveal autoplay".
- `kern/…/trainer/NumbersRun.kt:12` "the machine both apps used to re-derive".
- `kern/…/trainer/SlotDraws.kt:9` "The drill used to recover its value from the rendered prompt".
- `kern/…/trainer/NumbersMode.kt:81-85` "CAUTION, live quirk carried over verbatim" (and a quirk — a counting-only run in a phrase-capable pair files under `Counting.de-uk` — that reads as a bug kept for compatibility the project says it does not need: "pre-production — no data-format preservation").
- Fix: rewrite each to state the current rule; for `NumbersMode.recordLanguage` decide whether the pair suffix is wanted at all (if not, drop it — no live data to preserve).
- Size: S (M for the `recordLanguage` decision). Gate: none for comments; kern test if `recordKey` changes. Confidence: verified.

### D2. Stale references to a `DrillEffect.SayAnswer` that kern does not have
- Evidence: `DrillRunView.swift:57`, `WordScrambleView.swift:36`, `SentenceScrambleView.swift:38`, `NumbersRunView+Audio.swift:7` all say "kern hands over (`DrillEffect.SayAnswer`)"; kern's effects are `ArmAdvance, CancelAdvance, Tone, ReleaseFocus, Silence` (`trainer/DrillRun.kt:18-36`) — saying the answer goes through `Reading`/`Reader` now.
- Fix: point the four at `Reading` / `Reader.follow`. Size: S. Gate: none. Confidence: verified.

### D3. "Probe-only completion; gameplay is fire-and-forget" is no longer true
- Evidence: `App/Sources/Audio/Speaker.swift:17` and `PronunciationPlayer.swift:44` keep that sentence, but `Pronouncer.swift:152-157` and `:174-178` pass the listening run's `onFinish` into both (`AppModel+Listening.swift:246-249`).
- Fix: describe `onFinish` as what a listening beat arms off (Android's `Speaker.kt:52`/`PronunciationPlayer.kt:54-58` already word it that way). Size: S. Gate: none. Confidence: verified.

### D4. Dead Swift bridges in `KernBridge.swift`
- Evidence: `App/Sources/KernBridge.swift:117` `LetterDrill.ceiling(dictation:)`, `:119` `LetterDrill.format(sprosse:)`, `:127-131` `CountryDrill.step(sprosse:…)` — `rg -n 'ceiling\(dictation|format\(sprosse:|\.step\(sprosse:' App` finds only the declarations. `CountryDrill.ceiling` (`:125`) and `DateDrill.ceiling(content:reverse:)` (`:142`) are live but rename kern's `MAX_SPROSSE`/`maxSprosse` — Android calls the kern names (`CountriesOverviewScreen.kt:38`, `DatesOverviewScreen.kt:41`), so a reader of both sees two names for one rule.
- Fix: delete the three dead bridges; keep the `Int32` conversions but name them after kern (`maxSprosse`). Size: S. Gate: Mac. Confidence: verified.

### D5. Seven copies of the UI-test "stand the run mid-streak" arithmetic
- Evidence: `done: Int32(answerStreak + 6)`, `bestAnswerStreak: Int32(max(answerStreak, 12))` plus the full `core.doCopy(...)` field list at `CountryDrillFace.swift:139-154`, `DateDrillFace.swift:140-155`, `LetterDrillView+Run.swift:91-106`, `WordScrambleView+Run.swift:80-95`, `SentenceScrambleView+Run.swift:83-98`, `OppositesView+Run.swift:71-86`, `NumbersRunView+UITest.swift:15-18`.
- Why: a kern value (`DrillRunCore`) copied by hand seven times, each listing every `DrillRunCore` field — the next field added to the core is seven compile errors.
- Fix: `DrillRunCore.seededStreak(answerStreak: Int): DrillRunCore` in kern (DEBUG-only use, but a one-liner), each face calls `run.doCopy(core: run.core.seededStreak(n), …)`; or one Swift extension on `DrillRunCore`.
- Size: S. Gate: kern (no test) + Mac. Confidence: verified.

### D6. `hushAnswer()` duplicates the driver's `silence()`
- Evidence: `DrillRunView.swift:132-134` and `NumbersRunView+Audio.swift:16-18` both define `func hushAnswer() { reader.hush() }`; `DrillRunning.swift:243` already gives every drill `func silence() { reader.hush() }`, and `onDisappear` is the only caller (`DrillRunView.swift:125`, `NumbersRunView.swift:114`).
- Fix: call `silence()` in `onDisappear`, delete both. Size: S. Gate: Mac. Confidence: verified.

### D7. Android `ScrambleTileBank` cuts its card radius by hand
- Evidence: `ui/ScrambleTileBank.kt:118` `val radius = 28.dp` while `ui/Theme.kt:44` defines `large = RoundedCornerShape(28.dp)`; iOS reads `Theme.radius.card` (`ScrambleTileBank.swift:77,84`).
- Fix: read the shape token (`MaterialTheme.shapes.large`) or a `Theme.radius.card`. Size: S. Gate: Android. Confidence: verified.

### D8. Duplicated `// why:` lines inside these packages (backlog already tracks the scan; these are the concrete groups it names as missed)
- iOS "internal, not private — the +Run extension arms and cancels it" ×5 and "reads and drives it" ×3 (the drill views); "BOTH hooks. .onChange never fires for the FIRST question" ×2 (`WordScrambleView.swift:74`, `OppositesView.swift:59`) and the same sentence reworded at `LetterDrillView.swift:79`; "not under a screen reader — moving the keyboard focus would drag VoiceOver" ×2 (+ a reworded third in `LetterDrillView.swift:91`); "the run says its answers out loud, so it owes the learner a way to silence them here" at `DrillRunView.swift:103`, `NumbersRunView.swift:103` and on Android `TypedDrillScreen.kt:79`, `NumbersRunScreen.kt:71`.
- Why: the backlog's scan found "two scramble screens" by comment; these are the sites.
- Fix: one `showsMuteButton` ruling on the scaffold (`DrillRunScaffold`/`runScreen` default per drill kind) removes the four-site why; the focus hooks belong in one `DrillRunning` helper (`focusOnQuestion`) with the why said once.
- Size: S–M. Gate: Mac + Android. Confidence: verified. (Partly in backlog.)

---

## E. Files over ~300 lines not in the backlog
- `App/Sources/Screens/TrainerHubView.swift` — 315 (the `NumbersReading`/`NumbersExercise`/`DrillModifier` title extensions and `ChromeStrings` at `:246-315` are a natural `TrainerNaming.swift`, the Android file of that name already exists).
- `Watch/Sources/WatchModel.swift` — 329 (`practiceLap` + the run/queue half vs. sync/snapshot half).
- `kern/src/commonMain/kotlin/net/spross/kern/trainer/LetterDrillRun.kt` — 302.
- Backlog correction: `App/Sources/Model/AppModel+Listening.swift` is listed there as over 300 but stands at 292 — prune that entry (`wc -l`).
- Already listed and still over: Android `RunScaffold.kt` 287 is now UNDER 300 (prune too), `ListeningDriver.kt` 303, `ListeningService.kt` 306, kern `NumbersRun.kt` 332.
- Gate: none (splits), kern test for the Kotlin one. Confidence: verified (`wc -l`).

---

## Already in the backlog (not re-reported)
`DrillRunView`/`DrillSnapshot` wrapping its scaffold by hand (C1 adds the Android twin); Pronouncer `onFinish` parity (iOS `Reader.saidAnswer` polling, `Reader.swift:59-64`); watch practice-lap order and watch time-based grading (`WatchGrading`); `CountryDrillRun`/`DateDrillRun` near-duplicate reducers; `Presentation.kt` past-justifying KDoc; the duplicate-why scan; the >300 list (corrected in E).

---

## Digest
1. Parity break: iOS shows a Sprosse on the letter drill's score line, Android deliberately shows none (A1) — rule on it in kern, align one side.
2. Letters page re-mints the format order and the "format reachable" padlock rule on both phones; kern's `Report` should expose `formatOpen` (A2).
3. Reference captions (" · "-joined forms/people) and the lock-screen "Spross · mode / known – learned" lines are formatting rules written twice (A3, A4).
4. Milestone glyphs live on both phones while `DrillPauseReason.emoji` lives in kern — one ruling, not two (A5).
5. Widgets: display sort, bar/gutter geometry and the 🗂️ fallback are each minted on both phones (A6–A8); Android's widget recomputes the chrome language kern already ships (B1).
6. Both phones still read the typed drills through a hand-cut copy of `DrillRunProgress`, each carrying a dead `bestAnswerStreak`; iOS also copies `DrillRunSummary` into `DrillRunResult` (C1, C2).
7. Ten comments justify against the past, four cite a `DrillEffect.SayAnswer` kern never had, two call listening's `onFinish` "probe-only" (D1–D3).
8. Dead: three `KernBridge` extensions, two `hushAnswer()`s; seven copies of the uitest streak-seeding arithmetic (D4–D6).
9. iOS writes `0.25`/`0.2` animation literals at nine sites although `Animation.cardReveal` reads kern's `CardMotion` (A11).
10. Over 300 and unlisted: `TrainerHubView.swift` 315, `WatchModel.swift` 329, kern `LetterDrillRun.kt` 302; backlog's `AppModel+Listening.swift` and `RunScaffold.kt` entries are stale (E). Kern-only fixes here: A2/A3/A4/A5/A9 constants and accessors, B1's accessor, D5's helper; everything else needs a Mac or the Android gate.
