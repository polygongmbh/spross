import SwiftUI

/// Three tabs (Heute / Box / Fortschritt), onboarding sheet on first launch,
/// full-screen session cover.
struct RootView: View {
    @Bindable var model: AppModel

    private enum Tab: String {
        case heute, box, fortschritt
    }

    @State private var selection: Tab = .heute

    var body: some View {
        Group {
            if model.phase == .loading {
                loading
            } else {
                tabs
            }
        }
        .sheet(isPresented: onboardingPresented) {
            OnboardingView(model: model)
                .interactiveDismissDisabled()
        }
        .fullScreenCover(isPresented: $model.sessionPresented) {
            SessionView(model: model)
        }
        .task {
            await model.start()
            if let tab = model.uitestTab.flatMap(Tab.init(rawValue:)) {
                selection = tab
            }
        }
    }

    private var tabs: some View {
        TabView(selection: $selection) {
            HeuteView(model: model)
                .tabItem { Label("Heute", systemImage: "sun.max.fill") }
                .tag(Tab.heute)
            BoxView(model: model)
                .tabItem { Label("Box", systemImage: "shippingbox.fill") }
                .tag(Tab.box)
            FortschrittView(model: model)
                .tabItem { Label("Fortschritt", systemImage: "chart.bar.xaxis") }
                .tag(Tab.fortschritt)
        }
        .tint(.dlAccent)
    }

    private var loading: some View {
        VStack(spacing: DL.Space.l) {
            Text("📦")
                .font(.system(size: 56))
            ProgressView()
                .tint(.dlAccent)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.dlBackground.ignoresSafeArea())
    }

    /// The sheet is driven by the model's phase; it dismisses itself once
    /// onboarding completes (phase → .ready).
    private var onboardingPresented: Binding<Bool> {
        Binding(
            get: { model.phase == .onboarding },
            set: { _ in }
        )
    }
}
