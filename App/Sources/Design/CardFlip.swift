import SwiftUI
import SprossKern

// MARK: - Card flip transition
//
// The turn BETWEEN questions, timed and angled by kern's `CardMotion`. Within a card the
// reveal stays a fade — the flip only ever marks the switch to the next question.

struct CardFlipEffect: ViewModifier, @MainActor Animatable {
    var angle: Double

    var animatableData: Double {
        get { angle }
        set { angle = newValue }
    }

    func body(content: Content) -> some View {
        content
            .rotation3DEffect(.degrees(angle), axis: (x: 0, y: 1, z: 0), perspective: 0.4)
            // why: hide the mirrored "backface" at ±90° so the outgoing and
            // incoming card each show only their front half of the flip.
            .opacity(CardMotion.shared.flipShows(angle: angle) ? 1 : 0)
    }
}

extension AnyTransition {
    /// Insertion flips in from the right, removal flips out to the left.
    @MainActor static var cardFlip: AnyTransition {
        let motion = CardMotion.shared
        return .asymmetric(
            insertion: .modifier(active: CardFlipEffect(angle: motion.flipAngle(progress: 0, incoming: true)),
                                 identity: CardFlipEffect(angle: 0)),
            removal: .modifier(active: CardFlipEffect(angle: motion.flipAngle(progress: 1, incoming: false)),
                               identity: CardFlipEffect(angle: 0))
        )
    }
}

extension Animation {
    /// The one animation used for the between-questions flip.
    static let cardFlip = Animation.easeInOut(duration: Double(CardMotion.shared.FLIP_MS) / 1000)

    /// A reveal growing onto the answer, a correction box coming in, a field taking its verdict.
    static let cardReveal = Animation.easeOut(duration: Double(CardMotion.shared.REVEAL_MS) / 1000)
}
