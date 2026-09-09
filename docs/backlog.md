# Backlog — session-discovered, out-of-scope issues

Issues discovered mid-session that fall outside the current scope:
file them here instead of scattering notes across other docs;
prune an item when it is fixed.
One item per bullet, with a file or context pointer, filed under the section it belongs to —
as short as that allows, longer only to carry evidence or reasoning a fixer would otherwise have to redo.
Within a section, ready work comes first, then the items that end in a question for the owner,
then open design work, then what waits on someone else, grouped by who that is.
Catalog content — its forms, its audio and the per-language questions — lives in `../catalog/backlog.md`.

## Engine & scheduling

- A short round that goes stale mid-run (the profile or catalog moves under it) recomposes as a
  full one, because `SessionIntent.RecomposeIfStale` reaches for `composeSession`, which knows
  nothing about which round was opened
  (`kern/src/commonMain/kotlin/net/spross/kern/session/SessionRun.kt` `recompose`).
- The 24-hour register closes the twelve-hour cycle by NUMBER (`achtzehn Uhr` cannot answer 06:00),
  a closure nothing holds, unlike the day parts' (`dayPartReadingsCloseTheTwelveHourCycle`).
- `EnglishClock` triplicates its own count/noun/direction derivation (`spelledMinutes:70-73`,
  `american:85-89`, `EnglishClockRegisters.anchors:58-72`) with `past` as `<= 30` in two of
  them and `< 30` in the third — the largest true duplication in the clock corpus.
- `<pack>.cardinal(-n)` returns the digits rather than a reading — the negative reading lives
  in `formReading` deliberately, so nothing needs it today, but a caller that assumes
  `cardinal` covers every `Long` gets a digit string back with no error.
- The letter drill's `exampleText` fallback is not audibility-filtered, so an inaudible
  escape-hatch row stays promptable and shows a dead speaker on both platforms
  (`kern/.../trainer/LetterDrillAvailability.exampleWords` KDoc, pinned in its test).
- Same class: kern's audibility test is "the catalog names a recording path OR a voice
  exists", never whether the file resolves in the bundle, so on a voiceless language a row
  or dictation candidate whose authored recording is missing ships promptable with a dead
  speaker (`kern/.../trainer/LetterDrillAvailability.kt`).
- The letter drill's dictation Sprosse is dealt on a device silenced by its own volume slider
  and has no "can't listen right now?" of its own (`LetterDrillAvailability.report` takes only
  `hasVoice`; `AudioSession.silenced` exists but is unread there), where
  `docs/read-aloud.md:139-144` rules the drill exempt from mute — does that exemption extend
  to a zero volume slider, or does the dictation Sprosse get its own way out?
- The number forms have no Sprosse for prices/currency or digit-by-digit readings (a phone
  number, a PIN), two families a learner meets constantly and the ladder never asks, each an
  enum case, a `draw` arm, a `formReading` arm per pack and a Sprosse row
  (`kern/src/commonMain/kotlin/net/spross/kern/trainer/NumberForms.kt`) — do both become
  Sprossen, and which first?
- `AnswerNormalizer.strayLeadingWordRecovery` tests the RAW leading token for letters, but its
  own example never peels ("it" is two edits from every en article, so "it is half past two"
  and "it's half past two" both grade Wrong today) — strike the bullet, or reword it to the
  real case, a spaced elided article ("l' acqua") refused where "la acqua" is read back, and
  release the elision ruling in `RealCatalogGradingTest.kt:44-48`?
- `UkrainianClock.gloss` (lines 159-173) rebuilds its candidates from `Forms` instead of
  selecting them out of `readings` the way es does, a third encoding of uk's minute grammar,
  but `ClockRevealTests` already sweeps every uk gloss alternative into `accepted`, so a
  `.filter { it in readings }` guard would only mask that gate — strike the bullet, or spend
  an M refactor collapsing the third encoding the es way and accept a re-curated reveal order
  (no uk golden gloss test exists)?
- A `clockAnchors` slot for midnight/noon on `TrainerLanguagePack` is deferred because, unlike
  the day parts, it would be a NEW authored copy rather than a removed one (de bakes them into
  early returns in `GermanClock.conversational`, en into `EnglishClockRegisters.anchors`, es
  and uk into hand-written `ClockReading` constants, sw has none by design) — still deferred,
  or drop the record and let git hold the deferral?
- A per-card **defer** flag that sinks a word behind the whole catalog instead of switching it
  off (unlike `setSuspended`, hard off until revived by hand), wanting a `BoxState` field (an
  intent, not a `GrowthStage` standing), a `BoxEngine` verb, a Box-screen affordance on both
  phones, and a sort ruling against `OwnWords.SEED_BASE`, which is already "behind every
  catalog concept".
- Automatic growth walks seed order (`Growth.newCandidates` step 2b), and seed order inside an
  area runs in co-hyponym clusters (kitchen: four appliances, then six utensils, then the
  cooking verbs), so a `NEW_CARDS_PER_ROUND` round lands inside ONE semantic set — the
  interference the literature finds is an INTRODUCTION effect on mutually substitutable
  same-class words (spoon/fork/knife) while a thematic area is neutral-to-helpful, so the fix
  spreads a round's new cards WITHIN the area across word class and sub-cluster, never across
  areas, and review is unaffected (once bound, contrasting near-neighbors is the useful case,
  and `promptAmbiguous`/`CatalogAnswerGrader.OtherWord` already teach those apart).
- Watch multiple-choice distractors carry no novelty or recency criterion
  (`kern/src/commonMain/kotlin/net/spross/kern/session/MultipleChoice.kt`): word class, area
  and shape rank them, but the newest entry can still be the odd one out — the class of
  problem the phone's due-order reshuffle fixed, on another surface.
- Watch snapshot 60-entry cap: due-first ranking keeps due cards on-watch, but revisit the cap
  if the active box outgrows it (`../kern/docs/snapshots.md`).
- Real hardware has to time the assembled dates accepted set, an uncapped cross-product graded
  on every keystroke (worst de Sprosse 6 ≈ 128 five-word forms per `evaluate`, typical ~16),
  on the oldest supported phone before it is trusted free (`DateDrillTasks.fill`;
  `NumberReadingIndex.INDEXED_CARDINALS` states the bound precedent).

## App & UX

- The letters ladder files no answered-out Sprossen (it has no storage key at all), so its
  circles carry only the entry mark where the atlas and calendar wear a record
  (`LettersOverview+Practice.swift`, `ui/LettersOverviewScreen.kt`) — should the tile and
  typed stages, which enumerate, file one and open above it too?
- Android's answer-field mark returns null for `TurnFeedback.Revealed` where iOS draws `.revealed` amber,
  against the `// why: correctness is never color alone` comment in the same two files
  (`android/.../ui/SessionTurn.kt`, `ui/DrillField.kt`).
- Widget and watch snapshots ship the raw article string (`kern/.../snapshot/SnapshotSupport.kt`
  `articleTint`, `Widgets/Sources/WordWidgetView.swift`, `Watch/Sources/WatchTheme.swift`), so fr/it
  `le` cannot take its hue there until the snapshot carries a gender (a `!` change).
- A drill's typed-answer controls (the field, the one primary action that reveals or checks,
  the amber hold, the revealed branch with its stop offer, the screen-reader "Weiter") stand
  verbatim in `TrainerSessionView+Drill.swift`, `LetterDrillView+Stages.swift` and
  `DrillRunView+Content.swift` with the live check wired per copy, so one component owning the
  branch and the `onChange(of: input)` beside it would make a fourth drill's auto-confirm
  structural rather than remembered.
- "Move noun class, word types and the tenses further back" — filed as a suggestion
  without a surface; the three are a card's Swahili plural/class grammar, its kind badge
  and the tense phrases' seed positions, which sit in three different places. Which one
  arrives too early for the owner: the card's own lines, or the order content unlocks in?
- Android still stores read-aloud as the boolean iOS calls its legacy key (`pronunciationMuted`,
  `android/.../audio/Pronouncer.kt:313`, against iOS's three-state `readAloud` in
  `App/Sources/Audio/AudioSession.swift`) and so has no `followsPhone` middle state, but on
  Android `followsPhone` and `on` cannot differ (USAGE_MEDIA ignores the ringer, deliberately)
  — store the enum for shape parity with a dead value, or rule the boolean the honest model
  and add one sentence to `docs/read-aloud.md:145-153` saying the third state is iOS-only?
- Nothing marks an unlock: a row silently stops being a padlock between openings
  (`App/Sources/Screens/NumbersOverview+Practice.swift:55-68`) and the full-screen ceremony
  was rejected for something that happens a handful of times — is a row-level transition or
  announcement wanted, and which?
- Rating labels carry more weight on a first exposure now that Hard/Good/Easy graduate
  immediately (`kern/docs/fsrs.md:26-27`) — should Knew it / Shaky / Not at all
  (`App/Sources/Design/RatingButtonsView.swift:52-56`, `Localizable.xcstrings`) say what they
  cost on a first meeting, or is the neutral wording right?
- The letter drill's typed and dictation stage has no live-check auto-advance the way vocab
  review and the trainer drills have (`App/Sources/Design/AutoAdvance.swift`) because its
  verdict ladder carries a third `heard` outcome, a synonym of the dictated word
  (`kern/.../trainer/LetterDrillRun.kt`) — does a `heard` verdict arm the live auto-advance
  beat, hold amber, or neither (the shared drill component above leaves a `typed` hook for it)?
- No automated visual-parity check exists between iOS and Android for shared, parity-bearing
  UI (cards, layout tokens), and `scripts/card-parity.py` closes 5 of the 9 historical
  divergences (numbers and primitive names, not rendering) — is a snapshot gate
  (Roborazzi/Paparazzi + swift-snapshot-testing or simctl diff, versioned goldens) worth its
  cost, or does this bullet narrow to the residual non-numeric class?
- Nothing gates a screen re-cutting a component that already exists: `card-parity.py`'s face
  and body lists are hand-kept, so a newly added file is never scanned (the 2026-09-03 drill
  choice grid was written, reviewed and merged unseen before `dade95ee` consolidated it).
  Copied `// why:` comments are the tell — measured 2026-09-07, 90 comment texts stand
  duplicated WITHIN one platform across 52 files — so a duplicate-comment scan would find
  them, but it needs those 90 baselined or cleaned first. Deriving the scanned set the way
  `LayerBoundaryTest` derives its enum list is the other half.
- The credits screen names the target-language word every bundled recording says and offers
  no way to hear one, since the row tap opens the file's Commons page
  (`App/Sources/Screens/CreditsView.swift` `fileRow`) and `Components.kt:255` /
  `SpeakerIcon.swift:6` forbid a per-row speaker — accept that credits rows do not play, or
  exempt this screen?
- The letter drill's choice Sprossen diverge: Android renders a correction line under the
  tiles (caption + correct form + speaker, `ui/LetterDrillStages.kt:37-53`), iOS bare tiles
  that already mark the answer (`LetterDrillView+Stages.swift:100-130`) — which is right?
- The watch reveal carries no "also means" line because `WatchEntryDto` ships `sourceText`
  alone (`WatchSnapshotBuilder.kt:221-235`), so a merged word teaches only the meaning of the
  card that was asked; the kern side is one field plus `SCHEMA_VERSION` 5→6, so where does
  the "auch: …" line live after a recognize tap — appended to the prompt line, below the 2x2
  grid, or a reserved slot — and does the 900 ms correct-advance hold longer when it is present?
- At accessibility XXXL a card with a long note grows past the bottom of the screen and takes
  the rating row with it, and nothing scrolls, so that card cannot be graded at all — the
  card's growth is unbounded by design (`Theme.swift` reserves a minimum, never a maximum), it
  is the row below that has nowhere left to stand, and the note fallback plus the long grammar
  notes make it likelier now.
- A row that speaks is a gesture on content rather than a control (`pronounceOnTap`,
  `App/Sources/Design/SpokenWord.swift`; `clickable` on Android), so VoiceOver reaches it as a
  named action while Switch Control and Full Keyboard Access, which scan for focusable
  controls, reach nothing — on every surface on that modifier: the reference rows,
  `BoxCardRow.swift`, the produce narration lines.
- `android/.../AppModel.kt` is past the ~300-line budget; extracting the Werkstatt doors
  needs `screen`'s `private set` (:202, and eight more backers) widened or an internal verb minted.
- Android's `NumberReferenceTable` renders every band eagerly inside one `verticalScroll` —
  fine at today's ~50 rows, revisit if a band grows (`android/.../ui/NumberReference.kt`).

## Platform reach

- `compileSdk` sits at 36 and holds androidx back — lifecycle 2.11 refuses to resolve below
  37 (`checkDebugAarMetadata`) and the next Compose BOM will follow — so the bump is one edit
  to `gradle/libs.versions.toml` once the android-37 platform is installed, plus a separate
  re-check of `targetSdk`, since compiling against 37 does not opt the app into its runtime behavior.
- Android surfaces still unported: `docs/design.md` § Not yet owns the list (couple mode,
  accounts/sync, chrome past de/en, no forest canvas or growth headline), and the `growth*`
  rows in `Chrome.kt:456-458` stand ready for a headline that needs `AreaTree`/`TreeTransition`
  lifted from `App/Sources/Design/ForestLayout.swift` into kern first — render it, or delete
  the rows and let `design.md`'s deferral stand?
- Portability move 6 (`snapshot/WatchRun` + public snapshot DTOs — the watch's own queue and
  ranking in `Watch/Sources/WatchModel.swift`, latency-to-rating in `Shared/Sources/WatchGrading.swift`,
  shortlist sampling in `Shared/Sources/WatchPracticeQuestion.swift`) was deferred 2026-08-08 —
  reopen it as a series now (kern engine + tests + both consumers + `SCHEMA_VERSION`), or keep it parked?
- Audio ships un-thinned: both installs copy all of `catalog/audio/` (129 MB, 13–25 MB per
  language — `project.yml:35` folder reference, `android/build.gradle.kts:131` asset sync with
  mp3/wav uncompressed), so a Swahili learner carries ~116 MB they can never hear, and
  per-language delivery (on-demand resources / Play asset packs) is the fix, measured per
  platform first.
- The paid Developer Program registration gates the `ios` job, which stops at
  `App Store Connect API key from secret`, so no release has carried an IPA, a release
  publishes the APK alone and iPhones are served by `scripts/deploy-devices.sh`.
- A live spross.net gates the iPhone install link: GitHub renders the release notes'
  `itms-services://` URL as code, not a tappable link (`.github/workflows/release.yml:229`),
  and a `web/install.html` taking `?v=` would make it a button (`docs/website.md`).
- The `website` branch (16 commits in the `../app-website` worktree, `docs/website.md`) is
  parked and will be picked up when wanted.

## Localization

- Watch, widget, and complication chrome is hardcoded German with no string catalog
  (`Watch/Sources/WatchHomeView.swift`, `Watch/Sources/WatchQuizView.swift`,
  `Widgets/Sources/WordWidgetView.swift`, `WatchWidgets/Sources/WatchWordWidgetView.swift`) and needs its own catalog plus a
  chrome-language field on the snapshot, since those surfaces never see `AppModel.knownLocale`.

## Verification gaps

- A fully correct typed answer carrying a matched synonym's own article demotes Exact→Typo
  because `AnswerNormalizer.evaluate` reads the leading article back against the card's single
  `grammar.gender` instead of the accepted form it actually matched
  (`kern/.../session/AnswerNormalizer.kt`), and Italian promotes many cross-article synonyms
  (la vaccinazione on il vaccino, il farmaco on la medicina, il salario on lo stipendio), so
  the reveal teaches forms the grader then punishes.
- `CatalogAudioLintTest` and `CatalogAudioFixtureTest` are both past the ~300-line budget
  and split cleanly: provenance/attribution rules apart from the playback
  index and the naming rules, lookup apart from parse in the fixture half.
- Android reads no state-seeding launch extra — iOS pins a drill screenshot with `-uitest-streak/-level/-misses`
  (`App/Sources/Screens/TrainerSessionView+UITest.swift`) while `SprossActivity` mirrors only `readAloud`,
  so a side-by-side check of `DrillStreakLine` and the tier beats on Android has to be played to by adb;
  `--es streak N` and its siblings in `SprossActivity.onCreate` would close it.
- No Swift test target (`project.yml` declares four app/extension targets only), so pure Swift logic is ungated —
  the widget streak walk and flame state (`Widgets/Sources/WidgetSnapshot.swift`), watch option assembly
  (`Shared/Sources/WatchPracticeQuestion.swift`), the sleep-timer minutes (`App/Sources/Model/ListeningBedtime.swift`)
  and the route-to-plane decision (`App/Sources/Audio/AudioSession.swift`) — add a `bundle.unit-test` target,
  or accept the build and kern gates as the iOS line?
- Real hardware still has to answer three things about the Android player
  (`android/.../audio/Pronouncer.kt`): how the boost and lead skip sound, one letter-drill run
  end to end, and whether `MODIFY_AUDIO_SETTINGS` is needed for a session-scoped effect.
- Real hardware has never seen watch pairing, and complication rendering was never
  screenshot-verified (no simctl affordance).
- Real hardware once: on the emulator with a hardware keyboard, Enter after `input text` could
  walk focus onto the session top-bar mute toggle and flip it, probably an emulator artifact
  (`android/.../ui/SessionScreen.kt` top bar).
- `tools/FaceGen` (`docs/facegen.md`) is parked and will be picked up when wanted.

## Compliance

- CC BY-SA §2(a)(5)(B) vs FairPlay needs a legal read before the FIRST submission
  (`docs/audio-licensing.md` § 6 item 1: 2094 of 3597 files, mitigation on record); items 2–3
  there are the es accent and Azure S0.
