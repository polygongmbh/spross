# Backlog — session-discovered, out-of-scope issues

Issues discovered mid-session that fall outside the current scope:
append them here instead of scattering notes across other docs;
prune an item when it is fixed.
One line per item, with a file or context pointer, filed under the section it belongs to.

## Content & catalog

- Phrase→component auto-linking gaps: ~half of phrases carry no `components`
  (naive matcher — `catalog/README.md` § concepts.json).
- Each idiom pairing in `catalog/idioms/` (9 concepts) was chosen for genuine
  meaning-equivalence in de, en and es, not just checked by a translator —
  the candidates it beat are gone from the finished JSON,
  so that judgment call is the one thing a reader cannot recover
  and the one a native speaker should confirm or correct
  (method: `../../docs/sprachposter-learnings.md`).
  sw and uk idiom coverage is open future work:
  it needs a native speaker to find real equivalents from scratch,
  not a translation pass over the existing de/en/es set.
- Pronunciation coverage is uneven across LANGUAGES and absent for phrases — the packs
  only ever matched single-word realizations, so no phrase carries a recording and every
  one falls to TTS (silent on sw-iOS, which has no voice). What Commons never had is
  listed per pack in `data/reference/audio/pack-*/missing.txt`; gap-filling (commissioning
  or a paid voice) is a content project, scoped in that folder's README.
- Voice consistency varies by pack: sw and uk are a single speaker throughout, de is
  mostly one (Jeuwre) with a Lingua Libre remainder, while es is a crowd of Lingua Libre
  speakers in twenty credit groups, none with a stated country or variety
  (`data/reference/audio/pack-es/ATTRIBUTION.md` carries the accent caveat). Deliberately
  NOT addressed by shipping several recordings per word: measured, only 18% of German and
  29% of Spanish words have a second voice on Commons at all, uk and sw have effectively
  none, and the alternative is usually the noisier take — the fix for unevenness is a
  quality floor, not more voices.
- 23 open questions on the alphabet files, each one a call made where no source decided it
  (`catalog/alphabet/uk.json`, `catalog/alphabet/de.json`):
  12 for uk — the name «йот» vs the older «ий», the в allophony and о raising wording,
  the ч/щ anchors, ґудзик as an `exampleText` —
  and 11 for de: s→/z/ and -ig as the variety anchor, the English respellings, the ẞ policy,
  the Vau/We/Jot/Zett/Eszett strings against the de-DE voice,
  plus the three umlaut names MINTED here, A-Umlaut / O-Umlaut / U-Umlaut,
  which no source prescribed.
  Each is written up with its evidence in
  `data/orchestration/audio-langs-2026-07/alphabet-drafts/*-notes.md`.
- The es catalog (528 realizations in `catalog/*/es.json`) carries a review queue its build
  recorded: a 20-pick priority queue the authors themselves flagged lowest-confidence
  (`admin/office`, `work/leave`, `kitchen/reheat`, `bedroom/cuddle` lead it), ~24 medium
  flags, and 49 recorded per-area disputes, the author's choice standing in the JSON in
  every case. Queue, disputes and evidence:
  `data/orchestration/audio-langs-2026-07/es-content/final/REPORT.md` §5 (hand a native
  speaker §5 A first) and the seven `drafts/*-notes.md` "Review disputes" sections;
  method `../../docs/sprachposter-learnings.md`. Separate from the queue, five
  cross-cutting rulings stay with the catalog owner (REPORT §5 C): C2 `desk/internet`
  ships without `gender` (deliberate), C3 cross-gender synonyms sit under the wrong
  article tint (15 pairs; 5 more lexemes were demoted to variants over it), C6 `variants`
  doubles as a demotion bucket for genuine second lexemes, C7 whether Tatoeba-verbatim
  strings are banned, C8 cross-area accept-set overlaps. Already ruled and shipped:
  C1 (`los`/`las` on the three pluralia tantum), C4 (area titles as authored),
  C5 (a leading `¿`/`¡` folds away in grading and in the audio lookup).
- 15 open questions on the Spanish alphabet (`catalog/alphabet/es.json`), several of them
  pedagogical calls: whether the ll/y merger may be taught as flatly
  as it is (yeísmo is standard, but ʎ survives in rural Castile), which jota a Spanish voice
  really says ([x] or the northern [χ]), how far the vanishing final d may be simplified for
  a beginner, whether /s/ belongs in the after-l-n-s trill rule at all, and which letter
  names a teacher uses in 2026 — RAE's *ye*, *uve*, *erre doble* or the *i griega* and
  *doble ele* people still say. Two are scope calls rather than facts: the three vowel rows
  an English reader needs (i, e, u — non-derivable for them, derivable for a German) and a
  row for the written accent, which five example words carry and nothing explains. What the
  file teaches is es-ES throughout, which makes the voice reading it load-bearing: an es-MX
  voice speaks /s/ where the c and z rows promise /θ/ and contradicts them out loud. Every
  divergence LatAm would need instead — seseo, sheísmo, the aspirated j, the dropped final
  d, the letter names — is written up row by row in the same `es-notes.md`.
- Commission the letter names Commons does not have — seven clips close every remaining
  gap in the alphabet sheets:
  - uk «мʼякий знак». The `<Аа> – ukrainian.ogg` series is exactly 32 files with no
    soft-sign entry and Lingua Libre has nothing for the phrase; only the two halves exist
    separately (`Uk-м'який.ogg`, `Uk-знак.ogg`), and splicing them would be an adaptation
    under BY-SA as well as a recording of something nobody said. So `ь` is spoken by the
    device voice where one is installed and is silent where none is. (Its sibling
    «апостроф» is closed — Tohaomg's Lingua Libre file.)
  - es `elle`, `ye`, `erre doble`, `hache`, `eñe`, `uve`. Verified absent by enumerating
    the whole 19k-file `Category:Lingua Libre pronunciation-spa` AND by probing the
    `Es-<name>.ogg/.wav/.flac` convention in every casing. Six clips from one Spanish
    speaker would also fix what the other six cannot: the Spanish letter block currently
    changes voice row to row.
- The catalog-wide gap sweep reaches only plain digraphs, so two gaps stay one word deep.
  Ukrainian gains nothing at all — 33 of its 35 rows are `letter` rows, asked by spoken
  name, and it authors no digraph to sweep. And every position-bound row (de `ch`×3,
  `s`×2, the final-devoicing trio, `er`; es `c`, `g`, `gu`, `r`, `d`) still rides its one
  authored example, because `context` is prose keyed by the reader rather than a rule the
  engine can test. A machine-readable environment field would open both.
- No field carries Ukrainian stress, which is unmarked in writing and load-bearing:
  учень, миша and одяг teach their vowel only if the sheet can show which syllable
  carries it. A `stress` field on realizations (`catalog/README.md`) is the shape the
  pronunciation plan proposed; the alphabet table cannot teach it in the plan's place.
- Hints and contexts are keyed by the READER's language, so they follow the SOURCE
  languages the app offers — all three alphabet files carry de + en only. The day sw or uk
  becomes a base language, each needs its own hint pass, not a translation of the
  English: the German pivot prose for de is already parked in the drafts' notes, while
  sw needs authoring from scratch (sw `j` is /ɟ/, so the en "y in yes" anchor is wrong).
- Sentence-frame notes are authored for German readers only (`notes` in `catalog/drills/*.json`),
  so every other explanation language drills the frames with no gloss at all —
  the counted-noun agreement rules uk needs explained most of all.
- Swahili number frames can only ever count N-class nouns: the agreement lands on the
  NUMERAL, not on the noun, and `count` inflects the noun — so `i-have-n-notebooks`
  and `we-have-n-chairs` are dropped from `catalog/drills/sw.json` rather than coined wrong.
  A numeral-side agreement field is what it would take to author them.
- Ordinal phrase frames ("Ich bin auf dem vierten Platz") wait on that same field in every
  language: the frame must decline the NUMERAL, and the only agreement device runs the other
  way (`PhraseTemplate.CountForms` inflects the noun from the numeral). Ordinals are drilled
  bare meanwhile (`docs/number-forms.md`), and Swahili cannot drill them at all.
- sw `repeat-the-year`/`write-the-year` render byte-identical to `repeat-please`/`write-please`
  — a bare cardinal with no head noun, so nothing tells the learner which frame was asked.
  uk re-cut its pair to name «дату»; sw still needs a heading word a speaker would actually use.
- uk has no time-*when* clock frame (`о`/`об` + locative: "о шістнадцятій"): both shipped
  frames are predicates. The composer side is ready — it takes the absorbed word and the
  leading prepositions from the answer's pack (`TrainerLanguagePack.readingPrepositions`,
  a list because uk alternates о/об) — so what is left is the READING: `UkrainianClock`
  generates the nominative only, and «о» governs the locative (`clock-registers.md`).
- de accepts no bare hour word ("Es ist acht.") though the German is right — with the
  drill's stray-word rescue gone it is now safe to add, but it wants its own sweep run.
- The emphatic full hour is taught by `time/nine-am-sharp` now, and the phrase made three
  calls the finished JSON cannot show: sw hangs `kamili` after the part of the day
  (`saa tatu asubuhi kamili`), where `saa tatu kamili asubuhi` composes just as well;
  en took the meridiem (`nine a.m. sharp`) over the part of the day
  (`nine o'clock in the morning sharp`), which is the shape the other four use —
  `EnglishClockRegisters` reads both registers at 09:00, and only the meridiem stays short;
  and sw `afternoon` took `mchana` — the word the greeting uses — over `alasiri`,
  which is the late afternoon only, so `time/two-in-the-afternoon` rests on it.
- `time` has no `midnight`, though the clock reveal teaches it at 00:00 beside `noon`
  (`docs/clock-registers.md` §English a.m./p.m.). es carries no `morning`, no `afternoon`
  and no `late`, uk no `afternoon`: `mañana` is already `time/tomorrow` and `tarde` already
  `time/evening`, so each of them would now prompt one form for two cards INSIDE one area —
  the collision the lint calls unfixable at runtime — while uk simply has no plain noun for
  the afternoon. Spanish still meets the morning as `de la mañana` in `time/nine-am-sharp`.
  es `evening` keeps `noche` as a variant beside `night`'s `noche`: a variant is graded and
  never prompted, so the two stay tellable apart where it counts.

## Engine & scheduling

- A **defer** flag: a per-card opt-out that sinks a word behind the whole catalog rather
  than switching it off, so a learner can skip words they judge unimportant without them
  vanishing. Distinct from `setSuspended`, which is hard off until revived by hand — defer
  would stay in seed order, just last, and only surface once nothing unseen is left.
  Wants a `BoxState` field (not a `GrowthStage` rung — it is an intent, not a standing), a
  `BoxEngine` verb, a Box-screen affordance, and a decision on where it sorts against
  `OwnWords.SEED_BASE`, which is already "behind every catalog concept".

- Watch snapshot 60-entry cap: due-first ranking keeps due cards on-watch,
  but revisit the cap if the active box outgrows it (`../kern/docs/snapshots.md`).
- `AnswerNormalizer.strayLeadingWordRecovery` tests the RAW leading token for letters,
  so "it's half past two" behaves unlike "it is half past two" where the rule still
  lives (vocab review). Testing `cleaned(first)` would make it consistent — a widening,
  so it wants its own `RealCatalogGradingTest` run.
- Number near-twins gated in `TrainerTypoBridgeGuardTests`
  (sw `nne`↔`nane` incl. tens compounds; uk `дев'ять`↔`десять`;
  en `eight`↔`eighty`; es `sesenta`↔`setenta`, both with their compounds):
  at the drill's one-slip-per-word budget one can pass for the other —
  product call pending (no slips at all for number drills vs accept).
  `TrainerFormsTypoBridgeGuardTests` gates the same twins wearing form endings,
  so the ruling covers both allowlists at once.
- The number forms have no rung for prices/currency or digit-by-digit readings
  (a phone number, a PIN) — two families a learner meets constantly and the ladder
  never asks; adding one is an enum case, a `draw` arm, a `formReading` arm per pack
  and a rung row (`kern/src/commonMain/kotlin/net/spross/kern/trainer/NumberForms.kt`).
- `<pack>.cardinal(-n)` returns the digits rather than a reading: the negative reading
  lives in `formReading` deliberately, so nothing needs it today, but a caller that
  assumes `cardinal` covers every `Long` gets a digit string back with no error.
- `UkrainianClock.gloss` (lines 159-173) rebuilds its candidates from `Forms` instead of
  selecting them out of `readings` the way es does, so uk carries a third encoding of its
  own minute grammar and needs `.filter { it in readings }` as a guard.
- `EnglishClock` triplicates its own count/noun/direction derivation (`spelledMinutes:70-73`,
  `american:85-89`, `EnglishClockRegisters.anchors:58-72`) with `past` as `<= 30` in two of
  them and `< 30` in the third — the largest true duplication in the clock corpus, and
  larger than everything cross-language put together.
- A `clockAnchors` slot for midnight/noon on `TrainerLanguagePack` is deferred: unlike the day
  parts it would be a NEW authored copy rather than a removed one — de bakes them into early
  returns in `GermanClock.conversational`, en into `EnglishClockRegisters.anchors`, es and uk
  into hand-written `ClockReading` constants, and sw has none by design.
- The 24-hour register closes the twelve-hour cycle by NUMBER (`achtzehn Uhr` cannot answer 06:00),
  which `ClockCollisionSweepTests.sweep()` never grades because it skips same-cycle pairs —
  so that closure is unheld, unlike the day parts' (`dayPartReadingsCloseTheTwelveHourCycle`).
- FSRS parameter optimization from review logs —
  enabled by the full per-card logs, unbuilt (kern README §5).
- Box pack control under-reports: `BoxBrowser.enqueueableCount` counts only the area's own
  cards, while `BoxEngine.enqueue` also prepends a phrase's missing components — a phrase
  whose components sit on another shelf packs more than the number promised
  (`kern/src/commonMain/kotlin/net/spross/kern/box/BoxBrowser.kt` `enqueueableCardIds`).
- Watch multiple-choice distractors carry no novelty or recency criterion
  (`kern/src/commonMain/kotlin/net/spross/kern/session/MultipleChoice.kt`):
  word class, area and shape rank them now, but the newest entry can still be the odd
  one out — the same class of problem the phone's due-order reshuffle fixed,
  on another surface.
- Rating labels carry more weight on a first exposure now that Good sends a word about a week
  out (kern README §5) — the button wording deserves a look
  (`App/Sources/Design/RatingButtonsView.swift`).
- Automatic growth walks seed order, so a round's first sights are seed neighbours
  (`Growth.newCandidates` step 2b) — and seed order inside an area is written in co-hyponym
  runs (kitchen: four appliances, then six utensils, then the cooking verbs), so a
  `NEW_CARDS_PER_ROUND` round lands inside ONE run. The interference finding is about
  semantic SETS — same word class, same category, mutually substitutable (spoon/fork/knife) —
  and it is an INTRODUCTION effect: the words are too alike to tell apart while the
  form–meaning bond is still forming. The area itself is a THEMATIC set (mixed classes, one
  scene), which the same literature finds neutral-to-helpful, so the fix spreads WITHIN the
  area — across word class and sub-cluster — and never across areas. Review is unaffected:
  once bound, contrasting near-neighbours is the useful case, and the catalog already
  teaches those apart (`promptAmbiguous`, `CatalogAnswerGrader.OtherWord`).

## App & UX

- Nothing marks an unlock: a rung turning a locked row into a pickable one is the whole
  event, and the learner only sees it next time the overview opens
  (`App/Sources/Screens/NumbersOverview+Practice.swift`). A full-screen ceremony was
  rejected for something that happens a handful of times; a moment on the row itself was
  not considered and might be worth it.
- The numbers reference stops at the cardinals: the `forms` band — one worked example per
  number form — is the last piece of the numbers page that never shipped. Kern emits seven
  bands (`NumberReference.kt`) and `NumberReferenceTable.bandTitle` already knows the eighth
  key, so it is a row list and its readings.
- The Werkstatt card's two chips are laid out as a row; a third would want the grid the
  hub used to have (`App/Sources/Screens/TrainerHubView.swift`).
- `SessionView.swift` sits at 432 lines (guide ~300); the natural cut is a new
  `SessionView+Turn.swift`, which needs the xcodegen regen a new file costs.
- The iPhone widget header pairs a system-orange flame with the clay `wgAccent` bar —
  the ruling named only the strip, so the flame kept `.orange`
  (`Widgets/Sources/WordWidgetView.swift`; `wgAccent` is declared and internal, one-line close).
- The letter drill's typed and dictation stage has no live-check auto-advance —
  finishing the word does not end the step the way it does in vocab review and the
  trainer drills (`App/Sources/Design/AutoAdvance.swift`) — deferred because its verdict
  ladder carries a third `heard` outcome (a synonym of the dictated word) that the
  un-arm-on-further-typing logic would need a new case for, so it is a design call rather
  than a mechanical port (`LetterDrillView+Grading.swift` `verdict(_:task:)`).

- The watch quiz tells correctness to the EYE only — tile tint, red wash and the rating
  emoji are all visual, and the emoji is `accessibilityHidden` because VoiceOver reading
  "raising hands" after every tap is worse than silence
  (`Watch/Sources/WatchQuizView.swift` `ratingBadge`). The phone states the opposite rule
  for its letter drill ("correctness is never color alone", `surfaces.md`), so the watch owes
  a spoken equivalent — an accessibility label or value on the answered tile, not a mark.

- Article tints reach the two Box screens unevenly: Android tints the article on its
  browse rows, iOS box rows stay plain and tint only on the session card
  (`BoxCardRow.swift:43` vs `android/.../ui/BoxRows.kt`) — same palette, one design
  call on where the tint belongs; unify once decided.

## Localization

- Watch, widget, and complication chrome is hardcoded German with no string catalog
  (`Watch/Sources/WatchHomeView.swift`, `Widgets/Sources/WordWidgetView.swift`,
  `WatchWidgets/Sources/WatchWordWidgetView.swift`) —
  needs its own catalog plus a chrome-language field on the snapshot,
  since those surfaces never see `AppModel.knownLocale`.

## Platform reach

- Android surfaces still unported: `design.md` § Not yet owns the list; chimes additionally
  need `res/raw/` assets, a small player and a licence line in `audio-licensing.md`.
- Android back doors land on Heute even when opened from the box: `closeAbout()`,
  `cancelOnboarding()` and `activate()` all end at `Screen.Heute` (`android/.../AppModel.kt`).
- Android Heute's failure card and the `error*`/`growth*` chrome are wired but unreachable:
  AppModel has no load-failure state yet and the round summary does not render growth.
- Dead after the wave-3 Android sweep, prune in one pass: Chrome `easy`/`again`/`summaryLine`/
  `typoNote`/`practice`/`emptyState`/`dueLabel`/`consolidatedLabel`/`freshLabel`,
  `ui/Components.kt` `RatingButtons`, and `AppModel.sessionAvailable`/`canPracticeExtra`
  (unread since `HeuteStanding`).
- `ic_launcher_background` still holds retired-palette `#FF2E6B34`
  (`android/src/main/res/values/colors.xml`) — the adaptive-icon plate, not the window.
- Portability move 6 (`snapshot/WatchRun` + public snapshot DTOs, `docs/portability.md` § Moves)
  deferred per user 2026-08-08.
- Audio ships un-thinned: `catalog/audio/` is 26 MB (de 4.9, es 7.2, sw 5.2, uk 9.0) and
  BOTH installs carry all of it — the iOS folder reference and the Android catalog sync
  copy the tree whole — so a Swahili learner downloads 21 MB of German, Spanish and
  Ukrainian they can never hear. Per-language delivery (on-demand resources / Play asset packs) would cut
  the install to the target actually being learned; measure the real per-platform delta
  before choosing a mechanism.
- `compileSdk` sits at 36 and that is now what holds androidx back: lifecycle 2.11 refuses
  to resolve below 37 (`checkDebugAarMetadata`), and the next Compose BOM will follow it.
  Bumping needs the android-37 platform installed and a re-check of `targetSdk` separately —
  compiling against 37 does not opt the app into its runtime behavior.

## Compliance

- CC BY-SA vs App Store DRM, open before the FIRST submission, and needing a legal read
  rather than an engineering one: the obligation, the files it covers and the
  mitigation already on record are `audio-licensing.md` §6.1, which owns this question
  along with the es accent caveat and the Azure gap-fill terms.

## Verification gaps

- Watch pairing untested on real hardware;
  complication rendering never screenshot-verified (no simctl affordance).
- The `SprossWatchWidgets` auto-scheme resolves destinations erratically
  (project.yml declares no schemes, and an extension auto-scheme flip-flops between
  iOS and watchOS destination lists): a named iPhone destination may not match.
  Reliable gates: the `Spross` scheme (builds the whole embed chain, watch app and both
  widget extensions included) or `-scheme SprossWatchWidgets -destination
  'generic/platform=iOS Simulator'`.
- The 32 uk letter recordings have never been heard against the names the alphabet file
  speaks. `letters{}` carries no `matches` field (`catalog/audio/uk/manifest.json`), so
  no lint can pin «йот» to what `letters/u0439.mp3` actually says — the names were
  authored from the 1993 orthography, and wherever a clip says something else it is the
  `name` FIELD that has to change, never the audio. One listening pass, 32 clips.
- The Android pronunciation player has been heard on the EMULATOR only (2026-08-08:
  session rounds play, and the ANR it caused is fixed — the MediaPlayer/`LoudnessEnhancer`
  lifecycle now lives on its own thread, verified over a full unmuted round).
  Still open, and only real hardware can answer them: how the boost and the lead skip
  actually sound, one letter-drill run end to end, and whether `MODIFY_AUDIO_SETTINGS`
  is really needed for a session-scoped effect (it is declared).
- Android cold-start with an existing box showed the loading spinner 20–90 s on the
  emulator (fresh install loads in seconds) — likely the 787-card decode+join; profile
  before real devices (`android/.../AppModel.kt` restore path).
- On the emulator with a hardware keyboard, Enter after `input text` could walk focus onto
  the session top-bar mute toggle and flip it; probably an emulator artifact — check once
  on hardware before chasing (`android/.../ui/SessionScreen.kt` top bar).
- Resolved 2026-08-01 — the analysis index has its PEAK term: every `gain` is capped at the
  file's own measured headroom less 1 dB (`scripts/audio-catalog.py` [ANALYSIS]), so nothing
  reaches full scale and the iOS-clips / `LoudnessEnhancer`-compresses split has nothing
  left to act on; 70 entries sit under the loudness target to buy it.
