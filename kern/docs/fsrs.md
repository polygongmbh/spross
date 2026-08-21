# FSRS-6 — parameters, provenance and graduation

The reference implementation's own facts, kept where the golden vectors can be checked
against them. What the PRODUCT decided on top is the contract's (`../README.md` §5).

- 21 weights; defaults = ts-fsrs v5.4.1 / py-fsrs v6.3.1 (identical), **w20 decay 0.1542**
  (brief's 0.2 was a pre-release value). Formula set + cross-check resolutions per the
  pinned reference report: same-day `sinc >= 1` mask for G >= Hard, S_MIN 0.001, fuzz OFF,
  engine maximum_interval 36500.
- `elapsedDays` = fractional `max(0, (now - lastLog)/86400)`; short-term path < 1.0.
  Golden vectors all review exactly at due; real-timestamp vectors stay out of the suite.
- Golden vectors are copied verbatim from the pinned releases with PROVENANCE
  (repo/tag/SHA), and the engine's own defaults stay the reference pair
  (`learning [1m, 10m]`, `relearning [10m]`, desired retention 0.9) so they run unmodified.
- **Graduated intervals are continuous in the product.** `Fsrs.intervalRawDays` is the
  fractional interval the model asks for; `FsrsScheduler.graduate` quantizes it to
  `intervalGranularitySeconds` and floors it at `minimumIntervalSeconds`.
  Both default to 86_400 s — whole-day rounding is the reference bucket convention, not part
  of FSRS, and the default keeps the golden vectors on their exact day multiples.
  The product sets granularity to 1 s, so a 7.6-day interval is scheduled at 7.6 days.
- Two minute-scale learning steps put a missed word back in front of the learner half a
  dozen cards later, where it passes on being recognized as "that new one" rather than on
  the source-target pair having bound; `3m` was tried first and outlasted the day's practice
  altogether. Graduation otherwise follows the reference machine, tested against the pinned
  minute tables.
