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
  (repo/tag/SHA). The stability/difficulty (memory-state) formulas run unmodified
  against the reference — `Fsrs.nextMemory` is untouched — but the (re)learning-STEP
  MACHINE (`FsrsScheduler`) is product-owned and diverges even at this class's own
  defaults (`FsrsParameters.stepsSeconds` = `[10m]`, desired retention 0.9 still
  matches): see `../README.md` §5 for what changed and why, and `FsrsGoldenVectorTest`
  for which vectors had to be recomputed against the divergence rather than copied.
- **Graduated intervals are continuous in the product.** `Fsrs.intervalRawDays` is the
  fractional interval the model asks for; `FsrsScheduler.graduate` quantizes it to
  `intervalGranularitySeconds` and floors it at `minimumIntervalSeconds`.
  Both default to 86_400 s — whole-day rounding is the reference bucket convention, not part
  of FSRS, and the default keeps the golden vectors on their exact day multiples.
  The product sets granularity to 1 s, so a 7.6-day interval is scheduled at 7.6 days.
- The (re)learning step machine keeps ONE piece of the reference machine — the Hard
  interval's whole-minute-rounded blend of the ladder's first two entries — and diverges
  on Again (climbs the ladder instead of resetting to step 0) and Good/Easy (graduates
  immediately instead of walking every configured step); `FsrsStepLadderTest` covers
  both the kept piece and the divergence against the pinned minute tables where they
  still apply. `../README.md` §5 has the product's own ladder and why.
