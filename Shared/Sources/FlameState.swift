import Foundation

/// How the streak's flame burns: kern's `StreakHealth`, one case each, named for the mark.
/// Which day puts a run in which case is kern's ruling (`kern/docs/reports.md`).
/// The raw values are kern's case names — the widget decodes them off its snapshot,
/// the app maps the bridged enum by the same name.
enum FlameState: String, Codable {
    /// Today has reviews — the run is safe until tomorrow.
    case lit = "Earned"
    /// Nothing today yet, but a miss would only spend the run's one bridge.
    case dwindling = "Bridgeable"
    /// Nothing today, and the bridge is already spent — a miss ends the run.
    case atRisk = "Ending"
    /// No run to protect.
    case unlit = "NoRun"
}
