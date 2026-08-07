import SwiftUI
import SprossKern

/// Compact "Training" card on the Heute screen: TWO entries — 🔢 Zahlen and
/// 🔤 Buchstaben — plus the alphabet to read. Clock and sentences are not
/// siblings of the numbers drill but variants of it, so they live as rows
/// inside the numbers overview and are earned there; the chip row only has to
/// name the two things a learner can practise. Offerings stay registry-driven:
/// numbers appears only when Kern's trainer supports the learned language, the
/// letter drill only where this device can sound a letter, the alphabet only
/// where a file was authored — an empty card hides entirely. Trainers are
/// stateless: they never touch BoxState or FSRS.
struct TrainerHubView: View, LanguageNaming {
    let model: AppModel

    // why: internal, not private — LanguageNaming names the drilled
    // language through it.
    @Environment(\.locale) var locale
    @Environment(\.scenePhase) private var scenePhase

    // why: internal, not private — TrainerHubView+Letters.swift (file-size
    // split) reads the same three, and drives this state from its extension.
    @State var destination: HubDestination?
    /// What the letter drill can ask on THIS device — rebuilt on every
    /// foreground (TrainerHubView+Letters.swift), never decided once.
    @State var letterDrill: LetterDrillAvailability?

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

    var body: some View {
        Group {
            if slotsAvailable || alphabetAvailable {
                card
            }
        }
        .onAppear { refreshLetterDrill() }
        // why: a voice installed in Settings while the app slept must bring
        // the chip back on the next foreground, not on the next launch. On
        // becoming ACTIVE, not on willEnterForeground: the speaker drops its
        // cached voice table on that notification, and this has to read the
        // table after it was dropped rather than the stale one.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { refreshLetterDrill() }
        }
        .fullScreenCover(item: drillDestination) { destination in
            if let language = destination.lettersLanguage {
                LetterDrillView(model: model, language: language)
                    .environment(\.locale, model.knownLocale)
            }
        }
        .sheet(item: sheetDestination) { destination in
            Group {
                if let language = destination.numbersLanguage {
                    NumbersOverview(model: model, language: language,
                                    phraseDrill: phraseDrill.map { ($0.source, $0.templates) })
                } else if let language = destination.alphabetLanguage {
                    AlphabetSheetView(model: model, language: language)
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
            // why: gated as a whole — a language with an alphabet and no drills
            // (the moment such a file lands) would otherwise open an empty row
            // of chips above the Alphabet row.
            if slotsAvailable || letterDrillAvailable {
                HStack(spacing: DL.Space.m) {
                    if slotsAvailable {
                        numbersChip
                    }
                    if letterDrillAvailable {
                        lettersChip
                    }
                }
            }
            if alphabetAvailable {
                alphabetRow
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
        // UI-test hook: `-uitest-trainer numbers|letters|alphabet`
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
        .frame(maxWidth: .infinity, minHeight: 72)
        .padding(.vertical, DL.Space.s)
        .padding(.horizontal, DL.Space.xs)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                .fill(Color.dlSurfaceTint)
        )
    }
}

/// Pressed-state feedback for the hub's chips and rows (mirrors DL button
/// springs). Internal: the Alphabet row is a TrainerHubView+Letters.swift one.
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
    var trainerEmoji: String {
        switch self {
        case .numbers: return "🔢"
        case .years: return "📅"
        case .clock: return "🕐"
        case .forms: return "➗"
        }
    }

    /// Catalog key for the drill title.
    var trainerTitleKey: LocalizedStringKey {
        switch self {
        case .numbers: return "trainer.numbers"
        case .years: return "trainer.years"
        case .clock: return "trainer.clock"
        case .forms: return "trainer.forms"
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
