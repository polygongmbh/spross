import SwiftUI
import SprossKern

/// Writing down a word the catalog has none of. Reached only from a search that
/// found nothing — the moment the learner has already proved the box cannot
/// answer them.
///
/// Both sides are asked for, because a word is only studiable as a pair. The
/// known side arrives prefilled from the query: someone typing into a search box
/// is far more often naming what they want to be able to SAY than a form they
/// already met in the wild.
///
/// One side alone is still taken, as a SUGGESTION: the learner noticed a gap and
/// only has the half they came with. It is never scheduled — there is nothing to
/// ask them yet — and waits in the feedback section to be sent on to the catalog
/// (`BoxFeedbackSection`, `OwnWord`).
struct OwnWordFormView: View {
    let model: AppModel
    /// What the search could not find; the known-language field starts from it.
    let query: String
    /// Called with the new card's id once the word is in the box.
    let added: (String) -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var known: String
    @State private var learning = ""
    @State private var emoji = ""
    @FocusState private var focus: Field?

    private enum Field { case known, learning, emoji }

    init(model: AppModel, query: String, added: @escaping (String) -> Void) {
        self.model = model
        self.query = query
        self.added = added
        _known = State(initialValue: query)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: DL.Space.xl) {
                    field("box.ownWords.inLanguage \(languageName(model.sourceLanguage))",
                          text: $known, field: .known)
                    field("box.ownWords.inLanguage \(languageName(model.targetLanguage ?? ""))",
                          text: $learning, field: .learning)
                    field("box.ownWords.picture", text: $emoji, field: .emoji)
                    Text(isPair ? "box.ownWords.explainer" : "box.ownWords.explainer.suggestion")
                        .font(DL.Fonts.caption)
                        .foregroundStyle(Color.dlTextSecondary)
                }
                .padding(DL.Space.xl)
            }
            .background(Color.dlBackground.ignoresSafeArea())
            .navigationTitle("box.ownWords.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("box.ownWords.add") { save() }
                        .disabled(!hasAnything)
                }
            }
        }
        .tint(.dlAccent)
        // why: the known side is already filled in, so the cursor belongs on the
        // half that is actually missing.
        .onAppear { focus = .learning }
    }

    /// Both sides written: a studiable word rather than a suggestion.
    private var isPair: Bool { written(known) && written(learning) }

    /// One side is enough to take the word in — the other is what makes it studiable.
    private var hasAnything: Bool { written(known) || written(learning) }

    private func written(_ text: String) -> Bool {
        !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func save() {
        guard let id = model.addOwnWord(known: known, learning: learning, emoji: emoji)
        else { return }
        // why: a suggestion joins no card, so there is nothing on a shelf to reveal —
        // the caller's scroll-to would land on an area that does not exist.
        if isPair { added(id) }
        dismiss()
    }

    private func languageName(_ code: String) -> String {
        LanguageNames.display(code, locale: model.knownLocale, catalog: model.catalog)
    }

    private func field(_ label: LocalizedStringKey, text: Binding<String>,
                       field: Field) -> some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text(label)
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
            TextField(text: text) { EmptyView() }
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextPrimary)
                .autocorrectionDisabled()
                // why: a word is not a sentence — the automatic capital put one on a
                // Swahili noun, which is simply the wrong spelling of the word being
                // stored. Whoever writes German capitalizes it themselves.
                .textInputAutocapitalization(.never)
                .submitLabel(field == .emoji ? .done : .next)
                .focused($focus, equals: field)
                .onSubmit { advance(from: field) }
                .padding(.horizontal, DL.Space.l)
                .frame(minHeight: 52)
                .background(
                    RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
                        .fill(Color.dlSurface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
                        .strokeBorder(Color.dlSeparator, lineWidth: 1)
                )
        }
    }

    private func advance(from field: Field) {
        switch field {
        case .known: focus = .learning
        case .learning: focus = .emoji
        case .emoji: focus = nil
        }
    }
}
