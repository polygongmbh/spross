import Foundation
import SprossKern

// The learner's own words, app side. The rules — where they live, what ids they
// carry, that adding one packs it — are Kern's (`OwnWords`, kern §6); this layer
// trims the typed text, names the area in the chrome language, and persists.

extension AppModel {

    /// The one area own words live in; not a catalog folder name.
    var ownArea: String { OwnWords.shared.AREA }

    /// Every word the learner wrote, in the order they wrote them — the ones that
    /// joined a card and the suggestions still waiting for their other half alike.
    var ownWords: [OwnWord] { box?.ownWords ?? [] }

    /// The ones written in both of the profile's languages: study material with a card
    /// behind it, and progress on that card.
    var ownWordPairs: [OwnWord] {
        guard let box else { return [] }
        return Feedback.shared.wordPairs(state: box)
    }

    /// The ones still carrying a single half. Being a suggestion is a JOIN question and
    /// is kern's to answer (`Feedback.suggestions`), never the screen's.
    var suggestions: [OwnWord] {
        guard let box else { return [] }
        return Feedback.shared.suggestions(state: box)
    }

    /// The bare notes: what the learner had to say that names no word at all. Neither
    /// suggestions nor study material — what they ask about need not be in the catalog.
    var remarks: [OwnWord] {
        guard let box else { return [] }
        return Feedback.shared.remarks(state: box)
    }

    /// One of them by id, or nil for a catalog word.
    func ownWord(_ cardID: String) -> OwnWord? {
        box?.ownWords.first { $0.id == cardID }
    }

    func isOwnWord(_ cardID: String) -> Bool { OwnWords.shared.owns(cardId: cardID) }

    /// Take in a word the learner wrote. One side alone is enough: that is a
    /// SUGGESTION, which joins no card and is never scheduled until the other half
    /// arrives (`OwnWord`). A comment with no word under it at all is a REMARK — the
    /// entry that was never meant to become a card. Returns its card id, or nil when
    /// the learner wrote nothing anywhere.
    @discardableResult
    func addOwnWord(known: String, learning: String, emoji: String,
                    comment: String = "") -> String? {
        guard let box else { return nil }
        let knownText = known.trimmed
        let learningText = learning.trimmed
        let said = comment.trimmed
        guard !knownText.isEmpty || !learningText.isEmpty || !said.isEmpty else { return nil }

        // why: the id is minted from the LEARNED side — it is the one that stays put
        // while the known language is free to change under a source switch. A word
        // written only in the known language has nothing else to be named after.
        let id = OwnWords.shared.mint(text: learningText.isEmpty ? knownText : learningText,
                                      taken: Set(box.ownWords.map(\.id)))
        let word = OwnWords.shared.write(id: id,
                                        kind: OwnWords.shared.DEFAULT_KIND,
                                        emoji: picture(emoji),
                                        texts: texts(known: knownText, learning: learningText,
                                                     onto: [:]),
                                        comment: said)
        mutate {
            $0 = BoxEngine.shared.addOwnWord(state: $0, word: word,
                                             nowEpochMillis: Date().epochMillis)
        }
        return id
    }

    /// Rewrite one the learner already wrote, keeping its id — and with the id its
    /// schedule, its queue slot and anything filed against it (`BoxEngine.updateOwnWord`).
    /// Every field blank would be an entry that says nothing, so it is refused rather
    /// than stored empty; deleting is `removeOwnWord`.
    func updateOwnWord(_ word: OwnWord, known: String, learning: String, emoji: String,
                       comment: String = "") {
        let knownText = known.trimmed
        let learningText = learning.trimmed
        let said = comment.trimmed
        guard !knownText.isEmpty || !learningText.isEmpty || !said.isEmpty else { return }
        let rewritten = OwnWords.shared.write(id: word.id, kind: word.kind,
                                              emoji: picture(emoji),
                                              texts: texts(known: knownText,
                                                           learning: learningText,
                                                           onto: word.texts),
                                              comment: said)
        mutate { $0 = BoxEngine.shared.updateOwnWord(state: $0, word: rewritten) }
    }

    /// Take one back out, with its schedule and its place in the queue. Reaches
    /// own words only — Kern refuses the rest.
    func removeOwnWord(_ cardID: String) {
        mutate { $0 = BoxEngine.shared.removeOwnWord(state: $0, wordId: cardID) }
    }

    /// The two sides written onto whatever the word already carried. Editing under one
    /// profile must not throw away a half written under another: `texts` is keyed by
    /// language exactly as the catalog keys a concept, and a language this pair cannot
    /// see is still a language the word joins (`OwnWord`).
    private func texts(known: String, learning: String,
                       onto stored: [String: String]) -> [String: String] {
        guard let box else { return stored }
        var texts = stored
        texts[box.joinStamp.source] = known.isEmpty ? nil : known
        texts[box.joinStamp.target] = learning.isEmpty ? nil : learning
        return texts
    }

    private func picture(_ emoji: String) -> String? {
        emoji.trimmed.isEmpty ? nil : emoji.trimmed
    }
}

private extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
