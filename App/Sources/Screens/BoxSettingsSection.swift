import SwiftUI
import SprossKern

/// What the settings hold: known language (source),
/// learning language (target, one box each), learner name,
/// read-aloud source (`SettingsAudioRow`, only where the language has a sound),
/// backup, restart tutorial, reset.
/// The profile persists in UserDefaults + the box document.
struct BoxSettingsSection: View {
    let model: AppModel

    @State private var confirmingReset = false
    @State private var pendingResetExport: BackupFile?
    @State private var creditsPresented = false
    @Environment(\.locale) private var locale

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.lg) {
            Text("settings.title")
                .font(Theme.typography.hero)
                .foregroundStyle(Theme.colors.textPrimary)

            VStack(alignment: .leading, spacing: Theme.spacing.lg) {
                profileRow
                Divider().overlay(Theme.colors.separator)
                LearnerNameRow(model: model)
                // why: nothing can say this language — a row whose every option is silence
                // is not a choice, and both "on" segments would promise a sound that
                // cannot be made.
                if !SettingsAudioRow.sources(model).silent {
                    Divider().overlay(Theme.colors.separator)
                    SettingsAudioRow(model: model)
                }
                Divider().overlay(Theme.colors.separator)
                VStack(alignment: .leading, spacing: Theme.spacing.md) {
                    BackupRow(model: model)
                    restartTutorialRow
                    resetRow
                }
            }
            .panelSurface()
            .cardShadow()

            aboutFooter
        }
    }

    // MARK: About / feedback

    private var versionText: String {
        // why: the build number is always 1 here — showing "(1)" reads odd;
        // the marketing version alone identifies feedback mails fine.
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?"
        #if DEBUG
        return "Spross Dev v\(version)"
        #else
        return "Spross v\(version)"
        #endif
    }

    private var aboutFooter: some View {
        // The two links carry their own thumb's height, so the stack adds no spacing
        // of its own — only the version line, which is read and not tapped, asks for one.
        VStack(spacing: 0) {
            Text(versionText)
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
                .monospacedDigit()
                .padding(.bottom, Theme.spacing.sm)
            if let url = feedbackURL {
                Link(destination: url) {
                    LinkLabel("settings.feedback", icon: "envelope", font: Theme.typography.subheadline)
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.plain)
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
            LinkLabel("credits.title", icon: "info.circle", font: Theme.typography.subheadline)
                .frame(maxWidth: .infinity, minHeight: 44)
        }
        .buttonStyle(.plain)
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
            .disabled(model.switchingLanguage)
            // why: the re-join and box walk behind a pick take a beat — said here
            // rather than left silent, so a second tap while it settles reads as
            // "still working" and not as the row having ignored the first one.
            if model.switchingLanguage {
                HStack(spacing: Theme.spacing.xs) {
                    ProgressView().controlSize(.small)
                    Text("settings.profile.switching")
                        .font(Theme.typography.caption)
                        .foregroundStyle(Theme.colors.textSecondary)
                }
            } else {
                Text("settings.profile.hint")
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.textSecondary)
            }
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
            .menuStyle(.borderlessButton)
            .accessibilityLabel(Text(title))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Shows the onboarding pages again, the pair already made — nothing here
    /// touches progress (`resetRow` is the destructive row).
    private var restartTutorialRow: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            Button {
                model.restartOnboarding()
            } label: {
                LinkLabel("settings.restartTutorial.button", icon: "book", font: Theme.typography.subheadline)
            }
            .buttonStyle(.plain)
            Text("settings.restartTutorial.hint")
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
        }
    }

    /// Fresh start with the CURRENT catalog content.
    /// Where there is settled progress worth keeping, a save-file sheet for just this language opens first.
    /// A safety net ahead of the confirmation below, never a gate on it —
    /// a failed or canceled save still reaches the destructive dialog.
    private var resetRow: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            Button(role: .destructive) {
                startReset()
            } label: {
                LinkLabel("settings.reset.button \(targetName)", icon: "arrow.counterclockwise",
                          color: Theme.colors.wrong, font: Theme.typography.subheadline)
            }
            .buttonStyle(.plain)
            .backupExporter($pendingResetExport) { _ in
                confirmingReset = true
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

    private func startReset() {
        guard (model.stats?.allSettledCount ?? 0) > 0, let target = model.targetLanguage else {
            confirmingReset = true
            return
        }
        Task {
            do {
                pendingResetExport = try await BackupFile.taken(from: model, only: target)
            } catch {
                confirmingReset = true
            }
        }
    }

    // MARK: Choices & bindings

    private var targetName: String {
        model.targetLanguage.map { LanguageNames.native($0, catalog: model.catalog) } ?? "?"
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
