import Foundation

/// Pure answer normalization for typed production answers
/// (design.md §Review UX rules): lowercase, trim, strip punctuation,
/// collapse whitespace, strip a leading German article.
///
/// NOTE: this is pure domain logic and should move into DuoKern
/// (e.g. `Kern/Session/AnswerNormalizer.swift`) in a follow-up —
/// it lives here only because Kern is frozen during this integration wave.
enum AnswerNormalizer {

    private static let leadingArticles: Set<String> = ["der", "die", "das", "ein", "eine"]

    /// Canonical comparison form of a typed or expected answer.
    static func normalize(_ raw: String) -> String {
        let lowered = raw.lowercased()
        // Intra-word joiners vanish outright so "E-Mail"/"Email" and
        // "geht's"/"gehts" converge; other punctuation → space so
        // "Guten Morgen!" and "Guten Morgen" collapse to the same form.
        let joinersRemoved = lowered.filter { $0 != "-" && $0 != "'" && $0 != "’" }
        let cleaned = String(joinersRemoved.map { character in
            character.isLetter || character.isNumber || character.isWhitespace ? character : " "
        })
        var words = cleaned.split(whereSeparator: \.isWhitespace).map(String.init)
        // Strip a leading article only when more content follows —
        // typing just "die" must never match "die Spülmaschine".
        if let first = words.first, leadingArticles.contains(first), words.count > 1 {
            words.removeFirst()
        }
        return words.joined(separator: " ")
    }

    /// True when the typed input means the expected answer.
    static func matches(input: String, expected: String) -> Bool {
        let normalizedInput = normalize(input)
        return !normalizedInput.isEmpty && normalizedInput == normalize(expected)
    }
}
