# Review — how a card asks for a word and what an answer does
Which form a prompt shows, by sight or by ear, typed or recalled, when the picture appears, what each answer earns, and the one card every question is drawn from.
Neighbors: the schedule behind it `scheduling.md`, how a typed answer is graded `grading.md`, what a card says aloud `audio.md`, the screens around it `design.md`.

## What a prompt shows

- **Form rotation** (`turnPrompt`, `model/PromptForm.kt`):
  a recognition prompt cycles deterministically through `text` + `teaches` + every tagged `forms` entry,
  a produce prompt through the source's `text` and those of its tagged forms whose tag the target has too,
  so every form gets prompted at zero extra scheduling cost;
  the first exposure always prompts the canonical text, and `accepts` never rotates.
  The other side answers in the prompt's form where it has one,
  else in its citation with a marker for what that cannot show (`Lehrerin` → `teacher` ♀, `counterpart`).
  The reveal always shows the full family;
  the source-side reveal may show the source `teaches` informatively ("Amt / Verwaltung").
- **Sound-prompted production** (`producePrompt`) answers whether a produce turn asks by sight or by ear.
  Not a third role: a word asked from its sound is still produced,
  so only the side the card asks FROM moves and one schedule still sees one kind of answer.
  `Sound` needs the growing bar (`../kern/README.md` §5), because it WITHDRAWS the meaning rather than adding support,
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
- **Recalled production** (`produceAnswer`) answers whether a produce turn is typed.
  Writing the word is the strongest recall the box has and the only one that teaches the spelling,
  so a word that has not settled is always typed.
  Once settled, typing every produce turn mostly buys time spent typing:
  the breadth goal is better served by more words per minute,
  so every other produce turn asks the same question with no field — recall, reveal, self-grade.
  Typing stays on the other half so the spelling keeps being written.
  Not a third role: the question and its direction are unchanged, only the way the answer is given.
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

## The turn

Three pure machines: immutable state plus `reduce(state, intent) -> state + effects`.
The platform owns the field, the keyboard, focus, timers, animation and playback,
and text reaches a machine only inside an intent — never as state.

- **A turn is a machine, not a screen** (`session.TurnMachine`, state `TurnState`):
  one produce/recognize turn is immutable state plus `reduce(state, intent, nowEpochMillis)` —
  `SessionRun`'s shape, one step further in.
  It is opened with a card, its role, its produce prompt, the prompted form,
  whether this is the first exposure and whether the word has settled;
  it answers with the next state plus `TurnEffect`s —
  `Answer` (the rating leaves for the run), `PrimeField`,
  and `Cued` with the `Cue` a drill's `DrillEffect.Cued` carries too —
  `ArmAdvance`/`CancelAdvance`, `Tone` and `ReleaseFocus` —
  so each app carries a cue out in one place.
  The one text it holds is the answer it takes out of the field itself (`rejectedAnswer`, below).
  Every rule about what that text is worth is here.
  - **What each branch earns**: a clean answer is `Match.Exact.producedRating()`;
    a typo and a borrowed meaning are `TurnFeedback.Almost`, holding the rating grading decided until the owed form has been seen;
    finishing the retype after a miss is recalled-with-help (Hard);
    giving up on it is an honest Again;
    a self-grade is `SelfGrading` over the recall span and the prompt length.
  - **The beats belong to the engine** (`ADVANCE_LIVE_MS` 450, `ADVANCE_EXPLICIT_MS` 1200, carried by `AdvanceBeat`):
    finishing the word IS the answer,
    so a live-typed exact gets the short beat and an explicit Check the longer one,
    while an almost hold gets none at all.
    WHETHER a timer may run is the platform's fact —
    a screen reader makes a timed change hostile —
    but that an explicit button REPLACES it,
    and books exactly what the beat would have booked, is the rule (`TurnIntent.ConfirmPending`).
  - **Live approval is exact-only where an explicit submit forgives a slip**:
    the typo budget would fire a letter early and grade a word before it was finished,
    and a real slip has to pause on its correction anyway.
    Backing out of a finished word takes the acceptance and its parked rating with it.
  - **A miss keeps the field open**:
    the retype IS the answer, primed to the whole words already right (`AnswerNormalizer.matchingPrefixWordCount`),
    so nothing already correct is typed twice.
    The refused word is kept whole (`TurnState.rejectedAnswer`) because that priming drops it from the only place it stood,
    and `answerForReport(fieldText)` is what a report about the card carries:
    the word the catalog refused where there was one, else what stands written.
    A synonym marked wrong IS the report, so it may not be lost to the field it was typed in.
  - **The write-out** (`CopyStep`): a missed word is typed once with the answer in view.
    Only Again asks for it, only for a word that has not settled,
    and only where writing it is more than copying it off the prompt —
    production, or the first exposure, where the word is being taught.
    A later recognition miss does not qualify:
    the target has stood in the prompt since the first frame.
    The rating is HELD and applied unchanged — encoding, never a grade —
    and a produce retry that was given up on never opens one,
    because that field already was the one write-out the word gets.
  - **The recall span** is prompt-shown until the learner asks to see the answer, closed once;
    a typed answer never closes it, because it never reaches self-grading.
  - **Asked by ear**, the answer is the MEANING (`meaningSide`, graded by the source language's own normalizer) —
    a word heard and written back down has been transcribed, not understood.
    § What a prompt shows owns that rule;
    what the turn adds is that the answer side reaches every caller through `TurnState.answerText`/`answerLang`,
    and that `ShowPromptText` puts the word on screen for a learner who cannot listen without changing anything the answer is worth.

## The question on screen

- **One `Question` per card on screen** (`session.Question`), a review card and a drill task alike:
  its prompt and answer `Side`s, the caption (`QuestionAsk`), the picture and its cue, a first-sight `QuestionHint`, the closing lines and `opens` —
  whether the card carries its answer.
  Each app draws every card from it with one component, so what a face shows is decided once.
  Nothing in it is worded or placed:
  asks, hints, plural sentinels and closing labels are structures the apps word.
- **The review card** is `TurnState.question`.
  Grammar is target-side only and rides the cited form alone,
  so a rotated synonym stands bare and the citation it displaced closes the card as an alternate;
  the cited form carries its plural whether or not the language writes an article.
  The area is named over an ambiguous produce prompt and never over a recognition one,
  where any cue precise enough to disambiguate would hand over the answer.
  A card asked by ear asks with a sound until the learner cannot listen or the card opens,
  and then shows the word it played.
- **A drill's card is `DrillRunProgress.question`**,
  which every run state declares with no default, as it declares its sayings,
  so a new drill cannot ship without ruling on its card.
  The drills' first-sight words are `QuestionHint`s (a numbers place or form, a calendar pattern word), the atlas and letter captions `QuestionAsk`s;
  a flag that alone asks the question is `emojiIsQuestion`, a flag that would answer it waits for the reveal.
  A sentence scramble's bank is its prompt, so its prompt side holds no words.
- **A card opens on a miss or a reveal only** (`Question.opens`; a drill's `showsAnswer`, one rule on `DrillRunProgress`), never on an accepted answer,
  which already stands in the learner's own text.
  Where the meaning never stood on screen —
  the scrambles, the opposites, a letter heard —
  an accepted answer keeps the card closed and grows the meaning alone (`growsNote`).
- **What stands under the card is `AnswerControls`** —
  `TurnState.controls`, and `DrillRunProgress.controls`, again with no default:
  the slot the answer is given with (a field, tiles, an arrangement, the self-grade verdicts, the write-out), the field's own verdict, the one primary action, the confirm tap, the give-up, the way out and the can't-listen.
  A review miss keeps the field editable for the retype beside a quiet skip,
  and a miss asked by ear drops it for one Next;
  a drill's miss holds the field as written, since a drill has nothing to retype.
  Every drill wears one rule around its slot (`answerControls`):
  a submit only while a written answer is owed, a near miss and a miss held until tapped, a clean answer only where no beat may run, the way out where the run offers it.
- **A speaker is a `Side.saying`**, the tap;
  `Reading` stays the autoplay,
  and every target form a review card reads aloud also stands on a side with the speaker that says it.
- **How a card moves is `design.CardMotion`**:
  the switch to the next question turns the outgoing card out and the incoming one in about the vertical axis, each showing only its front half (`flipAngle`, `flipShows`),
  and a reveal settles before the shortest beat can move the card on;
  each app keeps its native easing.

## Evidence

- A recall made in the head practices about as well as one given aloud (Smith, Roediger & Karpicke 2013, *JEP: LMC* 39(6)) — the settled word's untyped produce turn.
- A miss is learned from once the answer is shown (Pashler et al. 2005, *JEP: LMC* 31(1)) — the write-out.
- Learners overrate what they know (Dunlosky & Rawson 2012, *Learning and Instruction* 22(4)), and response time has driven an adaptive scheduler well (Mettler, Massey & Kellman 2016, *JEP: General* 145(7)) — Easy earned by the clock.
- The picture up front is a fading cue, support while a word is weak and withdrawn once it lands, which helps where plain retrieval fails (Fiechter & Benjamin 2018, *Psychonomic Bulletin & Review* 25; Finley et al. 2011, *JML*).
- On the reveal once the word has landed: pictures that help retrieve the answer lower recall days later, the same pictures after the attempt do not (van den Broek et al. 2021, "Images that reveal the answer reduce vocabulary learning", *Journal of Educational Psychology* 113(8)).
