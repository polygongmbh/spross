import SwiftUI

/// The audio question, in the same card chrome as TrainerPromptCard: a caption
/// naming what is being asked, one large replay button, and — for a gap
/// question — the example word with the asked grapheme blanked.
///
/// No answer ever renders here, and that is the whole point: everything the
/// learner is given is the sound, plus whatever the gap word already shows.
///
/// Two silences can meet a learner on this card, and it names both. The app's
/// own read-aloud switch is one tap to undo, so the card BLOCKS with that tap
/// instead of showing a speaker button that does nothing. The hardware silent
/// switch cannot be detected at all (Sounds.swift doctrine) — so a run's first
/// question states it once, rather than leaving a dead screen to be guessed at.
struct HearPromptCard: View {
    /// What is being asked ("Welcher Buchstabe ist das?" …).
    let question: LocalizedStringKey
    /// The language the prompt is spoken in — named in the caption.
    let language: String
    /// `Na＿t` for a gap question; nil where a letter's name is spoken.
    var gapText: String?
    /// nil where the device can neither play nor speak this prompt.
    var replay: (() -> Void)?
    var muted: Bool
    var unmute: () -> Void
    /// First task of a run only — a line repeated on every question is noise.
    var showsSilentSwitchHint: Bool
    /// VoiceOver lands here on every task change: the question is one action
    /// away rather than somewhere below the caption.
    var replayFocus: AccessibilityFocusState<Bool>.Binding

    @Environment(\.locale) private var locale

    var body: some View {
        VStack(spacing: DL.Space.m) {
            caption
            replayButton
            if let gapText {
                Text(verbatim: gapText)
                    .font(.system(size: 40, weight: .bold, design: .rounded))
                    .foregroundStyle(Color.dlTextPrimary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
            }
            if muted {
                unmuteRow
            } else if showsSilentSwitchHint {
                Text("letters.silentSwitchHint")
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(DL.Space.l)
        .frame(maxWidth: .infinity)
        // why: the sibling prompt card's height, so the two drills do not jump
        // a layout apart when a learner moves between them.
        .frame(minHeight: 185)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.card, style: .continuous)
                .fill(Color.dlSurface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: DL.Radius.card, style: .continuous)
                .strokeBorder(Color.dlSeparator.opacity(0.6), lineWidth: 1)
        )
        .dlCardShadow()
    }

    private var caption: some View {
        Text.joined(Text(question),
                    Text("trainer.prompt.inLanguage \(LanguageNames.display(language, locale: locale, catalog: nil))"))
            .font(DL.Fonts.caption)
            .foregroundStyle(Color.dlTextSecondary)
            .textCase(.uppercase)
            .multilineTextAlignment(.center)
    }

    /// Well past the 44 pt floor: on this card it is not a control beside the
    /// content, it IS the content.
    private var replayButton: some View {
        Button { replay?() } label: {
            Image(systemName: "speaker.wave.2.fill")
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(replay == nil ? Color.dlTextSecondary : Color.dlOnColor)
                .frame(width: 72, height: 72)
                .background(Circle().fill(replay == nil ? Color.dlSurfaceTint : Color.dlAccent))
        }
        .disabled(replay == nil)
        .accessibilityLabel("a11y.replayPrompt")
        .accessibilityAddTraits(.startsMediaSession)
        .accessibilityFocused(replayFocus)
    }

    private var unmuteRow: some View {
        VStack(spacing: DL.Space.s) {
            Text("letters.audioOff")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
            Button("letters.enableSound", action: unmute)
                .buttonStyle(DLSoftButtonStyle())
        }
        .transition(.opacity)
    }
}

// MARK: - Previews

private struct HearPromptPreviewHost: View {
    @AccessibilityFocusState private var focus: Bool

    var body: some View {
        VStack(spacing: DL.Space.xl) {
            HearPromptCard(question: "letters.hear", language: "uk",
                           replay: {}, muted: false, unmute: {},
                           showsSilentSwitchHint: true, replayFocus: $focus)
            HearPromptCard(question: "letters.spell", language: "de",
                           gapText: "Na＿t", replay: {}, muted: true, unmute: {},
                           showsSilentSwitchHint: false, replayFocus: $focus)
        }
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.dlBackground)
    }
}

#Preview("Hear prompt") {
    HearPromptPreviewHost()
}

#Preview("Hear prompt · dark") {
    HearPromptPreviewHost()
        .preferredColorScheme(.dark)
}
