# Listening — a playlist of the learner's own words
What listening plays, in what order and with which pauses, how it runs with the screen locked, and how a bedtime ends it.
Neighbors: how a word is spoken `audio.md`, the drills it is not `drills.md`, the round it sits under `design.md`.

## On the phone

- **Listening is not a drill.** Only a skill with a ladder to climb earns a chip on the hub (`drills.md`);
  listening asks nothing, grades nothing and has no Sprosse to reach, so a chip would call it a peer of counting and spelling when it is a different kind of thing.
  It gets ONE CARD on Home, under the day's round:
  the round is what the box asks of the learner, and this is what the learner can do when answering is not on the table —
  a walk, a commute, the dishes.
  Its title names the mode once, and its second line carries the two facts the name cannot:
  which words it leans on, and that it needs no hands.
- **It keeps the drill contract even so**: it books no review and writes no schedule, so a run costs the box nothing and can be closed at any moment.
  Unlike a drill it asks nothing, so it moves no streak either.
  It has no end screen —
  a run that only laps counts no stretch to celebrate —
  and no way of ending by itself:
  it laps for as long as it is left playing.
- **What it plays is the box, short of the words it already calls settled, shakiest first**, never ordered by what is due —
  a schedule is about when to ASK, and nothing is being asked.
  New words take a fixed share from the first turn, queued words leading them, so the mode that asks the least still honors the queue the day's round does;
  suspended words are in the pool, since they are exactly the ones a due-driven surface never reaches.
  Hearing a word does NOT introduce it: introduction is the first ANSWER, and this surface has none.
  The deal and its numbers are § The playlist.
- **It plays with the screen locked.** That is the point of it —
  the mode is for the hours the phone is in a pocket, and one that stopped at the lock screen would be a mode for staring at a phone that is already speaking.
  Both platforms put the run on the lock screen and take play/pause/next from it and from a headphone button, driving the same reducer the on-screen buttons drive;
  how it is spoken, and what it does to whatever else was playing, is `audio.md`.
  What that card SAYS is the run and never the turn:
  the app icon, the app and mode as the title, the pair of languages under it, and nothing that moves word to word.
  That is what lets it be PUSHED when the run changes —
  opened, paused, resumed, given a bedtime, closed —
  rather than three times a minute:
  the artwork crosses to the system whole on every push, and a mode built for the hours a phone spends in a pocket cannot pay that per word.
  A bedtime, and only a bedtime, gives the card a progress bar:
  it is the one thing a run that otherwise laps forever has a length of, and the system runs the bar on between pushes.

- **Listening mode**: target word, meaning, target word again,
  every beat kern's (§ The playlist).
  No mute button; plays under `.playback`.
  Takes audio over (no `.mixWithOthers`, spoken-audio mode),
  session released on stop.
  A word enters only if both sides can be said on this device.

## The playlist

- **Listening is a playlist over the learner's own words** (`net.spross.kern.listen`):
  the target word, its meaning in the SOURCE language, then the target again —
  so it reaches the hours a language is available in and nothing can be typed or tapped.
- `ListeningPool.report(catalog, box, source, target, hasTargetVoice, hasSourceVoice, seed)` is the one gate, disciplined like `LetterDrillAvailability.report`:
  the only platform facts are the two `hasVoice` booleans, and kern caches nothing.
  It is asked ONCE PER RUN, when the learner opens one, never on the way past the entry card;
  the card itself stands on `listeningOffered`: words in the box and something that can say each language.
  `seed` only salts the order's tiebreak;
  kern never reads a clock or cares what the number means.
- **The pool is the sayable join short of the settled words, not a composed subset.**
  **Both halves must be sayable** (`audible` on both forms),
  or a turn plays a word and then silence.
  Scheduled and unseen words alike are in it,
  so a learner a few words in hears a stream of new words rather than lapping the handful they hold;
  unseen words enter through `Growth.isIntroducible`, and hearing one does not introduce it.
  **A settled word is out** (`Statistics.hasSettled`) until a lapse takes it under that bar:
  left in, a well-used box would open on the words it trusts most.
  **Suspended cards stay in**: suspension takes a word out of the box's rotation,
  and this is the surface that can still reach it — at the growing priority, never leading.
- **The ladder is the box's own bar** (`listeningPriority`):
  a word short of `GROWING_STABILITY` is SHAKY, one past it GROWING.
  **Nothing on it reads a due date** —
  a due term would make listening a second scheduler, need a clock the run does not take,
  and pin the same word first every run,
  which listening cannot resolve since it books nothing.
- **The deal** (`listeningOrder`, what `ListeningPool.report` returns):
  the shaky words play first, all of them,
  then share the turns with the growing ones, each lane starting over at its own head when it runs out,
  so no word returns before the rest of its lane has, and a recent word waits a floor of turns.
  Never-answered words are a fixed slice from the first turn rather than a priority,
  so breadth rides alongside the shaky words and three hundred unseen words cannot crowd out the twenty that are slipping;
  queued words lead that slice, most recently queued first,
  and the catalog's earliest concepts lead the rest, with a soft lean toward earlier words past them.
  Within a lane the order is hashed by card id salted with `seed`,
  so catalog neighbors are not heard in sequence and a fresh seed reshuffles.
  Only the ordered list crosses the ObjC boundary.
- **`ListeningRun` holds no `BoxState` at all** —
  that is what makes "listening books nothing" structural rather than promised.
  It walks the playlist it was handed by position and laps at its end;
  repeats are the deal's business, never the run's.
- **Back works like a music player's**: the first press restarts the word on air,
  a press before the run moves on steps to the word before it, and further presses keep stepping;
  the head steps to the playlist's end only once the run has lapped.
  `ListeningEffect` says `Play`/`Stop` because a first Back leaves the turn as it was and must still make the sound fire.
- **Every beat is kern's** (`ListeningTurn.sayings`):
  target, meaning, target again, the article on the target sayings only, and the meaning shown from its own saying on.
  The recall gap is long for a word answered before and short for a new one,
  and the echo and the gap between turns reuse those two.
  Each beat is armed off the previous word ACTUALLY ENDING plus its gap,
  and a word that never reports a finish is walked past after `LISTENING_WATCHDOG_MS`.
- **A bedtime fades the whole run rather than cutting it** (`listeningTimerStepMs`, `listeningGainDb`, `fadedGainDb`):
  a hard stop is loud enough to wake the listener.
  The deadline ends the run at the seam between turns (`listeningSeam`),
  and a PAUSED run is left parked —
  a bedtime ends a run nobody is attending, not one somebody just touched.
  The floor holds the SUM of a recording's level and the ramp, because that is what a listener hears.
  The remaining milliseconds are the APP's to track and hand in;
  the run state holds no deadline.
  The chip reads whole minutes rounded up and wakes only when one turns (`listeningTimerMinutes`, `listeningTimerWakeMs`).
