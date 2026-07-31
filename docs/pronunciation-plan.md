# Plan — letters & pronunciation reference (with audio)

A per-language reference sheet for graphemes and the sounds they make,
spoken aloud: Ukrainian in full, German and Spanish for the parts a reader cannot guess.
Delete this doc once shipped (`../CLAUDE.md` § Extended docs);
the format then lives in `../catalog/README.md` and the surface in `design.md`.

## Goal

A learner opening a new script has no entry point today.
Ukrainian vocabulary is unreadable before the alphabet;
German `sch`/`sp`/`st` and Spanish `ll`/`j`/`c` mislead a reader
who assumes their own language's values.
The reference answers "what sound is this?" and plays it,
before and alongside the box.

## The modeling decision: a grapheme is not a concept

The catalog's unit is a concept realized per language and joined per pair.
A letter has no slug, no realization, and never becomes a card —
so it does not belong in an area folder.

But its *explanations* are pair-shaped:
`Ж` is /ʒ/ for every reader,
while "wie das J in Journal" only helps a German one
and "like the s in measure" only an English one.
That is the split `notes` already models —
keyed by explanation language, selected by the reader's source language.
The new format follows it rather than inventing a second mechanism.

## Content: `catalog/alphabet/<lang>.json`

Top level beside `areas.json` and `languages.json`, one file per language, ordered.
Preserves the property that matters most:
adding a language is dropping a file, never editing one.

```json
{ "entries": [
  { "glyph": "ж", "upper": "Ж", "name": "же", "ipa": "ʒ",
    "example": "beetle",
    "hints": { "de": "wie das J in Journal", "en": "like the s in measure" } },
  { "glyph": "г", "upper": "Г", "name": "ге", "ipa": "ɦ",
    "hints": { "de": "gehauchtes h — nicht das deutsche g; ґ ist das harte g" } },
  { "glyph": "sch", "kind": "digraph", "ipa": "ʃ",
    "example": "school", "hints": { "en": "like sh in shoe" } },
  { "glyph": "s", "kind": "contextual", "ipa": "ʃ",
    "context": { "de": "am Wortanfang vor p und t", "en": "before p or t at word start" },
    "exampleText": "Sport",
    "hints": { "en": "sp- and st- begin with sh" } } ]}
```

Fields:

- `glyph` — the lowercase form, or the multigraph as written; `upper` only where a case pair exists.
- `kind` — `letter` (default) | `digraph` | `contextual`.
  The three carry different weight per language and drive how much gets authored:
  Ukrainian is almost all `letter`, German and Spanish almost all `digraph`/`contextual`.
- `name` — the letter's own name where the language has one (uk `же`, es `eñe`, de `es-zet`).
  Distinct from its sound, and separately speakable.
- `ipa` — the language-neutral anchor. Survives when no hint exists for the reader's language,
  and is the join key if cross-language sound comparison is ever wanted.
- `example` — a **concept slug**, not a string, so the sample word arrives
  with its emoji, its meaning in the reader's language, and its own audio for free.
  `exampleText` is the escape hatch when no concept fits (de `Sport`).
- `hints` / `context` — keyed by reader language, exactly as `notes` is. Optional; `ipa` is the fallback.
- `audio` — optional bundled filename; absent everywhere at first (see below).

Authoring test, unchanged from the rest of the catalog:
write it down when the learner could not derive it.
Enumerating the full German or Spanish alphabet would be noise —
only the entries that mislead earn a row.

## Audio

Measured on the current simulator, `AVSpeechSynthesisVoice.speechVoices()`:

| language | voices | note |
| --- | --- | --- |
| uk | 1 | uk-UA / Lesya |
| de | 9 | de-DE and regional |
| es | 18 | es-ES, es-MX and more |
| **sw** | **0** | no voice at any quality tier |

For the three languages in scope, system TTS covers it at zero content cost,
offline once the voice is downloaded,
and the same speaker then pronounces every catalog word on reveal —
the larger win hiding behind this feature.
Swahili has no voice at all and would need recordings or nothing.

**Decision: TTS by default, optional recorded override.**
The `audio` field lets recordings arrive per-language later without a format change
and gives the Swahili gap a place to be filled.

Two constraints the implementation has to respect:

- **The speakable unit is a word, not a glyph.**
  TTS reads a bare letter unreliably — it names it, spells it, or refuses.
  Speak the example word for the sound, and `name` for the letter's name: two actions, not one.
- **TTS is a platform capability, so it stays app-side.**
  The kern is pure and cannot hold a synthesizer.
  Kern owns the parsed table and lookup;
  the app gets a small `Speaker` facade over `AVSpeechSynthesizer`,
  which Android later mirrors with `TextToSpeech`.
  Watch and widgets stay silent — they are decode-only surfaces.

## Surface

The trainer hub (`App/Sources/Screens/TrainerHubView.swift`), which is already target-scoped
and already hosts reference material — `TrainerLanguagePack.tensReference` is the precedent
for a pack field that is a lookup table rather than a drill.

An **Alphabet** entry opens a reference sheet: glyph, name, IPA, hint, example, speak button.
A drill (glyph → sound, or heard word → letter) is a natural second step
on the existing ramping infrastructure, not part of this plan.

## Phasing

1. **Format** — schema section in `catalog/README.md`; parse into kern
   (`kern/.../catalog/`, alongside `languages.json` handling); lint rules in `CatalogLintTest`:
   unknown `example` slug is an error, `hints` keys must be declared languages,
   every entry carries `ipa` or at least one hint.
2. **Content, uk first** — all 33 letters plus `'` and `ь`, `de` + `en` hints,
   examples pointing at existing concepts wherever one fits.
   Ukrainian alone proves the format and is the language that most needs it.
3. **Surface, silent** — reference sheet in the trainer hub, no audio.
   Useful on its own and independently verifiable.
4. **Audio** — `Speaker` facade, speak-word and speak-name actions,
   graceful no-op when the target language has no installed voice
   (the sw case, and any user who has not downloaded the uk voice).
5. **de + es content** — digraphs and context rules only.
   Spanish additionally needs a `languages.json` entry;
   it has no vocabulary yet, and the alphabet file can legitimately be the first thing it ships.

Steps 1–3 are shippable without any audio work.

## Risks and open questions

- **Content correctness is the real risk, not the code.**
  Pronunciation hints are where an LLM is confidently wrong in ways that stick:
  uk `г` vs `ґ`, unstressed `о` reduction, what `ь` actually does,
  es `ll` regional variation (`ʝ` vs Rioplatense `ʒ`).
  This needs the native-speaker sweep in `../../docs/sprachposter-learnings.md`,
  and it queues behind the uk plural sweep already open in `backlog.md`.
- **Spanish variety is a product call.** es-ES and es-MX differ audibly
  (`c`/`z` seseo above all), and the voice list offers both.
  The reference has to pick one and say so, or carry both.
- **Ukrainian stress is unmarked and load-bearing**, and a letters table does not teach it.
  That is a `stress` field on realizations — a separate piece of work.
  File it in `backlog.md` if this plan ships without it.
- **Which hint languages to author.** de and en cover the current readers;
  a hint's usefulness depends on the reader's own phonology,
  so the set grows with source languages, not target ones.
