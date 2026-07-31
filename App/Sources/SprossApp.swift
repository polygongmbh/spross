import SwiftUI

@main
struct SprossApp: App {
    @State private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase

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
