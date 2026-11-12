import SwiftUI
import SprossKern

/// A word-list file picked in Settings, reviewed before its pairs go in as own words —
/// the same list the chat's way back shows (`HarvestReview`).
struct WordListImportSheet: View {
    let model: AppModel
    let harvested: [HarvestWord]

    @Environment(\.dismiss) private var dismiss
    @State private var picked: Set<String> = []

    var body: some View {
        NavigationStack {
            ScrollView {
                HarvestReview(harvested: harvested, picked: $picked, onKeep: keep)
                    .padding(Theme.spacing.xl)
            }
            .background(Theme.colors.background.ignoresSafeArea())
            .navigationTitle("import.words.title")
            .navigationBarTitleDisplayMode(.inline)
            .presentationActions(close: BarAction("common.cancel", .close) { dismiss() },
                                 confirm: BarAction("briefing.return.keep \(harvested.kept(picked).count)", .done,
                                                    enabled: !harvested.kept(picked).isEmpty, action: keep))
        }
        .tint(Theme.colors.accent)
        .onAppear { picked = harvested.initiallyPicked }
    }

    private func keep() {
        model.keepHarvested(harvested.kept(picked))
        dismiss()
    }
}
