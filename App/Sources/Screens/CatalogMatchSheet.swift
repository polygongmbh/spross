import SwiftUI
import SprossKern

/// The catalog catching up with the words the learner had to write themselves.
///
/// A word is written by hand because the catalog had none for it; the catalog grows, and
/// the word lands in one eventually. This is where the two meet: every own word the
/// catalog now has a word for, beside the catalog's own writing of it, and one button
/// that moves the progress across (`CatalogMatches`, `BoxEngine.mergeOwnWord`).
///
/// Nothing merges unasked. The words where both halves agree arrive ticked — there is
/// nothing left to judge about those — and a match on one half only waits to be read:
/// the catalog says the other half differently, and which of the two is right is the
/// learner's call, made by looking at both.
struct CatalogMatchSheet: View {
    let model: AppModel

    @Environment(\.dismiss) private var dismiss
    /// Taken once when the sheet opens: building it walks every card in the box.
    @State private var matches: [CatalogMatch]?
    /// Ticked own-word ids. Set from the matches, then the learner's.
    @State private var picked: Set<String> = []

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Theme.spacing.lg) {
                    if let matches {
                        if matches.isEmpty { emptyLine } else { runs(matches); mergeButton }
                    }
                }
                .padding(Theme.spacing.xl)
            }
            .background(Theme.colors.background.ignoresSafeArea())
            .navigationTitle("box.own.match.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.done") { dismiss() }
                }
            }
        }
        .tint(Theme.colors.accent)
        .onAppear(perform: look)
    }

    private var emptyLine: some View {
        Text("box.own.match.none")
            .font(Theme.typography.body)
            .foregroundStyle(Theme.colors.textSecondary)
    }

    /// Kern hands the list back with the whole matches leading, so a run of one side is
    /// one heading — and a side kern grows later heads itself rather than going unshown.
    @ViewBuilder
    private func runs(_ matches: [CatalogMatch]) -> some View {
        VStack(alignment: .leading, spacing: Theme.spacing.lg) {
            ForEach(grouped(matches)) { run in
                VStack(alignment: .leading, spacing: Theme.spacing.xs) {
                    Text(run.title)
                        .font(Theme.typography.caption)
                        .foregroundStyle(Theme.colors.textSecondary)
                    ForEach(run.matches, id: \.word.id) { row($0) }
                }
            }
        }
    }

    /// One heading's worth. The id is the run's place, not its title: `LocalizedStringKey`
    /// is Equatable but not Hashable, so it cannot be an id.
    private struct Run: Identifiable {
        let id: Int
        let title: LocalizedStringKey
        var matches: [CatalogMatch]
    }

    private func grouped(_ matches: [CatalogMatch]) -> [Run] {
        var runs: [Run] = []
        for match in matches {
            let title = heading(match.side)
            if runs.last?.title == title { runs[runs.count - 1].matches.append(match) }
            else { runs.append(Run(id: runs.count, title: title, matches: [match])) }
        }
        return runs
    }

    /// The sides are kern's; naming them is ours. The two one-sided ones read alike to
    /// the learner — the catalog says the other half differently — so they share a heading.
    private func heading(_ side: MatchSide) -> LocalizedStringKey {
        side == .both ? "box.own.match.group.whole" : "box.own.match.group.half"
    }

    /// The catalog's word, and under it the learner's own where the two are written
    /// differently — which is what a one-sided match has to be read for before it is
    /// ticked. Where they are written alike there is nothing under it: a line repeating
    /// the one above it is a line nobody reads twice.
    private func row(_ match: CatalogMatch) -> some View {
        let catalog = model.catalogText(match)
        let written = model.writtenText(match)
        return SelectionRow(
            title: Text(verbatim: catalog),
            caption: written == catalog ? nil : Text(verbatim: written),
            mark: .many,
            selected: picked.contains(match.word.id),
        ) {
            toggle(match.word.id)
        }
    }

    private var mergeButton: some View {
        Button {
            model.merge(kept)
            dismiss()
        } label: {
            Text("box.own.match.merge \(kept.count)").frame(maxWidth: .infinity)
        }
        .buttonStyle(PrimaryButtonStyle())
        .disabled(kept.isEmpty)
    }

    private var kept: [CatalogMatch] {
        (matches ?? []).filter { picked.contains($0.word.id) }
    }

    private func toggle(_ id: String) {
        if picked.contains(id) { picked.remove(id) } else { picked.insert(id) }
    }

    private func look() {
        let found = model.catalogMatches()
        matches = found
        picked = Set(found.filter { $0.side == .both }.map { $0.word.id })
    }
}
