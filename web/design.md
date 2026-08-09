# spross.net — design brief

The standing reference for the site: what it is for, the ideas it carries, how it should sound,
and what it may not do.
It describes no particular layout.
Everything under *Ideas*, *Voice*, *Colour and type* and *Constraints* is fixed;
everything under *Shapes the page could take* is a suggestion to argue with.

A point-in-time critique of whatever is currently built lives in `design-brief.md` and expires;
this file does not.

## The job

One page that makes a stranger want the app, and gives the ones who do a way to hear about it.

The visitor is someone already learning a language —
they have a tandem partner, a course, an evening class, a friend who switched languages on them —
and they are losing vocabulary between those meetings.
They are not looking for another course.
They are looking for the thing that makes the course stick.

The one action is the mailing-list signup.
Everything else on the page exists to earn it.

## What a visitor must understand in ten seconds

1. **Spross is an app you install** — native, on iPhone and Android.
   A page that never says this leaves the reader unsure what is being offered.
2. **It grows vocabulary in a language they are learning**, a few words a day, on its own schedule.
3. **They can try the real thing right now, on this page, without installing anything.**

If a layout hides any of the three below the fold, it is the wrong layout.

## Ideas

These are the substance of the page.
Each is true of the shipping app; each is stated as what the learner gets, never as how the engine works.

**The growing box.**
Words come back just before they would have been lost, and the box only takes in new ones while
the old ones are sitting well.
A few a day, indefinitely.
Say: material that grows while you rest.
Don't say: FSRS-6, scheduling intervals, stability and difficulty.

**Breadth before depth.**
Following a conversation takes many words, not a few perfect ones.
Spross spends the learner's minutes on range, and accepts imperfect recall as the price.
This is the thesis that separates it from every flashcard app that optimises retention of a small set.

**A companion, not a replacement.**
It sits beside a tandem partner or a course and keeps vocabulary growing between them.
It teaches no grammar course and pretends to no curriculum.
This framing is load-bearing: it sets the expectation the product can actually meet.

**Phrases stand on words.**
A phrase stays closed until the words inside it are steady, then opens onto ground that can hold it.
The learner meets whole sentences only when nothing in them is new but the shape.

**A forest, not a chart.**
Each topic area is a tree, each word a leaf that becomes blossom, then fruit, as it matures.
Progress is something you look at, not a percentage.
This is the app's most distinctive screen and the hardest thing to convey in text —
if any screenshot is ever produced, make it this one.

**Practice that never runs out.**
Numbers, clock times, years, the other ways a number is written, the alphabet, dictation,
and whole sentences — all generated, so there is no deck to exhaust.

**A near miss is amber, never red.**
One slip per word is forgiven and shown corrected, in ochre.
Nothing on the page — or in the app — punishes a wrong answer;
a wrong answer is the productive event in retrieval practice.
This is a feeling to reproduce in the page's own colour and copy, not only a feature to describe.

**Real voices.**
Hundreds of recordings from native speakers back the words in German, Spanish, Swahili and Ukrainian.
Where no recording exists the device speaks.
Name the four languages that have recordings; English does not, yet.

**Your own words.**
Whatever the learner picks up in the wild goes into the same box and grows there,
and a growing catalog never touches what is theirs.
This closes the loop with *A companion, not a replacement*:
the word from last night's conversation has somewhere to go.

**Nothing to sign up for.**
No account, no ads.
True and worth knowing — but it is a relief, not a headline.
Put it where a reader who is already interested will find it, not where it competes with the ideas above.

Two things the page must never claim:
that new words are withheld when reviews are going badly (they are not — intake is bounded per round and by nothing else),
and that there is no streak to lose (a day streak exists; it survives one missed day, not two).

## The name

*Spross* is German for a sprout.
*Sprosse* is a rung of a ladder.
One letter apart, and the product is both: something that grows on its own, and something you climb a step at a time.
A subscriber is a *Sprössling* — a seedling, and affectionately a kid.

Rules for the wordplay:

- **Play it, don't explain it.** A dictionary entry printed on the page kills the joke.
  Let the word do a job — name a level, label a step, sign off a footer — and let the reader find it.
- **Once, well.** One place where a German reader smiles is worth more than three where everyone groans.
- **Never at the cost of clarity.** A reader with no German must lose nothing by not getting it.
- Offer the meaning to the curious (a title attribute, a quiet gloss), never to everyone.
- Growth is the primary identity; the rung is the wink. Not the other way round.

## Voice

Warm, plain, a little playful, never breathless.
Short sentences.
The garden metaphor kept honest — it describes a real mechanic every time it appears.
Sparse emoji.
Honest about limits, which is what makes the rest believable.

The app's own strings are the reference:

> "Your box grows with you — a few new cards every day."
> "The box has something for you."
> "Newbies on the table."
> "Not much is sticking today — a tired head keeps nothing. Tomorrow will go easier."
> "Free practice — no schedule, no limit."
> "Done for today … Fresh cards tomorrow. See you then! 👋"

Write new lines in that register rather than quoting these.
Avoid: "unlock your potential", "supercharge", "AI-powered", exclamation stacks,
and any sentence that would fit a different product unchanged.

Two copy rules with teeth:

- **Name the outcome, not the mechanism.** If a sentence's answer to "so what?" is
  "the engine works this way", cut it or rewrite it from the learner's side.
- **No counts that go stale.** Numbers that a future release changes — how many languages,
  how many cards, how many rungs — should be phrased so they stay true, or left out.

## The live drill

The page embeds a real drill: the app's own engine, compiled to JavaScript,
generating numbers, spelling them in five languages, and grading typed answers with the same
one-slip-per-word tolerance the app uses.
The primer it can show is the app's numbers page, generated from the same tables the drill grades against.

This is the strongest thing the page has.
No competitor's landing page can show the product working, in the visitor's own browser, in their chosen language.
Whatever the layout, protect these:

- **It is the real engine, and the page may say so.** Nothing on it is a mock-up.
- **A visitor reaches it without a decision they cannot make** — picking a language is the only gate,
  and the drill should show what it is before they pick.
- **The forgiving grade is visible.** Amber correction, never a red slap. A visitor should feel it, once.
- **It never blocks.** No signup wall, no limit on how long someone plays.
- **It hands off.** After a stretch of play there is a natural moment to invite the signup —
  offered, never sprung, and dismissible without ending the run.

The drill is also the page's biggest layout problem: it is interactive, it changes height,
and it competes with everything near it.
Any layout that treats it as one more section among many is wasting it.

## Colour and type

`App/Sources/Design/Theme.swift` is the source of truth for colour; the site restates its tokens
and follows when it moves.
Stone-and-moss paper rather than white, deep forest ink rather than black,
clay as the voice of the brand, ocean and forest as secondaries.

The accents carry fixed meanings, and the page should not reassign them:

| token | role |
|---|---|
| clay (`--accent`) | the brand's own voice: headline, primary action |
| forest (`--success`) | a clean, correct answer |
| ochre (`--ochre`) | a near miss, a correction, a hint — never a failure state |
| brick (`--brick`) | a miss; used sparingly, never as a warning colour |
| ocean (`--teal`) | teaching asides: place words, references |

Dark is not an afterthought: every pairing is designed twice, and a lifted surface must read as lifted
in both, not merely inverted.

Type is a rounded humanist sans, matching the app's SF Rounded.
Off Apple platforms a system stack silently becomes something else,
so if the page's typography is meant to be part of its character,
inline a subsetted variable face (Cyrillic included — Ukrainian is one of the five)
rather than trusting a generic family name.

## Shapes the page could take

Suggestions. The ideas above are the fixed part; none of this is.

**Opening.** Options worth weighing: a headline-first hero with the product's face beside it,
the drill itself as the first thing on screen with the pitch beneath it,
or a single sentence and the numeral counting itself in five languages.
The trade is comprehension against demonstration.
Whatever wins, the three ten-second facts survive it.

**Arranging the ideas.** Ten ideas is more than a page can carry; four to six is the working range,
and which four depends on who is being convinced.
They could be a plain list, a grid, a ladder the reader climbs, a single scrolling argument,
or folded into the drill's own flow so each idea appears where the drill demonstrates it.
A structural device — rungs, a stem, numbering — should encode something true about the content;
if it is only decoration, plain type will beat it.

**Where the drill sits.** High enough that a visitor meets it before deciding to leave.
It deserves more room than a text section, and it benefits from a ground of its own —
though "of its own" can mean space and silence rather than a box.

**Rhythm.** Few measures rather than many; a page that keeps changing its column width reads as unresolved.
Two is usually enough: one for reading, one for everything else.

**Motion.** Only where it reports something true — an answer landing, a level climbing, a page being climbed.
Ambient movement for its own sake fights the calm the product is selling.
Everything must survive `prefers-reduced-motion` without losing meaning.

## Constraints

Not suggestions.

- **Static and self-contained.** No build framework, no external requests at runtime:
  no CDN fonts, no third-party scripts, no remote images. The page must work from any static host.
- **Light and dark**, both designed, following the system preference.
- **Reduced motion** honoured everywhere; no meaning conveyed by animation alone.
- **Down to 320px** with no horizontal scrolling, and touch targets a thumb can hit.
- **Keyboard and screen reader**: the whole drill operable without a mouse,
  target-language words tagged with their language so a screen reader switches voice,
  and state never conveyed by colour alone.
- **No analytics, no cookies, no trackers.** The product's privacy stance applies to its own front door.
- **The mailing list** posts to one configurable endpoint; until one exists the form says so honestly
  rather than pretending to succeed.
- **The drill's engine is built from this repo** (`:kern`'s browser target) and its content —
  the primer's bands and their keys — comes from the engine, not from copy typed into the page.
  When the engine's bands change, the page follows.

## Open questions

- **Screenshots.** There is no app imagery in the repo beyond the icon.
  The forest is the app's best idea and the page can currently only describe it.
  Producing a few real device shots would change what the page can be.
- **Timing.** Does the site go live before the app is listed, or alongside it?
  Before, the page is a waiting list and should say so plainly.
  After, the page is a download and the whole call to action changes.
- **Reach.** English-only, or German too?
  The audience most likely to get the name is the one least likely to need the English.
- **The mailing list** needs a provider before the form can do anything real.
