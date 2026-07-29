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

## Engine & scheduling

- Watch snapshot 60-entry cap: due-first ranking keeps due cards on-watch,
  but revisit the cap if the active box outgrows it (kern README §7).
- sw/uk number near-twins gated in `TrainerTypoBridgeGuardTests`
  (sw `nne`↔`nane` incl. tens compounds; uk `дев'ять`↔`десять`):
  at drill typo budget 1 one can pass for the other —
  product call pending (budget 0 for sw/uk number drills vs accept).
- FSRS parameter optimization from review logs —
  enabled by the full per-card logs, unbuilt (kern README §5).
- Watch multiple-choice distractors rank on `shapeDistance` alone
  (`kern/src/commonMain/kotlin/net/spross/kern/session/MultipleChoice.kt`):
  no novelty or recency criterion, so the newest entry can still be the odd one out —
  the same class of problem the phone's due-order reshuffle fixed, on another surface.
- Rating labels carry more weight on a first exposure now that Good sends a word about a week
  out (kern README §5) — the button wording deserves a look
  (`App/Sources/Design/RatingButtonsView.swift`).

## Localization

- No plural rules in `Localizable.xcstrings`: counted strings read "1 Stellen",
  "1 Wiederholungen" in German and English alike.
  Symbolic keys make per-language plural variations a catalog-only change now.
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

## Verification gaps

- Watch pairing untested on real hardware;
  complication rendering never screenshot-verified (no simctl affordance).
