/// Learner-facing reference data for the numbers drill: the place word for
/// each new digit length, and (Swahili only) the tens look-up. Pure data —
/// the UI decides when to surface it.
extension Trainer {

    /// Highest place-value word for a number of the given digit count, shown
    /// the first time the drill reaches a new length ("hundert", "tausend",
    /// "Million" · "mia", "elfu", "milioni"). nil for a single digit and
    /// beyond the supported 10-digit range.
    public static func placeValueHint(digits: Int, language: TrainerLanguage) -> String? {
        guard (2...10).contains(digits) else { return nil }
        switch language {
        case .german: return germanPlace[digits - 2]
        case .swahili: return swahiliPlace[digits - 2]
        case .ukrainian: return ukrainianPlace[digits - 2]
        }
    }

    /// Tens look-up ("10 kumi" … "90 tisini") — Swahili only, whose tens are
    /// the hardest part to recall. nil for languages with regular tens.
    public static func tensReference(language: TrainerLanguage) -> [String]? {
        language == .swahili ? SwahiliNumbers.tensReference : nil
    }

    // Indexed by (digits - 2): index 0 → 2-digit, … index 8 → 10-digit.
    private static let germanPlace = [
        "zehn", "hundert", "tausend", "zehntausend", "hunderttausend",
        "Million", "zehn Millionen", "hundert Millionen", "Milliarde",
    ]
    private static let swahiliPlace = [
        "kumi", "mia", "elfu", "elfu kumi", "elfu mia",
        "milioni", "milioni kumi", "milioni mia", "bilioni",
    ]
    private static let ukrainianPlace = [
        "десять", "сто", "тисяча", "десять тисяч", "сто тисяч",
        "мільйон", "десять мільйонів", "сто мільйонів", "мільярд",
    ]
}
