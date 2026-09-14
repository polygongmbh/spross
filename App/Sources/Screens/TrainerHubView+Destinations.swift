import SwiftUI
import SprossKern

/// What the trainer hub can OPEN, and the one availability question a file
/// answers: whether the learned language has an alphabet at all. The chips
/// themselves are TrainerHubView's; this is split out purely for file size.
///
/// Registry by FILE: a language has an alphabet exactly when
/// `catalog/alphabet/<lang>.json` was authored — adding one is dropping a
/// file, with no Kotlin and no Swift to touch.

/// One entry on the hub card: its face, its name and where it goes. A value
/// rather than a view, because the card counts its entries before it lays them
/// out (`TrainerHubView.chipRows`).
struct HubChip: Identifiable {
    let emoji: String
    let title: LocalizedStringKey
    let destination: HubDestination

    var id: String { destination.id }
}

/// Everything the hub presents, as ONE item, so a single `.sheet(item:)`
/// carries them all. A second `fullScreenCover(isPresented:)` stacked on the
/// same view is not reliably honored by SwiftUI (the symptom is a chip that
/// does nothing), which is why the hub presents no run outside this sheet.
///
/// The four overviews are pages you READ from, each starting its own run; the
/// two scrambles have nothing to read beside them — the box IS their material —
/// so their entries open the run itself.
enum HubDestination: Identifiable {
    case numbers(language: String)
    case letters(language: String)
    /// The atlas is the one surface named in BOTH languages — a country's name
    /// is a pair, never a property of the language being learned.
    case countries(source: String, target: String)
    /// The calendars are a pair too: the prompt side lends its weekday
    /// abbreviations and its digit format, the answer side spells the date out.
    case dates(source: String, target: String)
    /// Spelling a word back out of its own letters, in the learned language.
    case wordScramble(language: String)
    /// Putting an unlocked phrase's words back in order, in the learned language.
    case sentenceScramble(language: String)

    var id: String {
        switch self {
        case let .numbers(language): return "numbers-\(language)"
        case let .letters(language): return "letters-\(language)"
        case let .countries(source, target): return "countries-\(source)-\(target)"
        case let .dates(source, target): return "dates-\(source)-\(target)"
        case let .wordScramble(language): return "wordscramble-\(language)"
        case let .sentenceScramble(language): return "sentencescramble-\(language)"
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
}

#if DEBUG
extension TrainerHubView {
    /// UI-test hook: `-uitest-trainer numbers|letters|countries|dates|
    /// wordscramble|sentencescramble` resolved against what this profile
    /// actually offers.
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
        if raw == "dates", let pair = datesPair {
            return .dates(source: pair.source, target: pair.target)
        }
        if raw == "wordscramble", wordScrambleAvailable, let language = drillLanguage {
            return .wordScramble(language: language)
        }
        if raw == "sentencescramble", sentenceScrambleAvailable, let language = drillLanguage {
            return .sentenceScramble(language: language)
        }
        return nil
    }
}
#endif
