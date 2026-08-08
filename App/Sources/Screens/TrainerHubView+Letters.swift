import SwiftUI
import SprossKern

/// LETTERS half of the trainer hub: what the hub can open, whether the
/// alphabet exists at all, and the chip that opens it. State lives on
/// TrainerHubView; split out purely for file size.
///
/// Registry by FILE: a language has an alphabet exactly when
/// `catalog/alphabet/<lang>.json` was authored — adding one is dropping a
/// file, with no Kotlin and no Swift to touch.

/// Everything the hub presents, as ONE item — and every one of them is an
/// overview you READ from, each starting its own run, so one `.sheet(item:)`
/// carries them all. A second `fullScreenCover(isPresented:)` stacked on the
/// same view is not reliably honoured by SwiftUI (the symptom is a chip that
/// does nothing), which is why the hub presents no run itself any more.
enum HubDestination: Identifiable {
    case numbers(language: String)
    case letters(language: String)
    /// The atlas is the one surface named in BOTH languages — a country's name
    /// is a pair, never a property of the language being learned.
    case countries(source: String, target: String)

    var id: String {
        switch self {
        case let .numbers(language): return "numbers-\(language)"
        case let .letters(language): return "letters-\(language)"
        case let .countries(source, target): return "countries-\(source)-\(target)"
        }
    }

    /// The numbers overview's language.
    var numbersLanguage: String? {
        switch self {
        case let .numbers(language): return language
        case .letters, .countries: return nil
        }
    }

    /// The letters overview's language.
    var lettersLanguage: String? {
        switch self {
        case let .letters(language): return language
        case .numbers, .countries: return nil
        }
    }

    /// The atlas overview's pair.
    var countriesPair: (source: String, target: String)? {
        switch self {
        case let .countries(source, target): return (source: source, target: target)
        case .numbers, .letters: return nil
        }
    }
}

extension TrainerHubView {

    // MARK: - Availability

    /// The letters page exists exactly where the file does — the alphabet is
    /// what there is to read, and the drill on the page below it gates itself
    /// on what this device can say.
    var alphabetAvailable: Bool {
        guard let language = drillLanguage else { return false }
        return model.catalog?.alphabet(lang: language) != nil
    }

    // MARK: - The chip

    /// A chip beside the numbers one: the alphabet to read, and the letter drill
    /// started from the same page.
    var lettersChip: some View {
        Button {
            guard let language = drillLanguage else { return }
            destination = .letters(language: language)
        } label: {
            chipLabel(emoji: "🔤", title: Text("trainer.letters"))
        }
        .buttonStyle(TrainerChipButtonStyle())
        .accessibilityLabel(Text("trainer.letters")
            + Text("a11y.practiceSuffix \(languageName(drillLanguage ?? ""))"))
    }
}

#if DEBUG
extension TrainerHubView {
    /// UI-test hook: `-uitest-trainer numbers|letters|countries` resolved
    /// against what this language actually offers.
    ///
    /// Clock, phrases and the alphabet are no longer surfaces of their own:
    /// reach them with `-uitest-trainer numbers -uitest-variants clock
    /// -uitest-run 1` and `-uitest-trainer letters`, which is also the only way
    /// to photograph a modifier or a mixed selection.
    func uitestDestination(_ raw: String) -> HubDestination? {
        if raw == "numbers", slotsAvailable, let language = drillLanguage {
            return .numbers(language: language)
        }
        if raw == "letters", alphabetAvailable, let language = drillLanguage {
            return .letters(language: language)
        }
        if raw == "countries", let pair = atlasPair {
            return .countries(source: pair.source, target: pair.target)
        }
        return nil
    }
}
#endif
