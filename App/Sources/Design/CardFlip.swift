import SwiftUI

// MARK: - Card flip transition
//
// The turn BETWEEN questions: the outgoing card turns away edge-on to one side while the
// incoming one turns in from the other. Within a card the reveal stays a fade — the flip
// only ever marks the switch to the next question.

struct CardFlipEffect: ViewModifier, @MainActor Animatable {
    /// How far a card turns in or out: edge-on, where it vanishes.
    static let edgeOn = 90.0

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
            .opacity(abs(angle) < Self.edgeOn ? 1 : 0)
    }
}

extension AnyTransition {
    /// Insertion flips in from the right, removal flips out to the left.
    @MainActor static var cardFlip: AnyTransition {
        .asymmetric(
            insertion: .modifier(active: CardFlipEffect(angle: CardFlipEffect.edgeOn),
                                 identity: CardFlipEffect(angle: 0)),
            removal: .modifier(active: CardFlipEffect(angle: -CardFlipEffect.edgeOn),
                               identity: CardFlipEffect(angle: 0))
        )
    }
}

// Both at the system's default duration, which settles a verdict before kern's live
// advance beat (`ADVANCE_LIVE_MS`) can move the card on.
extension Animation {
    /// The one animation used for the between-questions flip.
    static let cardFlip = Animation.easeInOut

    /// The flip under Reduce Motion: a short fade, no turn. iOS's alone —
    /// Android scales its animations by the system's animator setting instead.
    static let cardFlipReduced = Animation.easeOut(duration: 0.2)

    /// A reveal growing onto the answer, a correction box coming in, a field taking its verdict.
    static let cardReveal = Animation.easeOut
}
