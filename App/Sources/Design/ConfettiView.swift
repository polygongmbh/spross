import SwiftUI

// MARK: - ConfettiView
//
// Paper confetti falling across a whole screen, drawn as ONE Canvas rather
// than as a view per piece: a hundred animated SwiftUI views would each carry
// their own layer and animation, where a Canvas is a single redraw per frame.
//
// Nothing is stored per piece. Every property — lane, speed, sway, spin,
// colour — is derived from (index, run) through a hash, so the pieces are
// varied but reproducible, the whole field replays by bumping `run`, and no
// state has to survive a redraw.
//
// Motion is deliberately not uniform: real confetti falls at different rates,
// swings on its own phase, and flickers as it turns edge-on. Those three
// together are what separate falling paper from falling dots.

struct ConfettiView: View {
    /// Bump to replay — each value seeds an entirely fresh field.
    var run: Int = 0
    var pieceCount: Int = 120
    /// How long the field lives; the last stretch fades whatever is still airborne.
    var duration: Double = 4.2

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var start = Date()
    @State private var falling = false
    @State private var stopper: Task<Void, Never>?

    /// Poster palette, minus the wrong-answer brick. Paper, not signal colour.
    private static let palette: [Color] = [
        .dlAccent, .dlTeal, .dlSuccess, .dlAmber, .dlDer, .dlDie, .dlDas,
    ]

    var body: some View {
        Group {
            if falling && !reduceMotion {
                TimelineView(.animation) { timeline in
                    Canvas { context, size in
                        draw(&context, size: size,
                             elapsed: timeline.date.timeIntervalSince(start))
                    }
                }
            }
        }
        .allowsHitTesting(false)
        .onAppear(perform: launch)
        .onChange(of: run) { _, _ in launch() }
        .onDisappear { stopper?.cancel() }
    }

    /// Restarts the field and arms the stop — without it the TimelineView would
    /// keep asking for frames long after the last piece has left the screen.
    private func launch() {
        stopper?.cancel()
        start = Date()
        falling = true
        stopper = Task { @MainActor in
            try? await Task.sleep(for: .seconds(duration))
            guard !Task.isCancelled else { return }
            falling = false
        }
    }

    private func draw(_ context: inout GraphicsContext, size: CGSize, elapsed: Double) {
        context.opacity = min(1, max(0, (duration - elapsed) / 0.8))
        for index in 0..<pieceCount {
            let age = elapsed - random(index, 1) * 1.2
            guard age > 0 else { continue }

            // Constant fall plus a gentle pull, so late pieces are visibly quicker
            // than they started — paper does not settle into one terminal speed.
            let speed = 210 + random(index, 2) * 210
            let y = -40 + age * speed + 30 * age * age
            guard y < size.height + 40 else { continue }

            let sway = 6 + random(index, 4) * 26
            let phase = (age + random(index, 6) * 6) * (0.35 + random(index, 5) * 0.7)
            let x = random(index, 3) * size.width + sin(phase * 2 * .pi) * sway

            let scale = 0.7 + random(index, 10) * 0.7
            let width = 7.0 * scale
            let height = (index % 3 == 2 ? 16.0 : 10.0) * scale
            let rect = CGRect(x: -width / 2, y: -height / 2, width: width, height: height)
            let shape = index % 3 == 1
                ? Path(ellipseIn: rect)
                : Path(roundedRect: rect, cornerRadius: 1.5)

            context.drawLayer { layer in
                layer.opacity = 0.75 + random(index, 11) * 0.25
                layer.translateBy(x: x, y: y)
                layer.rotate(by: .radians(random(index, 9) * .pi))
                // why: the horizontal squash IS the tumble — a piece turning
                // edge-on narrows to nothing and flickers back, which reading
                // as depth is the whole difference from a spinning sticker.
                layer.scaleBy(x: cos(age * (1.6 + random(index, 8) * 3.4)), y: 1)
                layer.fill(shape, with: .color(Self.palette[index % Self.palette.count]))
            }
        }
    }

    /// Stable 0..<1 noise for one (piece, property) of this run — SplitMix64
    /// finish, which decorrelates neighbouring indices well enough that the
    /// pieces never fall in visible rows.
    private func random(_ index: Int, _ salt: Int) -> Double {
        var x = UInt64(bitPattern: Int64(index &* 0x9E37_79B1 &+ salt &* 0x85EB_CA77 &+ run &* 0x2545_F491))
        x = (x ^ (x >> 33)) &* 0xFF51_AFD7_ED55_8CCD
        x = (x ^ (x >> 33)) &* 0xC4CE_B9FE_1A85_EC53
        x ^= x >> 33
        return Double(x >> 11) / Double(1 << 53)
    }
}

// MARK: - Preview

#Preview("Confetti") {
    ZStack {
        Color.dlBackground.ignoresSafeArea()
        ConfettiView()
    }
}
