import Foundation

/// Pure answer normalization for typed answers (design.md §Review UX rules):
/// lowercase, trim, drop intra-word joiners, strip other punctuation,
/// collapse whitespace, strip a leading German article.
public enum AnswerNormalizer {

    private static let leadingArticles: Set<String> = ["der", "die", "das", "ein", "eine"]

    /// Canonical comparison form of a typed or expected answer.
    public static func normalize(_ raw: String) -> String {
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

    /// True when the typed input means the expected answer. Seed translations
    /// may list alternatives separated by "/" ("полиця / стелаж") — any
    /// alternative counts as correct.
    public static func matches(input: String, expected: String) -> Bool {
        let normalizedInput = normalize(input)
        guard !normalizedInput.isEmpty else { return false }
        return expected.split(separator: "/").contains { variant in
            normalizedInput == normalize(String(variant))
        }
    }
}
