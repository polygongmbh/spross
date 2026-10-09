import SwiftUI

/// Where a question card stands, for the review session and every drill:
/// the switch to the next question flips the card (`CardFlip`), keyed on [key] —
/// a reveal within one question changes nothing here.
/// The answer area stands outside, so the field and its focus never turn with the card.
/// The Android twin is `QuestionStage`.
struct QuestionStage<Key: Hashable, Card: View>: View {
    let key: Key
    @ViewBuilder let card: () -> Card

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        // why: a ZStack, so the outgoing and incoming card overlap during the flip
        // instead of stacking; .id gives each question its own identity.
        ZStack {
            card()
                .id(key)
                .transition(reduceMotion ? .opacity : .cardFlip)
        }
        // why: the stage animates its own switch — a review's next turn begins from an
        // onChange, outside any withAnimation, and would otherwise swap the card unturned.
        .animation(reduceMotion ? .easeOut(duration: 0.2) : .cardFlip, value: key)
    }
}
