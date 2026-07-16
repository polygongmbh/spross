import Foundation

/// Naive same-area phrase→word linking: matches normalized phrase text against
/// word headwords, plural forms, and verb stems. Unresolved components are
/// simply omitted — this is a best-effort diagnostic aid, not a parser.
enum PhraseLinker {

    private static let stopwords: Set<String> = ["die", "der", "das", "nur", "singular", "selten"]

    /// `areaCards` must be the noun/verb cards already built for the phrase's
    /// own area (phrase cards in the list, if any, are ignored).
    static func components(forPhrase phraseGerman: String, in areaCards: [Card]) -> [String] {
        let phraseTokens = Set(tokens(of: phraseGerman))
        guard !phraseTokens.isEmpty else { return [] }

        var componentIDs: [String] = []
        for card in areaCards {
            switch card.kind {
            case .noun where matchesNoun(card, phraseTokens: phraseTokens):
                componentIDs.append(card.id)
            case .verb where matchesVerb(card, phraseTokens: phraseTokens):
                componentIDs.append(card.id)
            default:
                continue
            }
        }
        return componentIDs
    }

    private static func matchesNoun(_ card: Card, phraseTokens: Set<String>) -> Bool {
        nounCandidates(card).contains { phraseTokens.contains($0) }
    }

    private static func matchesVerb(_ card: Card, phraseTokens: Set<String>) -> Bool {
        let (lastWord, stem) = verbForms(card.german)
        for token in phraseTokens {
            if token == lastWord { return true }
            if stem.count >= 3, token.hasPrefix(stem) { return true }
        }
        return false
    }

    /// Headword plus any real word(s) found in the (already "Pl. "-stripped)
    /// plural string, e.g. "die Kühlschränke" → also try "kühlschränke".
    /// Suffix-only markers ("-n", "–") and stopwords contribute nothing.
    private static func nounCandidates(_ card: Card) -> Set<String> {
        var candidates: Set<String> = [tokens(of: card.german).joined()]
        if let plural = card.plural {
            for token in tokens(of: plural) where !stopwords.contains(token) && token.count >= 3 {
                candidates.insert(token)
            }
        }
        return candidates
    }

    /// Last whitespace-separated word (handles separable/reflexive verbs like
    /// "Müll rausbringen", "sich anziehen") plus its dropped-ending stem, so
    /// conjugated forms like "kochst" still match the infinitive "kochen".
    private static func verbForms(_ german: String) -> (lastWord: String, stem: String) {
        let lastWord = german.split(separator: " ").last.map(String.init) ?? german
        let normalized = tokens(of: lastWord).joined()
        let stem: String
        if normalized.hasSuffix("en") {
            stem = String(normalized.dropLast(2))
        } else if normalized.hasSuffix("n") {
            stem = String(normalized.dropLast(1))
        } else {
            stem = normalized
        }
        return (normalized, stem)
    }

    /// Lowercase word tokens, punctuation/quotes stripped. Umlauts are kept
    /// as-is (not transliterated) since matching is against literal German text.
    private static func tokens(of s: String) -> [String] {
        var current = ""
        var result: [String] = []
        for ch in s.lowercased() {
            if ch.isLetter || ch.isNumber {
                current.append(ch)
            } else if !current.isEmpty {
                result.append(current)
                current = ""
            }
        }
        if !current.isEmpty { result.append(current) }
        return result
    }
}
