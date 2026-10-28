import Foundation

/// Resolves a catalog key against a specific UI-language bundle. Needed for
/// runtime strings interpolated as `%@` arguments (e.g. a language name),
/// where SwiftUI's environment locale — which only drives `Text` /
/// `LocalizedStringKey` — can't reach.
enum ChromeStrings {
    static func string(_ key: String, locale: Locale) -> String {
        let code = locale.language.languageCode?.identifier ?? "de"
        guard let path = Bundle.main.path(forResource: code, ofType: "lproj"),
              let bundle = Bundle(path: path) else { return key }
        return bundle.localizedString(forKey: key, value: key, table: nil)
    }
}

extension BinaryInteger {
    /// The number as chrome shows it, its digits grouped the chrome language's way ("1.305" in German) —
    /// a bare `.formatted()` follows the device region instead.
    func counted(_ locale: Locale) -> String { Int(self).formatted(.number.locale(locale)) }
}
