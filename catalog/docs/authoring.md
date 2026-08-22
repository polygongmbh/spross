# What earns a slot, and where it lives

Whether a concept deserves a card, how its realization is worded, and which area it belongs to —
the content rules that cut across every language file.
The file format they are written into is `../README.md`.

**Every slot has to buy fluency.** 
A concept is worth a card when knowing it lets the learner say more; charm is not a qualification.

`sweet-dreams` was cut on this test — the bedroom already teaches `good-night-sleep-well`,
which covers the same moment with more useful words, so the second phrase only bought a warm feeling.
Redundancy is the usual symptom: when two entries serve one situation, keep the one whose words go furthest elsewhere.
Multiple phrases with a single word swapped should keep one, preferring teaching something small like gender agreement in any language.

**A phrase has to teach more than its words.** 
A sentence that is only its own vocabulary in a row is already known the moment those words are, so it buys nothing:
`Tee oder Kaffee?` was cut on this test. 
What earns the slot is the small extra the words alone do not give — an agreement (es `mucha agua`, feminine against `el agua`),
the form a construction forces (uk `чи` in a choice question, not `або`), an idiomatic turn, or one small function word carried in on the side.
Where that extra is a remark rather than a construction, it belongs in the WORD's `notes` and the phrase still goes.

**A realization mirrors the concept, not the translator's instinct.**
Every word in one language's text should have a visible counterpart in the others' —
that mapping is how a learner works out which word did what,
and it is worth re-cutting the source phrase to keep
(„Das WLAN ist weg" became „Das Internet ist weg" so `intaneti` had something to answer to;
„zu teuer" is `ghali mno`, not `ghali sana`, which is „sehr teuer").
The replaced wording moves to `variants` so nobody's typed answer stops grading.
Where a language genuinely has no equivalent —
a greeting formula, `Feierabend`, the Swahili clock — a `notes` entry carries the gap.

**Idioms are the exception.** The word-mirroring rule above is what makes an ordinary
phrase learnable: the words visibly correspond, so a learner works out which did what.
An idiom (`kind: "idiom"`, `catalog/idioms/`) is figurative by definition, so that
correspondence would be dishonest to fake — "es gießt wie aus Eimern" (lit. "it's
pouring as if from buckets") and "it's raining cats and dogs" describe the same event
with unrelated imagery, and forcing a calque onto either side would just teach the
wrong idiom. The curation bar replaces word-mirroring with **meaning-equivalence**:
ship a pairing only where another language has a genuinely equivalent expression —
same real-world function, not shared imagery — and use the ordinary coverage rule to
omit a language honestly where no such expression is known, rather than force one.
`notes` (keyed by explanation language, same field as everywhere else) carries the
literal back-translation of each side's imagery — that gap, made visible on reveal, is
the actual teaching content: not just the matching idiom, but why the words don't match.

The same rule decides **baked-in objects**: a verb carries its object in EVERY
language or in none. Swahili often cannot go bare, because one verb covers several
German ones (`kupanda` = besteigen/einsteigen/pflanzen), and then the object is
authored across the board — `Blumen pflanzen` / `to plant flowers` / `kupanda maua` /
`садити квіти`, never `pflanzen` answered by `kupanda mimea`. The exception is a
**merge**, where the target really has one word for two source concepts and the object
is what tells them apart (`kuacha` verlassen vs `kuacha kazi` kündigen,
`kuomba` beantragen vs `kuomba kazi` sich bewerben): there the object is a
disambiguator, it is carried by the merged language alone, and the homonym rule above
governs it.

**Pick the word a speaker says, never the word that sits furthest from another card.**
Two realizations one edit apart are safe by construction:
a typed form that is exactly some card's answer grades `Match.OtherWord`
instead of earning typo credit (`../../kern/README.md` § catalog-wide produce grading),
which is why sw `kupata` has always stood beside `kukata`
and de `sehen` beside `stehen`.
The bar that does bind is a display-identical prompt inside ONE area,
where the area label would be the same cue on both — and lint holds that,
so it is never a distance anyone has to measure while authoring.

## Which area a concept lives in

**An area holds a few dozen cards.** It is a shelf a learner can hold in their head and
choose to pull forward, not a drawer everything vaguely related falls into — so an area
growing past roughly forty asks to be cut along the seam a learner would name
(the doctor's visit out of health, the clock out of the everyday words, the colors out
before they ever land there). The cut is cheap: the slug is the card id and carries no
area, so nobody's schedule notices.

**No area may be the leftover bin.** An area whose name states no test for belonging will
refill, because every word that fits nowhere fits there — which is exactly what happened
to the area once called `essentials`, "Das Wichtigste im Alltag", until it had to be
dissolved into the scenes it was holding. Every area's name must therefore answer *what
gets in*, and the answer must be able to say no: a room (`kitchen`), an errand (`admin`),
a kind of word (`colors`, `qualities`), or — for the words that belong to no scene at
all — the deliberately narrow `verbs`, which admits a word only when no scene claims it.
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
Orientation words (`left`, `right`) sit in `nature` for want of a better scene:
they belong to no room and to no errand,
and an area of two words would earn nothing.

Moving one is mechanical and cheap — the slug is the card id and carries no area,
so nobody's schedule notices — and `../../scripts/catalog-move.py` is what does it:
it carries every language's realization verbatim,
appends words before the destination's phrase block,
and refuses a move that would part a phrase from a component,
a feminine from its base, or mint a same-area prompt collision.
