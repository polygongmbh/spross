/// German conversational clock times, ported from the prototype `ClockTrainer.tsx`:
/// Hochdeutsch standard plus regional (Oberfranken) variants
/// ("Viertel sieben", "Dreiviertel sieben", "punkt sechs").
enum GermanClock {
    private static let hourWords = ["zwölf", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn", "elf", "zwölf"]

    struct Conversational: Equatable {
        var standard: String
        var regional: String
    }

    /// Non-round minutes fall back to a digital reading ("drei Uhr 17").
    static func conversational(hours: Int, minutes: Int) -> Conversational {
        let h12 = hours % 12
        let nextH = (h12 + 1) % 12
        let hWord = hourWords[h12]
        let nextWord = hourWords[nextH]

        if hours == 0 && minutes == 0 { return .init(standard: "Mitternacht", regional: "Mitternacht") }
        if hours == 12 && minutes == 0 { return .init(standard: "Mittag", regional: "Mittag") }

        switch minutes {
        case 0: return .init(standard: "\(hWord) Uhr", regional: "punkt \(hWord)")
        case 5: return same("fünf nach \(hWord)")
        case 10: return same("zehn nach \(hWord)")
        case 15: return .init(standard: "Viertel nach \(hWord)", regional: "Viertel \(nextWord)")
        case 20: return same("zwanzig nach \(hWord)")
        case 25: return same("fünf vor halb \(nextWord)")
        case 30: return same("halb \(nextWord)")
        case 35: return same("fünf nach halb \(nextWord)")
        case 40: return same("zwanzig vor \(nextWord)")
        case 45: return .init(standard: "Viertel vor \(nextWord)", regional: "Dreiviertel \(nextWord)")
        case 50: return same("zehn vor \(nextWord)")
        case 55: return same("fünf vor \(nextWord)")
        default: return same("\(hWord) Uhr \(minutes)")
        }
    }

    private static func same(_ phrase: String) -> Conversational {
        .init(standard: phrase, regional: phrase)
    }

    static func task(hours: Int, minutes: Int) -> (display: String, accepted: [String], gloss: String?) {
        let c = conversational(hours: hours, minutes: minutes)
        var accepted = [c.standard]
        if c.regional != c.standard { accepted.append(c.regional) }
        let gloss = c.regional != c.standard ? "Regional (z. B. Oberfranken): \(c.regional)" : nil
        return (c.standard, accepted, gloss)
    }
}
