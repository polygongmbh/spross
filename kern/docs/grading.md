# Grading — how a typed answer becomes a rating

The produce path only: a recognition turn is a button self-grade (`SelfGrading`).
Leniency is safe to the extent the catalog can disprove it — that rule is the contract's
(`../README.md`); what follows is the machinery that pays for it.

## The normalizer

- **AnswerNormalizer contract** (produce only — recognition is button self-grade;
  catalog-fixture tested with "Kwaheri!", "to cook", "Der Kühlschrank ist leer.",
  "Мене звуть …"):
  normalize both sides (lowercase, ß→ss, the answer language's `diacriticDigraphs`,
  delete joiners `-'’`, punctuation → space incl.
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
  **Diacritics** are two rules, deliberately not one.
  A language's `diacriticDigraphs` (`languages.json`; German's `ä`→`ae`, `ö`→`oe`, `ü`→`ue`,
  nobody else's) fold in the normalizer beside `ß`→`ss`, for the same reason:
  the digraph is a full, established ASCII spelling of the letter, not a simplification,
  so "Kueche" grades Exact on a "Küche" card
  — and the fold lengthens the word before the budget measures it,
  so "für" ("fuer", four letters) forgives the slip three letters forgave none of.
  DROPPING a diacritic is the other rule and never reaches the normalizer:
  a substitution between two spellings of the same base vowel
  (`áàâäãå`→`a` and the other four rows, `model/Diacritics.kt`) simply costs 0
  inside the Damerau-Levenshtein distance.
  So the comparison strings keep their accents, the exact test still needs a literal match,
  and fr "ou" for "où" comes back Typo — Hard plus the correction, never Exact —
  which is what keeps the catalog-wide collision check below running on it.
  Free of the budget, it reaches the short accented words the floor hit hardest (`où`, `à`, `là`).
  Only typing-convenience accents are listed:
  `ç` and `ñ` (es `ano`/`año` is a real minimal pair),
  Esperanto `ĉ ĝ ĥ ĵ ŝ ŭ` (separate letters of that alphabet)
  and Ukrainian `й`/`ї` (distinct Cyrillic letters that merely NFD-decompose with a mark)
  stay full price.
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
  What a drill must not accept is one number for another, and that danger lives inside
  the number rather than across the sentence around it:
  most distinct cardinals sit ≥ 2 edits apart, so a per-word cap of 1 keeps them apart
  while the frame ("Ich habe … Hefte.") may fumble once per word.
  The one-edit twins the budget alone would forgive are refused by the **value check**
  (`otherNumber` in `DrillGrading.kt`, on both miss arms and never on Exact):
  two probes against `NumberReadingIndex` — the whole typed answer first,
  then each differing word at its own position (Typo arm only, where a measured
  form exists) — refuse whenever both sides name indexed values and the values are
  disjoint, returning `Match.OtherWord` with what was actually written ("setenta" is 70).
  A plain Wrong that is a complete reading of another value is named the same way
  ("arobaini na saba" at 46 is 47, two edits and never a typo).
  Evidence is keyed to the TYPED side, never the expected one:
  refusing because the expected word is indexed would turn every fumbled numeral
  (`sesemta` for `sesenta`) into a miss, which is the one behavior the drill must keep.
  The index is the packs' own output — `drillNumber(n)` over the drawn range plus
  `formReading(value)` over `NumberFormsAnswerSpace` — keyed by the normalizer's
  comparison shape and built lazily per language; nothing about it is authored,
  so a re-spelled numeral moves the index the moment the pack moves.
  The country drill runs the same shape through `CountryNameIndex`, kind-scoped
  (countries against countries, peoples against peoples, languages against languages) —
  `Ĉilio` typed for `Ĉinio` is Chile, refused and named, while de `Spanier` for
  `Spanien` stays the cross-kind near-miss two different rungs make of it.
  Coverage is deliberately best-effort: a colliding pair outside the index's reach
  stays the forgiven slip it always was, and no sweep pins the tail
  (which readings a clock may share at all is `../docs/clock-registers.md`).
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
