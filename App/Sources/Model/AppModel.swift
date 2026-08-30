import Foundation
import Observation
import SprossKern
import WidgetKit

/// A failure worth showing as error chrome on Heute. The model names the
/// case only — the view localizes it (`HeuteView`), so the message follows
/// the known-language chrome locale like every other string.
enum LoadFailure: Equatable {
    /// The bundled catalog folder is missing from the app bundle.
    case catalogMissing
    /// The stored/requested (source, target) pair is not in the catalog.
    case unknownProfile(source: String, target: String)
    /// Box load or bootstrap threw; carries the system error description.
    case contentUnavailable(reason: String)
    /// Destructive reset threw; carries the system error description.
    case resetFailed(reason: String)
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
    /// The whole session run — queue, tallies, and THE BOX (`SessionRun`).
    /// Settable across the model's extensions; every write goes through a
    /// reduction or `box` below.
    var run: SessionRunState?
    private(set) var stats: BoxStatistics?
    /// Where every card stands on the growth ladder — what the forest is drawn
    /// from. Cached beside `stats` rather than derived on read: it is one entry
    /// per card in the join, and Heute would otherwise rebuild it every redraw.
    private(set) var growth: [CardGrowth] = []
    /// Everything the Heute screen asks kern for, taken in one pass — see
    /// `HeuteStanding`. Cached for the same reason `growth` is, and more so:
    /// three of its answers each compose a whole round.
    private(set) var heute: HeuteStanding = .none
    /// One tree per area, as the forest draws them. Derived from `growth`, so
    /// it is rebuilt with it rather than per redraw.
    private(set) var trees: [AreaTree] = []
    /// The fortnight the activity strip shows, refreshed with the rest.
    private(set) var activity: [ActivityDay] = []
    /// The Box browser's shelves, in manifest order. Derived from `stats`, and
    /// the screen reads it three times per redraw.
    private(set) var areaGroupSections: [AreaGroupSection] = []
    /// What each shelf's two pack controls would do, every area at once
    /// (`BoxBrowser.shelfCounts`) — asked per shelf, each answer walked the
    /// whole box, and the browser draws both numbers on every one of them.
    private(set) var shelves: [String: ShelfCounts] = [:]
    /// Whether ANY word in the box can be said aloud on this device — the gate
    /// on the box's tap-to-hear hint. Where the target language has no device
    /// voice this is a walk of every card asking the catalog for a recording,
    /// so it is answered with the rest and not per redraw.
    private(set) var anyWordAudible = false
    /// Each area's tree as it stood when the current run started — the "before"
    /// the summary animates from. Held on the model rather than in the session
    /// state because it is a picture, not a rule kern has any business in.
    var treesBeforeSession: [String: AreaTree] = [:]
    /// The typed-answer grader over the whole join, held until the box moves.
    /// Building it walks every card's accepted forms through the normalizer, so
    /// a session that rebuilt it per card paid for the entire join on every
    /// answer; `refreshStats()` drops it wherever the join or the language can
    /// have changed, and the next turn rebuilds it against the box standing then.
    // why: ignored by Observation — it is filled in on a READ, and a stored
    // property written during a view's body would invalidate that body.
    @ObservationIgnored var cachedProduceGrader: CatalogAnswerGrader?
    /// Settable internally only so AppModel+Queries can report reset failures.
    var loadFailure: LoadFailure?
    private(set) var catalog: Catalog?

    /// The stored document, as a window onto the run: a reduction carries the
    /// box it answered against, so the two can never disagree about which state
    /// the next answer applies to.
    var box: BoxState? {
        get { run?.box }
        set {
            guard let newValue else { run = nil; return }
            run = run.map { SessionRun.shared.withBox(state: $0, box: newValue) }
                ?? SessionRun.shared.idle(box: newValue)
        }
    }

    // MARK: Session presentation (iOS-only; the run itself lives in `run`)

    var sessionPresented = false
    /// Whether a round still owes the learner the three lines that teach it
    /// (`SessionCoach`). Armed when onboarding opens that round, cleared when it closes,
    /// and in memory only — an app killed in between is simply back without the coaching.
    var coachPending = false
    private(set) var autostartSession = false
    /// DEBUG hook: `-uitest-screen box` pushes the Box screen after launch,
    /// `finish` jumps a fresh session to its finish screen.
    private(set) var uitestScreen: String?
    #if DEBUG
    /// DEBUG hook: `-uitest-screen finish` parks a fresh run on its summary
    /// without answering anything — a step the reducer has no intent for,
    /// because nothing but a test ever asks for it.
    var uitestFinished = false
    #endif

    let store: BoxStore
    /// `dailyStats` from every OTHER target-language box on disk — the box's
    /// streak is one commitment across every language the learner studies, not
    /// one per language (`Statistics.mergeDailyStats`). Reloaded whenever
    /// `activate` switches languages; a per-answer disk read for every OTHER
    /// language's box would be wasteful since those files only change while
    /// THEY are the active target.
    private(set) var otherLanguagesDailyStats: [[String: DayStats]] = []
    /// Watch sync bridge (PhoneConnectivity.swift): snapshot down, events up.
    let watchBridge = PhoneConnectivity()
    static let sourceLanguageKey = "sourceLanguage"
    static let targetLanguageKey = "targetLanguage"

    init(store: BoxStore = BoxStore()) {
        self.store = store
    }

    // MARK: - Profile

    var sourceLanguage: String {
        box?.joinStamp.source
            ?? UserDefaults.standard.string(forKey: Self.sourceLanguageKey)
            ?? defaultSource
    }

    var targetLanguage: String? { box?.joinStamp.target }

    /// What to call the learner, or nil where no name was given — what the greeting knows
    /// about who it greets, kept per person and not per pair (`LearnerProfile`).
    private(set) var learnerName: String? = LearnerProfile.name

    /// Blank clears it: the store trims, and an empty name is simply no name.
    func setLearnerName(_ raw: String?) {
        LearnerProfile.name = raw
        learnerName = LearnerProfile.name
    }

    /// What this device reports it reads — the one fact Kern cannot have.
    static var deviceLanguage: String {
        Locale.current.language.languageCode?.identifier ?? Catalog.companion.FALLBACK_SOURCE
    }

    /// The source a fresh install opens with (contract §1) — Kern's ruling over
    /// the catalog, so a device language nothing can be taught from still lands
    /// on a source that teaches.
    var defaultSource: String {
        catalog?.defaultSource(deviceLanguage: Self.deviceLanguage)
            ?? Catalog.companion.FALLBACK_SOURCE
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
            loadFailure = .catalogMissing
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
        let source = sourceOverride ?? storedSource ?? defaultSource
        await activate(source: source, target: target)
        if autostartSession, sessionAvailable {
            startSession()
        }
        #if DEBUG
        // UI-test hook: `-uitest-screen finish` opens a session and jumps
        // straight to its finish screen (confetti/cheer/exit buttons).
        if uitestScreen == "finish", sessionAvailable {
            startSession()
            uitestFinished = true
        }
        #endif
    }

    /// The Xcode project bundles the repo's catalog/ folder as a folder
    /// reference; Kern parses it through a path-based reader.
    private func loadCatalog() -> Catalog? {
        guard let directory = Bundle.main.url(forResource: "catalog", withExtension: nil)
        else { return nil }
        return Catalog.companion.load(source: BundleCatalogSource(directory: directory))
    }

    /// The end of the ONLY first-run path — the pick, then the round it was made for.
    /// Not `activate`, which every later language change goes through too: a switch in
    /// the box's settings must not raise a session over the screen you were reading.
    func completeOnboarding(source: String, target: String) async {
        await activate(source: source, target: target)
        // why: the picker is the last question the app asks. Landing on Heute to press
        // one more button makes the first round something you have to go and find — and
        // the coaching arms with that round, never ahead of a round nothing can open.
        if sessionAvailable {
            coachPending = true
            startSession()
        }
    }

    /// Re-open the onboarding sheet on demand (Box settings' "restart tutorial"
    /// row) — the pair and the box itself are untouched, only the story pages
    /// show again. `OnboardingView` reads the still-active pair to skip the
    /// language pick this time (`RootView`).
    func restartOnboarding() {
        phase = .onboarding
    }

    /// Load the target's box from disk (re-joined for the profile), or
    /// bootstrap it fresh from the catalog join.
    func activate(source: String, target: String) async {
        // why: a debounced save may still be holding the box being left behind,
        // and loading another target replaces it — `swapLanguages` promises the
        // outgoing box is on disk, so write it before believing that.
        try? await store.flush()
        guard let catalog, catalog.languages[source] != nil,
              catalog.languages[target] != nil, source != target else {
            loadFailure = .unknownProfile(source: source, target: target)
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
                    .withProductCalibration()
            } else {
                state = BoxEngine.shared.bootstrap(cards: cards,
                                                   config: BoxConfig.companion.product(),
                                                   joinStamp: stamp)
            }
            box = state
            try await store.saveNow(state: state, target: target)
            await reloadOtherLanguagesDailyStats(excluding: target)
            await store.saveWidgetSnapshot(state: state, nowEpochMillis: Date().epochMillis,
                                           otherLanguagesDailyStats: otherLanguagesDailyStats)
            // why: a widget left without a readable snapshot by an update shows the
            // sprout until its timeline is rebuilt — launching is what the sprout
            // asks for, so the handover happens then, not up to six hours later.
            WidgetCenter.shared.reloadTimelines(ofKind: "SprossWordWidget")
            UserDefaults.standard.set(source, forKey: Self.sourceLanguageKey)
            UserDefaults.standard.set(target, forKey: Self.targetLanguageKey)
            loadFailure = nil
            refreshStats()
            pushWatchSnapshot()
            recomposeSessionIfStale()
            phase = .ready
        } catch {
            loadFailure = .contentUnavailable(reason: error.localizedDescription)
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

    /// Picking the OTHER side's language swaps the pair. Both boxes survive:
    /// the current target's box is already persisted on disk, and `activate`
    /// loads (or bootstraps) the new target's box re-joined under the new
    /// source — schedules are per-target documents keyed by card id.
    func swapLanguages() {
        guard let stamp = box?.joinStamp else { return }
        // why: stamp.source != stamp.target always holds, so the swapped pair
        // keeps the invariant and `activate` accepts it.
        Task { await activate(source: stamp.target, target: stamp.source) }
    }

    /// Scene became active: the box file may predate a catalog/profile change.
    func handleForeground() {
        recomposeSessionIfStale()
        refreshStats()
    }

    // MARK: - UI-chrome locale

    /// Locale for UI chrome, derived from the profile's KNOWN language when
    /// chrome exists for it; other sources read English until their UIs are
    /// authored. Which languages those are, and the fallback, is kern's
    /// (`LanguageChoices`).
    var knownLocale: Locale { Self.chromeLocale(source: sourceLanguage) }

    /// The chrome language for a known language. Onboarding uses it too —
    /// with no box yet, `sourceLanguage` is the device language (when the
    /// catalog covers it), so the very first screen greets in it and then
    /// follows whatever the user picks.
    static func chromeLocale(source: String) -> Locale {
        Locale(identifier: LanguageChoices.shared.chromeLanguage(source: source))
    }

    /// Immersion: the language being LEARNED, but only when we have chrome for
    /// it — so an action button can show its word in the target language as a
    /// subtitle. nil = no immersion subtitle, which is why this asks
    /// `hasChrome` rather than `chromeLanguage`: the fallback would caption a
    /// button in the wrong language.
    var targetChromeLocale: Locale? {
        guard let target = targetLanguage,
              LanguageChoices.shared.hasChrome(language: target)
        else { return nil }
        return Locale(identifier: target)
    }

    // MARK: - Persistence & stats

    /// Recompute everything derived from the box: the statistics, the growth
    /// ladder, the Heute standing, the forest and the activity strip.
    ///
    /// The ONE place any of them go stale, and so the one place they are taken
    /// again — every path that can move the box ends here (a mutation, a
    /// language switch, a booked day, a foreground). Screens read the results
    /// as values; nothing on a hot path derives them for itself.
    func refreshStats() {
        // why: the grader snapshots the join, and this runs wherever the join,
        // the queue or the profile's languages can have moved — so this is the
        // one place it goes stale.
        cachedProduceGrader = nil
        let now = Date().epochMillis
        let tz = currentTzId()
        stats = box.map {
            BoxEngine.shared.statistics(state: $0, nowEpochMillis: now, tzId: tz,
                                         otherLanguagesDailyStats: otherLanguagesDailyStats)
        }
        growth = box.map {
            BoxEngine.shared.growth(state: $0, nowEpochMillis: now, tzId: tz)
        } ?? []
        heute = box.map { HeuteStanding.of(box: $0, nowEpochMillis: now, tzId: tz) } ?? .none
        trees = composedAreaTrees()
        activity = composedActivityWindow(now: now, tzId: tz)
        areaGroupSections = composedAreaGroupSections()
        shelves = box.map { BoxBrowser.shared.shelfCounts(state: $0) } ?? [:]
        anyWordAudible = composedAnyWordAudible()
    }

    /// Take the standing again where the day has turned under a screen that
    /// stayed open — nothing else moved, so nothing else would ask.
    func refreshIfDayTurned() {
        guard box != nil,
              heute.day != dayKey(nowEpochMillis: Date().epochMillis, tzId: currentTzId())
        else { return }
        refreshStats()
    }

    /// Reload `otherLanguagesDailyStats` for every catalog language except
    /// `target`. A sibling box that is missing or fails to decode is simply
    /// skipped — its own load path surfaces the real error when the learner
    /// switches to it; the streak merge only wants what is readable.
    private func reloadOtherLanguagesDailyStats(excluding target: String) async {
        guard let catalog else { otherLanguagesDailyStats = []; return }
        var gathered: [[String: DayStats]] = []
        for language in catalog.languages.keys where language != target {
            guard let json = try? await store.load(target: language),
                  let decoded = try? StoreCodec.shared.decode(json: json)
            else { continue }
            gathered.append(decoded.dailyStats)
        }
        otherLanguagesDailyStats = gathered
    }

    /// Scene went to background → fold any mid-session reviews into dailyStats
    /// (an evicted app must not lose them), then flush immediately.
    func persistNow() {
        // why: the fold's own Persist effect already flushed; a day with nothing
        // to fold still owes the disk whatever else moved before the app left.
        let flushed = reduce(SessionIntent.FoldPartial.shared)
            .contains { ($0 as? SessionEffect.Persist)?.immediate == true }
        guard !flushed, let box else { return }
        persist(box, immediate: true) // pushes the watch snapshot too (sync spec)
    }

    /// Hand the box to the store. Encoding happens there, off this actor — an
    /// answer never waits on it, and a burst of them is written once.
    func persist(_ state: BoxState, immediate: Bool = false) {
        // why: every save path also refreshes the watch snapshot, so config
        // changes and session end (immediate saves) reach the watch promptly.
        if immediate { pushWatchSnapshot() }
        let target = state.joinStamp.target
        let now = Date().epochMillis
        let others = otherLanguagesDailyStats
        Task { [store] in
            if immediate {
                try? await store.saveNow(state: state, target: target)
                // why: the decode-only widget renders from this precomputed file
                // (`kern/docs/snapshots.md`). Built with the immediate saves only —
                // session end, a config change, the app leaving the screen. Its
                // worth is long-term exposure, so a round's worth of staleness
                // costs nothing, and rebuilding it per answer costs a full walk
                // of the exposure ranking and every day the box has tallied.
                await store.saveWidgetSnapshot(state: state, nowEpochMillis: now,
                                               otherLanguagesDailyStats: others)
            } else {
                await store.save(state: state, target: target)
            }
        }
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
