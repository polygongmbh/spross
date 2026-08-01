import Foundation
import SprossKern

// The app half of the audio boundary: Kern hands out catalog-relative PATHS
// and never opens a file, so turning a path into a bundle URL happens here —
// once, in the model both the session and the drills already hold.

extension AppModel {

    /// why: the bundled catalog is a folder reference — it cannot move under a
    /// running process, so the lookup is paid once instead of per card.
    private static let bundledCatalog: URL? =
        Bundle.main.url(forResource: "catalog", withExtension: nil)

    /// The catalog folder inside the app bundle — the same directory
    /// `loadCatalog()` reads its JSON through.
    var catalogDirectory: URL? { Self.bundledCatalog }

    /// Bundle URL for a recording path from Kern ("audio/uk/office.mp3").
    /// A nil path means no recording matched the visible form; it stays nil,
    /// and the caller falls back to the live voice.
    func audioURL(_ path: String?) -> URL? {
        guard let path, let directory = catalogDirectory else { return nil }
        return directory.appending(path: path)
    }

    /// WHEN this card's target form may be said out loud — Kern's rule,
    /// consumed rather than re-derived: both apps switch on this one cue
    /// instead of each testing the role in its own way.
    func pronunciationCue(for card: Card) -> PronunciationCue {
        SprossKern.pronunciationCue(role: presentationRole(for: card.id))
    }
}
