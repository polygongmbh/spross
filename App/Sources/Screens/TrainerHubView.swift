import SwiftUI
import SprossKern

/// Compact "Training" card on the Heute screen: slot drills (Zahlen /
/// Uhrzeit), the sentence drill, and the alphabet of the language being
/// learned. Offerings are registry-driven: slot drills appear only when
/// Kern's trainer supports the learned language, the sentence drill only
/// where the catalog realizes a sentence frame in BOTH languages and the
/// answer language has a trainer pack, the alphabet only where a file was
/// authored — a language missing any of that hides its sections, an empty
/// card hides entirely. Trainers are stateless — they never touch BoxState
/// or FSRS.
struct TrainerHubView: View, LanguageNaming {
    let model: AppModel

    // why: internal, not private — LanguageNaming names the drilled
    // language through it.
    @Environment(\.locale) var locale
    @Environment(\.scenePhase) private var scenePhase

    /// Standalone drills offered on the card — years is intentionally absent
    /// (redundant with numbers), though it still backs phrase slots.
    private static let drillKinds: [TrainerKind] = [.numbers, .clock]

    // why: internal, not private — TrainerHubView+Letters.swift (file-size
    // split) reads the same three, and drives this state from its extension.
    @State var destination: HubDestination?
    /// What the letter drill can ask on THIS device — rebuilt on every
    /// foreground (TrainerHubView+Letters.swift), never decided once.
    @State var letterDrill: LetterDrillAvailability?

    /// The language being learned — every drill runs in it.
    var drillLanguage: String? { model.targetLanguage }

    private var slotsAvailable: Bool {
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
            if slotsAvailable || phraseDrill != nil || alphabetAvailable {
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
            Group {
                if let mode = destination.drillMode {
                    TrainerSessionView(mode: mode, normalizer: normalizer(for: mode),
                                       catalog: model.catalog, model: model)
                } else if let language = destination.lettersLanguage {
                    LetterDrillView(model: model, language: language)
                }
            }
            .environment(\.locale, model.knownLocale)
        }
        .sheet(item: sheetDestination) { destination in
            if let language = destination.sheetLanguage {
                AlphabetSheetView(model: model, language: language)
                    .environment(\.locale, model.knownLocale)
            }
        }
    }

    private func normalizer(for mode: TrainerSessionView.Mode) -> AnswerNormalizer? {
        // why: drills grade word by word — no article forgiveness, one slip per
        // word, digits exact-only — so a sentence may fumble one word while no
        // number can ever pass for another.
        model.languageInfo(mode.language)
            .map { AnswerNormalizer(answerLanguage: $0, articleLeniency: false,
                                    maxTyposPerWord: KotlinInt(int: 1)) }
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
            if slotsAvailable || phraseDrill != nil || letterDrillAvailable {
                HStack(spacing: DL.Space.m) {
                    // why: years drill dropped — it's covered by the numbers
                    // drill (identical reading in Swahili/Ukrainian). Years
                    // live on only as a phrase slot.
                    if slotsAvailable {
                        ForEach(Self.drillKinds, id: \.self) { kind in
                            drillChip(kind)
                        }
                    }
                    if phraseDrill != nil {
                        phraseChip
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
        // UI-test hook: `-uitest-trainer numbers|years|clock|phrases|alphabet`
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

    private func drillChip(_ kind: TrainerKind) -> some View {
        Button {
            guard let language = drillLanguage else { return }
            destination = .slots(kind: kind, language: language)
        } label: {
            chipLabel(emoji: kind.trainerEmoji, title: Text(kind.trainerTitleKey))
        }
        .buttonStyle(TrainerChipButtonStyle())
        .accessibilityLabel(Text(kind.trainerTitleKey)
            + Text("a11y.practiceSuffix \(languageName(drillLanguage ?? ""))"))
    }

    /// Sentence drill: composes phrase templates with slot values.
    private var phraseChip: some View {
        Button {
            guard let drill = phraseDrill else { return }
            destination = .phrases(source: drill.source, target: drill.target,
                                   templates: drill.templates)
        } label: {
            chipLabel(emoji: "💬", title: Text("trainer.phrases"))
        }
        .buttonStyle(TrainerChipButtonStyle())
        .accessibilityLabel("a11y.practicePhrases")
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
