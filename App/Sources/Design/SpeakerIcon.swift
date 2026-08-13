import SwiftUI

// MARK: - SpeakerIcon
//
// Where there is a row or a line to run down, the CONTENT is the control
// (`pronounceOnTap`, `DLSpokenWord.swift`) and no icon is drawn at all —
// the reference tables, the box list, the produce narration lines.
// Where there is not — a card headline, the big glyph of a pure-listening
// drill, the correction box — the icon IS the control.
//
// One reference page keeps it a control: an alphabet row holds TWO sounds,
// the letter's name and its example word, so which one a tap wants has to be
// aimed at.
//
// Never circled or filled, so it never LOOKS like a button.
// Pulses gently while its word is sounding, the shape Duolingo's speaker
// rides; Reduce Motion drops the pulse and just snaps.

struct SpeakerIcon: View {
    enum Size {
        /// Beside a word: a card headline, the correction box, an alphabet row.
        case small
        /// Alone on a pure-listening drill — the card's only content.
        case large

        var pointSize: CGFloat {
            switch self {
            case .small: return 13
            case .large: return 40
            }
        }

        /// The tap target, well past the glyph itself (Apple's 44 pt floor;
        /// large gets more still — it is the one thing on the card to hit).
        var hitTarget: CGFloat {
            switch self {
            case .small: return 44
            case .large: return 88
            }
        }
    }

    var size: Size = .small
    var isPlaying: Bool = false
    /// nil where nothing can be heard — renders dimmed and inert rather than
    /// vanishing, on a card where the glyph is the only content
    /// (`HearPromptCard`).
    /// Callers that draw the icon only where audio exists —
    /// the correction box, `DLSpokenWord`, an alphabet row — never pass nil.
    var pronounce: (() -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Button { pronounce?() } label: {
            Image(systemName: "speaker.wave.2.fill")
                .font(.system(size: size.pointSize, weight: .semibold))
                .foregroundStyle(size == .small ? Color.dlTextSecondary : Color.dlAccent)
                .opacity(pronounce == nil ? 0.35 : 1)
                .scaleEffect(pulsing ? 1.16 : 1.0)
                .animation(
                    pulsing
                        ? .easeInOut(duration: 0.35).repeatForever(autoreverses: true)
                        : .easeOut(duration: 0.15),
                    value: pulsing
                )
                .frame(minWidth: size.hitTarget, minHeight: size.hitTarget)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(pronounce == nil)
    }

    private var pulsing: Bool { isPlaying && !reduceMotion }
}

// MARK: - Previews

#Preview("Speaker icon") {
    VStack(spacing: DL.Space.xl) {
        HStack(spacing: DL.Space.l) {
            SpeakerIcon(size: .small, isPlaying: false, pronounce: {})
            SpeakerIcon(size: .small, isPlaying: true, pronounce: {})
        }
        HStack(spacing: DL.Space.l) {
            SpeakerIcon(size: .large, isPlaying: false, pronounce: {})
            SpeakerIcon(size: .large, isPlaying: true, pronounce: {})
            SpeakerIcon(size: .large, isPlaying: false, pronounce: nil)
        }
    }
    .padding(DL.Space.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
}
