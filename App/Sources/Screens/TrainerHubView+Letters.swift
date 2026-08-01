import SwiftUI
import SprossKern

/// LETTERS half of the trainer hub: what the hub can open, whether the
/// alphabet exists at all, and the row that opens it. State lives on
/// TrainerHubView; split out purely for file size.
///
/// Registry by FILE: a language has an alphabet exactly when
/// `catalog/alphabet/<lang>.json` was authored — adding one is dropping a
/// file, with no Kotlin and no Swift to touch.

/// Everything the hub presents, as ONE item.
///
/// One state, two modifiers reading halves of it: a second
/// `fullScreenCover(isPresented:)` stacked on the same view is not reliably
/// honoured by SwiftUI — the symptom is a chip that does nothing — and the
/// reference sheet is a `.sheet` besides, so the choice is which of the two
/// presenters this destination belongs to, not whether it is showing.
enum HubDestination: Identifiable {
    case slots(kind: TrainerKind, language: String)
    case phrases(source: String, target: String, reverse: Bool)
    case alphabet(language: String)

    var id: String {
        switch self {
        case let .slots(kind, language): return "\(kind.name)-\(language)"
        case let .phrases(source, target, reverse): return "phrases-\(source)-\(target)-\(reverse)"
        case let .alphabet(language): return "alphabet-\(language)"
        }
    }

    /// The run this opens full screen — nil for what is presented as a sheet.
    var drillMode: TrainerSessionView.Mode? {
        switch self {
        case let .slots(kind, language): return .slots(kind, language)
        case let .phrases(source, target, reverse):
            return .phrases(source: source, target: target, reverse: reverse)
        case .alphabet: return nil
        }
    }

    /// The alphabet this opens as a sheet — nil for every full-screen run.
    var sheetLanguage: String? {
        switch self {
        case .slots, .phrases: return nil
        case let .alphabet(language): return language
        }
    }
}

extension TrainerHubView {

    // MARK: - Availability

    /// The reference sheet exists exactly where the file does.
    var alphabetAvailable: Bool {
        guard let language = drillLanguage else { return false }
        return model.catalog?.alphabet(lang: language) != nil
    }

    // MARK: - Presentation

    /// The full-screen half of the destination.
    var drillDestination: Binding<HubDestination?> { half { $0.drillMode != nil } }

    /// The sheet half — reference material, which leaves the hub behind it.
    var sheetDestination: Binding<HubDestination?> { half { $0.sheetLanguage != nil } }

    private func half(_ belongs: @escaping (HubDestination) -> Bool) -> Binding<HubDestination?> {
        let destination = $destination
        return Binding(get: { destination.wrappedValue.flatMap { belongs($0) ? $0 : nil } },
                       set: { destination.wrappedValue = $0 })
    }

    // MARK: - The row

    /// Full width under the chips, never a chip among them: the chips start a
    /// run, and this one opens a table to read.
    var alphabetRow: some View {
        Button {
            guard let language = drillLanguage else { return }
            destination = .alphabet(language: language)
        } label: {
            HStack(spacing: DL.Space.m) {
                Text(verbatim: "🔠")
                    .font(.system(size: 26))
                    .accessibilityHidden(true)
                Text("trainer.alphabet")
                    .font(DL.Fonts.body)
                    .foregroundStyle(Color.dlTextPrimary)
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                    .accessibilityHidden(true)
            }
            .frame(maxWidth: .infinity, minHeight: 52, alignment: .leading)
            .padding(.horizontal, DL.Space.l)
            .background(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .fill(Color.dlSurfaceTint)
            )
        }
        .buttonStyle(TrainerChipButtonStyle())
    }
}

#if DEBUG
extension TrainerHubView {
    /// UI-test hook: `-uitest-trainer numbers|years|clock|phrases|alphabet`
    /// resolved against what this language actually offers.
    func uitestDestination(_ raw: String) -> HubDestination? {
        let kinds: [String: TrainerKind] = ["numbers": .numbers, "years": .years, "clock": .clock]
        if let kind = kinds[raw], let language = drillLanguage {
            return .slots(kind: kind, language: language)
        }
        if raw == "phrases", let key = phraseKey {
            return .phrases(source: key.source, target: key.target, reverse: key.reverse)
        }
        if raw == "alphabet", alphabetAvailable, let language = drillLanguage {
            return .alphabet(language: language)
        }
        return nil
    }
}
#endif
