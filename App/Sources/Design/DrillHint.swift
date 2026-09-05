import SwiftUI

/// A short fact about THIS prompt, in the language being LEARNED — the place
/// word the first time a length appears, the word a date's pattern adds the
/// first time it is asked ("Neue Stelle: mia", "Neu: mwaka wa").
///
/// One shape for both drill cards, because it is one rule: a first-sight hint
/// hands over target-language material the card cannot otherwise teach, and it
/// is scaffolding for a prompt still unanswered, so the reveal TAKES its slot
/// rather than stacking under it (`docs/surfaces.md`).
struct DrillHint {
    let icon: String
    let text: LocalizedStringKey
}

/// [DrillHint] as the cards draw it — a tinted capsule inside the card, so its
/// coming and going never moves the field or the button below.
struct DrillHintPill: View {
    let hint: DrillHint

    init(_ hint: DrillHint) { self.hint = hint }

    var body: some View {
        Label(hint.text, systemImage: hint.icon)
            .font(Theme.typography.caption)
            .foregroundStyle(Theme.colors.accent)
            .padding(.horizontal, Theme.spacing.md)
            .padding(.vertical, Theme.spacing.sm)
            .background(
                Capsule().fill(Theme.colors.surfaceTint)
            )
    }
}
