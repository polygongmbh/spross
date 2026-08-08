# Content catalog format (v2.1)

Language learning content organised **one folder per area**.
Designed for reuse (any language pair is a runtime join of shared parts),
crowdsourced per-language contribution, and a slow default learning progression.

## The key modeling decision: everything is a concept

- A **concept** is language-neutral: a `slug`, a `kind`
  (`noun` | `verb` | `adjective` | `phrase` | `idiom`), and an optional `emoji`.
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
  languages/            # what each language calls the languages, inflected
    <lang>.json         # { languageNames: { code: { name, in, speak?, learn? } } }
  <area>/               # one folder per area (basics, kitchen, …)
    concepts.json       # ordered [{slug, kind, emoji?}] — order IS introduction order
    de.json             # { title, words: { slug: realization } }
    en.json
    sw.json
    uk.json
  drills/               # sentence frames for the generated number/year/clock drills
    frames.json         # ordered [{slug, slot}] — language-neutral frame concepts
    <lang>.json         # { frames: { slug: realization } }
  audio/                # GENERATED pronunciation recordings, one folder per language
    <lang>/
      manifest.json     # { language, words: { slug: … }, letters?: { glyph: … } }
      <slug>.mp3
      letters/u<hex>.mp3
```

These files are hand-edited, and their shape is part of the review unit: an entry
that fits on one line stays on one line (`{ "slug": "seventh-heaven", "kind": "idiom" }`,
`"notes": { "en": "…" }`). Edit them in place — never round-trip a file through a
serializer to change one field, which reflows every entry and buries the real change
in a formatting diff.

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
  { "slug": "cook",    "kind": "verb", "emoji": "🧑‍🍳" },
  { "slug": "careful", "kind": "adjective", "emoji": "⚠️" },
  { "slug": "teacher-f", "kind": "noun", "emoji": "👩‍🏫", "feminineOf": "teacher" },
  { "slug": "the-fridge-is-empty", "kind": "phrase", "emoji": "🧊",
    "components": ["fridge"] } ]
```
Slugs are readable English lemmas, **globally unique across every area** (lint-enforced).
English doubles as the keying language AND a content language: the slug is the identity,
`en.json` carries the English realization (display text may differ from the slug —
verb `cook` → `"to cook"`, phrase `the-fridge-is-empty` → `"The fridge is empty."`).
- `emoji` — optional on EVERY kind, not just nouns. It is the engine's meaning cue, shown
  upfront on a first exposure and on an unsettled produce prompt (`../kern/README.md` §3),
  so the bar is that it must not teach the wrong thing: authored wherever an honest picture
  exists, absent where one does not. That is why the function words have none —
  `viel`, `jetzt`, `groß`, `wo`, `aber`, `oft` are exactly where a picture would mislead,
  and a wrong cue costs more than a missing one. A phrase takes its topic's picture, so
  sharing one with the word it is built from is expected (`the-fridge-is-empty` ← `fridge`);
  two distinct WORDS in one area sharing a picture is not, unless one names the other
  (`Zähne putzen` may wear the toothbrush's).
- `components` (phrases only) — same-area word slugs the phrase is built from;
  the box gates a phrase's unlock on those words being learned. Empty = no gate.
  A component only ever unlocks a phrase where the TARGET realizes it, so gate on a
  concept every language carries: a `feminineOf` component would leave the phrase locked
  forever in a pair whose target has no feminine form (en, sw).
- `idiom` — a figurative expression, curated (not auto-linked) for genuine
  cross-language meaning-equivalence; see "Idioms are the exception" below.
  Structurally forbidden from carrying `emoji`, `components`, or `feminineOf` —
  the parser rejects a concept that tries. Every idiom card shows the engine's
  fixed `IDIOM_EMOJI` instead (`../kern/README.md` §2), and idioms carry no
  unlock gate, so ordering (last group in `areas.json`) is what keeps them
  behind the vocabulary they presuppose.
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
{ "title": "Die Küche", "subtitle": "Hier duftet es nach Abendessen.",
  "words": {
    "fridge": { "text": "Kühlschrank", "grammar": { "gender": "der", "plural": "Kühlschränke" } },
    "cook":   { "text": "kochen" },
    "the-fridge-is-empty": { "text": "Der Kühlschrank ist leer." } } }
```
- `title` — the area's plain NAME, nothing appended. It doubles as the disambiguating
  cue on an ambiguous produce prompt, where a flavour tail glued on with `·` would
  turn a label into a sentence.
- `subtitle` — OPTIONAL flavour clause rendered under the title, one short line.
  Never the title again and never a fragment of it, and per area it is **all-or-nothing
  across the declared languages**: a clause only one language carries reads as a hole
  in every other reader's box. Lint holds both.

Realization fields — only `text` is required:
- `text` — the canonical answer/display form, nothing else (no embedded glosses/labels).
  Never bracket a disambiguator into it (`"mto (Kissen)"`) — everything in `text` has to be
  typed. Homonyms are handled by the area rule above, not inside the string.
  A form that only ever appears bound carries its leading dash (sw `-zuri`, which takes the
  noun's class prefix): it is the citation convention, grading ignores the dash, and the
  engine takes it off again wherever the dash alone would identify the answer among plain
  words (`../kern/docs/snapshots.md`). The agreement forms themselves go in `variants`.
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
  **A register pair is a swap, not a rewrite**: the du-form differs from the Sie-form in the
  address alone, and a `bitte` the Sie-form never had makes it a second sentence the slug
  no longer names (`can-you-repeat-that` says nothing about please). Lint holds the
  politeness particle equal across `text` and every alternate, in both directions.
  Which register `text` carries is the **scene's** call — the counter, the surgery and the
  office say Sie, the kitchen and the hall say du — so a phrase whose scene fixes the
  register carries no register variant at all, and only the phrases that travel between
  scenes (`whats-your-name`, `where-is-your-father`) carry both.
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
  dash (`zuri` / "-zuri") — the engine folds those away (`../kern/docs/audio.md`).
  Letters carry no `matches`: they speak a name, and that string belongs to the
  alphabet file.
- `source` — the original Commons filename; the credits screen links `File:<source>`,
  which is what keeps attribution checkable rather than merely present.
- Word files are `<slug>.mp3`; letter files are `letters/u<codepoint>….mp3`, one
  `u` + four lowercase hex digits PER CODEPOINT, never glyph-named — `й`/`ї` decompose
  under NFD on APFS and a Unicode filename has to survive git, Gradle sync and AAPT
  unchanged. A sequence rather than one codepoint because a named row may be a digraph
  (es `ch`), which a single codepoint would file under `c`. The manifest maps the glyph,
  so the name is purely internal.
- **The mp3 bytes are the Commons transcode untouched**, renamed and nothing else:
  re-encoding (including loudness normalization, so packs differ in loudness) is an
  adaptation under BY-SA. `sha256` is the digest the generator verified after the copy
  and lint re-hashes what was committed, which makes it a gate rather than a promise.
- `gain` (dB) and `lead` (ms) are the generator's own MEASUREMENT of those untouched
  bytes — how far the recording sits from the catalog's analysis target, capped by the
  headroom that file still has, and how much dead air to start past — so the files stay
  unmodified and only the player corrects them. What was measured, against which target,
  is `../scripts/audio-catalog.py`'s `ANALYSIS`.
- `snr` (dB) is a third measurement of the same bytes — peak minus noise floor, how far
  the word stands above the hiss under it — but nothing plays it. It is carried so lint
  can see the SHAPE of a pack and refuse a rebuild that quietly reintroduces noise an
  earlier sweep removed. A floor per file would be dishonest: some words have nothing
  cleaner on Commons, so the rule is on the median and the size of the bad tail.
- No `README.md` inside `audio/` — the Android sync only excludes one at the catalog
  root, so a nested one would ship in the APK. Audio schema docs live here.

Lint (`CatalogAudioLintTest`) holds the rest: every entry names a slug its language
realizes and a form some card can show, no two entries claim one spoken form with
different bytes, every file ships and is referenced exactly once, and no author is a
placeholder like "Own work" — BY and BY-SA both require naming somebody.

## Alphabet (`catalog/alphabet/`)

One file per declared language, `alphabet/<lang>.json`, entries in teaching order and
optionally grouped into `sections` — the reference sheet renders it, the letter drill
samples from it. **File presence is the
registry**: adding a language's alphabet is dropping a file, no code lists which languages
have one. A file for an undeclared language is never read; lint (`AlphabetLintTest`)
fails it loudly instead of letting it sit.

```json
{ "sections": [ { "id": "umlauts", "title": { "en": "The umlauts" } } ],
  "entries": [
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

- `sections` is OPTIONAL and groups the rows the sheet renders — the umlauts together,
  the ch/sch family together, the plain letters last. Declaring it binds every entry to a
  `section`, and the rows must then follow the declared order in contiguous runs (a parse
  error otherwise: a file that reads in one order and renders in another is a trap, and
  `entries` is also what the drill samples). Titles are keyed by the READER, like `hints`.
  uk declares none on purpose — its order IS the alphabet, which a learner needs for a
  dictionary or a form, so grouping it would cost more than the reading it buys.
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
- **The drill gaps a POOL, not the one example.** Where the glyph string identifies the
  row's sound on its own, `Catalog.alphabetExamples` sweeps the whole catalog for words of
  the language carrying it exactly once — the authored example leads, the rest follow in
  seed order, and the sheet still shows only the authored one. Three things bar the sweep,
  because each means the letters can stand where the sound does not: `kind` `contextual`,
  a declared `context` (es `gu` before e/i — *seguro* has the letters, not the rule), and a
  glyph two rows share. `"mine": false` is the author's own bar for a string that lies
  anyway (de `chs`, whose only catalog hit is a compound seam). A candidate is one bare
  word: no space, no sentence punctuation.
- **Gap rule** (lint): a drill-true `digraph`/`contextual` row's resolved example
  contains its glyph EXACTLY once — zero leaves nothing to blank, and with two the blank
  can land on the wrong, position-bound instance and teach the opposite of the entry.
  `letter` and `rule` rows are exempt: their example is sheet decoration.
- No `audio` field. Letter recordings live in the audio manifest's `letters{}` (above),
  keyed by lowercase glyph — lint holds that every recorded glyph addresses exactly one
  NAMED alphabet row. A recording is only ever reached through a row's `name`, so the
  nameless rows of a shared glyph (de `ch`×3, `v-loan` beside `v-f`) are no ambiguity;
  two named rows on one glyph would be, and stay barred.

## Drill frames (`catalog/drills/`)

Sentence frames for the procedural drills:
a curated sentence whose single `{slot}` the engine fills with a generated
number, year or clock time.
A frame is a **concept** exactly as a word is — `frames.json` names it and the slot kind
it takes, and each `drills/<lang>.json` renders it in that language.
Nothing pair-shaped is stored: `de→uk` and `en→uk` read the same Ukrainian file.

**`drills/frames.json`** — the ordered frame manifest:
```json
[ { "slug": "train-departs-at",   "slot": "clock"   },
  { "slug": "i-have-n-notebooks", "slot": "numbers" } ]
```

**`drills/<lang>.json`** — the frames this language renders, keyed by slug (`de.json`):
```json
{ "frames": {
    "train-departs-at": { "text": "Der Zug fährt um {slot} Uhr ab." },
    "repeat-please":    { "text": "Wiederholen Sie bitte: {slot}.",
                          "variants": ["Wiederhole bitte: {slot}."] } } }
```

The same frames on the Ukrainian side, carrying what only Ukrainian needs (`uk.json`):
```json
{ "frames": {
    "i-have-n-notebooks": {
      "text": "У мене є {slot} {count}.",
      "count": { "one": "зошит", "few": "зошити", "many": "зошитів" },
      "notes": { "de": "Zahlwort-Kongruenz: 1 → зошит, 2–4 → зошити, 5+ → зошитів" } },
    "it-costs-n-euros": { "text": "Це {slot} євро.", "masculineNumeral": true } } }
```

**`numberNotes`** — the other root key of the same file:
what trips a learner up in THIS language's numbers, two to four lines,
keyed by explanation language exactly as a realization's `notes` are.
It describes the language, not any one frame, which is why it sits beside `frames` rather than inside one
(`sw.json`):
```json
{ "numberNotes": {
    "de": ["6, 7 und 9 sind aus dem Arabischen entlehnt: sita, saba, tisa."],
    "en": ["6, 7 and 9 are Arabic loans: sita, saba, tisa."] },
  "frames": { "…": { "text": "…" } } }
```
The numbers overview prints them under its generated reading table —
the table is derived from the trainer's own readings and can never be authored,
so this is the only place a language's irregularities get said in words.
Being a ROOT key it never enters the slug namespace: a frame may still be called `numberNotes`,
and would be realized inside `frames` like any other.
Selection is by READER with an English fallback, unlike a card's note, which has none:
a note hangs off a card that carries itself without it, while this IS the section,
so lint requires English of every language the trainer can generate.

- `slot` is `numbers`, `years`, `clock` or `fraction` — which generator fills the frame.
  A `fraction` slot draws a reduced `n/d` the answer language can read as a NOUN
  (`ein Viertel Kilo Mehl`, `un tercio de kilo de harina`);
  halves are never drawn, because German and Spanish read 1/2 adjectivally
  (`ein halbes Kilo`, `medio kilo`) and the frame has no way to decline around it.
  There is deliberately **no `forms` slot**, and one family per kind rather than one shared kind:
  a frame is grammatically bound to the family it carries —
  an ordinal frame needs the NUMERAL declined by the frame (`auf dem vierten Platz`),
  and the only agreement device runs the other way, from the numeral to the noun (`count`).
  Separate kinds also keep every `when` over them exhaustive,
  so adding `ordinal` once that agreement field exists is a new arm, never a silent fallthrough.
- **The drill is a symmetric runtime join**, like the card join:
  a frame realized in BOTH the learner's languages becomes one drill,
  and the profile decides which side prompts and which side is typed.
  A frame realized in one language only simply never appears — the coverage rule again,
  and the honest way to drop a frame a language cannot carry.
- A frame drill exists only where the **answer** language has a trainer pack:
  the slot value is generated in the language being typed,
  so a language without one can still supply prompts but never answers.
- **An absent `drills/` folder is legal** — no frames, no sentence drill.
- Frame slugs share the concept namespace and must not collide with one:
  a slug names either a card or a frame, never both.

Realization fields — `text` is required, everything else is per-language:
- `text` — the frame, carrying **exactly one `{slot}`** (and `{count}` iff `count` is authored).
- `variants` — accept-only alternate frames, the same rule as a realization's `variants`:
  the du-form beside the Sie-form, graded as correct and never displayed.
- `count` — counted-noun agreement (`one`/`few`/`many`) substituted for the `{count}` marker;
  `numbers` frames only, since there is otherwise no numeral to agree with.
- `masculineNumeral` — this frame counts a masculine or indeclinable noun,
  so the feminine numerals (uk одна/дві) must NOT be accepted:
  the frame exists to train exactly that agreement.
- `notes` — keyed by explanation language, exactly as a realization's `notes`.

Frames sit outside `areas.json` on purpose:
they are not scheduled cards,
so they stay out of the card join, out of `seedIndex` and out of the phrase-unlock gate,
and editing one never restamps a learner's box.

**Language constraints** found in review, which bind whoever authors a frame:
- Swahili clock readings start "Saa …" and drop into mid-sentence adverbial position
  lowercased ("Treni inaondoka saa mbili usiku."),
  so a Swahili clock frame must read naturally with the value inline.
- Ukrainian time-at ("о + Lokativ") does NOT compose with the nominative clock readings
  the trainer generates, so Ukrainian clock frames are predicate frames
  ("Зараз …", "На будильнику …") — fewer, but correct.
- Ukrainian year frames would need ordinal and case forms the trainer does not produce,
  so they use dictation framing, where the bare cardinal reading is natural.
- Ukrainian counted nouns must be **masculine**,
  so the trainer's canonical masculine numeral stays grammatical.
- Swahili needs "tangu mwaka …" for a year: a bare cardinal after `tangu` does not read as one.
- A `fraction` frame must read naturally with EVERY fraction the language can draw,
  which is what decides its shape per language:
  German puts the noun straight against the measure ("Ich brauche ein Viertel Kilo Mehl."),
  while English and Spanish need the partitive ("three quarters **of a** kilo of flour",
  "un tercio **de** kilo de harina") — "one quarter kilo" is not what a recipe says.

Every non-slot content word on the answer side is verified against the card join
(`PhraseVocabAuditTests`); only documented function words go beyond it.

## Language names (`catalog/languages/`)

What each language calls the languages, in the forms a sentence needs.
One file per **naming** language, keyed by the language being **named** —
`languages/de.json` says how German names Swahili, `languages/sw.json` how Swahili does.
Every declared language names every declared language, itself included.

Not to be confused with `languages.json` beside it, which is per-language app metadata
(the picker's self-name, the flag, the articles). This directory is content the learner reads.

```json
{ "languageNames": {
    "sw": { "name": "Suaheli", "in": "auf Suaheli", "variants": ["Kisuaheli"] },
    "uk": { "name": "Ukrainisch", "in": "auf Ukrainisch" } } }
```
- `name` — the citation form, and what `{language}` resolves to.
- `in` — the "in X" adverbial **including its adposition**
  (de "auf Deutsch", es "en alemán", sw "kwa Kijerumani", uk instrumental "німецькою").
  Required: a sentence carrying `{language-in}` supplies no preposition of its own,
  because which one it is, and whether there is one at all, is what differs between languages.
- `speak` / `learn` — the verb-object forms, optional;
  a language whose object looks like the citation form authors neither.
  Ukrainian is the one that needs them: instrumental "німецькою" after *розмовляти*,
  accusative "німецьку" after *вчити*.
- `variants` — accept-only alternates, never displayed (de "Kisuaheli" beside "Suaheli").
- `notes` — keyed by explanation language, exactly as a realization's `notes` are.

**Language markers.** A realization may name the language being LEARNED instead of hardcoding
one, with `{language}`, `{language-in}`, `{language-speak}` or `{language-learn}`:
```json
"im-learning-your-language": { "text": "Ich lerne {language}." },
"how-do-you-say-this":       { "text": "Wie sagt man das {language-in}?" }
```
Both sides of a pair resolve against **their own** table's entry for the **target** language,
so a de→sw learner reads "Ich lerne Suaheli." and answers "Ninajifunza Kiswahili."
The marker's presence IS the declaration — no concept field says a text is language-dependent.

Pick the marker whose form keeps the sentence grammatical for **every** named language.
Where a language needs a preposition no form carries (Spanish "un poco **de** alemán"),
author it in the sentence around `{language}`.

Rules, enforced at load: at most one marker per string, only the four forms above, and never
at the start of a string — nothing re-capitalizes what a marker inserts. A side whose table
has no entry for the target drops that concept from the join,
the same honest-out as a missing realization.

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

**Idioms are the exception.** The word-mirroring rule above is what makes an ordinary
phrase learnable: the words visibly correspond, so a learner works out which did what.
An idiom (`kind: "idiom"`, `catalog/idioms/`) is figurative by definition, so that
correspondence would be dishonest to fake — "es gießt wie aus Eimern" (lit. "it's
pouring as if from buckets") and "it's raining cats and dogs" describe the same event
with unrelated imagery, and forcing a calque onto either side would just teach the
wrong idiom. The curation bar replaces word-mirroring with **meaning-equivalence**:
ship a pairing only where another language has a genuinely equivalent expression —
same real-world function, not shared imagery — and use the ordinary coverage rule to
omit a language honestly where no such expression is known, rather than force one.
`notes` (keyed by explanation language, same field as everywhere else) carries the
literal back-translation of each side's imagery — that gap, made visible on reveal, is
the actual teaching content: not just the matching idiom, but why the words don't match.

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

## Which area a concept lives in

**An area holds a few dozen cards.** It is a shelf a learner can hold in their head and
choose to pull forward, not a drawer everything vaguely related falls into — so an area
growing past roughly forty asks to be cut along the seam a learner would name
(the doctor's visit out of health, the clock out of the everyday words, the colours out
before they ever land there). The cut is cheap: the slug is the card id and carries no
area, so nobody's schedule notices.

**No area may be the leftover bin.** An area whose name states no test for belonging will
refill, because every word that fits nowhere fits there — which is exactly what happened
to the area once called `essentials`, "Das Wichtigste im Alltag", until it had to be
dissolved into the scenes it was holding. Every area's name must therefore answer *what
gets in*, and the answer must be able to say no: a room (`kitchen`), an errand (`admin`),
a kind of word (`colours`, `qualities`), or — for the words that belong to no scene at
all — the deliberately narrow `verbs`, which admits a word only when no scene claims it.
A word that fits none of them is not homeless; it is evidence that a shelf is missing.

The area is the folder, and three things ride on it:
it is the produce prompt's disambiguator,
`components` and `feminineOf` resolve **inside** it,
and it is the unit a contributor writes and reviews.
So a concept sits with the scene it belongs to,
and a phrase gating on it travels with it —
`components` is an unlock gate, not a claim about the sentence's words,
so a phrase whose second component would stay behind simply drops it
(`old-people` keeps `person`, lets go of `old`, and unlocks a little earlier).
Orientation words (`left`, `right`) sit in `nature` for want of a better scene:
they belong to no room and to no errand,
and an area of two words would earn nothing.

Moving one is mechanical and cheap — the slug is the card id and carries no area,
so nobody's schedule notices — and `../scripts/catalog-move.py` is what does it:
it carries every language's realization verbatim,
appends words before the destination's phrase block,
and refuses a move that would part a phrase from a component,
a feminine from its base, or mint a same-area prompt collision.

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
