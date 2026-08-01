import SwiftUI
import SprossKern

/// Settings block at the bottom of the Box tab: known language (source),
/// learning language (target, one box each), daily new-card budget, reset.
/// The profile persists in UserDefaults + the box document; the budget in
/// `BoxState.config`.
struct BoxSettingsSection: View {
    let model: AppModel

    @State private var confirmingReset = false
    @State private var creditsPresented = false
    @Environment(\.locale) private var locale

    var body: some View {
        VStack(alignment: .leading, spacing: DL.Space.l) {
            Text("settings.title")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)

            VStack(alignment: .leading, spacing: DL.Space.l) {
                profileRow
                Divider().overlay(Color.dlSeparator)
                audioRow
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
            creditsButton
        }
        .frame(maxWidth: .infinity)
        .padding(.top, DL.Space.m)
        .sheet(isPresented: $creditsPresented) {
            // why: a sheet leaves the chrome language behind, and credits are
            // chrome — hand it the locale the settings block renders in.
            CreditsView(model: model).environment(\.locale, locale)
        }
    }

    /// Attribution for the bundled pronunciation recordings — a licence
    /// obligation, not a courtesy: BY and BY-SA both ask for the speaker
    /// by name, so the surface ships with the audio.
    private var creditsButton: some View {
        Button {
            creditsPresented = true
        } label: {
            Label("settings.credits", systemImage: "waveform")
                .font(DL.Fonts.subheadline)
                .foregroundStyle(Color.dlAccent)
        }
    }

    private var feedbackURL: URL? {
        let subject = versionText.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        return URL(string: "mailto:\(Self.feedbackAddress)?subject=\(subject)")
    }

    // MARK: Rows

    /// The pair, side by side. Both menus list every covered language, so
    /// picking the one the OTHER side holds swaps them; switching the known
    /// language re-joins in place (schedules are keyed by card id, so all
    /// progress survives), and each target keeps its own box.
    private var profileRow: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            HStack(alignment: .top, spacing: DL.Space.l) {
                languageMenu(title: "settings.source.title",
                             selection: sourceBinding, choices: sourceChoices)
                languageMenu(title: "settings.target.title",
                             selection: targetBinding, choices: targetChoices)
            }
            Text("settings.profile.hint")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    /// A dropdown per side. The collapsed label carries the English exonym
    /// alone — it has half a row to live in — while the menu itself has room
    /// for "🇺🇦 Українська · Ukrainian".
    private func languageMenu(title: LocalizedStringKey, selection: Binding<String>,
                              choices: [String]) -> some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text(title)
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
            Menu {
                Picker(title, selection: selection) {
                    ForEach(choices, id: \.self) { candidate in
                        Text(verbatim: LanguageNames.pickerRow(candidate, catalog: model.catalog))
                            .tag(candidate)
                    }
                }
            } label: {
                HStack(spacing: DL.Space.xs) {
                    Text(verbatim: LanguageNames.pickerLabel(selection.wrappedValue,
                                                             catalog: model.catalog))
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.caption2)
                }
                .foregroundStyle(Color.dlTextPrimary)
                .padding(.vertical, DL.Space.s)
                .padding(.horizontal, DL.Space.m)
                .background(
                    RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                        .fill(Color.dlSurfaceTint)
                )
            }
            .accessibilityLabel(Text(title))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// The same switch the session's top bar carries, and the place the
    /// tap-to-replay gesture is disclosed — the card itself grows no
    /// affordance for it, so the hint line is where it is named.
    private var audioRow: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Toggle(isOn: readAloudBinding) {
                Text("settings.audio.title")
                    .font(DL.Fonts.headline)
                    .foregroundStyle(Color.dlTextPrimary)
            }
            .tint(.dlAccent)
            Text("settings.audio.hint")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    /// Bound to NOT-muted: the row names the feature, the flag stores the
    /// exception. It lives in UserDefaults, one flag for the device — not in
    /// the box, where the product calibration would reset it on every load,
    /// and not per target language.
    private var readAloudBinding: Binding<Bool> {
        Binding(
            get: { !Pronouncer.shared.muted },
            set: { Pronouncer.shared.muted = !$0 }
        )
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
}
