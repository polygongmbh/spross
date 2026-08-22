# Country atlas (`catalog/countries/`)

The atlas drill's own content, and the one catalog file that never joins a card.

The atlas drill's own content: which countries exist, which languages they carry,
and what each declared language calls them.
Drill-only — nothing here ever joins a card, so editing it never restamps a running box.
**File presence is the registry**, like the alphabet's:
no `atlas.json`, no drill; a language without `countries/<lang>.json` has no atlas for any pair it is on.

`atlas.json` is language-neutral:

```json
{ "languages": [ { "code": "de", "tier": 2 }, { "code": "fr", "tier": 3 } ],
  "countries": [ { "slug": "switzerland", "flag": "🇨🇭", "languages": ["de", "fr", "it"], "tier": 2 } ] }
```

- `tier` says how far from home a row sits, and is authored **2–4**:
  2 is the app's own five languages and their home countries, 3 the common world, 4 the regional rest.
  **Tier 1 is never authored** — it is derived per profile from the learner's own source and target,
  and the countries that carry them.
- `languages` on a country resolve into the manifest's own list, in authored order (most widely spoken first);
  the relation is many-to-many, and a country reusing an already-authored language is a cheap row.
- Language codes are ISO 639-1 and reach far past the five the app teaches from —
  every one of them needs an entry in **every** `catalog/languages/<lang>.json` (see above),
  because that table, not this one, is where language names live.

`countries/<lang>.json` is keyed by the manifest's slugs:

```json
{ "switzerland": { "text": "die Schweiz", "variants": ["Schweiz"], "grammar": { "gender": "die" },
                   "nationality": { "text": "Schweizer", "variants": ["Schweizerin"] } } }
```

- `text` is the citation form and `variants` are accept-only, exactly as a realization's are.
  German authors the article in `text` and the bare form as a variant; Spanish does the reverse,
  because RAE treats *los* Estados Unidos as the optional one.
- `nationality` is REQUIRED — each entry teaches the triple
  **country, nationality, language**: "Germany, being a German, speaking German".
  The masculine is `text` and the feminine a variant; a common-gender word (es *belga*) carries none.
- `grammar` and `notes` follow the realization schema.

Every country is realized in **every** declared language, and every manifest language is
named by every declared language — `CountryAtlasLintTest` holds both, plus the slugs'
disjointness from concepts and frames.
