import SwiftUI
import DuoKern

/// Compact "Training" card on the Heute screen: three slot drills
/// (Zahlen / Jahreszahlen / Uhrzeit) plus a small language toggle.
/// Trainers are stateless — they never touch BoxState or FSRS.
struct TrainerHubView: View {
    let model: AppModel

    /// nil = follow the box's direction (the language being produced).
    @State private var selectedLanguage: TrainerLanguage?
    @State private var activeDrill: Drill?

    private struct Drill: Identifiable {
        let kind: TrainerKind
        let language: TrainerLanguage
        var id: String { "\(kind.rawValue)-\(language.rawValue)" }
    }

    var body: some View {
        Group {
            if let config = model.box?.config {
                card(config)
            }
        }
        .fullScreenCover(item: $activeDrill) { drill in
            TrainerSessionView(kind: drill.kind, language: drill.language)
        }
        #if DEBUG
        // UI-test hook: `-uitest-trainer numbers|years|clock` opens that drill.
        .onAppear {
            if activeDrill == nil,
               let raw = UserDefaults.standard.string(forKey: "uitest-trainer"),
               let kind = TrainerKind(rawValue: raw) {
                activeDrill = Drill(kind: kind, language: effectiveLanguage)
            }
        }
        #endif
    }

    // MARK: - Card

    private func card(_ config: BoxConfig) -> some View {
        VStack(alignment: .leading, spacing: DL.Space.l) {
            HStack(alignment: .firstTextBaseline) {
                Text("Training")
                    .font(DL.Fonts.title)
                    .foregroundStyle(Color.dlTextPrimary)
                Spacer(minLength: DL.Space.m)
                languageToggle(config)
            }
            Text("Zehn schnelle Aufgaben — zählt nicht in deine Box.")
                .font(DL.Fonts.subheadline)
                .foregroundStyle(Color.dlTextSecondary)
            HStack(spacing: DL.Space.m) {
                ForEach(TrainerKind.allCases, id: \.rawValue) { kind in
                    drillChip(kind)
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
    }

    private func drillChip(_ kind: TrainerKind) -> some View {
        Button {
            activeDrill = Drill(kind: kind, language: effectiveLanguage)
        } label: {
            VStack(spacing: DL.Space.s) {
                Text(kind.trainerEmoji)
                    .font(.system(size: 30))
                    .accessibilityHidden(true)
                Text(kind.trainerTitle)
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
        .accessibilityLabel("\(kind.trainerTitle) üben, auf \(effectiveLanguage.trainerName)")
    }

    // MARK: - Language toggle

    private func languageToggle(_ config: BoxConfig) -> some View {
        HStack(spacing: DL.Space.xs) {
            ForEach(languageOptions(config), id: \.rawValue) { language in
                let active = language == effectiveLanguage
                Button {
                    selectedLanguage = language
                } label: {
                    Text(language.trainerName)
                        .font(DL.Fonts.caption)
                        .foregroundStyle(active ? Color.dlAccent : Color.dlTextSecondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                        .padding(.horizontal, DL.Space.m)
                        .frame(minHeight: 44)
                        .background(
                            Capsule().fill(active ? Color.dlAccent.opacity(0.14) : .clear)
                        )
                }
                .buttonStyle(.plain)
            }
        }
        .background(Capsule().strokeBorder(Color.dlSeparator, lineWidth: 1))
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Trainingssprache")
    }

    /// Target language first (it's the one being learned), German second.
    private func languageOptions(_ config: BoxConfig) -> [TrainerLanguage] {
        [Self.targetLanguage(config.pair), .german]
    }

    private var effectiveLanguage: TrainerLanguage {
        if let selectedLanguage { return selectedLanguage }
        guard let config = model.box?.config else { return .german }
        // Default = the language the user currently produces answers in.
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

    /// Singular caption on the prompt card ("Zahl · auf Swahili").
    var trainerPromptLabel: String {
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
}
