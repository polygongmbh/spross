import SwiftUI
import SprossKern

/// Heute's header: the date, and the line under it that greets the learner.
extension HeuteView {

    // MARK: - Header

    /// The date, and under it what the box is growing. Naming the screen "Heute" spent the
    /// biggest type on the page on the one thing a learner who just opened the app already
    /// knows; the language being learned is the one piece of standing the screen never said,
    /// and it is what the day's work is for.
    var header: some View {
        VStack(alignment: .leading, spacing: DL.Space.xs) {
            Text(Date().formatted(
                Date.FormatStyle(locale: locale)
                    .weekday(.wide).day().month(.wide)
            ))
            .font(DL.Fonts.caption)
            .foregroundStyle(Color.dlTextSecondary)
            .textCase(.uppercase)
            // A greeting is a phrase, not a headline word: it shrinks a step rather than
            // pushing the day's card down a third line.
            greeting
                .font(DL.Fonts.hero)
                .foregroundStyle(Color.dlTextPrimary)
                .lineLimit(2)
                .minimumScaleFactor(0.7)
        }
    }

    /// The language being learned, as the chrome language calls it; nil before a profile
    /// picks one.
    var targetLanguageName: String? {
        model.targetLanguage.map {
            LanguageNames.display($0, locale: locale, catalog: model.catalog)
        }
    }

    /// "Habari za asubuhi, Tim!", "Tayari kujifunza, Nachteule?", "Ein Feierabend mit
    /// Suaheli?" — two registers for the same line: the language speaking for itself, or the
    /// known language asking about it. Either way the line carries the language, which is
    /// what the header is for.
    ///
    /// Which stretch of the day it is and which of the candidates this one takes are kern's
    /// (`dayPart`, `partVariant`); the words are the catalog's and the chrome's. Falls back
    /// to the screen's own name while no profile names a language.
    var greeting: Text {
        guard let language = targetLanguageName else { return Text("heute.title") }
        let now = Date().epochMillis, tz = currentTzId()
        // The language's own hours: four in the afternoon is still Tag in German and
        // already jioni in Swahili (kern's `dayPart`).
        let target = model.targetLanguage
        let part = dayPart(nowEpochMillis: now, tzId: tz, language: target)
        let lines = greetingLines(part, language: language, target: target)
        let pick = partVariant(nowEpochMillis: now, tzId: tz,
                               language: target, count: Int32(lines.count))
        return lines[Int(pick)]
    }

    /// Everything the app could say right now, the language's own lines first — they both
    /// greet and teach, and they are the only ones that can address the learner: by name, or
    /// by the word the hour lends when no name is known.
    private func greetingLines(_ part: DayPart, language: String, target: String?) -> [Text] {
        var lines: [Text] = []
        if let target {
            let spoken = model.catalog?.spokenLines(lang: target, part: part,
                                                    name: model.learnerName ?? addressee(part))
            lines += (spoken ?? []).map { Text(verbatim: $0) }
        }
        switch part {
        case .morning:
            lines += [Text("heute.greeting.morning.0 \(language)"),
                      Text("heute.greeting.morning.1 \(language)"),
                      Text("heute.greeting.morning.epithet \(language)")]
        case .day:
            lines += [Text("heute.greeting.day.0 \(language)"),
                      Text("heute.greeting.day.1 \(language)")]
        case .evening:
            lines += [Text("heute.greeting.evening.0 \(language)"),
                      Text("heute.greeting.evening.1 \(language)")]
        case .night:
            lines += [Text("heute.greeting.night.0 \(language)"),
                      Text("heute.greeting.night.1 \(language)"),
                      Text("heute.greeting.night.epithet \(language)")]
        }
        return lines
    }

    /// The word the hour lends as an address. Resolved through `DLChrome` against the
    /// profile's known language rather than `String(localized:)`, which would read the
    /// device's — and it has to be a plain String, since it goes inside a sentence the
    /// catalog wrote.
    private func addressee(_ part: DayPart) -> String? {
        switch part {
        case .morning:
            return DLChrome.string("heute.greeting.morning.addressee", locale: model.knownLocale)
        case .night:
            return DLChrome.string("heute.greeting.night.addressee", locale: model.knownLocale)
        case .day, .evening:
            return nil
        }
    }
}
