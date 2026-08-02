# What the evidence says about new-word intake

Why the box bounds new words the way it does, and why it stopped bounding them the other way.
This doc owns the literature; `kern/README.md` §6 owns the resulting rules and points here.

The box is deliberately breadth-first: exposure to a lot of the language, accepting that any
single word may not stick. That makes "how many new words is too many" the load-bearing
question, so it is worth knowing how thin the evidence for a cap actually is.

## The short version

Intake is bounded by **two** things: how many cards a sitting tests (`sessionCap`) and how many
first sights one round offers (`NEW_CARDS_PER_ROUND`). Nothing throttles on how *shaky* the
material is, and nothing throttles on how far behind the box has fallen. Both of those throttles
existed and were removed on 2026-08-01 — `maxUnsettled` against `unsettledLoad`, and the
`dueSoftCap` health gate. The sections below are why; the backlog one is arithmetic rather than
literature, so it is [its own section](#the-backlog-gate-was-arithmetic-not-evidence).

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

## A cap on the sitting is free; the floor under it needs a different argument

Bounding the sitting is the one cap with support on both sides of the ledger. Output
interference supplies the memory half; the engagement half is Welbers et al. (2019), a field
experiment with N = 101 on a study app
([DOI](https://journals.sagepub.com/doi/10.1177/2042753018818342)): a daily session limit
**spread use over more unique days** (b = 0.62, p < .01) with **no loss in total sessions**
(66 vs 41, n.s.). A cap is not a tax on engagement. It is one experiment on one app rather than
a vocabulary SRS, so read it as "no evidence a cap costs adherence" rather than proof it helps.

`SESSION_FLOOR_CARDS` is the opposite direction and rests on a weaker footing than its own
comment claims. "A round this short reads as the app having nothing to give" is a product
judgement with **no evidence behind it** — nothing in this literature says short sessions feel
worthless or drive anyone away. What does support a floor is Kornell (2009), on spacing rather
than feeling: a 20-card stack beat four 5-card stacks, because the larger stack lengthens
within-session spacing, and ~90 % of participants learned more spaced while ~72 % believed the
reverse. That is the same mechanism as the Nakata & Webb result above — **a short round is a
massed round** — and it is why the floor is filled out with reviews rather than left short.
Habit research is sometimes offered here too and does not reach: Lally-lineage work
([Springer](https://link.springer.com/article/10.1007/s10865-015-9640-7)) puts automaticity at
~66 days with wide variance and ties habit strength to positive affect during the activity —
an argument for ending a session before it turns aversive, not for any particular floor.

## Pulling reviews forward is nearly free, if they are the soonest-due ones

The floor and the quiet-day reservation both pull not-yet-due cards forward, so the question is
what early review costs. FSRS answers it structurally: the stability gain from a successful
review is larger at **low** retrievability, so reviewing a card while recall is still near-certain
buys almost nothing ([Expertium's algorithm notes](https://expertium.github.io/Algorithm.html)),
and the fsrs4anki helper frames its own Advance feature as damage minimisation rather than a
benefit. There is no published cost curve for "k days early".

This is why the ordering matters more than the count: soonest-due-first pulls the cards closest
to needing the review anyway, where the loss rounds to nothing, while pulling an arbitrary undue
card burns real spacing. The engine's rules already follow from this — `fillOut` sorts by due,
and the quiet-day reservation reaches only as far as tomorrow.

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

## The backlog gate was arithmetic, not evidence

The health gate shut growth off entirely once the projected post-session backlog
(`dueCount − sessionCap`) reached `dueSoftCap`. Unlike the throttles above it aimed at a real
failure mode — a queue the learner never works off — but it was never needed to prevent one,
and it contradicted the mechanism sitting next to it.

**The reserve already bounds intake to a small constant.** `growthReserve` is ≤ 5 slots and does
not scale with the queue, so a sitting introduces at most a handful of cards no matter how far
behind the box is. At `desiredRetention` 0.8 that same sitting sends the great majority of ~25
reviewed cards away on longer intervals, and FSRS shrinks each card's ongoing load as its
stability grows. Cards leave the daily queue faster than five a day enter it, so growth cannot
compound into a backlog — the ratio is structural, not a tuning question.

**The two mechanisms were fighting.** `growthReserve` exists precisely so a full due queue cannot
starve growth; the health gate existed to starve growth anyway once the queue got full enough.
Keeping both meant the box grew through a busy period and then stopped at an arbitrary
threshold — with no bound on `dueSoftCap` derived from what a learner actually answers.

**The cost of keeping it fell on the returning learner.** Coming back after two weeks away is
exactly when the gate shut, so the box that had been growing daily went silent at the moment the
learner re-engaged, and stayed silent until the backlog cleared. Nothing above supports paying
that for a backlog the arithmetic says will not run away.

**Delay itself costs little.** The best available measurement is one practitioner's 13-year Anki
history — 105,393 reviews that came due late
([analysis](https://controlaltbackspace.org/assets/attachments/overduecards.html)) — where
overdue cards were forgotten 14.8 % of the time against a 13.6 % baseline: a penalty of about
1.8 points. n = 1 and not peer-reviewed, but it points the same way FSRS does structurally, since
a successful review while retrievability has decayed raises stability more than an early one
(see the section above). A backlog is an adherence problem, not a memory one, and no study links
backlog size to dropout — that link is folklore.

What remains as backlog protection is the ordering, not a brake: reviews fill the session first
and new cards take only what the reserve holds back, so a busy box spends nearly its whole
sitting catching up on its own.

## What the other systems do is not evidence

The obvious objection to all of the above is that every established SRS caps intake, so the
numbers must mean something. They do not have data behind them.

- **Anki** defaults to 20 new cards a day, and the manual's only stated rationale is downstream
  load — 20 a day settles at roughly 200 reviews a day
  ([deck options](https://docs.ankiweb.net/deck-options.html)). That is an argument about
  sitting length, which `sessionCap` already bounds.
- **Anki's own FSRS FAQ** states there is no optimal number of new cards per day and that FSRS
  "works equally well whether you are learning 5 or 50"
  ([FAQ](https://faqs.ankiweb.net/frequently-asked-questions-about-fsrs.html)).
- **SuperMemo** names overload the main cause of drop-outs
  ([wiki](https://supermemo.guru/wiki/Overload)) but treats it as a workload problem solved by
  postponing reviews, not by an intake cap.
- **Memrise** defaults to about 5 words per session, with no published rationale.
- The load-based approach the retired `maxUnsettled` throttle implemented exists as an Anki
  add-on ("Limit New by Young"), whose case is purely structural — feed new cards at the rate
  the learner matures them — with no data offered.

Practitioner consensus converges on a number because everyone copied a number, and where its
authors do say why, the reason is review load rather than retention.

## Verification note

The load-bearing findings above are from accessible primary sources. A few older figures could
not be confirmed at source and are cited only where the direction, not the number, carries the
argument: the exact recall percentages in Kornell (2009), and the statistics inside Tinkham
(1993/1997), Waring (1997) and Erten & Tekin (2008), all of which are paywalled and reached via
named secondaries. Two supporting figures are deliberately weak evidence and are labelled as
such where they appear: the overdue-review penalty is one practitioner's own history (n = 1,
not peer-reviewed), and Welbers et al. (2019) is a single field experiment on a study app rather
than a vocabulary SRS.

**The gap worth naming**: no peer-reviewed study manipulates new-card intake rate in a real
spaced-repetition system over weeks or months with both retention *and* dropout as outcomes.
The retention side of this literature is lab-scale and short; the dropout side is anecdote. Every
number the box picks is therefore a product judgement, and the evidence's job is to say which
judgements are *ruled out* rather than which one is right.
