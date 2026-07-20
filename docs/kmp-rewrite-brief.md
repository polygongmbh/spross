# Brief: KMP core rewrite against catalog v2

**You own:** rewriting the DuoKern core (model, content parser, FSRS, Box, Session)
in Kotlin Multiplatform. **Already done for you:** the content format is finalized
and committed as **catalog v2**. This brief is the handoff.

## Start here

- **Branch from `content-catalog-v2`, not `main`.** The v2 catalog exists only on
  that branch; `main` still has v1. The Swift build is intentionally red there (its
  importer expects v1). Plan is: structure + core rewrite land **together, green**,
  then merge — so do your work on top of `content-catalog-v2`.
- **Read `data/README.md`** — the authoritative v2 format spec.
- **Read `app/docs/design.md`** — box growth, phrase unlock, session semantics. Unchanged.
- **Reference (don't preserve) the Swift core** as the behavioral spec:
  - `Kern/Sources/DuoKern/Model/Types.swift` — the model you're porting.
  - `Kern/Sources/DuoKern/Content/CatalogImporter.swift` + `CatalogModels.swift` — the
    v1 importer. **Its input shape is obsolete; its OUTPUT (`[Card]`) is your target.**
  - `Kern/Sources/DuoKern/FSRS/` — the scheduler. Port faithfully.
  - `Kern/Tests/DuoKernTests/` — especially `Fixtures/trainer-golden.json`.

## The one hard contract: FSRS golden vectors

Everything else is free (beta, no live data — ids/encoding/schema carry no
preservation guarantee). **The scheduler is not.** FSRS-5 is golden-vector tested
against ts-fsrs v4.7.1. Port `trainer-golden.json` (and `FSRSTests`, `FSRSPropertyTests`)
as KMP tests and keep them green. That is the definition of "the port is correct."

Also preserve these behavioral invariants (see design.md):
- `phase == .new  ⟺  memory == nil  ⟺  due == nil`.
- **Introduction = first answer**, never at composition (budget accounting depends on it).
- Every answer event is an FSRS review (retries use the short-term path).
- Kern takes `now` as a parameter — never wall-clock. Keeps it pure/testable.
- Phrases unlock from their component words once those reach a stability threshold.

## What the parser must produce

Signature stays conceptually the same: given the catalog directory + a `LanguagePair`,
return the pair's `[Card]`. A **pair is a join**: for `de-sw`, walk each area's
`concepts.json`, and for every slug take the `de.json` realization as `german` and the
`sw.json` realization as `translation`. **A slug missing from the target language is
skipped** — that is the coverage model (pair-specific phrases fall out naturally).

Per-card field mapping from v2:

| Card field      | v2 source |
|-----------------|-----------|
| `id`            | `area/slug` (kind no longer in the id). Scheduling key is `id\|direction`. |
| `kind`          | concept `kind` (`noun`/`verb`/`phrase`) |
| `area`          | folder name |
| `german`        | `de.json` `words[slug].text` |
| `article`       | `de.json` `grammar.gender` (nil for non-nouns) |
| `plural`        | `grammar.plural` — **already bare** (no `"Pl."`, no `(selten)`); no stripping needed |
| `emoji`         | concept `emoji` (nouns only) |
| `translation`   | `<target>.json` `words[slug].text`, `variants` joined as before |
| `note`          | `<target>.json` `words[slug].notes.de` (the learner-facing gloss; nil if absent) |
| `componentIDs`  | phrase→word links: compute as v1 did (match phrase tokens to word slugs in the pair) |
| `seedIndex`     | global introduction order: `areas.json` group order → area order → `concepts.json` position |

Notes:
- `seedIndex` now has an explicit source of truth — `areas.json` orders groups then
  areas, and each `concepts.json` orders concepts (words then phrases). Flatten that.
- v2 grammar values are structured and bare; the v1 `strippedPlural` hack is gone.
- `notes` is keyed by explanation language (`de` only today) — see README for why the
  key is load-bearing. For now read `notes.de`.

## Suggested build order

1. **Model** (`Types.swift` → Kotlin). Fixes the coordination point everything depends on.
2. **Parser** (v2 → `[Card]`). Leaf module; fixture-test against a couple of areas by
   hand-checking a handful of cards. This is the first thing to get right.
3. **FSRS** — port + golden vectors green.
4. **Box** (budgets, health gate, phrase unlock, leeches) then **Session** (composer, drain).
5. Wire the app to the KMP core; retire the Swift `CatalogImporter`.

## Open decisions (yours to make)

- Exact `id` string (`area/slug` proposed; anything stable works — no parity needed).
- Whether `componentIDs` linking stays token-matching or moves to an explicit
  `components` field in `concepts.json` (cleaner, but a format change — coordinate if so).
- KMP module/package layout and where the app consumes it from.
