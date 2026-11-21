# Scheduling evidence — FSRS-6 and the retry ladder
The studies behind the scheduling model and the retry ladder; the reference pins, formulas and the product's divergences are the KDoc on `Fsrs`, `FsrsParameters`, `FsrsScheduler` and `FsrsGoldenVectorTest`.
Neighbors: the product's rules on top `../kern/README.md` §5, new-word intake `growth-evidence.md`.

FSRS descends from Ye, Su & Cao (2022), "A stochastic shortest path algorithm for optimizing spaced repetition scheduling", KDD,
and is benchmarked against SM-2 and others on real review logs at `open-spaced-repetition/srs-benchmark`.

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
