import SwiftUI

// MARK: - ConfettiView
//
// Paper confetti falling across a whole screen, drawn as ONE Canvas rather
// than as a view per piece: a hundred animated SwiftUI views would each carry
// their own layer and animation, where a Canvas is a single redraw per frame.
//
// Nothing is stored per piece. Every property — lane, speed, sway, spin,
// colour — is derived from (index, wave) through a hash, so the pieces are
// varied but reproducible, and no state has to survive a redraw.
//
// Motion is deliberately not uniform: real confetti falls at different rates,
// swings on its own phase, and flickers as it turns edge-on. Those three
// together are what separate falling paper from falling dots.

struct ConfettiView: View {
    /// Bump to throw a fresh handful — waves ADD, they never replace, so a
    /// replay lands in whatever is still in the air.
    var run: Int = 0
    /// Pieces per wave.
    var pieceCount: Int = 130
    /// How long a wave keeps launching pieces. They come front-loaded across
    /// it, so a wave opens thick and thins out instead of falling as one block.
    var emission: Double = 4.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var waves: [Wave] = []
    @State private var nextWave = 0

    /// One handful, identified so its own timer can retire exactly it.
    private struct Wave: Identifiable {
        let id: Int
        let start: Date
    }

    /// Poster palette, minus the wrong-answer brick. Paper, not signal colour.
    private static let palette: [Color] = [
        .dlAccent, .dlTeal, .dlSuccess, .dlAmber, .dlDer, .dlDie, .dlDas,
    ]

    /// Emission window plus the longest fall still to come after it.
    private var life: Double { emission + 3.2 }

    var body: some View {
        Group {
            if !waves.isEmpty {
                TimelineView(.animation) { timeline in
                    Canvas { context, size in
                        for wave in waves {
                            var layer = context
                            let elapsed = timeline.date.timeIntervalSince(wave.start)
                            // why: the tail fade catches whatever is still airborne
                            // when a wave retires, so nothing pops out mid-screen.
                            layer.opacity = min(1, max(0, (life - elapsed) / 1.0))
                            draw(&layer, size: size, elapsed: elapsed, wave: wave.id)
                        }
                    }
                }
            }
        }
        .allowsHitTesting(false)
        .onAppear(perform: launch)
        .onChange(of: run) { _, _ in launch() }
    }

    /// Adds a wave and arms its retirement — without that the TimelineView
    /// would keep asking for frames long after the last piece left the screen.
    private func launch() {
        guard !reduceMotion else { return }
        let wave = Wave(id: nextWave, start: Date())
        nextWave += 1
        waves.append(wave)
        // why: taps can come faster than waves retire; past a few in the air
        // the oldest is the thinnest, so it is the one to drop.
        if waves.count > 4 { waves.removeFirst() }
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(life))
            waves.removeAll { $0.id == wave.id }
        }
    }

    private func draw(_ context: inout GraphicsContext, size: CGSize,
                      elapsed: Double, wave: Int) {
        for index in 0..<pieceCount {
            // Squaring the launch spread front-loads it: most of the handful is
            // away in the first moment, the rest keeps trickling after it.
            let age = elapsed - pow(random(wave, index, 1), 2.2) * emission
            guard age > 0 else { continue }

            // Constant fall plus a gentle pull, so late pieces are visibly quicker
            // than they started — paper does not settle into one terminal speed.
            let speed = 210 + random(wave, index, 2) * 210
            let y = -40 + age * speed + 30 * age * age
            guard y < size.height + 40 else { continue }

            let sway = 6 + random(wave, index, 4) * 26
            let phase = (age + random(wave, index, 6) * 6) * (0.35 + random(wave, index, 5) * 0.7)
            let x = random(wave, index, 3) * size.width + sin(phase * 2 * .pi) * sway

            let scale = 0.7 + random(wave, index, 10) * 0.7
            let width = 7.0 * scale
            let height = (index % 3 == 2 ? 16.0 : 10.0) * scale
            let rect = CGRect(x: -width / 2, y: -height / 2, width: width, height: height)
            let shape = index % 3 == 1
                ? Path(ellipseIn: rect)
                : Path(roundedRect: rect, cornerRadius: 1.5)

            context.drawLayer { layer in
                layer.opacity = 0.75 + random(wave, index, 11) * 0.25
                layer.translateBy(x: x, y: y)
                layer.rotate(by: .radians(random(wave, index, 9) * .pi))
                // why: the horizontal squash IS the tumble — a piece turning
                // edge-on narrows to nothing and flickers back, which reading
                // as depth is the whole difference from a spinning sticker.
                layer.scaleBy(x: cos(age * (1.6 + random(wave, index, 8) * 3.4)), y: 1)
                layer.fill(shape, with: .color(Self.palette[index % Self.palette.count]))
            }
        }
    }

    /// Stable 0..<1 noise for one (wave, piece, property) — SplitMix64 finish,
    /// which decorrelates neighbouring indices well enough that the pieces
    /// never fall in visible rows.
    private func random(_ wave: Int, _ index: Int, _ salt: Int) -> Double {
        var x = UInt64(bitPattern: Int64(index &* 0x9E37_79B1 &+ salt &* 0x85EB_CA77 &+ wave &* 0x2545_F491))
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
