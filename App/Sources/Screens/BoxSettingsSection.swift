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
        VStack(alignment: .leading, spacing: Theme.spacing.lg) {
            Text("settings.title")
                .font(Theme.typography.title)
                .foregroundStyle(Theme.colors.textPrimary)

            VStack(alignment: .leading, spacing: Theme.spacing.lg) {
                profileRow
                Divider().overlay(Theme.colors.separator)
                LearnerNameRow(model: model)
                Divider().overlay(Theme.colors.separator)
                audioRow
                Divider().overlay(Theme.colors.separator)
                restartTutorialRow
                Divider().overlay(Theme.colors.separator)
                resetRow
            }
            .padding(Theme.spacing.lg)
            .background(
                RoundedRectangle(cornerRadius: Theme.radius.tile, style: .continuous)
                    .fill(Theme.colors.surface)
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
        VStack(spacing: Theme.spacing.sm) {
            Text(versionText)
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
                .monospacedDigit()
            if let url = feedbackURL {
                Link(destination: url) {
                    Label("settings.feedback", systemImage: "envelope")
                        .font(Theme.typography.subheadline)
                        .foregroundStyle(Theme.colors.accent)
                }
            }
            creditsButton
        }
        .frame(maxWidth: .infinity)
        .padding(.top, Theme.spacing.md)
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
            Label("credits.title", systemImage: "info.circle")
                .font(Theme.typography.subheadline)
                .foregroundStyle(Theme.colors.accent)
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
        VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            HStack(alignment: .top, spacing: Theme.spacing.lg) {
                languageMenu(title: "settings.known.title",
                             selection: sourceBinding, choices: sourceChoices)
                languageMenu(title: "settings.learning.title",
                             selection: targetBinding, choices: targetChoices)
            }
            Text("settings.profile.hint")
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
        }
    }

    /// A dropdown per side. The collapsed label carries the English exonym
    /// alone — it has half a row to live in — while the menu itself has room
    /// for "🇺🇦 Українська · Ukrainian".
    private func languageMenu(title: LocalizedStringKey, selection: Binding<String>,
                              choices: [String]) -> some View {
        VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            Text(title)
                .font(Theme.typography.headline)
                .foregroundStyle(Theme.colors.textPrimary)
            Menu {
                Picker(title, selection: selection) {
                    ForEach(choices, id: \.self) { candidate in
                        Text(verbatim: LanguageNames.pickerRow(candidate, catalog: model.catalog))
                            .tag(candidate)
                    }
                }
            } label: {
                HStack(spacing: Theme.spacing.xs) {
                    Text(verbatim: LanguageNames.pickerLabel(selection.wrappedValue,
                                                             catalog: model.catalog))
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.caption2)
                }
                .foregroundStyle(Theme.colors.textPrimary)
                .padding(.vertical, Theme.spacing.sm)
                .padding(.horizontal, Theme.spacing.md)
                .background(
                    RoundedRectangle(cornerRadius: Theme.radius.tile, style: .continuous)
                        .fill(Theme.colors.surfaceTint)
                )
            }
            .accessibilityLabel(Text(title))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// The same choice the session's top bar carries (there reduced to the
    /// mute button), and the place the tap-to-replay gesture is disclosed — the
    /// card itself grows no affordance for it, so the hint line is where it is
    /// named. It is also the standing home of the voice-download pointer, which
    /// the Home banner only borrows once: dismissed there, it is still findable
    /// here.
    private var audioRow: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            Text("settings.audio.title")
                .font(Theme.typography.headline)
                .foregroundStyle(Theme.colors.textPrimary)
            Picker("settings.audio.title", selection: audioPreferenceBinding) {
                ForEach(AudioPreference.allCases) { option in
                    Text(optionLabel(option)).tag(option)
                }
            }
            .pickerStyle(.segmented)
            Text(audioHintKey)
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
            if VoiceUpgradeHint.shared.suggests(language: model.targetLanguage) {
                Label("settings.audio.voiceUpgrade \(targetChromeName)",
                      systemImage: "speaker.wave.2")
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.accent)
                    .padding(.top, Theme.spacing.xs)
            }
        }
    }

    /// The three options the row carries, each a combination of the mute switch
    /// and the voice source. The picker alone decides both: there is no state
    /// where a source is chosen but the app is silent.
    private enum AudioPreference: String, CaseIterable, Identifiable {
        case off, recordings, tts
        var id: String { rawValue }
    }

    private func optionLabel(_ option: AudioPreference) -> LocalizedStringKey {
        switch option {
        case .off: return "settings.audio.option.off"
        case .recordings: return "settings.audio.option.recordings"
        case .tts: return "settings.audio.option.tts"
        }
    }

    /// The hint names the chosen behavior, not the picker as a whole. The
    /// tap-to-replay gesture is disclosed in the No audio line alone — the only
    /// preference where a learner might think the app has gone silent for good.
    private var audioHintKey: LocalizedStringKey {
        switch audioPreferenceBinding.wrappedValue {
        case .off: return "settings.audio.hint.off"
        case .recordings: return "settings.audio.hint.recordings"
        case .tts: return "settings.audio.hint.tts"
        }
    }

    /// `.off` is the read-aloud switch off; the two "on" options are the voice
    /// source with the switch on. Picking one of them turns reading aloud back
    /// on, so the picker can never leave the app silent behind a chosen source.
    private var audioPreferenceBinding: Binding<AudioPreference> {
        Binding(
            get: {
                if Pronouncer.shared.muted { return .off }
                return Pronouncer.shared.voiceSource == .tts ? .tts : .recordings
            },
            set: { preference in
                switch preference {
                case .off:
                    Pronouncer.shared.setReadAloud(on: false)
                case .recordings:
                    Pronouncer.shared.voiceSource = .recordings
                    if Pronouncer.shared.muted { Pronouncer.shared.setReadAloud(on: true) }
                case .tts:
                    Pronouncer.shared.voiceSource = .tts
                    if Pronouncer.shared.muted { Pronouncer.shared.setReadAloud(on: true) }
                }
            }
        )
    }

    /// Shows the onboarding pages again, the pair already made — nothing here
    /// touches progress (`resetRow` is the destructive row).
    private var restartTutorialRow: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            Button {
                model.restartOnboarding()
            } label: {
                Text("settings.restartTutorial.button")
                    .font(Theme.typography.headline)
            }
            Text("settings.restartTutorial.hint")
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
        }
    }

    /// Fresh start with the CURRENT catalog content.
    private var resetRow: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            Button(role: .destructive) {
                confirmingReset = true
            } label: {
                Text("settings.reset.button \(targetName)")
                    .font(Theme.typography.headline)
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
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
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
    /// Resolved when the profile changes: asking counts the cards of every pair
    /// the catalog could teach, which means joining all of them.
    private var targetChoices: [String] { model.targetChoices }

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
