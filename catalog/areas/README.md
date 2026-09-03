# What earns a slot, and where it lives

Whether a concept deserves a card, how its realization is worded, and which area it belongs to —
the content rules that cut across every language file.
The file format they are written into is `../README.md`.

## What earns a card

**Every slot has to buy fluency.**
A concept is worth a card when knowing it lets the learner say more; charm is not a qualification.
Redundancy is the usual symptom: when two entries serve one situation, keep the one whose words go
furthest elsewhere — `sweet-dreams` was cut because the bedroom already teaches `sleep-well`.
Of several phrases with a single word swapped, keep one, preferring the one that also teaches
something small like gender agreement — unless the SWAP is the lesson, which is the contrast pair below.

**A phrase has to teach more than its words.**
A sentence that is only its own vocabulary in a row is already known the moment those words are,
so it buys nothing (`Tee oder Kaffee?` was cut on this test).
What earns the slot is the small extra the words alone do not give — an agreement
(es `mucha agua`, feminine against `el agua`), the form a construction forces
(uk `чи` in a choice question, not `або`), an idiomatic turn, or one small function word carried
in on the side. Where that extra is a remark rather than a construction, it belongs in the WORD's
`notes` and the phrase still goes.

**Prefer splitting a word out of a phrase over inflating the phrase.**
Short phrases keep typing manageable and let the word be recalled on its own, and `adjective` is
the catch-all that takes whatever is neither noun nor verb (`draußen`, `immer`, `Vorsicht`).
A word too marginal for a card of its own rides in instead, as the bare collocation with the word
it lives with (`bellen` in `Der Hund bellt.`); a phrase's `components` gate can then only name the
side that HAS a card — the noun here, nothing at all where the noun is the marginal one.

**A picture only where it cannot mislead.**
`emoji` is authored wherever an honest picture exists and left off where none does, because a wrong
cue costs more than a missing one — which is why the function words carry none (`viel`, `jetzt`,
`groß`, `wo`, `aber`, `oft`). A phrase takes its topic's picture, so sharing one with the word it is
built from is expected (`the-fridge-is-empty` ← `fridge`); two distinct WORDS in one area sharing a
picture is not, unless one names the other (`Zähne putzen` may wear the toothbrush's).

**A card that has not earned its slot moves back; it is deleted only on request.**
Position is the usual complaint and deletion is rarely the fix: content and audio are keyed by slug,
so a move to a later shelf — or a reorder to the tail of the area's own word block — preserves every
realization and every recording, while a rename orphans them (`../README.md`).
`../../scripts/catalog-move.py` carries the move for free, area included.
Deletion stays available and is sometimes right; it is the user's call to make,
and worth pricing in recordings when it is proposed.

## How a realization is worded

**A realization mirrors the concept, not the translator's instinct.**
Every word in one language's text should have a visible counterpart in the others' —
that mapping is how a learner works out which word did what,
and it is worth re-cutting the source phrase to keep
(„Das WLAN ist weg" became „Das Internet ist weg" so `intaneti` had something to answer to;
„zu teuer" is `ghali mno`, not `ghali sana`, which is „sehr teuer").
The replaced wording moves to `variants` so nobody's typed answer stops grading.
Where a language genuinely has no equivalent —
a greeting formula, `Feierabend`, the Swahili clock — a `notes` entry carries the gap.

**Idioms are the exception.** An idiom (`kind: "idiom"`, `idioms/`) is figurative by definition,
so faking word-correspondence would just teach the wrong idiom — „es gießt wie aus Eimern" and
"it's raining cats and dogs" describe the same event with unrelated imagery.
The curation bar replaces word-mirroring with **meaning-equivalence**: ship a pairing only where
another language has a genuinely equivalent expression — same real-world function, not shared
imagery — and use the ordinary coverage rule to omit a language honestly where no such expression
is known, rather than force one. `notes` carries the literal back-translation of each side's
imagery: that gap, made visible on reveal, is the actual teaching content.

The mirroring rule also decides **baked-in objects**: a verb carries its object in EVERY
language or in none. Swahili often cannot go bare, because one verb covers several
German ones (`kupanda` = besteigen/einsteigen/pflanzen), and then the object is
authored across the board — `Blumen pflanzen` / `to plant flowers` / `kupanda maua` /
`садити квіти`, never `pflanzen` answered by `kupanda mimea`. The exception is a
**merge**, where the target really has one word for two source concepts and the object
is what tells them apart: there the object is a disambiguator, it is carried by the
merged language alone, and the homonym rule (`../README.md`) governs it.

That exception is the narrow case, not the first move. Before reaching for it, ask whether
the SOURCE concept is under-specified too — usually it is, and then the object belongs in
all eight languages by the rule above: `quit` alone does not say whether a job, a game or
smoking is being quit, so `den Job kündigen` / `to quit your job` is the card finally saying
what it means, and it mirrors, which the one-sided version never did.
Keep the merged language's private object only where the source really is unambiguous on its
own and the others would sound wrong carrying one — and price the length in: a side over
`WidgetSnapshotBuilder.MAX_TEXT_CHARS` still teaches, but stops reaching the widget.

**An alternate is a `synonym` when it is knowledge, a `variant` when it is only a surface**
(the fields are `../README.md`). The test is what a learner who knows `text` already knows.
- **Spell the alternate out.** An abbreviation of a form the card already carries teaches nothing
  the long form does not, so it is a variant and the full word is what gets shown (en `résumé`
  names *curriculum vitae*, not CV). An abbreviation that IS the everyday word is a synonym like
  any other (es `id-card` DNI, whose expansion nobody says).
- **English is authored in American spelling and vocabulary.** A SPELLING is a variant
  (`color`/`colour`, `gray`/`grey`, `to practice`/`to practise`): accept it, never teach it.
  A different WORD is a synonym (`truck`/`lorry`, `pants`/`trousers`, `faucet`/`tap`,
  `vacation`/`holiday`), because knowing "truck" does not tell anyone what a lorry is.
  Slugs and prose follow the American form, except where the everyday word already names another
  card (`tin-can`, beside the modal `can`) or the British word is simply the better one to teach
  (`cinema`).
- **A register pair is a swap, not a rewrite**: the du-form differs from the Sie-form in the
  address alone, and a `bitte` the Sie-form never had makes it a second sentence the slug no longer
  names (`can-you-repeat-that` says nothing about please). Lint holds the politeness particle equal
  across `text` and every alternate, in both directions.
  Which register `text` carries is the **scene's** call — the counter, the surgery and the office
  say Sie, the kitchen and the hall say du — so a phrase whose scene fixes the register carries no
  register variant at all, and only the phrases that travel between scenes (`whats-your-name`,
  `where-is-your-father`) carry both.

**Pick the word a speaker says, never the word that sits furthest from another card.**
Two realizations one edit apart are safe by construction:
a typed form that is exactly some card's answer grades `Match.OtherWord`
instead of earning typo credit (`../../kern/README.md` § catalog-wide produce grading),
which is why sw `kupata` has always stood beside `kukata`
and de `sehen` beside `stehen`.
The bar that does bind is a display-identical prompt inside ONE area,
where the area label would be the same cue on both — and lint holds that,
so it is never a distance anyone has to measure while authoring.

## How a grammar rule gets taught

**A rule gets one home, and phrases that exercise it.**
A CLASS fact — German's two-way prepositions, the accusative most verbs take —
is stated once, on the earliest card that shows it, and never restated on every card it touches;
a LEXICAL fact — that `folgen` takes the dative where `sehen` takes the accusative —
is a fact about that word and sits on it.
Neither is the teaching. The phrases are:
a learner who produces `Siehst du mich?` has the accusative,
and no wording of the rule gets them there.

**Write the note in the language it explains**, and every reader gets it — a German rule in German,
a Ukrainian one in Ukrainian. That is the default, not a fallback: one wording where eight
translations would each serve one, and it is the language the learner is there to read.
Write it example-first and it stays readable that early: `мама → мамо, тато → тату` teaches the
vocative to somebody who could not yet read the word for it, where naming the case would need a
note per reader instead.
Key a note to a reader's language only where the shared wording will not do — a card that arrives
before a learner could read the target's own words, and a quirk that exists only because these two
languages met (Spanish `doler` explained as German's `gefallen`, an idiom's back-translation).

**Keep a note only if it changes what the learner would say or do**; pure etymology ("wörtl. …")
is cut, and load-bearing teaching (which word for "rice") is destined to become first-class
training content, not a permanent note.
A note explains its own word and no other: what OTHER words do belongs on none of them.
Where the rule is what the learner has to practice, a phrase that exercises it beats every wording
of it.

**A contrast pair is two phrases whose difference IS the lesson.**
`Wir fahren in die Berge.` beside `In den Bergen ist es kalt.` teaches the case
that `in` takes for a destination against the one it takes for a location —
a fact neither sentence can carry alone.
The pair still owes what any phrase owes: each half useful on its own,
and both mirroring word for word across the languages that carry them.

**The pair has to be worth a slot in more than one language.**
Pick the concept the other targets also draw a line through, and reject the pair only German can see.
That mountain pair fires in German (Dativ/Akkusativ),
Esperanto (`en la montaro` / `en la montaron`) and Ukrainian (`у горах` / `в гори`) alike,
and the languages that draw no line — French, Italian, Swahili all say it one way —
teach exactly that, which is why the concept still earns its slot in all eight.
A German genitive of possession earns none: outside German and Ukrainian it is nothing at all.

**A phrase picks its person on purpose.**
The corpus is one paradigm, not two hundred independent sentences: German phrases lean heavily on
1sg, third-person NPs and `Sie`, and third plural and the informal `ihr` are unattested.
A new phrase whose person is free takes the least-represented one;
`Ich` needs a reason, and a card genuinely about the speaker (`Ich habe Hunger`) has one.
This is a preference, not a gate — most phrases are imperatives or fragments carrying no person
at all, and a check built on that could only pin a guess.

**Check an authoring pass against the lint, not against a script you wrote for it.**
`CatalogLintTest` owns the collision rules and is the only home they have:
`./gradlew :kern:jvmTest --tests '*CatalogLintTest*'` is seconds of typing and the
authority, where a hand-rolled sweep is a second implementation nothing keeps honest.
The full gate stays `./gradlew :kern:jvmTest -Psweeps` after a content edit;
this is the narrow one to run while still authoring.

## Which area a concept lives in

**An area holds a few dozen cards.** It is a shelf a learner can hold in their head and
choose to pull forward, not a drawer everything vaguely related falls into — so an area
growing past roughly forty asks to be cut along the seam a learner would name
(the doctor's visit out of health, the clock out of the everyday words).
The cut is cheap: the slug is the card id and carries no area, so nobody's schedule notices.

**A front-group slot is earned by what a learner can use on the first day**, never by
how basic the topic sounds. Introduction walks a flat seed order, so group order in
`areas.json` IS card order — a shelf placed eleventh is not "early material", it is two
hundred cards in. The `start`/`compose` seam states the test: a fixed formula you exchange
with a person belongs in `start`, a word you compose your own sentences from belongs in
`compose`. Judge a shelf by where its cards actually land, not by where its name suggests they do.

**No area may be the leftover bin.** An area whose name states no test for belonging will
refill, because every word that fits nowhere fits there — which is what dissolved the area once
called `essentials`, "Das Wichtigste im Alltag". Every area's name must therefore answer *what
gets in*, and the answer must be able to say no: a room (`kitchen`), an errand (`admin`),
a kind of word (`colors`, `qualities`), a shelf of sentence machinery (`place`, which admits
whatever answers *where?* — the orientation words, the deictics, the spatial prepositions),
or — for the words that belong to no scene at all — the deliberately narrow `verbs`, which
admits a word only when no scene claims it.
A word that fits none of them is not homeless; it is evidence that a shelf is missing.

The area is the folder, and three things ride on it:
it is the produce prompt's disambiguator,
`components` and `feminineOf` resolve **inside** it,
and it is the unit a contributor writes and reviews.
So a concept sits with the scene it belongs to,
and a phrase gating on it travels with it —
`components` is an unlock gate, not a claim about the sentence's words,
so a phrase whose second component would stay behind simply drops it
(`old-people` keeps `person`, lets go of `old`, and unlocks a little earlier).

Moving one is mechanical and cheap, and `../../scripts/catalog-move.py` is what does it:
it carries every language's realization verbatim,
appends words before the destination's phrase block,
and refuses a move that would part a phrase from a component,
a feminine from its base, or mint a same-area prompt collision.
