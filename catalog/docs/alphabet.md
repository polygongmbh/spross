# Alphabet (`catalog/alphabet/`)

The per-language letter sheets the reference screen renders and the letter drill samples from.

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
