# Presentation — what a prompt shows, and when

Which form is prompted, whether the meaning is given or withdrawn, and when the picture
appears. Every rule here is a render-time function of the card and its review count — none
of it touches the schedule, and none of it names a screen position.
Engine contract: `../README.md` §3.

- **Synonym rotation** on recognition prompts: the prompted form cycles deterministically
  through `text` + `synonyms` — index = (`count / 2` + id-hash offset) mod formCount
  (parity-independent: recognition happens every other review); first exposure always
  prompts canonical text; variants never rotate.
  Every form gets prompted at zero extra scheduling cost.
  Reveal always shows the full family; the source-side reveal may show source synonyms
  informatively ("Amt / Verwaltung").
- **Sound-prompted production**: `producePrompt(cardId, reviewCount, consolidated, audible)`
  answers whether a produce turn asks by MEANING or by ear. Not a third role — the role
  function is fixed and a word asked from its sound is still produced,
  so only the prompt side moves and one schedule still sees one kind of answer.
  `Sound` needs the STRICTER consolidated bar (`../README.md` §5), because this WITHDRAWS the meaning
  rather than adding support, plus the app's word that the form can be heard right now
  (no recording and no voice, reading aloud off, or a screen reader — each falls back to
  `Source` rather than putting up an empty card). Alternation divides the count by two like
  the synonym rotation: roles alternate per review, so `reviewCount % 2` is CONSTANT across
  one card's produce turns. Grading narrows to the form that played (`session.spokenOnly`,
  shared with the letter drill's dictation); a synonym of the same card is almost, never
  wrong, since the reveal itself teaches those forms.
- **The target is spoken with its article; the source is not.**
  `spokenTargetForm(article, shownForm, targetText)` (beside `utterance`, which answers the
  same question — what string does the synthesizer get) prefixes the article `shownArticle`
  allows, so a rotated synonym that may carry another gender is spoken bare rather than
  mislabeled. It applies on the SYNTHESIZED branch only: a bundled recording says what was
  recorded, and re-cutting one is an edit to bytes kern never edits. The two branches then
  sound different, which is the accepted cost — the recording is the branch falling short.
  Reverses `../../docs/read-aloud.md`'s "only the headword is ever spoken" (user ruling 2026-08-21),
  everywhere a target word is synthesized and not only in listening.
- **Emoji cue**: `emojiCue(role, consolidated)` answers WHEN the picture appears,
  never whether it appears at all and never where (that is the renderer's, and it is fixed).
  **Upfront** iff role == Produce ∧ the word has not landed (`../README.md` §5) — the one prompt it can
  support recall on without giving the answer away, since a produce prompt already names
  the concept in the source language and asks for the other one;
  **OnReveal** everywhere else, in every phase and on every recognition prompt.
  Having landed, not the FSRS phase, is what "still landing" means here.
  Hiding it outright once a word was learned took it away from exactly the reviews where a
  word is still matched on novelty rather than on meaning; once the answer is out there is
  nothing left to leak, and binding the picture to the meaning is what those reviews need.
  The first exposure does not carry it (the contract's ruling). It used to, as the cue that
  made a first recall attempt possible. "The emoji was obvious" and "I knew the word" reach the button
  identically, and the schedule cannot tell them apart; a first exposure is where that
  costs most, because that answer decides how long the word goes away for.
  Nothing is withheld from a learner meeting the word: the target form is on screen, its
  sound plays (`pronunciationCue` is Upfront on every recognition prompt), and the reveal
  brings meaning and picture together, which is where a first sight teaches. What moved is
  WHERE the picture lands — off a prompt no one can grade honestly, onto the typed produce
  turn that follows it, which by role resolution is the very next review and the first one
  that asks the learner to actually know the word.
