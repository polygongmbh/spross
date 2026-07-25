import SwiftUI

/// Single-screen app: Heute is the root, the Box pushes via the 📦 toolbar
/// icon. Onboarding sheet on first launch, full-screen session cover.
struct RootView: View {
    @Bindable var model: AppModel

    @State private var boxPresented = false

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
            if model.uitestScreen == "box" {
                boxPresented = true
            }
        }
    }

    private var home: some View {
        NavigationStack {
            HeuteView(model: model, openBox: { boxPresented = true })
                .navigationDestination(isPresented: $boxPresented) {
                    BoxView(model: model)
                }
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            boxPresented = true
                        } label: {
                            Image(systemName: "shippingbox.fill")
                        }
                        .accessibilityLabel("nav.box")
                    }
                }
                .toolbarBackground(.hidden, for: .navigationBar)
        }
        .tint(.dlAccent)
    }

    private var loading: some View {
        VStack(spacing: DL.Space.l) {
            Text(verbatim: "📦")
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
