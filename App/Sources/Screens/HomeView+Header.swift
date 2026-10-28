import SwiftUI
import SprossKern

/// Home's header: the line that greets the learner.
extension HomeView {

    // MARK: - Header

    /// A greeting that names the language being learned; a long one shrinks rather than taking a third line.
    @ViewBuilder
    var header: some View {
        if let greeting {
            greeting
                .font(Theme.typography.hero)
                .foregroundStyle(Theme.colors.textPrimary)
                .lineLimit(2)
                .minimumScaleFactor(0.7)
        }
    }

    /// The language being learned, as the chrome language calls it; nil before a profile
    /// picks one.
    var targetLanguageName: String? {
        model.targetLanguage.map {
            LanguageNames.display($0, catalog: model.catalog)
        }
    }

    /// "Habari za asubuhi, Tim!" or "Feierabend auf Suaheli?":
    /// a line in the language being learned, or one in the known language about it.
    /// The register, the addressee and the line are kern's (`GreetingPlan`);
    /// the words are the catalog's and the chrome's.
    /// Nil while no profile names a language.
    var greeting: Text? {
        guard let language = targetLanguageName else { return nil }
        let target = model.targetLanguage
        let plan = GreetingPlan(nowEpochMillis: Date().epochMillis, tzId: currentTzId(),
                                target: target, learnerNamed: model.learnerName != nil)
        let spoken = target.flatMap {
            model.catalog?.spokenLines(lang: $0, part: plan.targetPart, name: address(plan.address))
        } ?? []
        let chrome = chromeLines(plan.chromePart, language: language)
        let line = plan.pick(spoken: Int32(spoken.count), chrome: Int32(chrome.count))
        return line.spoken ? Text(verbatim: spoken[Int(line.index)]) : chrome[Int(line.index)]
    }

    /// The known language's own lines for the chrome's stretch of the day.
    private func chromeLines(_ part: DayPart, language: String) -> [Text] {
        switch part {
        case .morning:
            return [Text("home.greeting.morning.0 \(language)"),
                    Text("home.greeting.morning.1 \(language)"),
                    Text("home.greeting.morning.epithet \(language)")]
        case .day:
            return [Text("home.greeting.day.0 \(language)"),
                    Text("home.greeting.day.1 \(language)")]
        case .evening:
            return [Text("home.greeting.evening.0 \(language)"),
                    Text("home.greeting.evening.1 \(language)")]
        default:
            return [Text("home.greeting.night.0 \(language)"),
                    Text("home.greeting.night.1 \(language)"),
                    Text("home.greeting.night.epithet \(language)")]
        }
    }

    /// The word kern's `Addressee` names, a plain String for a sentence the catalog wrote.
    /// `ChromeStrings` reads the profile's known language; `String(localized:)` would read the device's.
    private func address(_ addressee: Addressee) -> String? {
        switch addressee {
        case .learner: return model.learnerName
        case .morningWord:
            return ChromeStrings.string("home.greeting.morning.addressee", locale: model.knownLocale)
        case .nightWord:
            return ChromeStrings.string("home.greeting.night.addressee", locale: model.knownLocale)
        default: return nil
        }
    }
}
