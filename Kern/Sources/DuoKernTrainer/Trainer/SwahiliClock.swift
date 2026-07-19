/// Swahili saa-system clock times, ported from the prototype `ClockTrainer.tsx`.
/// Saa hour = western hour shifted by 6; day periods asubuhi/mchana/jioni/usiku;
/// "na nusu" for half past, "kasoro dakika ..." counting down past the half hour.
enum SwahiliClock {
    private static let hourWords = ["kumi na mbili", "moja", "mbili", "tatu", "nne", "tano", "sita", "saba", "nane", "tisa", "kumi", "kumi na moja", "kumi na mbili"]

    static let gloss = "Saa ± 6h · asubuhi/mchana/jioni/usiku optional"

    /// Day periods that fit the hour, canonical (display) form first. mchana
    /// = afternoon starts at noon, not 10; the mchana↔jioni boundary isn't
    /// fixed, so both are accepted across the late-afternoon overlap. The
    /// period is optional (see `accepted`), so these only widen what counts.
    private static func periods(hours: Int) -> [String] {
        switch hours {
        case 4..<12:  return ["asubuhi"]
        case 12..<15: return ["mchana"]
        case 15..<16: return ["mchana", "jioni"]
        case 16..<18: return ["jioni", "mchana"]
        case 18..<19: return ["jioni"]
        default:      return ["usiku"]
        }
    }

    /// Canonical display string, with the primary day period appended.
    /// Any minute is spelled out (`SwahiliNumbers.cardinal`).
    static func time(hours: Int, minutes: Int) -> String {
        core(hours: hours, minutes: minutes) + " " + periods(hours: hours)[0]
    }

    /// All accepted spellings: the bare reading (period optional) plus one per
    /// plausible day period. First is the period-less form.
    static func accepted(hours: Int, minutes: Int) -> [String] {
        let base = core(hours: hours, minutes: minutes)
        return [base] + periods(hours: hours).map { "\(base) \($0)" }
    }

    /// The time reading without any day period.
    private static func core(hours: Int, minutes: Int) -> String {
        let saaHour = (hours + 6) % 12
        let nextSaaHour = (saaHour + 1) % 12
        let hWord = hourWords[saaHour]
        let nextWord = hourWords[nextSaaHour]

        if minutes == 0 { return "Saa \(hWord)" }
        if minutes == 30 { return "Saa \(hWord) na nusu" }
        if minutes < 30 { return "Saa \(hWord) na dakika \(SwahiliNumbers.cardinal(minutes))" }
        return "Saa \(nextWord) kasoro dakika \(SwahiliNumbers.cardinal(60 - minutes))"
    }
}
