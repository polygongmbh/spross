import SprossKern
import SwiftUI

/// The first run's way around the pair: a backup from another phone brings its own,
/// so a learner who has one skips the pages and lands in the restored box
/// (`AppModel.restore`).
///
/// No confirmation stands over it, unlike the settings' import (`BackupRow`):
/// a phone with no box yet has nothing for the file to replace.
struct OnboardingImport: View {
    let model: AppModel
    /// The known language picked so far — the pair's other half where the file names none.
    let source: String

    @State private var importing = false
    @State private var failed = false

    var body: some View {
        Button {
            importing = true
        } label: {
            LinkLabel("onboarding.import", icon: "square.and.arrow.down", font: Theme.typography.caption)
        }
        .buttonStyle(.plain)
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json]) { result in
            Task { await read(result) }
        }
        .alert("settings.backup.importFailed", isPresented: $failed) {
            Button("common.done", role: .cancel) {}
        }
    }

    private func read(_ result: Result<URL, any Error>) async {
        guard case .success(let url) = result else { return }
        let scoped = url.startAccessingSecurityScopedResource()
        let json = try? String(contentsOf: url, encoding: .utf8)
        if scoped { url.stopAccessingSecurityScopedResource() }
        do {
            try await model.restore(try await model.readBackup(json ?? ""), firstRunSource: source)
        } catch {
            failed = true
        }
    }
}
