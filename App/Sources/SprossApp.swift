import AVFoundation
import SwiftUI
import SprossKern

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
        AudioSession.adopt(Pronouncer.shared.readAloud)
    }

    var body: some Scene {
        WindowGroup {
            RootView(model: model)
                // why: a challenge link — the site's universal link or the app's own scheme —
                // opens the numbers page with its code entered and accepted.
                .onOpenURL { url in
                    if let code = NumbersChallenge.companion.codeInLink(url: url.absoluteString) {
                        model.pendingChallengeCode = code
                    }
                }
                .onChange(of: scenePhase) { _, phase in
                    // why: leaving the app writes the box, snapshots and all,
                    // so no answered review is ever lost;
                    // returning re-checks the join stamp (source/catalog may
                    // have moved) and refreshes time-derived stats.
                    if phase == .background {
                        model.saveNow()
                    } else if phase == .active {
                        model.handleForeground()
                    }
                }
        }
    }
}
