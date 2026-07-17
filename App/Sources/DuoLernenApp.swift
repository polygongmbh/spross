import SwiftUI

@main
struct DuoLernenApp: App {
    @State private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView(model: model)
                .onChange(of: scenePhase) { _, phase in
                    // why: leaving the app flushes the debounced save so no
                    // answered review is ever lost (design.md save cadence).
                    if phase == .background {
                        model.persistNow()
                    }
                }
        }
    }
}
