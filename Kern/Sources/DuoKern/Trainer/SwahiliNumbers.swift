/// Swahili cardinal numbers, ported from the prototype `NumbersTrainer.tsx`.
enum SwahiliNumbers {
    static let ones = ["", "moja", "mbili", "tatu", "nne", "tano", "sita", "saba", "nane", "tisa"]
    private static let tens = ["", "kumi", "ishirini", "thelathini", "arobaini", "hamsini", "sitini", "sabini", "themanini", "tisini"]

    /// 0...999_999; larger values fall back to digits.
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
        if n < 1_000_000 {
            let t = n / 1000
            let rest = n % 1000
            let tWord = t == 1 ? "elfu moja" : "elfu \(cardinal(t))"
            return rest == 0 ? tWord : "\(tWord) na \(cardinal(rest))"
        }
        return String(n)
    }
}
