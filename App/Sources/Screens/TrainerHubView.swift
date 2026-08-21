import SwiftUI
import SprossKern

/// Compact "Sprossen" card on the Heute screen: 🔢 Zahlen, 🔤 Buchstaben and
/// 🌍 Länder. Each opens an overview: what the language does with numbers or
/// letters, or what the world is called in it, and the run started from the same
/// page. Clock and sentences are not siblings of the numbers drill but variants
/// of it, and the alphabet is not a sibling of the letter drill but the page it
/// is launched from, so the chips only have to name the things a learner can
/// practice. Offerings stay registry-driven: numbers appears only when Kern's
/// trainer supports the learned language, letters only where an alphabet file
/// was authored, and the atlas only where the pair joins one — an empty card
/// hides entirely. Trainers are stateless: they never touch BoxState or FSRS.
struct TrainerHubView: View, LanguageNaming {
    let model: AppModel

    // why: internal, not private — LanguageNaming names the drilled
    // language through it.
    @Environment(\.locale) var locale

    // why: internal, not private — TrainerHubView+Letters.swift (file-size
    // split) drives this state from its extension.
    @State var destination: HubDestination?

    /// The language being learned — every drill runs in it.
    var drillLanguage: String? { model.targetLanguage }

    // why: internal, not private — the UI-test hook in
    // TrainerHubView+Letters.swift gates the numbers overview on it.
    var slotsAvailable: Bool {
        drillLanguage.map { Trainer.shared.supports(language: $0) } ?? false
    }

    /// The sentence drill this profile can run: the frames the catalog joins
    /// for the pair, always asked known-language prompt → learned-language answer.
    var phraseDrill: (source: String, target: String, templates: [PhraseTemplate])? {
        guard let catalog = model.catalog, let target = drillLanguage else { return nil }
        let source = model.sourceLanguage
        // why: Kern throws on an unknown or self-paired language rather than
        // returning empty, and a Kotlin throw crossing back is a crash.
        guard source != target,
              catalog.languages[source] != nil,
              catalog.languages[target] != nil else { return nil }
        let templates = catalog.phraseTemplates(source: source, target: target)
        return templates.isEmpty ? nil : (source: source, target: target, templates: templates)
    }

    /// The pair whose atlas this profile can drill, or nil where the catalog
    /// joins none — registry by FILE, exactly as the alphabet's is. Kern is the
    /// only judge of that, so nothing here counts countries.
    var atlasPair: (source: String, target: String)? {
        guard let catalog = model.catalog, let target = drillLanguage else { return nil }
        let source = model.sourceLanguage
        // why: kern REQUIRES a real pair and a Kotlin throw crossing back is a
        // crash — the same guard the phrase drill takes.
        guard source != target,
              catalog.countryDrillContent(source: source, target: target) != nil else { return nil }
        return (source: source, target: target)
    }

    var atlasAvailable: Bool { atlasPair != nil }

    var body: some View {
        Group {
            if slotsAvailable || alphabetAvailable || atlasAvailable {
                card
            }
        }
        .sheet(item: $destination) { destination in
            Group {
                if let language = destination.numbersLanguage {
                    NumbersOverview(model: model, language: language,
                                    phraseDrill: phraseDrill.map { ($0.source, $0.templates) })
                } else if let language = destination.lettersLanguage {
                    LettersOverview(model: model, language: language)
                } else if let pair = destination.countriesPair {
                    CountriesOverview(model: model, source: pair.source, target: pair.target)
                }
            }
            .environment(\.locale, model.knownLocale)
        }
    }

    // MARK: - Card

    private var card: some View {
        VStack(alignment: .leading, spacing: DL.Space.l) {
            Text("trainer.title")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
            Text("trainer.subtitle")
                .font(DL.Fonts.subheadline)
                .foregroundStyle(Color.dlTextSecondary)
            // ONE row: three chips sit on it comfortably on every device, and a
            // grid that wrapped the third onto a line of its own would spend a
            // whole row saying what fits beside its siblings.
            HStack(spacing: DL.Space.m) {
                if slotsAvailable {
                    numbersChip
                }
                if alphabetAvailable {
                    lettersChip
                }
                if atlasAvailable {
                    countriesChip
                }
            }
        }
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.card, style: .continuous)
                .fill(Color.dlSurface)
        )
        .dlCardShadow()
        #if DEBUG
        // UI-test hook: `-uitest-trainer numbers|letters`
        // opens that surface (in the learned language, like the chips).
        // Attached HERE because the card only appears once the box is loaded;
        // resolved in TrainerHubView+Letters.swift.
        .onAppear {
            guard destination == nil,
                  let raw = UserDefaults.standard.string(forKey: "uitest-trainer") else { return }
            destination = uitestDestination(raw)
        }
        #endif
    }

    var namingCatalog: Catalog? { model.catalog }

    /// The whole numbers progression behind one chip: the reference page, the
    /// clock and the sentences, and whatever the ladder has opened so far.
    private var numbersChip: some View {
        Button {
            guard let language = drillLanguage else { return }
            destination = .numbers(language: language)
        } label: {
            chipLabel(emoji: TrainerKind.numbers.trainerEmoji, title: Text("trainer.numbers"))
        }
        .buttonStyle(TrainerChipButtonStyle())
        .accessibilityLabel(Text("trainer.numbers")
            + Text("a11y.practiceSuffix \(languageName(drillLanguage ?? ""))"))
    }

    /// The atlas: the countries of the two languages first, then the world
    /// outward — read on the page, drilled from it.
    private var countriesChip: some View {
        Button {
            guard let pair = atlasPair else { return }
            destination = .countries(source: pair.source, target: pair.target)
        } label: {
            chipLabel(emoji: "🌍", title: Text("trainer.countries"))
        }
        .buttonStyle(TrainerChipButtonStyle())
        .accessibilityLabel(Text("trainer.countries")
            + Text("a11y.practiceSuffix \(languageName(drillLanguage ?? ""))"))
    }

    /// One chip's face — shared with the letters chip next door.
    func chipLabel(emoji: String, title: Text) -> some View {
        VStack(spacing: DL.Space.s) {
            Text(emoji)
                .font(.system(size: 30))
                .accessibilityHidden(true)
            title
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
        }
        .frame(maxWidth: .infinity, minHeight: DL.Reserve.tile)
        .padding(.vertical, DL.Space.s)
        .padding(.horizontal, DL.Space.xs)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                .fill(Color.dlSurfaceTint)
        )
    }
}

/// Pressed-state feedback for the hub's chips (mirrors DL button springs).
/// Internal: the letters chip is a TrainerHubView+Letters.swift one, and the
/// letter drill's answer tiles borrow the same press.
struct TrainerChipButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.7 : 1)
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

// MARK: - Shared display names

extension TrainerKind {
    /// A fraction wears the Forms face: it is one of the number forms, and it is only
    /// ever met inside a sentence, so it never names a drill of its own.
    var trainerEmoji: String {
        switch self {
        case .numbers: return "🔢"
        case .years: return "📅"
        case .clock: return "🕐"
        case .forms, .fraction: return "➗"
        }
    }

    /// Catalog key for the drill title.
    var trainerTitleKey: LocalizedStringKey {
        switch self {
        case .numbers: return "trainer.numbers"
        case .years: return "trainer.years"
        case .clock: return "trainer.clock"
        case .forms, .fraction: return "trainer.forms"
        }
    }
}

/// What a RUN variant is called, wherever one has to be named on its own — the
/// score line of a mixed run today, the overview's rows next. Numbers, Clock and
/// Forms deliberately borrow the slot kind's face: they are the same exercise.
extension DrillVariant {
    var trainerEmoji: String {
        switch self {
        case .phrases: return "💬"
        case .numbers, .clock, .forms: return slotKind?.trainerEmoji ?? "🔢"
        }
    }

    var trainerTitleKey: LocalizedStringKey {
        switch self {
        case .phrases: return "trainer.phrases"
        case .numbers, .clock, .forms: return slotKind?.trainerTitleKey ?? "trainer.numbers"
        }
    }
}

/// How a run is PLAYED, as the overview offers it. A modifier has no face of its
/// own: it changes every variant alike, so it is named and explained in words.
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
        case .fast: return "trainer.modifier.fast.hint"
        case .mix: return "trainer.modifier.mix.hint"
        }
    }
}

/// Resolves a catalog key against a specific UI-language bundle. Needed for
/// runtime strings interpolated as `%@` arguments (e.g. a language name),
/// where SwiftUI's environment locale — which only drives `Text` /
/// `LocalizedStringKey` — can't reach.
enum DLChrome {
    static func string(_ key: String, locale: Locale) -> String {
        let code = locale.language.languageCode?.identifier ?? "de"
        guard let path = Bundle.main.path(forResource: code, ofType: "lproj"),
              let bundle = Bundle(path: path) else { return key }
        return bundle.localizedString(forKey: key, value: key, table: nil)
    }
}
