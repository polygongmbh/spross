import Foundation

/// An FSRS rating as the watch knows it; it travels to the phone by name.
enum WatchRating: String, Sendable {
    case again, hard, good, easy
}

/// Response-time → FSRS rating for the watch's multiple-choice practice.
/// The watch has no keyboard, so it grades by RECOGNITION latency:
/// a fast right tap earns a higher rating, a slow one less, and a wrong one Again.
///
/// Policy: breadth of exposure over perfect single-word retention — Easy is a
/// deliberate, reachable rating, so effortless words stretch out fast and make
/// room for new material.
///
/// Differs from the phone's kern `SelfGrading` on purpose: picking one of four
/// options is recognition, not recall, and a slow right tap may be a guess, so
/// the clock here grades down to Hard as well as up to Easy.
enum WatchGrading {

    // Field-calibratable — reading four options costs time, more for long
    // words, so the "fast" budget scales with the total option text length.
    static let baseMs = 1800       // fixed reading/react floor
    static let perCharMs = 15      // per displayed option character
    static let easyFactor = 0.5    // Easy window is the inner half of Good

    /// Again when wrong; else Easy very fast, Good fast, Hard slow —
    /// thresholds scaled by the total option characters.
    static func rating(correct: Bool, elapsedMs: Int, optionChars: Int) -> WatchRating {
        guard correct else { return .again }
        let goodBudget = Double(baseMs + perCharMs * optionChars)
        let easyBudget = goodBudget * easyFactor
        let elapsed = Double(elapsedMs)
        if elapsed <= easyBudget { return .easy }
        if elapsed <= goodBudget { return .good }
        return .hard
    }
}
