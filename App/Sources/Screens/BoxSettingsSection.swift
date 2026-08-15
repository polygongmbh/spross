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

    /// Attribution for the bundled pronunciation recordings — a license
    /// obligation, not a courtesy: BY and BY-SA both ask for the speaker
    /// by name, so the surface ships with the audio. The same sheet carries the
    /// Impressum and the privacy policy, which is why the row names both.
    private var creditsButton: some View {
        Button {
            creditsPresented = true
        } label: {
            Label("settings.credits", systemImage: "info.circle")
                .font(DL.Fonts.subheadline)
                .foregroundStyle(Color.dlAccent)
        }
    }

    private var feedbackURL: URL? {
        let subject = versionText.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        return URL(string: "mailto:\(Legal.contactAddress)?subject=\(subject)")
    }

    // MARK: Rows

    /// The pair, side by side. Neither side hides the other's pick — choosing the
    /// language the OTHER side holds swaps them, wherever that swapped pair is
    /// one the catalog can teach (`LanguageChoices`). Switching the known language
    /// re-joins in place (schedules are keyed by card id, so all progress
    /// survives), and each target keeps its own box.
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
    /// affordance for it, so the hint line is where it is named. It is also the
    /// standing home of the voice-download pointer, which the Heute banner only
    /// borrows once: dismissed there, it is still findable here.
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
            if VoiceUpgradeHint.shared.suggests(language: model.targetLanguage) {
                Label("settings.audio.voiceUpgrade \(targetChromeName)",
                      systemImage: "speaker.wave.2")
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlAccent)
                    .padding(.top, DL.Space.xs)
            }
        }
    }

    /// Bound to NOT-muted: the row names the feature, the setting stores the
    /// exception. It lives in UserDefaults, one setting for the device — not in
    /// the box, where the product calibration would reset it on every load,
    /// and not per target language. Switching it on also lifts autoplay past a
    /// silenced phone (`AudioSession`), which is what the hint line names.
    private var readAloudBinding: Binding<Bool> {
        Binding(
            get: { !Pronouncer.shared.muted },
            set: { Pronouncer.shared.setReadAloud(on: $0) }
        )
    }

    /// Fresh start with the CURRENT catalog content.
    private var resetRow: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Button(role: .destructive) {
                confirmingReset = true
            } label: {
                Text("settings.reset.button \(targetName)")
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

    /// The target named in the CHROME's language, not its own — the voice hint
    /// is a sentence about the phone's settings, and it reads in the language
    /// the rest of the block does.
    private var targetChromeName: String {
        model.targetLanguage.map {
            LanguageNames.display($0, locale: locale, catalog: model.catalog)
        } ?? "?"
    }

    /// The pair as the pickers see it.
    private var selection: LanguageChoices.Selection {
        LanguageChoices.Selection(source: model.sourceLanguage, target: model.targetLanguage)
    }

    /// ALL covered sources — including the current target: picking it swaps.
    private var sourceChoices: [String] {
        model.catalog?.coveredSources() ?? []
    }

    /// The target picker's rows — `LanguageChoices.targetChoices`, which offers
    /// the swap row only where the swapped pair actually teaches something.
    private var targetChoices: [String] {
        guard let catalog = model.catalog else { return [] }
        return LanguageChoices.shared.targetChoices(catalog: catalog, selection: selection)
    }

    private var sourceBinding: Binding<String> {
        Binding(
            get: { model.sourceLanguage },
            set: { candidate in
                guard let catalog = model.catalog else { return }
                apply(LanguageChoices.shared.pickSource(catalog: catalog,
                                                        selection: selection,
                                                        code: candidate))
            }
        )
    }

    private var targetBinding: Binding<String> {
        Binding(
            get: { model.targetLanguage ?? "" },
            set: { candidate in
                apply(LanguageChoices.shared.pickTarget(selection: selection, code: candidate))
            }
        )
    }

    /// The pair kern picked, carried into the app's own persistence: an exchanged
    /// pair is one move (both boxes survive), a new known language re-joins in
    /// place, a new learned language opens that target's own box.
    private func apply(_ next: LanguageChoices.Selection) {
        let current = selection
        if next.source == current.target, next.target == current.source {
            model.swapLanguages()
            return
        }
        if next.source != current.source { model.switchSource(next.source) }
        // why: the target list is source-dependent, so a source tap can carry a
        // fallback target with it — that target has to follow into the box, or
        // the pair stays on one the new source cannot teach.
        if let target = next.target, target != current.target { model.switchTarget(target) }
    }
}
