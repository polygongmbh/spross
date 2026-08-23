# Catalog schema — engine-side rules

What the engine requires of the catalog, and the lint that holds it. The file format itself is `../../catalog/README.md`'s.
Engine contract: `../README.md`.

- `languages.json`: `articles` (`../README.md` §1).
- `areas.json`: a group's `areas` is an array of **objects** (`{ "area", "emoji" }`).
  The emoji is language-neutral display metadata, so the catalog owns the area icon
  and both apps read the same one.
  The parser rejects unknown keys and validates the emoji with the concept-emoji rule
  (non-blank, ≤ 12 chars, every char ≥ U+2000), so a new area cannot ship without one.
  `AreaGroup.areas: [String]` is unchanged — the ordered names every consumer flat-maps;
  the emoji rides alongside in `AreaGroup.areaEmojis: [String: String]` and is read via
  `Catalog.areaEmoji(area) -> String?`, the language-neutral sibling of `areaTitle`.
- `areas/<area>/<lang>.json`: an optional `subtitle` beside `title`, read via
  `Catalog.areaSubtitle(area, lang) -> String?` — the same shape as `areaTitle`, so a
  reader gets it in their own language or not at all. `title` therefore stays a plain
  NAME: it is also the produce prompt's disambiguating cue, which no consumer trims.
  Lint (`subtitlesAreCompletePerAreaAndDistinctFromTheTitle`): an area authoring one
  authors it in every declared language, and it neither contains the title nor a `·`.
- Realization: `notes` is selected by the profile's SOURCE language at join time with **no
  cross-language fallback** — a `de` note never surfaces for an `en`-source learner, and a
  source nobody has authored notes for is note-less rather than served another language's.
- Realization: `variants: [String]` next to `synonyms` — a **display/accept distinction
  only**, never a scheduling one (`../README.md` §3): synonyms rotate as recognition prompt forms and
  show on reveal; variants are accepted silently and never prompted.
  A form nobody can type is a variant, never a `text`: an embedded `" / "` is untypeable,
  so a Sie/du pair is `text` = Sie-form + `variants` = [du-form].
- **CatalogLintTest** (permanent, on the real catalog) enforces:
  parse/shape/order rules, slug charset (no `|`), seedIndex uniqueness, synonyms ≠ text,
  no duplicate synonym/variant entries, no `" / "` in text, components resolve same-area,
  feminineOf resolves, concept emoji well-formed, every manifest area carries an emoji.
- **Homonym gates** (no schema field — the area label is the disambiguator,
  `../README.md` §2/§3).
  Lint owns what the engine cannot fix, runtime tolerates the rest:
  - `noPromptCollisionWithinAnArea` — a display-identical prompt inside ONE area is a hard
    error: the area cue would be identical, so the prompt stays unanswerable. Fix in content.
  - `noConceptPairCollidesInTwoLanguages` — the same pair colliding in two languages means
    one meaning authored twice; unify (the `variantOf` ruling). Caveat when it fires: check
    it is not simply two languages independently merging a distinction de/en do draw —
    `relax`/`rest` collided in sw AND uk while de (sich entspannen/sich ausruhen) and en
    keep them apart, so the fix was a precise uk realization, not a deletion.
  - `crossAreaPromptCollisionsAreKnown` — pins the tolerated cross-area set, so adding
    `nature/river` next to `bedroom/pillow` (both sw `mto`) fails the gate instead of
    silently minting an ambiguous prompt. Comparison is case-SENSITIVE: `Husten`/`husten`
    is a real visual distinction and must stay legal.
- `catalog/alphabet/<lang>.json` → `Alphabet`/`AlphabetEntry` (`AlphabetParser`, hand-parsed
  on CatalogParser conventions; `JsonSupport` gained `optionalBoolean` and `stringListMap`).
  The registry is file presence — `Catalog.alphabet(lang)` is null where no file is
  authored — and alphabet reads fold into the fingerprint (content: editing one recomposes
  a stale session once on upgrade; the audio manifest stays fingerprint-exempt). Example
  resolution splits target and reader halves (`alphabetExample` / `exampleMeaning`) so the
  sheet degrades per reader instead of erroring. Lint: shape/closure/homophone/gap rules on
  synthetic JSON in `AlphabetFixtureTest`; real-content rules in **`AlphabetLintTest`**
  (declared-language files only, own-language example realization, names on drill-true
  letters, exactly-one-gap on gap rows, letters-manifest glyph collision).
  `letters{}.matches == name` is WAIVED — the audio manifest schema rejects the field, so
  the name↔recording check is a manual listening pass (backlog).
- `catalog/language-names/<lang>.json` → `LanguageName` (`CatalogParser.parseLanguageNames`), read
  via `Catalog.languageName(reader, named) -> LanguageName?`. The registry is file presence,
  as it is for an alphabet, and the reads are **TRACKED** — a name lands inside joined card
  texts, so editing one restamps a running box exactly as a realization does (the audio and
  frame exemptions do not apply). `in` is Kotlin's keyword, so the field is `inForm`.
  The table reaches PAST the five the app teaches: it also names every language the country
  atlas lists, which is what the atlas drill's language vocabulary is made of.
  Lint: **`CatalogLanguageNamesLintTest`** (declared-language files only, every declared
  language names every declared language including itself, every atlas code named by every
  reader, forms trimmed and non-blank, `in` present, note keys are declared readers).
- `catalog/countries/` → `CountryAtlas` + `CountryName` (`CountryAtlasParser`, hand-parsed on
  the same conventions): `atlas.json` is the language-neutral manifest (slug, flag, the
  languages spoken there, tier 2..4) and `<lang>.json` the realizations beside it. Read
  through the **RAW** source, not the fingerprinting wrapper — the atlas joins no card, so
  editing it must not restamp a running box (the frames' exemption, for the frames' reason).
  Language names are not repeated here; they come from `catalog/language-names/`, which is why a
  manifest code no table names is a contradiction lint reports rather than a blank the drill
  renders. Tier 1 is in no file — it is derived per profile from the pair being learned.
  `Catalog.countryDrillContent(source, target)` joins a pair in manifest order and is null
  where either side has no file or the join is empty.
  Parse-shape rules (unknown keys, slug/code charset, duplicate rows, a tier outside 2..4,
  a country naming an undeclared language) hard-fail the load, so lint carries only what
  CONTENT alone can break: **`CountryAtlasLintTest`** (declared-language files only, every
  slug realized in every declared language, every manifest code named by every reader, every
  code spoken in some listed country, the five app languages entering at tier 2, slugs
  disjoint from concepts and frames, a flag and a nationality on every row, forms
  trimmed/deduped/never echoing their text, note keys are declared readers, and every
  ordered pair joining an atlas with a tier-1 country in it).
- **Language markers** (`{language}`, `{language-in}`, `{language-speak}`, `{language-learn}`)
  in a realization's text/synonyms/variants. No schema field declares them: the marker's
  presence is the declaration, and it always names the profile's TARGET, so each side of
  `join` resolves against ITS OWN table's target entry. A side that cannot name the target
  drops the concept — the honest-out a missing realization already has, extended to the
  join predicate. Parse rules (one marker per string, known forms only, never
  string-initial) fail the load like any other; `CatalogLintTest`
  (`languageMarkersOnlyAppearWhereTheyResolve`) additionally pins that no `{language…`
  reaches a note, a grammar value, a heading or a name table, where nothing would resolve it.
  A frame text may carry one too: `phraseTemplates` resolves before the `PhraseTemplate` is
  built, so `{slot}`/`{count}` filling never meets a marker, and `CatalogFrameLintTest` adds
  the two rules only a frame can break — text and variants agree on carrying one, and a
  marked frame joins every pair its realizations otherwise allow.
- `catalog/drills/` — the sentence frames, a top-level sibling outside `areas.json`
  (format owned by `catalog/drills/README.md`). A frame is a concept + per-language realizations,
  joined at runtime like a card, but it is not a card: no area, no `seedIndex`, outside the
  phrase-unlock gate. **Frames are read through the RAW `CatalogSource`, not the
  fingerprinting wrapper** — the same exemption the audio manifest has, and for the same
  reason: a frame edit can never change the card join, so it must not restamp and recompose
  a running box. An absent `drills/` folder is legal. Lint: **`CatalogFrameLintTest`**
  (slug shape/uniqueness/disjointness from concepts, one `{slot}` per text and per variant,
  `{count}` ⟺ `count` and only on a `numbers` frame, note keys are declared languages);
  vocab grounding of every answer side in **`PhraseVocabAuditTests`**.
