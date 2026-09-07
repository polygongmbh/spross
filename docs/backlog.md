# Backlog — session-discovered, out-of-scope issues

Issues discovered mid-session that fall outside the current scope:
file them here instead of scattering notes across other docs;
prune an item when it is fixed.
One item per bullet, with a file or context pointer, filed under the section it belongs to —
as short as that allows, longer only to carry evidence or reasoning a fixer would otherwise have to redo.
Within a section, ready work comes first, then the items that end in a question for the owner,
then open design work, then what waits on someone else, grouped by who that is.

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
- `android/.../AppModel.kt` sits at 949 lines (guide ~300); extracting the Werkstatt doors
  needs `screen`'s `private set` (:202, and eight more backers) widened or an internal verb minted.
- Android's `NumberReferenceTable` renders every band eagerly inside one `verticalScroll` —
  fine at today's ~50 rows, revisit if a band grows (`android/.../ui/NumberReference.kt`).

## Platform reach

- `compileSdk` sits at 36 and now holds androidx back — lifecycle 2.11 refuses to resolve below
  37 (`checkDebugAarMetadata`) and the next Compose BOM will follow — so bumping needs the
  android-37 platform installed and a separate re-check of `targetSdk`, since compiling
  against 37 does not opt the app into its runtime behavior.
- Android surfaces still unported: `docs/design.md` § Not yet owns the list (couple mode,
  accounts/sync, chrome past de/en, no forest canvas or growth headline), and the `growth*`
  rows in `Chrome.kt:456-458` stand ready for a headline that needs `AreaTree`/`TreeTransition`
  lifted from `App/Sources/Design/ForestLayout.swift` into kern first — render it, or delete
  the rows and let `design.md`'s deferral stand?
- Portability move 6 (`snapshot/WatchRun` + public snapshot DTOs, `docs/portability.md` § Moves)
  was deferred 2026-08-08 — reopen it as a series now (kern engine + tests + both consumers +
  `SCHEMA_VERSION`), or keep it parked?
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

## Content & catalog

- 778 of 943 catalog notes are de-only on a non-German target (eo 108, es 147, fr 182, it 181,
  sw 125, uk 35, plus 3 es and 2 en phrase frames in `catalog/phrases/*.json`), and each is
  to be rewritten in the target's own language, example-first («мама → мамо, тато → тату»
  teaches the vocative to a learner who could not yet read the word for it), so that it
  reaches every reader via `notes[source] ?: notes[lang]` (`kern/docs/catalog.md`) as
  `catalog/areas/README.md` § How a grammar rule gets taught ("Write the note in the language
  it explains") rules — sw is the largest side (6 frame + 119 word notes, three of them the
  concord-by-note pattern and one pure etymology that README cuts) and, with eo, wants a
  reviewer who reads it.
- de accepts no bare hour word ("Es ist acht.") though the German is right; with the drill's
  stray-word rescue gone it is safe to add, but it wants its own sweep run.
- `time` has no `midnight` though the clock reveal teaches it at 00:00 beside `noon`
  (`docs/clock-registers.md` § English a.m./p.m.) — a clean add.
- `qualities` is 47 concepts, past the ~40 line (`catalog/areas/README.md` § which area a
  concept lives in); the seam is `comparison` — different, difference, same, similar,
  opposite, real, to-compare — carried by `scripts/catalog-move.py --create`.
- `rufen` → sw `kuita` has no card: en/es/fr/it say one word for calling and phoning (to call,
  llamar, appeler, chiamare — all already `desk/to-call`), so a second card would author one
  meaning twice (`CatalogLintTest.noConceptPairCollidesInTwoLanguages`); `desk/to-call` would
  have to be re-cut to phone/telefonear/téléphoner/telefonare first.
- `verbs/to-deliver` liefern ↔ sw `kupeleka` mirrors hinbringen (take somebody or something
  somewhere), not liefern; an honest re-cut touches all eight languages.
- uk `the-exam-is-already-corrected` is built on `перевірити` (check) while the `to-correct`
  component it unlocks from is `виправляти` (fix errors) — phrase and gate name different verbs.
- The gap sweep counts glyph occurrences with no longest-glyph-wins, so a glyph nested in a
  longer row's glyph sweeps in that row's words (fr `au` would gap the a-u inside 13 `eau`
  words — bateau, beaucoup …); fr `au` opts out via `mine: false` meanwhile, and an
  engine-side exclusion would win its honest pool back (`Catalog.alphabetExamples`).
- 58/212 phrases (27%) carry no `components`: 20 are greetings, component-free by design
  (`catalog/README.md:127`), 9 gained theirs in 2db13420, and ~29 (`im-tired`, `wash-your-hands`,
  `i-love-you`, …) have no same-area word to gate on — author the missing word in eight languages,
  move the phrase via `scripts/catalog-move.py`, or declare it a building block?
- es has no `morning`, `afternoon` or `late` and uk no `afternoon` because `mañana` and `tarde`
  already realize `time/tomorrow` and `time/evening` (uk simply has no plain noun for the
  afternoon), one form for two cards inside one area being the collision the lint calls
  unfixable at runtime, so Spanish meets the morning only as `de la mañana` in
  `time/nine-am-sharp` — accept the gap, allow one form on two cards in one area (a lint
  change), or move one of each pair to another area?
- The es review queue — 20 lowest-confidence picks (`admin/office`, `work/leave`,
  `kitchen/reheat`, `bedroom/cuddle` lead it), ~24 medium flags, 49 per-area disputes, the
  author's choice standing in the JSON in every case — waits on a native es-ES speaker
  (`data/orchestration/audio-langs-2026-07/es-content/final/REPORT.md` §5 A first, then the
  seven `drafts/*-notes.md` "Review disputes"; method `../../docs/sprachposter-learnings.md`),
  while five cross-cutting rulings stay with the catalog owner (REPORT §5 C: C2 `desk/internet`
  ships without `gender`, C3 cross-gender synonyms under the wrong article tint, C6 `variants`
  as a demotion bucket, C7 Tatoeba-verbatim strings, C8 cross-area accept-set overlaps) — rule
  on each, or delegate them to the native reviewer with the disputes?
- 38 open questions on `catalog/alphabet/{uk,de,es}.json` wait on a native sweep — 12 uk («йот»
  vs «ий», the в allophony and о raising wording, the ч/щ anchors, ґудзик as `exampleText`),
  11 de (s→/z/ and -ig as the variety anchor, the English respellings, the ẞ policy, the
  Vau/We/Jot/Zett/Eszett strings against the de-DE voice), 15 es (the ll/y merger, which jota,
  the vanishing final d, /s/ in the after-l-n-s trill rule, 2026 letter names), each written up
  in `data/orchestration/audio-langs-2026-07/alphabet-drafts/*-notes.md`, the es file teaching
  es-ES throughout so an es-MX voice contradicts the c/z rows out loud (every LatAm divergence
  is in `es-notes.md`), and the 33 uk letter clips (`catalog/audio/uk/manifest.json` `letters`,
  incl. ʼ) never heard against the names the file speaks, where a clip that says something else
  changes the `name` FIELD, never the audio (no lint pins a letter clip to its name by design,
  `CatalogAudioLintTest.kt:187`) — and es's two scope calls are the owner's: do the en-only
  vowel rows i/e/u (Q13) stay, and does a written-accent row (Q14) belong in an alphabet file?
- `food` holds 47 concepts, `desk` 42 and `admin` 41, all past the ~40 line
  (`catalog/areas/README.md` § which area a concept lives in), and `food`'s seam is raw
  ingredients against meals and drinks — name the new shelf and its members, or accept the
  three past the line?
- Italian dates: the dayMonth reveal teaches `il otto`/`il undici marzo` on 2 of 31 days
  because a static pattern cannot elide, though the elided `l'{day} {month}` already grades as
  a variant (`catalog/dates/it.json`) — elision-aware date patterns in the engine, or wait for
  an Italian native's ruling on `il otto` vs `l'otto` and the weekday article
  (`docs/date-readings.md:88`)?
- `CountryAtlas.notes` parses and reaches no screen, the shape the dates calendar just
  answered with a calendar-level `dateNotes` band (`catalog/dates/README.md`) — render the
  atlas' on its reference row, drop the field, or give the atlas page its own prose band?
- `life-death` ships 5 concepts and `people/to-be-born` is the obvious sixth, but moving it
  thins `people`'s family block — does this stay a watch note, or go until a seventh candidate
  (`funeral`? `grave`? `to-grow-up`?) turns up?
- Voice consistency varies by pack (sw and uk one speaker throughout, de mostly Jeuwre with a
  Lingua Libre remainder, es a crowd of Lingua Libre speakers in twenty credit groups with no
  stated variety, `data/reference/audio/pack-es/ATTRIBUTION.md` carries the accent caveat),
  and the settled answer is a quality floor rather than more voices (only 18% of German and
  29% of Spanish words have a second voice on Commons, uk and sw effectively none, usually the
  noisier take) — does that decision stay here, or move into `ATTRIBUTION.md` as the ruling
  and leave the backlog?
- The catalog-wide gap sweep reaches only plain digraphs, so two gaps stay one word deep:
  Ukrainian gains nothing (33 of its 35 rows are `letter` rows asked by spoken name, and it
  authors no digraph to sweep), and every position-bound row (de `ch`×3, `s`×2, the
  final-devoicing trio, `er`; es `c`, `g`, `gu`, `r`, `d`) rides its one authored example
  because `context` is prose keyed by the reader rather than a rule the engine can test — a
  machine-readable environment field would open both.
- No field carries Ukrainian stress, which is unmarked in writing and load-bearing (учень,
  миша and одяг teach their vowel only if the sheet can show which syllable carries it); a
  `stress` field on realizations (`catalog/README.md`) is the shape the pronunciation plan
  proposed, and the alphabet table cannot teach it in the plan's place.
- Alphabet hints and contexts carry de+en only (`catalog/alphabet/*.json`), so a sw- or
  uk-reading learner — both already selectable as source — meets unhinted rows, and each
  needs its own hint pass rather than a translation of the English: the German pivot prose
  for de is parked in the drafts' notes, while sw needs authoring from scratch (sw `j` is /ɟ/,
  so the en "y in yes" anchor is wrong).
- Swahili concord is taught by note, not exposure: 26 stem entries (`text` opening with `-`)
  across `colors`, `degree`, `desk`, `illness`, `market`, `qualities`, `questions` and `time`
  carry the rule in different wordings and 11 carry nothing, so turning them into phrases the
  way `colors/a-white-car` does (a `concepts.json` entry with emoji and `components` plus a
  realization in all eight language files) is a content project.
- uk has no time-*when* clock frame (`о`/`об` + locative: "о шістнадцятій"), both shipped
  frames being predicates; the composer side is ready (`TrainerLanguagePack.readingPrepositions`,
  a list because uk alternates о/об), so what is left is the READING — `UkrainianClock`
  generates the nominative only, and «о» governs the locative (`clock-registers.md`).
- Ordinal phrase frames ("Ich bin auf dem vierten Platz") wait on a numeral-side agreement
  field in every language, since the frame must decline the NUMERAL while the general device
  runs the other way (`PhraseTemplate.CountForms` inflects the noun from the numeral) and
  `swahiliNounClass` prefixes a cardinal stem where an ordinal's `-a` concord belongs to the
  frame — ordinals are drilled bare meanwhile (`docs/number-forms.md`), and Swahili cannot
  drill them at all.
- Swahili noun class is a frame-only fact (`swahiliNounClass` names it per phrase frame) while
  the nouns carry no class the way de/fr/es nouns carry `grammar.gender`; a catalog
  `"grammar": { "class": "KI_VI" }` on `catalog/areas/*/sw.json` would let an author state it
  once for the frames, the card itself and a future concord drill.
- A Swahili speaker's vocabulary queue: `qualities/neutral`, `aggressive`, `defensive` and
  `organs/thyroid`, `nerve`, `vein` have no honest sw word (absent from `qualities/sw.json`,
  `organs/sw.json`; checked 2026-09-02 against kaikki, freedict, the ipa-dict wordlist and
  Tatoeba — `wastani` is the arithmetic average, `-kali` is already `sharp`, `-jeuri`/`-korofi`
  mean rude, defensive exists only as the verbs `kulinda`/`kujihami`, `tezi` is any gland, and
  `neva`/`vena`/`mshipa` cannot take both nerve and vein in one area), and `organs/intestine`
  sw `utumbo` pl. `matumbo` collides with `body/stomach` `tumbo` pl. `matumbo` (kaikki calls
  `utumbo` class XI with plural `tumbo`).
- A Swahili speaker's forms queue: `catalog/phrases/sw.json:47-60` repeat/write-the-year are
  byte-identical to repeat/write-please and need a heading word (mwaka or tarehe before a bare
  cardinal, as uk cut to `… дату: {slot}`); `time/sw.json:58` `saa tatu asubuhi kamili` (does
  `kamili` follow the day part?) and :54 `mchana` over `alasiri` for early afternoon, with
  `en.json:29` teaching the meridiem where the other four teach the day part; and the weekday
  `abbr` Jtt/Jnn/Jtn/Alh/Ijm/Jmo/Jpl (`catalog/dates/sw.json:3-9`, modeled on it.json) may
  want other truncations.
- A Swahili or Ukrainian native's atlas check: the country atlas ships exonyms and
  nationalities a native has never read (`catalog/countries/*.json`,
  `catalog/language-names/*.json`), authored from sw/uk Wikipedia, Wiktionary and SUM-11 with
  corpus checks, and the residue — which of two attested Swahili stems is canonical, which
  Ukrainian feminines exist at all — is listed with its evidence in the bodies of the two
  `feat(catalog): the atlas reaches …` commits.
- A native speaker per language confirms the 9 idiom pairings in `catalog/areas/idioms/`, whose
  meaning-equivalence judgment is unrecoverable from the JSON (method:
  `../../docs/sprachposter-learnings.md`); uk carries 3 of 9 (сьоме небо, як з відра, тримати
  кулаки) and sw 0, and filling them needs a speaker finding real equivalents, not a
  translation pass.
- Recordings nobody has made yet: pronunciation coverage is uneven across languages and absent
  for phrases (no `catalog/audio/*/manifest.json` has a phrases section, so every phrase falls
  to TTS, silent on sw-iOS; gaps per pack in `data/reference/audio/pack-*/missing.txt`), and
  those unversioned packs have drifted from the catalog so
  `scripts/audio-catalog.py --packs ../data/reference/audio` dies on `pack-es` — a re-fetch
  from Lingua Libre (`../data/reference/audio/lingualibre.py`) precedes any fill, and phrases
  need commissioning or a paid voice.
- Recordings nobody has made yet, seven letter-name clips: uk «мʼякий знак» (the
  `<Аа> – ukrainian.ogg` series has no soft-sign entry, Lingua Libre nothing for the phrase,
  and splicing `Uk-м'який.ogg` + `Uk-знак.ogg` would be a BY-SA adaptation of something nobody
  said, so `ь` falls to the device voice or silence) and es `elle`, `ye`, `erre doble`,
  `hache`, `eñe`, `uve` (absent from the whole `Category:Lingua Libre pronunciation-spa` and
  the `Es-<name>.ogg` convention in every casing), which from one speaker would also stop the
  Spanish letter block changing voice row to row.

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
- `CatalogAudioLintTest` (399 lines) and `CatalogAudioFixtureTest` (340) are both past the
  ~300-line budget and split cleanly: provenance/attribution rules apart from the playback
  index and the naming rules, lookup apart from parse in the fixture half.
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
