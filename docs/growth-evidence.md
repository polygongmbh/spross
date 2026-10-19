# What the evidence says about pacing
The literature behind how the box bounds new words, which words arrive together and how a missed word comes back.
Neighbors: the resulting rules `../kern/README.md` §6.

The box is breadth-first: exposure to a lot of the language, accepting that any
single word may not stick.

## The short version

Intake is bounded by **two** things: how many cards a sitting tests (`sessionCap`)
and how many first sights one round offers (`NEW_CARDS_PER_ROUND`).
Nothing throttles on how shaky the material is,
and nothing throttles on how far behind the box has fallen.

## Proactive interference does not survive spaced practice

Underwood (1957): recall of a new list falls from ~80% to ~20% as prior lists accumulate.
The caveat: studies were included only if prior learning was massed.
Underwood & Ekstrand (1966/67) got interference from massed prior learning
and none at all when the same learning was spread over four days.
Wixted's review
([*Annual Review of Psychology* 55, 235-269](https://cenl.ucsd.edu/Jclub/Wixted_2004.pdf)):
if learning is typically distributed, proactive interference may not be a major source of
everyday forgetting.
A spaced-repetition app is exactly the regime where this evidence evaporates.

## List length: real, tiny, and not an encoding cost

Yim, Dennis & Osth (2025), *JEP: General* 154(10), 2772-2799 -- N = 3,612,
list lengths 8/16/32/64/80, retention interval equated
([preprint](http://lapensee.ivyro.net/my_articles/published/YimDennisOsth_LL)):

- d' falls 1.41 -> 1.04 (BF = 1.95x10^8) -- the effect is real
- **entirely false alarms**: FA .24 -> .38, while **hits stay flat .73 -> .73**, BF = .029
- square-root form, flattens fast; no interaction with delay

Hits flat from 8 to 80 items: a longer list does not degrade encoding.
The cost is discriminability against lures, and it is small for a ten-fold increase.

Brandt, Zaiser & Schnuerch (2019), *JEP:LMC* 45(5):
the effect is present for homogeneous material (d = 0.97-1.16)
and absent for heterogeneous lists (F < 1).

## Working memory and cognitive load do not apply

- **Cowan's "4"**: defined over familiar integrated chunks,
  explicitly excludes foreign or nonsense words
  ([2005 chapter](https://memory.psych.missouri.edu/assets/doc/articles/2005/cowan-2005-izawa-volume-draft.pdf)).
- **Cognitive load theory**: Sweller, van Merrienboer & Paas (2019) state it is
  only relevant for complex learning; Sweller (1994) calls foreign vocabulary
  low element interactivity. CLT does not predict that 30 independent word pairs
  is a heavy load.
- Miller (1956) called his own number a coincidence and does not discuss learning.

## The direct L2 test finds set size near-irrelevant

Nakata & Webb (2016), *SSLA* 38(3)
([ERIC](https://eric.ed.gov/?id=EJ1113915)) -- 169 undergraduates,
20-item sets against 4- and 10-item sets with spacing matched:

> Part learning produced more correct retrievals during learning,
> but **not in posttests** ...
> as long as spacing is equivalent, the part-whole distinction has little effect.

With exposures fixed, session size is lag:
a smaller batch shortens within-session spacing, so shrinking a round is massing.

Corroborating: Healy, Schneider & Kole (2025), *Behavioral Sciences* 15(5), 692
([PMC](https://pmc.ncbi.nlm.nih.gov/articles/PMC12108878/)):
blocks of 6 vs 12 differ during first-session acquisition and converge by session 2.

## What does survive: output interference

Criss, Malmberg & Shiffrin (2011), *JML* 64(4)
([PDF](https://memolab.syr.edu/wp-content/uploads/2021/04/CrissMalmbergShiffrin_2011.pdf)):
d' declines ~0.82 -> 0.42 across 150 test trials in a single sitting.
Present on first study-test cycle; survives delay, constant lag, feedback, forced choice.

Output interference knocks down hits with false alarms stable --
the mirror image of the list-length effect -- and by far the larger.
Osth et al. (2018): the number of items tested matters more than the number studied.

**This argues for bounding the sitting length (`sessionCap`), not the intake.**
The measurement is word-list recognition over 150 trials,
so it sets the direction and not the number 24.

## A cap on the sitting is free; the floor under it needs a different argument

Output interference supplies the memory half.
Welbers et al. (2019)
([DOI](https://journals.sagepub.com/doi/10.1177/2042753018818342)):
a daily session limit spread use over more unique days (b = 0.62, p < .01)
with no loss in total sessions (66 vs 41, n.s.).

`SESSION_FLOOR_CARDS` rests on Kornell (2009): a 20-card stack beat four 5-card stacks,
because the larger stack lengthens within-session spacing -- a short round is a massed round.
The floor is filled with reviews rather than left short.

## Pulling reviews forward is nearly free, if they are the soonest-due ones

FSRS: stability gain from a successful review is larger at low retrievability,
so reviewing near-certain recall buys almost nothing
([Expertium's algorithm notes](https://expertium.github.io/Algorithm.html)).
Soonest-due-first pulls cards closest to needing review anyway;
the engine's `fillOut` sorts by due.

## Related words cost trials, not retention

Seed order deliberately lets an area's related words arrive together (`../kern/README.md` §6).
The early evidence is against SEMANTIC sets (spoon, fork, knife):
Tinkham (1993), *System* 21(3), found them needing more trials than unrelated words,
and Waring (1997), "The negative effects of learning words in semantic sets: A replication", *System* 25(2), confirmed it.
Thematic sets (words of one scene) helped where semantic ones hurt (Tinkham 1997, *Second Language Research* 13(2)),
and later studies split both ways rather than replicating the harm.

Nakata & Suzuki (2019), *SSLA* 41(2)
([Cambridge](https://www.cambridge.org/core/journals/studies-in-second-language-acquisition/article/effects-of-massing-and-spacing-on-the-learning-of-semantically-related-and-unrelated-words/F58BA8D70385603B9C42E408BFCB8A10)):
meta-analysis where the outcome measure decides the answer:

- trials-to-criterion d = 0.73 [0.41, 1.05] -- semantic sets cost more trials
- posttests d = -0.24 [-0.71, 0.23] -- crosses zero

Their own study (N = 133): no relatedness effect at one week;
within-set confusion during learning 8.22% vs 2.69% (d = 0.86).

So the cost is paid in early misses and the retention is the same,
while a set shows what an unrelated list cannot — how the language builds its words:
antonyms learned as pairs (Yang 2021, *Chinese as a Second Language* 56(1)),
and a stem met across its family (rein-/rausgehen), the unit Bauer & Nation (1993), *International Journal of Lexicography* 6(4), count vocabulary in.

## The tuning trap

In-session accuracy reliably improves when batches shrink
and reliably fails to predict retention
(Soderstrom & Bjork 2015, "Learning versus performance", *Perspectives on Psychological Science* 10(2)).
A scheduler tuned to maximize in-session correctness is optimizing
the illusion of a good round.

## A missed word comes back soon, then later

The retry ladder (`BoxConfig.stepsSeconds`) alternates ten minutes with growing day-scale waits.
Latimier, Peyre & Ramus (2021), *Educational Psychology Review* 33, 959-987, meta-analyzed 29 studies:
spaced retrieval beats massed by g = 0.74,
while expanding against uniform gaps is g = 0.034, n.s. --
spacing matters and the schedule's shape barely does,
so the first gap only has to be short enough for the next attempt to succeed.
Expanding gained a little as the number of tests grew, which the widening day steps keep.

A failed attempt with the answer shown is worth about a restudy for an arbitrary pair like a translation:
errorful generation strengthens the cue and the target on their own but not the link between them
(Seabrooke et al. 2019, *JML*).
Potts & Shanks (2014), *JEP: General* 143(2), found the opposite with foreign-word translations,
so the reading is contested; neither side argues for pushing a missed word further out.

The retry belongs to the next sitting rather than the tail of this one:
one correct recall in each of three spaced sessions beat three in a single session
(Rawson & Dunlosky 2022, "Successive relearning", *Current Directions in Psychological Science*).

A word still missed at the top of the ladder is a leech,
and no study says what to do with one;
rewriting the word or letting it go is practitioner advice (Wozniak's "Twenty rules of formulating knowledge").

## A standing backlog is the normal state, and no gate should close on it

**What intake costs is the reviews it draws.**
A new card accrues roughly four to five reviews in its first year at `desiredRetention` 0.85,
putting the <= 4-card reserve near 18 reviews/day against a `sessionCap` of 24.
The margin narrows as a box ages.
A remainder left over is normal, not a debt.

**Falling behind costs a settled word little and a young one plenty.**
The tail of `R(t) = (1 + 0.98*t/S)^-0.1542` is flat in proportion to stability:
30 days late leaves a mature card (S 60) at 0.79, a settling one (S 10) at 0.74,
and a word met once (S 2.31) at 0.65.

**The health gate cost fell on the returning learner:**
coming back after two weeks away was exactly when it shut,
so the box went silent at the moment the learner re-engaged.

## What the other systems do is not evidence

- **Anki** defaults to 20 new cards/day; its stated rationale is downstream load.
- **Anki's FSRS FAQ**: no optimal number; FSRS works equally well at 5 or 50.
- **SuperMemo** treats overload as a workload problem solved by postponing reviews.
- **Memrise** defaults to ~5 words/session with no published rationale.

Practitioner consensus converges on a number because everyone copied one;
where the authors say why, the reason is review load.

## Verification note

The load-bearing findings above are from accessible primary sources.
A few older figures could not be confirmed at source and are cited only where
the direction, not the number, carries the argument.

**The gap worth naming**: no peer-reviewed study manipulates new-card intake rate
in a real spaced-repetition system over weeks or months with both retention
and dropout as outcomes.
Every number the box picks is a product judgment,
and the evidence's job is to say which judgments are ruled out.
