/// Swahili saa-system clock times, ported from the prototype `ClockTrainer.tsx`.
/// Saa hour = western hour shifted by 6; day periods asubuhi/mchana/jioni/usiku;
/// "na nusu" for half past, "imebakia dakika ..." counting down past the half hour.
enum SwahiliClock {
    private static let hourWords = ["kumi na mbili", "moja", "mbili", "tatu", "nne", "tano", "sita", "saba", "nane", "tisa", "kumi", "kumi na moja", "kumi na mbili"]

    static let gloss = "Saa-System: Saa = deutsche Stunde − 6 (mod 12); Tageszeit: asubuhi/mchana/jioni/usiku"

    private static func period(hours: Int) -> String {
        if hours >= 4 && hours < 10 { return "asubuhi" }
        if hours >= 10 && hours < 16 { return "mchana" }
        if hours >= 16 && hours < 19 { return "jioni" }
        return "usiku"
    }

    private static func minuteWord(_ m: Int) -> String {
        switch m {
        case 15: return "kumi na tano"
        case 30: return "thelathini (nusu)"
        case 5: return "tano"
        case 10: return "kumi"
        case 20: return "ishirini"
        case 25: return "ishirini na tano"
        default: return String(m)
        }
    }

    /// `minutes` must be a multiple of 5 (the trainer rounds beforehand).
    static func time(hours: Int, minutes: Int) -> String {
        let saaHour = (hours + 6) % 12
        let nextSaaHour = (saaHour + 1) % 12
        let p = period(hours: hours)
        let hWord = hourWords[saaHour]
        let nextWord = hourWords[nextSaaHour]

        if minutes == 0 { return "Saa \(hWord) \(p)" }
        if minutes == 30 { return "Saa \(hWord) na nusu \(p)" }
        if minutes < 30 { return "Saa \(hWord) na dakika \(minuteWord(minutes)) \(p)" }
        return "Saa \(nextWord) imebakia dakika \(minuteWord(60 - minutes)) \(p)"
    }
}
