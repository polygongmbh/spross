import Foundation

/// Response-time → FSRS raw rating (1–4) for the watch's multiple-choice
/// practice. The watch has no keyboard, so it grades by RECOGNITION latency:
/// a fast correct tap means the word is already well remembered and earns a
/// higher rating; a slow one earns less. Wrong is always Again.
///
/// Policy: breadth of exposure over perfect single-word retention — Easy is a
/// deliberate, reachable rating, so effortless words stretch out fast and make
/// room for new material.
enum WatchGrading {

    // Field-calibratable — reading four options costs time, more for long
    // words, so the "fast" budget scales with the total option text length.
    static let baseMs = 800        // fixed reading/react floor
    static let perCharMs = 30      // per displayed option character
    static let easyFactor = 0.5    // Easy window is the inner half of Good

    /// FSRS raw rating: Again(1) when wrong; else Easy(4) very-fast, Good(3)
    /// fast, Hard(2) slow — thresholds scaled by the total option characters.
    static func rating(correct: Bool, elapsedMs: Int, optionChars: Int) -> Int {
        guard correct else { return 1 }
        let goodBudget = Double(baseMs + perCharMs * optionChars)
        let easyBudget = goodBudget * easyFactor
        let elapsed = Double(elapsedMs)
        if elapsed <= easyBudget { return 4 }
        if elapsed <= goodBudget { return 3 }
        return 2
    }
}
