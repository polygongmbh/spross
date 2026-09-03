# Content catalog format (v2.1)

Language learning content organized **one folder per area**.
Designed for reuse (any language pair is a runtime join of shared parts)
and potential future crowdsourced per-language contribution.

This file owns the file shapes and what each field means.
What earns a card and how it is worded is `areas/README.md`;
every other folder below is documented by the `README.md` standing in it.

## The key modeling decision: everything is a concept

- A **concept** is language-neutral: a `slug`, a `kind`
  (`noun` | `verb` | `adjective` | `phrase` | `idiom`), and an optional `emoji`.
  Words and phrases live in one ordered list.
- A **realization** is one concept rendered in one language (`text` + grammar + notes).
- A **pair** (de↔sw, de↔uk, later sw↔uk) is a **runtime join** on slug — never stored.
  The German side is authored once and shared across every pair that includes German.
- **Coverage may be non-uniform.**
  A concept without a realization in a language never appears in pairs involving that language.
  This is how pair-specific content works: `relax` has `de`, `en` and `uk` realizations and no `sw`,
  because the Swahili word would be the same as for `rest`.
- **Homonyms & target-language merges.** Slugs are unique, but realization *texts* are not:
  one text may legitimately serve two concepts in two areas — usually because the target language
  merges a distinction the source draws (sw `kuacha` = `verlassen` AND `aufhören`, and with an
  object `kuacha kazi` = `kündigen`).
  The **area** is the disambiguator, and the engine renders the area label on an ambiguous
  *produce* prompt only — never on recognize, where any cue strong enough to identify the concept
  would reveal the answer.
  Tolerated cross-area collisions are pinned by `CatalogLintTest`, so minting a new one has to be
  a conscious decision. Asked the other way — what does this word MEAN — the merge is simply
  honored: every concept printing the form answers for it, and the reveal names the rest
  (`../kern/docs/grading.md`).

## Layout

```
catalog/
  areas.json            # ordered GROUPS → ordered areas: the default progression
  languages.json        # per-language metadata (display name, verb citation prefix)
  areas/                # the card areas, one folder each — everything else here is a registry
    README.md           # what earns a slot, how a realization is worded, which area it lives in
    <area>/             # greetings, kitchen, …
      concepts.json     # ordered [{slug, kind, emoji?}] — order IS introduction order
      de.json           # { title, words: { slug: realization } }
      en.json
      sw.json
      uk.json
  language-names/       # what each language calls the languages, inflected
    README.md
    <lang>.json         # { languageNames: { code: { name, in, speak?, learn? } } }
  alphabet/             # the letter sheets the reference screen renders
    README.md
    <lang>.json         # { sections, entries }
  countries/            # the atlas drill's content
    README.md
    atlas.json          # language-neutral manifest: languages + countries, with tiers
    <lang>.json         # { slug: { text, nationality } }
  dates/                # the dates drill's calendars
    README.md
    <lang>.json         # { weekdays, months, numeric, patterns }
  phrases/              # sentence frames for the generated number/year/clock drills
    README.md
    frames.json         # ordered [{slug, slot}] — language-neutral frame concepts
    <lang>.json         # { frames: { slug: realization } }
  audio/                # GENERATED pronunciation recordings, one folder per language
    README.md           # the manifest schema and the provenance every recording carries
    <lang>/
      manifest.json     # { language, words: { slug: … }, letters?: { glyph: … } }
      <slug>.mp3
      letters/u<hex>.mp3
```

The json files are hand-edited, and their shape is part of the review unit:
an entry that fits on one line stays on one line
(`{ "slug": "seventh-heaven", "kind": "idiom" }`, `"notes": { "en": "…" }`),
so an area file reads as the word list it is and a reordering diff shows the new order rather than a reflow.
`../scripts/catalog-format.py --fix` applies that layout and `--check` holds it;
the rules it follows are in its own header, and it owns every catalog file except the generated `audio/` manifests.

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
  carrying its own map. Required and validated at parse time by the same rule as concept emoji,
  so adding an area fails the gate rather than silently losing its icon.

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
- `flag` — exactly ONE emoji flag sequence for chrome/badges (sw uses 🇹🇿 Tanzania).
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

**`areas/<area>/concepts.json`** — ordered, language-neutral. Order across all kinds IS seed/introduction order.
A phrase with `components` follows its area's words, so the building blocks land first;
a component-free phrase is a building block itself (a greeting, `ja`, `bitte`) and may stand anywhere,
which is how `greetings` opens the whole course on `Hallo!` and `basics` on `Ja.`:
```json
[ { "slug": "fridge",  "kind": "noun", "emoji": "🧊" },
  { "slug": "to-cook", "kind": "verb", "emoji": "🧑‍🍳" },
  { "slug": "caution", "kind": "adjective", "emoji": "⚠️" },
  { "slug": "teacher-f", "kind": "noun", "emoji": "👩‍🏫", "feminineOf": "teacher" },
  { "slug": "the-fridge-is-empty", "kind": "phrase", "emoji": "🧊",
    "components": ["fridge"] } ]
```
Slugs are readable English lemmas, **globally unique across every area** (lint-enforced).
English doubles as the keying language AND a content language: the slug is the identity,
`en.json` carries the English realization (display text may differ from the slug —
verb `to-cook` → `"to cook"`, phrase `the-fridge-is-empty` → `"The fridge is empty."`).
A verb slug always carries the `to-` prefix, so a noun and the verb it shares a lemma with
never compete for one slug (`help`/`to-help`, `work`/`to-work`).
- `emoji` — optional on EVERY kind, not just nouns. It is the engine's meaning cue, shown
  upfront on a first exposure and on an unsettled produce prompt (`../kern/README.md` §3).
  Which concepts get one, and which must not, is `areas/README.md`.
- `components` (phrases only) — same-area word slugs the phrase is built from;
  the box gates a phrase's unlock on those words being learned. Empty = no gate.
  A gate can only name a concept that HAS a card and that every target realizes:
  a `feminineOf` component would leave the phrase locked forever in a pair whose target
  has no feminine form (en, sw).
- `idiom` — a figurative expression, curated (not auto-linked) for genuine
  cross-language meaning-equivalence (`areas/README.md`).
  Structurally forbidden from carrying `emoji`, `components`, or `feminineOf` —
  the parser rejects a concept that tries. Every idiom card shows the engine's
  fixed `IDIOM_EMOJI` instead (`../kern/README.md` §2), and idioms carry no
  unlock gate, so ordering (last group in `areas.json`) is what keeps them
  behind the vocabulary they presuppose.
- `adjective` is the catch-all for single words that are neither noun nor verb:
  adjectives, adverbs, and interjections (`draußen`, `immer`, `Vorsicht`).
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
so a genuine repeat across areas is disambiguated by qualifying the slug.
The price is that **renaming a slug is a breaking act**:
it orphans the schedule and every audio pack row keyed by the old name,
and the word returns as new — so rename deliberately, never just to polish a lemma.
When one is due, `../scripts/catalog-rename-slugs.py` carries it through the concept,
every realization, every `components` reference and the shipped recordings in one run;
the pack rows outside the repo are still yours to update.

**`areas/<area>/<lang>.json`** — title + realizations keyed by slug:
```json
{ "title": "Die Küche", "subtitle": "Hier duftet es nach Abendessen.",
  "words": {
    "fridge": { "text": "Kühlschrank", "grammar": { "gender": "der", "plural": "Kühlschränke" } },
    "to-cook": { "text": "kochen" },
    "the-fridge-is-empty": { "text": "Der Kühlschrank ist leer." } } }
```
- `title` — the area's plain NAME, nothing appended. It doubles as the disambiguating
  cue on an ambiguous produce prompt, where a flavor tail glued on with `·` would
  turn a label into a sentence.
- `subtitle` — OPTIONAL flavor clause rendered under the title, one short line.
  Never the title again and never a fragment of it, and per area it is **all-or-nothing
  across the declared languages**: a clause only one language carries reads as a hole
  in every other reader's box. Lint holds both.

Realization fields — only `text` is required:
- `text` — the canonical answer/display form, nothing else: no embedded gloss, label or
  bracketed disambiguator (`"mto (Kissen)"`), because everything in `text` has to be typed.
  Homonyms are handled by the area rule above, not inside the string.
  A form that only ever appears bound carries its leading dash (sw `-zuri`, which takes the
  noun's class prefix): it is the citation convention, grading ignores the dash, and the
  engine takes it off again wherever the dash alone would identify the answer among plain
  words (`../kern/docs/snapshots.md`). The agreement forms themselves go in `variants`.
- `synonyms` — DISTINCT-KNOWLEDGE alternates of `text` (array; omit if none):
  genuinely different lexemes for the same concept that a learner must recognize
  on their own (uk `office` установа/відомство, uk `boss` шеф/керівник).
  Each entry is **prompt-worthy**: it grades as correct when producing this language,
  and takes its turn as the recognize prompt when learning FROM it — on the concept's
  one schedule, never as a unit of its own (`../kern/README.md` §3).
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

  Which of the two an alternate belongs in turns on one question — what a learner already
  knows from `text` against what they do not — and `areas/README.md` decides it.
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
  and a Swahili plural IS the noun class (`kiti`→`viti`), the most load-bearing fact about the word.
  en and uk author only what the regular pattern does not give: en beyond a bare +s
  (`knife`→`knives`), uk beyond swapping the ending — stem alternations (`ніж`→`ножі`),
  fleeting vowels (`день`→`дні`), suppletives (`людина`→`люди`), indeclinables and `-ння`
  neuters (`"="`), and phrases whose other words have to agree (`письмовий стіл`→`письмові столи`).
- `notes` — keyed by the language the note is WRITTEN IN, and that key decides who reads it.
  Key it by the FILE'S OWN language and it is the shared wording, which every reader falls to.
  Key it by another language and it is written for that reader alone, beating the shared one
  for them (`../kern/docs/catalog.md`) and reaching nobody else — so writing one is a promise
  to write it for every reader who needs it.
  Which of the two to write, and what earns a note at all, is `areas/README.md`.

## Parked areas

An area folder that is deliberately absent from `areas.json`.
The manifest is what the loader reads, so a parked area takes no seed index, joins no card
and reaches no box — it is content kept in the catalog rather than in a branch, because the
catalog is where it will land and where the gates can watch it.

Parking is for content whose GUARD is missing, never for content that is unfinished:
a half-written area belongs on a branch, where nothing has to explain it.
What earns the state is work that is done and must not yet be met —
`reproduction` waits on a quiet flag and a default-off setting, not on its own wording.

The declaration is `CatalogLintTest.parkedAreas`, beside the collision list and reviewed the
same way: a folder missing from the manifest is a hole unless that set says otherwise.
While it waits, `parkedAreasStayActivatable` holds only what decides whether it can still
land — its slugs are card ids, so one minted live in the meantime would fuse two concepts,
and a missing language file would ship a hole. The content rules do NOT apply, because a
parked area is not in `catalog.areas`; activation is a review, not a rename.
