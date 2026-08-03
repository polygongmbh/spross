import SwiftUI

/// Single-screen app: Heute is the root and holds the box itself, as the forest
/// at its foot. An area pushes from its grove there or from a search hit;
/// settings push from the gear. Onboarding sheet on first launch, full-screen
/// session cover.
struct RootView: View {
    @Bindable var model: AppModel

    /// The area being browsed — nil is Heute. Search and the forest both set it,
    /// so a hit and a tap land on one screen rather than two ways in.
    @State private var openedArea: String?
    @State private var settingsPresented = false
    @State private var searchPresented = false
    @State private var sprouting = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Group {
            if model.phase == .loading {
                loading
            } else {
                home
            }
        }
        // why: render chrome in the user's KNOWN language (German learners see
        // English) — SwiftUI resolves every Text/LocalizedStringKey against it.
        .environment(\.locale, model.knownLocale)
        .sheet(isPresented: onboardingPresented) {
            // why: onboarding sets its own locale from the language being
            // picked, so it re-renders as the user changes the pick.
            OnboardingView(model: model)
                .interactiveDismissDisabled()
        }
        .fullScreenCover(isPresented: $model.sessionPresented) {
            SessionView(model: model)
                .environment(\.locale, model.knownLocale)
        }
        .task {
            await model.start()
            if model.uitestScreen == "settings" {
                settingsPresented = true
            }
        }
    }

    private var home: some View {
        NavigationStack {
            HeuteView(model: model, openArea: { openedArea = $0 })
                .navigationDestination(item: $openedArea) { area in
                    AreaView(model: model, area: area)
                }
                .navigationDestination(isPresented: $settingsPresented) {
                    SettingsView(model: model)
                }
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            searchPresented = true
                        } label: {
                            Image(systemName: "magnifyingglass")
                        }
                        .accessibilityLabel("box.search")
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            settingsPresented = true
                        } label: {
                            Image(systemName: "gearshape")
                        }
                        .accessibilityLabel("nav.settings")
                    }
                }
                .toolbarBackground(.hidden, for: .navigationBar)
                .sheet(isPresented: $searchPresented) {
                    BoxSearchView(model: model, reveal: { openedArea = $0 })
                }
        }
        .tint(.dlAccent)
    }

    private var loading: some View {
        VStack(spacing: DL.Space.l) {
            Text(verbatim: "🌱")
                .font(.system(size: 56))
                .dlSway(angle: 4, period: 2.2)
                .scaleEffect(sprouting ? 1.08 : 0.92)
                .animation(
                    reduceMotion ? nil
                        : .easeInOut(duration: 1.4).repeatForever(autoreverses: true),
                    value: sprouting
                )
                .onAppear { sprouting = true }
            ProgressView()
                .tint(.dlSuccess)
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
