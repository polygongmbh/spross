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

    /// тисяча/тисячі/тисяч agreement for a multiplier `t` (1...999).
    private static func thousandWord(_ t: Int) -> String {
        let lastTwo = t % 100
        if (11...14).contains(lastTwo) { return "тисяч" }
        switch t % 10 {
        case 1: return "тисяча"
        case 2, 3, 4: return "тисячі"
        default: return "тисяч"
        }
    }

    private static func compose(_ n: Int, feminineUnits: Bool) -> String {
        if n == 0 { return "нуль" }
        if n < 1000 { return subThousand(n, feminine: feminineUnits).joined(separator: " ") }
        guard n < 1_000_000 else { return String(n) }
        let t = n / 1000
        let rest = n % 1000
        // why: multiplier before тисяча is always feminine (одна тисяча, дві тисячі)
        var words = subThousand(t, feminine: true)
        words.append(thousandWord(t))
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
