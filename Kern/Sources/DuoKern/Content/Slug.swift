import Foundation

/// Deterministic ASCII slugs used for stable card IDs.
enum Slug {
    private static let umlautMap: [Character: String] = [
        "ä": "ae", "ö": "oe", "ü": "ue", "ß": "ss",
    ]

    /// Lowercases, expands German umlauts (ä→ae etc.), and replaces every
    /// non-alphanumeric run with a single "-". Same input → same output, always.
    static func slugify(_ input: String) -> String {
        var expanded = ""
        expanded.reserveCapacity(input.count)
        for ch in input.lowercased() {
            expanded += umlautMap[ch] ?? String(ch)
        }

        var result = ""
        var lastWasDash = false
        for ch in expanded {
            if ch.isASCII, ch.isLetter || ch.isNumber {
                result.append(ch)
                lastWasDash = false
            } else if !lastWasDash {
                result.append("-")
                lastWasDash = true
            }
        }
        while result.hasPrefix("-") { result.removeFirst() }
        while result.hasSuffix("-") { result.removeLast() }
        return result
    }
}
