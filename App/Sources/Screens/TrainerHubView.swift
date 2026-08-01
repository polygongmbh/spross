import SwiftUI
import SprossKern

/// Compact "Training" card on the Heute screen: slot drills (Zahlen /
/// Uhrzeit), the sentence drill, and the alphabet of the language being
/// learned. Offerings are registry-driven: slot drills appear only when
/// Kern's trainer supports the learned language, the sentence drill only when
/// (source, target) templates exist, the alphabet only where a file was
/// authored — an unauthored language (e.g. en) hides its sections, an empty
/// card hides entirely. Trainers are stateless — they never touch BoxState
/// or FSRS.
struct TrainerHubView: View {
    let model: AppModel

    @Environment(\.locale) private var locale

    /// Standalone drills offered on the card — years is intentionally absent
    /// (redundant with numbers), though it still backs phrase slots.
    private static let drillKinds: [TrainerKind] = [.numbers, .clock]

    // why: internal, not private — TrainerHubView+Letters.swift (file-size
    // split) reads the same three, and drives this state from its extension.
    @State var destination: HubDestination?

    /// The language being learned — every drill runs in it.
    var drillLanguage: String? { model.targetLanguage }

    private var slotsAvailable: Bool {
        drillLanguage.map { Trainer.shared.supports(language: $0) } ?? false
    }

    /// Sentence templates are keyed (source, target); learners OF German get
    /// the reverse drill over the (de, source) templates.
    var phraseKey: (source: String, target: String, reverse: Bool)? {
        guard let target = drillLanguage else { return nil }
        let key = target == "de"
            ? (source: "de", target: model.sourceLanguage, reverse: true)
            : (source: model.sourceLanguage, target: target, reverse: false)
        let templates = PhraseTemplates.shared.templates(source: key.source, target: key.target)
        return templates.isEmpty ? nil : key
    }

    var body: some View {
        Group {
            if slotsAvailable || phraseKey != nil || alphabetAvailable {
                card
            }
        }
        .fullScreenCover(item: drillDestination) { destination in
            if let mode = destination.drillMode {
                TrainerSessionView(mode: mode, normalizer: normalizer(for: mode))
                    .environment(\.locale, model.knownLocale)
            }
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
        model.languageInfo(mode.typedLanguage)
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
            if slotsAvailable || phraseKey != nil {
                HStack(spacing: DL.Space.m) {
                    // why: years drill dropped — it's covered by the numbers
                    // drill (identical reading in Swahili/Ukrainian). Years
                    // live on only as a phrase slot.
                    if slotsAvailable {
                        ForEach(Self.drillKinds, id: \.self) { kind in
                            drillChip(kind)
                        }
                    }
                    if phraseKey != nil {
                        phraseChip
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

    private func languageName(_ code: String) -> String {
        LanguageNames.display(code, locale: locale, catalog: model.catalog)
    }

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
            guard let key = phraseKey else { return }
            destination = .phrases(source: key.source, target: key.target, reverse: key.reverse)
        } label: {
            chipLabel(emoji: "💬", title: Text("trainer.phrases"))
        }
        .buttonStyle(TrainerChipButtonStyle())
        .accessibilityLabel("a11y.practicePhrases")
    }

    private func chipLabel(emoji: String, title: Text) -> some View {
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
        }
    }

    /// Catalog key for the drill title.
    var trainerTitleKey: LocalizedStringKey {
        switch self {
        case .numbers: return "trainer.numbers"
        case .years: return "trainer.years"
        case .clock: return "trainer.clock"
        }
    }

    /// Localized display key for the singular prompt caption ("Zahl · auf …").
    var trainerPromptLabelKey: LocalizedStringKey {
        switch self {
        case .numbers: return "trainer.prompt.number"
        case .years: return "trainer.prompt.year"
        case .clock: return "trainer.clock"
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
