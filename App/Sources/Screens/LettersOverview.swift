import SwiftUI
import SprossKern

/// The Letters hub entry: the alphabet of the language being learned, and
/// the place its drill is started from.
///
/// Two sections, start first: the stages the drill will walk through and the
/// button that opens it, then the alphabet table (one card per row of
/// `catalog/alphabet/<lang>.json`). What the stage rows say, and why the page
/// still stands where this device can sound nothing: `docs/drills-words.md`.
///
/// The alphabet rows live in LettersOverview+Alphabet.swift and the stage
/// ladder in LettersOverview+Practice.swift; split purely for file size.
struct LettersOverview: View {
    let model: AppModel
    /// Which alphabet — the language being learned, never the reader's.
    let language: String

    @Environment(\.scenePhase) private var scenePhase

    /// What the drill can ASK on this device. Rebuilt on every foreground, never
    /// decided once: a voice installed in Settings while the app slept must turn
    /// the start button on without a relaunch.
    // why: internal, not private — +Practice.swift renders the ladder from it.
    @State var availability: LetterDrillAvailability?
    @State private var launch: DrillLaunch<String>?
    /// What the run that just closed came to — one tile above the stages, instead
    /// of a screen with a second ✕ on it.
    @State private var lastRun: DrillRunResult?

    var body: some View {
        DrillOverviewPage(title: Text("letters.title \(languageName)"),
                          lastRun: lastRun,
                          startable: drillAvailable,
                          start: start,
                          scrollAnchor: DrillUITest.anchor(["alphabet": .bottom])) {
            practiceSection
            alphabetSection
        }
        .onAppear { refreshAvailability() }
        // why: the drill's reach can change while the app sleeps — on becoming
        // ACTIVE, not on willEnterForeground, because the speaker drops its
        // cached voice table on that notification and this must read the new one.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { refreshAvailability() }
        }
        .fullScreenCover(item: $launch) { launch in
            LetterDrillView(model: model, language: launch.value,
                            onFinish: { result in
                                withAnimation(.easeOut(duration: 0.25)) { lastRun = result }
                            })
                .environment(\.locale, model.knownLocale)
        }
    }

    // MARK: - Starting a run

    func start() {
        launch = DrillLaunch(value: language)
    }

    func refreshAvailability() {
        availability = LetterDrillAvailability(model: model, language: language)
        DrillUITest.autoStart("letters", ready: launch == nil && drillAvailable, start: start)
    }

    // MARK: - Chrome

    var languageName: String {
        LanguageNames.display(language, catalog: model.catalog)
    }
}
