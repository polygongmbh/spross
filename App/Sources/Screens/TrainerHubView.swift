import SwiftUI
import DuoKern
import DuoKernTrainer

/// Compact "Training" card on the Heute screen: three slot drills
/// (Zahlen / Jahreszahlen / Uhrzeit) plus the sentence drill. The drill
/// language is always the language being learned (no toggle).
/// Trainers are stateless — they never touch BoxState or FSRS.
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

        init(kind: TrainerKind, language: TrainerLanguage) {
            mode = .slots(kind, language)
            id = "\(kind.rawValue)-\(language.rawValue)"
        }

        init(phrases pair: LanguagePair, reverse: Bool) {
            mode = .phrases(pair, reverse: reverse)
            id = "phrases-\(pair.rawValue)-\(reverse)"
        }
    }

    var body: some View {
        Group {
            if let config = model.box?.config {
                card(config)
            }
        }
        .fullScreenCover(item: $activeDrill) { drill in
            TrainerSessionView(mode: drill.mode)
                .environment(\.locale, model.knownLocale)
        }
    }

    // MARK: - Card

    private func card(_ config: BoxConfig) -> some View {
        VStack(alignment: .leading, spacing: DL.Space.l) {
            Text("Training")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
            Text("Auf \(effectiveLanguage.displayName(in: locale)) · zählt nicht für deine Box.")
                .font(DL.Fonts.subheadline)
                .foregroundStyle(Color.dlTextSecondary)
            HStack(spacing: DL.Space.m) {
                // why: years drill dropped — it's covered by the numbers drill
                // (identical reading in Swahili/Ukrainian). Years live on only
                // as a phrase slot.
                ForEach(Self.drillKinds, id: \.rawValue) { kind in
                    drillChip(kind)
                }
                phraseChip(config)
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
        // HERE because the card only appears once the box config is loaded.
        .onAppear {
            guard activeDrill == nil,
                  let raw = UserDefaults.standard.string(forKey: "uitest-trainer") else { return }
            if let kind = TrainerKind(rawValue: raw) {
                activeDrill = Drill(kind: kind, language: effectiveLanguage)
            } else if raw == "phrases" {
                activeDrill = Drill(phrases: config.pair,
                                    reverse: config.direction == .targetToDe)
            }
        }
        #endif
    }

    private func drillChip(_ kind: TrainerKind) -> some View {
        Button {
            activeDrill = Drill(kind: kind, language: effectiveLanguage)
        } label: {
            VStack(spacing: DL.Space.s) {
                Text(kind.trainerEmoji)
                    .font(.system(size: 30))
                    .accessibilityHidden(true)
                Text(kind.trainerTitleKey)
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
        .buttonStyle(TrainerChipButtonStyle())
        .accessibilityLabel(Text(kind.trainerTitleKey) + Text(" üben, auf \(effectiveLanguage.displayName(in: locale))"))
    }

    /// Sentence drill: composes phrase templates with slot values.
    /// Learners of German get the REVERSE drill (target sentence shown,
    /// German typed) — like all drills, it runs in the learned language.
    private func phraseChip(_ config: BoxConfig) -> some View {
        Button {
            activeDrill = Drill(phrases: config.pair,
                                reverse: config.direction == .targetToDe)
        } label: {
            VStack(spacing: DL.Space.s) {
                Text("💬")
                    .font(.system(size: 30))
                    .accessibilityHidden(true)
                Text("Sätze")
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
        .buttonStyle(TrainerChipButtonStyle())
        .accessibilityLabel("Sätze üben")
    }

    // MARK: - Drill language

    /// Always the language being learned: `.deToTarget` → the pair's
    /// target language, `.targetToDe` → German.
    private var effectiveLanguage: TrainerLanguage {
        guard let config = model.box?.config else { return .german }
        return config.direction == .deToTarget
            ? Self.targetLanguage(config.pair)
            : .german
    }

    private static func targetLanguage(_ pair: LanguagePair) -> TrainerLanguage {
        pair == .deSw ? .swahili : .ukrainian
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

    var trainerTitle: String {
        switch self {
        case .numbers: return "Zahlen"
        case .years: return "Jahreszahlen"
        case .clock: return "Uhrzeit"
        }
    }

    /// Localized display key for the drill title (German source = catalog key).
    var trainerTitleKey: LocalizedStringKey {
        switch self {
        case .numbers: return "Zahlen"
        case .years: return "Jahreszahlen"
        case .clock: return "Uhrzeit"
        }
    }

    /// Singular caption on the prompt card ("Zahl · auf Swahili").
    var trainerPromptLabel: String {
        switch self {
        case .numbers: return "Zahl"
        case .years: return "Jahreszahl"
        case .clock: return "Uhrzeit"
        }
    }

    /// Localized display key for the singular prompt caption.
    var trainerPromptLabelKey: LocalizedStringKey {
        switch self {
        case .numbers: return "Zahl"
        case .years: return "Jahreszahl"
        case .clock: return "Uhrzeit"
        }
    }
}

extension TrainerLanguage {
    var trainerName: String {
        switch self {
        case .german: return "Deutsch"
        case .swahili: return "Swahili"
        case .ukrainian: return "Ukrainisch"
        }
    }

    /// Language name in the UI locale: English chrome shows "German"/"Swahili"
    /// even though `trainerName` stays German (it drives the content input
    /// placeholder, which must not be localized). Falls back to the German key.
    func displayName(in locale: Locale) -> String {
        DLChrome.string(trainerName, locale: locale)
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
