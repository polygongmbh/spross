# Brief: KMP core rewrite against catalog v2

**You own:** rewriting the DuoKern core (model, content parser, FSRS, Box, Session)
in Kotlin Multiplatform. **Already done for you:** the content format is finalized
and committed as **catalog v2**. This brief is the handoff.

## Start here

- **Branch from `content-catalog-v2`, not `main`.** The v2 catalog exists only on
  that branch; `main` still has v1. The Swift build is intentionally red there (its
  importer expects v1). Plan is: structure + core rewrite land **together, green**,
  then merge — so do your work on top of `content-catalog-v2`.
- **Read `catalog/README.md`** — the authoritative v2 format spec (lives with the data).
- **Read `app/docs/design.md`** — box growth, phrase unlock, session semantics. Unchanged.
- **Reference (don't preserve) the Swift core** as the behavioral spec:
  - `Kern/Sources/DuoKern/Model/Types.swift` — the model you're porting.
  - `Kern/Sources/DuoKern/Content/CatalogImporter.swift` + `CatalogModels.swift` — the
    v1 importer. **Its input shape is obsolete; its output is a CHECKLIST of what a card
    needs — but the `Card` struct itself is de-centric v1 legacy. Redesign it, don't
    reproduce it (see "Card: redesign, don't preserve").**
  - `Kern/Sources/DuoKern/FSRS/` — **legacy FSRS-5. Do NOT port** — implement FSRS-6 fresh (see below).
  - `Kern/Tests/DuoKernTests/` — especially `Fixtures/trainer-golden.json`.

## FSRS: implement FSRS-6 fresh (not a port)

Decision (2026-07-21): **migrate to FSRS-6**; do not port the Swift FSRS-5 scheduler.
The Swift `FSRS/` is legacy — grown FSRS-5 values with a *fixed* decay. FSRS-6 makes
the forgetting-curve decay a learnable parameter, so the rewrite is the moment to do
the scheduler fresh rather than carry FSRS-5 forward. This deliberately trims the port:
the FSRS-5 tests below stop being the spec.

- **21 weights** (w0–w20), vs FSRS-5's 19. `w20` = decay, default **0.2**, range 0.1–0.8
  (FSRS-5 used a fixed decay of 0.5). Treat decay as a real parameter from the start.
- **Golden vectors are re-pinned by *copying*, not self-generating.** The FSRS-5
  `FSRSTests` / `FSRSPropertyTests` values are obsolete — drop them. Copy the reference's
  own **published, version-tagged FSRS-6 test vectors** verbatim (ts-fsrs's FSRS-6 test
  suite or py-fsrs), pinning the exact reference version as the old brief pinned
  ts-fsrs v4.7.1. Copied = maximum provenance: the numbers are the maintainers' asserted
  outputs, not something our harness could mis-feed. Green against them *is* "the
  scheduler is correct." Only self-generate to fill a gap the reference doesn't publish —
  and then commit the harness + pinned version, as an explicitly lower-authority tier.
- **Don't fabricate a 0.8 golden vector.** Retention enters FSRS only in the interval
  formula; once the copied vectors pin that formula and the stabilities, feeding 0.8 just
  substitutes a constant into an already-verified function — no conformance value, lost
  provenance. Product-path assurance at 0.8 goes in the **normal test tier**: a plain,
  self-computed, clearly-labeled behavioral test (card at DR 0.8 schedules ~N days), never
  dignified as a reference golden vector.
- **`trainer-golden.json` is unrelated — port it faithfully.** It is number-spelling
  content (`67 → "siebenundsechzig"`), not the scheduler; the FSRS-6 switch does not
  touch it. It stays a hard contract.
- **Desired retention — two defaults, don't conflate.** Engine reference default 0.9
  (the golden-vector anchor). Product `BoxConfig.desiredRetention` default **0.8** — a
  deliberate breadth/fluency default (maximizes total recallable vocabulary, accepting
  that some words won't stick and get parked as leeches), not a port artifact. No slider.
- **Hand-roll the scheduler, not the optimizer.** The scheduler is ~200 lines of pure
  math — implement it in `commonMain`. A JVM FSRS library can't compile to Kotlin/Native,
  so a runtime dependency would break the shared core; the reference impls stay *oracles*,
  not deps. Weight *optimization* (fitting w0–w20 from review logs) is the hard part —
  keep it out of the core, behind a clean "weights in" boundary; offload to `fsrs-rs`
  offline/server-side if/when wanted. Not needed at current scale (ship default weights).

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

Content languages are now **de, en, sw, uk** (each `<area>/<lang>.json`). en is a full
content language but genderless (like sw): no `-f` feminine realizations, `plural` only
for irregular/pluralia-tantum. Any pair is still just a join of two `<lang>.json` files;
the de-specific `Card` fields (`german`, `article`) only populate when de is a side, so
generalize the current de-centric `LanguagePair`/field naming as you add en-bearing
pairs (en↔de, en↔sw, …). Which pairs to ship is an app decision, not the parser's.

### Card: redesign, don't preserve

The v1 `Card` (`Types.swift:22`) is denormalized and German-anchored — treat it as a
checklist of what the engine consumes, then design a clean, **language-symmetric** card.
Legacy to drop:
- `german` + `translation` — hard-codes German-on-one-side; cannot represent `en↔sw`,
  `sw↔uk`, or en-bearing pairs. Carry **two realizations by side/role**, each a
  `{text, synonyms, grammar, note}` mirroring the format, and let `Direction` pick which
  is prompt vs. answer.
- `article` + `plural` — German-only grammar flattened to two strings; drops sw/en/uk
  `plural` and the `"="`/`"only"` sentinels. Keep `grammar` structured, per side.
- `note: String?` — loses the explanation-language keying and which realization it
  explains (and v1 also swallowed accepted alternatives into a joined string). Keep the
  `notes` map on its realization; keep `synonyms` as a real list.
- `pair: LanguagePair` (de-sw | de-uk enum) — generalize to any ordered language pair.

Keep the clean concept-level parts: `id` (`area/slug`), `kind`, `area`, `emoji`,
`seedIndex`, and `components` (was `componentIDs`).

**Field shapes: see `catalog/README.md`** — it is authoritative for the concept spine,
realization fields, grammar keys, and `notes`. Don't re-derive them here. The mappings
below are source → the information a card needs (land it on your redesigned model):

- `id` = `area/slug` (kind no longer in the id). Scheduling key is `id|direction`,
  extended to `id|direction|form` for synonym recognition (below).
- **`synonyms` — direction-asymmetric scheduling.** A realization's `synonyms` are
  equivalent surface forms of ONE concept (true synonyms, spelling, phrase gender-agreement
  forms — NOT distinct concepts). They grade differently by direction:
  - **Producing INTO this language** (answer side): one card, accept `text` ∪ `synonyms`;
    a single schedule. (Producing only ever requires one correct form.)
  - **Recognizing FROM this language** (prompt side): one card **per form** (`text` and
    each synonym), each **scheduled separately** — recognizing установа→Amt is different
    knowledge from відомство→Amt. Extend the scheduling key with the form. Trivial forms
    (a phrase's other gender) just become easy recognition cards; harmless.
  This is intentional (full model), not accept-list-only. It does NOT apply to
  `feminineOf` (those are separate concepts) — synonyms stay one concept, many forms.
- **each side's realization** ← that language's `<lang>.json` entry, carried whole:
  `text`, `synonyms` (a real list, not joined into text), structured `grammar`, `notes`
  map. de `grammar` has `gender`+`plural`; the v1 `strippedPlural` hack is gone. Two
  `plural` sentinels need rendering, not literal display: `"="` → "= Pl." (identical to
  singular), `"only"` → "nur Pl." (pluralia tantum). Notes are keyed by explanation
  language and stay on the realization they explain (nil if absent).
- **Verb-prefix grading:** read `catalog/languages.json`. A language's
  `optionalVerbPrefixes` is an ARRAY of infinitive citation markers (en `["to "]`, sw
  `["ku","kw"]`), each **optional on input** for `kind == verb`: a leading occurrence of any listed prefix
  is stripped before comparing, so `cook`==`to cook`, `pika`==`kupika`, `enda`==`kwenda`.
  Display keeps the full form. Over-listing is safe — every verb is an infinitive, so
  stripping only ever yields the stem.
- `componentIDs` ← the phrase concept's **`components` list** (authored slugs, same
  area) — **read them, don't re-derive**. The fragile v1 `PhraseLinker` matcher is
  retired. Empty list = no unlock gate.
- **`feminineOf` (nouns):** the feminine sibling of its base. **Direction-aware, not
  learner-aware** — a pair mixes directions (`mixedDirections`), so both directions
  live in one box regardless of which language the user is "learning." Emit a `Card`
  for a direction only when THAT direction's ANSWER (produced) language has a distinct
  feminine realization (de always; uk where distinct; sw never). So de↔sw yields a
  produce-de card only; the produce-sw direction has no distinct answer → no card.
  Never an autogenerated answer. On the prompt side, if the prompt language has no distinct
  form, show base word + a **♀** marker (U+2640, female sign — not a woman emoji) to
  disambiguate from the base card (`mwalimu ♀ → Lehrerin`); ♀ is display-only, never
  in the graded answer. Card icon = the concept's own female-specific `emoji` if it has
  one (`👩‍🏫`), else the base emoji + ♀. A future non-German pair (sw↔uk) should
  suppress these siblings entirely.
- `seedIndex` = global introduction order, flattened from `areas.json` (group order →
  area order) then each `concepts.json` position (words then phrases).

## Suggested build order

1. **Model** (`Types.swift` → Kotlin). Fixes the coordination point everything depends on.
2. **Parser** (v2 → `[Card]`). Leaf module; fixture-test against a couple of areas by
   hand-checking a handful of cards. This is the first thing to get right.
3. **FSRS** — implement **FSRS-6** fresh; golden vectors regenerated from an FSRS-6
   reference and green (see the "FSRS" section). Do not port the FSRS-5 tests.
4. **Box** (budgets, health gate, phrase unlock, leeches) then **Session** (composer, drain).
5. Wire the app to the KMP core; retire the Swift `CatalogImporter`.

## Open decisions (yours to make)

- Exact `id` string (`area/slug` proposed; anything stable works — no parity needed).
- KMP module/package layout and where the app consumes it from.

## Pending content change (heads-up, not blocking)

Phrases are still poster-dense (many words per sentence) and are slated for a
**deconstruction pass** into simpler combine-only sentences. That will change the
phrase set and re-seed each phrase's `components`. Structure/field shapes won't
change — but don't hard-code the current phrase inventory.
