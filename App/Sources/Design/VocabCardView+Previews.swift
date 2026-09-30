import SprossKern
import SwiftUI

// MARK: - Previews

#Preview("Shared screen · prompt") {
    VocabCardView(
        emoji: "🧊",
        prompt: .init(text: "friji"),
        answer: .init(text: "Kühlschrank", article: .init("der", gender: .masculine),
                      plural: "Pl. Kühlschränke"),
        note: nil,
        revealed: false
    )
    .padding(Theme.spacing.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Theme.colors.background)
}

#Preview("Shared screen · revealed") {
    VocabCardView(
        emoji: "🍳",
        emojiCue: emojiCue(givesAnswerAway: true),
        prompt: .init(text: "kikaango"),
        answer: .init(text: "Pfanne", article: .init("die", gender: .feminine),
                      plural: "Pl. Pfannen"),
        note: "wörtlich: kleines Bratgefäß",
        revealed: true
    )
    .padding(Theme.spacing.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Theme.colors.background)
}

#Preview("Shared screen · with vs. without emoji") {
    VStack(spacing: Theme.spacing.lg) {
        // Not-yet-sticking word: emoji as light support.
        VocabCardView(
            emoji: "🥄",
            prompt: .init(text: "Löffel", article: .init("der", gender: .masculine),
                          plural: "Pl. Löffel"),
            answer: .init(text: "kijiko"),
            note: nil,
            revealed: false,
            arrangement: .beside
        )
        // Sticking word (or a verb/phrase): no circle, word-focused.
        VocabCardView(
            emoji: nil,
            prompt: .init(text: "rennen"),
            answer: .init(text: "kukimbia"),
            note: nil,
            revealed: false,
            arrangement: .beside
        )
    }
    .padding(Theme.spacing.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Theme.colors.background)
}

#Preview("Owns the screen · before and after the meaning") {
    VStack(spacing: Theme.spacing.lg) {
        VocabCardView(
            emoji: "🍚",
            prompt: .init(text: "mchele"),
            answer: .init(text: "Reis"),
            note: nil,
            revealed: false,
            arrangement: .above
        )
        VocabCardView(
            emoji: "🔪",
            prompt: .init(text: "kisu", alternates: "auch: chombo"),
            answer: .init(text: "Messer"),
            note: nil,
            revealed: true,
            arrangement: .above
        )
    }
    .padding(Theme.spacing.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Theme.colors.background)
    .preferredColorScheme(.dark)
}
