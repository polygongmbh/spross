# Content catalog format (v2.1)

Language-agnostic content in this directory, organised **one folder per area**.
Designed for reuse (any language pair is a runtime join of shared parts),
crowdsourced per-language contribution, and a slow default learning progression.

**THIS DIRECTORY IS CANONICAL**: both apps read `catalog/` directly.
Edit content HERE and it is what the apps bundle on their next build.

> **Beta — no live user data.** Ids, slugs, and encoding carry no
> preservation guarantee; they are free to change
> (the pre-production invariant: `../CLAUDE.md` § Invariants).

## The key modeling decision: everything is a concept

- A **concept** is language-neutral: a `slug`, a `kind`
  (`noun` | `verb` | `adjective` | `phrase`), and (for words) an `emoji`.
  Words and phrases live in one ordered list.
- A **realization** is one concept rendered in one language (`text` + grammar + notes).
- A **pair** (de↔sw, de↔uk, later sw↔uk) is a **runtime join** on slug — never stored.
  The German side is authored once and shared across every pair that teaches German.
- **Coverage may be non-uniform.** A concept without a realization in some language
  simply never appears in pairs involving that language. This is how pair-specific
  phrases work: "Fleisch grillen" is a concept with `de` + `sw` realizations and no
  `uk`, so it shows up in de↔sw only — no separate pair files needed.
- **Homonyms & target-language merges.** Slugs are unique, but realization *texts* are not:
  one text may legitimately serve two concepts in two areas — usually because the
  target language merges a distinction the source draws (sw `kuvaa` = `anziehen` AND
  `sich anziehen`; sw `kupumzika` covers three concepts). There is **no disambiguation
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
```

Adding a language is purely additive: drop a `<area>/<lang>.json` in each area for
the slugs you can cover. It never edits an existing file.
Reviewing a language is per area: `<area>/<lang>.json` is the contribution/review unit.

## Schemas

**`areas.json`** — the ordering + grouping manifest (the ONE place cross-area
structure and group display titles live; area titles live in the area's own files):
```json
[ { "group": "home",
    "titles": { "de": "Zuhause", "sw": "Nyumbani", "uk": "Вдома" },
    "areas": ["kitchen", "living", "bath", "bedroom", "desk", "hall"] } ]
```
Default global order = groups top-to-bottom, areas as listed. "Pull one area ahead"
is a runtime/user-preference concern; the content only supplies the default.

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
- `synonyms` — DISTINCT-KNOWLEDGE alternates of `text` (array; omit if none):
  genuinely different lexemes for the same concept that a learner must recognize
  on their own (uk `office` установа/відомство, uk `boss` шеф/керівник).
  Each entry is **schedule-worthy**: it grades as correct when producing this
  language AND gets its own recognize unit when learning FROM it.
  NOT a home for distinct learnable items: feminine nouns belong to `feminineOf`
  concepts, and different-meaning words belong to their own concept.
- `variants` — ACCEPTED surface forms of the SAME knowledge (array; omit if none):
  alternate renderings a learner already knows if they know `text` — register pairs
  (de Sie-form in `text`, du-form here), gender-agreement forms of a phrase
  (uk `Ти завів/завела …?`), diminutives (uk миша/мишка), internationalism spellings
  (uk договір/контракт). **Accept-only, never scheduled**: they grade as correct on
  produce and rotate as display alternates of the canonical recognize unit,
  but never become their own unit.
- `grammar` — language-specific, open keys, **bare values** (no `"Pl."`/`"die"`
  labels, no `(selten)` qualifier), one fact per key: de `gender` + `plural`,
  sw `plural`, en `plural` (irregular/pluralia-tantum only — regular +s is omitted),
  uk `plural` (only for pluralia tantum). No gender outside de. Omit if empty.
  `plural` is a bare full form (`"Wörter"`), a suffix (`"-n"`, `"-nen"`),
  `"="` (identical to the singular → render `"= Pl."`), or
  `"only"` (pluralia tantum, no singular → render `"nur Pl."`).
  True uncountables (Regen, Hunger) simply omit `plural`.
- `notes` — keyed by EXPLANATION language (today only `de`). A note lives on the
  realization it explains and is selected by the reader's base language, so the
  key is load-bearing the moment a language is taught to more than one audience
  (a German note on a shared `sw.json` must not surface for an English learner).
  Keep a note only if it changes what the learner would say or do; pure etymology
  ("wörtl. …") is cut. Load-bearing teaching (e.g. which word for "rice") is
  destined to become first-class training content, not a permanent note.

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
  `homonymOf` would encode at concept level a fact that is per-language (`kupumzika` is
  ambiguous in sw only) and rots as languages are added. Same reasoning that deleted
  `variantOf`. See the homonym rule above and kern README §2/§3.

## Legacy

The project-level `data/generated/` (`vocab-de-*.json`, `catalog.py`) is a frozen
reference snapshot of the retired v1 per-pair format; not read by the apps, not
maintained.
