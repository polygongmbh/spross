import Foundation

/// The locale the watch, the complication and the iOS widget write their chrome in
/// (`Shared/Resources/Glance.xcstrings`, table `Glance`).
///
/// These bundles never see the app's model, so they follow the snapshot's
/// `chromeLanguage` — kern's answer, the one the phone's own chrome follows — and set
/// it as the SwiftUI `locale` their views resolve against, as the app does with
/// `AppModel.knownLocale`. With no snapshot to read, the device language stands in where
/// the catalog carries it, and English where it does not, like kern's fallback.
enum GlanceChrome {
    static let table = "Glance"

    static func locale(_ chromeLanguage: String?) -> Locale {
        if let chromeLanguage { return Locale(identifier: chromeLanguage) }
        let device = Locale.preferredLanguages.first
            .flatMap { Locale(identifier: $0).language.languageCode?.identifier }
        let carried = Bundle.main.localizations
        return Locale(identifier: device.flatMap { carried.contains($0) ? $0 : nil } ?? "en")
    }
}
