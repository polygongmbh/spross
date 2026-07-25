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
            Text("settings.title")
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
                    Label("settings.feedback", systemImage: "envelope")
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

    /// Picker rows are "🇩🇪 German" — one neutral English form on both sides,
    /// matching onboarding; sentence chrome (reset dialog) stays native.
    private func pickerName(_ code: String) -> String {
        LanguageNames.pickerRow(code, catalog: model.catalog)
    }

    // MARK: Rows

    /// The known language ("I speak"). Switching re-joins in place —
    /// schedules are keyed by card id, so all progress survives. Picking the
    /// language currently being LEARNED swaps the pair.
    private var sourceRow: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text("settings.source.title")
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
            Picker("settings.source.title", selection: sourceBinding) {
                ForEach(sourceChoices, id: \.self) { candidate in
                    Text(verbatim: pickerName(candidate)).tag(candidate)
                }
            }
            .pickerStyle(.segmented)
            Text("settings.source.hint")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    /// The target language ("I'm learning") — one box per target. Picking the
    /// language currently SPOKEN swaps the pair.
    private var targetRow: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text("settings.target.title")
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
            Picker("settings.target.title", selection: targetBinding) {
                ForEach(targetChoices, id: \.self) { candidate in
                    Text(verbatim: pickerName(candidate)).tag(candidate)
                }
            }
            .pickerStyle(.segmented)
            Text("settings.target.hint")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    private var maxLearningRow: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Stepper(value: maxLearningBinding, in: 0...30) {
                HStack {
                    Text("settings.maxLearning.title")
                        .font(DL.Fonts.headline)
                        .foregroundStyle(Color.dlTextPrimary)
                    Spacer()
                    Text("\(maxLearningBinding.wrappedValue)")
                        .font(DL.Fonts.statValue)
                        .foregroundStyle(Color.dlAccent)
                        .monospacedDigit()
                }
            }
            Text("settings.maxLearning.hint")
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
                Text("settings.reset.button")
                    .font(DL.Fonts.headline)
            }
            .confirmationDialog(
                "settings.reset.confirm \(targetName)",
                isPresented: $confirmingReset,
                titleVisibility: .visible
            ) {
                Button("common.reset", role: .destructive) {
                    Task { await model.resetBox() }
                }
                Button("common.cancel", role: .cancel) {}
            }
            Text("settings.reset.hint")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    // MARK: Choices & bindings

    private var targetName: String {
        model.targetLanguage.map { LanguageNames.native($0, catalog: model.catalog) } ?? "?"
    }

    /// ALL covered sources — including the current target: picking it swaps.
    private var sourceChoices: [String] {
        model.catalog.map { model.coveredSources($0) } ?? []
    }

    /// Learnable targets from the current source PLUS the source itself —
    /// picking the language you speak swaps the pair (mirrors onboarding).
    private var targetChoices: [String] {
        let targets = model.catalog?.availableTargets(source: model.sourceLanguage)
            .map(\.code) ?? []
        return (targets + [model.sourceLanguage]).sorted()
    }

    private var sourceBinding: Binding<String> {
        Binding(
            get: { model.sourceLanguage },
            set: { candidate in
                if candidate == model.targetLanguage {
                    model.swapLanguages()
                } else {
                    model.switchSource(candidate)
                }
            }
        )
    }

    private var targetBinding: Binding<String> {
        Binding(
            get: { model.targetLanguage ?? "" },
            set: { candidate in
                if candidate == model.sourceLanguage {
                    model.swapLanguages()
                } else {
                    model.switchTarget(candidate)
                }
            }
        )
    }

    private var maxLearningBinding: Binding<Int> {
        Binding(
            get: { Int(model.box?.config.maxLearning ?? 8) },
            set: { model.setMaxLearning($0) }
        )
    }
}
