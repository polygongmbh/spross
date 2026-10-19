import SwiftUI

/// The foot of the settings: the version, the feedback mail and the credits sheet.
struct SettingsAboutFooter: View {
    let model: AppModel

    @State private var creditsPresented = false
    @Environment(\.locale) private var locale

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

    var body: some View {
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
}
