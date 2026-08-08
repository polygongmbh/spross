import Foundation
import Observation
import UIKit

/// Whether to point the learner at the free voice download, and where that
/// pointer has already been answered.
///
/// iOS bundles only the compact voice for a language; the enhanced and premium
/// ones are a free download in Settings that nothing in the system advertises,
/// and a synthesized word is the whole pronunciation for any form the catalog
/// has no recording of. So the gap is worth naming — but only while it is
/// real: the hint asks the synthesizer what would actually answer, and stops
/// appearing the moment a better voice is installed.
@Observable
@MainActor
final class VoiceUpgradeHint {

    static let shared = VoiceUpgradeHint()

    /// Bumped whenever the installed voices may have changed, so a view that
    /// asked before re-asks. Read by `suggests(language:)` — that read is what
    /// subscribes the view to it.
    private var revision = 0
    private var bannerDismissed: Bool

    private static let dismissedKey = "voiceUpgradeBannerDismissed"

    private init() {
        bannerDismissed = UserDefaults.standard.bool(forKey: Self.dismissedKey)
        // why: a voice downloaded in Settings is installed while the app sleeps
        // and no API announces it — coming back is the only moment to re-ask,
        // and it is exactly the moment the learner expects the hint gone.
        NotificationCenter.default.addObserver(
            forName: UIApplication.willEnterForegroundNotification,
            object: nil, queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.revision += 1 }
        }
    }

    /// Whether `language` would be read by the compact voice right now. Silent
    /// for a muted app — a hint about how the reading sounds is noise to
    /// someone who switched reading aloud off.
    func suggests(language: String?) -> Bool {
        _ = revision
        guard let language, !Pronouncer.shared.muted else { return false }
        return Pronouncer.shared.hasOnlyBasicVoice(language: language)
    }

    /// The Heute banner: the same condition, plus a dismissal, plus a box that
    /// has actually been heard from — a fresh install is told about the voice
    /// before it has played a single word otherwise.
    func suggestsBanner(language: String?, activeCards: Int) -> Bool {
        !bannerDismissed && activeCards > 0 && suggests(language: language)
    }

    /// Dismissal is permanent: the settings row keeps the pointer for anyone
    /// who wants it later, so the banner never has to ask twice.
    func dismissBanner() {
        bannerDismissed = true
        UserDefaults.standard.set(true, forKey: Self.dismissedKey)
    }
}
