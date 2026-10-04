import SwiftUI
import SprossKern

/// The Letters hub entry: the alphabet of the language being learned, and
/// the place its drill is started from.
///
/// Two sections, start first: the formats the drill will walk through and the
/// button that opens it, then the alphabet table (one card per row of
/// `catalog/alphabet/<lang>.json`). What the format rows say, and why the page
/// still stands where this device can sound nothing: `docs/drills-words.md`.
///
/// The alphabet rows live in LettersOverview+Alphabet.swift and the format
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
    /// The Sprossen earlier runs climbed off clean — re-read as a run's cover
    /// comes down, because that close just filed into it.
    // why: internal, not private — +Practice.swift marks the formats from it.
    @State var cleared: Set<KotlinInt> = []
    /// The formats this page last showed padlocked and now shows open — marked
    /// once (`DrillUnlockMark`), then filed as seen.
    // why: internal, not private — +Practice.swift marks the rows from it.
    @State var unlocking: Set<String> = []
    @Environment(\.locale) private var locale
    @State private var launch: DrillLaunch<String>?
    /// What the run that just closed came to — one tile above the formats, instead
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
        .fullScreenCover(item: $launch, onDismiss: refreshAvailability) { launch in
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
        cleared = TrainerProgress.held(for: LetterDrillView.storageKey(language))
        markUnlocks()
        DrillUITest.autoStart("letters", ready: launch == nil && drillAvailable, start: start)
    }

    /// Only a priced padlock counts: where the drill cannot run at all, every
    /// format is shut for a reason that is not the learner's to earn.
    private func markUnlocks() {
        let priced = drillAvailable ? Self.formats : []
        let marked = TrainerProgress.unlockMarks(
            page: LetterDrillView.storageKey(language),
            locked: Set(priced.filter { !reachable($0) }.map { DrillUnlockMark.shared.row(format: $0) }),
            open: Set(priced.filter { reachable($0) }.map { DrillUnlockMark.shared.row(format: $0) }))
        guard !marked.isEmpty else { return }
        unlocking.formUnion(marked)
        UnlockMark.announce(priced.filter { marked.contains(DrillUnlockMark.shared.row(format: $0)) }
                                .map { ChromeStrings.string(Self.titleName($0), locale: locale) },
                            locale: locale)
    }

    // MARK: - Chrome

    var languageName: String {
        LanguageNames.display(language, catalog: model.catalog)
    }
}
