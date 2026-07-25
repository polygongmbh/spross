import SwiftUI
import SprossKern

/// Compact "Training" card on the Heute screen: slot drills (Zahlen /
/// Uhrzeit) plus the sentence drill. Offerings are registry-driven: slot
/// drills appear only when Kern's trainer supports the learned language,
/// the sentence drill only when (source, target) templates exist — an
/// unauthored language (e.g. en) hides its sections, an empty card hides
/// entirely. Trainers are stateless — they never touch BoxState or FSRS.
struct TrainerHubView: View {
    let model: AppModel

    @Environment(\.locale) private var locale

    /// Standalone drills offered on the card — years is intentionally absent
    /// (redundant with numbers), though it still backs phrase slots.
    private static let drillKinds: [TrainerKind] = [.numbers, .clock]

    @State private var activeDrill: Drill?

    private struct Drill: Identifiable {
        let mode: TrainerSessionView.Mode
        let id: String

        init(kind: TrainerKind, language: String) {
            mode = .slots(kind, language)
            id = "\(kind.name)-\(language)"
        }

        init(phrases source: String, target: String, reverse: Bool) {
            mode = .phrases(source: source, target: target, reverse: reverse)
            id = "phrases-\(source)-\(target)-\(reverse)"
        }
    }

    /// The language being learned — every drill runs in it.
    private var drillLanguage: String? { model.targetLanguage }

    private var slotsAvailable: Bool {
        drillLanguage.map { Trainer.shared.supports(language: $0) } ?? false
    }

    /// Sentence templates are keyed (source, target); learners OF German get
    /// the reverse drill over the (de, source) templates.
    private var phraseKey: (source: String, target: String, reverse: Bool)? {
        guard let target = drillLanguage else { return nil }
        let key = target == "de"
            ? (source: "de", target: model.sourceLanguage, reverse: true)
            : (source: model.sourceLanguage, target: target, reverse: false)
        let templates = PhraseTemplates.shared.templates(source: key.source, target: key.target)
        return templates.isEmpty ? nil : key
    }

    var body: some View {
        Group {
            if slotsAvailable || phraseKey != nil {
                card
            }
        }
        .fullScreenCover(item: $activeDrill) { drill in
            TrainerSessionView(mode: drill.mode, normalizer: normalizer(for: drill.mode))
                .environment(\.locale, model.knownLocale)
        }
    }

    private func normalizer(for mode: TrainerSessionView.Mode) -> AnswerNormalizer? {
        // why: drills grade strictly — no article forgiveness, typo budget
        // capped at 1, digits exact-only (design.md §Trainers) — unlike the
        // lenient vocab-produce default.
        model.languageInfo(mode.typedLanguage)
            .map { AnswerNormalizer(answerLanguage: $0, articleLeniency: false,
                                    maxTypoBudget: KotlinInt(int: 1)) }
    }

    // MARK: - Card

    private var card: some View {
        VStack(alignment: .leading, spacing: DL.Space.l) {
            Text("trainer.title")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
            Text("trainer.subtitle \(languageName(drillLanguage ?? ""))")
                .font(DL.Fonts.subheadline)
                .foregroundStyle(Color.dlTextSecondary)
            HStack(spacing: DL.Space.m) {
                // why: years drill dropped — it's covered by the numbers drill
                // (identical reading in Swahili/Ukrainian). Years live on only
                // as a phrase slot.
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
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.card, style: .continuous)
                .fill(Color.dlSurface)
        )
        .dlCardShadow()
        #if DEBUG
        // UI-test hook: `-uitest-trainer numbers|years|clock|phrases` opens
        // that drill (in the learned language, like the chips). Attached
        // HERE because the card only appears once the box is loaded.
        .onAppear {
            guard activeDrill == nil,
                  let raw = UserDefaults.standard.string(forKey: "uitest-trainer") else { return }
            let kinds: [String: TrainerKind] = ["numbers": .numbers, "years": .years, "clock": .clock]
            if let kind = kinds[raw], let language = drillLanguage {
                activeDrill = Drill(kind: kind, language: language)
            } else if raw == "phrases", let key = phraseKey {
                activeDrill = Drill(phrases: key.source, target: key.target, reverse: key.reverse)
            }
        }
        #endif
    }

    private func languageName(_ code: String) -> String {
        LanguageNames.display(code, locale: locale, catalog: model.catalog)
    }

    private func drillChip(_ kind: TrainerKind) -> some View {
        Button {
            guard let language = drillLanguage else { return }
            activeDrill = Drill(kind: kind, language: language)
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
            activeDrill = Drill(phrases: key.source, target: key.target, reverse: key.reverse)
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

/// Pressed-state feedback for the drill chips (mirrors DL button springs).
private struct TrainerChipButtonStyle: ButtonStyle {
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
