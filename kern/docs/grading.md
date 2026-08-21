# Grading — how a typed answer becomes a rating

The produce path only: a recognition turn is a button self-grade (`SelfGrading`).
Leniency is safe to the extent the catalog can disprove it — that rule is the contract's
(`../README.md`); what follows is the machinery that pays for it.

## The normalizer

- **AnswerNormalizer contract** (produce only — recognition is button self-grade;
  catalog-fixture tested with "Kwaheri!", "to cook", "Der Kühlschrank ist leer.",
  "Мене звуть …"):
  normalize both sides (lowercase, ß→ss, delete joiners `-'’`, punctuation → space incl.
  `…—`, collapse whitespace) → ONE leading listed article of the answer language optional
  on both sides → iff `kind == verb`: any listed `optionalVerbPrefixes` entry (normalized
  the same way, space-preserving — en `"to "`) optional on both sides → Damerau-Levenshtein
  typo budget → article-mismatch-demotes-to-typo only when the expected
  answer's grammar carries `gender`; a leading word that reads as a **mistyped article**
  and, once dropped, makes the rest match is a typo.
  Article-like is the whole gate — no longer than any listed article
  and within one slip of one of them —
  because the article list holds exact forms only ("de Zug" for "der Zug")
  and peeling anything else is leniency the language cannot pay for:
  a language listing no article at all never peels
  (sw "muda nini" came back as a spelling slip of "lini", itself a word of the catalog).
  **Vocab reviews only** on top of that: it recurses, peeling one
  word per level, and a drill grades against a whole reading where every word names
  which time it is ("fünf vor halb sieben" is not a misspelling of "halb sieben").
  **Typo budget**: one slip per six letters (spaces excluded), floor 1,
  and zero below four letters.
  Article leniency is constructor-opt-out for drill grading:
  `AnswerNormalizer(language, articleLeniency = false)` keeps the article in `normalize`
  and only matches a form whose leading article equals the typed one —
  wrong or missing article grades Wrong (never typo-bridged);
  the one-arg init stays the lenient vocab-review default (both inits in the ObjC header).
  The budget is likewise constructor-switched for drill grading:
  `AnswerNormalizer(language, articleLeniency, maxTyposPerWord = 1)` grades **word by word** —
  each word forgives one slip FLATLY, regardless of the word's own length
  (a three-letter word forgives the same one slip a long one does),
  and a word carrying a digit forgives none
  ("21"/"29", "18:05" → "18" "05" sit one edit apart),
  and a typed answer with a different word COUNT falls back to the whole-form rule.
  What a drill must never accept is one number for another, and that danger lives inside
  the number rather than across the sentence around it:
  distinct cardinals sit ≥ 2 edits apart, so a per-word cap of 1 keeps them apart
  while the frame ("Ich habe … Hefte.") may fumble once per word.
  The cardinal sweep (TrainerTypoBridgeGuardTests) grades every 0–999 German pair
  through the real drill normalizer and proves none is accepted for another;
  audited exceptions — sw `nne`↔`nane` (4↔8, incl. tens compounds)
  and uk `дев'ять`↔`десять` (9↔10) — are gated explicitly in the sweep.
  `TrainerFormsTypoBridgeGuardTests` runs the same machinery (`TypoBridgeSweep`) over the
  **forms answer space only** — negatives, decimals, percentages, multiplicatives, fractions
  and ordinals, each pack over its own limits.
  Scoping it there is the point: two prompts can only be confused if one run grades both
  against one accepted set, and a run asks one task at a time,
  so sweeping form readings against plain cardinals would fail on `acht`↔`achte`
  for a confusion no run can produce.
  Its allowlist adds the same twins wearing form endings
  (uk `дев'ята`↔`десята` and the other three, en `eight ninths`↔`eighty ninth`)
  plus es `un décimo`↔`undécimo`, a space-only minimal pair of the language.
  Everything else the sweep found was a reading bug and was fixed there instead.
  `ClockCollisionSweepTests` is its clock half, over all 1440 times in every authored language;
  which readings a clock may share and which it may not is `../docs/clock-registers.md`.
  Vocab reviews (`maxTyposPerWord = null`, the default) keep one budget over the whole form.
  `matchingPrefixWordCount(input, answer)` is a UI-only sibling of `evaluate` — how many
  leading whole words already match, each within its own single-word budget — so a miss's
  retry field can keep the words already right and drop only the wrong tail; it never
  feeds a rating, only what the retry field starts from.

## Catalog-wide collision

- **Catalog-wide produce grading** — `CatalogAnswerGrader(normalizer, cards)`, the app's
  produce path. One card at a time the normalizer cannot tell a slip from a different word,
  so another concept's answer lands inside this card's typo budget:
  sw `kufunga` (abschließen) ↔ `kufungua` (aufschließen), en `to pay` ↔ `to say`.
  A form the join already accepts **exactly** elsewhere is that word, not a slip of this one:
  it grades `Match.OtherWord(word, meanings)` — no typo credit, and the reveal can name what
  was written (`meanings` = the source words of every owning concept, seed order).
  Exact on the prompted card always wins, so a form two concepts share stays correct;
  the feminine base leniency (`../README.md` §3) is left to its own demotion; and dropping a citation
  prefix off the INPUT reaches verb owners only (a noun never owns `kupika`).
  Only the collision is catalog-wide — nothing else about "wrong" widens.
  `RealCatalogGradingTest` sweeps every near pair of every de→{en,sw,uk} join:
  no catalog word grades as a forgiven slip of another.
