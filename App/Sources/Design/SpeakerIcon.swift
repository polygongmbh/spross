import SwiftUI

// MARK: - SpeakerIcon
//
// The one audio affordance: the icon IS the control, beside a word on a card
// or standing alone (big, never circled or filled, so it never LOOKS like a
// button) on a pure-listening drill. One tap target instead of three
// different ones (a word, a row, a whole card) is the simpler rule to learn
// once and reuse.
//
// Exception: the box list (`BoxCardRow`) has no icon at all — a row already
// carries a wake/pack control competing for width, so there the whole row is
// the tap target instead (`pronounceOnTap`, `SessionView+Audio.swift`).
//
// Pulses gently while its word is sounding, the shape Duolingo's speaker
// rides; Reduce Motion drops the pulse and just snaps.

struct SpeakerIcon: View {
    enum Size {
        /// Beside a word: card headline, catalog row.
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
    /// (`HearPromptCard`). Callers that only ever show the icon when audio
    /// exists (`VocabCardView`, the box list) never pass nil.
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
