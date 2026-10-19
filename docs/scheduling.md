# Scheduling — FSRS-6 and the retry ladder
The FSRS-6 reference implementation's own facts, kept where the golden vectors can be checked against them, and the evidence behind the model and the ladder.
Neighbors: the product's rules on top `../kern/README.md` §5, new-word intake `growth-evidence.md`.

- 21 weights; defaults = ts-fsrs v5.4.1 / py-fsrs v6.3.1 (identical), **w20 decay 0.1542**
  (the brief's 0.2 was a pre-release value).
  Formula set and cross-check resolutions per the pinned reference report:
  same-day `sinc >= 1` mask for G >= Hard, S_MIN 0.001, fuzz OFF, engine maximum_interval 36500.
- `elapsedDays` = fractional `max(0, (now - lastLog)/86400)`; short-term path < 1.0.
  Golden vectors all review exactly at due; real-timestamp vectors stay out of the suite.
- Golden vectors are copied verbatim from the pinned releases with PROVENANCE (repo/tag/SHA).
  The memory-state formulas run unmodified against the reference (`Fsrs.nextMemory`),
  but the (re)learning-STEP MACHINE (`FsrsScheduler`) is product-owned
  and diverges even at this class's own defaults (`FsrsParameters.stepsSeconds` = `[10m]`);
  `FsrsGoldenVectorTest` says which vectors were recomputed against the divergence rather than copied.
- The step machine diverges on every rating:
  `Again` climbs the ladder instead of resetting to step 0,
  and Hard/Good/Easy graduate immediately instead of walking every configured step —
  the ladder spaces out repeated FAILURES, it does not grade flavors of success.
  `FsrsStepLadderTest` covers that behavior on its own terms;
  the product's own ladder and why is `../kern/README.md` §5.
- **Graduated intervals are continuous in the product.**
  `Fsrs.intervalRawDays` is the fractional interval the model asks for;
  `FsrsScheduler.graduate` quantizes it to `intervalGranularitySeconds`
  and floors it at `minimumIntervalSeconds`.
  Both default to 86_400 s — whole-day rounding is the reference bucket convention, not part of FSRS,
  and the default keeps the golden vectors on their exact day multiples.
  The product sets granularity to 1 s, so a 7.6-day interval is scheduled at 7.6 days.

## Evidence

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
