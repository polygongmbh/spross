import SwiftUI
import DuoKern

@main
struct DuoLernenApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}

// Placeholder until the UI wave lands.
struct RootView: View {
    var body: some View {
        Text("DuoLernen")
            .font(.largeTitle.bold())
    }
}
