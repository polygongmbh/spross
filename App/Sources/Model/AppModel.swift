import Foundation
import Observation
import SprossKern

/// One step of a running session.
enum SessionStep: Equatable {
    /// Show this card (by id).
    case card(String)
    case completed
}

/// The observable app model: owns the `BoxState` of the selected profile
/// (source = known language, target = learning language), persistence,
/// statistics, and the running session.
///
/// Time discipline: THIS layer injects `Date()` / the device time zone into
/// SprossKern as epochMillis + tzId — Kern never self-times.
@MainActor
@Observable
final class AppModel {

    enum Phase: Equatable {
        case loading
        case onboarding
        case ready
    }

    private(set) var phase: Phase = .loading
    /// Settable internally only so AppModel+Session can apply answers.
    var box: BoxState?
    private(set) var stats: BoxStatistics?
    /// Settable internally only so AppModel+Queries can report reset failures.
    var loadErrorMessage: String?
    private(set) var catalog: Catalog?

    // MARK: Session state (mutated in AppModel+Session)

    var sessionPresented = false
    var sessionStep: SessionStep?
    var sessionQueue: [String] = []
    var sessionTotal = 0
    var sessionAnswered = 0
    /// Ratings in answer order, feeding the segmented progress bar.
    var sessionRatings: [Rating] = []
    /// Answers already folded into dailyStats (partial folds on backgrounding).
    var sessionFolded = 0
    /// End-of-session summary tallies (design §Session): new cards started,
    /// cards graduated to review ("gefestigt"), and review answers.
    var sessionNew = 0
    var sessionGraduated = 0
    var sessionReviews = 0
    /// Endless practice: on completion the user can keep pulling due + new
    /// cards until they stop, instead of ending the round.
    var sessionEndless = false
    var sessionEnded = true
    /// Join the running session was composed against; a mismatch with the
    /// box's stamp (source switch, catalog update) forces a recompose.
    var sessionJoinStamp: JoinStamp?
    private(set) var autostartSession = false
    /// DEBUG hook: `-uitest-screen box` pushes the Box screen after launch.
    private(set) var uitestScreen: String?

    let store: BoxStore
    let calendar = Calendar.current
    /// Watch sync bridge (PhoneConnectivity.swift): snapshot down, events up.
    let watchBridge = PhoneConnectivity()
    static let sourceLanguageKey = "sourceLanguage"
    static let targetLanguageKey = "targetLanguage"
    /// UI chrome exists only for these languages; other sources fall back to en.
    static let chromeLanguages: Set<String> = ["de", "en"]

    init(store: BoxStore = BoxStore()) {
        self.store = store
    }

    // MARK: - Profile

    var sourceLanguage: String {
        box?.joinStamp.source
            ?? UserDefaults.standard.string(forKey: Self.sourceLanguageKey)
            ?? Self.defaultSource(covered: catalog.map(coveredSources) ?? [])
    }

    var targetLanguage: String? { box?.joinStamp.target }

    /// Sources worth offering: every language with at least one learnable target.
    func coveredSources(_ catalog: Catalog) -> [String] {
        catalog.languages.keys.sorted()
            .filter { !catalog.availableTargets(source: $0).isEmpty }
    }

    /// Device language when covered, else English (contract §1).
    static func defaultSource(covered: [String]) -> String {
        let device = Locale.current.language.languageCode?.identifier ?? "en"
        return covered.contains(device) ? device : "en"
    }

    func languageInfo(_ code: String) -> LanguageInfo? {
        catalog?.languages[code]
    }

    // MARK: - Launch

    func start() async {
        startWatchBridge()
        var sourceOverride: String?
        var targetOverride: String?
        #if DEBUG
        // UI-test hooks: `-uitest-source de -uitest-target sw` skips onboarding
        // with that profile, `-uitest-autostart 1` opens the session after launch.
        let defaults = UserDefaults.standard
        sourceOverride = defaults.string(forKey: "uitest-source")
        targetOverride = defaults.string(forKey: "uitest-target")
        autostartSession = defaults.bool(forKey: "uitest-autostart")
        uitestScreen = defaults.string(forKey: "uitest-screen")
        #endif

        guard let catalog = loadCatalog() else {
            loadErrorMessage = "Die Inhalte konnten nicht geladen werden. (catalog fehlt im App-Bundle)"
            phase = .ready
            return
        }
        self.catalog = catalog

        let storedTarget = UserDefaults.standard.string(forKey: Self.targetLanguageKey)
        guard let target = targetOverride ?? storedTarget else {
            phase = .onboarding
            return
        }
        let storedSource = UserDefaults.standard.string(forKey: Self.sourceLanguageKey)
        let source = sourceOverride ?? storedSource
            ?? Self.defaultSource(covered: coveredSources(catalog))
        await activate(source: source, target: target)
        if autostartSession, sessionAvailable {
            startSession()
        }
    }

    /// The Xcode project bundles the repo's catalog/ folder as a folder
    /// reference; Kern parses it through a path-based reader.
    private func loadCatalog() -> Catalog? {
        guard let directory = Bundle.main.url(forResource: "catalog", withExtension: nil)
        else { return nil }
        return Catalog.companion.load(source: BundleCatalogSource(directory: directory))
    }

    func completeOnboarding(source: String, target: String) async {
        await activate(source: source, target: target)
    }

    /// Load the target's box from disk (re-joined for the profile), or
    /// bootstrap it fresh from the catalog join.
    func activate(source: String, target: String) async {
        guard let catalog, catalog.languages[source] != nil,
              catalog.languages[target] != nil, source != target else {
            loadErrorMessage = "Unbekanntes Sprachprofil (\(source) → \(target))."
            phase = .ready
            return
        }
        do {
            let cards = catalog.join(source: source, target: target)
            let stamp = JoinStamp(source: source, target: target,
                                  catalogFingerprint: catalog.fingerprint)
            let state: BoxState
            if let json = try await store.load(target: target) {
                // why: schedules are keyed by card id (source-agnostic), so a
                // stored box re-joins under ANY source with progress intact.
                state = try StoreCodec.shared.decode(json: json)
                    .join(cards: cards, joinStamp: stamp)
            } else {
                state = BoxEngine.shared.bootstrap(cards: cards, config: .product(),
                                                   joinStamp: stamp)
            }
            box = state
            try await store.saveNow(json: StoreCodec.shared.encode(state: state), target: target)
            await store.saveWidgetSnapshot(json: widgetSnapshotJSON(for: state))
            UserDefaults.standard.set(source, forKey: Self.sourceLanguageKey)
            UserDefaults.standard.set(target, forKey: Self.targetLanguageKey)
            loadErrorMessage = nil
            refreshStats()
            pushWatchSnapshot()
            recomposeSessionIfStale()
            phase = .ready
        } catch {
            loadErrorMessage = "Die Inhalte konnten nicht geladen werden. (\(error.localizedDescription))"
            phase = .ready
        }
    }

    /// Switch the known language in place — every schedule survives (keys are
    /// card ids); non-joining entries turn inert and revive on switch-back.
    func switchSource(_ newSource: String) {
        guard let box, let catalog, box.joinStamp.source != newSource,
              catalog.languages[newSource] != nil, newSource != box.joinStamp.target
        else { return }
        let cards = catalog.join(source: newSource, target: box.joinStamp.target)
        let stamp = JoinStamp(source: newSource, target: box.joinStamp.target,
                              catalogFingerprint: catalog.fingerprint)
        let next = BoxEngine.shared.rejoin(state: box, cards: cards, joinStamp: stamp)
        self.box = next
        UserDefaults.standard.set(newSource, forKey: Self.sourceLanguageKey)
        persist(next, immediate: true)
        refreshStats()
        recomposeSessionIfStale()
    }

    func switchTarget(_ newTarget: String) {
        guard let box, box.joinStamp.target != newTarget else { return }
        let source = box.joinStamp.source
        Task { await activate(source: source, target: newTarget) }
    }

    /// Scene became active: the box file may predate a catalog/profile change.
    func handleForeground() {
        recomposeSessionIfStale()
        refreshStats()
    }

    // MARK: - UI-chrome locale

    /// Locale for UI chrome, derived from the profile's KNOWN language when
    /// chrome exists for it (de/en); other sources read English until their
    /// UIs are authored. Falls back to the device before a box is loaded.
    var knownLocale: Locale {
        guard box != nil else { return Self.onboardingChromeLocale }
        let source = sourceLanguage
        return Locale(identifier: Self.chromeLanguages.contains(source) ? source : "en")
    }

    /// Before any box exists there is no known language yet — follow the
    /// device, mapped to the two shipped UI languages (German or English).
    static var onboardingChromeLocale: Locale {
        Locale.current.language.languageCode?.identifier == "de"
            ? Locale(identifier: "de")
            : Locale(identifier: "en")
    }

    /// Immersion: the language being LEARNED, but only when we have chrome for
    /// it (de/en) — so an action button can show its word in the target
    /// language as a subtitle. nil = no immersion subtitle.
    var targetChromeLocale: Locale? {
        guard let target = targetLanguage, Self.chromeLanguages.contains(target)
        else { return nil }
        return Locale(identifier: target)
    }

    // MARK: - Config (persisted inside BoxState.config)

    func setMaxLearning(_ count: Int) {
        let clamped = max(0, min(30, count))
        mutate { $0 = $0.with(config: $0.config.with(maxLearning: clamped)) }
    }

    // MARK: - Persistence & stats

    func refreshStats() {
        stats = box.map {
            BoxEngine.shared.statistics(state: $0, nowEpochMillis: Date().epochMillis,
                                        tzId: currentTzId())
        }
    }

    /// Scene went to background → fold any mid-session reviews into dailyStats
    /// (an evicted app must not lose them), then flush immediately.
    func persistNow() {
        foldPartialSession()
        guard let box else { return }
        pushWatchSnapshot() // app background → refresh the watch (sync spec)
        let json = StoreCodec.shared.encode(state: box)
        let widgetJSON = widgetSnapshotJSON(for: box)
        let target = box.joinStamp.target
        Task { [store] in
            try? await store.saveNow(json: json, target: target)
            await store.saveWidgetSnapshot(json: widgetJSON)
        }
    }

    func persist(_ state: BoxState, immediate: Bool = false) {
        // why: every save path also refreshes the watch snapshot, so config
        // changes and session end (immediate saves) reach the watch promptly.
        if immediate { pushWatchSnapshot() }
        let json = StoreCodec.shared.encode(state: state)
        let widgetJSON = widgetSnapshotJSON(for: state)
        let target = state.joinStamp.target
        Task { [store] in
            if immediate {
                try? await store.saveNow(json: json, target: target)
            } else {
                await store.save(json: json, target: target)
            }
            // why: the decode-only widget renders from this precomputed file
            // (contract §7) — refresh it on every persist, never debounced.
            await store.saveWidgetSnapshot(json: widgetJSON)
        }
    }

    /// Non-private so AppModel+Queries' reset flow can rewrite the snapshot.
    func widgetSnapshotJSON(for state: BoxState) -> String {
        WidgetSnapshotBuilder.shared.build(
            state: state, nowEpochMillis: Date().epochMillis,
            exposureLimit: WidgetSnapshotBuilder.shared.DEFAULT_EXPOSURE_LIMIT)
    }

    /// Apply a change to the box, persist immediately, refresh statistics.
    func mutate(_ change: (inout BoxState) -> Void) {
        guard var state = box else { return }
        change(&state)
        box = state
        persist(state, immediate: true)
        refreshStats()
    }
}

/// Path-based `CatalogSource` over the bundled catalog folder reference.
private final class BundleCatalogSource: NSObject, CatalogSource {
    private let directory: URL

    init(directory: URL) {
        self.directory = directory
    }

    func read(path: String) -> String? {
        try? String(contentsOf: directory.appendingPathComponent(path), encoding: .utf8)
    }
}
