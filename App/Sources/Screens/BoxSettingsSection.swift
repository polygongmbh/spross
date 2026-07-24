import SwiftUI
import SprossKern

/// Settings block at the bottom of the Box tab: known language (source),
/// learning language (target, one box each), daily new-card budget, reset.
/// The profile persists in UserDefaults + the box document; the budget in
/// `BoxState.config`.
struct BoxSettingsSection: View {
    let model: AppModel

    @State private var confirmingReset = false
    @Environment(\.locale) private var locale

    var body: some View {
        VStack(alignment: .leading, spacing: DL.Space.l) {
            Text("Einstellungen")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)

            VStack(alignment: .leading, spacing: DL.Space.l) {
                sourceRow
                Divider().overlay(Color.dlSeparator)
                targetRow
                Divider().overlay(Color.dlSeparator)
                maxLearningRow
                Divider().overlay(Color.dlSeparator)
                resetRow
            }
            .padding(DL.Space.l)
            .background(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .fill(Color.dlSurface)
            )
            .dlCardShadow()

            aboutFooter
        }
    }

    // MARK: About / feedback

    private static let feedbackAddress = "feedback@spross.net"

    private var versionText: String {
        // why: the build number is always 1 here — showing "(1)" reads odd;
        // the marketing version alone identifies feedback mails fine.
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?"
        return "Spross v\(version)"
    }

    private var aboutFooter: some View {
        VStack(spacing: DL.Space.s) {
            Text(versionText)
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
                .monospacedDigit()
            if let url = feedbackURL {
                Link(destination: url) {
                    Label("Feedback senden", systemImage: "envelope")
                        .font(DL.Fonts.subheadline)
                        .foregroundStyle(Color.dlAccent)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, DL.Space.m)
    }

    private var feedbackURL: URL? {
        let subject = versionText.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        return URL(string: "mailto:\(Self.feedbackAddress)?subject=\(subject)")
    }

    private func languageName(_ code: String) -> String {
        LanguageNames.native(code, catalog: model.catalog)
    }

    // MARK: Rows

    /// "Ich spreche": the known language. Switching re-joins in place —
    /// schedules are keyed by card id, so all progress survives.
    private var sourceRow: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text("Ich spreche")
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
            Picker("Ich spreche", selection: sourceBinding) {
                ForEach(availableSources, id: \.self) { candidate in
                    Text(verbatim: languageName(candidate)).tag(candidate)
                }
            }
            .pickerStyle(.segmented)
            Text("Beim Wechsel der Ausgangssprache bleibt dein Fortschritt erhalten.")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    /// "Ich lerne": the target language — one box per target.
    private var targetRow: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text("Ich lerne")
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
            Picker("Ich lerne", selection: targetBinding) {
                ForEach(availableTargets) { candidate in
                    Text(verbatim: languageName(candidate.code)).tag(candidate.code)
                }
            }
            .pickerStyle(.segmented)
            Text("Jede Sprache hat ihre eigene Box.")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    private var maxLearningRow: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Stepper(value: maxLearningBinding, in: 0...30) {
                HStack {
                    Text("Karten gleichzeitig im Lernen")
                        .font(DL.Fonts.headline)
                        .foregroundStyle(Color.dlTextPrimary)
                    Spacer()
                    Text("\(maxLearningBinding.wrappedValue)")
                        .font(DL.Fonts.statValue)
                        .foregroundStyle(Color.dlAccent)
                        .monospacedDigit()
                }
            }
            Text("So viele neue Karten übst du parallel — neue kommen nach, sobald welche sitzen.")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    /// Fresh start with the CURRENT catalog content.
    private var resetRow: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Button(role: .destructive) {
                confirmingReset = true
            } label: {
                Text("Box zurücksetzen …")
                    .font(DL.Fonts.headline)
            }
            .confirmationDialog(
                "Alle Lernfortschritte für \(targetName) löschen und neu mit den ersten Wörtern beginnen?",
                isPresented: $confirmingReset,
                titleVisibility: .visible
            ) {
                Button("Zurücksetzen", role: .destructive) {
                    Task { await model.resetBox() }
                }
                Button("Abbrechen", role: .cancel) {}
            }
            Text("Löscht Fortschritt und Verlauf dieser Box — die Inhalte bleiben.")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    // MARK: Choices & bindings

    private var targetName: String {
        model.targetLanguage.map(languageName) ?? "?"
    }

    /// Sources that can learn the CURRENT target (the pair must stay valid).
    private var availableSources: [String] {
        guard let catalog = model.catalog, let target = model.targetLanguage else { return [] }
        return model.coveredSources(catalog).filter { source in
            catalog.availableTargets(source: source).contains { $0.code == target }
        }
    }

    private var availableTargets: [AvailableTarget] {
        model.catalog?.availableTargets(source: model.sourceLanguage) ?? []
    }

    private var sourceBinding: Binding<String> {
        Binding(
            get: { model.sourceLanguage },
            set: { model.switchSource($0) }
        )
    }

    private var targetBinding: Binding<String> {
        Binding(
            get: { model.targetLanguage ?? "" },
            set: { model.switchTarget($0) }
        )
    }

    private var maxLearningBinding: Binding<Int> {
        Binding(
            get: { Int(model.box?.config.maxLearning ?? 8) },
            set: { model.setMaxLearning($0) }
        )
    }
}
