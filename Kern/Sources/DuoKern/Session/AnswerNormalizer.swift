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
    /// alternative counts as correct. Small typos are tolerated (see
    /// `allowedTypos`); diacritic slips like "Kuhlschrank" ride the same rule.
    public static func matches(input: String, expected: String) -> Bool {
        evaluate(input: input, expected: expected) != .wrong
    }

    public enum Match: Equatable {
        case exact
        /// Accepted with a small typo; carries the correct normalized form.
        case typo(corrected: String)
        case wrong
    }

    /// `optionalPrefix`: a citation-form marker the learner may drop —
    /// Swahili verb cards pass "ku" so "pika" matches "kupika". Only applied
    /// where the caller knows it's linguistically right (never German).
    public static func evaluate(input: String, expected: String,
                                optionalPrefix: String? = nil) -> Match {
        let normalizedInput = normalize(input)
        guard !normalizedInput.isEmpty else { return .wrong }
        var best: Match = .wrong
        for variant in expected.split(separator: "/") {
            let target = normalize(String(variant))
            var targets = [target]
            if let optionalPrefix, target.hasPrefix(optionalPrefix),
               target.count > optionalPrefix.count {
                targets.append(String(target.dropFirst(optionalPrefix.count)))
            }
            for candidate in targets {
                if normalizedInput == candidate { return .exact }
                let letters = candidate.filter { !$0.isWhitespace }.count
                if damerauLevenshtein(normalizedInput, candidate) <= allowedTypos(letters: letters) {
                    // Reveal always shows the full citation form.
                    best = .typo(corrected: target)
                }
            }
        }
        return best
    }

    /// ~10% of letters, but never for very short words (a 1-edit slip on
    /// "kula" is usually a different word, not a typo).
    static func allowedTypos(letters: Int) -> Int {
        guard letters >= 5 else { return 0 }
        return max(1, letters / 10)
    }

    /// Optimal-string-alignment distance (Damerau-Levenshtein: insert,
    /// delete, substitute, and adjacent transposition each cost 1).
    static func damerauLevenshtein(_ a: String, _ b: String) -> Int {
        let s = Array(a), t = Array(b)
        if s.isEmpty { return t.count }
        if t.isEmpty { return s.count }
        var d = [[Int]](repeating: [Int](repeating: 0, count: t.count + 1), count: s.count + 1)
        for i in 0...s.count { d[i][0] = i }
        for j in 0...t.count { d[0][j] = j }
        for i in 1...s.count {
            for j in 1...t.count {
                let cost = s[i - 1] == t[j - 1] ? 0 : 1
                d[i][j] = min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if i > 1, j > 1, s[i - 1] == t[j - 2], s[i - 2] == t[j - 1] {
                    d[i][j] = min(d[i][j], d[i - 2][j - 2] + 1)
                }
            }
        }
        return d[s.count][t.count]
    }
}
