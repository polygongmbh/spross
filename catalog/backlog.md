# Catalog backlog

Catalog content, its forms, its audio and the per-language questions that want a speaker are filed here,
one item per bullet with a file or context pointer — as short as that allows,
longer only to carry evidence or reasoning an author would otherwise have to redo — and pruned when fixed.
Ready work comes first, then the items that end in a question for the owner, then what waits on a native speaker.

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
