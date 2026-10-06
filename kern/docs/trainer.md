# Trainer packs — what a drill is made of
The language packs, sentence frames and letter drill that generate a drill's prompts and readings, and the gates on them; how a run plays them is `turns.md`.
Neighbors: the run machines `turns.md`, per-language readings `../../docs/number-forms.md`, grading `grading.md`.

- One `:kern` module, package `net.spross.kern.trainer`,
  with `Long` cardinals everywhere (Kotlin `Int` is 32-bit on all platforms).
  The registry (`trainerPacks`) holds de/en/es/sw/uk/eo/fr/it;
  a language outside it has no drills (the hub's handling of that is an app rule).
- `Catalog.phraseTemplates(source, target)` is the frames' half of the card join:
  one `PhraseTemplate` per frame realized in BOTH languages, directional like a `Card`,
  with `count`/`masculineNumeral`/`note` riding along from the ANSWER realization.
  Nothing pair-shaped is stored, so authoring one language file lights up every pair it makes.
  Availability gate: **empty unless `Numbers.supports(target)`** —
  sampling generates the answer side's number words,
  so a language without a pack can only ever supply prompts.
  A frame whose slot the target cannot fill drops out on the same rule (`Numbers.supportsSlot`):
  a cardinal, a year and a clock come with every pack,
  a `fraction` needs the pack to READ one, a `phone` its `PhonePlan` (`../../docs/phone-readings.md`).
  Reverse mode is the same template read the other way, for any pair.
- A `NumbersTask` carries two prompt strings:
  **`prompt` is the machine form** (`"347"`, `"14:35"`) that callers parse —
  `PhraseSlots` does `prompt.toLong()`, and a Kotlin throw crossing the ObjC boundary crashes the app —
  while **`promptDisplay` is what the UI shows**, defaulting to `prompt`.
  Cardinals group their digits with **U+202F narrow no-break space** (`groupDigits`):
  dot and comma are inverted between German and English,
  so a neutral mark is the only one that teaches neither as the truth.
  A sentence slot's grouped digits are accepted alongside the plain ones.
- `NumbersReading.Form` asks the other ways a number is written over a ladder
  where each Sprosse keeps everything below it;
  its own `internal` model (`NumberValue`, `FormLimits`) never reaches the ObjC header.
  The Sprosse's forms are intersected with the language's,
  so a pack that cannot read one never draws it,
  and a pack that authors none offers no Forms drill at all (`Numbers.supportsForms`).
  **A Forms prompt is the one language-dependent prompt**:
  German shows `3,7` where English shows `3.7`, because the reading names the mark
  (`Komma` · `point`) and a shared prompt would lie about the answer it grades,
  and a price wears its language's own tag (`FormLimits.currency`);
  everything else stays neutral, including the ordinal mark `20.` and the `45 %` thin space.
  Fractions are drawn REDUCED: `2/4` would legitimately read both "zwei Viertel" and "ein halb",
  and no pack should carry that equivalence to grade its own drill.
- **`NumbersExercise` is what a RUN offers, `NumbersReading` is what fills a SLOT** —
  a Phrases run draws tasks whose reading is Cardinal, Year or Clock —
  and the two must not be collapsed, because progress is kept per exercise.
  `DrillUnlocks` is the unlock ladder and `DrillRamp.step` the Sprosse ramp every drill shares;
  both read progress the APP persists, and kern stores nothing.
  `PhraseSlots` samples at a Sprosse on the same per-kind ramp tables as the plain drills
  (a template's slot kind clamps the Sprosse).
- `Numbers.reference(language)` generates the numbers page from the same packs,
  so the table cannot drift from what the drill grades.
  `irregulars` (16–30) is offered only to a language whose readings there
  are not what its own siblings predict, so a band count varies.
- **`LetterDrill` is a separate facade, not a `NumbersReading` case**:
  its registry is alphabet file presence in the catalog (adding a language edits no Kotlin),
  and its ramp is stateless and kern-owned (`Report.openingSprosse`, `WINS_TO_ADVANCE`, then `DrillRamp.step`),
  so two platforms cannot drift.
  Sampling takes an injected `Random` and an app-computed promptable set
  (device voices are an app fact).
  A gap row draws its word from a POOL (`Catalog.alphabetExamples`, rules in
  `../../catalog/alphabet/README.md`), the app narrowing it to what the device can say
  and flagging what the box already holds;
  kern favors the known words while at least three stand,
  and spends no randomness where a row offers one word.
  Dictation draws only `BoxEngine.arrivedCardIds` through `dictationGradingCard`,
  weighted by `LetterDictation.weight`, whose difficulty figure rides in on `DictationCandidate` —
  kern reads no state.
