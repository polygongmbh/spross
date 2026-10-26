import SwiftUI

@main
struct SprossWatchApp: App {
    @State private var model = WatchModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            WatchHomeView(model: model)
                .task { model.start() }
                // why: leaving the foreground hands the phone the answers held since the last
                // hand-over, so it applies and reschedules them in one pass; backgrounding also
                // ends the round — a run resumed hours later is stale, and starting one is a tap.
                // (`.inactive` is only a wrist drop or an overlay: flush, keep the round.)
                .onChange(of: scenePhase) { _, phase in
                    if phase == .background {
                        model.endSession()
                    } else if phase != .active {
                        model.connectivity.flush()
                    }
                }
        }
    }
}
