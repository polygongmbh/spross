import SwiftUI
import SprossKern

/// Taking the box into a conversation the app does not host, and bringing back what
/// the conversation turned up.
///
/// The app ships no assistant and knows the name of none: it hands over a text and a
/// share sheet, and whichever chat the learner already pays for takes it from there.
/// What that text may say is Kern's (`Briefing`) and is never shown here: 7 KB of prompt
/// scrolling past is a wall, not a preview. What the sheet spends its words on instead is
/// the three moves the loop takes — the counts that once stood in for the text named the
/// box back at the learner, who already has it.
///
/// The way back is the half that pays: a conversation turns up words no catalog has,
/// the assistant is asked to list them, and pasting that list here reads them into
/// own words (`Harvest`). Nothing is taken in unasked: every pair the paste carried
/// is shown under the heading for where it stands against the box, and only the ones
/// the box has nothing like arrive ticked.
struct BriefingSheet: View {
    let model: AppModel

    @Environment(\.dismiss) private var dismiss
    /// Taken once when the sheet opens: building it walks every card in the box.
    @State private var briefing: Briefing?
    @State private var copied = false
    @State private var harvested: [HarvestWord] = []
    /// Ticked target forms. Set from the paste, then the learner's — a word dropped
    /// and one never offered are the same answer, so one set holds both.
    @State private var picked: Set<String> = []
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
            Text("briefing.lead")
                .font(Theme.typography.body)
                .foregroundStyle(Theme.colors.textPrimary)
            steps
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

    /// The loop in three moves, numbered: take it out, talk, bring the words back.
    private var steps: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            step(1, "briefing.step.copy")
            step(2, "briefing.step.talk")
            step(3, "briefing.step.back")
        }
    }

    private func step(_ number: Int, _ key: LocalizedStringKey) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: Theme.spacing.md) {
            Text(verbatim: "\(number.formatted()).")
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.accent)
            Text(key)
                .font(Theme.typography.subheadline)
                .foregroundStyle(Theme.colors.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
    }

    // MARK: - Bringing it back

    private var readBack: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.md) {
            Text("briefing.return.title")
                .font(Theme.typography.headline)
                .foregroundStyle(Theme.colors.textPrimary)
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
                harvestGroups
                keepButton
            }
        }
    }

    /// Everything the paste carried, headed where the kind turns. Kern's list arrives
    /// grouped (`Harvest.read`), so a run of one kind is one heading — and a kind kern
    /// grows later heads itself rather than going unshown.
    private var harvestGroups: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.lg) {
            ForEach(harvestRuns) { run in
                VStack(alignment: .leading, spacing: Theme.spacing.xs) {
                    blockTitle(run.title)
                    ForEach(run.words, id: \.word.target) { harvestRow($0) }
                }
            }
        }
    }

    /// One heading's worth of a paste. The id is the run's place in the list, not its
    /// title: `LocalizedStringKey` is Equatable but not Hashable, so it cannot be an id.
    private struct HarvestRun: Identifiable {
        let id: Int
        let title: LocalizedStringKey
        var words: [HarvestWord]
    }

    private var harvestRuns: [HarvestRun] {
        var runs: [HarvestRun] = []
        for found in harvested {
            let title = heading(found.kind)
            if runs.last?.title == title { runs[runs.count - 1].words.append(found) }
            else { runs.append(HarvestRun(id: runs.count, title: title, words: [found])) }
        }
        return runs
    }

    /// Which heading a group wears. The kinds are kern's; naming them is ours.
    /// `.theNew` is Kern's `New` — the bridge renames what would collide with `+new`.
    private func heading(_ kind: HarvestKind) -> LocalizedStringKey {
        switch kind {
        case .theNew: return "briefing.group.new"
        case .near: return "briefing.group.near"
        case .held: return "briefing.group.held"
        }
    }

    private func blockTitle(_ key: LocalizedStringKey) -> some View {
        Text(key)
            .font(Theme.typography.caption)
            .foregroundStyle(Theme.colors.textSecondary)
    }

    private func harvestRow(_ found: HarvestWord) -> some View {
        let on = picked.contains(found.word.target)
        return Button {
            toggle(found.word.target)
        } label: {
            HStack(spacing: Theme.spacing.md) {
                Image(systemName: on ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(on ? Theme.colors.accent : Theme.colors.textSecondary)
                VStack(alignment: .leading, spacing: 0) {
                    Text(verbatim: found.word.target)
                        .font(Theme.typography.body)
                        .foregroundStyle(Theme.colors.textPrimary)
                    Text(verbatim: gloss(found))
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

    /// The gloss, and after it the box's own word where this one leans on it — which is
    /// the whole reason the row came up unticked, said in the row rather than the heading.
    private func gloss(_ found: HarvestWord) -> String {
        guard found.kind == .near, let match = found.match else { return found.word.source }
        return "\(found.word.source) · ≈ \(match)"
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

    private var kept: [BriefWord] {
        harvested.filter { picked.contains($0.word.target) }.map(\.word)
    }

    private func toggle(_ target: String) {
        if picked.contains(target) { picked.remove(target) } else { picked.insert(target) }
    }

    /// Read the clipboard through Kern, which forgives the assistant its formatting and
    /// says of each pair what the box already has of it. The new ones arrive ticked —
    /// the learner asked for exactly those — and the rest wait to be asked for.
    private func paste() {
        let found = model.harvest(from: UIPasteboard.general.string ?? "")
        harvested = found
        picked = Set(found.filter { $0.kind == .theNew }.map { $0.word.target })
        pasteWasEmpty = found.isEmpty
    }
}
