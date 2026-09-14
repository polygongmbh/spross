# Notes pass — continuation brief

Status as of 2026-09-14. Pick up any item independently.

## What shipped

- **873 of 930** catalog notes now carry an own-language wording (was 78).
  527 redundant German notes were cut after audit.
- Early areas (first 12) are clean on both axes:
  jargon = 0 everywhere, later-word = 0 for 6/8 languages (5 justified residuals in fr/it).
- `scripts/notes-vocabulary.py` is committed, report-only.
  Run with `--until 12` for the cleaned early areas, no flag for the whole catalog.
- Rules tightened in `catalog/areas/README.md` § "How a grammar rule gets taught":
  notes must use seeded words, must not name grammar, must show-not-gloss,
  invariance is not worth a note, and a reader note is an addition not a replacement.
- Area order: pronouns and connectors now come before place (areas.json).

## Open items

### 1. Areas 13+ vocabulary repair (~400 notes)

`python3 scripts/notes-vocabulary.py` shows ~342 later-word + ~56 jargon notes
in areas 13–42 (people through idioms).
Same three-agent split as the early pass works: sw+es, fr+it, eo+uk+en+de.
The agents' brief is identical to what ran for the early areas,
just drop the `--until 12` scope.
Jargon bites equally hard wherever it sits; later-word notes bite less
because the learner holds ~300+ concepts by area 13.
The "show, don't gloss" rule and the "delete if you can't say it from seeded words" rule
both apply — the agents had those this time and applied them well.

### 2. `senseOf` — concepts that split in some languages

Design filed at `docs/sense-of.md`.
Generalizes `feminineOf`: a `senseOf` field on `CatalogConcept`,
languages that split realize both, the engine skips the sibling where the target doesn't.
Skipped sibling's source text folds as a **synonym** (actively taught).
Grading is strict (typing the base for a sense sibling = Wrong).

Implementation: `CatalogConcept.senseOf`, `Card.promptSenseMarker`,
a branch in `Catalog.join()` parallel to `feminineOf`, a second pass to fold
skipped siblings' source text. Prompt disambiguation via the concept's emoji.

First content to land with it: `to-know-person` (de kennen, fr connaître,
it conoscere, es conocer, eo koni; en/sw/uk omit).
Look for other candidates: `to-play` (spielen/jouer split instrument vs game in some languages),
`to-know-how` (savoir-faire), evening greetings that also arrive (es Buenas noches).

### 3. Catalog area order refinement

The compose-group reorder shipped (pronouns, connectors, verbs, place, ...).
Still open from the audit agent's report:
- `qualities` at 47 concepts past the ~40 soft cap.
  Clean seam: core sensory adjectives (~30) vs abstract relational/comparison (~17).
  `scripts/catalog-move.py --create` carries the split.
- Whether the `start` group order (greetings, conversation, questions, language) is optimal —
  the audit agent did not test that axis.

### 4. German notes that stayed — a few lost facts

Three specific facts left with a cut German note and never moved to the own-language side:
- `sw verbs/to-send`: the `kutuma`/`kupeleka` contrast — can't restore it,
  `kupeleka` is its own card. Wants a contrast phrase if it wants anything.
- `it body/hand`: WHY `la mano` is feminine (-o ending) — restored already.
- `fr kitchen/pan`: `le poêle` is masculine — not yet restored.

The `fr kitchen/pan` one is a one-line fix:
the `fr` note currently says `la poêle pour cuire · le poêle chauffe la maison.`
but doesn't say `le poêle` is masculine — add `(masculin)` or show the article contrast.

### 5. Swahili claims wanting a native check

From the authoring and repair agents' own uncertainty lists:
- `neno jingine` (class 5 `-ingine` → `jingine`) — standard?
- `hamna` as plural negative of `kuwa na` — standard?
- `qualities/beautiful`: `urembo ni wa kuvutia` — slightly bookish?
- `greetings/sorry-to-hear-that`: `Pole! — Asante.` as the standard reply.
- `qualities/old`: `nyumba kuukuu, meza kuukuu` — deliberately N-class-only
  to avoid a concord claim; does `-kuukuu` actually agree?

### 6. `eo conversation/to-know` glosses `scii` with `koni`

`koni` is not a card anywhere. By the rule that a note explaining a word with
another one that deserves a card should lead to adding that card,
this is the first `senseOf` content candidate — but it can also land as a plain
concept with synonyms on the languages that don't split, without waiting for the engine work.
