/// German cardinal numbers and year readings.
/// Ported from the prototype `NumbersTrainer.tsx` (source of truth),
/// with one fix: tens compounds use "ein" ("einundzwanzig"),
/// not the prototype's erroneous "einsundzwanzig".
enum GermanNumbers {
    private static let ones = ["", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun"]
    private static let teens = ["zehn", "elf", "zwölf", "dreizehn", "vierzehn", "fünfzehn", "sechzehn", "siebzehn", "achtzehn", "neunzehn"]
    private static let tens = ["", "", "zwanzig", "dreißig", "vierzig", "fünfzig", "sechzig", "siebzig", "achtzig", "neunzig"]

    /// 0...999_999; larger values fall back to digits.
    static func cardinal(_ n: Int) -> String {
        if n == 0 { return "null" }
        if n < 10 { return ones[n] }
        if n < 20 { return teens[n - 10] }
        if n < 100 {
            let o = n % 10
            let t = n / 10
            if o == 0 { return tens[t] }
            return (o == 1 ? "ein" : ones[o]) + "und" + tens[t]
        }
        if n < 1000 {
            let h = n / 100
            let rest = n % 100
            let hWord = h == 1 ? "einhundert" : ones[h] + "hundert"
            return rest == 0 ? hWord : hWord + cardinal(rest)
        }
        if n < 1_000_000 {
            let t = n / 1000
            let rest = n % 1000
            let tWord = t == 1 ? "eintausend" : cardinal(t) + "tausend"
            return rest == 0 ? tWord : tWord + cardinal(rest)
        }
        return String(n)
    }

    /// "neunzehnhundertachtundsiebzig" style for years like 1978.
    static func yearHundred(_ y: Int) -> String {
        let century = y / 100
        let rest = y % 100
        if rest == 0 { return cardinal(century) + "hundert" }
        return cardinal(century) + "hundert" + cardinal(rest)
    }

    /// Canonical year reading: hundred-counting is standard for 1100–1999.
    static func year(_ y: Int) -> String {
        if y >= 1100 && y <= 1999 && y % 1000 != 0 {
            return yearHundred(y)
        }
        return cardinal(y)
    }

    /// All accepted year readings (plain cardinal + hundred-counting where idiomatic).
    static func yearVariants(_ y: Int) -> [String] {
        var variants = [cardinal(y)]
        if y >= 1100 && y <= 1999 && y % 1000 != 0 {
            variants.append(yearHundred(y))
        }
        if y >= 2000 && y % 1000 != 0 {
            variants.append(yearHundred(y))
        }
        var seen = Set<String>()
        return variants.filter { seen.insert($0).inserted }
    }
}
