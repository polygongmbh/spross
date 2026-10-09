import SwiftUI
import SprossKern

// MARK: - ConfettiView
//
// Paper confetti falling across a whole screen, drawn as ONE Canvas rather than as a view per piece:
// a hundred animated SwiftUI views would each carry their own layer and animation,
// where a Canvas is a single redraw per frame.
// Kern places every piece (`ConfettiFrame`); this runs the clock and inks its pieces in the theme's colors.

struct ConfettiView: View {
    /// Bump to throw a fresh handful — waves ADD, they never replace, so a
    /// replay lands in whatever is still in the air.
    var run: Int = 0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var waves: [Wave] = []
    @State private var nextWave = 0
    /// Kern's buffer, filled in place every frame, so a frame crosses the bridge as numbers rather than objects.
    @State private var frame = ConfettiFrame()

    /// One handful, identified so its own timer can retire exactly it.
    private struct Wave: Identifiable {
        let id: Int
        let start: Date
    }

    private static let palette: [Color] = ConfettiFrame.companion.INKS.map { color($0) }

    var body: some View {
        // why: an invisible but REAL view underneath, not a bare `if`. Before a
        // wave exists there is nothing to draw, and an empty container dropped
        // into an already-visible screen does not reliably get its onAppear —
        // which is how a celebration that starts later than its screen (a drill
        // record) ended up silent while the same view worked on arrival.
        Color.clear
            .overlay {
                if !waves.isEmpty {
                    TimelineView(.animation) { timeline in
                        Canvas { context, size in
                            for wave in waves {
                                var layer = context
                                let elapsed = timeline.date.timeIntervalSince(wave.start)
                                layer.opacity = ConfettiFrame.companion.fade(elapsed: elapsed)
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
        if waves.count > Int(ConfettiFrame.companion.MAX_WAVES) { waves.removeFirst() }
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(ConfettiFrame.companion.LIFE))
            waves.removeAll { $0.id == wave.id }
        }
    }

    private func draw(_ context: inout GraphicsContext, size: CGSize, elapsed: Double, wave: Int) {
        let k = ConfettiFrame.companion
        let count = frame.fill(wave: Int32(wave), elapsed: elapsed, width: size.width, height: size.height)
        let values = frame.values
        for piece in 0..<count {
            let o = piece * k.STRIDE
            func at(_ field: Int32) -> Double { values.get(index: o + field) }
            let width = at(k.WIDTH), height = at(k.HEIGHT)
            let rect = CGRect(x: -width / 2, y: -height / 2, width: width, height: height)
            let shape = at(k.OVAL) > 0 ? Path(ellipseIn: rect) : Path(roundedRect: rect, cornerRadius: k.CORNER)
            context.drawLayer { layer in
                layer.opacity = at(k.ALPHA)
                layer.translateBy(x: at(k.X), y: at(k.Y))
                layer.rotate(by: .radians(at(k.ROTATION)))
                layer.scaleBy(x: at(k.TUMBLE), y: 1)
                layer.fill(shape, with: .color(Self.palette[Int(at(k.INK))]))
            }
        }
    }

    private static func color(_ ink: ConfettiInk) -> Color {
        switch ink {
        case .accent: Theme.colors.accent
        case .teal: Theme.colors.teal
        case .success: Theme.colors.success
        case .amber: Theme.colors.amber
        case .der: Theme.colors.der
        case .die: Theme.colors.die
        case .das: Theme.colors.das
        }
    }
}

// MARK: - Preview

#Preview("Confetti") {
    ZStack {
        Theme.colors.background.ignoresSafeArea()
        ConfettiView()
    }
}
