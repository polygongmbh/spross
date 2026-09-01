import SwiftUI
import SprossKern

/// Writing down a word the catalog has none of, or rewriting one already written.
///
/// Both sides are asked for, because a word is only studiable as a pair. Where the
/// form was reached from a search that found nothing, the known side arrives
/// prefilled from the query: someone typing into a search box is far more often
/// naming what they want to be able to SAY than a form they already met in the wild.
///
/// One side alone is still taken, as a SUGGESTION: the learner noticed a gap and
/// only has the half they came with. It is never scheduled — there is nothing to
/// ask them yet — and waits in the Box's own-content section to be sent on to the
/// catalog (`BoxOwnContentSection`, `OwnWord`).
///
/// Editing keeps the word's id, and with it its schedule and its place in the
/// queue (`BoxEngine.updateOwnWord`): a typo fixed must not cost the progress made
/// on the word.
struct OwnWordFormView: View {
    let model: AppModel
    /// What the form opens on.
    let seed: Seed
    /// Called with the card's id once a NEW word is in the box.
    var added: (String) -> Void = { _ in }

    /// The three ways in. Writing from scratch carries at most a failed query; a
    /// catalog word hands over both of its sides; an own word is rewritten in place.
    enum Seed {
        case query(String)
        case card(Card)
        case editing(OwnWord)
    }

    @Environment(\.dismiss) private var dismiss

    @State private var known: String
    @State private var learning: String
    @State private var emoji: String
    @FocusState private var focus: Field?

    private enum Field { case known, learning, emoji }

    init(model: AppModel, seed: Seed, added: @escaping (String) -> Void = { _ in }) {
        self.model = model
        self.seed = seed
        self.added = added
        switch seed {
        case .query(let query):
            _known = State(initialValue: query)
            _learning = State(initialValue: "")
            _emoji = State(initialValue: "")
        case .card(let card):
            _known = State(initialValue: card.source.text)
            _learning = State(initialValue: card.target.text)
            _emoji = State(initialValue: card.emoji ?? "")
        case .editing(let word):
            _known = State(initialValue: word.texts[model.sourceLanguage] ?? "")
            _learning = State(initialValue: word.texts[model.targetLanguage ?? ""] ?? "")
            _emoji = State(initialValue: word.emoji ?? "")
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: DL.Space.xl) {
                    field(label(model.sourceLanguage), text: $known, field: .known)
                    swapButton
                    field(label(model.targetLanguage ?? ""), text: $learning, field: .learning)
                    picture
                    Text(isPair ? "box.own.word.explainer" : "box.own.word.explainer.suggestion")
                        .font(DL.Fonts.caption)
                        .foregroundStyle(Color.dlTextSecondary)
                }
                .padding(DL.Space.xl)
            }
            .background(Color.dlBackground.ignoresSafeArea())
            .navigationTitle(isEditing ? "box.own.word.edit" : "box.own.word.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isEditing ? "box.own.word.save" : "box.own.word.add") { save() }
                        .disabled(!hasAnything)
                }
            }
        }
        .tint(.dlAccent)
        // why: the cursor belongs on the half that is MISSING — the learned side
        // where the known one arrived prefilled, the first field where neither did.
        // A form that opened on a finished pair is there to be read before it is
        // changed, so it raises no keyboard over itself.
        .onAppear {
            if !written(learning) { focus = written(known) ? .learning : .known }
        }
    }

    private var isEditing: Bool {
        if case .editing = seed { return true }
        return false
    }

    /// Both sides written: a studiable word rather than a suggestion.
    private var isPair: Bool { written(known) && written(learning) }

    /// One side is enough to take the word in — the other is what makes it studiable.
    private var hasAnything: Bool { written(known) || written(learning) }

    private func written(_ text: String) -> Bool {
        !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func save() {
        if case .editing(let word) = seed {
            model.updateOwnWord(word, known: known, learning: learning, emoji: emoji)
            dismiss()
            return
        }
        guard let id = model.addOwnWord(known: known, learning: learning, emoji: emoji)
        else { return }
        // why: a suggestion joins no card, so there is nothing on a shelf to reveal —
        // the caller's scroll-to would land on an area that does not exist.
        if isPair { added(id) }
        dismiss()
    }

    /// The language's flag in front of the name — the same pair of marks the profile
    /// pickers wear, so the two fields are told apart at a glance rather than read.
    private func label(_ code: String) -> LocalizedStringKey {
        let name = LanguageNames.display(code, locale: model.knownLocale, catalog: model.catalog)
        let flag = model.languageInfo(code)?.flag
        return "box.own.word.inLanguage \(flag.map { "\($0) \(name)" } ?? name)"
    }

    /// For the learner who filled the two fields in the wrong way round — the one
    /// mistake this form cannot catch itself, since either order is a real word.
    private var swapButton: some View {
        Button {
            swap(&known, &learning)
        } label: {
            Label("box.own.word.swap", systemImage: "arrow.up.arrow.down")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlAccent)
        }
        .frame(maxWidth: .infinity, alignment: .center)
    }

    private var picture: some View {
        field("box.own.word.picture", text: $emoji, field: .emoji) { quickPicks }
            // why: the field takes anything a keyboard can send, so the cap is
            // enforced on what lands in it rather than on what may be typed.
            .onChange(of: emoji) { _, typed in
                let capped = String(typed.prefix(Int(OwnWords.shared.MAX_EMOJI)))
                if capped != typed { emoji = capped }
            }
    }

    /// The commonest pictures, one tap away — the emoji keyboard is a long trip for
    /// what is an optional decoration on most words. Which they are is Kern's
    /// (`OwnWords.QUICK_EMOJI`), so both phones offer the same set.
    private var quickPicks: some View {
        ScrollView(.horizontal) {
            HStack(spacing: DL.Space.s) {
                ForEach(OwnWords.shared.QUICK_EMOJI, id: \.self) { pick in
                    Button {
                        emoji = pick
                    } label: {
                        Text(verbatim: pick)
                            .font(.title3)
                            .frame(width: 40, height: 40)
                            .background(
                                Circle().fill(emoji == pick
                                              ? Color.dlAccent.opacity(0.18)
                                              : Color.dlSurface)
                            )
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text(verbatim: pick))
                }
            }
            .padding(.vertical, 2)
        }
        .scrollIndicators(.hidden)
    }

    private func field<Between: View>(
        _ label: LocalizedStringKey,
        text: Binding<String>,
        field: Field,
        // why: the picture's taps belong UNDER its own label — offered above it they
        // read as one more thing about the language field before them.
        @ViewBuilder between: () -> Between = { EmptyView() },
    ) -> some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text(label)
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
            between()
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
