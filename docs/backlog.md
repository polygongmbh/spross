# Backlog — session-discovered, out-of-scope issues

Issues discovered mid-session that fall outside the current scope:
append them here instead of scattering notes across other docs;
prune an item when it is fixed.
One item per bullet, with a file or context pointer, filed under the section it belongs to —
as short as that allows, longer only to carry evidence or reasoning a fixer would otherwise have to redo.

## Content & catalog

- `qualities` is at 45 concepts after `beautiful`, the two stance pairs and `health`/`healthy`
  landed, well past the ~40 an area is meant to hold (`catalog/areas/README.md` § which area a concept lives in).
  The seam a learner would name is `comparison`: `different`, `difference`, `same`,
  `similar`, `opposite`, `equal`, `real` and `to-compare` — eight cards that answer "is it
  the same one?" rather than "what is it like?", which leaves `qualities` the adjectives it
  is named for. `scripts/catalog-move.py` carries it. This is now the largest area in the
  catalog and the only one over the line.
- `life-death` ships with five concepts, which is thin for a shelf. `people/to-be-born` is
  the obvious sixth — it is the arc's other end and sits in `people` only because birth is
  a family event — but moving it thins `people`'s own family block, so it was left. Decide
  when a seventh candidate turns up (`funeral`? `grave`? `to-grow-up`?).
- Swahili has no honest word for `qualities/neutral`, `aggressive` or `defensive`, nor for
  `organs/thyroid`, `nerve` or `vein`, so those six ship without sw and never reach a de→sw learner — the pair the suggestions came
  from. Checked 2026-09-02 against kaikki, freedict swh-eng/eng-swh, the ipa-dict Swahili
  wordlist and the Tatoeba Swahili sentences: `wastani` is the arithmetic average, `-kali`
  is already `qualities/sharp`, `-jeuri`/`-korofi` mean rude or savage, and defensive exists
  only as the verbs `kulinda`/`kujihami`. On the organs: no source carries thyroid at all
  (`tezi` is any gland or swelling), and nerve and vein resolve to the loans `neva`/`vena`
  or to `mshipa`, which is nerve AND vessel at once — one word cannot take both cards in one
  area. A reviewer who speaks Swahili may know a phrase the dictionaries do not carry
  (`-a kujihami`? `bila upande`? `mshipa` on one of the two?); until then the gaps stand.
- `organs/intestine` ships sw `utumbo` with plural `matumbo`, the form the ipa-dict wordlist
  carries and the one in ordinary use for guts. kaikki disagrees, calling `utumbo` class XI
  with a class X plural `tumbo` — which would make the intestine's plural identical to the
  stomach card's singular (`body/stomach` is `tumbo`/`matumbo`). Worth one native check.
- `nature` is at 44 concepts, past the ~40 an area is meant to hold
  (`catalog/areas/README.md` § which area a concept lives in), and the two case-contrast
  phrases added there made it worse rather than caused it. The seam a learner would name is
  `weather`: `sun`, `rain`, `wind`, `snow`, `sky`, `cloud`, `storm`, `thunder`, `lightning`,
  `humid` plus the five weather phrases — a shelf that can say no, where what is left of
  `nature` keeps the landscape. `scripts/catalog-move.py` carries it, recordings included.
- German adjective endings are the next rule family with an anchor and no note: it draws
  them, and eo/es/fr/it/uk/sw all agree adjectives too, so the cross-language test passes at
  six of eight. `people/a-good-friend`, `people/many-friends`, `people/old-people` and
  `qualities/a-lot-of-water` already exercise it unlabeled — the same shape
  `at-school`/`go-to-school` was in before it was named.
- ~800 German-reader notes on the seven non-German targets are still the tail rather than
  the default: `notes` now leads with the language it explains, so each of those reaches a
  German reader alone where one rewrite in the target's own language would reach all eight.
  It is the highest-leverage note work left, and the pattern to copy is example-first —
  «мама → мамо, тато → тату» teaches the vocative to a learner who could not yet read the
  word for it, where naming the case would need a note per reader instead.
  Swahili and Esperanto prose still need a reviewer who reads them.
- Cases still unnamed where a language draws one German does not: Ukrainian's instrumental
  of predication («працює вчителем») and its dative of the experiencer beyond the one
  `health/feel` note. Esperanto's accusative of measure and time (`Mi restos tri tagojn`)
  is not one — German marks it the same way, so it fails the more-than-one-language test.
- Phrase→component linking gaps: 55/192 phrases (29%) carry no `components` —
  these are hand-authored, not auto-linked (`catalog/README.md` § concepts.json),
  so the gap is unfinished authoring, not a matcher to fix.
- Each idiom pairing in `catalog/areas/idioms/` (9 concepts) was chosen for genuine
  meaning-equivalence across the languages that carry it, not just checked by a translator —
  the candidates it beat are gone from the finished JSON,
  so that judgment call is the one thing a reader cannot recover
  and the one a native speaker should confirm or correct
  (method: `../../docs/sprachposter-learnings.md`).
  uk carries three of the nine (сьоме небо, як з відра, тримати кулаки);
  the rest of uk and all of sw is open future work:
  it needs a native speaker to find real equivalents from scratch,
  not a translation pass over the existing set.
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
  the Vau/We/Jot/Zett/Eszett strings against the de-DE voice.
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
  strings are banned, C8 cross-area accept-set overlaps.
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
    device voice where one is installed and is silent where none is.
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
- The gap sweep counts glyph occurrences with no longest-glyph-wins, so a glyph nested in a
  longer row's glyph sweeps in that row's words (fr `au` would gap the a-u inside 13 `eau`
  words — bateau, beaucoup …); fr `au` opts out via `mine: false` meanwhile, an engine-side
  exclusion would win its honest pool back (`Catalog.alphabetExamples`).
- No field carries Ukrainian stress, which is unmarked in writing and load-bearing:
  учень, миша and одяг teach their vowel only if the sheet can show which syllable
  carries it. A `stress` field on realizations (`catalog/README.md`) is the shape the
  pronunciation plan proposed; the alphabet table cannot teach it in the plan's place.
- Hints and contexts are keyed by the READER's language, so they follow the SOURCE
  languages the app offers — every alphabet file carries de + en only. The day sw or uk
  becomes a base language, each needs its own hint pass, not a translation of the
  English: the German pivot prose for de is already parked in the drafts' notes, while
  sw needs authoring from scratch (sw `j` is /ɟ/, so the en "y in yes" anchor is wrong).
- The country atlas ships exonyms and nationalities a native has never read
  (`catalog/countries/*.json`, `catalog/language-names/*.json`). Swahili was authored from
  sw.wikipedia and sw.wiktionary and Ukrainian from uk.wikipedia, uk.wiktionary and SUM-11
  with corpus checks, but a residue of judgment calls stayed open: which of two attested
  Swahili stems is canonical, and which Ukrainian feminines exist at all. Every one of them
  is listed, with its evidence, in the bodies of the two `feat(catalog): the atlas
  reaches …` commits — that is the pointer, and nothing restates it.
- Sentence-frame notes now carry `en` alongside `de` for it/fr/eo (2026-08-15) and uk
  (2026-08-02) drills (`notes` in `catalog/phrases/*.json`), but sw is still de-only, so an
  English reader drilling Swahili's frames meets no gloss at all.
  The vocab side improved the same way for de-target words in `catalog/areas/*/de.json`
  (both `de` and `en` keys now), but `notes` on a word is still keyed by the reader's
  language with no fallback (`../kern/docs/catalog.md`), and sw vocab is still de-only,
  so an English reader learning Swahili meets no note on any card.
- Swahili noun-class concord is taught by note rather than by exposure: 20 stem
  entries (`text` opening with `-`) across `catalog/areas/colors`, `qualities`, `health`
  and `questions` carry the same rule in different wordings (`qualities` `good`,
  `questions` `how-many`) while nine of them carry nothing at all.
  The examples buried in those notes are doing a phrase's job — a learner who produces
  `Shati jeupe.` meets the concord the way the box teaches everything else, and `colors`
  already has exactly one such card (`a-white-car`). Turning them into phrases is a
  content project: each wants a `concepts.json` entry with emoji and `components`
  plus a realization in all eight language files.
- Ordinal phrase frames ("Ich bin auf dem vierten Platz") wait on a numeral-side agreement
  field in every language: the frame must decline the NUMERAL, and the general agreement
  device runs the other way (`PhraseTemplate.CountForms` inflects the noun from the numeral).
  Swahili's `swahiliNounClass` does not reach them — it prefixes a cardinal stem, where an
  ordinal's `-a` concord belongs to the frame. Ordinals are drilled bare meanwhile
  (`docs/number-forms.md`), and Swahili cannot drill them at all.
- Swahili noun class is a frame-only fact: `swahiliNounClass` names it per phrase frame, while
  the nouns themselves carry no class the way de/fr/es nouns carry `grammar.gender`. A catalog
  `"grammar": { "class": "KI_VI" }` on `catalog/areas/*/sw.json` would let an author state it
  once and any surface read it — the frames, the card itself, a future concord drill.
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
- The letter drill's choice Sprossen: Android shows a reveal under the tiles, iOS suppresses it —
  the tiles already mark the answer (`ui/LetterDrillStages.kt`, `LetterDrillView+Stages.swift`).

- Italian dates: the dayMonth reveal teaches `il otto`/`il undici marzo` on 2 of 31 days —
  a static pattern cannot elide; the elided `l'{day} {month}` already grades as a variant
  (`catalog/dates/it.json`; the article ruling itself is still ⚠ in `docs/date-readings.md`).
- Swahili weekday `abbr` (Jtt, Jnn, Jtn, Alh, Ijm, Jmo, Jpl) are invented, display-only —
  a native may prefer other truncations (`catalog/dates/sw.json`).
- Dates `notes` parse and lint but reach no screen: `DateReferenceRow` carries no note
  slot, so the Swahili counting note (⚠ `docs/date-readings.md` § Swahili) has nowhere to
  land once a native words it (`kern/.../trainer/DateDrillTask.kt`).

## Engine & scheduling

- A **defer** flag: a per-card opt-out that sinks a word behind the whole catalog rather
  than switching it off, so a learner can skip words they judge unimportant without them
  vanishing. Distinct from `setSuspended`, which is hard off until revived by hand — defer
  would stay in seed order, just last, and only surface once nothing unseen is left.
  Wants a `BoxState` field (not a `GrowthStage` Sprosse — it is an intent, not a standing), a
  `BoxEngine` verb, a Box-screen affordance, and a decision on where it sorts against
  `OwnWords.SEED_BASE`, which is already "behind every catalog concept".

- Watch snapshot 60-entry cap: due-first ranking keeps due cards on-watch,
  but revisit the cap if the active box outgrows it (`../kern/docs/snapshots.md`).
- The assembled dates accepted set is an uncapped cross-product graded on every keystroke
  (worst de Sprosse 6 ≈ 128 five-word forms per `evaluate`; typical ~16) — measure on the
  oldest supported phone before trusting it free (`DateDrillTasks.fill`;
  `NumberReadingIndex.INDEXED_CARDINALS` states the bound precedent).
- `AnswerNormalizer.strayLeadingWordRecovery` tests the RAW leading token for letters,
  so "it's half past two" behaves unlike "it is half past two" where the rule still
  lives (vocab review). Testing `cleaned(first)` would make it consistent — a widening,
  so it wants its own `RealCatalogGradingTest` run.
- The number forms have no Sprosse for prices/currency or digit-by-digit readings
  (a phone number, a PIN) — two families a learner meets constantly and the ladder
  never asks; adding one is an enum case, a `draw` arm, a `formReading` arm per pack
  and a Sprosse row (`kern/src/commonMain/kotlin/net/spross/kern/trainer/NumberForms.kt`).
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
  a closure nothing holds, unlike the day parts' (`dayPartReadingsCloseTheTwelveHourCycle`).
- FSRS parameter optimization from review logs —
  enabled by the full per-card logs, unbuilt (kern README §5).
- Authoring the number packs' word tables in the catalog (`catalog/numbers/<lang>.json`)
  was assessed and not taken — packs keep their Kotlin tables; the full assessment is in
  git history (`docs/drill-grading-plan.md`, deleted with the series that closed Part 1).
- Box pack control under-reports: `BoxBrowser.enqueueableCount` counts only the area's own
  cards, while `BoxEngine.enqueue` also prepends a phrase's missing components — a phrase
  whose components sit on another shelf packs more than the number promised
  (`kern/src/commonMain/kotlin/net/spross/kern/box/BoxBrowser.kt` `enqueueableCardIds`).
- Watch multiple-choice distractors carry no novelty or recency criterion
  (`kern/src/commonMain/kotlin/net/spross/kern/session/MultipleChoice.kt`):
  word class, area and shape rank them now, but the newest entry can still be the odd
  one out — the same class of problem the phone's due-order reshuffle fixed,
  on another surface.
- A short round that goes stale mid-run (the profile or catalog moves under it) recomposes
  as a full one: `SessionIntent.RecomposeIfStale` reaches for `composeSession`, which knows
  nothing about which round was opened
  (`kern/src/commonMain/kotlin/net/spross/kern/session/SessionRun.kt` `recompose`).
- Rating labels carry more weight on a first exposure now that Good sends a word about a week
  out (`kern/docs/fsrs.md`) — the button wording deserves a look
  (`App/Sources/Design/RatingButtonsView.swift`).
- Automatic growth walks seed order, so a round's first sights are seed neighbors
  (`Growth.newCandidates` step 2b) — and seed order inside an area is written in co-hyponym
  runs (kitchen: four appliances, then six utensils, then the cooking verbs), so a
  `NEW_CARDS_PER_ROUND` round lands inside ONE run. The interference finding is about
  semantic SETS — same word class, same category, mutually substitutable (spoon/fork/knife) —
  and it is an INTRODUCTION effect: the words are too alike to tell apart while the
  form–meaning bond is still forming. The area itself is a THEMATIC set (mixed classes, one
  scene), which the same literature finds neutral-to-helpful, so the fix spreads WITHIN the
  area — across word class and sub-cluster — and never across areas. Review is unaffected:
  once bound, contrasting near-neighbors is the useful case, and the catalog already
  teaches those apart (`promptAmbiguous`, `CatalogAnswerGrader.OtherWord`).
- The letter drill's `exampleText` fallback is not audibility-filtered, so an inaudible
  escape-hatch row stays promptable and shows a dead speaker — shared by both platforms
  (`kern/.../trainer/LetterDrillAvailability.exampleWords` KDoc, pinned in its test).
- Same class: kern's audibility test is "the catalog names a recording path OR a voice
  exists", never whether the file resolves in the bundle — on a voiceless language, a row
  or dictation candidate whose authored recording is missing ships promptable with a dead
  speaker (`kern/.../trainer/LetterDrillAvailability.kt`).
- `TrainerRun` has no `openAt(mode:levels:rng:)` sibling of `LetterDrillRun.openAt` —
  its absence forces iOS's DEBUG-only `TrainerRunState.seeded` doCopy helper
  (`App/Sources/Screens/TrainerSessionView+UITest.swift`).

## App & UX

- Italian and French articles render un-hued: `articleGender` (`kern/.../model/Article.kt`) maps
  de/es articles only, so il/lo/i/gli/le/uno fall to null and the reveal shows them
  uncolored; French is worse — half-hued — because la/un hue via their es homographs
  while le/les/une fall to null (`l'` stays null rightly — it marks both genders).
- Both reveals join article + text with a plain space (`android/.../ui/Components.kt`
  `articleColoredText` and the iOS twin), so an elided `l'` renders "l' acqua" —
  an apostrophe-final article should write onto its noun, the citation
  `RealCatalogGradingTest.everyGenderedCardAcceptsTheCitationFormItTeaches` now grades.
- Nothing marks an unlock: a Sprosse turning a locked row into a pickable one is the whole
  event, and the learner only sees it next time the overview opens
  (`App/Sources/Screens/NumbersOverview+Practice.swift`). A full-screen ceremony was
  rejected for something that happens a handful of times; a moment on the row itself was
  not considered and might be worth it.
- The letter drill's typed and dictation stage has no live-check auto-advance —
  finishing the word does not end the step the way it does in vocab review and the
  trainer drills (`App/Sources/Design/AutoAdvance.swift`) — deferred because its verdict
  ladder carries a third `heard` outcome (a synonym of the dictated word) that the
  un-arm-on-further-typing logic would need a new case for, so it is a design call rather
  than a mechanical port (the ladder is kern's now: `kern/.../trainer/LetterDrillRun.kt`).
- The iOS result tile still hand-codes the 10/5/2 emoji ladder kern now owns —
  `DrillRunResult` wants to carry `DrillRunSummary.tier` the way Android's `tierEmoji` reads it
  (`App/Sources/Design/DrillChrome.swift` vs `android/.../ui/DrillChrome.kt`).
- `NumbersOverview.swift` holds its picks as `Set<DrillVariant>` where `DrillSelection`
  hands back ordered lists (converted at both boundaries), and hand-spells its progress key
  `"\(variant.storageTag).\(language)"` — `TrainerMode.companion.progressKey(variant:language:)`
  is the public spelling; `DrillVariant.storageTag`/`.slotKind` stay `internal` in kern over it.
- `TrainerRecords.swift` hard-codes `"trainer.record."`; `TrainerMode.companion.RECORD_PREFIX`
  now exists.
- `AppModel+Queries.swift` `consolidatedCards()` has no caller left — prune.
- `TrainerSessionView+Grading.swift` and `LetterDrillView+Grading.swift` now hold the run
  DRIVERS (dispatch/effects/close), not grading — rename to `+Run.swift` in a pass that
  regenerates the Xcode project.
- `android/.../AppModel.kt` sits at 803 lines (guide ~300); extracting the Werkstatt doors
  needs widening `screen`'s private setter.
- Android's `NumberReferenceTable` renders every band eagerly inside one `verticalScroll` —
  fine at today's ~50 rows, revisit if a band grows (`android/.../ui/NumberReference.kt`).
- The credits screen names the target-language word every bundled recording says and offers
  no way to hear one: the row tap is already spent on the file's Commons page
  (`App/Sources/Screens/CreditsView.swift` `fileRow`), so playing a pack from the list that
  credits it wants a second control per row.
- The answer field's accepted mark is Correct-only on Android where iOS rides it on both
  correct states, so a near miss there is amber and nothing else in the field itself
  (`ui/SessionTurn.kt` `AnswerField`, `ui/DrillField.kt`). Not a live 1.4.1 failure —
  the correction box below says the state in words — but the mark is the parity.
- A revealed field has a spoken state on iOS (`a11y.verdict.notAnswered`) and none on Android,
  the same silence `a11y.verdict.almost` was just brought out of: a `Chrome.kt` field
  and the same two `when` arms.
- A row that speaks is a gesture on content rather than a control (`pronounceOnTap`,
  `App/Sources/Design/SpokenWord.swift`; `clickable` on Android), so VoiceOver reaches it
  as a named action while Switch Control and Full Keyboard Access, which scan for focusable
  controls, reach nothing — shared by every surface on that modifier: the reference rows,
  `BoxCardRow.swift`, the produce narration lines.
- A drill's typed-answer controls — the field, the one primary action that reveals or
  checks, the amber hold, the revealed branch with its stop offer, the screen-reader
  "Weiter" — stand verbatim in three files (`TrainerSessionView+Drill.swift`,
  `LetterDrillView+Stages.swift`, `DrillRunView+Content.swift`), and the live check
  that arms them is wired per copy. One component owning the branch and the
  `onChange(of: input)` beside it would make a fourth drill's auto-confirm structural
  rather than remembered.
- At accessibility XXXL a card with a long note grows past the bottom of the screen and
  takes the rating row with it, and nothing scrolls — that card cannot be graded at all.
  The card's own growth is unbounded by design (`Theme.swift` reserves a minimum, never
  a maximum); it is the row below that has nowhere left to stand.
  Now more likely to be hit: the note fallback means a card shows a note for every reader
  rather than only a German one, and the grammar notes are among the longest authored.
- The atlas is the first drill whose PROMPT is a word rather than a numeral or a played
  sound: it can be heard neither by tap nor by autoplay — `read-aloud.md`'s table has no
  row for a spoken-word drill prompt, so the rule is owed before the code
  (`DrillRunView+Content.swift`).

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

- `AppModel.activate()` silently bootstraps a fresh box when decode fails
  (`android/.../AppModel.kt:496-509`), so a corrupt or mis-pathed box reads as empty
  with no trace — surface the failure (log + error card) before real devices.
- No automated visual-parity check between iOS and Android for shared, parity-bearing UI
  (cards, layout tokens): a card/layout drift bug recurred across multiple sessions because
  nothing verified the two platforms side by side before a change was called done. A
  Compose `@Preview`/snapshot-testing setup, or an equivalent iOS-side mechanism, would
  make that check structural rather than remembered.

## Localization

- Watch, widget, and complication chrome is hardcoded German with no string catalog
  (`Watch/Sources/WatchHomeView.swift`, `Widgets/Sources/WordWidgetView.swift`,
  `WatchWidgets/Sources/WatchWordWidgetView.swift`) —
  needs its own catalog plus a chrome-language field on the snapshot,
  since those surfaces never see `AppModel.knownLocale`.

## Platform reach

- The `ios` job stops at `App Store Connect API key from secret` and no release has
  carried an IPA: the Apple secrets wait on the developer-program registration, so a
  release publishes the APK alone and iPhones are served by `scripts/deploy-devices.sh`.
- The two phones name a language differently in chrome: iOS resolves through
  `LanguageNames.display` (chrome-locale exonym — "Suaheli"), Android reads
  `catalog.languages[code].name` (endonym — "Kiswahili"). Pre-existing, but the
  own-word form's flagged labels now put the two side by side in a screenshot.
- Android surfaces still unported: `design.md` § Not yet owns the list.
- The web numbers drill never gained it/fr/eo: `web/site.js` mirrors
  `catalog/languages.json` by hand (its `LANGS` rows) and still offers the five older
  languages only (`docs/website.md` § Drill scope).
- Android back doors land on Home even when opened from the box: `closeAbout()` and
  `activate()` (reached via a box-settings language change) both end at `Screen.Home`
  (`android/.../AppModel.kt`).
- Android Home's failure card and the `error*`/`growth*` chrome are wired but unreachable:
  AppModel has no load-failure state yet and the round summary does not render growth.
- Dead after the wave-3 Android sweep: `AppModel.canPracticeExtra` (unread since
  `HomeStanding`).
- Portability move 6 (`snapshot/WatchRun` + public snapshot DTOs, `docs/portability.md` § Moves)
  deferred per user 2026-08-08.
- Audio ships un-thinned: `catalog/audio/` is 76 MB (8–14 MB per language) and
  BOTH installs carry all of it — the iOS folder reference and the Android catalog sync
  copy the tree whole — so a Swahili learner downloads ~68 MB of languages
  they can never hear. Per-language delivery (on-demand resources / Play asset packs) would cut
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
- On the emulator with a hardware keyboard, Enter after `input text` could walk focus onto
  the session top-bar mute toggle and flip it; probably an emulator artifact — check once
  on hardware before chasing (`android/.../ui/SessionScreen.kt` top bar).
- `TrainerStore`'s read/write plumbing is untested (needs `SharedPreferences`, no Robolectric
  in the module; the key rules are kern's and tested there) — one emulator check that a Sprosse
  survives an app restart (`android/.../TrainerStore.kt`).
- The iPhone install link is an `itms-services://` URL in the release notes, which GitHub
  renders as code rather than a tappable link. A one-page `web/install.html` taking `?v=`
  and building the link would make it a button once spross.net is live
  (`.github/workflows/release.yml` publish step, `docs/website.md`).
- A fully correct typed answer carrying a matched synonym's own article demotes Exact→Typo:
  `AnswerNormalizer.evaluate` reads the leading article back against the card's single
  `grammar.gender` instead of the accepted form it actually matched
  (`kern/.../session/AnswerNormalizer.kt`). Italian promotes many cross-article synonyms
  (la vaccinazione on il vaccino, il farmaco on la medicina, il salario on lo stipendio),
  so the reveal teaches forms the grader then punishes; es only ever hid such forms in variants.
- Drill runs have no RNG seed hook — `drillRandom` is `KotlinRandom.companion`
  (`App/Sources/KernBridge.swift`), so a screenshot or verification run cannot pin which task
  it will be asked, and reaching a given verdict means relaunching until the draw matches.
  kern already takes an injected `Random` per run, so `-uitest-seed N` is a small hook.
  Narrower than it was: `idb ui text` types AFTER the prompt has been read back out of
  `idb ui describe-all`, so AIMING an answer needs no seed. What is still unreachable is
  pinning WHICH task gets drawn — a screenshot of one particular verdict or Sprosse.
- Read-aloud is stored differently on the two phones: iOS keeps the three-state `readAloud`
  (`App/Sources/Audio/AudioSession.swift`), Android is still on the boolean iOS calls its
  LEGACY key, `pronunciationMuted` (`android/.../audio/Pronouncer.kt`). So Android has no
  `followsPhone` middle state, and the two cannot be reasoned about as one setting.
- The letter drill's dictation Sprosse is dealt on a device silenced by its own volume, where
  the review card asked by ear is not any more (`LetterDrillAvailability.report` takes only
  `hasVoice`, `docs/read-aloud.md`). Its question plays under `.playback`, so the phone's
  switch is deliberately overruled there — but a volume slider at zero silences that too,
  and the Sprosse has no "can't listen right now?" of its own to fall back on.
- `CatalogAudioLintTest` (399 lines) and `CatalogAudioFixtureTest` (340) are both past the
  ~300-line budget and split cleanly: provenance/attribution rules apart from the playback
  index and the naming rules, lookup apart from parse in the fixture half.
- The watch reveal carries no "also means" line: `WatchEntryDto` ships `sourceText` alone, so
  a merged word teaches only the meaning of the card that was asked. Costs a wire field and a
  `SCHEMA_VERSION` bump (`WatchSnapshotBuilder`), plus the line in the watch's own reveal.
- `tools/FaceGen` cannot build: it depends on `../../Kern` for a `DuoKern` SwiftPM product,
  and neither has existed since the kern became the Gradle-built `SprossKern` framework.
  `docs/facegen.md` still documents `swift run facegen` as if it works. Port the imports to
  SprossKern, or delete the tool and its doc.
- `nature` holds 47 concepts, past the ~40 an area should stay under
  (`catalog/areas/README.md`). The seam a learner would name is weather-and-sky against the
  living things — animals, trees, flowers — which would also give the animal-sound phrases
  a shelf of their own.
