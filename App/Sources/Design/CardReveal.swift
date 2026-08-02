import SwiftUI

// MARK: - DLCardReveal
//
// What a card grows when the answer comes out: a short rule, the answer, and
// an optional note under it. The reveal is always BELOW the prompt and always
// looks the same — a vocabulary card and a drill card reveal alike, so the two
// never drift into two different ideas of "the answer".

struct DLCardReveal<Content: View>: View {
    /// Literal gloss ("wörtlich: …") or the sentence's meaning — post-reveal
    /// only, and always the last line.
    var note: String?
    @ViewBuilder var content: Content

    var body: some View {
        VStack(spacing: DL.Space.m) {
            RoundedRectangle(cornerRadius: 1)
                .fill(Color.dlSeparator)
                .frame(width: 44, height: 2)
            content
            if let note {
                // why: subheadline, not caption — post-reveal lines are meant to
                // be read, and 12 pt secondary text is where legibility broke.
                Text(note)
                    .font(DL.Fonts.subheadline)
                    .italic()
                    .foregroundStyle(Color.dlTextSecondary)
                    .multilineTextAlignment(.center)
            }
        }
    }
}

extension View {
    /// The line an amber hold pauses on — a typo's proper spelling, the word
    /// that was heard instead, the other word the answer turned out to be.
    /// Read, not glanced at, so it carries the same weight everywhere.
    func dlPauseLine() -> some View {
        font(DL.Fonts.subheadline)
            .italic()
            .foregroundStyle(Color.dlTextSecondary)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
    }
}

// MARK: - Previews

#Preview("Reveal") {
    VStack(spacing: DL.Space.l) {
        DLCardReveal(note: "wörtlich: kleines Bratgefäß") {
            Text(verbatim: "kikaango")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlAccent)
        }
        Text(verbatim: "Fast! Richtig geschrieben: cuatro")
            .dlPauseLine()
    }
    .padding(DL.Space.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
}
