# Review D — platform tree and tooling hygiene
Read-only review of the iOS tree, the Android tree and the tooling around them for historical cruft; ranked by value within each package.
Gates: `kern jvmTest` is the only gate a cloud session can run; "Mac" means the Xcode build gate; "Android" means `:android:testDebugUnitTest` on a local checkout; "text" means a pre-commit/text check or none.

Method: every "unused" claim was searched with `rg` across all Swift trees, `android/src`, `kern/src`, `scripts/`, `.github/`, `.claude/`, `project.yml`, both `.xcstrings` catalogs and `docs/`; duplication claims were read in full at every site named.
Scripts were read, never run (so the chrome-table agreement below was checked by re-deriving the generator's field rule in a scratch script, not by running `scripts/chrome.py`).

## Package 1 — iOS tree (`App/`, `Watch/`, `Widgets/`, `WatchWidgets/`, `Shared/`, `project.yml`)

### 1.1 Two dead `SessionView` members, one with a `// why:` naming a reader that does not exist
- Evidence: `App/Sources/Screens/SessionView.swift:147-149`
  ```swift
  // why: internal, not private — the audio extension reads it to drop a
  // delayed word whose card has already gone.
  var currentCardID: String? { model.currentCardId }
  ```
  `rg -n currentCardID` over every Swift tree returns only this line; `SessionView+Audio.swift` reads `model.currentCard` directly.
  `App/Sources/Screens/SessionView+Turn.swift:155-162` `func askedByEar(_ card: Card) -> Bool` — no caller anywhere (kern's `TurnQuestion.kt` has its own private `askedByEar`; the Swift one is a leftover from before kern took the question).
- Why it matters: a `// why:` that describes a reader that is gone is the comment class CLAUDE.md forbids, and `askedByEar` carries a five-line rationale for a rule kern now owns.
- Fix: delete both members with their comments. Size S. Gate: Mac. Confidence: verified.

### 1.2 The DEBUG `seedAnswerStreak` hook is cut by hand at six sites and mints two numbers per site
- Evidence: identical `run.core.doCopy(done: Int32(answerStreak + 6), answerStreak: ..., bestAnswerStreak: Int32(max(answerStreak, 12)), missRun: run.core.missRun, outcomes: ..., solved: ..., slipped: ..., solvedClean: ..., pacing: ...)` at
  `OppositesView+Run.swift:71-87`, `WordScrambleView+Run.swift:80-`, `SentenceScrambleView+Run.swift:83-`, `LetterDrillView+Run.swift:91-106`, `CountryDrillFace.swift:139-155`, `DateDrillFace.swift:140-156` (the last two even keep a mis-indented `pacing:` line in common, which is how a paste travels).
- Why it matters: the `+6` / `max(…, 12)` seed shape is a value both platforms would compute if Android ever grew the same screenshot hook, and CLAUDE.md puts such a value in kern once; six copies of a nine-argument `doCopy` also break every time `DrillCore` gains a field.
- Fix: one `DrillCore.seeded(answerStreak:)` in kern (or, if it must stay DEBUG-only Swift, one `extension DrillCore` in `DrillRunning.swift`) and six one-line calls; the per-run outer `doCopy` stays since each run state differs. Size M. Gate: kern jvmTest if kern, Mac for the Swift side. Confidence: verified.

### 1.3 The rounded control surface (fill + hairline stroke) is cut fresh at five sites while `cardSurface()` exists for cards
- Evidence: the same `.background(RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous).fill(Theme.colors.surface)).overlay(RoundedRectangle(...).strokeBorder(Theme.colors.separator, lineWidth: 1))` pair at
  `Design/SearchField.swift:42-49`, `Screens/NumbersOverview+Challenge.swift:46-54`, `Screens/OwnEntrySheet.swift:93-99`, `Screens/OwnWordFormView.swift:267-273`, `Screens/ReportIssueSheet.swift:122-128`;
  `Design/Theme+Styles.swift:16` already defines `func cardSurface()` for the card variant.
- Why it matters: `scripts/card-parity.py` guards numbers, not composition; a fifth copy is how the next field gets a different corner.
- Fix: `func controlSurface() -> some View` beside `cardSurface()` in `Theme+Styles.swift`, five call sites. Size S. Gate: Mac. Confidence: verified.

### 1.4 The lock-toggle modifier row is written three times
- Evidence: `Screens/DrillOverview+Practice.swift:132-146` (`fastRow`), `Screens/NumbersOverview+Practice.swift:82-96` (`modifierRow`) — identical `Toggle { HStack { if !open { Image(systemName: "lock.fill")... } else { FadingPadlock(...) } Text(...).font(headline).foregroundStyle(open ? ... : ...) } }` bodies; the block scan also pairs `LettersOverview+Practice.swift:120` with both (7 lines).
- Fix: one `ModifierToggleRow(open:unlocking:title:isOn:)` in `Design/`, three call sites. Size S–M. Gate: Mac. Confidence: verified for the first two sites, likely for the third (seen by the scan, not read in full).

### 1.5 Four drill screens repeat the run-view scaffold the `DrillFace` screens already share
- Evidence: `OppositesView.swift:13-36` and `WordScrambleView.swift:18-42` are 20 identical lines (environment, `@State run/input/autoAdvance/reader`, `@FocusState`, the init); `OppositesView+Run.swift:22-45` and `WordScrambleView+Run.swift:31-50` repeat `typedControls` / `reduce` / `typedMove` / `submitMove` word for word but for the type names; `SentenceScrambleView+Run.swift` and `LetterDrillView+Run.swift` carry the same shape.
  Countries and Dates instead conform a `DrillFace` and let `DrillRunView<Face>` hold the scaffold once.
- Why it matters: the backlog item "The iOS drill run (`DrillRunView`) still wraps its scaffold by hand" names the generic run; it does not name that four screens never moved onto it at all, which is where the six duplicated `// why: internal, not private — the +Run extension…` comments (1.9) come from.
- Fix: give Opposites, WordScramble, SentenceScramble and LetterDrill a `DrillFace` each so they ride `DrillRunView`; a tiled face needs the face protocol to grow a tile-bank slot. Size L. Gate: Mac. Confidence: likely (the duplication is verified; whether the face protocol stretches to the tile screens without a second axis is the open design question).

### 1.6 Past-justifying comments in Swift
- Evidence (all quoted):
  - `Design/Sounds.swift:28-30` "they follow the ring/silent switch the way the system-sound route **used to** guarantee"
  - `Audio/PronunciationPlayer.swift:55` "Recovery **used to be** the NEXT word; it is this one."
  - `Design/AnswerInputView.swift:127-128` "the amber edge is the only thing left marking a reveal once the lightbulb **is gone**"; `:147-148` "the lightbulb that **used to sit here** read as a button it never was"; `:245` "a newline-only field **used to** offer Check and then meet an inert submit"
  - `Watch/Sources/WatchModel.swift:204-205` "a silent correct tap **used to** feel the same as no tap at all"
  - `Screens/TrainerHubView+Destinations.swift:147` "Clock, phrases and the alphabet are **no longer** surfaces of their own"
- Why it matters: CLAUDE.md "In Code comments, always only explain the current state of things, do not justify or illustrate behavior against the past".
- Fix: restate each as the current rule (e.g. Sounds: "the chimes follow the ring/silent switch"; AnswerInputView: "a reveal is marked by the amber edge alone, which a screen reader cannot see, so the state is spoken"). Size S. Gate: none (comments). Confidence: verified.

### 1.7 The backlog's over-300-lines list is stale on the iOS side
- Evidence (`wc -l`): backlog names `AppModel+Listening.swift` and `SessionView.swift` — now 292 and 246; not named but over: `Watch/Sources/WatchModel.swift` 329, `Screens/TrainerHubView.swift` 315, `Design/AnswerInputView.swift` 303, `Design/Theme.swift` 302, `Screens/BoxSettingsSection.swift` 301 (`AppModel.swift` 397 stays listed).
- Fix: swap the two names for the five in `docs/backlog.md` (rides along with whichever split lands first). Size S. Gate: text. Confidence: verified. (Already in backlog as a topic; the list itself is what is wrong.)

### 1.8 A DEBUG-only fixture ships in the Release watch app
- Evidence: `project.yml:170-171` adds `Watch/Fixtures` to `SprossWatch` resources unconditionally; its only reader is `WatchModel.swift:97 loadFixture()`, reached from the `#if DEBUG` `-uitest-snapshot` branch at `:74-92`.
- Why it matters: 3 KB of fake box in every shipped watch bundle, and a fixture a reviewer could mistake for live data.
- Fix: xcodegen `excludes`/config-conditional source or drop the fixture into the DEBUG-only asset path. Size S. Gate: Mac. Confidence: verified (small impact).

### 1.9 String-catalog classification: `a11y.verdict.wrong` sits in `ANDROID_ONLY` but Swift reads it
- Evidence: `scripts/strings.py:84` lists `'a11y.verdict.wrong'` under `ANDROID_ONLY` ("no Swift will ever ask for them"); `App/Sources/Screens/DrillChoiceGrid.swift:77 Text("a11y.verdict.wrong")` and `Screens/ScrambleTileBank.swift:135` both ask for it.
- Why it matters: `--built` unions `ANDROID_ONLY` into the emitted set, so the key is never reported "in the catalog, no longer in the code" even after Swift stops using it — the drift check is blind on exactly this key.
- Fix: delete the entry from `ANDROID_ONLY`. Size S. Gate: text (`scripts/strings.py --built` on a Mac confirms). Confidence: verified.
- Negative result: every other `Localizable.xcstrings` key (445) and `Glance.xcstrings` key (22) is referenced from Swift, is a declared `ANDROID_ONLY`/`COMPOSED` family, or is `preview.*` canvas scaffolding that `Theme.swift:247/278` does use; no key referenced from Swift is missing; no stale flags, no `%@` twins; `App/Resources` (five chimes, `silence.mp3`, `NowPlayingArtwork`, both icon sets) all have readers; `project.yml`'s `UIBackgroundModes: audio`, `applinks:spross.net` and the `spross` URL scheme all have handlers (`SprossApp.swift:24 onOpenURL`, `ListeningService`).

### 1.10 Word-for-word duplicated `// why:` comments (already in backlog — count refresh)
- Evidence: six groups in the iOS trees today, all boilerplate: "internal, not private — the +Run extension reads and drives it" ×3, "…arms and cancels it" ×5, "…+Practice.swift renders the ladder from it" ×2, "…marks the rows from it" ×2, and the two focus-hook pairs `OppositesView.swift:59/64` ↔ `WordScrambleView.swift:74/79`. Android has one: `QuestionCardReview.kt:40` ↔ `QuestionCard.kt:133`.
- The backlog line says "still stands at 46 groups"; it is 7 now, and five of the seven vanish with 1.5. Already in backlog; update the figure. Confidence: verified.

### 1.11 UI-test launch arguments: all live, one doc justifies against the past
- Every `-uitest-*` key in Swift (37 of them) has a reader; none of the keys named in `scripts/run-sim.sh` or `.claude/skills/verify/SKILL.md` is missing from Swift.
- `.claude/skills/verify/SKILL.md:88` "and why the old `-uitest-input`/`-uitest-submit` pair, which had to prefill before the prompt was known, is gone" documents a removal (CLAUDE.md "Do not document a removal"). Fix: cut the clause. Size S. Confidence: verified.

## Package 2 — Android tree (`android/`)

### 2.1 Dead `AppModel.pronounceTarget(form:)`
- Evidence: `android/src/main/kotlin/net/spross/app/SessionAudio.kt:42-46`
  ```kotlin
  /** Says [form] of the card in play on a tap, which is a request and passes both mutes. */
  fun AppModel.pronounceTarget(form: String) {
  ```
  `rg -n pronounceTarget android/src kern/src` returns only the definition; the screens use `sayOnTap`/`pronounceAction` instead.
- Fix: delete. Size S. Gate: Android (compile). Confidence: verified.
- Negative result: no other `main` declaration (function, property, class, composable) is unreferenced; every `res/drawable`, `res/font`, `res/values*`, `res/xml` entry has a reader; every manifest permission has a call site (`MODIFY_AUDIO_SETTINGS` is the backlog's open hardware question, `WAKE_LOCK`/`POST_NOTIFICATIONS`/`FOREGROUND_SERVICE*` are used by `ListeningService.kt`).

### 2.2 Two chrome fields are generated for Android but read by nothing — a gap the generator's own check cannot see
- Evidence: `Chrome.kt:528-529 val errorUnknownProfile / val errorResetFailed`, filled in `ChromeDe.kt:509-510` and `ChromeEn.kt:499-500`; no file outside the three `Chrome*.kt` names either. iOS shows both (`HomeView.swift:160,164`).
- Why it matters: `scripts/chrome.py` classifies every key as "a field reads it" / `IOS_ONLY` / `ANDROID_TODO` and promises that "what Android still owes is a list rather than a silence" — but a field that exists and is never read is exactly that silence. These are either copy Android owes (then `ANDROID_TODO`, and `ANDROID_TODO = set()` with its "empty is what caught up looks like" comment is wrong) or `IOS_ONLY`.
- Fix: decide which; and give `chrome.py`'s no-flag run a third check, "a declared field with no reader in `android/src/main` outside `Chrome*.kt`", which is text-only and runs in the cloud. Size S (classification) + S (check). Gate: text / Android for the copy. Confidence: verified.
- Chrome tables vs. generator: every non-`IOS_ONLY`, non-family catalog key camel-cases to a declared `Chrome.kt` field and the three `FAMILIES` series resolve, so the generated tables agree with the generator by construction; both tables carry the GENERATED header and no hand-edited field was found (the pre-commit hook and the release workflow diff them whole, so none could survive).

### 2.3 Past-justifying comments in Kotlin
- Evidence:
  - `Queries.kt:24-26` "asking [Catalog.availableTargets] about an undeclared locale THROWS, so a French or Italian phone **used to crash** on launch here."
  - `ui/Icons.kt:15-20` a whole paragraph on what the glyphs "**used to be**: `✕ › ✓ ✗` and the 🔊/🔇 emoji. Two things were wrong with that…"
  - `test/SessionRunWiringTest.kt:94-96` "where the app **used to** call any non-Again answer 'gefestigt'"; `:144-146` "Drift 1, the extra round: the app … **no longer** picks the composition itself"
  - `test/ui/HubRosterTest.kt:41` "the wiring a roster of parallel lists **used to** cross"
- Fix: keep the current rule, drop the before-picture (`Icons.kt` keeps its "one system: a 24 unit box, a 2 unit stroke…" paragraph). Size S. Gate: none. Confidence: verified.

### 2.4 Duplicated composables Android cuts by hand where iOS shares a piece
- Evidence:
  - Learner name field: `ui/BoxSettings.kt:200-215` and `ui/OnboardingScreen.kt:198-213` are the same `OutlinedTextField(singleLine, placeholder = chrome.settingsNamePlaceholder, KeyboardOptions(Words, autoCorrect off, Done), fillMaxWidth)`; iOS has one `NameField` (`LearnerNameRow.swift:26`, `OnboardingView.swift:178`).
  - Growable bottom sheet: `ui/BriefingSheet.kt:71-80`, `ui/OwnEntrySheet.kt:52-61` and `ui/CatalogMatchSheet.kt:~60-66` repeat `ModalBottomSheet { Column(fillMaxWidth) { Column(weight(1f, fill=false).verticalScroll().padding(lg), spacedBy(md)) {…} /* action outside */ } }`, with the `// why: the primary action stands OUTSIDE the scrolling half…` prose re-worded at each site — the "copy whose prose drifted" the backlog's duplicate-why line says a scan cannot catch.
- Fix: `LearnerNameField(value, onChange)` and `GrowableSheet(onDismiss, action: @Composable, content: ColumnScope)` in `ui/Components.kt` (253 lines; or a new file). Size M. Gate: Android. Confidence: verified.

### 2.5 `CountryDrillFlow.view()` / `DateDrillFlow.view()` are the same nine lines (adds to a backlog item)
- Evidence: `CountryDrillFlow.kt:63-71` and `DateDrillFlow.kt:82-90` build `TypedDrillView(index, sprosse, answerStreak, bestAnswerStreak, outcomes, tally, question)` identically.
- Already in backlog ("`CountryDrillRun` and `DateDrillRun` reduce near line for line"); the Android mapping is a third copy of the same seam and collapses with the shared reducer — worth naming on that line so the kern change takes the two flows with it. Size S (rides along). Gate: Android. Confidence: verified.

### 2.6 `Chrome.kt` is 570 hand-written lines, not in the backlog's over-300 list
- Evidence: `android/src/main/kotlin/net/spross/app/Chrome.kt` (interface, 413 fields); `ChromeDe.kt` 544 / `ChromeEn.kt` 535 are generated and exempt by nature.
- Decision rather than fix: an interface of fields is a table, and a split by surface (`ChromeSession`, `ChromeBox`…) would have to be mirrored in `scripts/chrome.py`'s `fields()` regex; naming it in the backlog's over-300 line as "exempt — generated-shape table" costs one clause. Size S. Confidence: verified (length), likely (that it should stay).

### 2.7 Tests asserting literal copy — nothing beyond the three the backlog names
- Checked every `assert*("…")` in `android/src/test`: the remaining literals are data (catalog words, file names, `"🇩🇪 Deutsch"` language names from the catalog), not chrome copy. Negative result.

## Package 3 — tooling and side trees

### 3.1 `scripts/rename-alternates.py` is a finished one-shot codemod that nothing runs
- Evidence: `rg -c '"(variants|synonyms)"\s*:' catalog` finds no key left; the script is referenced by no hook, workflow, doc or script (`rg -F rename-alternates.py` over the repo, hidden dirs included, returns nothing); its docstring argues for its own survival ("which is why it is a script and why the script stays: `--check` is what says the old names are gone") — a `--check` nothing invokes says nothing.
- Why it matters: 172 lines with a `KEEP` list of path globs (`kern/src/jvmTest/.../trainer/PhraseSlot…`) that will rot silently; CLAUDE.md "Do not document a removal or absence".
- Fix: delete. Size S. Gate: none. Confidence: verified.

### 3.2 Two more scripts that no hook, workflow, doc or runbook reaches
- `scripts/font-stress-anchors.py` (82 lines): patches `android/src/main/res/font/nunito.ttf` and offers `--check`; nothing calls it and `RUNBOOK-android.md` (which does document `scripts/android-icon.py` for the icon) does not mention it, so a Nunito refresh would lose the Ukrainian anchors with no gate saying so.
- `scripts/audio-restress.py` (109 lines): referenced by nothing; depends on an out-of-repo workbench (`$W/sync-from-shipped.py`, docstring line 15) that only `catalog/audio/README.md` describes.
- Fix: for the font, one line under RUNBOOK "Launcher icon" (or a pre-commit check when `nunito.ttf` is staged); for restress, either a line in `catalog/audio/README.md`'s pack workflow or delete. Size S. Gate: text. Confidence: verified (orphaned), likely (which fix).

### 3.3 Stale paths in tooling and the backlog
- `scripts/build-web.sh:2` "(docs/website.md)" — the file is `docs/plans/website.md`.
- `docs/backlog.md:33` "`docs/sync.md` plans a paid service" — the file is `docs/plans/sync.md`.
- `docs/2026-09-28-audio-quality-measure.md:7` and `…-tools.md:8` point readers to "Code: `/private/tmp/claude-501/-Users-tj-IT-duolernen-app/…/scratchpad/quality/`" — a session scratchpad that no longer exists; either the code is worth committing under `tools/` or the pointer goes.
- Fix: three one-line edits. Size S. Gate: text (`doc-header.py` does not check link targets). Confidence: verified.

### 3.4 `.claude/settings.json` regenerates the Xcode project at a hard-coded machine path
- Evidence: `.claude/settings.json:20` PostToolUse hook ends `cd /Users/tj/IT/duolernen/app && xcodegen generate`, while the SessionStart/UserPromptSubmit hooks in the same file resolve `r=$(git rev-parse --show-toplevel)`.
- Why it matters: the backlog's first item is a second worktree (`../app-website`); an edit there regenerates the MAIN checkout's project, and any other clone of the owner's gets a silent `cd` failure.
- Fix: `r=$(git rev-parse --show-toplevel) && cd "$r" && scripts/gen.sh` (which also brings the xcodegen cache the hook currently bypasses — `scripts/gen.sh:11` "a generation that bypassed it would leave it vouching for a project that is no longer on disk"). Size S. Gate: none. Confidence: verified.

### 3.5 The release workflow's "text lints" backstop runs four of the pre-commit hook's eight checks
- Evidence: `.github/workflows/release.yml:33-39` runs `catalog-format.py --check`, `strings.py --check-format` (Localizable only), `chrome.py`, `card-parity.py --check` and says it "is the backstop for what slipped past" the hook; `scripts/hooks/pre-commit` also runs `strings.py --check-format Shared/Resources/Glance.xcstrings`, `doc-header.py`, `audio-coverage.py --check` and `arch-status.py --check`.
- Fix: add the four lines. Size S. Gate: the workflow itself. Confidence: verified.

### 3.6 Past-justifying prose in gate scripts, hooks and the workflow — and one story told in two homes
- `scripts/hooks/pre-commit:52-53` "its rule lived only in CLAUDE.md — where 78% of catalog commits shipped off-layout anyway"; `:77-79` "nine of these divergences shipped and were corrected again, eight of them across a session boundary"
- `scripts/card-parity.py:7-9` the same nine-divergences story again ("nine of these divergences were introduced and corrected again over three weeks")
- `scripts/hooks/commit-msg:2-5` "Three commits carried it in one session for a card removal and a field rename…"
- `scripts/arch-status.py:5-6` "the atlas drill reached 386 lines of Kotlin and 264 of Swift that way"
- `scripts/chrome.py:5-8` "The two tables used to be kept in step by hand and had drifted on some fifty strings"
- `.github/workflows/release.yml:160-162` "an expired Apple credential or a macOS runner outage **used to** skip this job"; `:181` "the IPA is optional **now**"
- Fix: keep the rule, drop the anecdote; the card-parity story lives once (in the script docstring) if at all. Size S. Gate: none. Confidence: verified.

### 3.7 `web/site.js` still mints the numbers run policy kern now owns
- Evidence: `web/site.js:22 const MAX_LEVEL = 4`, `:147-170` page-side `level/streak/best/cleanAtLevel/hintUsed/locked/nudged` state, `:183-208` the sample/ramp/hint-gating loop; `kern/src/jsMain/kotlin/net/spross/kern/web/WebDrill.kt:33-35` says so in its KDoc: "Run policy — Sprosse ramping, streaks, hint gating — stays page-side, mirroring the app/kern split" — the split from before the unification, when kern had no `NumbersRun`.
- Why it matters: a third numbers run (after the two phones were collapsed onto kern's) with its own ramp; the JS target is otherwise alive (`scripts/build-web.sh` bundles `jsBrowserDistribution`, `web/index.html:165` loads `kern.js`, `kotlin-js-store/yarn.lock` is its lockfile).
- Already in backlog as the website-branch reconcile; this is the concrete thing to reconcile. Fix: expose `NumbersRun` through `WebDrill` and let `site.js` drive it. Size M–L. Gate: kern jvmTest (kern side), browser by hand. Confidence: likely (that the web run should ride kern's; the duplication itself is verified).

### 3.8 `.gitignore` entries nothing names
- `.dd/`, `.ddw/`, `marketing/print/preview/`: no script, doc, `build.py`/`check.py` or workflow writes or mentions them (`marketing/print/build.py` uses `out/` and `html/`). `.project-nowatch.yml`, `web/dist/`, `marketing/print/out|html/`, `local.properties` all have writers.
- Fix: drop the three, or name the tool that writes `.dd/`/`.ddw/` beside them. Size S. Confidence: likely (they may be a local tool's output that the repo never sees).

### 3.9 Negative results worth recording
- Every other script is reachable: `sibling.py`/`audio_gates.py`/`audio_measure.py`/`audio_voices.py` are imports; `commit-msg` and `pre-commit` are installed as a directory by `bootstrap.sh`; `ExportOptions-testflight.plist` is read by the workflow; `scripts/cloud/*` by `.claude/settings.json`; `xcode-progress.awk` by `run-sim.sh`/`deploy-devices.sh`; `catalog-rename-slugs.py`, `catalog-move.py`, `notes-vocabulary.py`, `audio-listen.py` by the catalog READMEs.
- `gradle/`, `settings.gradle.kts`, `gradle.properties` carry no dead entries; `kotlin-js-store/` is live (3.7); `marketing/print/` is self-contained and `kern`'s `PaletteParityTest` reads its `print.css`; `tools/FaceGen` is the known backlog item and nothing else sits in `tools/`.
- The three dated `docs/2026-*.md` files are research verdicts (rationale), not plans with work to do, so CLAUDE.md's "never a dated file in docs/" is a naming question for the owner rather than cruft; `docs/archive/` is where the other dated history sits.

## Digest
1. Dead Swift: `SessionView.currentCardID` (its `// why:` names a reader that does not exist) and `SessionView.askedByEar` — delete (S, Mac).
2. Dead Kotlin: `AppModel.pronounceTarget` in `SessionAudio.kt` — delete (S, Android); every other Android declaration, resource and permission has a reader.
3. The DEBUG `seedAnswerStreak` hook is pasted at six iOS sites, each minting `+6`/`max(…,12)` — one kern/Swift helper (M).
4. iOS cuts the control surface (5 sites) and the lock-toggle row (3 sites) by hand; Android cuts the name field (2) and the growable sheet (3) by hand — four small shared pieces (S–M).
5. Chrome fields `errorUnknownProfile`/`errorResetFailed` are generated for Android and read by nothing; `chrome.py` cannot see a declared-but-unread field — classify them and add that text check (S).
6. `scripts/strings.py` lists `a11y.verdict.wrong` as ANDROID_ONLY while two Swift files read it, blinding the drift check on that key (S).
7. `scripts/rename-alternates.py` is a finished codemod nothing runs — delete; `font-stress-anchors.py` and `audio-restress.py` are reachable from no doc, hook or workflow (S).
8. Tooling rot: `.claude/settings.json` regenerates Xcode at a hard-coded `/Users/tj/…` path; `build-web.sh` and the backlog point at `docs/website.md`/`docs/sync.md` that moved to `docs/plans/`; two research docs point at a vanished scratchpad; `release.yml`'s lint backstop skips four of the hook's eight checks (S each).
9. Past-justifying prose ("used to", "no longer", "nine divergences shipped…") at 7 Swift, 5 Kotlin and 7 script/hook/workflow sites, with the card-parity story told in two homes (S, comments only).
10. Backlog refresh: the iOS over-300 list is stale (drop AppModel+Listening/SessionView; add WatchModel 329, TrainerHubView 315, AnswerInputView, Theme, BoxSettingsSection), the duplicate-why count is 7 not 46, `web/site.js` is the concrete third numbers run the website reconcile owes, and `Watch/Fixtures` ships in Release.
