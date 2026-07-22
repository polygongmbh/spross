import SwiftUI

@main
struct SprossWatchApp: App {
    @State private var model = WatchModel()

    var body: some Scene {
        WindowGroup {
            WatchHomeView(model: model)
                .task { model.start() }
        }
    }
}
