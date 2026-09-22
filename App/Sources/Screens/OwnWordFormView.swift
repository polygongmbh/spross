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
/// The note field takes what neither side can hold, and takes it ALONE: a form with
/// nothing but a note filed is a REMARK, which is how the learner says something that
/// is about no word — this form is the only surface that is not already a card's.
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

    @FocusState private var focus: Field?

    private enum Field { case known, learning, emoji, comment }

    /// What is typed, and what it is typed over — the model's (`AppModel.ownWordDraft`),
    /// so backgrounding the app cannot take it. A draft left over from another opening
    /// is not this form's, and the seed is read instead.
    private var draft: OwnWordDraft {
        if let held = model.ownWordDraft, held.opening == opening { return held }
        return seeded
    }

    private var known: String { draft.known }
    private var learning: String { draft.learning }
    private var emoji: String { draft.emoji }
    private var comment: String { draft.comment }

    /// Which opening of the form this is: another word, or another failed search, is
    /// another form and starts from its own seed.
    private var opening: String {
        switch seed {
        case .query(let query): return "query:\(query)"
        case .card(let card): return "card:\(card.id)"
        case .editing(let word): return "edit:\(word.id)"
        }
    }

    private var seeded: OwnWordDraft {
        switch seed {
        case .query(let query):
            return OwnWordDraft(opening: opening, known: query, learning: "",
                                emoji: "", comment: "")
        case .card(let card):
            return OwnWordDraft(opening: opening, known: card.source.text,
                                learning: card.target.text, emoji: card.emoji ?? "",
                                comment: "")
        case .editing(let word):
            return OwnWordDraft(opening: opening,
                                known: word.texts[model.sourceLanguage] ?? "",
                                learning: word.texts[model.targetLanguage ?? ""] ?? "",
                                emoji: word.emoji ?? "", comment: word.comment ?? "")
        }
    }

    private func write(_ field: WritableKeyPath<OwnWordDraft, String>) -> Binding<String> {
        Binding(
            get: { draft[keyPath: field] },
            set: {
                var written = draft
                written[keyPath: field] = $0
                model.ownWordDraft = written
            },
        )
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Theme.spacing.xl) {
                    field(label(model.sourceLanguage), text: write(\.known), field: .known)
                    swapButton
                    field(label(model.targetLanguage ?? ""), text: write(\.learning),
                          field: .learning)
                    picture
                    field("box.own.word.comment", text: write(\.comment), field: .comment)
                    Text(explainer)
                        .font(Theme.typography.caption)
                        .foregroundStyle(Theme.colors.textSecondary)
                }
                .padding(Theme.spacing.xl)
            }
            .background(Theme.colors.background.ignoresSafeArea())
            .navigationTitle(isEditing ? "box.own.word.edit" : "box.own.word.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.cancel") { close() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isEditing ? "box.own.word.save" : "box.own.word.add") { save() }
                        .disabled(!hasAnything)
                }
            }
        }
        .tint(Theme.colors.accent)
    }

    private var isEditing: Bool {
        if case .editing = seed { return true }
        return false
    }

    /// Both sides written: a studiable word rather than a suggestion.
    private var isPair: Bool { written(known) && written(learning) }

    /// The note is all there is: something to say about no word at all. An EMPTY form is
    /// not one — it has nothing to say yet, and reads as the ordinary word form until the
    /// learner writes into the note instead of into a side.
    private var isRemark: Bool {
        written(comment) && !written(known) && !written(learning)
    }

    /// One side is enough to take the word in — the other is what makes it studiable —
    /// and a note on its own is enough to take a remark in.
    private var hasAnything: Bool {
        written(known) || written(learning) || written(comment)
    }

    /// What the form says it is doing, read off what has been typed into it.
    private var explainer: LocalizedStringKey {
        if isRemark { return "box.own.word.explainer.remark" }
        return isPair ? "box.own.word.explainer" : "box.own.word.explainer.suggestion"
    }

    private func written(_ text: String) -> Bool {
        !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func save() {
        if case .editing(let word) = seed {
            model.updateOwnWord(word, known: known, learning: learning, emoji: emoji,
                                comment: comment)
            close()
            return
        }
        let pair = isPair
        guard let id = model.addOwnWord(known: known, learning: learning, emoji: emoji,
                                        comment: comment)
        else { return }
        // why: a suggestion joins no card, so there is nothing on a shelf to reveal —
        // the caller's scroll-to would land on an area that does not exist.
        if pair { added(id) }
        close()
    }

    /// Leaves the form and drops the draft with it: what was written is either in the box
    /// now or was given up on, and either way it must not stand in the next form opened.
    private func close() {
        model.ownWordDraft = nil
        dismiss()
    }

    /// The language's flag in front of the name — the same pair of marks the profile
    /// pickers wear, so the two fields are told apart at a glance rather than read.
    private func label(_ code: String) -> LocalizedStringKey {
        let name = LanguageNames.display(code, catalog: model.catalog)
        let flag = model.languageInfo(code)?.flag
        return "box.own.word.inLanguage \(flag.map { "\($0) \(name)" } ?? name)"
    }

    /// For the learner who filled the two fields in the wrong way round — the one
    /// mistake this form cannot catch itself, since either order is a real word.
    private var swapButton: some View {
        Button {
            var swapped = draft
            swapped.known = draft.learning
            swapped.learning = draft.known
            model.ownWordDraft = swapped
        } label: {
            LinkLabel("box.own.word.swap", icon: "arrow.up.arrow.down", font: Theme.typography.caption)
        }
        .frame(maxWidth: .infinity, alignment: .center)
    }

    private var picture: some View {
        field("box.own.word.picture", text: write(\.emoji), field: .emoji) { quickPicks }
            // why: the field takes anything a keyboard can send, so the cap is
            // enforced on what lands in it rather than on what may be typed.
            .onChange(of: emoji) { _, typed in
                let capped = String(typed.prefix(Int(OwnWords.shared.MAX_EMOJI)))
                if capped != typed { write(\.emoji).wrappedValue = capped }
            }
    }

    /// The commonest pictures, one tap away — the emoji keyboard is a long trip for
    /// what is an optional decoration on most words. Which they are is Kern's
    /// (`OwnWords.QUICK_EMOJI`), so both phones offer the same set.
    private var quickPicks: some View {
        ScrollView(.horizontal) {
            HStack(spacing: Theme.spacing.sm) {
                ForEach(OwnWords.shared.QUICK_EMOJI, id: \.self) { pick in
                    Button {
                        write(\.emoji).wrappedValue = pick
                    } label: {
                        Text(verbatim: pick)
                            .font(.title3)
                            .frame(width: 40, height: 40)
                            .background(
                                Circle().fill(emoji == pick
                                              ? Theme.colors.accent.opacity(0.18)
                                              : Theme.colors.surface)
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
        // The note is prose, not a word: it grows down the page as it is written, and it
        // gets the keyboard's help — the corrections and the sentence capital that would
        // MISSPELL a stored word are simply right in a sentence about one.
        let prose = field == .comment
        return VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            Text(label)
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
            between()
            TextField(text: text, axis: prose ? .vertical : .horizontal) { EmptyView() }
                .lineLimit(prose ? 2...5 : 1...1)
                .font(Theme.typography.body)
                .foregroundStyle(Theme.colors.textPrimary)
                .autocorrectionDisabled(!prose)
                // why: a word is not a sentence — the automatic capital put one on a
                // Swahili noun, which is simply the wrong spelling of the word being
                // stored. Whoever writes German capitalizes it themselves.
                .textInputAutocapitalization(prose ? .sentences : .never)
                .submitLabel(field == .comment ? .done : .next)
                .focused($focus, equals: field)
                .onSubmit { advance(from: field) }
                .padding(.horizontal, Theme.spacing.lg)
                .padding(.vertical, prose ? Theme.spacing.md : 0)
                .frame(minHeight: 52) // card-parity: the field's own height, not a card reserve
                .background(
                    RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous)
                        .fill(Theme.colors.surface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous)
                        .strokeBorder(Theme.colors.separator, lineWidth: 1)
                )
        }
    }

    private func advance(from field: Field) {
        switch field {
        case .known: focus = .learning
        case .learning: focus = .emoji
        case .emoji: focus = .comment
        case .comment: focus = nil
        }
    }
}
