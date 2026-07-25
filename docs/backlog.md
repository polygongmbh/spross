# Backlog — session-discovered, out-of-scope issues

Issues discovered mid-session that fall outside the current scope:
append them here instead of scattering notes across other docs;
prune an item when it is fixed.
One line per item, with a file or context pointer, filed under the section it belongs to.

## Content & catalog

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

## Localization

- Xcode rewrites `Localizable.xcstrings` behind the build: its index-based extractor
  reads `Text("key \(count)")` as `%@` where the compiler emits `%lld`,
  so opening the project adds dead `%@` twins and marks the live keys stale.
  Interpolating pre-formatted strings would make both agree — until then,
  diff the catalog against the compiler's `.stringsdata` before committing it.
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

## Verification gaps

- Watch pairing untested on real hardware;
  complication rendering never screenshot-verified (no simctl affordance).
