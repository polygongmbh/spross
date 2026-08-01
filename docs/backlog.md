# Backlog — session-discovered, out-of-scope issues

Issues discovered mid-session that fall outside the current scope:
append them here instead of scattering notes across other docs;
prune an item when it is fixed.
One line per item, with a file or context pointer, filed under the section it belongs to.

## Content & catalog

- uk plural sweep unverified by a native speaker: the irregular forms authored in
  `catalog/*/uk.json` (stem alternations, fleeting vowels, agreeing phrases) are
  LLM-authored — the substantivized `лікарняні` and the phrase plurals most of all.
- uk native-verification sweep pending for the 4 LLM-adapted twin realizations —
  kitchen pot-on-stove, hall doorbell-rang, desk laptop-charge, desk wifi-gone
  (method: `../../docs/sprachposter-learnings.md`).
- Phrase→component auto-linking gaps: ~half of phrases carry no `components`
  (naive matcher — `catalog/README.md` § concepts.json).
- Pronunciation coverage is partial and uneven — 200/402 de single words, 280/384 sw,
  304/399 uk carry a recording (`catalog/audio/`), and NO phrase carries one: the packs
  only ever matched single-word realizations, leaving ~126 phrases per language to TTS
  (silent on sw-iOS, which has no voice). What Commons never had is listed per pack in
  `data/reference/audio/pack-*/missing.txt` (de 171, uk 37, sw 17); gap-filling
  (commissioning or a paid voice) is a content project, scoped in that folder's README.
- Each language speaks with one voice: sw and uk are a single speaker throughout, de is
  half one (104 of 200) — one accent, one register, one microphone per language — and uk
  `ь` has no letter recording at all (32/33).

## Engine & scheduling

- Watch snapshot 60-entry cap: due-first ranking keeps due cards on-watch,
  but revisit the cap if the active box outgrows it (kern README §7).
- sw/uk number near-twins gated in `TrainerTypoBridgeGuardTests`
  (sw `nne`↔`nane` incl. tens compounds; uk `дев'ять`↔`десять`):
  at the drill's one-slip-per-word budget one can pass for the other —
  product call pending (no slips at all for sw/uk number drills vs accept).
- FSRS parameter optimization from review logs —
  enabled by the full per-card logs, unbuilt (kern README §5).
- Watch multiple-choice distractors carry no novelty or recency criterion
  (`kern/src/commonMain/kotlin/net/spross/kern/session/MultipleChoice.kt`):
  word class, area and shape rank them now, but the newest entry can still be the odd
  one out — the same class of problem the phone's due-order reshuffle fixed,
  on another surface.
- `WatchEntryDto.accepted` ships every entry's full target family and no watch surface
  reads it (`Shared/Sources/WatchSnapshot.swift`): its doc calls it reveal display,
  but the quiz has no such reveal. Either the watch grows the phone's "auch: …" line,
  or the field leaves the wire, where it is a fair slice of the ~60 KB budget
  (kern README §7).
- Rating labels carry more weight on a first exposure now that Good sends a word about a week
  out (kern README §5) — the button wording deserves a look
  (`App/Sources/Design/RatingButtonsView.swift`).
- Automatic growth walks seed order, so a round's new words are seed neighbours and often the
  same semantic field (`Growth.newCandidates` step 2b) — the L2 literature on semantic
  clustering says a same-field batch is the harder batch, and batch composition looks like a
  cheaper win than batch size. A field-spreading pick would keep introduction fair without
  touching the budget.
- `dueSoftCap` (30) has no anchor in the learner's actual throughput: the health gate's
  mechanism — throttle introduction, never hide reviews — matches practitioner consensus,
  but the threshold would be truer as a multiple of what a day really answers
  (`Growth.healthGateOpen`, kern README §6).

## Localization

- Watch, widget, and complication chrome is hardcoded German with no string catalog
  (`Watch/Sources/WatchHomeView.swift`, `Widgets/Sources/WordWidgetView.swift`,
  `WatchWidgets/Sources/WatchWordWidgetView.swift`) —
  needs its own catalog plus a chrome-language field on the snapshot,
  since those surfaces never see `AppModel.knownLocale`.

## Platform reach

- Android not yet ported: Box browse, trainers, widget, 14-day strip, confetti/haptics
  (`design.md` § Android companion).
- Android carries its own unrelated palette (`android/.../ui/Theme.kt`) that never went
  through the contrast pass — it predates the ocean/forest re-cut and shares no values
  with `Design/Theme.swift`.
- Audio ships un-thinned: `catalog/audio/` is 19 MB (de 4.9, sw 5.2, uk 9.0) and BOTH
  installs carry all of it — the iOS folder reference and the Android catalog sync copy
  the tree whole — so a Swahili learner downloads 14 MB of German and Ukrainian they can
  never hear. Per-language delivery (on-demand resources / Play asset packs) would cut
  the install to the target actually being learned; measure the real per-platform delta
  before choosing a mechanism.

## Verification gaps

- Watch pairing untested on real hardware;
  complication rendering never screenshot-verified (no simctl affordance).
