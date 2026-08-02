# Backlog — session-discovered, out-of-scope issues

Issues discovered mid-session that fall outside the current scope:
append them here instead of scattering notes across other docs;
prune an item when it is fixed.
One line per item, with a file or context pointer, filed under the section it belongs to.

## Content & catalog

- uk plural sweep unverified by a native speaker: the irregular forms authored in
  `catalog/*/uk.json` (stem alternations, fleeting vowels, agreeing phrases) are
  LLM-authored — the substantivized `лікарняні` and the phrase plurals most of all.
- uk native-verification sweep pending for the 4 LLM-adapted twin realizations —
  kitchen pot-on-stove, hall doorbell-rang, desk laptop-charge, desk wifi-gone
  (method: `../../docs/sprachposter-learnings.md`).
- Phrase→component auto-linking gaps: ~half of phrases carry no `components`
  (naive matcher — `catalog/README.md` § concepts.json).
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
- The alphabet files are LLM-authored and await a native-speaker sweep of every hint and
  every letter name (`catalog/alphabet/uk.json`, `catalog/alphabet/de.json`): 12 open
  questions for uk (the name «йот» vs the older «ий», the в allophony and о raising
  wording, the ч/щ anchors, ґудзик as an `exampleText`) and 11 for de (s→/z/ and -ig as
  the variety anchor, the English respellings, the ẞ policy, the Vau/We/Jot/Zett/Eszett
  strings against the de-DE voice — plus the three umlaut names minted here, A-Umlaut /
  O-Umlaut / U-Umlaut, which no source prescribed). Each is written up with its evidence
  in `data/orchestration/audio-langs-2026-07/alphabet-drafts/*-notes.md`; method as ever
  in `../../docs/sprachposter-learnings.md`.
- The es catalog (528 realizations in `catalog/*/es.json`) is LLM-authored against the
  German anchor and owes its native-speaker sweep — explicit debt per the build's decision
  record: a 20-pick priority queue the authors themselves flagged lowest-confidence
  (`admin/office`, `work/leave`, `kitchen/reheat`, `bedroom/cuddle` lead it), ~24 medium
  flags, and 49 recorded per-area disputes, the author's choice standing in the JSON in
  every case. Queue, disputes and evidence:
  `data/orchestration/audio-langs-2026-07/es-content/final/REPORT.md` §5 (hand a native
  speaker §5 A first) and the seven `drafts/*-notes.md` "Review disputes" sections;
  method `../../docs/sprachposter-learnings.md`. Separate from the sweep, five
  cross-cutting rulings stay with the catalog owner (REPORT §5 C): C2 `desk/internet`
  ships without `gender` (deliberate), C3 cross-gender synonyms sit under the wrong
  article tint (15 pairs; 5 more lexemes were demoted to variants over it), C6 `variants`
  doubles as a demotion bucket for genuine second lexemes, C7 whether Tatoeba-verbatim
  strings are banned, C8 cross-area accept-set overlaps. Already ruled and shipped:
  C1 (`los`/`las` on the three pluralia tantum), C4 (area titles as authored),
  C5 (a leading `¿`/`¡` folds away in grading and in the audio lookup).
- The Spanish alphabet (`catalog/alphabet/es.json`) is the same LLM authoring and owes the
  same sweep, with 15 questions of its own: whether the ll/y merger may be taught as flatly
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
- German clock readings lose their capital mid-sentence: the slot fill lowercases a
  mid-sentence reading so Swahili's "Saa …" embeds, but German's nouns must keep theirs —
  `train-departs-at` at 00:00 answers "Der Zug fährt um mitternacht ab.", and "Viertel",
  "Dreiviertel" and "Mittag" go the same way (`PhraseSlots.adjustCase`). The rule is a
  property of the language's readings, so it belongs on `TrainerLanguagePack`, not on
  every call site.
- sw `repeat-the-year`/`write-the-year` render byte-identical to `repeat-please`/`write-please`
  — a bare cardinal with no head noun, so nothing tells the learner which frame was asked.
  uk re-cut its pair to name «дату»; sw still needs a heading word a speaker would actually use.

## Engine & scheduling

- Watch snapshot 60-entry cap: due-first ranking keeps due cards on-watch,
  but revisit the cap if the active box outgrows it (kern README §7).
- Number near-twins gated in `TrainerTypoBridgeGuardTests`
  (sw `nne`↔`nane` incl. tens compounds; uk `дев'ять`↔`десять`;
  en `eight`↔`eighty`; es `sesenta`↔`setenta`, both with their compounds):
  at the drill's one-slip-per-word budget one can pass for the other —
  product call pending (no slips at all for number drills vs accept).
- FSRS parameter optimization from review logs —
  enabled by the full per-card logs, unbuilt (kern README §5).
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

- The letter drill's typed and dictation stage has no live-check auto-advance —
  finishing the word does not end the step the way it does in vocab review and the
  trainer drills (`App/Sources/Design/AutoAdvance.swift`) — deferred because its verdict
  ladder carries a third `heard` outcome (a synonym of the dictated word) that the
  un-arm-on-further-typing logic would need a new case for, so it is a design call rather
  than a mechanical port (`LetterDrillView+Grading.swift` `verdict(_:task:)`).

## Localization

- Watch, widget, and complication chrome is hardcoded German with no string catalog
  (`Watch/Sources/WatchHomeView.swift`, `Widgets/Sources/WordWidgetView.swift`,
  `WatchWidgets/Sources/WatchWordWidgetView.swift`) —
  needs its own catalog plus a chrome-language field on the snapshot,
  since those surfaces never see `AppModel.knownLocale`.

## Platform reach

- Android not yet ported: Box browse, trainers, widget, 14-day strip, confetti/haptics
  (`design.md` § Android companion).
- Android carries its own unrelated palette (`android/.../ui/Theme.kt`) that never went
  through the contrast pass — it predates the ocean/forest re-cut and shares no values
  with `Design/Theme.swift`.
- Audio ships un-thinned: `catalog/audio/` is 26 MB (de 4.9, es 7.2, sw 5.2, uk 9.0) and
  BOTH installs carry all of it — the iOS folder reference and the Android catalog sync
  copy the tree whole — so a Swahili learner downloads 21 MB of German, Spanish and
  Ukrainian they can never hear. Per-language delivery (on-demand resources / Play asset packs) would cut
  the install to the target actually being learned; measure the real per-platform delta
  before choosing a mechanism.

## Compliance

- CC BY-SA vs App Store DRM, open before the FIRST submission, and needing a legal read
  rather than an engineering one: the obligation, the files it covers and the
  mitigation already on record are `audio-licensing.md` §6.1, which owns this question
  along with the es accent caveat and the Azure gap-fill terms.

## Verification gaps

- Watch pairing untested on real hardware;
  complication rendering never screenshot-verified (no simctl affordance).
- The 32 uk letter recordings have never been heard against the names the alphabet file
  speaks. `letters{}` carries no `matches` field (`catalog/audio/uk/manifest.json`), so
  no lint can pin «йот» to what `letters/u0439.mp3` actually says — the names were
  authored from the 1993 orthography, and wherever a clip says something else it is the
  `name` FIELD that has to change, never the audio. One listening pass, 32 clips.
- The Android pronunciation player has never been HEARD. `PronunciationPlayer` moved to
  MediaPlayer + `LoudnessEnhancer` to carry the analysis index (SoundPool can neither seek
  nor boost); the arithmetic is unit-pinned (`PlaybackIndexTest`) and the build is green,
  but no emulator image and no device were at hand when it landed, so the boost, the lead
  skip and the async request guard have only ever been reasoned about. One letter-drill
  run on hardware settles all three — and it is the only way to learn whether
  `MODIFY_AUDIO_SETTINGS` is really needed for a session-scoped effect (it is declared).
- Resolved 2026-08-01 — the analysis index has its PEAK term: every `gain` is capped at the
  file's own measured headroom less 1 dB (`scripts/audio-catalog.py` [ANALYSIS]), so nothing
  reaches full scale and the iOS-clips / `LoudnessEnhancer`-compresses split has nothing
  left to act on; 70 entries sit under the loudness target to buy it.
