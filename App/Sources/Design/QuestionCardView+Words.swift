import SprossKern
import SwiftUI

/// What `Question` leaves unworded — the asks, the hints, the closing note — and the sizes a side's form picks.
extension QuestionCardView {

    // MARK: - Sizes by form

    /// What is asked picks the size: there is room for one numeral where there is none for a whole line.
    /// Digits stand at one width, save in a name or a dated line, which reads as text.
    func promptFont(_ form: Question.Form) -> Font {
        switch form {
        case .numeral: return Theme.prompt.digits.monospacedDigit()
        case .sentence: return Theme.prompt.sentence.monospacedDigit()
        case .name: return Theme.prompt.name
        case .glyph: return Theme.prompt.letter.monospacedDigit()
        default: return Theme.prompt.word.monospacedDigit()
        }
    }

    func promptLines(_ form: Question.Form) -> Int {
        switch form {
        case .sentence: return 4
        case .name: return 3
        default: return 1
        }
    }

    /// A reveal under a sentence is set no larger than a line of it.
    var drillAnswerFont: Font {
        question.prompt.form == .sentence || question.answer.form == .sentence
            ? Theme.typography.headline : Theme.typography.title
    }

    /// The prompt as written, the opening letters a mixed word keeps standing set bold —
    /// weight alone, since that anchor is an aid the ladder takes away and not worth a legend.
    func promptText(_ side: Question.Side, _ text: String) -> Text {
        let lead = min(Int(side.fixedLeading), text.count)
        guard lead > 0 else { return Text(verbatim: text) }
        return Text(verbatim: String(text.prefix(lead))).bold() + Text(verbatim: String(text.dropFirst(lead)))
    }

    /// The prompt as VoiceOver reads it, in the voice of the language it is written in —
    /// save a numeral, which read in the learned language would say the answer.
    func promptReading(_ side: Question.Side, _ text: String) -> Text {
        guard side.form != .numeral, let spoken = Text.spoken(text, language: side.lang) else {
            return Text(verbatim: text)
        }
        return spoken
    }

    // MARK: - Chrome words

    func askKey(_ ask: QuestionAsk) -> LocalizedStringKey {
        switch onEnum(of: ask) {
        case .country(let country):
            switch country.kind {
            case .countryName: return "countries.ask.country"
            case .flagCountry: return "countries.ask.flag"
            case .languageName: return "countries.ask.language"
            case .nationality: return "countries.ask.nationality"
            case .spokenIn: return "countries.ask.spokenIn"
            case .spokenWhere: return "countries.ask.spokenWhere"
            }
        case .date(let date):
            switch date.kind {
            case .nameChoice: return "dates.ask.name"
            case .weekday: return "dates.ask.weekday"
            case .month: return "dates.ask.month"
            case .dayAndMonth, .fullDate, .fullDateWithYear: return "dates.ask.date"
            }
        case .letterHear: return "letters.ask.hear"
        case .letterSpell: return "letters.ask.spell"
        case .letterDictation: return "letters.ask.dictation"
        }
    }

    func hintPill(_ hint: QuestionHint) -> DrillHint {
        switch onEnum(of: hint) {
        case .newForm(let form): return .init(icon: "plusminus", text: "numbers.newForm \(form.word)")
        case .newPlace(let place): return .init(icon: "textformat.123", text: "numbers.newPlace \(place.word)")
        case .newWord(let word): return .init(icon: "text.append", text: "dates.newWord \(word.word)")
        }
    }

    /// The card's last line: its own note, or what the prompted form also means.
    var noteText: String? {
        guard let note = question.closing.note else { return nil }
        switch onEnum(of: note) {
        case .own(let own): return own.text
        case .alsoMeans(let also):
            return String(format: ChromeStrings.string("session.means.also %@", locale: locale),
                          also.meanings.joined(separator: " / "))
        }
    }

    /// Which plural is a sentinel is kern's (`PluralForm`); the labels each one wears are chrome.
    func pluralText(_ plural: PluralForm) -> String {
        switch onEnum(of: plural) {
        case .sameAsSingular: return ChromeStrings.string("session.grammar.plural.equals", locale: locale)
        case .pluralOnly: return ChromeStrings.string("session.grammar.plural.only", locale: locale)
        case .form(let form):
            return String(format: ChromeStrings.string("session.grammar.plural %@", locale: locale), form.text)
        }
    }

    /// "also: Amt / Verwaltung" — the word's family beyond the forms already on the card.
    var alternatesText: String? {
        let family = question.closing.alternates
        guard !family.isEmpty else { return nil }
        return String(format: ChromeStrings.string("session.grammar.also %@", locale: locale),
                      family.joined(separator: " / "))
    }
}
