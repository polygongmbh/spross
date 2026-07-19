import SwiftUI

/// A button label that reads in the user's KNOWN language, with an optional
/// smaller subtitle in the language they are LEARNING (immersion). The primary
/// resolves against the environment locale; the subtitle is looked up in
/// `targetLocale` and hidden when it would just repeat the primary.
///
/// `key` is the German catalog key (also its source text). `targetLocale` is
/// `AppModel.targetChromeLocale` — nil for learners whose target language has
/// no chrome yet, which simply hides the subtitle.
struct DLActionLabel: View {
    @Environment(\.locale) private var locale
    let key: String
    var targetLocale: Locale?

    var body: some View {
        VStack(spacing: 1) {
            Text(LocalizedStringKey(key))
            if let targetLocale,
               targetLocale.language.languageCode?.identifier != locale.language.languageCode?.identifier {
                Text(DLChrome.string(key, locale: targetLocale))
                    .font(DL.Fonts.caption)
                    .opacity(0.75)
            }
        }
        .frame(maxWidth: .infinity)
    }
}
