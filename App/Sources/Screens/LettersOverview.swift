import SwiftUI
import SprossKern

/// The "Buchstaben" hub entry: the alphabet of the language being learned, and
/// the place its drill is started from.
///
/// Two sections, in the order the numbers page uses — start first, reading
/// after: the stages the drill will walk through and the button that opens it,
/// then the alphabet table (one card per row of
/// `catalog/alphabet/<lang>.json`). The table and the drill it prepares you for
/// used to be two unrelated surfaces, one of them buried under a row of its own.
///
/// Nothing here is graded and nothing is stored: the letter drill books no
/// review (D12) and keeps no record, so this page has no ladder to read — where
/// a run STARTS is derived from the words the learner already holds, and which
/// stages it can reach from what this device can say.
///
/// The alphabet rows live in LettersOverview+Alphabet.swift and the stage
/// ladder in LettersOverview+Practice.swift; split purely for file size.
struct LettersOverview: View {
    let model: AppModel
    /// Which alphabet — the language being learned, never the reader's.
    let language: String

    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    // why: internal, not private — both extensions read the reader's locale.
    @Environment(\.locale) var locale

    /// What the drill can ASK on this device. Rebuilt on every foreground, never
    /// decided once: a voice installed in Settings while the app slept must turn
    /// the start button on without a relaunch.
    // why: internal, not private — +Practice.swift renders the ladder from it.
    @State var availability: LetterDrillAvailability?
    @State private var launch: Launch?
    /// What the run that just closed came to — one tile above the stages, the
    /// shape the numbers page uses, instead of a screen with a second ✕ on it.
    @State private var lastRun: DrillRunResult?

    /// The run the start button opens, wrapped so ONE `fullScreenCover(item:)`
    /// carries it — the same shape the numbers overview uses.
    private struct Launch: Identifiable {
        let language: String
        let id = UUID()
    }

    /// Scroll target for the tile a closed run leaves.
    static let resultAnchor = "result"

    var body: some View {
        NavigationStack {
            ScrollViewReader { scroll in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: DL.Space.xl) {
                        if let lastRun {
                            DrillResultTile(result: lastRun)
                                .id(Self.resultAnchor)
                        }
                        practiceSection
                        alphabetSection
                    }
                    .padding(DL.Space.xl)
                }
                // why: the numbers page's rule — a tile inserted above the
                // content keeps the offset, so the page comes up to meet it.
                .onChange(of: lastRun) { _, run in
                    guard run != nil else { return }
                    withAnimation(.easeOut(duration: 0.25)) {
                        scroll.scrollTo(Self.resultAnchor, anchor: .top)
                    }
                }
            }
            #if DEBUG
            .defaultScrollAnchor(Self.uitestAnchor)
            #endif
            .background(Color.dlBackground.ignoresSafeArea())
            .navigationTitle(Text("letters.title \(languageName)"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { dismiss() } label: { Image(systemName: "xmark") }
                        .accessibilityLabel(Text("common.close"))
                }
                // why: the same corners as the numbers page — the way out left,
                // the way in right, reachable from inside the alphabet table.
                ToolbarItem(placement: .topBarTrailing) {
                    Button("overview.start") { start() }
                        .disabled(!drillAvailable)
                }
            }
        }
        .tint(.dlAccent)
        .onAppear { refreshAvailability() }
        // why: the drill's reach can change while the app sleeps — on becoming
        // ACTIVE, not on willEnterForeground, because the speaker drops its
        // cached voice table on that notification and this must read the new one.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { refreshAvailability() }
        }
        .fullScreenCover(item: $launch) { launch in
            LetterDrillView(model: model, language: launch.language,
                            onFinish: { result in
                                withAnimation(.easeOut(duration: 0.25)) { lastRun = result }
                            })
                .environment(\.locale, model.knownLocale)
        }
    }

    // MARK: - Starting a run

    func start() {
        launch = Launch(language: language)
    }

    func refreshAvailability() {
        availability = LetterDrillAvailability(model: model, language: language)
        #if DEBUG
        // why: a cover raised while the sheet under it is still animating in is
        // dropped — the tap this stands in for always comes after that. ONCE:
        // a run that reopens itself on every foreground never ends.
        if launch == nil, !Self.uitestLaunched, UserDefaults.standard.bool(forKey: "uitest-run"),
           drillAvailable {
            Self.uitestLaunched = true
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(600))
                start()
            }
        }
        #endif
    }

    // MARK: - Chrome

    var languageName: String {
        LanguageNames.display(language, locale: locale, catalog: model.catalog)
    }

    func heading(_ key: LocalizedStringKey) -> some View {
        Text(key)
            .font(DL.Fonts.title)
            .foregroundStyle(Color.dlTextPrimary)
            .accessibilityAddTraits(.isHeader)
    }
}

#if DEBUG
extension LettersOverview {
    /// One auto-start per launch — see `refreshAvailability`.
    @MainActor static var uitestLaunched = false

    /// `-uitest-section alphabet` opens the page at the table instead of at the
    /// top — the numbers overview's hook, reused, because on both pages the
    /// reading now sits below the fold and no tap driver is installed.
    static var uitestAnchor: UnitPoint {
        UserDefaults.standard.string(forKey: "uitest-section") == "alphabet" ? .bottom : .top
    }
}
#endif
