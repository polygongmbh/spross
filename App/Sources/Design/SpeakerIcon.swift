import SwiftUI

// MARK: - SpeakerIcon
//
// Marks a word as audible — decoration only. The tap gesture and its
// accessibility action always live on the surrounding text/card (VoiceOver
// reads the word, never a control) — see `VocabCardView.headline` and
// `HearPromptCard`. Pulses gently while its word is sounding, the shape
// Duolingo's speaker rides; Reduce Motion drops the pulse and just snaps.

struct SpeakerIcon: View {
    enum Size {
        /// Beside a word: card headline, catalog row.
        case small
        /// The whole-card affordance on a pure-listening drill — big, but
        /// never circled or filled, so it never reads as a button.
        case large

        var pointSize: CGFloat {
            switch self {
            case .small: return 13
            case .large: return 32
            }
        }
    }

    var size: Size = .small
    var isPlaying: Bool = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Image(systemName: "speaker.wave.2.fill")
            .font(.system(size: size.pointSize, weight: .semibold))
            .foregroundStyle(size == .small ? Color.dlTextSecondary : Color.dlAccent)
            .scaleEffect(pulsing ? 1.16 : 1.0)
            .animation(
                pulsing
                    ? .easeInOut(duration: 0.35).repeatForever(autoreverses: true)
                    : .easeOut(duration: 0.15),
                value: pulsing
            )
            .accessibilityHidden(true) // decorative; the word/card carries the tap action & label
    }

    private var pulsing: Bool { isPlaying && !reduceMotion }
}

// MARK: - Previews

#Preview("Speaker icon") {
    VStack(spacing: DL.Space.xl) {
        HStack(spacing: DL.Space.l) {
            SpeakerIcon(size: .small, isPlaying: false)
            SpeakerIcon(size: .small, isPlaying: true)
        }
        HStack(spacing: DL.Space.l) {
            SpeakerIcon(size: .large, isPlaying: false)
            SpeakerIcon(size: .large, isPlaying: true)
        }
    }
    .padding(DL.Space.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.dlBackground)
}
