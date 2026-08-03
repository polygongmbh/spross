import Foundation
import SprossKern

// The learner's own words, app side. The rules — where they live, what ids they
// carry, that adding one packs it — are Kern's (`OwnWords`, kern §6); this layer
// trims the typed text, names the area in the chrome language, and persists.

extension AppModel {

    /// The one area own words live in; not a catalog folder name.
    var ownArea: String { OwnWords.shared.AREA }

    func isOwnWord(_ cardID: String) -> Bool { OwnWords.shared.owns(cardId: cardID) }

    /// Take in a word the learner wrote, in both of the profile's languages.
    /// Returns its card id, or nil when either side was left blank.
    @discardableResult
    func addOwnWord(known: String, learning: String, emoji: String) -> String? {
        guard let box else { return nil }
        let knownText = known.trimmed
        let learningText = learning.trimmed
        guard !knownText.isEmpty, !learningText.isEmpty else { return nil }

        // why: the id is minted from the LEARNED side — it is the one that stays put
        // while the known language is free to change under a source switch.
        let id = OwnWords.shared.mint(text: learningText,
                                      taken: Set(box.ownWords.map(\.id)))
        let word = OwnWord(id: id,
                           kind: OwnWords.shared.DEFAULT_KIND,
                           emoji: emoji.trimmed.isEmpty ? nil : emoji.trimmed,
                           texts: [box.joinStamp.source: knownText,
                                   box.joinStamp.target: learningText])
        mutate { $0 = BoxEngine.shared.addOwnWord(state: $0, word: word) }
        return id
    }

    /// Take one back out, with its schedule and its place in the queue. Reaches
    /// own words only — Kern refuses the rest.
    func removeOwnWord(_ cardID: String) {
        mutate { $0 = BoxEngine.shared.removeOwnWord(state: $0, wordId: cardID) }
    }
}

private extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
