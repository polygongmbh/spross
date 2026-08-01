# What the evidence says about new-word intake

Why the box bounds new words the way it does, and why it stopped bounding them the other way.
This doc owns the literature; `kern/README.md` §6 owns the resulting rules and points here.

The box is deliberately breadth-first: exposure to a lot of the language, accepting that any
single word may not stick. That makes "how many new words is too many" the load-bearing
question, so it is worth knowing how thin the evidence for a cap actually is.

## The short version

Intake is bounded by **two** things: how many cards a sitting tests (`sessionCap`) and how many
first sights one round offers (`NEW_CARDS_PER_ROUND`), with the **health gate** stopping growth
when the due backlog outruns what a session can work off. Nothing throttles on how *shaky* the
material is. That third throttle existed (`maxUnsettled` against `unsettledLoad`) and was
removed on 2026-08-01; the sections below are why.

## Proactive interference does not survive spaced practice

The classic case for "too much in flight hurts" is Underwood (1957): recall of a new list falls
from ~80 % to ~20 % as prior lists accumulate. The load-bearing caveat is one line in the
original — **studies were included only if the prior learning was massed**.

Underwood's own lab then failed to extend it. Underwood & Postman (1960, *Psych Review* 67,
73–95) predicted words rich in pre-experimental associations would decay faster than trigrams
over a week; forgetting rates were identical. Underwood & Ekstrand (1966/67) got interference
easily from massed prior learning and **none at all** when the same learning was spread over
four days.

Wixted's review is the summary to cite:
[*Annual Review of Psychology* 55, 235–269](https://cenl.ucsd.edu/Jclub/Wixted_2004.pdf) —
if learning is typically distributed, proactive interference may not be a major source of
everyday forgetting. **A spaced-repetition app is exactly the regime where this evidence
evaporates.**

## List length: real, tiny, and not an encoding cost

Yim, Dennis & Osth (2025), *JEP: General* 154(10), 2772–2799 — N = 3,612, list lengths
8/16/32/64/80, retention interval equated, only the first 8 studied items analyzed so output
interference cannot contaminate it
([preprint](http://lapensee.ivyro.net/my_articles/published/YimDennisOsth_LL)):

- d′ falls 1.41 → 1.04 (BF₁₀ = 1.95 × 10⁸) — the effect is real
- **entirely false alarms**: FA .24 → .38, while **hits stay flat .73 → .73**, BF₁₀ = .029,
  positive evidence *for* the null on hits
- square-root form, so it flattens fast; no interaction with delay

Hits flat from 8 to 80 items is direct evidence that a longer list does not degrade encoding.
The cost is discriminability against lures, and it is Δd′ ≈ 0.37 for a ten-fold increase.

Brandt, Zaiser & Schnuerch (2019), *JEP:LMC* 45(5): the effect is present for **homogeneous**
material (d = 0.97–1.16) and **absent for heterogeneous lists** (F < 1).

## Working memory and cognitive load do not apply

Both frameworks are routinely invoked for new-card limits, and both exclude this case by their
authors' own scope statements.

- **Cowan's "4"** is defined over *"sets of stimuli that are familiar so that each item is
  represented in memory initially as an integrated chunk … **but not foreign or nonsense
  words***"
  ([2005 chapter](https://memory.psych.missouri.edu/assets/doc/articles/2005/cowan-2005-izawa-volume-draft.pdf)).
  Novel L2 word forms are explicitly outside the paradigm that produces the number.
- **Cognitive load theory**: Sweller, van Merriënboer & Paas (2019) state it is *"only relevant
  for complex learning"*, and Sweller (1994) calls foreign vocabulary **low element
  interactivity**. Element interactivity is about simultaneity, not count, so CLT does not
  predict that 30 independent word pairs is a heavy load. For low-EI material it partly
  reverses: Chen, Kalyuga & Sweller (2015) find the generation effect beats worked examples.
- Miller (1956) called his own number *"a pernicious, Pythagorean coincidence"* and does not
  discuss learning at all.

## The direct L2 test finds set size near-irrelevant

Nakata & Webb (2016), *SSLA* 38(3) ([ERIC](https://eric.ed.gov/?id=EJ1113915)) — 169
undergraduates, two experiments, 20-item sets against 4- and 10-item sets **with spacing
matched**, receptive and productive posttests immediately and at one week:

> Part learning produced more correct retrievals during learning, but **not in posttests** …
> as long as spacing is equivalent, the part–whole distinction has little effect on learning,
> and spacing has a larger effect than the part–whole distinction.

This is the cleanest test of the actual question. With exposures fixed, **session size is lag**:
a smaller batch mechanically shortens within-session spacing, so shrinking a round *is* massing.

Corroborating: Healy, Schneider & Kole (2025), *Behavioral Sciences* 15(5), 692
([PMC](https://pmc.ncbi.nlm.nih.gov/articles/PMC12108878/)) — blocks of 6 vs 12 differ during
first-session acquisition (η² = .180) and converge by session 2, with no difference immediately
or at one week.

The apparent counter-evidence, Pajkossy & Racsmány (2019)
([PMC](https://pmc.ncbi.nlm.nih.gov/articles/PMC6938364/)), shows a steep drop across blocks of
2/4/8 — but each pair was presented **once**, with no repetition, no feedback and no delayed
test. It is a short-term interference result, not a claim about retention under a spaced
schedule.

## What does survive: output interference

Criss, Malmberg & Shiffrin (2011), *JML* 64(4)
([PDF](https://memolab.syr.edu/wp-content/uploads/2021/04/CrissMalmbergShiffrin_2011.pdf)):
d′ declines ~0.82 → 0.42 across 150 test trials in a single sitting, F(14,770) = 6.24,
p < .001. Present on the first study–test cycle, so not fatigue; survives a delay, constant
study–test lag, feedback, and forced choice.

Note the signatures are mirror images — output interference knocks down **hits** with false
alarms stable, while the list-length effect pushes up **false alarms** with hits stable — and
output interference is by far the larger. Osth et al. (2018) conclude the number of items
*tested* matters more than the number *studied*.

**This is the one memory-based argument for a cap that survives scrutiny, and it argues for
bounding the length of a sitting — `sessionCap` — not the intake.**

## Semantic clustering is weaker than the folklore

Relevant to how new words are *ordered*, not how many enter. Nakata & Suzuki (2019),
*SSLA* 41(2)
([Cambridge](https://www.cambridge.org/core/journals/studies-in-second-language-acquisition/article/effects-of-massing-and-spacing-on-the-learning-of-semantically-related-and-unrelated-words/F58BA8D70385603B9C42E408BFCB8A10))
includes a small meta-analysis where **the outcome measure decides the answer**:

- trials-to-criterion **d = 0.73 [0.41, 1.05]** — semantic sets cost more trials
- posttests **d = −0.24 [−0.71, 0.23]** — crosses zero

Their own study (N = 133, matched on familiarity, frequency and pronounceability) found no
relatedness effect immediately or at one week; what differed was within-set confusion during
learning (8.22 % vs 2.69 %, d = 0.86). Two factors showed *larger* effects than category
membership in the same literature: shared initial letters, and L1 familiarity. There is no
meta-analysis of L2 semantic clustering, and Tinkham (1993), often cited as foundational, had
N = 20 acquaintances of the researcher with presentation differing between groups.

## The tuning trap

In-session accuracy is the metric that reliably improves when batches shrink and reliably fails
to predict retention (Nakata & Webb above; Healy et al. 2025; Kornell's 90 %-better-but-72 %-
believed-otherwise result). **A scheduler tuned to maximize in-session correctness is optimizing
the illusion of a good round.**

This is exactly what the retired `maxUnsettled` throttle did. `isSettled` is
`phase == Review && stability >= settledStability`, a bar a single **Good** clears
(S₀(Good) = 2.3065 > 2.0), so `unsettledLoad` counted the words recently answered *wrong* and
narrowed breadth in response. A breadth-first box can pick a metric that makes removing a cap
look good just as easily as a depth-first one can pick one that makes keeping it look good;
neither is a finding.

## Verification note

The load-bearing findings above are from accessible primary sources. A few older figures could
not be confirmed at source and are cited only where the direction, not the number, carries the
argument: the exact recall percentages in Kornell (2009), and the statistics inside Tinkham
(1993/1997), Waring (1997) and Erten & Tekin (2008), all of which are paywalled and reached via
named secondaries.
