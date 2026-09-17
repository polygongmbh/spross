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

/// What each entry of kern's roster wears here. The six are enumerated in
/// `Drill` and nowhere else; the glyph and the catalog key are this side's,
/// which is why they hang off the roster rather than sitting inside it.
extension Drill {
    var emoji: String {
        switch self {
        // layer-ok: the chip IS the numbers one — reading its own emoji, not picking a reading
        case .numbers: return numbersReadingEmoji(reading: .cardinal)
        case .letters: return "🔤"
        case .countries: return "🌍"
        case .dates: return "📅"
        case .wordScramble: return "🔀"
        case .sentenceScramble: return "🧩"
        }
    }

    var titleKey: LocalizedStringKey {
        switch self {
        case .numbers: return "trainer.drill.numbers"
        case .letters: return "trainer.drill.letters"
        case .countries: return "trainer.drill.countries"
        case .dates: return "trainer.drill.dates"
        case .wordScramble: return "trainer.drill.wordScramble"
        case .sentenceScramble: return "trainer.drill.sentenceScramble"
        }
    }
}

/// Everything the hub presents, as ONE item: the four overviews reach the
/// screen through its sheet and the two runs through its cover
/// (`TrainerHubView.presented`), both off this one state.
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
    /// Putting a phrase's words back in order, in the learned language.
    case sentenceScramble(language: String)

    /// Whether this entry opens a RUN rather than a page to read from. A run is
    /// a full screen with its own ✕ wherever it is started from, the overviews'
    /// covers included (`DrillLaunch`), so the two that skip the page still get
    /// one.
    var isRun: Bool {
        switch self {
        case .wordScramble, .sentenceScramble: return true
        case .numbers, .letters, .countries, .dates: return false
        }
    }

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

    /// Where a roster entry's chip goes, or nil where this profile cannot offer
    /// it at all — the one place a `Drill` meets its condition, so a seventh
    /// cannot reach the hub without one.
    func destination(for drill: Drill) -> HubDestination? {
        guard let language = drillLanguage else { return nil }
        switch drill {
        case .numbers:
            return slotsAvailable ? .numbers(language: language) : nil
        case .letters:
            return alphabetAvailable ? .letters(language: language) : nil
        case .countries:
            return atlasPair.map { HubDestination.countries(source: $0.source, target: $0.target) }
        case .dates:
            return datesPair.map { HubDestination.dates(source: $0.source, target: $0.target) }
        case .wordScramble:
            return wordScrambleAvailable ? .wordScramble(language: language) : nil
        case .sentenceScramble:
            return sentenceScrambleAvailable ? .sentenceScramble(language: language) : nil
        }
    }
}

#if DEBUG
extension Drill {
    /// What `-uitest-trainer` calls this entry: the roster's own name, lowercased.
    var uitestName: String {
        switch self {
        case .numbers: return "numbers"
        case .letters: return "letters"
        case .countries: return "countries"
        case .dates: return "dates"
        case .wordScramble: return "wordscramble"
        case .sentenceScramble: return "sentencescramble"
        }
    }
}

extension TrainerHubView {
    /// UI-test hook: `-uitest-trainer numbers|letters|countries|dates|
    /// wordscramble|sentencescramble` resolved against what this profile
    /// actually offers.
    ///
    /// Clock, phrases and the alphabet are no longer surfaces of their own:
    /// reach them with `-uitest-trainer numbers -uitest-exercises clock
    /// -uitest-run 1` and `-uitest-trainer letters`, which is also the only way
    /// to photograph a modifier or a mixed selection.
    func uitestDestination(_ raw: String) -> HubDestination? {
        Drill.allCases.first { $0.uitestName == raw }.flatMap { destination(for: $0) }
    }
}
#endif
