import Foundation
#if os(watchOS)
import WatchKit
#endif

/// What an answer's rating LOOKS and FEELS like on the wrist.
///
/// The watch derives its FSRS rating rather than asking for one
/// (`WatchGrading`), and it never labels the result: the learner picked a tile,
/// not a grade, and a screen that announced "Hard" would invite gaming the
/// latency it is measuring. So the emoji is a quiet tell — a hand ladder an
/// insider can read and nobody else has to — while the haptic says the coarser
/// thing the wrist already knows: that went well, or it did not.
///
/// Ratings are the raw FSRS 1–4 (`WatchGrading.rating`), not an enum: this
/// file sits beside the grader that produces them and the model that sends
/// them, and neither has ever needed a richer type.
enum WatchFeedback {

    /// The tell, in one hand each: 🙌 effortless, 👍 known, 👌 got there,
    /// 👋 a nudge to come back. Rising confidence, so the ladder reads even
    /// for someone who has never been told what it means.
    static func emoji(forRating rating: Int) -> String {
        switch rating {
        case 4: return "🙌"
        case 3: return "👍"
        case 2: return "👌"
        default: return "👋"
        }
    }

    #if os(watchOS)
    /// Correct or not, in the hand: the two affirming ratings share `.success`
    /// (the emoji is the finer channel), a slow correct answer gets `.retry`'s
    /// double tap, and a miss gets `.failure`.
    ///
    /// The end-of-batch celebration keeps its own `.success` — a whole screen
    /// away from a tile, so the two never read as the same event.
    static func haptic(forRating rating: Int) -> WKHapticType {
        switch rating {
        case 4, 3: return .success
        case 2: return .retry
        default: return .failure
        }
    }
    #endif
}
