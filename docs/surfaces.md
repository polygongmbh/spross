# Surfaces beyond the review loop

The drills, the wrist and the Android companion. The review loop itself, and the
auto-advance beats these share with it, are `design.md`'s.

## Trainers & the letter drill

- **Trainers**: number, clock and phrase drills, registry-driven from kern, so the hub
  offers only languages with authored content. Drills grade word by word and ramp with
  the learner instead of sitting at one level.
  A drill card is a review card — same face, same reveal, and the revealed reading is
  spoken and replayable like any other answer (`read-aloud.md`) — and carries nothing but
  the prompt: the run's header line already names what is drilled and how far the ramp has
  come, and the field's placeholder names the language to answer in, so a badge or a
  "Zahl · auf Spanisch" caption would be the third telling of what one tap said.
  The one thing it does carry is the place-value hint the first time a length appears —
  a fact about THIS number, and the reveal takes its place rather than stacking under it.
- **Letter drill & alphabet sheet**: the Training card shows when slots, phrases OR an
  alphabet exist for the target (the third predicate is catalog file presence, recomputed
  on foreground/readiness — a voice installed in Settings brings the chip back). The
  sheet renders every row (glyph, name, IPA, context, hint, example with meaning where
  the reader's language knows the word) and ships even where the drill cannot — audio is
  the drill's precondition, not the sheet's. The drill asks everything by ear: letter
  NAME or gap word — and a gap word is drawn from the whole catalog wherever the glyph
  says its own sound, words the learner already holds first, so a rung stops meaning one
  memorized blank (`catalog/README.md` § Alphabet owns which rows may draw). Tiles first
  among strangers, then among look- and sound-alikes, then
  typed, and — once enough words are consolidated — dictation of the learner's own
  consolidated words, which never touches their schedule and leans toward the ones worth
  spelling twice: words carrying the language's hard graphemes, and words this learner has
  forgotten before. Correctness is never color
  alone (checkmark/X over the tint); a miss never auto-advances. Neither mute reaches the
  drill and it carries no mute button: entering a screen whose only content is a sound is
  itself the request to hear one (`read-aloud.md`), so no run of it can open on a card with
  nothing to answer.

## Android companion (core loop)

`android/` renders THIS contract with Compose — same engine facades, same review UX
rules. Platform deltas only: the catalog ships as APK assets synced from `catalog/`,
and the box is app-private and written after every answer rather than debounced.
Its scope is § Not yet.

## Watch & widgets (decode-only)

- The phone precomputes both snapshots on every persist; the surfaces decode and draw,
  and never compute what the phone could pre-resolve.
- Watch: one graded **multiple-choice** loop — the watch never types, and the options
  arrive ranked from kern so that nothing but meaning tells the answer from its company:
  word class, then how the sentence closes, then area, then string shape
  (`../kern/docs/snapshots.md`).
  No self-grading: correctness and response time derive the rating.
  Multiple choice on a keyboard-less device is a deliberate concession to the
  recall-first rule, with the latency curve compensating for it.
  Answers return as events; the phone reschedules against real timestamps and re-pushes.
- **A word too long for a tile is a phone word.** The wrist carries only what four tiles
  can hold at a readable size (kern `MAX_TEXT_CHARS`); a longer phrase is never pushed and
  never offered as somebody else's distractor. This costs the watch about a quarter of the
  phrases and no single word at all, and it is a gain rather than a loss: a four-way pick
  between sentences is exposure, not recall, and exposure is what the phone's own card
  already does better. What the watch drops, it drops from BOTH the entries and the option
  pool, from one predicate — the two can never disagree about what fits.
- **Every answer answers back, on three channels.** A haptic shaped like the derived rating
  (affirming, a double tap for slow-but-right, a failure buzz for a miss), the rating itself
  badged on the tapped tile as an emoji, and — on a miss only — the tile and a brief
  full-screen wash in red. **Red is the wrist's alone**: the phone keeps wrong off its cards
  because the learner stays there and can correct it, while a glance-long wrist answer has
  no second face to be gentle on. The badge is a tell, never a label: naming the grade would
  invite playing to the latency the grade is measuring. Reduce Motion keeps every color and
  the badge and drops only the movement. No sound — the wrist is a silent surface, and two
  channels already carry it.
- **The picture arrives with the answer.** A card that has an emoji shows it on the watch's
  prompt line once a tile is tapped, never before: on a recognition question the picture
  depicts the very meaning being asked for. That reveal moment is also why the wire now
  carries held-back pictures at all — the emoji cue picks which KEY the picture travels
  under rather than whether it travels (`../kern/docs/snapshots.md`), so no surface can show one early
  by reading the wrong field. It joins the prompt line instead of taking a slot of its own,
  so an answered card never reflows under the thumb.
- Two runs, and only one of them ends: the **due batch** is a counter that reaches its
  end and returns to the start screen by itself, while **free practice** takes the words
  closest to slipping, lap after lap, carrying the answer streak in place of a total.
  Practice has no end screen — a run the learner ends when they like has nothing to
  celebrate.
