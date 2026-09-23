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

- kern's audibility test is "the catalog names a recording path OR a voice
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
- Watch snapshot 60-entry cap: due-first ranking keeps due cards on-watch, but revisit the cap
  if the active box outgrows it (`../kern/docs/snapshots.md`).
- Real hardware has to time the assembled dates accepted set, an uncapped cross-product graded
  on every keystroke (worst de Sprosse 6 ≈ 128 five-word forms per `evaluate`, typical ~16),
  on the oldest supported phone before it is trusted free (`DateDrillTasks.fill`;
  `NumberReadingIndex.INDEXED_CARDINALS` states the bound precedent).

## App & UX


- The listening drill deals its words in an order nobody tuned for audibility: the owner expected
  the first words to come in catalog order and heard them skip, because seeding is pure catalog
  order (`Growth.kt`) with no audibility term, so a silent card takes its slot and the run sounds
  shuffled. Either the drill's queue prefers cards a voice can actually say, or the ordering
  expectation is wrong and the drill says so — a ruling, not a bug.

- The letters ladder files no answered-out Sprossen (it has no storage key at all), so its
  circles carry only the entry mark where the atlas and calendar wear a record
  (`LettersOverview+Practice.swift`, `ui/LettersOverviewScreen.kt`) — should the tile and
  typed stages, which enumerate, file one and open above it too?
- Widget and watch snapshots ship the raw article string (`kern/.../snapshot/SnapshotSupport.kt`
  `articleTint`, `Widgets/Sources/WordWidgetView.swift`, `Watch/Sources/WatchTheme.swift`), so fr/it
  `le` cannot take its hue there until the snapshot carries a gender (a `!` change).
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
- The letter drill needs a Check tap where `docs/design.md` rules that finishing the word IS the
  answer, because `LetterDrillIntent` has no `InputChanged` — the one thing `DrillRunning` could
  not carry (`typedMove` returns nil there alone). A new intent plus a live verdict in
  `LetterDrillRun` closes it, and its ladder carries a third `heard` outcome the other drills
  have no arm for: does `heard` arm the beat, hold amber, or neither?
- No automated visual-parity check exists between iOS and Android for shared, parity-bearing
  UI (cards, layout tokens), and `scripts/card-parity.py` closes 5 of the 9 historical
  divergences (numbers and primitive names, not rendering) — is a snapshot gate
  (Roborazzi/Paparazzi + swift-snapshot-testing or simctl diff, versioned goldens) worth its
  cost, or does this bullet narrow to the residual non-numeric class?
- A duplicate-`// why:` scan earns a ranked report, never a commit gate: it reads files that
  duplicate a COMMENT, so a copy whose prose drifted is invisible — it missed two scramble
  screens, a second `DrillBeat` in `TurnFlow`, a third reference sheet in `NumberReferenceTable`
  and a panel cut by hand at 15 sites — and still stands at 46 groups after six clusters shipped.
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
- At accessibility XXXL the iOS session card breaks a headword inside a word ("Gute" / "n" /
  "Tag!") rather than between words (`VocabCardView.swift` `headline`, `minimumScaleFactor(0.85)`).
- Android's `NumberReferenceTable` renders every band eagerly inside one `verticalScroll` —
  fine at today's ~50 rows, revisit if a band grows (`android/.../ui/NumberReference.kt`).
- Compound/morpheme-boundary training for a compounding language (marking the component seams
  inside a German compound, the way Leichte Sprache's mediopunkt does) is a distinct unbuilt
  drill needing curated component-boundary data, and syllable data would not deliver it since a
  syllable split cuts through a stem rather than landing on a seam ("Fei-er-tag" buries
  "Feier") — considered for word scramble (`drills-words.md`) and left out.

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
- A playback noise gate would quiet the steady hiss of the noisier recordings (Kampy's, most of the lower German `snr`) the way `gain` levels them: a per-file threshold from `audio_measure.noise_margin` in the manifest, applied by iOS's dynamics-processor audio unit and Android's `DynamicsProcessing` noise gate, never by editing the bytes.
- No release has carried an IPA yet: the `ios` job needs the App Store Connect secrets
  (`docs/distribution.md` § Secrets) present to get past `App Store Connect API key from
  secret`, and iPhones are served by `scripts/deploy-devices.sh` until a run has published one.
- A live spross.net gates the iPhone install link: GitHub renders the release notes'
  `itms-services://` URL as code, not a tappable link (`.github/workflows/release.yml:229`),
  and a `web/install.html` taking `?v=` would make it a button (`docs/website.md`).
- The `website` branch (16 commits in the `../app-website` worktree, `docs/website.md`) is
  parked and will be picked up when wanted.
- The repo grants nobody anything — there is no `LICENSE` file, while `docs/sync.md` plans a
  paid service around a free and open app; the options and what constrains them are
  `docs/source-license.md`, and the decision is the owner's.

## Localization

- Watch, widget, and complication chrome is hardcoded German with no string catalog
  (`Watch/Sources/WatchHomeView.swift`, `Watch/Sources/WatchQuizView.swift`,
  `Widgets/Sources/WordWidgetView.swift`, `WatchWidgets/Sources/WatchWordWidgetView.swift`) and needs its own catalog plus a
  chrome-language field on the snapshot, since those surfaces never see `AppModel.knownLocale`.

## Verification gaps

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
