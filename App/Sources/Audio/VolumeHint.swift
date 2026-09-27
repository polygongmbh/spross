import SwiftUI

/// The line a screen about to play words shows while the device's volume is
/// at or near zero: the words still play, and this asks for them to be heard.
///
/// Read once a second while on screen rather than observed — the volume keys
/// notify nothing a view can hear without an active audio session, which the
/// app holds only for a listening run. Android's `VolumeHint` is the twin.
struct VolumeHint: View {
    /// Whether this screen will make a sound on its own right now — a muted
    /// review loop will not, a letter drill and a listening run always do.
    let active: Bool
    @State private var low = false

    /// Stacks under a screen's top bar at no spacing of its own: hidden, it
    /// costs the layout nothing; shown, it brings its own gap.
    var body: some View {
        VStack(spacing: 0) {
            // why: an always-present anchor, so `.task` has a view to run on
            // while the hint itself is absent.
            Color.clear.frame(height: 0)
            if active && low {
                Label("common.volumeLow", systemImage: "speaker.wave.1")
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.top, Theme.spacing.sm)
                    .transition(.opacity)
            }
        }
        .frame(maxWidth: .infinity)
        .animation(.easeOut(duration: 0.2), value: active && low)
        .task {
            while !Task.isCancelled {
                low = AudioSession.volumeIsLow
                try? await Task.sleep(for: .seconds(1))
            }
        }
    }
}
