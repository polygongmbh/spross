# Content catalog format (v2.1)

Language learning content organised **one folder per area**.
Designed for reuse (any language pair is a runtime join of shared parts),
crowdsourced per-language contribution, and a slow default learning progression.

## The key modeling decision: everything is a concept

- A **concept** is language-neutral: a `slug`, a `kind`
  (`noun` | `verb` | `adjective` | `phrase`), and (for words) an `emoji`.
  Words and phrases live in one ordered list.
- A **realization** is one concept rendered in one language (`text` + grammar + notes).
- A **pair** (de↔sw, de↔uk, later sw↔uk) is a **runtime join** on slug — never stored.
  The German side is authored once and shared across every pair that teaches German.
- **Coverage may be non-uniform.** A concept without a realization in some language
  simply never appears in pairs involving that language. This is how pair-specific
  content works: `relax` is a concept with `de`, `en` and `uk` realizations and no `sw`,
  so it never shows up in a pair that teaches Swahili — no separate pair files needed.
  It is also the honest way out when a language has no word worth teaching yet:
  drop the realization rather than ship a coinage.
- **Homonyms & target-language merges.** Slugs are unique, but realization *texts* are not:
  one text may legitimately serve two concepts in two areas — usually because the
  target language merges a distinction the source draws (sw `kuacha` = `verlassen` AND
  `aufhören`, and with an object `kuacha kazi` = `kündigen`). There is **no disambiguation
  field**: the **area** is the disambiguator, and the engine renders the area label on an
  ambiguous *produce* prompt only — never on recognize, where any cue strong enough to
  identify the concept would reveal the answer. Two rules are lint-enforced: a
  display-identical prompt **within one area** is an error (the area cue would be identical
  — repick the word), and the same concept pair colliding in **two languages** means one
  meaning was authored twice → unify it. Exception to the second: if de/en genuinely
  distinguish the two and only both targets merge them, fix the imprecise realization
  instead of deleting a concept. Tolerated cross-area collisions are pinned by a test, so
  minting a new one has to be a conscious decision.

## Layout

```
catalog/
  areas.json            # ordered GROUPS → ordered areas: the default progression
  languages.json        # per-language metadata (display name, verb citation prefix)
  <area>/               # one folder per area (basics, kitchen, …)
    concepts.json       # ordered [{slug, kind, emoji?}] — order IS introduction order
    de.json             # { title, words: { slug: realization } }
    en.json
    sw.json
    uk.json
  audio/                # GENERATED pronunciation recordings, one folder per language
    <lang>/
      manifest.json     # { language, words: { slug: … }, letters?: { glyph: … } }
      <slug>.mp3
      letters/u<hex>.mp3
```

Adding a language is purely additive: drop a `<area>/<lang>.json` in each area for
the slugs you can cover. It never edits an existing file.
Reviewing a language is per area: `<area>/<lang>.json` is the contribution/review unit.

## Schemas

**`areas.json`** — the ordering + grouping manifest (the ONE place cross-area
structure, group display titles, and per-area language-neutral metadata live;
area titles live in the area's own files):
```json
[ { "group": "home",
    "titles": { "en": "At home", "de": "Zuhause", "sw": "Nyumbani", "uk": "Вдома" },
    "areas": [ { "area": "kitchen", "emoji": "🍳" },
               { "area": "living",  "emoji": "🛋️" } ] } ]
```
Default global order = groups top-to-bottom, areas as listed. "Pull one area ahead"
is a runtime/user-preference concern; the content only supplies the default.
- `titles` — the group heading, keyed by reader language; required for every declared language.
- `area` — the folder name; globally unique across groups.
- `emoji` — the area's illustrative icon. **Language-neutral display metadata owned by the
  catalog**, exactly as concept `emoji` is, so both apps show the same icon instead of each
  carrying its own map (it used to be a hardcoded Swift dictionary, which meant area icons
  existed on iOS only). Required and validated at parse time by the same rule as concept
  emoji — adding an area therefore fails the gate rather than silently losing its icon.

**`languages.json`** — per-language metadata, keyed by lang code:
```json
{ "en": { "name": "English", "englishName": "English", "flag": "🇬🇧",
          "optionalVerbPrefixes": ["to "], "articles": ["the", "a", "an"] },
  "sw": { "name": "Kiswahili", "englishName": "Swahili", "flag": "🇹🇿",
          "optionalVerbPrefixes": ["ku", "kw"] } }
```
- `name` — the language's own name for itself ("Deutsch", "Українська");
  language pickers use this so speakers always recognize their language.
- `englishName` — English exonym ("German", "Ukrainian"). Required, non-empty.
- `flag` — exactly ONE emoji flag sequence for chrome/badges
  (sw uses 🇹🇿 Tanzania, the v1 choice).
- `articles` — the language's articles (de `der/die/das/ein/eine`, en `the/a/an`).
  ONE leading listed article is optional when grading input in this language;
  it also drives article coloring in the UI.
  Omit for articleless languages (sw, uk).
- `optionalVerbPrefixes` — array of infinitive citation prefixes on this language's
  verb realizations (en `"to cook"`, sw `"kupika"`/`"kwenda"`). A leading occurrence of
  ANY listed prefix is **optional when grading input**: the answer matches with or
  without it (`cook` == `to cook`, `pika` == `kupika`, `enda` == `kwenda`), while display
  keeps the full citation form. Swahili lists both `ku` and its pre-vowel coalesced form
  `kw` (`ku+enda → kwenda`). Omit for languages with no such prefix (de `-en` suffix, uk
  `-ти` suffix). Harmless to over-list: every verb is stored in infinitive form, so
  stripping a listed prefix only ever yields the same stem.

**`<area>/concepts.json`** — ordered, language-neutral. Order across all kinds IS
seed/introduction order (phrases follow their area's words):
```json
[ { "slug": "fridge",  "kind": "noun", "emoji": "🧊" },
  { "slug": "cook",    "kind": "verb" },
  { "slug": "careful", "kind": "adjective" },
  { "slug": "teacher-f", "kind": "noun", "emoji": "👩‍🏫", "feminineOf": "teacher" },
  { "slug": "the-fridge-is-empty", "kind": "phrase", "components": ["fridge"] } ]
```
Slugs are readable English lemmas, **globally unique across every area** (lint-enforced).
English doubles as the keying language AND a content language: the slug is the identity,
`en.json` carries the English realization (display text may differ from the slug —
verb `cook` → `"to cook"`, phrase `the-fridge-is-empty` → `"The fridge is empty."`).
- `components` (phrases only) — same-area word slugs the phrase is built from;
  the box gates a phrase's unlock on those words being learned. Empty = no gate.
- `adjective` is the catch-all for single words that are neither noun nor verb:
  adjectives, adverbs, and interjections (`draußen`, `immer`, `Vorsicht`).
  Prefer splitting such a word out of a phrase over inflating the phrase:
  short phrases keep typing manageable and let the word be recalled on its own.
- `feminineOf` (nouns only) — marks this concept as the feminine form of `<base-slug>`.
  It carries the distinct `de` form always, and a realization only where that language
  grammatically distinguishes the feminine (uk `вчителька`; NOT sw or en, which are
  genderless; NOT uk for epicene nouns like колега). It may carry its own female-specific `emoji`
  where one exists (`👩‍🏫`), else none. How this drives per-direction card emission and
  the ♀ prompt marker is engine behavior — see the engine contract (`../kern/README.md` §2/§3).

**The slug IS the card id.** The engine keys each learner's schedule by it,
and neither the area nor the `kind` appears in that key —
which is exactly what makes both free to change:
a concept can move to another area or be reclassified without resetting anyone's progress.
Global uniqueness is what makes that safe:
two areas claiming one slug would fuse two concepts into a single schedule,
so a genuine repeat is disambiguated by qualifying the slug
(the noun keeps `help`/`plant`/`work`, the verb becomes `to-help`/`to-plant`/`to-work`).
The price is that **renaming a slug is a breaking act**:
it orphans the schedule and the word returns as new,
so rename deliberately, never just to polish a lemma.

**`<area>/<lang>.json`** — title + realizations keyed by slug:
```json
{ "title": "Die Küche",
  "words": {
    "fridge": { "text": "Kühlschrank", "grammar": { "gender": "der", "plural": "Kühlschränke" } },
    "cook":   { "text": "kochen" },
    "the-fridge-is-empty": { "text": "Der Kühlschrank ist leer." } } }
```
Realization fields — only `text` is required:
- `text` — the canonical answer/display form, nothing else (no embedded glosses/labels).
  Never bracket a disambiguator into it (`"mto (Kissen)"`) — everything in `text` has to be
  typed. Homonyms are handled by the area rule above, not inside the string.
  A form that only ever appears bound carries its leading dash (sw `-zuri`, which takes the
  noun's class prefix): it is the citation convention, grading ignores the dash, and the
  engine takes it off again wherever the dash alone would identify the answer among plain
  words (kern README §7). The agreement forms themselves go in `variants`.
- `synonyms` — DISTINCT-KNOWLEDGE alternates of `text` (array; omit if none):
  genuinely different lexemes for the same concept that a learner must recognize
  on their own (uk `office` установа/відомство, uk `boss` шеф/керівник).
  Each entry is **prompt-worthy**: it grades as correct when producing this language,
  and takes its turn as the recognize prompt when learning FROM it — on the concept's
  one schedule, never as a unit of its own (kern README §3).
  NOT a home for distinct learnable items: feminine nouns belong to `feminineOf`
  concepts, and different-meaning words belong to their own concept.
- `variants` — ACCEPTED surface forms of the SAME knowledge (array; omit if none):
  alternate renderings a learner already knows if they know `text` — register pairs
  (de Sie-form in `text`, du-form here), gender-agreement forms of a phrase
  (uk `Ти завів/завела …?`), diminutives (uk миша/мишка), internationalism spellings
  (uk договір/контракт), and the noun-class agreement forms of a Swahili adjective
  stem (`-zuri` → nzuri/mzuri/kizuri/wazuri), which is what a learner meets in the wild.
  **Accept-only, never scheduled and never shown**: they grade as correct on produce,
  and that is the whole of it — `text` is the form prompted on recognize and the form the
  reveal teaches, and `synonyms` are what rotate beside it.
  Author them for reach, not for display: a form that deserves to be seen is a synonym.
- `grammar` — language-specific, open keys, **bare values** (no `"Pl."`/`"die"`
  labels, no `(selten)` qualifier), one fact per key: de and es `gender` + `plural`,
  sw `plural`, en `plural`, uk `plural`. Omit if empty.
  `gender` is the ARTICLE the learner says, and always one the language declares
  in `languages.json` — de der/die/das, es el/la, and `los`/`las` on the nouns
  whose article genuinely IS the plural one (los auriculares, las vacaciones).
  That is not decoration: grading reads it back as an article and demotes an
  otherwise exact answer whose PRESENT leading article disagrees, so a singular
  article on a plural-only noun would mark the one right answer a typo.
  Omit `gender` where the language allows both and neither is taught
  (es `internet`, which RAE writes without an article).
  `plural` is a bare full form (`"Wörter"`), a suffix (`"-n"`, `"-nen"`),
  `"="` (identical to the singular → render `"= Pl."`), or
  `"only"` (pluralia tantum, no singular → render `"nur Pl."`).
  True uncountables (Regen, Hunger) simply omit `plural`.
  **How much to author is per language**, and the test is always the same:
  write it down when the learner could not derive it.
  de and sw author every countable noun — German plurals are unpredictable by class,
  and a Swahili plural IS the noun class (`kiti`→`viti`, `mlango`→`milango`),
  the single most load-bearing fact about the word.
  en and uk author only what the regular pattern does not give:
  en beyond a bare +s (`knife`→`knives`, `bus`→`buses`),
  uk beyond swapping the ending —
  stem alternations (`ніж`→`ножі`), fleeting vowels (`день`→`дні`),
  suppletives (`людина`→`люди`), indeclinables and `-ння` neuters (`"="`),
  and phrases whose other words have to agree (`письмовий стіл`→`письмові столи`).
- `notes` — keyed by EXPLANATION language (today only `de`). A note lives on the
  realization it explains and is selected by the reader's base language, so the
  key is load-bearing the moment a language is taught to more than one audience
  (a German note on a shared `sw.json` must not surface for an English learner).
  Keep a note only if it changes what the learner would say or do; pure etymology
  ("wörtl. …") is cut. Load-bearing teaching (e.g. which word for "rice") is
  destined to become first-class training content, not a permanent note.

## Audio (`catalog/audio/`)

Bundled pronunciation recordings, one folder per language, **generated** by
`app/scripts/audio-catalog.py --packs <workspace>` — edit packs, not this directory.
The packs (Wikimedia Commons transcodes plus a `manifest.tsv` of provenance) are
unversioned research input; what is committed here is the shipped bytes and the
licence record that has to travel with them. Both apps bundle the whole tree as it
stands (iOS folder reference, the Android catalog sync), so nothing needs registering.

```json
{ "language": "uk",
  "words": {
    "office": { "file": "office.mp3", "matches": "установа",
                "licence": "CC BY 3.0 us",
                "licenceUrl": "https://creativecommons.org/licenses/by/3.0/us/",
                "author": "Галя Раптова, Nicolas Vion",
                "source": "Uk-установа.ogg", "sha256": "1c44…" } },
  "letters": {
    "ж": { "file": "letters/u0436.mp3", "licence": "CC BY-SA 4.0",
           "licenceUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
           "author": "Tabrus", "source": "Жж – ukrainian.ogg", "sha256": "77b0…",
           "gain": 20.0, "lead": 1069 } } }
```

- `language` must equal the folder name, and a folder for a language `languages.json`
  does not declare is never read — adding one is dropping a directory in, nothing else.
- `words` is keyed by concept slug, `letters` (optional, uk only today) by lowercase
  glyph. Every field is required except `licenceUrl`, which is absent exactly for
  public-domain files, having no deed to link, and `gain`/`lead`, absent where they
  would be zero.
- `matches` — the surface form the recording actually SPEAKS, and the lookup key:
  playback is keyed by what stands on the card, never by the slug the file was fetched
  for, so a rotated synonym nobody recorded falls through to the app's own voice
  instead of playing the canonical word. It may differ from `text` in case
  (`unterlagen` / "Unterlagen"), edge punctuation (`hallo` / "Hallo!") or the citation
  dash (`zuri` / "-zuri") — the engine folds those away (`../kern/README.md` §11).
  Letters carry no `matches`: they speak a name, and that string belongs to the
  alphabet file.
- `source` — the original Commons filename; the credits screen links `File:<source>`,
  which is what keeps attribution checkable rather than merely present.
- Word files are `<slug>.mp3`; letter files are `letters/u<codepoint>.mp3`, four
  lowercase hex digits, never glyph-named — `й`/`ї` decompose under NFD on APFS and a
  Unicode filename has to survive git, Gradle sync and AAPT unchanged. The manifest maps
  the glyph, so the name is purely internal.
- **The mp3 bytes are the Commons transcode untouched**, renamed and nothing else:
  re-encoding (including loudness normalization, so packs differ in loudness) is an
  adaptation under BY-SA. `sha256` is the digest the generator verified after the copy
  and lint re-hashes what was committed, which makes it a gate rather than a promise.
- `gain` (dB) and `lead` (ms) are the generator's own MEASUREMENT of those untouched
  bytes — how far the recording sits from the catalog's analysis target, and how much
  dead air to start past — so the files stay unmodified and only the player corrects
  them. What was measured, against which target, is `../scripts/audio-catalog.py`'s
  `ANALYSIS`.
- No `README.md` inside `audio/` — the Android sync only excludes one at the catalog
  root, so a nested one would ship in the APK. Audio schema docs live here.

Lint (`CatalogAudioLintTest`) holds the rest: every entry names a slug its language
realizes and a form some card can show, no two entries claim one spoken form with
different bytes, every file ships and is referenced exactly once, and no author is a
placeholder like "Own work" — BY and BY-SA both require naming somebody.

## Alphabet (`catalog/alphabet/`)

One file per declared language, `alphabet/<lang>.json`, entries in teaching order — the
reference sheet renders it, the letter drill samples from it. **File presence is the
registry**: adding a language's alphabet is dropping a file, no code lists which languages
have one. A file for an undeclared language is never read; lint (`AlphabetLintTest`)
fails it loudly instead of letting it sit.

```json
{ "entries": [
  { "glyph": "и", "upper": "И", "name": "и", "ipa": "ɪ", "example": "mouse",
    "hints": { "de": "kurzes, lockeres i wie in bitte", "en": "lax i as in bit" },
    "confusable": { "look": ["й", "н"], "sound": ["і", "е"] } },
  { "glyph": "ch", "kind": "contextual", "id": "ch-ich", "ipa": "ç",
    "context": { "de": "nach hellen Vokalen", "en": "after front vowels" },
    "example": "light", "hints": { "en": "…" },
    "confusable": { "look": ["ch-ach"], "sound": ["sch"] } },
  { "glyph": "б д з ж г", "kind": "rule",
    "context": { "de": "am Wortende", "en": "word-finally" },
    "hints": { "de": "keine Auslautverhärtung: б bleibt b — хліб" } }
]}
```

- `kind` is `letter` (default), `digraph`, `contextual` or `rule`. A **rule** row is
  sheet-only prose (uk's no-final-devoicing table): never prompted, never a choice tile,
  and the only kind whose `glyph` may carry whitespace. `drill: false` keeps a real but
  undrillable grapheme (uk `ʼ`, de length-h) out of every prompt; it stays a tile.
- `id` (slug charset) is REQUIRED the moment two entries share a `glyph` (de authors
  `ch` three times) and is then the entry's **ref**; otherwise the glyph is. `confusable`
  refs (an id, or a glyph naming exactly one row) are closed symmetrically at parse —
  authoring и → й also makes й → и — and homophone groups are derived from
  byte-identical `ipa` strings, never authored.
- Every entry needs an `ipa` or at least one hint. `hints`/`context` are keyed by the
  READER's language (⊆ declared, like realization `notes`); `name` is the letter's own
  name — the string a synthesizer is handed, never the bare glyph. Apostrophes are
  stored as U+02BC; grading folds the class, so realizations keeping U+0027 still match.
- `example` is a concept slug, resolved in two independent halves that never consult the
  join: the alphabet's OWN language must realize the word (what the drill speaks and
  gaps — a lint error otherwise), while the reader's language supplies the meaning line
  (nullable — the sheet omits it, graceful degradation). `exampleText` is the escape
  hatch where no concept fits; it carries no slug and therefore never claims a recording.
- **Gap rule** (lint): a drill-true `digraph`/`contextual` row's resolved example
  contains its glyph EXACTLY once — zero leaves nothing to blank, and with two the blank
  can land on the wrong, position-bound instance and teach the opposite of the entry.
  `letter` and `rule` rows are exempt: their example is sheet decoration.
- No `audio` field. Letter recordings live in the audio manifest's `letters{}` (above),
  keyed by lowercase glyph — lint holds that every recorded glyph addresses exactly one
  alphabet row, which is why colliding-glyph entries can never carry one.

## What earns a slot, and how it is worded

Two content rules that cut across every language file.

**Every slot has to buy fluency.** A concept is worth a card when knowing it
lets the learner say more; charm is not a qualification.
Length is judged on the **target** side, never the source: German compounds what
Swahili spells out as a genitive chain (Rezept → `cheti cha dawa`,
Apotheke → `duka la dawa`), so a sentence that reads normal in the prompt can
triple in the answer the learner actually types. The median phrase is three
words; `take-the-prescription-to-the-pharmacy` reached nine and was cut.
`sweet-dreams` was cut on this test — the bedroom already teaches
`good-night-sleep-well`, which covers the same moment with more useful words,
so the second phrase only bought a warm feeling.
Redundancy is the usual symptom: when two entries serve one situation,
keep the one whose words go furthest elsewhere.

**A realization mirrors the concept, not the translator's instinct.**
Every word in one language's text should have a visible counterpart in the others' —
that mapping is how a learner works out which word did what,
and it is worth re-cutting the source phrase to keep
(„Das WLAN ist weg" became „Das Internet ist weg" so `intaneti` had something to answer to;
„zu teuer" is `ghali mno`, not `ghali sana`, which is „sehr teuer").
The replaced wording moves to `variants` so nobody's typed answer stops grading.
Where a language genuinely has no equivalent —
a greeting formula, `Feierabend`, the Swahili clock — a `notes` entry carries the gap.

The same rule decides **baked-in objects**: a verb carries its object in EVERY
language or in none. Swahili often cannot go bare, because one verb covers several
German ones (`kupanda` = besteigen/einsteigen/pflanzen), and then the object is
authored across the board — `Blumen pflanzen` / `to plant flowers` / `kupanda maua` /
`садити квіти`, never `pflanzen` answered by `kupanda mimea`. The exception is a
**merge**, where the target really has one word for two source concepts and the object
is what tells them apart (`kuacha` verlassen vs `kuacha kazi` kündigen,
`kuomba` beantragen vs `kuomba kazi` sich bewerben): there the object is a
disambiguator, it is carried by the merged language alone, and the homonym rule above
governs it.

## What v2 dropped from v1

- `kind`/`area` encoded in ids (kind is a concept field; area is the folder).
- `areas.json` as a title store (titles moved into area files; it now orders/groups).
- `realizations/<lang>.json` and `phrases/de-<lang>.json` (folded into area folders).
- Pair-authored phrase files (phrases are concepts; pairs are runtime joins).
- Per-word `review` provenance (returns at file granularity if crowdsourcing needs it).
- `"Pl."`/`"die"` prefixes and plural-in-`notes` (structured into `grammar`).
- `singularOnly` grammar key (noise: near-redundant with omitting `plural`).
  `pluralOnly` folded into `plural: "only"`; `feminine` promoted from a grammar
  hint to proper `feminineOf` sibling concepts.
- German area keys (`amt`/`arzt`/`arbeit` → `admin`/`health`/`work`).
- A homonym disambiguation field — per-realization `sense`/`gloss` or concept-level
  `homonymOf`. The area label already disambiguates for free, in every language, and lint
  guarantees it exists; `sense` would be new authored content for a handful of entries, and
  `homonymOf` would encode at concept level a fact that is per-language (`kuacha` is
  ambiguous in sw only) and rots as languages are added. Same reasoning that deleted
  `variantOf`. See the homonym rule above and kern README §2/§3.

## Legacy

The project-level `../data/generated/` (`vocab-de-*.json`, `catalog.py`) is a frozen
reference snapshot of the retired v1 per-pair format;
not read by the apps, not maintained.
