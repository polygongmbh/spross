# What earns a slot, and where it lives

Whether a concept deserves a card, how its realization is worded, and which area it belongs to --
the content rules that cut across every language file.
The file format they are written into is `../README.md`.

## What earns a card

**Every slot has to buy fluency.**
A concept is worth a card when knowing it lets the learner say more; charm is not a qualification.
Redundancy is the usual symptom:
when two entries serve one situation, keep the one whose words go furthest elsewhere.
Of several phrases with a single word swapped, keep one,
preferring the one that also teaches something small like gender agreement --
unless the SWAP is the lesson (a contrast pair, below).

**A phrase has to teach more than its words.**
A sentence that is only its own vocabulary in a row is already known
the moment those words are, so it buys nothing.
What earns the slot is the small extra the words alone do not give --
an agreement (es `mucha agua`, feminine against `el agua`),
the form a construction forces (uk `чи` in a choice question, not `або`),
an idiomatic turn, or one small function word carried in on the side.

**Prefer splitting a word out of a phrase over inflating the phrase.**
Short phrases keep typing manageable and let the word be recalled on its own,
and `adjective` is the catch-all that takes whatever is neither noun nor verb
(`draußen`, `immer`, `Vorsicht`).
A word too marginal for a card of its own rides in as the bare collocation
with the word it lives with (`bellen` in `Der Hund bellt.`);
a phrase's `components` gate can then only name the side that HAS a card.

**A picture only where it cannot mislead.**
`emoji` is authored wherever an honest picture exists and left off where none does,
because a wrong cue costs more than a missing one.
A phrase takes its topic's picture,
so sharing one with the word it is built from is expected;
two distinct WORDS in one area sharing a picture is not,
unless one names the other (`Zähne putzen` may wear the toothbrush's).

**A card that has not earned its slot moves back; it is deleted only on request.**
Content and audio are keyed by slug,
so a move to a later shelf preserves every realization and every recording,
while a rename orphans them (`../README.md`).
`../../scripts/catalog-move.py` carries the move.
Deletion stays available and is the user's call to make.

## How a realization is worded

**A realization mirrors the concept, not the translator's instinct.**
Every word in one language's text should have a visible counterpart in the others' --
that mapping is how a learner works out which word did what.
The replaced wording moves to `accepts` so nobody's typed answer stops grading.
Where a language genuinely has no equivalent --
a greeting formula, `Feierabend`, the Swahili clock --
a `notes` entry carries the gap.

**Idioms are the exception.**
An idiom (`kind: "idiom"`, `idioms/`) is figurative by definition,
so faking word-correspondence would teach the wrong idiom.
The curation bar replaces word-mirroring with **meaning-equivalence**:
ship a pairing only where another language has a genuinely equivalent expression,
and use the ordinary coverage rule to omit a language honestly where none is known.
`notes` carries the literal back-translation of each side's imagery:
that gap, made visible on reveal, is the actual teaching content.
Write it as `Wörtlich: <back-translation>.`
(or the reader's own language's word for "literally"),
with no quote marks around the back-translation itself.

**Baked-in objects**: a verb carries its object in EVERY language or in none.
Swahili often cannot go bare because one verb covers several German ones
(`kupanda` = besteigen/einsteigen/pflanzen),
and then the object is authored across the board.
The exception is a **merge**, where the target has one word for two source concepts
and the object is the disambiguator:
carried by the merged language alone, governed by the homonym rule (`../README.md`).
Before reaching for a merge, ask whether the SOURCE concept is under-specified too --
usually it is, and then the object belongs in all eight languages.
Keep the merged language's private object only where the source really is unambiguous
and the others would sound wrong carrying one --
and price the length in: a side over `WidgetSnapshotBuilder.MAX_TEXT_CHARS`
stops reaching the widget.

**An alternate the card TEACHES is knowledge; one it only ACCEPTS is a surface**
(the fields are `../README.md`).
- **Spell the alternate out.**
  An abbreviation of a form the card already carries teaches nothing the long form does not,
  so it goes in `accepts` (en `résumé` names *curriculum vitae*, not CV).
  An abbreviation that IS the everyday word goes in `teaches` (es `id-card` DNI).
- **English is authored in American spelling and vocabulary.**
  A SPELLING goes in `accepts` (`color`/`colour`, `gray`/`grey`): accept it, never teach it.
  A different WORD goes in `teaches` (`truck`/`lorry`, `faucet`/`tap`),
  because knowing "truck" does not tell anyone what a lorry is.
  Slugs and prose follow the American form,
  except where the everyday word already names another card (`tin-can`, beside the modal `can`).
- **A register pair is a swap, not a rewrite**:
  the du-form differs from the Sie-form in the address alone.
  Lint holds the politeness particle equal across `text` and every alternate, in both directions.
  Which register `text` carries is the **scene's** call --
  the counter, the surgery and the office say Sie, the kitchen and the hall say du --
  so a phrase whose scene fixes the register carries no register alternate at all,
  and only the phrases that travel between scenes carry both.

**Pick the word a speaker says, never the word that sits furthest from another card.**
Two realizations one edit apart are safe by construction:
a typed form that is exactly some card's answer grades `Match.OtherWord`
(`../../kern/docs/grading.md` catalog-wide collision).
The bar that does bind is a display-identical prompt inside ONE area,
where the area label would be the same cue on both -- and lint holds that.

**Realizations and notes write the typewriter apostrophe** (U+0027);
the modifier letter U+02BC belongs only inside `alphabet/`
(`CatalogLintTest.contentWritesTheTypewriterApostrophe`).

## How a grammar rule gets taught

**A rule gets one home, and phrases that exercise it.**
A CLASS fact -- German's two-way prepositions, the accusative most verbs take --
is stated once, on the earliest card that shows it;
a LEXICAL fact -- that `folgen` takes the dative -- sits on that word.
The phrases are the teaching:
a learner who produces `Siehst du mich?` has the accusative.

**Write the note in the language it explains.**
One wording where eight translations would each serve one,
and it is the language the learner is there to read.
Write it example-first:
`мама → мамо, тато → тату` teaches the vocative to somebody who could not yet read the word for it.
The examples have to be words the card's own learner already holds --
`../../scripts/notes-vocabulary.py` names both forward and backward vocabulary issues.
A chain of teaches explains nothing to the learner who is missing the first link.
Key a note to a reader's language only where the shared wording will not do --
a card that arrives before a learner could read the target,
and a quirk that exists only because these two languages met
(Spanish `doler` explained as German's `gefallen`, an idiom's back-translation).

**Keep a note only if it changes what the learner would say or do**;
pure etymology ("wörtl. ...") is cut,
so is a form's invariance -- nobody inflects a word they were never shown inflecting.
Which prefix a word DOES take is carried by the dashed stem and its `accepts` (`../README.md`),
never by a note.
A note explains its own word and no other.
Where the rule is what the learner has to practice,
a phrase that exercises it beats every wording of it.

**A contrast pair is two phrases whose difference IS the lesson.**
`Wir fahren in die Berge.` beside `In den Bergen ist es kalt.`
teaches the case `in` takes for a destination against the one it takes for a location.

**The pair has to be worth a slot in more than one language.**
Pick the concept the other targets also draw a line through.
That mountain pair fires in German (Dativ/Akkusativ),
Esperanto (`en la montaro`/`en la montaron`) and Ukrainian (`у горах`/`в гори`) alike;
the languages that draw no line teach exactly that.

**A phrase picks its person on purpose.**
The corpus leans heavily on 1sg, third-person NPs and `Sie`;
third plural and `ihr` are unattested.
A new phrase whose person is free takes the least-represented one.

**Check an authoring pass against the lint, not against a script you wrote for it.**
`CatalogCollisionLintTest` owns the collision rules;
`./gradlew :kern:jvmTest --tests '*Catalog*LintTest*'` is the authority.
The full gate stays `./gradlew :kern:jvmTest` after a content edit.

## Which area a concept lives in

**An area holds a few dozen cards.**
It is a shelf a learner can hold in their head and choose to pull forward,
not a drawer everything vaguely related falls into --
so an area growing past roughly forty asks to be cut along the seam a learner would name.
The cut is cheap: the slug carries no area, so nobody's schedule notices.

**A front-group slot is earned by what a learner can use on the first day.**
Introduction walks a flat seed order,
so group order in `areas.json` IS card order --
a shelf placed eleventh is two hundred cards in.
The `start`/`compose` seam states the test:
a fixed formula you exchange with a person belongs in `start`,
a word you compose your own sentences from belongs in `compose`.

**No area may be the leftover bin.**
An area whose name states no test for belonging will refill,
because every word that fits nowhere fits there.
Every area's name must answer *what gets in*,
and the answer must be able to say no:
a room (`kitchen`), an errand (`admin`), a kind of word (`colors`, `qualities`),
a shelf of sentence machinery (`place`),
or the deliberately narrow `verbs`, which admits a word only when no scene claims it.
A word that fits none of them is evidence that a shelf is missing.

The area is the folder, and three things ride on it:
it is the produce prompt's disambiguator,
`components` and `feminineOf` resolve inside it,
and it is the unit a contributor writes and reviews.
A phrase gating on a component travels with it --
`components` is an unlock gate, not a claim about the sentence's words,
so a phrase whose second component would stay behind simply drops it.

Moving is mechanical:
`../../scripts/catalog-move.py` carries every language's realization verbatim,
appends words before the destination's phrase block,
and refuses a move that would part a phrase from a component,
a feminine from its base, or mint a same-area prompt collision.
