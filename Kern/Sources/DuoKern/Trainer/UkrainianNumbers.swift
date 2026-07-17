/// Ukrainian cardinal numbers 0...999_999.
/// Canonical form uses masculine counting words (один, два);
/// feminine forms (одна, дві) are produced as accepted variants.
/// Thousands always take the grammatically required feminine multiplier
/// plus тисяча/тисячі/тисяч agreement (одна тисяча, дві тисячі, п'ять тисяч).
enum UkrainianNumbers {
    private static let onesMasc = ["", "один", "два", "три", "чотири", "п'ять", "шість", "сім", "вісім", "дев'ять"]
    private static let onesFem = ["", "одна", "дві", "три", "чотири", "п'ять", "шість", "сім", "вісім", "дев'ять"]
    private static let teens = ["десять", "одинадцять", "дванадцять", "тринадцять", "чотирнадцять", "п'ятнадцять", "шістнадцять", "сімнадцять", "вісімнадцять", "дев'ятнадцять"]
    private static let tens = ["", "", "двадцять", "тридцять", "сорок", "п'ятдесят", "шістдесят", "сімдесят", "вісімдесят", "дев'яносто"]
    private static let hundreds = ["", "сто", "двісті", "триста", "чотириста", "п'ятсот", "шістсот", "сімсот", "вісімсот", "дев'ятсот"]

    /// Words for 1...999 (space-joined). `feminine` switches 1/2 to одна/дві.
    private static func subThousand(_ n: Int, feminine: Bool) -> [String] {
        var words: [String] = []
        var rest = n
        if rest >= 100 {
            words.append(hundreds[rest / 100])
            rest %= 100
        }
        if rest >= 20 {
            words.append(tens[rest / 10])
            rest %= 10
        } else if rest >= 10 {
            words.append(teens[rest - 10])
            rest = 0
        }
        if rest > 0 {
            words.append(feminine ? onesFem[rest] : onesMasc[rest])
        }
        return words
    }

    /// Slavic count agreement for a multiplier `t`: form for a bare 1,
    /// forms for 2–4, else the "many" (genitive plural) form; the 11–14
    /// exception always takes the "many" form.
    private static func agree(_ t: Int, one: String, few: String, many: String) -> String {
        let lastTwo = t % 100
        if (11...14).contains(lastTwo) { return many }
        switch t % 10 {
        case 1: return one
        case 2, 3, 4: return few
        default: return many
        }
    }

    private static func thousandWord(_ t: Int) -> String {
        agree(t, one: "тисяча", few: "тисячі", many: "тисяч")
    }

    private static func compose(_ n: Int, feminineUnits: Bool) -> String {
        if n == 0 { return "нуль" }
        if n < 1000 { return subThousand(n, feminine: feminineUnits).joined(separator: " ") }
        // Guard on the billions count, not a 10-digit literal — Int is 32-bit
        // on watchOS.
        guard n / 1_000_000_000 <= 9 else { return String(n) }
        var words: [String] = []
        var rest = n
        // Millions/billions count with MASCULINE multipliers (один мільйон,
        // два мільйони); only тисяча takes the feminine multiplier.
        let billions = rest / 1_000_000_000; rest %= 1_000_000_000
        if billions > 0 {
            words.append(contentsOf: subThousand(billions, feminine: false))
            words.append(agree(billions, one: "мільярд", few: "мільярди", many: "мільярдів"))
        }
        let millions = rest / 1_000_000; rest %= 1_000_000
        if millions > 0 {
            words.append(contentsOf: subThousand(millions, feminine: false))
            words.append(agree(millions, one: "мільйон", few: "мільйони", many: "мільйонів"))
        }
        let thousands = rest / 1000; rest %= 1000
        if thousands > 0 {
            // why: multiplier before тисяча is always feminine (одна тисяча, дві тисячі)
            words.append(contentsOf: subThousand(thousands, feminine: true))
            words.append(thousandWord(thousands))
        }
        if rest > 0 {
            words.append(contentsOf: subThousand(rest, feminine: feminineUnits))
        }
        return words.joined(separator: " ")
    }

    /// Canonical (masculine counting) form.
    static func cardinal(_ n: Int) -> String {
        compose(n, feminineUnits: false)
    }

    /// Canonical form first, then accepted variants:
    /// feminine unit ending (одна/дві) and, for 1xxx numbers,
    /// the common reading without leading "одна" ("тисяча дев'ятсот ...").
    static func variants(_ n: Int) -> [String] {
        var list = [cardinal(n)]
        let feminine = compose(n, feminineUnits: true)
        if feminine != list[0] { list.append(feminine) }
        if (1000...1999).contains(n) {
            for form in [list[0], feminine] where form.hasPrefix("одна тисяча") {
                let short = String(form.dropFirst("одна ".count))
                if !list.contains(short) { list.append(short) }
            }
        }
        var seen = Set<String>()
        return list.filter { seen.insert($0).inserted }
    }
}
