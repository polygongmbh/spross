# `senseOf` — concepts that split in some languages

## The problem

Some concepts split in a few languages but not others.
de `wissen`/`kennen`, fr `savoir`/`connaître`, it `sapere`/`conoscere`, es `saber`/`conocer`,
eo `scii`/`koni` are five senses where en, sw and uk use one word.
Today the second sense lives in notes or is untaught.

When the catalog needs a note in every target language for the same fact,
that fact has outgrown notes and wants structure.

## Design: generalize `feminineOf`

A new field `senseOf` on `CatalogConcept`, parallel to `feminineOf`:

```json
{ "slug": "to-know", "kind": "verb", "emoji": "🧠" },
{ "slug": "to-know-person", "kind": "verb", "emoji": "🤝", "senseOf": "to-know" }
```

Languages that split realize both; languages that don't realize only the base.

### Join rules

- **Target doesn't realize the sibling**: skip the card.
  The sibling's source text folds into the base card as a **source synonym** —
  it is actively taught, since the learner needs to recognize both source words
  that map to the same target.
- **Source doesn't realize the sibling**: borrow the base's source text,
  disambiguate with the sibling's emoji on the prompt (`to know [🤝]`).
- **Both realize**: normal two-card emit, each on its own schedule.

### Grading

Typing the base answer for a sense sibling is `Wrong`, not `Typo`.
The whole point is that these are distinct words the learner must know apart.
(Unlike `feminineOf`, where typing the masculine form is a soft miss.)

### What changes in Kotlin

- `CatalogConcept` gains `val senseOf: String?`
- `Card` gains `val senseOf: String?` and `val promptSenseMarker: Boolean`
- `Catalog.join()` gets a branch parallel to the `feminineOf` fallback (~line 143)
- A second pass after the main loop folds skipped siblings' source text as source synonyms
- `CatalogParser` validates: same area, same kind, no chaining

### Worked example

| pair | to-know | to-know-person |
|---|---|---|
| de→fr | wissen→savoir | kennen→connaître |
| de→en | wissen→to know (synonym: kennen) | SKIP |
| en→fr | to know→savoir | to know [🤝]→connaître |
| sw→de | kujua→wissen (synonym: … none, sw base only) | SKIP |

### What it does NOT solve

`good-night` vs `good-evening` is not a semantic split — every language realizes both.
The redundancy of "only when leaving" across target languages was a symptom of
concept-level metadata sitting in per-realization notes; the notes are now removed,
since no learner of any language would confuse good-night with a greeting.

## Status

Design only. Not yet built.
