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
    case numbers(language: String)
    case letters(language: String)
    case alphabet(language: String)

    var id: String {
        switch self {
        case let .numbers(language): return "numbers-\(language)"
        case let .letters(language): return "letters-\(language)"
        case let .alphabet(language): return "alphabet-\(language)"
        }
    }

    /// The numbers overview's language — the run it starts is that screen's own
    /// to present, so the hub never builds a slot-drill mode any more.
    var numbersLanguage: String? {
        switch self {
        case let .numbers(language): return language
        case .letters, .alphabet: return nil
        }
    }

    /// The letter drill's language — the last full-screen run the hub opens
    /// directly, until the letters overview absorbs the alphabet sheet.
    var lettersLanguage: String? {
        switch self {
        case .numbers, .alphabet: return nil
        case let .letters(language): return language
        }
    }

    /// The alphabet this opens as a sheet — nil for every full-screen run.
    var alphabetLanguage: String? {
        switch self {
        case .numbers, .letters: return nil
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

    /// The drill exists where the sheet does AND this device can actually ask
    /// something: a bundled letter recording, or a voice for the language.
    /// Swahili on the iPhone has neither, and hides — twice over, since no
    /// Swahili alphabet is authored either.
    ///
    /// It does NOT turn on reading aloud being switched on. Hiding a whole
    /// feature behind a one-tap-fixable state is how a feature stops being
    /// found; the drill says so on its own prompt card instead (§6.1).
    var letterDrillAvailable: Bool { letterDrill?.drillAvailable ?? false }

    /// Availability is not decided once at launch: a voice may be installed in
    /// Settings while the app sleeps, so this is rebuilt on every foreground —
    /// the same signal on which the speaker drops its cached voice table.
    func refreshLetterDrill() {
        letterDrill = drillLanguage.map { LetterDrillAvailability(model: model, language: $0) }
    }

    // MARK: - Presentation

    /// The full-screen half of the destination: a run the hub opens directly,
    /// which is now only the letter drill.
    var drillDestination: Binding<HubDestination?> { half { $0.lettersLanguage != nil } }

    /// The sheet half — everything you READ from: the numbers overview (which
    /// starts its own run) and the alphabet.
    var sheetDestination: Binding<HubDestination?> { half { $0.lettersLanguage == nil } }

    private func half(_ belongs: @escaping (HubDestination) -> Bool) -> Binding<HubDestination?> {
        let destination = $destination
        return Binding(get: { destination.wrappedValue.flatMap { belongs($0) ? $0 : nil } },
                       set: { destination.wrappedValue = $0 })
    }

    // MARK: - The chip and the row

    /// A chip among the drills, because it starts a run like they do — the
    /// Alphabet row below it opens a table instead.
    var lettersChip: some View {
        Button {
            guard let language = drillLanguage else { return }
            destination = .letters(language: language)
        } label: {
            chipLabel(emoji: "🔤", title: Text("trainer.letters"))
        }
        .buttonStyle(TrainerChipButtonStyle())
        .accessibilityLabel(Text("trainer.letters")
            + Text("a11y.practiceSuffix \(languageName(drillLanguage ?? ""))"))
    }

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
    /// UI-test hook: `-uitest-trainer numbers|letters|alphabet` resolved against
    /// what this language actually offers.
    ///
    /// Clock and phrases are no longer surfaces of their own: reach them with
    /// `-uitest-trainer numbers -uitest-variants clock -uitest-run 1`, which is
    /// also the only way to photograph a modifier or a mixed selection.
    func uitestDestination(_ raw: String) -> HubDestination? {
        if raw == "numbers", slotsAvailable, let language = drillLanguage {
            return .numbers(language: language)
        }
        if raw == "letters", let language = drillLanguage,
           // why: computed here rather than read off `letterDrill` — this hook
           // hangs off the CARD's onAppear, which SwiftUI runs before the
           // enclosing view's, so the state it would read is still nil.
           LetterDrillAvailability(model: model, language: language).drillAvailable {
            return .letters(language: language)
        }
        if raw == "alphabet", alphabetAvailable, let language = drillLanguage {
            return .alphabet(language: language)
        }
        return nil
    }
}
#endif
