import SwiftUI
import SprossKern

/// Compact "Wiese" card on the Home screen: 🔢 Numbers, 🔤 Letters,
/// 🌍 Countries, 📅 Dates, 🔀 the word scramble and 🧩 the sentence one. The
/// first four open an overview — what the language does with numbers or
/// letters, what the world is called in it, how it says a date — with the run
/// started from the same page; the two scrambles have nothing to read beside
/// them and open their run directly. Offerings stay registry-driven: numbers
/// appears only when Kern's trainer supports the learned language, letters only
/// where an alphabet file was authored, the atlas and the calendar only where
/// the pair joins one, and each scramble only where the BOX holds enough to ask
/// — an empty card hides entirely. Trainers are stateless: they never touch
/// BoxState or FSRS.
struct TrainerHubView: View, LanguageNaming {
    let model: AppModel

    // why: internal, not private — LanguageNaming names the drilled
    // language through it.
    @Environment(\.locale) var locale

    // why: internal, not private — TrainerHubView+Destinations.swift (file-size
    // split) drives this state from its extension.
    @State var destination: HubDestination?

    /// The language being learned — every drill runs in it.
    var drillLanguage: String? { model.targetLanguage }

    // why: internal, not private — the UI-test hook in
    // TrainerHubView+Destinations.swift gates the numbers overview on it.
    var slotsAvailable: Bool {
        drillLanguage.map { Numbers.shared.supports(language: $0) } ?? false
    }

    /// The sentence drill this profile can run: the frames the catalog joins
    /// for the pair, always asked known-language prompt → learned-language answer.
    var phraseDrill: (source: String, target: String, templates: [PhraseTemplate])? {
        guard let target = drillLanguage else { return nil }
        let templates = model.phraseTemplatesForPair
        return templates.isEmpty ? nil : (source: model.sourceLanguage, target: target,
                                          templates: templates)
    }

    /// The pair whose atlas this profile can drill, or nil where the catalog
    /// joins none — registry by FILE, exactly as the alphabet's is. Kern is the
    /// only judge of that, so nothing here counts countries.
    var atlasPair: (source: String, target: String)? {
        guard model.atlasJoinsPair, let target = drillLanguage else { return nil }
        return (source: model.sourceLanguage, target: target)
    }

    var atlasAvailable: Bool { atlasPair != nil }

    /// The pair whose calendars this profile can drill, or nil where the
    /// catalog joins none — the atlas' registry rule, on the dates files.
    var datesPair: (source: String, target: String)? {
        guard model.datesJoinPair, let target = drillLanguage else { return nil }
        return (source: model.sourceLanguage, target: target)
    }

    var datesAvailable: Bool { datesPair != nil }

    /// Whether the box holds enough consolidated single words to be worth
    /// mixing. Kern's own floor, read — this side counts nothing.
    var wordScrambleAvailable: Bool { WordScrambleAvailability(model: model).drillAvailable }

    /// Whether the box has unlocked enough long-enough phrases to arrange.
    var sentenceScrambleAvailable: Bool {
        SentenceScrambleAvailability(model: model).drillAvailable
    }

    var body: some View {
        Group {
            if !chips.isEmpty {
                card
            }
        }
        .sheet(item: $destination) { destination in
            Group {
                switch destination {
                case let .numbers(language):
                    NumbersOverview(model: model, language: language,
                                    phraseDrill: phraseDrill.map { ($0.source, $0.templates) })
                case let .letters(language):
                    LettersOverview(model: model, language: language)
                case let .countries(source, target):
                    CountriesOverview(model: model, source: source, target: target)
                case let .dates(source, target):
                    DatesOverview(model: model, source: source, target: target)
                case let .wordScramble(language):
                    WordScrambleView(model: model, language: language)
                case let .sentenceScramble(language):
                    SentenceScrambleView(model: model, language: language)
                }
            }
            .environment(\.locale, model.knownLocale)
        }
    }

    // MARK: - Card

    private var card: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.lg) {
            Text("trainer.hub.title")
                .font(Theme.typography.title)
                .foregroundStyle(Theme.colors.textPrimary)
            Text("trainer.hub.subtitle")
                .font(Theme.typography.subheadline)
                .foregroundStyle(Theme.colors.textSecondary)
            VStack(spacing: Theme.spacing.md) {
                ForEach(Array(chipRows.enumerated()), id: \.offset) { _, row in
                    HStack(spacing: Theme.spacing.md) {
                        ForEach(row) { chip(for: $0) }
                    }
                }
            }
        }
        .padding(Theme.spacing.xl)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: Theme.radius.card, style: .continuous)
                .fill(Theme.colors.surface)
        )
        .cardShadow()
        #if DEBUG
        // UI-test hook: `-uitest-trainer numbers|letters|countries|dates|
        // wordscramble|sentencescramble` opens that surface (in the learned
        // language, like the chips).
        // Attached HERE because the card only appears once the box is loaded;
        // resolved in TrainerHubView+Destinations.swift.
        .onAppear {
            guard destination == nil,
                  let raw = UserDefaults.standard.string(forKey: "uitest-trainer") else { return }
            destination = uitestDestination(raw)
        }
        #endif
    }

    var namingCatalog: Catalog? { model.catalog }

    // MARK: - The entries, and how many lines they take

    /// Every entry this profile can reach, in the order the card offers them —
    /// kern's `Drill` roster, cut down to what this profile can open
    /// (`destination(for:)`). A VALUE per chip rather than a view apiece,
    /// because the row has to COUNT them before it can decide how many lines
    /// it needs.
    var chips: [HubChip] {
        Drill.allCases.compactMap { drill in
            destination(for: drill).map {
                HubChip(emoji: drill.emoji, title: drill.titleKey, destination: $0)
            }
        }
    }

    /// The chips cut into lines. Three or fewer stand on one; past that the card
    /// breaks into TWO, `ceil(n/2)` above and `floor(n/2)` below — 4 stand 2+2,
    /// 5 stand 3+2, 6 stand 3+3 — and each line keeps the equal-width chips one
    /// line carries on its own. The break is DRAWN rather than discovered: an
    /// HStack overflows rather than wrapping, and six chips sharing one row
    /// would be six slivers of a word apiece.
    private var chipRows: [[HubChip]] {
        let chips = chips
        guard chips.count > 3 else { return chips.isEmpty ? [] : [chips] }
        let top = (chips.count + 1) / 2
        return [Array(chips.prefix(top)), Array(chips.dropFirst(top))]
    }

    private func chip(for chip: HubChip) -> some View {
        Button {
            destination = chip.destination
        } label: {
            chipLabel(emoji: chip.emoji, title: Text(chip.title))
        }
        .buttonStyle(ChipButtonStyle())
        .accessibilityLabel(Text(chip.title)
            + Text("a11y.suffix.practice \(languageName(drillLanguage ?? ""))"))
    }

    /// One chip's face.
    func chipLabel(emoji: String, title: Text) -> some View {
        VStack(spacing: Theme.spacing.sm) {
            Text(emoji)
                .font(.system(size: 30))
                .accessibilityHidden(true)
            title
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
        }
        .frame(maxWidth: .infinity, minHeight: Theme.reserve.tile)
        .padding(.vertical, Theme.spacing.sm)
        .padding(.horizontal, Theme.spacing.xs)
        .background(
            RoundedRectangle(cornerRadius: Theme.radius.tile, style: .continuous)
                .fill(Theme.colors.surfaceTint)
        )
    }
}

/// Pressed-state feedback for every chip-shaped control (mirrors the shared button springs):
/// the hub's chips, a drill's choice tiles, a scramble's tile bank and the listening row.
/// Internal: the letters chip is a TrainerHubView+Destinations.swift one.
struct ChipButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.7 : 1)
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

// MARK: - Shared display names

extension NumbersReading {
    /// Catalog key for the drill title.
    var trainerTitleKey: LocalizedStringKey {
        switch self {
        case .cardinal: return "trainer.drill.numbers"
        case .year: return "trainer.drill.numbers.exercise.years"
        case .clock: return "trainer.drill.numbers.exercise.clock"
        case .form, .fraction: return "trainer.drill.numbers.exercise.forms"
        }
    }
}

/// What an EXERCISE is called, wherever one has to be named on its own — the
/// score line of a mixed run today, the overview's rows next. Numbers, Clock and
/// Forms deliberately borrow the slot kind's title: they are the same exercise.
/// The matching FACE is kern's `numbersExerciseEmoji` — one glyph table, not two.
extension NumbersExercise {
    var trainerTitleKey: LocalizedStringKey {
        switch self {
        case .phrases: return "trainer.drill.numbers.exercise.phrases"
        case .counting, .clock, .forms: return reading?.trainerTitleKey ?? "trainer.drill.numbers"
        }
    }
}

/// How a run is PLAYED, as the overview offers it. A modifier has no face of its
/// own: it changes every exercise alike, so it is named and explained in words.
extension DrillModifier {
    var trainerTitleKey: LocalizedStringKey {
        switch self {
        case .reverse: return "trainer.modifier.reverse"
        case .fast: return "trainer.modifier.fast"
        case .mix: return "trainer.modifier.mix"
        }
    }

    /// One line saying what it does to a run — the settings-row caption pattern.
    var trainerHintKey: LocalizedStringKey {
        switch self {
        case .reverse: return "trainer.modifier.reverse.hint"
        case .fast:
            return "trainer.modifier.fast.hint \(Int(Numbers.shared.winsToAdvance(fast: false)))"
        case .mix: return "trainer.modifier.mix.hint"
        }
    }
}

/// Resolves a catalog key against a specific UI-language bundle. Needed for
/// runtime strings interpolated as `%@` arguments (e.g. a language name),
/// where SwiftUI's environment locale — which only drives `Text` /
/// `LocalizedStringKey` — can't reach.
enum ChromeStrings {
    static func string(_ key: String, locale: Locale) -> String {
        let code = locale.language.languageCode?.identifier ?? "de"
        guard let path = Bundle.main.path(forResource: code, ofType: "lproj"),
              let bundle = Bundle(path: path) else { return key }
        return bundle.localizedString(forKey: key, value: key, table: nil)
    }
}
