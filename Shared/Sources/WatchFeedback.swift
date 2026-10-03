import Foundation
#if os(watchOS)
import WatchKit
#endif

/// What an answer's rating LOOKS and FEELS like on the wrist.
///
/// The watch derives its FSRS rating rather than asking for one
/// (`WatchGrading`), and it marks what earned the higher ratings — speed —
/// rather than naming a grade: a bolt says why it came, where a grade's name
/// would leave that a guess. The haptic says the coarser thing the wrist
/// already knows: that went well, or it did not.
///
/// Ratings are the raw FSRS 1–4 (`WatchGrading.rating`), not an enum: this
/// file sits beside the grader that produces them and the model that sends
/// them, and neither has ever needed a richer type.
enum WatchFeedback {

    /// A quick right answer's mark: two bolts for Easy, one for Good. A slow
    /// right answer has the green tile alone, and a miss the red.
    static func speedMark(forRating rating: Int) -> String? {
        switch rating {
        case 4: return "⚡⚡"
        case 3: return "⚡"
        default: return nil
        }
    }

    #if os(watchOS)
    /// Correct or not, in the hand. A right answer comes all the time, so it is
    /// the lightest tap there is (`.click`, also the quietest) for every
    /// affirming rating — the speed mark is the finer channel. A miss is the rare
    /// event and gets `.failure`, the type whose documented meaning it is;
    /// `.retry` would promise a second try the quiz never offers.
    static func haptic(forRating rating: Int) -> WKHapticType {
        rating >= 2 ? .click : .failure
    }
    #endif
}
