import SwiftUI
import SprossKern

/// Home's header: the date, and the line under it that greets the learner.
extension HomeView {

    // MARK: - Header

    /// The date, and under it what the box is growing. Naming the screen "Home" spent the
    /// biggest type on the page on the one thing a learner who just opened the app already
    /// knows; the language being learned is the one piece of standing the screen never said,
    /// and it is what the day's work is for.
    var header: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.xs) {
            Text(Date().formatted(
                Date.FormatStyle(locale: locale)
                    .weekday(.wide).day().month(.wide)
            ))
            .font(Theme.typography.caption)
            .foregroundStyle(Theme.colors.textSecondary)
            .textCase(.uppercase)
            // A greeting is a phrase, not a headline word: it shrinks a step rather than
            // pushing the day's card down a third line.
            if let greeting {
                greeting
                    .font(Theme.typography.hero)
                    .foregroundStyle(Theme.colors.textPrimary)
                    .lineLimit(2)
                    .minimumScaleFactor(0.7)
            }
        }
    }

    /// The language being learned, as the chrome language calls it; nil before a profile
    /// picks one.
    var targetLanguageName: String? {
        model.targetLanguage.map {
            LanguageNames.display($0, catalog: model.catalog)
        }
    }

    /// "Habari za asubuhi, Tim!", "Tayari kujifunza, Nachteule?", "Ein Feierabend mit
    /// Suaheli?" — two registers for the same line: the language speaking for itself, or the
    /// known language asking about it. Either way the line carries the language, which is
    /// what the header is for.
    ///
    /// Which stretch of the day each register is in, whom the language's own lines address
    /// and which line this one takes are kern's (`GreetingPlan`); the words are the catalog's
    /// and the chrome's. Nil while no profile names a language — the first launch, where this
    /// screen stands behind the onboarding sheet and the header has nothing to greet.
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

    /// The word kern's `Addressee` names. Resolved through `ChromeStrings` against the
    /// profile's known language rather than `String(localized:)`, which would read the
    /// device's — and it has to be a plain String, since it goes inside a sentence the
    /// catalog wrote.
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
