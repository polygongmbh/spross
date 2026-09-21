import SwiftUI

/// The three sections a bar switches between, each named for its screen: what
/// the learner reads under the box's glyph is `box.name` and can be reworded
/// without touching this.
enum Tab: Hashable {
    case home, box, settings
}

/// Three peer sections behind a tab bar; a run, a drill or the story covers it
/// whole. Onboarding sheet on first launch, full-screen session cover.
struct RootView: View {
    @Bindable var model: AppModel

    @State private var tab: Tab = .home
    /// The area the box should open on, set by tapping a tree on Home —
    /// that names a place, the box is still the screen that shows it.
    @State private var boxArea: String?
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
            // picked, so it re-renders as the user changes the pick. A box
            // already on disk means this is a restart, not the first run —
            // the pair is already made, so the story pages open directly.
            OnboardingView(model: model, skipLanguagePick: model.targetLanguage != nil)
                .interactiveDismissDisabled()
        }
        .fullScreenCover(isPresented: $model.sessionPresented) {
            SessionView(model: model)
                .environment(\.locale, model.knownLocale)
        }
        .task {
            await model.start()
            switch model.uitestScreen {
            case "box": tab = .box
            case "settings": tab = .settings
            default: break
            }
        }
    }

    private var home: some View {
        // Each item is a glyph AND its section's name: the bar is the one place a name
        // is worth its room. A grown tree rather than a leaf for the box — `leaf.fill`
        // is the learning tier's mark on the screen it opens, and the sprout is the
        // streak's. The bar fills the selected glyph itself.
        TabView(selection: $tab) {
            NavigationStack {
                HomeView(model: model, openBox: { area in
                    boxArea = area
                    tab = .box
                })
                .toolbarBackground(.hidden, for: .navigationBar)
            }
            .tabItem { Label("home.name", systemImage: "house") }
            .tag(Tab.home)

            NavigationStack {
                BoxView(model: model, revealArea: boxArea)
            }
            .tabItem { Label("box.name", systemImage: "tree") }
            .tag(Tab.box)

            NavigationStack {
                SettingsView(model: model)
            }
            .tabItem { Label("settings.title", systemImage: "gearshape") }
            .tag(Tab.settings)
        }
        .tint(Theme.colors.accent)
        // why: the area a tree named is spent on the way in. Left standing, the next
        // visit that the bar itself opens would land on that one shelf again, and the
        // box is meant to open where the learner left off.
        .onChange(of: tab) { _, now in
            if now != .box { boxArea = nil }
        }
    }

    private var loading: some View {
        VStack(spacing: Theme.spacing.lg) {
            Text(verbatim: "🌱")
                .font(.system(size: 56)) // card-parity: the splash's own glyph, not a card prompt
                .sway(angle: 4, period: 2.2)
                .scaleEffect(sprouting ? 1.08 : 0.92)
                .animation(
                    reduceMotion ? nil
                        : .easeInOut(duration: 1.4).repeatForever(autoreverses: true),
                    value: sprouting
                )
                .onAppear { sprouting = true }
            ProgressView()
                .tint(Theme.colors.success)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.colors.background.ignoresSafeArea())
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
