import Foundation
import SprossKern

// The learner's own words, app side. The rules — where they live, what ids they
// carry, that adding one packs it — are Kern's (`OwnWords`, kern §6); this layer
// trims the typed text, names the area in the chrome language, and persists.

extension AppModel {

    /// The one area own words live in; not a catalog folder name.
    var ownArea: String { OwnWords.shared.AREA }

    /// Whether any word the learner wrote joins this profile — what puts the
    /// area on the Box screen at all.
    var hasOwnWords: Bool { areaNames.contains(ownArea) }

    func isOwnWord(_ cardID: String) -> Bool { OwnWords.shared.owns(cardId: cardID) }

    /// Take in a word the learner wrote. One side alone is enough: that is a
    /// SUGGESTION, which joins no card and is never scheduled until the other half
    /// arrives (`OwnWord`). Returns its card id, or nil when both sides were blank.
    @discardableResult
    func addOwnWord(known: String, learning: String, emoji: String) -> String? {
        guard let box else { return nil }
        let knownText = known.trimmed
        let learningText = learning.trimmed
        guard !knownText.isEmpty || !learningText.isEmpty else { return nil }

        // why: the id is minted from the LEARNED side — it is the one that stays put
        // while the known language is free to change under a source switch. A word
        // written only in the known language has nothing else to be named after.
        let id = OwnWords.shared.mint(text: learningText.isEmpty ? knownText : learningText,
                                      taken: Set(box.ownWords.map(\.id)))
        var texts: [String: String] = [:]
        if !knownText.isEmpty { texts[box.joinStamp.source] = knownText }
        if !learningText.isEmpty { texts[box.joinStamp.target] = learningText }
        let word = OwnWords.shared.write(id: id,
                                        kind: OwnWords.shared.DEFAULT_KIND,
                                        emoji: emoji.trimmed.isEmpty ? nil : emoji.trimmed,
                                        texts: texts)
        mutate {
            $0 = BoxEngine.shared.addOwnWord(state: $0, word: word,
                                             nowEpochMillis: Date().epochMillis)
        }
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
