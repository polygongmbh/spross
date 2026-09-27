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
        let standings = Drill.allCases.filter { destination(for: $0) != nil }.map { drill in
            let key = DrillSuggestion.shared.lastRunKey(drill: drill, language: language)
            return DrillSuggestion.Standing(drill: drill,
                                            lastRunEpochMillis: TrainerProgress.lastRun(key).map { KotlinLong(value: $0) },
                                            ladder: ladder(drill, language: language))
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

    /// What each drill has filed of its ladder, read in the shape kern weighs it.
    /// The letter drill files none.
    private func ladder(_ drill: Drill, language: String) -> DrillSuggestion.Ladder? {
        switch drill {
        case .numbers:
            let reached = Dictionary(uniqueKeysWithValues: NumbersExercise.allCases.map {
                ($0, KotlinInt(int: Int32(TrainerProgress.best(
                    for: NumbersMode.companion.progressKey(exercise: $0, language: language)))))
            })
            return DrillSuggestion.Ladder.companion.numbers(reached: reached, language: language)
        case .letters:
            return nil
        case .countries:
            return atlasPair.map { cleared("\(CountryDrillFace.key).\($0.source)-\($0.target)",
                                           top: CountryDrill.shared.ceiling) }
        case .dates:
            return datesPair.map { cleared("\(DateDrillFace.key).\($0.source)-\($0.target)",
                                           top: model.datesSprossen) }
        case .wordScramble:
            return cleared(WordScrambleView.storageKey(language),
                           top: Int(WordScrambleAvailability(model: model).report.maxLevel))
        case .sentenceScramble:
            return cleared(SentenceScrambleView.storageKey(language),
                           top: Int(SentenceScrambleAvailability(model: model).report.maxLevel))
        }
    }

    /// A ladder that files its cleared Sprossen, read the way its run opens: forward.
    private func cleared(_ key: String, top: Int) -> DrillSuggestion.Ladder {
        let forward = NumbersMode.companion.clearedKey(key: key, reverse: false)
        return DrillSuggestion.Ladder.companion.cleared(cleared: TrainerProgress.held(for: forward),
                                                        top: Int32(top))
    }
}

extension DrillSuggestion.Pick {
    /// Why the drill is named, in the words of whichever term carried it.
    var reasonText: Text {
        switch reason {
        case .newScript: return Text("home.suggestion.reason.newScript")
        case .earlyNumbers: return Text("home.suggestion.reason.earlyNumbers")
        case .wordsGrown: return Text("home.suggestion.reason.wordsGrown")
        case .neverRun: return Text("home.suggestion.reason.neverRun")
        case .notLately: return Text("home.suggestion.reason.notLately \(Int(daysSinceRun))")
        case .variety: return Text("home.suggestion.reason.variety")
        }
    }
}
