# Drill frames

The sentence frames the generated number, year and clock drills fill.
What the engine generates into them is `../../kern/docs/catalog.md`.

Sentence frames for the procedural drills:
a curated sentence whose single `{slot}` the engine fills with a generated
number, year or clock time.
A frame is a **concept** exactly as a word is — `frames.json` names it and the slot kind
it takes, and each `phrases/<lang>.json` renders it in that language.
Nothing pair-shaped is stored: `de→uk` and `en→uk` read the same Ukrainian file.

**`phrases/frames.json`** — the ordered frame manifest:
```json
[ { "slug": "train-departs-at",   "slot": "clock"   },
  { "slug": "i-have-n-notebooks", "slot": "numbers" } ]
```

**`phrases/<lang>.json`** — the frames this language renders, keyed by slug (`de.json`):
```json
{ "frames": {
    "train-departs-at": { "text": "Der Zug fährt um {slot} Uhr ab." },
    "repeat-please":    { "text": "Wiederholen Sie bitte: {slot}.",
                          "variants": ["Wiederhole bitte: {slot}."] } } }
```

The same frames on the Ukrainian side, carrying what only Ukrainian needs (`uk.json`):
```json
{ "frames": {
    "i-have-n-notebooks": {
      "text": "У мене є {slot} {count}.",
      "count": { "one": "зошит", "few": "зошити", "many": "зошитів" },
      "notes": { "de": "Zahlwort-Kongruenz: 1 → зошит, 2–4 → зошити, 5+ → зошитів" } },
    "it-costs-n-euros": { "text": "Це {slot} євро.", "masculineNumeral": true } } }
```

**`numberNotes`** — the other root key of the same file:
what trips a learner up in THIS language's numbers, two to four lines,
keyed by explanation language exactly as a realization's `notes` are.
It describes the language, not any one frame, which is why it sits beside `frames` rather than inside one
(`sw.json`):
```json
{ "numberNotes": {
    "de": ["6, 7 und 9 sind aus dem Arabischen entlehnt: sita, saba, tisa."],
    "en": ["6, 7 and 9 are Arabic loans: sita, saba, tisa."] },
  "frames": { "…": { "text": "…" } } }
```
The numbers overview prints them under its generated reading table —
the table is derived from the trainer's own readings and can never be authored,
so this is the only place a language's irregularities get said in words.
Being a ROOT key it never enters the slug namespace: a frame may still be called `numberNotes`,
and would be realized inside `frames` like any other.
Selection is by READER with an ENGLISH fallback, where a card's note falls back to the
language it explains instead: a note hangs off a card that carries itself without it, and its
reader is at least studying that language, while this IS the section and its reader may have
met no numbers yet. So lint requires English of every language the trainer can generate.

- `slot` is `numbers`, `years`, `clock` or `fraction` — which generator fills the frame.
  A `fraction` slot draws a reduced `n/d` the answer language can read as a NOUN
  (`ein Viertel Kilo Mehl`, `un tercio de kilo de harina`);
  halves are never drawn, because German and Spanish read 1/2 adjectivally
  (`ein halbes Kilo`, `medio kilo`) and the frame has no way to decline around it.
  There is deliberately **no `forms` slot**, and one family per kind rather than one shared kind:
  a frame is grammatically bound to the family it carries —
  an ordinal frame needs the NUMERAL declined by the frame (`auf dem vierten Platz`),
  and the only agreement device runs the other way, from the numeral to the noun (`count`).
  Separate kinds also keep every `when` over them exhaustive,
  so adding `ordinal` once that agreement field exists is a new arm, never a silent fallthrough.
- **The drill is a symmetric runtime join**, like the card join:
  a frame realized in BOTH the learner's languages becomes one drill,
  and the profile decides which side prompts and which side is typed.
  A frame realized in one language only simply never appears — the coverage rule again,
  and the honest way to drop a frame a language cannot carry.
- A frame drill exists only where the **answer** language has a trainer pack:
  the slot value is generated in the language being typed,
  so a language without one can still supply prompts but never answers.
- **An absent `phrases/` folder is legal** — no frames, no sentence drill.
- Frame slugs share the concept namespace and must not collide with one:
  a slug names either a card or a frame, never both.

Realization fields — `text` is required, everything else is per-language:
- `text` — the frame, carrying **exactly one `{slot}`** (and `{count}` iff `count` is authored).
- `variants` — accept-only alternate frames, the same rule as a realization's `variants`:
  the du-form beside the Sie-form, graded as correct and never displayed.
- `count` — counted-noun agreement (`one`/`few`/`many`) substituted for the `{count}` marker;
  `numbers` frames only, since there is otherwise no numeral to agree with.
- `masculineNumeral` — this frame counts a masculine or indeclinable noun,
  so the feminine numerals (uk одна/дві) must NOT be accepted:
  the frame exists to train exactly that agreement.
- `swahiliNounClass` — Swahili only (`KI_VI`/`JI_MA`), `numbers` frames:
  the counted noun's Bantu class, so the numeral takes its concord prefix
  (*viti viwili*, `../../docs/number-forms.md`). Absent for N-class nouns, which need none.
- `notes` — keyed by explanation language, exactly as a realization's `notes`.

Frames sit outside `areas.json` on purpose:
they are not scheduled cards,
so they stay out of the card join, out of `seedIndex` and out of the phrase-unlock gate,
and editing one never restamps a learner's box.

**Language constraints** found in review, which bind whoever authors a frame:
- Swahili clock readings start "Saa …" and drop into mid-sentence adverbial position
  lowercased ("Treni inaondoka saa mbili usiku."),
  so a Swahili clock frame must read naturally with the value inline.
- Ukrainian time-at ("о + Lokativ") does NOT compose with the nominative clock readings
  the trainer generates, so Ukrainian clock frames are predicate frames
  ("Зараз …", "На будильнику …") — fewer, but correct.
- Italian time-at contracts the preposition with the hour's article
  ("alle due" but "all'una"), and a frame cannot know which of the two a draw will need,
  so Italian clock frames are predicate frames too
  ("Adesso …", "La sveglia dice che …") — the reading brings its own copula.
- Ukrainian year frames would need ordinal and case forms the trainer does not produce,
  so they use dictation framing, where the bare cardinal reading is natural.
- A Swahili counted noun stands in its **plural** and names that plural's class:
  the numeral agrees with the noun beside it, and a class is only unambiguous there
  (*daftari* is contested in the singular, *madaftari* is plainly JI-MA).
- Ukrainian counted nouns must be **masculine**,
  so the trainer's canonical masculine numeral stays grammatical.
- French counted nouns would need the same, and its plural -s besides
  (`vingt et une assiettes`, `un euro` against `deux euros`),
  which no numeral-side agreement field can supply —
  so French carries no counted-noun frame at all, the way Spanish does not.
- French clock frames stay PREPOSITIONAL, which is the Ukrainian constraint from the other side:
  `à` never contracts with an hour word and the reading is bare,
  so `Le train part à {slot}.` composes for every draw.
  Its copula rides along as an accepted reading and is dropped where a frame does not say it
  (`../../docs/clock-registers.md` § French's bare reading).
- Swahili needs "tangu mwaka …" for a year: a bare cardinal after `tangu` does not read as one.
- Esperanto reads the clock as a bare nominative noun phrase (`la tria`) and `je` does not contract,
  so it is the one language whose clock frames may carry the preposition
  ("La trajno ekveturas je {slot}.") for every reading the ladder draws.
- An Esperanto slot in OBJECT position is wrong: the readings are nominative and a fraction noun
  would need the accusative `-n` ("Mi bezonas kvaronon…"),
  so its fraction frame puts the slot in a prepositional phrase instead ("… el {slot} de kilogramo …").
  A counted-noun frame is out for a second reason — `miliono` is a NOUN and takes `da` before what
  it counts, so no one frame can hold both `dudek unu teleroj` and `unu miliono da teleroj`.
- A `fraction` frame must read naturally with EVERY fraction the language can draw,
  which is what decides its shape per language:
  German puts the noun straight against the measure ("Ich brauche ein Viertel Kilo Mehl."),
  while English, Spanish, French and Italian need the partitive ("three quarters **of a** kilo of flour",
  "un tercio **de** kilo de harina", "un quart **de** kilo de farine", "un quarto **di** chilo di farina") —
  "one quarter kilo" is not what a recipe says.
  In French that `de` may only stand AFTER the slot: before it, `de` + `un quart` elides to `d'un quart`,
  and a frame cannot know which draw is coming.
  The frame's VERB must not agree with the drawn fraction either:
  Italian's "mi serve/mi servono" would, so its frame is built on "Ho bisogno di …".

Every non-slot content word on the answer side is verified against the card join
(`PhraseVocabAuditTests`); only documented function words go beyond it.
