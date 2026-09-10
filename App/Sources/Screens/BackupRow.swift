import SwiftUI
import UniformTypeIdentifiers

/// Carrying the boxes across a reinstall or to another phone: every language's progress
/// out to one file, and back in (`AppModel+Backup`).
///
/// A picked file is read whole before anything is asked, so the confirmation only ever
/// stands over a restore that can land.
struct BackupRow: View {
    let model: AppModel

    @State private var exportFile: BackupFile?
    @State private var importing = false
    @State private var pending: [String: String]?
    @State private var failure: LocalizedStringKey?

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            Text("settings.backup.title")
                .font(Theme.typography.headline)
                .foregroundStyle(Theme.colors.textPrimary)
            HStack(spacing: Theme.spacing.lg) {
                exportButton
                importButton
            }
            Text("settings.backup.hint")
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
        }
        .confirmationDialog("settings.backup.confirm \(pendingNames)",
                            isPresented: shown($pending), titleVisibility: .visible,
                            presenting: pending) { documents in
            Button("settings.backup.replace", role: .destructive) {
                Task {
                    do { try await model.restore(documents) } catch { failure = "settings.backup.importFailed" }
                }
            }
            Button("common.cancel", role: .cancel) {}
        }
        .alert(failure ?? "", isPresented: shown($failure)) {
            Button("common.done", role: .cancel) {}
        }
    }

    // why: each picker hangs off its own button — two file panels on one view
    // leave one of them unable to present.
    private var exportButton: some View {
        Button {
            Task {
                do { exportFile = BackupFile(text: try await model.backupJSON()) }
                catch { failure = "settings.backup.exportFailed" }
            }
        } label: {
            Label("settings.backup.export", systemImage: "square.and.arrow.up")
                .font(Theme.typography.subheadline)
        }
        .fileExporter(isPresented: shown($exportFile), document: exportFile, contentType: .json,
                      defaultFilename: "Spross-\(Date.now.formatted(.iso8601.year().month().day()))") { result in
            if case .failure = result { failure = "settings.backup.exportFailed" }
        }
    }

    private var importButton: some View {
        Button {
            importing = true
        } label: {
            Label("settings.backup.import", systemImage: "square.and.arrow.down")
                .font(Theme.typography.subheadline)
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json]) { result in
            Task { await read(result) }
        }
    }

    private var pendingNames: String {
        (pending?.keys.sorted() ?? [])
            .map { LanguageNames.native($0, catalog: model.catalog) }
            .joined(separator: ", ")
    }

    private func read(_ result: Result<URL, any Error>) async {
        guard case .success(let url) = result else { return }
        let scoped = url.startAccessingSecurityScopedResource()
        let json = try? String(contentsOf: url, encoding: .utf8)
        if scoped { url.stopAccessingSecurityScopedResource() }
        do {
            pending = try await model.readBackup(json ?? "")
        } catch {
            failure = "settings.backup.importFailed"
        }
    }

    /// A presentation flag over an optional: shown while it holds a value, emptied on dismiss.
    private func shown<T>(_ value: Binding<T?>) -> Binding<Bool> {
        Binding(get: { value.wrappedValue != nil }, set: { if !$0 { value.wrappedValue = nil } })
    }
}

/// The backup text as the exporter writes it.
private struct BackupFile: FileDocument {
    static let readableContentTypes: [UTType] = [.json]
    let text: String

    init(text: String) { self.text = text }

    init(configuration: ReadConfiguration) throws {
        text = String(decoding: configuration.file.regularFileContents ?? Data(), as: UTF8.self)
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}
