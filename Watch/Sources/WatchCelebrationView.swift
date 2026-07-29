import SwiftUI
import WatchKit

/// The due batch's end: a short emoji burst with the tally, then back to the
/// start screen on its own — the run had a goal and it was reached, so there is
/// nothing left to decide and no button to press. Free practice never arrives
/// here; it recycles instead of ending.
struct WatchCelebrationView: View {
    let answered: Int
    let onDone: () -> Void

    @State private var burst = false

    /// Angles in degrees (negative = upward), distances in points — tuned for
    /// the smallest watch face, where a phone-sized spread would fly off screen.
    private static let pieces: [(emoji: String, angle: Double, distance: CGFloat)] = [
        ("⭐️", -150, 54), ("✨", -30, 54), ("🌟", -95, 60), ("🎈", -12, 32),
    ]

    private static let linger: Duration = .milliseconds(2000)

    var body: some View {
        VStack(spacing: 2) {
            burstHero
            Text("\(answered) Karten geübt")
                .font(.system(.footnote, design: .rounded, weight: .semibold))
                .foregroundStyle(Color.wlTextSecondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .contentShape(Rectangle())
        // why: an impatient tap skips the linger and lands on the start screen
        // right away, where practice is one more tap.
        .onTapGesture(perform: onDone)
        .onAppear {
            burst = true
            WKInterfaceDevice.current().play(.success)
        }
        .task {
            try? await Task.sleep(for: Self.linger)
            guard !Task.isCancelled else { return }
            onDone()
        }
    }

    private var burstHero: some View {
        ZStack {
            ForEach(Array(Self.pieces.enumerated()), id: \.offset) { index, piece in
                let radians = piece.angle * .pi / 180
                Text(piece.emoji)
                    .font(.body)
                    .offset(x: burst ? piece.distance * cos(radians) : 0,
                            y: burst ? piece.distance * sin(radians) : 0)
                    .scaleEffect(burst ? 1 : 0.2)
                    .opacity(burst ? 1 : 0)
                    .animation(.spring(response: 0.6, dampingFraction: 0.6)
                        .delay(0.1 + Double(index) * 0.06), value: burst)
            }
            Text(verbatim: "🎉")
                .font(.system(size: 52))
                .scaleEffect(burst ? 1 : 0.4)
                .animation(.spring(response: 0.5, dampingFraction: 0.55), value: burst)
        }
        .frame(height: 104)
        .accessibilityHidden(true) // why: purely celebratory; the tally below carries the message
    }
}

#Preview {
    WatchCelebrationView(answered: 12, onDone: {})
}
