# Presentation — what a prompt shows, and when
Which form a prompt shows, whether the meaning is given or withdrawn and when the picture appears: render-time rules only, never the schedule or a screen position.
Neighbors: the contract `../README.md` §3, how a meaning answer is graded `grading.md`.

- **Synonym rotation** on recognition prompts (`recognitionPromptForm`):
  the prompted form cycles deterministically through `text` + `teaches`,
  so every form gets prompted at zero extra scheduling cost;
  the first exposure always prompts the canonical text, and `accepts` never rotates.
  The reveal always shows the full family;
  the source-side reveal may show the source `teaches` informatively ("Amt / Verwaltung").
- **Sound-prompted production** (`producePrompt`) answers whether a produce turn asks by sight or by ear.
  Not a third role: a word asked from its sound is still produced,
  so only the side the card asks FROM moves and one schedule still sees one kind of answer.
  `Sound` needs the growing bar (`../README.md` §5), because it WITHDRAWS the meaning rather than adding support,
  plus the app's word that the form can be heard right now —
  no recording and no voice, reading aloud off, or a screen reader
  each fall back to `Source` rather than putting up an empty card.
- **A card asked by ear is answered with the MEANING** —
  the source-language word, graded against `session.meaningSide` (the same card with its two sides swapped,
  so the whole grading pipeline is reused) by the SOURCE language's own `AnswerNormalizer`.
  Hearing a word and writing it back down proves the ear worked and nothing else;
  translating it is what the box is for.
  A miss reveals both and is never retyped (`TurnState.retypes`):
  nothing is written out in the language the learner already has,
  so the reveal stands alone and moving on is an honest Again.
  Every meaning the played form carries counts (`grading.md` § Catalog-wide collision),
  but the reveal never NAMES another concept's target word:
  that would teach in the language being learned.
  `TurnState.answerText`/`answerLang` are where a field's placeholder and a screen reader tag
  read the answer side off, so no platform re-derives it.
  (`spokenOnly` stays: the letter drill's dictation Sprosse still transcribes, and there the glyphs ARE the lesson.)
- **"Can't listen right now?"** — `TurnIntent.ShowPromptText` puts the played word on the card as text
  (`TurnState.promptInText`), and nothing else about the turn moves: same question, same answer, same rating.
  It lasts the turn, not a mode or a setting:
  the next card asked by ear asks by ear again, because the device's own audibility decides that.
- **Emoji cue** (`emojiCue`): WHEN the picture appears, never whether or where (that is the renderer's, and fixed).
  **Upfront** iff role == Produce and the word has not landed —
  a produce prompt already names the concept in the source language, so the picture supports recall without giving the answer away;
  **OnReveal** everywhere else, recognition prompts and the first exposure included.
  Having landed, not the FSRS phase, is what "still landing" means.
  Once a word has landed the picture still comes on the reveal, where it can leak nothing
  and binds the picture to the meaning — hiding it outright took it from exactly the reviews that need it.
  On a self-graded card the picture depicts the very concept being asked for,
  and "the emoji was obvious" reaches the button exactly like "I knew the word";
  the first exposure is where that costs most, since that answer decides how long the word goes away for.
  The target form, its sound and the reveal still teach a first sight;
  the picture lands on the typed produce turn that follows, the first one that asks the learner to know the word.
