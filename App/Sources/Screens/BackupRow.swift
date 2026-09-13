import SprossKern
import SwiftUI
import UniformTypeIdentifiers

/// Carrying the boxes across a reinstall or to another phone: the progress in every
/// language the learner has one — or in the one on screen — out to a file, and back in
/// (`AppModel+Backup`).
///
/// A picked file is read whole before anything is asked, so the confirmation only ever
/// stands over a restore that can land.
struct BackupRow: View {
    let model: AppModel

    @State private var exportFile: BackupFile?
    /// The languages an export would carry — what the export button offers to narrow to.
    @State private var carried: [String] = []
    @State private var importing = false
    @State private var pending: StoredBoxes?
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
        .task { carried = await model.backupLanguages() }
        .confirmationDialog("settings.backup.confirm \(pendingNames)",
                            isPresented: shown($pending), titleVisibility: .visible,
                            presenting: pending) { imported in
            Button("settings.backup.replace", role: .destructive) {
                Task {
                    do { try await model.restore(imported) } catch { failure = "settings.backup.importFailed" }
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
        exportControl
            .fileExporter(isPresented: shown($exportFile), document: exportFile,
                          contentType: .json, defaultFilename: exportFile?.name ?? "Spross") { result in
                if case .failure = result { failure = "settings.backup.exportFailed" }
            }
    }

    /// A plain button while the file can only say one thing, a choice once the learner has
    /// a second language in the box: the whole box travels to a new phone, one language is
    /// what they hand to someone learning it.
    @ViewBuilder
    private var exportControl: some View {
        if let current = model.targetLanguage, carried.count > 1, carried.contains(current) {
            Menu {
                Button("settings.backup.exportOnly \(LanguageNames.native(current, catalog: model.catalog))") {
                    write(only: current)
                }
                Button("settings.backup.exportAll") { write(only: nil) }
            } label: {
                exportLabel
            }
        } else {
            Button { write(only: nil) } label: { exportLabel }
        }
    }

    private var exportLabel: some View {
        Label("settings.backup.export", systemImage: "square.and.arrow.up")
            .font(Theme.typography.subheadline)
    }

    /// The file the exporter then puts somewhere — named for what it carries, so two of
    /// them in one folder are told apart before either is opened.
    private func write(only: String?) {
        Task {
            let day = Date.now.formatted(.iso8601.year().month().day())
            do {
                exportFile = BackupFile(text: try await model.backupJSON(only: only),
                                        name: "Spross-\(only.map { "\($0)-" } ?? "")\(day)")
            } catch {
                failure = "settings.backup.exportFailed"
            }
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
        (pending?.boxes.keys.sorted() ?? [])
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

/// The backup text as the exporter writes it, under the name it offers for it.
private struct BackupFile: FileDocument {
    static let readableContentTypes: [UTType] = [.json]
    let text: String
    let name: String

    init(text: String, name: String) {
        self.text = text
        self.name = name
    }

    init(configuration: ReadConfiguration) throws {
        text = String(decoding: configuration.file.regularFileContents ?? Data(), as: UTF8.self)
        name = "Spross"
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}
