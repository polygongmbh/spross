# Language names (`catalog/language-names/`)

What each language calls the languages, in the forms a sentence needs.

What each language calls the languages, in the forms a sentence needs.
One file per **naming** language, keyed by the language being **named** —
`language-names/de.json` says how German names Swahili, `language-names/sw.json` how Swahili does.
Every declared language names every declared language, itself included.

Per-language app metadata — the picker's self-name, the flag, the articles — is `languages.json`.

```json
{ "languageNames": {
    "sw": { "name": "Suaheli", "in": "auf Suaheli", "variants": ["Kisuaheli"] },
    "uk": { "name": "Ukrainisch", "in": "auf Ukrainisch" } } }
```
- `name` — the citation form, and what `{language}` resolves to.
- `in` — the "in X" adverbial **including its adposition**
  (de "auf Deutsch", es "en alemán", sw "kwa Kijerumani", uk instrumental "німецькою").
  Required: a sentence carrying `{language-in}` supplies no preposition of its own,
  because which one it is, and whether there is one at all, is what differs between languages.
- `speak` / `learn` — the verb-object forms, optional;
  a language whose object looks like the citation form authors neither.
  Ukrainian is the one that needs them: instrumental "німецькою" after *розмовляти*,
  accusative "німецьку" after *вчити*.
- `variants` — accept-only alternates, never displayed (de "Kisuaheli" beside "Suaheli").
- `notes` — keyed by explanation language, exactly as a realization's `notes` are.

**Language markers.** A realization may name the language being LEARNED instead of hardcoding
one, with `{language}`, `{language-in}`, `{language-speak}` or `{language-learn}`:
```json
"im-learning-your-language": { "text": "Ich lerne {language}." },
"how-do-you-say-this":       { "text": "Wie sagt man das {language-in}?" }
```
Both sides of a pair resolve against **their own** table's entry for the **target** language,
so a de→sw learner reads "Ich lerne Suaheli." and answers "Ninajifunza Kiswahili."
The marker's presence IS the declaration — no concept field says a text is language-dependent:
a mechanism serving a small corner of a vast catalog stays that low-profile.
Prefer content shape — a marker, a file's presence, a key that exists — over a schema field,
an enum or a registry, and propose the field only where two behaviors genuinely diverge.
(A `names: source|target` field with its enum was planned here and cut for that reason,
which simplified the semantics to target-only in the same stroke.)

Pick the marker whose form keeps the sentence grammatical for **every** named language.
Where a language needs a preposition no form carries (Spanish "un poco **de** alemán"),
author it in the sentence around `{language}`.

Rules, enforced at load: at most one marker per string, only the four forms above, and never
at the start of a string — nothing re-capitalizes what a marker inserts. A side whose table
has no entry for the target drops that concept from the join,
the same honest-out as a missing realization.
