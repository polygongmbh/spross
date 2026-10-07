import SwiftUI
import SprossKern

/// The one drill Home names (`docs/drills.md` § The suggestion), which leads
/// the day's card wherever it is named (`HomeView.drillLeadCard`). WHICH drill,
/// WHEN and WHY are kern's `DrillSuggestion`; this side reads the stores it is
/// asked about and words the answer. It hangs off the hub because what it
/// opens is a chip's own destination.
extension TrainerHubView {

    /// Kern's pick among the chips this profile offers, while one is named.
    var suggestedDrill: DrillSuggestion.Pick? {
        guard let language = drillLanguage, let box = model.box,
              shown(DrillSuggestion.shared.shown(offer: model.homeOffer))
        else { return nil }
        let store = LadderStore()
        let pairs = model.catalog?.oppositePairs ?? []
        let standings = Drill.allCases.filter { destination(for: $0) != nil }.map { drill in
            let key = DrillSuggestion.shared.lastRunKey(drill: drill, language: language)
            let ladder = DrillLadders.shared.ladder(drill: drill, source: model.sourceLanguage, target: language,
                                                    box: box, oppositePairs: pairs, dates: model.dates,
                                                    store: store)
            return DrillSuggestion.Standing(drill: drill,
                                            lastRunEpochMillis: TrainerProgress.lastRun(key).map { KotlinLong(value: $0) },
                                            ladder: ladder)
        }
        return DrillSuggestion.shared.suggest(standings: standings,
                                              facts: DrillSuggestion.BoxFacts.companion.of(box: box),
                                              nowEpochMillis: Date().epochMillis, tzId: currentTzId(),
                                              language: language)
    }

    private func shown(_ kern: Bool) -> Bool {
        #if DEBUG
        // UI-test hook: `-uitest-suggestion 1` names a drill on any day, and
        // `HomeView.dayLead` leads with it, so a screenshot needs no worked day.
        if UserDefaults.standard.bool(forKey: "uitest-suggestion") { return true }
        #endif
        return kern
    }
}

/// The trainer store, as kern's ladder table reads it.
private final class LadderStore: NSObject, DrillLaddersStore {
    func reached(key: String) -> Int32 { Int32(TrainerProgress.best(for: key)) }
    func cleared(key: String) -> Set<KotlinInt> { TrainerProgress.held(for: key) }
}

extension DrillSuggestion.Pick {
    /// Why the drill is named, in the words of whichever term carried it.
    var reasonText: Text {
        switch reason {
        case .newScript: return Text("home.suggestion.reason.newScript")
        case .earlyNumbers: return Text("home.suggestion.reason.earlyNumbers")
        case .wordsSettled: return Text("home.suggestion.reason.wordsSettled")
        case .neverRun: return Text("home.suggestion.reason.neverRun")
        case .notLately: return Text("home.suggestion.reason.notLately \(Int(daysSinceRun))")
        case .variety: return Text("home.suggestion.reason.variety")
        }
    }
}
