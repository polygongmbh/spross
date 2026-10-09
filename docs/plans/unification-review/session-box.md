# Review A — session, box/home/settings, shared design primitives

Scope: iOS `App/Sources/{Screens,Design,Model,Store}`, Android `android/src/main/kotlin/net/spross/app/{*,ui/*}`,
kern `session/`, `model/`, `box/`, `store/`, `design/` — read whole, compared screen by screen.
Every claim below was checked with `rg` against `App`, `android`, `kern`, `Watch`, `Widgets`, `Shared`.
Gates: the cloud session runs only `./gradlew :kern:jvmTest`; every Swift change needs a Mac, every Compose change the Android unit gate on a Mac/Linux box with the SDK.

Ranked by value within each section.

---

## A. Rules, values and geometry computed on both platforms (or kern-exposed and ignored)

### A1. `grammar["gender"]` is the article's home in eight places, two of them platforms
**Evidence**
- `App/Sources/Model/DisplayText.swift:83-85` — `private static func article(of realization: Realization) -> String? { realization.grammar["gender"] }`
- `android/src/main/kotlin/net/spross/app/CardDisplay.kt:23` — `fun article(realization: Realization): String? = realization.grammar["gender"]`
- kern itself spells the key six more times: `box/HarvestForms.kt:58`, `box/Briefing.kt:186`, `box/CatalogMatches.kt:89`, `session/TurnQuestion.kt:94`, `session/AnswerNormalizer.kt:123`, `listen/ListeningRun.kt:197`, plus `snapshot/SnapshotSupport.kt:11` which already wraps it as `internal fun article(card: Card)`.
- `model/Card.kt:59` declares only `val grammar: Map<String, String>`; nothing on `Realization` names the article.

**Why it matters** The one fact "which grammar key carries the article" is a rule minted nine times; every platform read model (row citation, spoken article, badge tint) depends on it, and a second grammar convention (a language storing a gender name instead of the article) would have to be fixed in nine places.
**Fix** `val Realization.article: String? get() = grammar["gender"]` in `model/Card.kt` (or `Article.kt`); replace all nine sites; delete `SnapshotSupport.article`. Platforms then read `realization.article` and `CardDisplay.article` on both sides goes.
**Size** S. **Gate** kern test (existing `AnswerNormalizer`/`TurnQuestion` tests cover); the two platform swaps need Mac + Android gate. **Confidence** verified.

### A2. Summary hero ceiling share `0.45` minted on both phones
**Evidence**
- `App/Sources/Design/SummaryScaffold.swift:58` — `content(ceiling: geo.size.height * 0.45)`
- `android/src/main/kotlin/net/spross/app/ui/SummaryScaffold.kt:108` — `val ceiling = maxHeight * 0.45f` with its own `// why: a grown tree's box reaches 45 % of the screen …`
- kern `design/AreaTree.kt:97-101` `heroHeight(tree, ceiling)` already takes the ceiling but not the share.

**Why** A proportion both compute; the unification put every other hero number (`HERO_MIN`, `HERO_MAX`) into `AreaTree`.
**Fix** `const val HERO_SCREEN_SHARE = 0.45` on `AreaTree` (or `heroHeight(tree, screenHeight)` computing it); both scaffolds read it.
**Size** S. **Gate** kern test (`AreaTreeTest` if it exists; else none needed — a constant), Mac + Android for the two call sites. **Confidence** verified.

### A3. Reset-before-export gate `allSettledCount > 0` minted on both phones
**Evidence**
- `App/Sources/Screens/BoxSettingsSection.swift:230` — `guard (model.stats?.allSettledCount ?? 0) > 0, let target … else { confirmingReset = true; return }`
- `android/src/main/kotlin/net/spross/app/ui/BoxSettings.kt:145` — `if ((model.stats?.allSettledCount ?: 0) > 0) { resetExport.launch(…) } else { confirmingReset = true }`

**Why** "Progress worth backing up before a reset" is a box ruling (which count, which threshold); both screens decide it with a literal.
**Fix** `BoxStatistics.worthBackingUp: Boolean` (or on `BoxEngine`), one line, one test; both screens read it.
**Size** S. **Gate** kern test; Mac + Android for the swaps. **Confidence** verified.

### A4. Backup file name `Spross-<lang>-<day>` — three sites, not the backlog's two
**Evidence**
- `App/Sources/Screens/BackupRow.swift:154` — `name: "Spross-\(only.map { "\($0)-" } ?? "")\(day)"` (day = ISO `yyyy-MM-dd`)
- `android/src/main/kotlin/net/spross/app/ui/BackupSetting.kt:80` — `"Spross-${pick?.let { "$it-" } ?: ""}${LocalDate.now()}.json"`
- `android/src/main/kotlin/net/spross/app/ui/BoxSettings.kt:146` — `"Spross-${box.joinStamp.target}-${LocalDate.now()}.json"` (the reset path, Android-only third copy)

**Why** Already in backlog ("Both phones mint rules kern should name …"), but the bullet names two sites and misses Android's second one; the same bullet's other two items are RESOLVED (see D1), so it should shrink to this one with three pointers.
**Fix** `BoxBackup.fileName(only: Language?, dayIso: String)` in kern `store/`; three call sites.
**Size** S. **Gate** kern test. **Confidence** verified. *(already in backlog — new: third site + stale siblings)*

### A5. "Any word in the box can be heard" — two different algorithms
**Evidence**
- `App/Sources/Model/AppModel+Queries.swift:185-193` — short-circuits on kern's `AudioCapability` (`hasVoice → true`, `silent → false`), walks cards only in between.
- `android/src/main/kotlin/net/spross/app/ui/BoxScreen.kt:162-164` — `remember(catalog, box.cards) { box.cards.values.any { model.boxPronounceAction(it.target) != null } }` — always walks every card, never asks `AudioCapability` (which Android already has: `AppModel.audioSources`, `BoxSettings.kt:85`).

**Why** The rule ("the hint stands iff a device voice exists, or, with recordings only, some card has one") is written twice and differently; Android pays a full-box walk per box change that iOS avoids. Kern owns `AudioCapability` and `listeningOffered`; it could own this too, with the two platform facts handed in.
**Fix** kern `fun anyWordAudible(sources: AudioCapability, hasRecording: (Card) -> Boolean): Boolean` beside `listeningOffered` (catalog package); both platforms call it. Minimum fix: Android adopts the short-circuit.
**Size** S. **Gate** kern test (one case per branch); Mac + Android for callers. **Confidence** verified.

### A6. Question-card geometry written on both phones (emoji slot, reveal divider, replay glyph, verdict tile, field height, card reserves)
**Evidence (iOS → Android)**
- Emoji slot: `App/Sources/Design/CardReveal.swift:52-53` `diameter 52/96`, `glyph 28/52` → `android/…/ui/CardFace.kt:116-119` `EMOJI_SLOT 52.sp, EMOJI_GLYPH 28.sp, EMOJI_HERO 96.sp, EMOJI_HERO_GLYPH 52.sp`
- Reveal divider 44×2, corner 1: `CardReveal.swift:22-24` → `ui/CardText.kt:135-136`
- Replay glyph height 52 (and 88 wide): `App/Sources/Design/QuestionCardView.swift:183` `.frame(height: 52)`, `Theme.swift:73-76` "88 pt replay target" → `ui/QuestionCard.kt:272` `.size(width = 88.dp, height = 52.dp)`
- Verdict tile floor 60: `App/Sources/Design/RatingButtonsView.swift:90` → `ui/SessionTurn.kt:139` `VERDICT_TILE = 60.dp` ("The iOS tile's own floor")
- Answer field floor 56: `AnswerInputView.swift:114` → Android M3 default (56) — implicit, fragile
- Card reserves 144/120/72: `Theme.swift:72-80` → `ui/Theme.kt:210-222` ("the same roles and the same numbers")
- Activity strip: gutter 6 / rule 2.5 / corner 3: `ActivityStripView.swift:19,98,119` (`xs+2`, `cornerRadius: 3`, `height: 2.5`) → `ui/ActivityStrip.kt:43,46,138`
- Trees label glyph 13: `App/Sources/Screens/Trees.swift:77` → `ui/Trees.kt:105`

**Why** CLAUDE.md: "a shape's geometry … both would compute … lives in kern once"; the unification already moved `ActivityScale`, `CardSurface`, `SegmentsBar.gap`, `TreesLayout` into `design/`. These are the leftovers in the same family. `ui/Theme.kt:185` and `ThemeColors.kt:18` claim "spacing and the type ramp stay native", which licenses spacing/type sizes but says nothing about reserves, slots or control floors.
**Fix** one `design/CardGeometry.kt` (`EMOJI_SLOT`, `EMOJI_GLYPH`, `EMOJI_HERO*`, `REVEAL_RULE_WIDTH/HEIGHT`, `REPLAY_HEIGHT/WIDTH`, `VERDICT_TILE`, `FIELD_HEIGHT`, `RESERVE_DRILL/REVIEW/TILE`) and `ActivityScale` growing `gutter`, `ruleThickness`, `corner`; platforms convert units. Decide separately whether `Theme.spacing`/`Theme.radius` (identical 4/8/12/16/24 and 28/20/14 on both) join them — the docs say native, the invariant says once.
**Size** M (kern S, two platforms M). **Gate** kern none beyond compile; Mac + Android. **Confidence** verified (numbers); likely (that the ruling wants them in kern — `ui/Theme.kt` argues the other way for spacing).

### A7. Kern's `PressKind` is read by iOS only; Android presses nothing
**Evidence**
- kern `design/PressKind.kt` names scale per control kind and the spring (`RESPONSE`, `DAMPING`).
- `rg PressKind` — iOS: `Theme+Styles.swift:58-63` (`press(_:_:)`), `RatingButtonsView.swift:109`; Android: 0 files. Android verdict tiles, buttons and icon buttons use Material ripple only (`ui/SessionTurn.kt:107-118`, `Components.kt`).

**Why** The "one press across the app" rule exists in kern precisely so both match; Android ignores it, so the two apps press differently — the inverse of a leftover.
**Fix** a `Modifier.press(kind: PressKind)` in `ui/Motion.kt` using `responseSpring(PressKind.RESPONSE, PressKind.DAMPING)` and `interactionSource.collectIsPressedAsState()`, applied at the four control kinds. Or, if Android should keep Material's own press, say so in `PressKind`'s KDoc and `docs/surfaces.md` so the gap is a ruling and not a drift.
**Size** M. **Gate** Android unit (none needed for motion) — Mac/Android build. **Confidence** verified.

### A8. Commons file URL built on both phones
**Evidence**
- `App/Sources/Screens/CreditsView.swift:187-188` — `"https://commons.wikimedia.org/wiki/File:\(encoded)"` (percent-encoded, `.urlPathAllowed`)
- `android/…/ui/AboutScreen.kt:262-264` — `"https://commons.wikimedia.org/wiki/File:" + URLEncoder.encode(source, "UTF-8").replace("+", "%20")`
- kern `catalog/Pronunciation.kt:144-153` `AudioCreditFile` carries `source` with the KDoc "the credits screen links `File:<source>`" — the link rule is named in kern and built outside it.

**Why** A URL format rule written twice with two encoders (Swift's path-allowed set vs Java's form encoding) — the two phones can already differ on a name with `+` or `&`.
**Fix** `AudioCreditFile.commonsUrl: String` in kern, percent-encoding done once (kern has `fnv1a64`-style helpers; a small `percentEncode` is 10 lines); both screens open it.
**Size** S. **Gate** kern test (one name with a space and one with Cyrillic). **Confidence** verified.

### A9. Report's carried answer rule duplicated
**Evidence**
- `App/Sources/Screens/ReportIssueSheet.swift:82-84` — `learnerInput.isEmpty ? (onFileIssue?.learnerInput ?? "") : learnerInput`
- `android/…/ui/ReportIssue.kt:217-219` — `learnerInput.ifBlank { model.reportedIssue(card.id)?.learnerInput.orEmpty() }`

**Why** "An edit keeps the answer the first report rode in with" is a `Feedback` rule; both forms decide it, and already disagree on empty-vs-blank.
**Fix** `BoxEngine.reportIssue` keeps the existing `learnerInput` when handed a blank one (kern side, one line + test); both forms pass what they have.
**Size** S. **Gate** kern test. **Confidence** verified.

### A10. Own-word row text read models minted twice
**Evidence** (iOS `App/Sources/Model/AppModel+Feedback.swift` / `+OwnWords.swift` → Android `BoxActions.kt`)
- `suggestionText` 50-52 → 124-125 (`languages.first.flatMap { texts[$0] }`)
- `otherPairText` 56-58 → 131-132 (`languages.compactMap { texts[$0] }.joined(" → ")`)
- `catalogText` 159-162 → 238-241 (`"\(target.text) → \(source.text)"`)
- `writtenText` 165-171 → 244-248 (`[texts[target], texts[source]].compactMap.joined(" → ")`)
- report row line `"\(card.target.text) → \(card.source.text)"`: `BoxOwnContentSection+Lists.swift:132` → `ui/BoxOwnRows.kt:168`
- `otherPairFlags` 63-72 → 138-143 (same `languagesOutside` + flag join)

**Why** Six read models of `OwnWord`/`CatalogMatch`/`Card` ("target side first", which half a suggestion shows, which languages are outside the pair) re-derived per platform; `kern/docs/reports.md` owns the "target first" rule.
**Fix** `OwnWord.suggestionText`, `OwnWord.otherPairText`, `CatalogMatch.catalogText(state)`/`writtenText(stamp)` and `Card.citationLine` in kern (the ` → ` joiner is a formatting rule, not chrome — it is identical on both and in no string table).
**Size** S–M. **Gate** kern test. **Confidence** verified.

### A11. Prompt line limits by form differ
**Evidence**
- `App/Sources/Design/QuestionCardView+Words.swift:21-27` — sentence 4, name 3, else 1
- `android/…/ui/QuestionCardWords.kt:33` — sentence 4, else 1 (a name goes through `Headword`, `maxLines = 2`, `ui/CardText.kt:104`)

**Why** "How many lines a prompt may wrap" is decided per form on both and already disagrees for `Name` (3 vs 2).
**Fix** `Question.Form.lines: Int` in kern (a one-line `when`); both read it.
**Size** S. **Gate** kern none needed (a table); Mac + Android for the readers. **Confidence** verified.

### A12. Session coach caption and the Recognize double-check
**Evidence**
- `App/Sources/Model/SessionCoach.swift:22-24` and `android/…/SessionCoach.kt:28-29` both decide `role == Recognize && !revealed`; Android's caller re-checks the role a third time: `ui/SessionScreen.kt:85` `if (ui.role == PresentationRole.Recognize && model.coachActive)`.
- `gradeCaption`: `SessionView.swift:233-235` vs `AppModel.kt:181-182` — same rule (`coachActive ? coach line : standing question`).

**Why** Small, but it is a turn rule (which coaching line stands when) and `TurnState` already carries `role` and `revealed`; kern could answer `coachLine: CoachLine?` given `coachActive`.
**Fix** `TurnState.coachLine(active: Boolean): CoachLine?` enum (`Recognize`, `Grade`, `Write`, none) in `session/`; platforms word it. Drop the Android double-check either way.
**Size** S. **Gate** kern test. **Confidence** verified.

---

## B. Rules minted on one platform only, where the other has the same screen

### B1. iOS summary "burst hero" has geometry and timing Android cannot match
**Evidence**
- `App/Sources/Design/SessionSummaryView.swift:42-48` — six pieces `("🌱", -184, 78) …` with angles and radii; `swayAngle = 5 + i%3*2`, `swayPeriod = 2.1 + i*0.27`; springs `0.6/0.6`, `0.5/0.5`, delays `0.15 + i*0.06` (lines 153-166).
- `android/…/ui/SummaryScaffold.kt:162-165` — `SummaryGlyph` draws the 🎉 alone; `rg -n burst android/` → nothing.

**Why** An animation's geometry and timeline is exactly what the invariant says lives in kern once (`Confetti`, `TreeRise`, `CardMotion` already do). Today the no-tree summary differs visibly between phones.
**Fix** `design/SummaryBurst.kt` (pieces, angles, radii, sway angle/period per index, spring, stagger) read by both; Android draws it, or the owner rules Android keeps the plain glyph and the KDoc says so.
**Size** M. **Gate** kern none (a table); Mac + Android. **Confidence** verified.

### B2. iOS `voiceUpgradeBanner` / `VoiceUpgradeHint` — iOS-only by nature; fine
`HomeView.swift:119-149`, `SettingsAudioRow.swift:42-48`; `rg VoiceUpgradeHint android` → none. The system-voice download is an iOS fact; no finding, noted so it is not re-reported.

### B3. Android search debounce `SEARCH_SETTLE_MS = 120` vs iOS synchronous search
`ui/BoxSearchScreen.kt:260` vs `BoxSearchView.swift:56-59`. A platform performance affordance, not a rule — no fix asked; mention only because `120` is also the iOS focus-retry delay (`AnswerInputView.swift:261`) and the two could share a `design/Latency` home if either grows.

---

## C. Platform code re-deriving a kern read model

### C1. iOS `queueCard` re-mints `BoxEngine.queue`'s own guards
**Evidence** `App/Sources/Model/AppModel+Search.swift:21-26` — `guard box?.cards[cardID] != nil, scheduling(for: cardID) == nil, !isQueued(cardID)`; kern `box/BoxEngine.kt:118-123` `append(id)` already drops unknown, scheduled and already-queued ids. Android `BoxSearchScreen.kt:182` calls `BoxEngine.queue(it, listOf(card.id))` bare.
**Fix** drop the guard (and `isQueued`, which has no other caller — verify on a Mac). **Size** S. **Gate** Mac. **Confidence** verified.

### C2. Credits sections grouped by language on both phones
`App/Sources/Screens/CreditsView.swift:82-90` and `android/…/ui/AboutScreen.kt:103-109` both bucket `Catalog.audioCredits()` by first-seen language. **Fix** `Catalog.audioCreditSections(): List<CreditSection>` in kern. **Size** S. **Gate** kern test. **Confidence** verified.

### C3. Android `HomeCard` enum re-states `DayLead` with a failure case
`ui/HomeStanding.kt:15-38` — `homeCard(failed, lead)`; iOS does the same inline (`HomeView.swift:30-40`). Failure outranking the lead is reasonable platform state, but the Android enum is a second name for kern's `DayLead` with one extra case. Low value; fold into a `when` or leave. **Size** S. **Confidence** verified.

### C4. Android `AreaChip` spoken label vs iOS `.combine` — not a finding
Both read `AreaStatistics`; the spoken composition is a11y, which is platform-owned. Noted to avoid re-review.

---

## D. Historical cruft, stale rationale, dead code, past-tense comments

### D1. Backlog bullet is two-thirds resolved
`docs/backlog.md` § Ready › Engine: "Both phones mint rules kern should name: an own word's id side and trimming (`ui/BoxLogic.kt` `OwnWordDraft.word`), the shelf's 'fully queued and settled' (`BoxAreaSection.swift` `fullyQueuedAndSettled`, `ui/BoxSections.kt:120`) and the backup file name …".
- `rg fullyQueuedAndSettled App android kern` → no hits; both shelves now read kern's `ShelfControl` (`BoxAreaSection.swift:69`, `ui/BoxSections.kt:118`, `kern/box/ShelfControl.kt`).
- `ui/BoxLogic.kt:53-63` `OwnWordDraft.word` now delegates whole to `OwnWords.fromDraft`; iOS `AppModel+OwnWords.swift:114-123` likewise.
**Fix** prune the bullet to the backup name (A4), with three pointers. Rides along with whichever commit acts on A4. **Size** S. **Confidence** verified. *(already in backlog — materially new: stale)*

### D2. "Design is kern-free" is a stale rationale propping up five twin types
**Evidence**
- `App/Sources/KernBridge.swift:157-163` — "`App/Sources/Design` is kern-free by design, so every rule it renders arrives as one of its own value types … bar `AutoAdvance`, `NumberReferenceTable` and `SessionSummaryView`".
- `rg -l 'import SprossKern' App/Sources/Design | wc -l` → 27 of 41 files import kern (`Theme+Styles`, `ProgressComponents`, `CardFlip`, `AnswerArea`, `RatingButtonsView`, `CardReveal`, `QuestionCardView*`, `SessionScaffold`, `ActivityStripView`, `ConfettiView`, `GrowingTreeView`, `TreeArt`, `UnlockMark`, …).
- Twins kept only for that reason, each with a hand-written mapping in `KernBridge.swift`:
  - `SessionOutcome` (`SessionScaffold.swift:11-21`) ↔ `AnswerOutcome` + `SelfGrading.Verdict` (`KernBridge.swift:165-184`; `AnswerArea.swift:164` hands the self-grade through it)
  - `AnswerInputView.Feedback` / `.AlmostReason` (`AnswerInputView.swift:18-40`) ↔ `TurnFeedback`/`AlmostReason` (`KernBridge.swift:197-218`)
  - `Theme.Gender` / `Theme.Article` (`Theme.swift:176-191`, "so components stay kern-free") ↔ `Gender` (`KernBridge.swift:220-231`)
  - `AreaProgress` (`ProgressComponents.swift:14-28`, "so Design stays kern-free") ↔ `AreaStatistics` (`KernBridge.swift:95-98`)
  - `StageBadge.Stage` (`ProgressComponents.swift:158-160`) ↔ `ActiveStage` (`BoxCardRow.swift:243-250`)
- Android reads every one of these kern types directly (`AnswerArea.kt`, `AnswerField.kt`, `Components.kt:94`, `ProgressBars.kt:113`).

**Why** Five enums and two structs exist to serve a boundary that no longer exists; each is a mapping that can drift (the `LayerBoundaryTest` KDoc records that one such drift lasted thirteen days). `CardEmoji.Cue` (`CardReveal.swift:66-71`) already made the opposite call and says why.
**Fix** delete the twins, type the Design views on kern's enums (`AnswerOutcome`, `SelfGrading.Verdict`, `TurnFeedback`, `Gender`, `AreaStatistics`, `ActiveStage`), delete the `KernBridge` mappings and rewrite its header. `LayerBoundaryTest` keeps watching that the views only READ them.
**Size** M. **Gate** Mac. **Confidence** verified.

### D3. `CardType.LISTENING_REVEAL_DIVIDED` is a flag nothing turns off
`kern/design/CardType.kt:18` `const val LISTENING_REVEAL_DIVIDED: Boolean = true`; `CardReveal.swift:14` `var divided = true` and `CardText.kt:125` `divided: Boolean = true` exist only to be handed this constant (`QuestionCardView+Review.swift:91`, `QuestionCardReview.kt:145`). `askCase` (line 11) is the same shape with one value. CLAUDE.md: "feature flags or paths nothing reaches".
**Fix** delete the constant and the `divided` parameter on both platforms (the rule "every reveal has the divider" is then the code); or keep it only if a card without the divider is planned.
**Size** S. **Gate** kern test compile; Mac + Android. **Confidence** verified.

### D4. Past-tense justifications in my packages (CLAUDE.md: "never justify against the past")
- `App/Sources/Design/AnswerInputView.swift:146-148` "the lightbulb that used to sit here read as a button it never was"; `:245` "a newline-only field used to offer Check and then meet an inert submit".
- `kern/box/StabilityBars.kt:19` "A separate, faster `settledStability` of 2.0 used to gate presentation support".
- `kern/session/SessionRun.kt:112` "the machine both apps used to re-derive"; `:153` "it used to be composed by rules of its own"; `:230` "Cards coming due … used to be pulled"; `kern/session/SessionComposer.kt:109` "they used to have their own"; `kern/session/TurnMachine.kt:12` "used to re-derive, each drifting its own way".
- `android/…/Queries.kt:26` "a French or Italian phone used to crash on launch here"; `android/…/ui/Icons.kt:16` "used to be a character in a `Text`".
- `App/Sources/Design/SessionSummaryView.swift` / `BriefingSheet.swift:11-12` and `ui/BriefingSheet.kt:49` "the counts that once stood in for the text".
(The backlog already names `Presentation.kt`'s KDoc; these are additional.)
**Fix** rewrite each to state the current rule; drop the sentence where the rule is already stated. **Size** S. **Gate** none (comments). **Confidence** verified.

### D5. iOS reduce-motion / fold duration `0.2` is one literal at nine sites
`SessionView.swift:223`, `SessionView+Turn.swift:60`, `QuestionStage.swift:24`, `DrillRunning.swift:139` (`reduceMotion ? .easeOut(duration: 0.2) : .cardFlip`), `BoxView.swift:151,164`, `BoxAreaSection.swift:34`, `OnboardingView.swift:140,206` (`.easeInOut(duration: 0.2)`). Android animates none of these folds and uses the animator scale for reduced motion.
**Fix** one `Animation.cardFlipReduced` / `Animation.fold` next to `cardFlip` in `CardFlip.swift`; if the fold timing should match Android some day, it belongs in `CardMotion`. **Size** S. **Gate** Mac. **Confidence** verified.

### D6. Dead or near-dead iOS symbols
- `Theme.typography.badge` (`Theme.swift:94`) — only read by the `#Preview` in the same file (`:259`); `rg 'typography\.badge' App` → no other hit. Android's ramp still names a "badge" slot (`ui/Theme.kt:164`) for it.
- `LanguageNames.native` (`DisplayText.swift:15-17`) — an alias that "resolves to the same one word", three callers; collapse onto `display`.
- `SettingsAudioRow.sources(_:)` (`SettingsAudioRow.swift:54-58`) duplicates `AppModel.audioSources(_:)` (`AppModel+Queries.swift:166-170`) line for line.
- `SessionScaffold.swift:1,3` imports `SprossKern` twice.
- Android: `Queries.kt:48-49` builds `AreaNaming(…, ownSubtitle = null)` while `ui/BoxScreen.kt:82` builds a second one with `chrome.boxOwnWordExplainer` as the own shelf's subtitle — nothing draws the own area as a shelf (`BoxItem.OwnContent`), so the explainer argument is dead and the two instances disagree; iOS passes `nil`.
- `android/…/ui/SessionTurn.kt:162-165` `AppModel.targetName(ui)` has one caller (`ReviewAnswer.kt:31`) and reads the card's target lang where `flow.state.card` already holds it; fold into `answerName`'s neighbor or inline.
**Size** S each. **Gate** Mac / Android. **Confidence** verified.

### D7. Own-word quick-pick wash uses a literal alpha
`App/Sources/Screens/OwnWordFormView.swift:221-223` — `Theme.colors.accent.opacity(0.18)` for the picked emoji; every other tinted pill reads `Palette.WASH` (0.14) and Android's picks use `secondaryContainer` (= `wash(teal)`). One literal, two platforms, three looks. **Fix** `Palette.WASH` on iOS, `wash(accent)` on Android. **Size** S. **Gate** Mac + Android. **Confidence** verified.

---

## E. Files over ~300 lines not already in the backlog
(backlog lists: Android `AppModel.kt`, `RunScaffold.kt`, `Pronouncer.kt`, `ListeningDriver.kt`, `BoxSections.kt`, `ListeningService.kt`; iOS `AppModel.swift`, `AppModel+Listening.swift`, `SessionView.swift`; kern `AnswerNormalizer.kt`, `NumbersRun.kt`, `SessionRun.kt`, `Catalog.kt`)
- `android/…/Chrome.kt` 570 — a hand-declared interface, not generated (the KDoc says the two tables are; the interface is not). D2-style split by surface (`ChromeSession`, `ChromeBox`, …) would also let `ChromeTableShapeTest` name the missing field's surface. Sizeable; owner decision.
- `kern/box/BoxEngine.kt` 319 — not in the backlog's kern list; own-word/feedback verbs (`addOwnWord`, `updateOwnWord`, `removeOwnWord`, `mergeOwnWord`, `reportIssue`, `dismissReportedIssue`, `markExported`, `clearFeedback`) are a natural `BoxEngine+Own.kt` split.
- `App/Sources/Design/AnswerInputView.swift` 303, `App/Sources/Design/Theme.swift` 302, `App/Sources/Screens/BoxSettingsSection.swift` 301, `kern/session/TurnMachine.kt` 300 — at the line; D2 shrinks `AnswerInputView` and `Theme` below it.
- Counts here differ by a line or two from the backlog's (`AppModel.kt` 453 vs 452, `AnswerNormalizer.kt` 398 vs 375, `AppModel.swift` 397 vs 398): refresh the numbers when the bullet is next touched.

---

## Digest (10 lines)
1. `grammar["gender"]` is the article's home in 9 places (2 platforms + 7 kern) — one `Realization.article` in kern; kern-only fix plus two one-line swaps (A1).
2. Five iOS "Design twins" (`SessionOutcome`, `AnswerInputView.Feedback`, `Theme.Gender`, `AreaProgress`, `StageBadge.Stage`) serve a "Design is kern-free" rationale that 27/41 Design files already break — delete them with their `KernBridge` mappings (D2, Mac).
3. Summary hero share `0.45`, reset-export gate `allSettledCount > 0`, backup file name (3 sites, backlog says 2), Commons URL, report's carried answer, own-word row texts: each a rule both phones mint, each a small kern home (A2–A4, A8–A10).
4. Card geometry still written twice: emoji slot 52/28/96/52, reveal rule 44×2, replay 88×52, verdict tile 60, field 56, reserves 144/120/72, strip gutter 6 / rule 2.5 / corner 3 — a `design/CardGeometry` plus `ActivityScale` fields (A6; `ui/Theme.kt` argues spacing/type stay native, decide where the line is).
5. Kern's `PressKind` is read by iOS only; Android has no press at all — match it or rule the gap (A7).
6. iOS summary burst (6 pieces, angles, radii, sway, springs) has no kern table and no Android twin (B1).
7. "Any word audible" is two different algorithms (iOS short-circuits on `AudioCapability`, Android walks every card); kern should own it beside `listeningOffered` (A5).
8. Backlog bullet on minted rules is two-thirds resolved (`ShelfControl`, `OwnWords.fromDraft` shipped) — prune to the backup name (D1).
9. `CardType.LISTENING_REVEAL_DIVIDED` is a `true` nothing flips; nine past-tense comments across kern/session, StabilityBars, AnswerInputView, Queries.kt, Icons.kt; `typography.badge`, `LanguageNames.native`, `SettingsAudioRow.sources`, a duplicate import and a dead Android `ownSubtitle` (D3–D7).
10. Over-300 not in backlog: `Chrome.kt` 570, `BoxEngine.kt` 319; three iOS files and `TurnMachine.kt` sit exactly at the line (E). Kern-only fixes: A1 (core), A3, A4, A8, A9, A10, A11, A12, C2 (each needs its callers swapped on a Mac later).
