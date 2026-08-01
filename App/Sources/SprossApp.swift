import AVFoundation
import SwiftUI

@main
struct SprossApp: App {
    @State private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // why: one category for the whole process, set once and never
        // activated by hand — .ambient mixes with whatever else is playing and
        // follows the ring/silent switch, so a spoken word is as silenceable
        // as the feedback chimes (Sounds.swift) already are.
        try? AVAudioSession.sharedInstance().setCategory(.ambient)
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
