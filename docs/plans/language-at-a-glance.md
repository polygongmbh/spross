# Language at a glance — a rules sheet per learned language
Plan for a short, hand-written sheet of the rules that hold for every card of the language being learned (genders and their endings, the regular plural, elision, core verbs), shown in the app and printed on the posters from one source.
Neighbors: letter sheets `../../catalog/alphabet/README.md`, notes `../../kern/docs/catalog.md`, card marks `../design.md`.

## What exists

- The posters (`../../../sprachposter`) end with a hand-written "<Language> auf einen Blick" page per learned × reader pair (`templates/grammar/fr-de.html`, the German page inside `sprachposter.html`).
  It is the legend for every mark the cards wear: why a regular plural shows nothing, what `= Pl` and `nur Pl.` mean, why `l'` cards carry `m.` / `f.`.
- The app shows the same marks (`grammar.genus` chip, `forms.pl` only where irregular) and explains none of them.
  The only rule prose a learner can reach is per-word `notes` and the alphabet's `rule` rows.
- `../../../data/reference/grammar-{de,sw,uk}.md` are markdown extracts of the DE/SW/UK poster pages, kept as reference.
  They are not a source: the poster HTML is, and nothing derives from the markdown.

## Where a learner meets it

Three places, in order of value:

1. **The mark on a card is the link.**
   On a reveal, the `m.`/`f.` chip, the `= Pl` / `nur Pl.` marks and a contracted form open the sheet scrolled to the one section that explains them (a topic, not a page).
   This is the poster legend carried over: the explanation sits where the question arises, costs the learner nothing until they ask, and is never a gate or a graded step.
2. **A home page per language.**
   One row for the learned language's sheet, stacked with the other reference material (the letter sheet, the drills' references) where the learner browses on purpose.
   Whether that is the Box tab or the letters page's neighbor is open (see questions).
   The tab bar stays three tabs.
3. **One aside line in the first round,** pointing at the sheet once, like the other first-round lines (`../design.md` § Profile & onboarding).
   Nothing recurring: no banner, no "learn the rules" nudge, no streak coupling.

Not recommended: a box or briefing moment that presents rules ("new rule unlocked").
It would turn the sheet into content the app schedules, against "the app composes the work" and "breadth over perfect retention".
The sheet stays available, never assigned.

## Content and storage

One file per learned language, `catalog/glance/<lang>.json`, **file presence is the registry** like `alphabet/`.
Prose is keyed by the reader like `hints` and `notes`; target-language forms are neutral and stored once.

```json
{ "sections": [
  { "id": "genus", "topic": "genus-marker", "title": { "de": "Zwei Genus: le & la", "en": "Two genders: le & la" },
    "blocks": [
      { "kind": "text", "text": { "de": "{m:le} maskulin  {f:la} feminin — unbestimmt: **un** / **une**" } },
      { "kind": "bullets", "items": [ { "de": "Vor Vokal … werden beide zu **l’** …" } ] } ] },
  { "id": "plural", "topic": "plural",
    "blocks": [ { "kind": "table", "rows": [ { "rule": { "de": "**-al** → -aux" }, "example": "l’animal → les anim**aux**" } ] } ] },
  { "id": "parler", "blocks": [ { "kind": "paradigm", "lemma": "parler", "gloss": { "de": "sprechen" },
      "rows": [ ["je parl**e**", "nous parl**ons**"] ] } ] } ] }
```

- **Structured blocks, prose inside.**
  Blocks (`text`, `bullets`, `table`, `paradigm`) are what both phones and the poster lay out in their own way;
  a block's words are short reader-keyed strings with one tiny inline markup (`**bold**`, `{m:le}` / `{f:la}` gender chips) that kern parses once into spans, so no platform parses markup.
  Free markdown or HTML per sheet was rejected: a phone cannot lay out poster HTML, and every renderer would grow its own subset.
- **Reader coverage degrades, never breaks.**
  A string missing for the reader falls to the learned language's own wording, then en; a block with neither is omitted.
  Lint holds that every section has a `de` and an `en` reading once the language ships both, and that `topic` values come from a closed set.
- **Topics** are a small closed vocabulary kern owns (`genus-marker`, `plural`, `plural-invariable`, `plural-only`, `elision`, `contraction`), each mapped from the card mark that raises it.
  A sheet without the topic simply shows no link, so a language grows into it.
- **Examples are plain text,** not catalog slugs: they illustrate a rule, and joining them to cards would make every vocabulary edit a sheet edit.
  A cheap lint may check that example forms exist in the catalog where tagged, later.
- A sheet holds only what is true of every card; a rule that holds for one word stays a `notes` entry (`../../catalog/areas/README.md`).
- Seed-content rule applies: a sheet is a content change and goes through a verification sweep before shipping.

## One source for app and poster

Make the catalog file the source and the poster a renderer of it.
`generate.py` already joins spec and catalog; `grammar()` stops reading `templates/grammar/<learn>-<read>.html` and instead renders `catalog/glance/<learn>.json` for the reader, with the poster's `gbox` styling.
`data/reference/grammar-*.md` are retired after migration (or kept as one-time input), and the hand-made SW/UK posters keep their HTML pages until they join the generator (`sprachposter/README.md`).

Trade-offs:

- The hand-tuned poster page may lose a little layout polish to a generic renderer.
  Mitigation: blocks map onto the markup the page already uses (heading, list, two-cell table, paradigm table, hint box), and a section may declare a column hint (`"poster": { "col": 1 }`) that apps ignore.
- A catalog-resident sheet is an app content change (bundled, audited, sweep-gated) rather than a poster-side edit; that is the price of one source and the right owner, since the app ships the catalog.
- Copying the HTML into the app instead (two copies) is cheapest today and drifts the first time a rule is corrected.

## Architecture and parity

- Parsing, reader resolution, markup spans and the topic map live in kern (`net.spross.kern`, beside the alphabet loader); a platform reads a ready list of sections and blocks and only draws it.
  This is the layer rule, and the gender chip hues come from the existing article colors (`Palette`).
- iOS and Android each need: the sheet screen (scrolling, anchored to a topic), the entry row, the mark-to-sheet link on the reveal, string-catalog chrome (`glance.title` and friends via `scripts/strings.py`), VoiceOver / TalkBack headings per section, chips that differ in shape or label and not color alone.
- Same sweep on both phones; a screenshot check on each.
- No new persisted state: no "read" flags, no per-learner data, so no schema bump.
- Offline and light: text only; an example's audio, where a recording exists, is a later addition, not part of the first cut.

## Fit with the philosophy

- **Breadth, minimum effort:** a learner who never opens it loses nothing; one who wonders about `l'` finds the answer one tap from the card.
- **Not a lesson:** the sheet adds no cards, schedules nothing and grades nothing.
  Pulling its examples into the box is not planned; phrases and words already carry them.
- **Growing box:** the sheet is complete from day one rather than unlocking with the box.
  Highlighting the sections that the learner's met words exercise is possible later, but a half-hidden sheet costs more reading than it saves.
- **Copy:** chrome says "French at a Glance" (title case in English); docs and identifiers say "sheet", not the poster's metaphor.

## Recommendation

Ship the French sheet end to end, small, then widen.

1. **Format and poster.** `catalog/glance/fr.json` (de + en readers), kern loader and lint, `generate.py` renders it; `templates/grammar/fr-de.html` is deleted once the rendered page matches.
   This kills the duplication before the app depends on it.
2. **App screen** on both platforms, reached from one row; nothing else changes.
3. **Reveal links** for `m.`/`f.` and `= Pl` / `nur Pl.`, then the first-round aside.
4. Further languages as each poster gains a sheet (de from `grammar-de.md`, sw, uk, es), authoring the reader versions on demand.

## Questions for the owner

- **Entry point:** a row on the Box tab, on the letters page beside the alphabet, or a Settings line for the learned language?
- **Scope of readers:** de + en first, or only the readers of the posters that exist (a sheet written in German for sw/uk readers, as the German poster is)?
  Notes' "written for somebody meeting this word" rule suggests the reader key is the wording language, and that a sheet in the learned language is the shared fallback.
- **Single source** in the catalog (recommended), or does the poster repo stay the owner, with the app importing a generated JSON?
- **Reveal link:** acceptable to add a tappable affordance to the reveal's marks, or must the reveal stay free of anything but the answer (`../design.md` § Review UX rules)?
- **Verification:** the same independent review sweep as catalog content, with the poster's native-reader check for French?
- **Backlog hook:** the sheet could also give `docs/backlog.md`'s "gender hues ignore `grammar.genus`" fix a place to teach the neutral `l'` hue; wanted together?
