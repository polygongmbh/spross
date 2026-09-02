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
  answers whether a produce turn asks by sight or by ear. Not a third role — the role
  function is fixed and a word asked from its sound is still produced,
  so only the side the card asks FROM moves and one schedule still sees one kind of answer.
  `Sound` needs the STRICTER consolidated bar (`../README.md` §5), because this WITHDRAWS the meaning
  rather than adding support, plus the app's word that the form can be heard right now
  (no recording and no voice, reading aloud off, the device silenced, or a screen reader —
  each falls back to `Source` rather than putting up an empty card). Alternation divides the
  count by two like the synonym rotation: roles alternate per review, so `reviewCount % 2`
  is CONSTANT across one card's produce turns.
- **A card asked by ear is answered with the MEANING** — the source-language word, graded
  against `session.meaningSide` (the same card with its two sides swapped, so the whole
  grading pipeline is reused) by the SOURCE language's own `AnswerNormalizer`, articles and
  typo budget included. Hearing a word and writing it back down proves the ear worked and
  nothing else; translating it is what the box is for. So the meaning side's synonyms are
  simply the answer, and the word that played is a miss like any other — the reveal then
  teaches both, which is where the spelling still lands.
  The join is read from the other end here: a form more than one concept PRINTS carries
  every meaning they give it, because the target language merges what the source splits
  (sw `ndege` is Vogel AND Flugzeug), and each of them answers what the word means —
  `CatalogAnswerGrader.conceptsSharing`, literal where produce grading is lenient, so a
  case difference stays two words. The borrowed meaning books in full and holds on the one
  THIS card teaches (`AlmostReason.Merged`), which is the word still to be learned.
  It still never NAMES the other concept's target word: that would teach in the language
  being LEARNED, and the source language is the one the learner already has.
  `TurnState.answerText`/`answerLang` are where a field's placeholder and a screen reader
  tag read the answer side off, so no platform re-derives it.
  (`spokenOnly` stays: the letter drill's dictation Sprosse still transcribes, and there the
  glyphs ARE the lesson.)
- **"Can't listen right now?"** — `TurnIntent.ShowPromptText` puts the played word on the
  card as text (`TurnState.promptInText`), and nothing else about the turn moves: same
  question, same answer, same rating. A learner in a quiet room may not be able to hear the
  one thing the card consists of, and the alternative to reading it is answering blind.
  Not a mode and not a setting — it lasts the turn, and the next card asked by ear asks by
  ear again, because the device's own audibility is what decides that (`../README.md` §3).
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
