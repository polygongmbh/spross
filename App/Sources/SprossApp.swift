import AVFoundation
import SwiftUI

@main
struct SprossApp: App {
    @State private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // why: the standing category for everything the app fires by itself,
        // set once and never activated by hand — .ambient, so the ring/silent
        // switch keeps its authority over autoplay and the feedback chimes
        // alike, unless reading aloud was switched on by hand. A deliberate tap
        // raises it per sound; the whole rule lives in AudioSession.
        AudioSession.adopt(.stored)
    }

    var body: some Scene {
        WindowGroup {
            RootView(model: model)
                .onChange(of: scenePhase) { _, phase in
                    // why: leaving the app flushes the debounced save so no
                    // answered review is ever lost;
                    // returning re-checks the join stamp (source/catalog may
                    // have moved) and refreshes time-derived stats.
                    if phase == .background {
                        model.persistNow()
                    } else if phase == .active {
                        model.handleForeground()
                    }
                }
        }
    }
}
