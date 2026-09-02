import SwiftUI
import SprossKern

/// Taking the box into a conversation the app does not host, and bringing back what
/// the conversation turned up.
///
/// The app ships no assistant and knows the name of none: it hands over a text and a
/// share sheet, and whichever chat the learner already pays for takes it from there.
/// What that text may say is Kern's (`Briefing`) — this sheet shows only how much of
/// the box is in it, because a wall of prompt scrolling past is not a preview.
///
/// The way back is the half that pays: a conversation turns up words no catalog has,
/// the assistant is asked to list them, and pasting that list here reads them into
/// own words (`Harvest`). Nothing is taken in unasked — the parsed words arrive as a
/// list to keep or drop.
struct BriefingSheet: View {
    let model: AppModel

    @Environment(\.dismiss) private var dismiss
    /// Taken once when the sheet opens: building it walks every card in the box.
    @State private var briefing: Briefing?
    @State private var copied = false
    @State private var harvested: [BriefWord] = []
    @State private var dropped: Set<String> = []
    @State private var pasteWasEmpty = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Theme.spacing.xl) {
                    if let briefing {
                        handOver(briefing)
                        Divider().overlay(Theme.colors.separator)
                        readBack
                    }
                }
                .padding(Theme.spacing.xl)
            }
            .background(Theme.colors.background.ignoresSafeArea())
            .navigationTitle("briefing.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.done") { dismiss() }
                }
            }
        }
        .tint(Theme.colors.accent)
        .onAppear { briefing = model.makeBriefing() }
    }

    // MARK: - Taking it out

    @ViewBuilder
    private func handOver(_ briefing: Briefing) -> some View {
        VStack(alignment: .leading, spacing: Theme.spacing.md) {
            Text("briefing.intro")
                .font(Theme.typography.body)
                .foregroundStyle(Theme.colors.textPrimary)
            tally(briefing)
            HStack(spacing: Theme.spacing.lg) {
                Button {
                    UIPasteboard.general.string = briefing.text
                    copied = true
                } label: {
                    Label(copied ? "briefing.copied" : "briefing.copy",
                          systemImage: copied ? "checkmark" : "doc.on.doc")
                        .font(Theme.typography.subheadline)
                }
                ShareLink(item: briefing.text) {
                    Label("briefing.share", systemImage: "square.and.arrow.up")
                        .font(Theme.typography.subheadline)
                }
            }
        }
    }

    /// What the brief carries, as three counts. The words themselves are not shown:
    /// they are already the box, listed everywhere else in it.
    private func tally(_ briefing: Briefing) -> some View {
        VStack(alignment: .leading, spacing: Theme.spacing.xs) {
            countLine("briefing.tally.free \(Int(briefing.freeCount))", when: briefing.freeCount > 0)
            countLine("briefing.tally.inPlay \(briefing.inPlay.count)", when: !briefing.inPlay.isEmpty)
            countLine("briefing.tally.new \(briefing.newWords.count)", when: !briefing.newWords.isEmpty)
        }
    }

    @ViewBuilder
    private func countLine(_ key: LocalizedStringKey, when shown: Bool) -> some View {
        if shown {
            Text(key)
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
        }
    }

    // MARK: - Bringing it back

    private var readBack: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.md) {
            Text("briefing.return.title")
                .font(Theme.typography.headline)
                .foregroundStyle(Theme.colors.textPrimary)
            Text("briefing.return.explainer")
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
            Button {
                paste()
            } label: {
                Label("briefing.return.paste", systemImage: "doc.on.clipboard")
                    .font(Theme.typography.subheadline)
            }
            if pasteWasEmpty {
                Text("briefing.return.empty")
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.textSecondary)
            }
            if !harvested.isEmpty {
                harvestList
                keepButton
            }
        }
    }

    private var harvestList: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.xs) {
            ForEach(harvested, id: \.target) { word in
                Button {
                    toggle(word)
                } label: {
                    HStack(spacing: Theme.spacing.md) {
                        Image(systemName: dropped.contains(word.target) ? "circle" : "checkmark.circle.fill")
                            .foregroundStyle(dropped.contains(word.target)
                                             ? Theme.colors.textSecondary : Theme.colors.accent)
                        VStack(alignment: .leading, spacing: 0) {
                            Text(verbatim: word.target)
                                .font(Theme.typography.body)
                                .foregroundStyle(Theme.colors.textPrimary)
                            Text(verbatim: word.source)
                                .font(Theme.typography.caption)
                                .foregroundStyle(Theme.colors.textSecondary)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, Theme.spacing.md)
                    .padding(.vertical, Theme.spacing.xs + 2)
                    .background(
                        RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous)
                            .fill(Theme.colors.surfaceTint)
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var keepButton: some View {
        Button {
            model.keepHarvested(kept)
            dismiss()
        } label: {
            Text("briefing.return.keep \(kept.count)").frame(maxWidth: .infinity)
        }
        .buttonStyle(PrimaryButtonStyle())
        .disabled(kept.isEmpty)
    }

    private var kept: [BriefWord] { harvested.filter { !dropped.contains($0.target) } }

    private func toggle(_ word: BriefWord) {
        if dropped.contains(word.target) { dropped.remove(word.target) } else { dropped.insert(word.target) }
    }

    /// Read the clipboard through Kern, which forgives the assistant its formatting and
    /// drops what the box already holds. Everything it finds arrives already ticked:
    /// the learner asked for this list, so keeping it is the answer to expect.
    private func paste() {
        let text = UIPasteboard.general.string ?? ""
        let found = model.harvest(from: text)
        harvested = found
        dropped = []
        pasteWasEmpty = found.isEmpty
    }
}
