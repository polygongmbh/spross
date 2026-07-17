/// Swahili cardinal numbers, ported from the prototype `NumbersTrainer.tsx`.
enum SwahiliNumbers {
    static let ones = ["", "moja", "mbili", "tatu", "nne", "tano", "sita", "saba", "nane", "tisa"]
    private static let tens = ["", "kumi", "ishirini", "thelathini", "arobaini", "hamsini", "sitini", "sabini", "themanini", "tisini"]

    /// The tens as a labelled reference ("10 kumi" … "90 tisini") — Swahili
    /// tens are the least guessable part of the system, so the drill offers
    /// them as a look-up.
    static var tensReference: [String] {
        (1...9).map { "\($0)0 \(tens[$0])" }
    }

    /// 0...9_999_999_999; larger values fall back to digits.
    static func cardinal(_ n: Int) -> String {
        if n == 0 { return "sifuri" }
        if n < 10 { return ones[n] }
        if n == 10 { return "kumi" }
        if n < 20 { return "kumi na \(ones[n - 10])" }
        if n < 100 {
            let o = n % 10
            let t = n / 10
            return o == 0 ? tens[t] : "\(tens[t]) na \(ones[o])"
        }
        if n < 1000 {
            let h = n / 100
            let rest = n % 100
            let hWord = h == 1 ? "mia moja" : "mia \(ones[h])"
            return rest == 0 ? hWord : "\(hWord) na \(cardinal(rest))"
        }
        if n < 1_000_000 { return scale(n, unit: 1000, word: "elfu") }
        if n < 1_000_000_000 { return scale(n, unit: 1_000_000, word: "milioni") }
        if n <= 9_999_999_999 { return scale(n, unit: 1_000_000_000, word: "bilioni") }
        return String(n)
    }

    /// Accepted spellings for the drill: the canonical reading plus one with
    /// the "na" connectors dropped ("mia tatu sitini tano"), which speakers
    /// routinely omit in longer numbers.
    static func acceptedVariants(_ n: Int) -> [String] {
        let canonical = cardinal(n)
        let naless = canonical.replacingOccurrences(of: " na ", with: " ")
        return naless == canonical ? [canonical] : [canonical, naless]
    }

    /// "elfu moja / milioni mbili / bilioni tatu [na rest]": one scale word,
    /// "moja" for a bare 1, else the cardinal of the multiplier, rest joined
    /// with "na".
    private static func scale(_ n: Int, unit: Int, word: String) -> String {
        let t = n / unit
        let rest = n % unit
        let tWord = t == 1 ? "\(word) moja" : "\(word) \(cardinal(t))"
        return rest == 0 ? tWord : "\(tWord) na \(cardinal(rest))"
    }
}
