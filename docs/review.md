# Review — how a card asks for a word and what an answer does
Which form a prompt shows, by sight or by ear, typed or recalled, when the picture appears, what each answer earns, and the one card every question is drawn from; each rule's mechanics are the KDoc on the kern type it names.
Neighbors: the evidence behind the schedule `scheduling.md`, what a card says aloud `audio.md`, the screens around it `design.md`.

## What a prompt shows

- **Every form gets prompted at no scheduling cost** (`turnPrompt`):
  a prompt rotates through the forms the answer side can give, the first exposure prompts the canonical text, `accepts` never rotates,
  and the reveal always shows the full family.
- **Three turns** (`presentationRole`): Recognize reads the word, Hear only plays it, Produce asks for it from the meaning.
  A word still landing alternates Recognize and Produce —
  Hear WITHDRAWS the meaning rather than adding support,
  and production practice is what builds speaking, so an early cycle keeps half of it;
  past the growing bar the three cycle in equal thirds.
  Where the form cannot be heard right now, Hear asks as Recognize rather than putting up an empty card.
- **A card asked by ear is answered with the MEANING** (`meaningSide`), graded by the SOURCE language's own rules:
  hearing a word and writing it back down proves the ear worked and nothing else.
  A miss reveals both and is never retyped,
  and the reveal never NAMES another concept's target word, since that would teach in the language being learned.
  "Can't listen right now?" puts the word on the card for the rest of the run and changes nothing the answer is worth (`SessionRunState.cannotListen`).
- **A settled word's produce turn is typed every other time** (`turnAnswer`):
  writing is the strongest recall and the one that teaches spelling,
  but once a word has settled, typing every turn mostly buys time spent typing,
  and the breadth goal is better served by more words per minute.
- **The picture comes up front only on a produce turn of a word still landing** (`emojiCue`), and on the reveal everywhere else:
  on a self-graded card the picture depicts the very concept asked for,
  and "the emoji was obvious" reaches the button exactly like "I knew the word".

## The turn

- **A turn is a machine, not a screen** (`TurnMachine`):
  the platform owns the field, the keyboard, focus, timers, animation and playback;
  every rule about what an answer is worth, how long an accepted answer stands,
  and that an explicit button replaces a beat where no timer may run, is kern's.
- What each branch earns:
  a clean answer its exact rating, a typo or a borrowed meaning an almost held until the owed form has been seen,
  a finished retype after a miss Hard, a given-up retype an honest Again, a self-grade `SelfGrading`'s.
- **A miss keeps the field open**, primed to the words already right,
  and keeps the refused word whole for a report (`TurnState.rejectedAnswer`).
- **A missed word that has not settled is written out once with the answer in view** (`CopyStep`):
  encoding, never a grade.

## The question on screen

- **One `Question` per card on screen**, a review card and a drill task alike,
  and one `AnswerControls` under it:
  each app draws every card and every answer area from one component,
  and nothing in either is worded or placed.
  A drill's run state declares both with no default, so a new drill cannot ship without ruling on its card.
- **A card opens on a miss or a reveal only** (`Question.opens`), never on an accepted answer,
  which already stands in the learner's own text.
- **A verdict is drawn before the card moves on**: each app's verdict motion settles inside the live beat (`ADVANCE_LIVE_MS`);
  how a card moves is that app's own.

## Evidence

- A recall made in the head practices about as well as one given aloud (Smith, Roediger & Karpicke 2013, *JEP: LMC* 39(6)) — the settled word's untyped produce turn.
- A miss is learned from once the answer is shown (Pashler et al. 2005, *JEP: LMC* 31(1)) — the write-out.
- Learners overrate what they know (Dunlosky & Rawson 2012, *Learning and Instruction* 22(4)), and response time has driven an adaptive scheduler well (Mettler, Massey & Kellman 2016, *JEP: General* 145(7)) — Easy earned by the clock.
- The picture up front is a fading cue, support while a word is weak and withdrawn once it lands, which helps where plain retrieval fails (Fiechter & Benjamin 2018, *Psychonomic Bulletin & Review* 25; Finley et al. 2011, *JML*).
- On the reveal once the word has landed: pictures that help retrieve the answer lower recall days later, the same pictures after the attempt do not (van den Broek et al. 2021, "Images that reveal the answer reduce vocabulary learning", *Journal of Educational Psychology* 113(8)).
