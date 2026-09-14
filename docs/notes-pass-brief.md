# Notes pass — continuation brief

Status as of 2026-09-14.

## What shipped

- **873 of 930** catalog notes carry an own-language wording (was 78).
  527 redundant German notes cut after audit.
- Early areas (first 12): jargon = 0 everywhere, later-word = 0 for 6/8 languages.
- `scripts/notes-vocabulary.py` committed (report-only).
- Rules tightened in `catalog/areas/README.md` § "How a grammar rule gets taught".
- Area order: pronouns and connectors before place (`areas.json`).
- Items 1 (areas 13+ repair) and 3 (qualities split) filed in `catalog/backlog.md`.
- Items 4 (lost German-note facts) and 5 (Swahili native checks) done.

## Open: `senseOf` + first content

Design: `docs/sense-of.md`. Implementation + content in one pass.

### Engine

`CatalogConcept` gains `senseOf: String?`, parallel to `feminineOf`.
`Card` gains `senseOf: String?` and `promptSenseMarker: Boolean`.
`Catalog.join()` gets a branch parallel to `feminineOf` (~line 143):
- Target doesn't realize sibling → skip; fold source text as **synonym** on the base card.
- Source doesn't realize sibling → borrow base source, disambiguate with emoji.
- Both realize → normal two-card emit.
Grading: base answer for a sense sibling = `Wrong` (not `Typo` like `feminineOf`).
Parser validates: same area, same kind, no chaining.

### First content: `to-know-person`

```json
{ "slug": "to-know-person", "kind": "verb", "emoji": "🤝", "senseOf": "to-know" }
```

| lang | realizes `to-know-person`? | text |
|---|---|---|
| de | yes | kennen |
| fr | yes | connaître |
| it | yes | conoscere |
| es | yes | conocer |
| eo | yes | koni |
| en | no | — (synonym `to know` folds onto base) |
| sw | no | — |
| uk | no | — |

The `eo conversation/to-know` note currently glosses `scii` with `koni` —
once `to-know-person` is a card, that note goes.

### Other candidates to check

`to-play` (de spielen covers instruments and games; fr jouer/jouer de, it giocare/suonare split),
`to-leave` (de verlassen/weggehen, fr quitter/partir),
`wall` (de Wand/Mauer, fr mur/paroi — but this is a noun split, senseOf is verb-only today).

### Test plan

- `CatalogLintTest`: `senseOf` target exists, same area, same kind, no chain.
- `RealCatalogJoinTest`: de→en skips `to-know-person` and folds `kennen` as synonym;
  en→de emits two cards with emoji disambiguation; de→fr emits two normal cards.
- `:kern:jvmTest` green, `catalog-format.py --check` clean.
