import SwiftUI
import SprossKern

/// Taking the box into a conversation the app does not host, and bringing back what
/// the conversation turned up.
///
/// The app ships no assistant and knows the name of none: it hands over a text and a
/// share sheet, and whichever chat the learner already pays for takes it from there.
/// What that text may say is Kern's (`Briefing`) and is never shown here: 7 KB of prompt
/// scrolling past is a wall, not a preview. What the sheet spends its words on instead is
/// the three moves the loop takes; the box's counts would only name the box back at the
/// learner, who already has it.
///
/// The way back is the half that pays: a conversation turns up words no catalog has,
/// the assistant is asked to list them, and pasting that list here — or picking it as a
/// file — reads them into own words (`Harvest`, `WordListFile`). Nothing is taken in unasked: every pair the paste carried
/// is shown under the heading for where it stands against the box, and only the ones
/// the box has nothing like arrive ticked.
struct BriefingSheet: View {
    let model: AppModel

    @Environment(\.dismiss) private var dismiss
    @Environment(\.locale) private var locale
    /// Taken once when the sheet opens: building it walks every card in the box.
    @State private var briefing: Briefing?
    @State private var copied = false
    @State private var harvested: [HarvestWord] = []
    /// Ticked target forms. Set from the paste, then the learner's — a word dropped
    /// and one never offered are the same answer, so one set holds both.
    @State private var picked: Set<String> = []
    /// What the last paste or file turned out empty, named under the buttons.
    @State private var emptyNote: LocalizedStringKey?
    @State private var pickingFile = false
    /// A file past `WordListFile.LARGE_LINES`, held until the learner says to read it.
    @State private var largeFile: String?

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
            // why: the paste can grow a long word list, so the accept action sits in
            // the bar rather than below it — nobody scrolls the whole list to keep it.
            .presentationActions(close: BarAction("common.done", .close) { dismiss() },
                                 confirm: harvested.isEmpty ? nil : keepAction)
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
                    Label(copied ? "briefing.copied" : "common.copy",
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
            Text(verbatim: "\(number.counted(locale)).")
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
            Text("briefing.return.lead")
                .font(Theme.typography.subheadline)
                .foregroundStyle(Theme.colors.textSecondary)
            HStack(spacing: Theme.spacing.lg) {
                Button {
                    show(model.harvest(from: UIPasteboard.general.string ?? ""), empty: "briefing.return.empty")
                } label: {
                    Label("briefing.return.paste", systemImage: "doc.on.clipboard")
                        .font(Theme.typography.subheadline)
                }
                Button {
                    pickingFile = true
                } label: {
                    Label("briefing.return.file", systemImage: "doc")
                        .font(Theme.typography.subheadline)
                }
                .fileImporter(isPresented: $pickingFile, allowedContentTypes: WordListFiles.types) { result in
                    guard let text = WordListFiles.text(of: result) else { return emptyNote = "briefing.return.fileEmpty" }
                    if Int(WordListFile.shared.lineCount(text: text)) > Int(WordListFile.shared.LARGE_LINES) { largeFile = text }
                    else { readFile(text) }
                }
            }
            if let emptyNote {
                Text(emptyNote)
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.textSecondary)
            }
            if !harvested.isEmpty {
                HarvestReview(harvested: harvested, picked: $picked, onKeep: keep)
            }
        }
        .largeWordListAlert($largeFile, read: readFile)
    }

    /// Mirrors the keep button below the list in the navigation bar (`.confirmationAction`) —
    /// a list long enough to fill the sheet must never make the accept action a scroll away.
    private var keepAction: BarAction {
        let kept = harvested.kept(picked)
        return BarAction("briefing.return.keep \(kept.count)", .done, enabled: !kept.isEmpty, action: keep)
    }

    private func keep() {
        model.keepHarvested(harvested.kept(picked))
        dismiss()
    }

    private func readFile(_ text: String) {
        show(model.harvest(fromFile: text), empty: "briefing.return.fileEmpty")
    }

    /// What kern read off the clipboard or the file, forgiving the assistant its formatting.
    /// The new ones arrive ticked — the learner asked for exactly those — the rest wait to be asked for.
    private func show(_ found: [HarvestWord], empty: LocalizedStringKey) {
        harvested = found
        picked = found.initiallyPicked
        emptyNote = found.isEmpty ? empty : nil
    }
}
