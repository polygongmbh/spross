import Foundation
import Observation
import DuoKern

/// One step of a running session.
enum SessionStep: Equatable {
    /// Show this card (by id).
    case card(String)
    /// Learning steps land in the near future: short breather with countdown.
    case pause(until: Date)
    case completed
}

/// The observable app model: owns the `BoxState` of the selected pair,
/// persistence, statistics, and the running session.
///
/// Time discipline: THIS layer injects `Date()` / `Calendar.current` into
/// DuoKern — Kern never self-times (design.md §Architecture).
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
    private(set) var loadErrorMessage: String?

    // MARK: Session state (mutated in AppModel+Session)

    var sessionPresented = false
    var sessionStep: SessionStep?
    var sessionQueue: [String] = []
    var sessionTotal = 0
    var sessionAnswered = 0
    var sessionEnded = true
    private(set) var autostartSession = false
    /// DEBUG hook: `-uitest-tab box|fortschritt` opens that tab after launch.
    private(set) var uitestTab: String?

    let store: BoxStore
    let calendar = Calendar.current
    private static let selectedPairKey = "selectedPair"

    init(store: BoxStore = BoxStore()) {
        self.store = store
    }

    // MARK: - Launch

    func start() async {
        var pairOverride: LanguagePair?
        #if DEBUG
        // UI-test hooks: `-uitest-pair de-sw` skips onboarding with that pair,
        // `-uitest-autostart 1` opens the session immediately after launch.
        let defaults = UserDefaults.standard
        if let raw = defaults.string(forKey: "uitest-pair") {
            pairOverride = LanguagePair(rawValue: raw)
        }
        autostartSession = defaults.bool(forKey: "uitest-autostart")
        uitestTab = defaults.string(forKey: "uitest-tab")
        #endif

        let stored = UserDefaults.standard.string(forKey: Self.selectedPairKey)
            .flatMap(LanguagePair.init(rawValue:))
        guard let pair = pairOverride ?? stored else {
            phase = .onboarding
            return
        }
        await activate(pair: pair)
        if autostartSession, sessionAvailable {
            startSession()
        }
    }

    func completeOnboarding(pair: LanguagePair, direction: Direction) async {
        await activate(pair: pair, direction: direction)
    }

    /// Load the pair's box from disk, or bootstrap it from the bundled seed.
    func activate(pair: LanguagePair, direction: Direction? = nil) async {
        do {
            var state: BoxState
            if let loaded = try await store.load(pair: pair) {
                state = loaded
            } else {
                let cards = try Self.loadSeedCards(pair: pair)
                state = BoxEngine.bootstrap(cards: cards, config: BoxConfig(pair: pair))
            }
            if let direction {
                state.config.direction = direction
            }
            box = state
            try await store.saveNow(state)
            UserDefaults.standard.set(pair.rawValue, forKey: Self.selectedPairKey)
            loadErrorMessage = nil
            refreshStats()
            phase = .ready
        } catch {
            loadErrorMessage = "Die Inhalte konnten nicht geladen werden. (\(error.localizedDescription))"
            phase = .ready
        }
    }

    /// The Xcode project bundles the repo's content/ folder as a folder
    /// reference; seed files resolve inside it via Bundle.main.
    static func loadSeedCards(pair: LanguagePair) throws -> [Card] {
        guard let directory = Bundle.main.url(forResource: "content", withExtension: nil) else {
            throw CocoaError(.fileNoSuchFile,
                             userInfo: [NSLocalizedDescriptionKey: "content/ fehlt im App-Bundle"])
        }
        return try SeedContent.loadAll(from: directory).filter { $0.pair == pair }
    }

    // MARK: - Heute-derived values

    var todayPlan: SessionPlan {
        guard let box else { return SessionPlan() }
        return BoxEngine.composeSession(state: box, now: Date(), calendar: calendar)
    }

    var dueNowCount: Int {
        guard let box else { return 0 }
        return BoxEngine.dueNow(state: box, now: Date()).count
    }

    var sessionAvailable: Bool {
        !todayPlan.isEmpty || dueNowCount > 0
    }

    /// Cards that will be due by tomorrow evening (preview on the done state).
    var tomorrowDueCount: Int {
        guard let box,
              let end = calendar.date(byAdding: .day, value: 2,
                                      to: calendar.startOfDay(for: Date()))
        else { return 0 }
        return BoxEngine.dueNow(state: box, now: end).count
    }

    // MARK: - Config (persisted inside BoxState.config)

    func setDirection(_ direction: Direction) {
        mutate { $0.config.direction = direction }
    }

    func setNewPerDay(_ count: Int) {
        mutate { $0.config.newPerDay = max(0, min(20, count)) }
    }

    func switchPair(_ pair: LanguagePair) {
        guard let box, box.config.pair != pair else { return }
        Task { await activate(pair: pair) }
    }

    // MARK: - Box actions

    /// "Pack in die Box": enqueue the area's unscheduled cards in seed order.
    func enqueueArea(_ area: String) {
        let ids = cards(inArea: area)
            .filter { scheduling(for: $0.id) == nil }
            .map(\.id)
        guard !ids.isEmpty else { return }
        mutate { $0 = BoxEngine.enqueue(state: $0, cardIDs: ids) }
    }

    func setSuspended(cardID: String, suspended: Bool) {
        mutate { $0 = BoxEngine.setSuspended(state: $0, cardID: cardID, suspended: suspended) }
    }

    // MARK: - Box queries

    /// Area keys ordered by their German display name.
    var areaNames: [String] {
        (stats?.areas.map(\.name) ?? []).sorted {
            AreaInfo.info(for: $0).name.localizedCompare(AreaInfo.info(for: $1).name) == .orderedAscending
        }
    }

    func areaStats(_ name: String) -> AreaStatistics? {
        stats?.areas.first { $0.name == name }
    }

    func cards(inArea area: String) -> [Card] {
        guard let box else { return [] }
        return box.cards.values
            .filter { $0.area == area }
            .sorted { $0.seedIndex < $1.seedIndex }
    }

    func scheduling(for cardID: String) -> CardScheduling? {
        guard let box else { return nil }
        return box.scheduling[BoxState.schedulingKey(cardID: cardID, direction: box.config.direction)]
    }

    /// Unscheduled cards in the area that are not already queued —
    /// what "Pack in die Box" would actually add.
    func enqueueableCount(area: String) -> Int {
        guard let box else { return 0 }
        let queued = Set(box.enqueued)
        return cards(inArea: area)
            .filter { scheduling(for: $0.id) == nil && !queued.contains($0.id) }
            .count
    }

    // MARK: - Fortschritt

    /// Reviews per day for the trailing 14 days (oldest first), from dailyStats.
    func last14Days(now: Date = Date()) -> [(day: Date, reviews: Int)] {
        guard let box else { return [] }
        let start = calendar.startOfDay(for: now)
        return (0..<14).reversed().compactMap { offset in
            guard let day = calendar.date(byAdding: .day, value: -offset, to: start) else { return nil }
            return (day, box.dailyStats[dayKey(day)]?.reviews ?? 0)
        }
    }

    /// Must format identically to Kern's day key (yyyy-MM-dd, caller calendar).
    private func dayKey(_ date: Date) -> String {
        let parts = calendar.dateComponents([.year, .month, .day],
                                            from: calendar.startOfDay(for: date))
        return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }

    // MARK: - Persistence & stats

    func refreshStats() {
        stats = box.map { BoxEngine.statistics(state: $0, now: Date(), calendar: calendar) }
    }

    /// Scene went to background → flush immediately.
    func persistNow() {
        guard let box else { return }
        Task { [store] in try? await store.saveNow(box) }
    }

    func persist(_ state: BoxState, immediate: Bool = false) {
        Task { [store] in
            if immediate {
                try? await store.saveNow(state)
            } else {
                await store.save(state)
            }
        }
    }

    /// Apply a change to the box, persist immediately, refresh statistics.
    private func mutate(_ change: (inout BoxState) -> Void) {
        guard var state = box else { return }
        change(&state)
        box = state
        persist(state, immediate: true)
        refreshStats()
    }
}
