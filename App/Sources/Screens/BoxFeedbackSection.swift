import SwiftUI
import SprossKern

/// What the learner has to say back about the catalog: the words they had to write
/// down themselves with only one half, and the problems they filed against words
/// that are in it.
///
/// It sits at the bottom of the Box, after the shelves, and appears only once there
/// is something in it — an empty complaints box is furniture. Reported words keep
/// their place on their own shelf (`BoxCardRow` flags them there); what this section
/// lists are the SUGGESTIONS, which no shelf can show because a word written in one
/// language joins nothing and is never scheduled.
///
/// The two actions take the same lot two ways: onto the clipboard, or into a mail to
/// whoever maintains the catalog. Both offer "everything" or "only what is new",
/// and both mark the copy taken, so the next "new" means what it says.
struct BoxFeedbackSection: View {
    let model: AppModel

    @Environment(\.openURL) private var openURL

    var body: some View {
        if model.hasFeedback(onlyNew: false) {
            VStack(alignment: .leading, spacing: DL.Space.l) {
                Text("feedback.title")
                    .font(DL.Fonts.title)
                    .foregroundStyle(Color.dlTextPrimary)

                VStack(alignment: .leading, spacing: DL.Space.l) {
                    if !model.suggestions.isEmpty {
                        suggestionList
                        Divider().overlay(Color.dlSeparator)
                    }
                    actions
                }
                .padding(DL.Space.l)
                .background(
                    RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                        .fill(Color.dlSurface)
                )
                .dlCardShadow()
            }
        }
    }

    private var suggestionList: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text("feedback.suggestions")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
            ForEach(model.suggestions, id: \.id) { word in
                suggestionRow(word)
            }
        }
    }

    private func suggestionRow(_ word: OwnWord) -> some View {
        HStack(spacing: DL.Space.m) {
            Text(verbatim: word.emoji ?? OwnWords.shared.EMOJI)
                .font(.title3)
                .accessibilityHidden(true)
            Text(verbatim: model.suggestionText(word))
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextPrimary)
                .lineLimit(1)
            Spacer(minLength: DL.Space.s)
            // why: it is not a shortcoming of the word, it is the whole point of the
            // entry — the half that is missing is what the catalog owes.
            Text("feedback.needsTranslation")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
        }
        .padding(.horizontal, DL.Space.m)
        .padding(.vertical, DL.Space.xs + 2)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
                .fill(Color.dlSurfaceTint)
        )
        .contextMenu {
            Button("box.ownWords.remove", systemImage: "trash", role: .destructive) {
                model.removeOwnWord(word.id)
            }
        }
    }

    @ViewBuilder
    private var actions: some View {
        HStack(spacing: DL.Space.l) {
            scopedButton("feedback.copy", icon: "doc.on.doc") { onlyNew in
                UIPasteboard.general.string = model.ownWordsText(onlyNew: onlyNew)
                model.markExported()
            }
            scopedButton("feedback.send", icon: "envelope") { onlyNew in
                guard let url = model.reportMailURL(onlyNew: onlyNew) else { return }
                openURL(url)
                model.markExported()
            }
        }
    }

    /// One action, offered over the whole lot or only what is new. Until a copy has
    /// ever been taken the two would be the same list, so it stays a plain button
    /// and asks nothing.
    @ViewBuilder
    private func scopedButton(_ title: LocalizedStringKey, icon: String,
                              run: @escaping (Bool) -> Void) -> some View {
        if model.hasExportedBefore {
            Menu {
                Button("feedback.scope.new") { run(true) }
                    .disabled(!model.hasFeedback(onlyNew: true))
                Button("feedback.scope.all") { run(false) }
            } label: {
                Label(title, systemImage: icon)
                    .font(DL.Fonts.subheadline)
                    .foregroundStyle(Color.dlAccent)
            }
        } else {
            Button { run(false) } label: {
                Label(title, systemImage: icon)
                    .font(DL.Fonts.subheadline)
                    .foregroundStyle(Color.dlAccent)
            }
        }
    }
}
