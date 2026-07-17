/// Colloquial Ukrainian clock times (new — not in the prototype; needs review).
/// Patterns (one canonical form each, plus common accepted variants):
/// - exact hour: "друга година" (variant "друга")
/// - :15 → "чверть на третю" (variant "п'ятнадцять по другій")
/// - :30 → "пів на третю"
/// - :45 → "за чверть третя" (variant "за п'ятнадцять третя")
/// - other minutes → digital reading "друга тридцять п'ять"
///   (variants "десять по другій" for <30, "за десять третя" for >30).
/// Hour ordinals are feminine, agreeing with година; 12-hour cycle.
enum UkrainianClock {
    // index 1...12
    private static let ordinalNominative = ["", "перша", "друга", "третя", "четверта", "п'ята", "шоста", "сьома", "восьма", "дев'ята", "десята", "одинадцята", "дванадцята"]
    private static let ordinalAccusative = ["", "першу", "другу", "третю", "четверту", "п'яту", "шосту", "сьому", "восьму", "дев'яту", "десяту", "одинадцяту", "дванадцяту"]
    private static let ordinalLocative = ["", "першій", "другій", "третій", "четвертій", "п'ятій", "шостій", "сьомій", "восьмій", "дев'ятій", "десятій", "одинадцятій", "дванадцятій"]
    private static let ordinalGenitive = ["", "першої", "другої", "третьої", "четвертої", "п'ятої", "шостої", "сьомої", "восьмої", "дев'ятої", "десятої", "одинадцятої", "дванадцятої"]

    private static func hourIndex(_ h: Int) -> Int {
        let h12 = h % 12
        return h12 == 0 ? 12 : h12
    }

    /// `minutes` must be a multiple of 5 (the trainer rounds beforehand).
    static func task(hours: Int, minutes: Int) -> (display: String, accepted: [String], gloss: String?) {
        let cur = hourIndex(hours)
        let next = hourIndex(hours + 1)
        let minuteWord = UkrainianNumbers.cardinal(minutes)

        switch minutes {
        case 0:
            let display = "\(ordinalNominative[cur]) година"
            return (display, [display, ordinalNominative[cur]], nil)
        case 15:
            let display = "чверть на \(ordinalAccusative[next])"
            // Variants confirmed equally common by the language review.
            let variants = ["п'ятнадцять по \(ordinalLocative[cur])",
                            "чверть по \(ordinalLocative[cur])"]
            return (display, [display] + variants + [digital(cur, minuteWord)],
                    "«чверть на …» zählt zur kommenden Stunde (wie „Viertel drei“)")
        case 30:
            let display = "пів на \(ordinalAccusative[next])"
            let variant = "пів \(ordinalGenitive[next])"
            return (display, [display, variant, digital(cur, minuteWord)],
                    "«пів на …» zählt zur kommenden Stunde (wie deutsches „halb drei“)")
        case 45:
            let display = "за чверть \(ordinalNominative[next])"
            let variants = ["за п'ятнадцять \(ordinalNominative[next])",
                            "за чверть до \(ordinalGenitive[next])"]
            return (display, [display] + variants + [digital(cur, minuteWord)],
                    "«за чверть …» = Viertel vor der kommenden Stunde")
        default:
            let display = digital(cur, minuteWord)
            var accepted = [display]
            if minutes < 30 {
                accepted.append("\(minuteWord) по \(ordinalLocative[cur])")
            } else {
                accepted.append("за \(UkrainianNumbers.cardinal(60 - minutes)) \(ordinalNominative[next])")
            }
            return (display, accepted, nil)
        }
    }

    /// Digital-style reading: "друга тридцять п'ять".
    private static func digital(_ hourIdx: Int, _ minuteWord: String) -> String {
        "\(ordinalNominative[hourIdx]) \(minuteWord)"
    }
}
