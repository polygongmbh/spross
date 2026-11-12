import SwiftUI
import SprossKern
import UniformTypeIdentifiers

/// The pairs a paste or a word-list file carried, headed by where each stands against the
/// box, ticked a word at a time — shared by the chat sheet (`BriefingSheet`) and the
/// settings import (`WordListImportSheet`).
///
/// Everything is shown and sorted, never filtered: only the ones the box has nothing like
/// arrive ticked (`HarvestWord.initiallyPicked`).
struct HarvestReview: View {
    let harvested: [HarvestWord]
    @Binding var picked: Set<String>
    let onKeep: () -> Void

    var body: some View {
        // why: lazy — a word-list file can carry thousands of rows.
        LazyVStack(alignment: .leading, spacing: Theme.spacing.xs) {
            ForEach(runs) { run in
                Text(run.title)
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.textSecondary)
                    .padding(.top, run.id == 0 ? 0 : Theme.spacing.md)
                ForEach(run.words, id: \.word.target) { row($0) }
            }
            keepButton.padding(.top, Theme.spacing.md)
        }
    }

    /// One heading's worth of the list. The id is the run's place in the list, not its
    /// title: `LocalizedStringKey` is Equatable but not Hashable, so it cannot be an id.
    private struct Run: Identifiable {
        let id: Int
        let title: LocalizedStringKey
        var words: [HarvestWord]
    }

    /// Kern's list arrives grouped (`Harvest.read`), so a run of one kind is one heading —
    /// and a kind kern grows later heads itself rather than going unshown.
    private var runs: [Run] {
        var runs: [Run] = []
        for found in harvested {
            let title = Self.heading(found.kind)
            if runs.last?.title == title { runs[runs.count - 1].words.append(found) }
            else { runs.append(Run(id: runs.count, title: title, words: [found])) }
        }
        return runs
    }

    /// Which heading a group wears. The kinds are kern's; naming them is ours.
    /// `.theNew` is Kern's `New` — the bridge renames what would collide with `+new`.
    private static func heading(_ kind: HarvestKind) -> LocalizedStringKey {
        switch kind {
        case .theNew: return "briefing.group.new"
        case .near: return "briefing.group.near"
        case .held: return "briefing.group.held"
        }
    }

    /// The shared row (`SelectionRow`), which is what the catalog merge list also ticks
    /// with: two sheets that tick words a word at a time have no business wearing two marks.
    private func row(_ found: HarvestWord) -> some View {
        SelectionRow(
            title: Text(verbatim: found.word.target),
            caption: Text(verbatim: gloss(found)),
            mark: .many,
            selected: picked.contains(found.word.target),
        ) {
            if picked.contains(found.word.target) { picked.remove(found.word.target) }
            else { picked.insert(found.word.target) }
        }
    }

    /// The gloss, and after it the box's own word where this one leans on it — which is
    /// the whole reason the row came up unticked, said in the row rather than the heading.
    private func gloss(_ found: HarvestWord) -> String {
        guard found.kind == .near, let match = found.match else { return found.word.source }
        return "\(found.word.source) · ≈ \(match)"
    }

    /// Repeated below the list for a short one, where reaching up to the bar is the extra step.
    private var keepButton: some View {
        Button(action: onKeep) {
            Text("briefing.return.keep \(harvested.kept(picked).count)").frame(maxWidth: .infinity)
        }
        .buttonStyle(PrimaryButtonStyle())
        .disabled(harvested.kept(picked).isEmpty)
    }
}

extension Array where Element == HarvestWord {
    /// The ones the learner asked for — those the box has nothing like.
    var initiallyPicked: Set<String> {
        Set(filter { $0.kind == .theNew }.map { $0.word.target })
    }

    func kept(_ picked: Set<String>) -> [BriefWord] {
        filter { picked.contains($0.word.target) }.map(\.word)
    }
}

/// What a word-list file may be: another app's export or a saved chat answer.
enum WordListFiles {
    static let types: [UTType] = [.plainText, .commaSeparatedText, .tabSeparatedText, .utf8PlainText, .text]

    /// A picked file's text, read inside its security scope; nil when it cannot be read.
    static func text(of result: Result<URL, any Error>) -> String? {
        guard case .success(let url) = result else { return nil }
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        return (try? String(contentsOf: url, encoding: .utf8))
            ?? (try? String(contentsOf: url, encoding: .isoLatin1))
    }
}

extension View {
    /// Asks before a file past `WordListFile.LARGE_LINES` is read, against an unexpected wait.
    func largeWordListAlert(_ file: Binding<String?>, read: @escaping (String) -> Void) -> some View {
        let lines = file.wrappedValue.map { Int(WordListFile.shared.lineCount(text: $0)) } ?? 0
        return alert("import.large \(lines.formatted())", isPresented: Binding(presenting: file)) {
            Button("import.large.read") { if let text = file.wrappedValue { read(text) } }
            Button("common.cancel", role: .cancel) {}
        }
    }
}
