import Foundation
import SprossKern

// Read-side derivations: Heute values, box browsing, presentation
// resolution, and Fortschritt aggregates. Every count is in cards
// (contract §4 — one schedule per card).

extension AppModel {

    // MARK: - Heute-derived values

    var todayPlan: SessionPlan? {
        guard let box else { return nil }
        return SessionComposer.shared.composeSession(state: box,
                                                     nowEpochMillis: Date().epochMillis)
    }

    var dueNowCount: Int {
        guard let box else { return 0 }
        return BoxEngine.shared.dueNow(state: box, nowEpochMillis: Date().epochMillis).count
    }

    var sessionAvailable: Bool {
        !(todayPlan?.isEmpty ?? true) || dueNowCount > 0
    }

    /// Cards that will be due by tomorrow evening (preview on the done state).
    var tomorrowDueCount: Int {
        guard let box,
              let end = calendar.date(byAdding: .day, value: 2,
                                      to: calendar.startOfDay(for: Date()))
        else { return 0 }
        return BoxEngine.shared.dueNow(state: box, nowEpochMillis: end.epochMillis).count
    }

    // MARK: - Presentation (contract §3 — render-time role resolution)

    func scheduling(for cardID: String) -> CardScheduling? {
        box?.scheduling[cardID]
    }

    /// How this card's NEXT review is presented (alternating, per log count).
    func presentationRole(for cardID: String) -> PresentationRole {
        SprossKern.presentationRole(cardId: cardID,
                                    reviewCount: scheduling(for: cardID)?.reviewCount ?? 0)
    }

    /// Emoji policy: first exposure, or produce-while-learning (contract §3).
    func emojiVisible(for card: Card) -> Bool {
        let sched = scheduling(for: card.id)
        return SprossKern.emojiVisible(role: presentationRole(for: card.id),
                                       phase: sched?.phase ?? .theNew,
                                       reviewCount: sched?.reviewCount ?? 0)
    }

    /// The rotated target form to prompt on a recognition review.
    func promptForm(for card: Card) -> String {
        SprossKern.recognitionPromptForm(card: card,
                                         reviewCount: scheduling(for: card.id)?.reviewCount ?? 0)
    }

    /// Typed-answer grader for the profile's target (produce only).
    var answerNormalizer: AnswerNormalizer? {
        guard let target = targetLanguage, let info = languageInfo(target) else { return nil }
        return AnswerNormalizer(answerLanguage: info)
    }

    // MARK: - Box actions

    /// "Pack in die Box": enqueue the area's unscheduled cards in seed order.
    func enqueueArea(_ area: String) {
        let ids = cards(inArea: area)
            .filter { scheduling(for: $0.id) == nil }
            .map(\.id)
        guard !ids.isEmpty else { return }
        mutate { $0 = BoxEngine.shared.enqueue(state: $0, cardIds: ids) }
    }

    func setSuspended(cardID: String, suspended: Bool) {
        mutate { $0 = BoxEngine.shared.setSuspended(state: $0, cardId: cardID, suspended: suspended) }
    }

    /// Destructive fresh start: re-bootstrap this target's box from the
    /// current catalog join, keeping the user's config (budget).
    func resetBox() async {
        guard let old = box else { return }
        let fresh = BoxEngine.shared.bootstrap(cards: Array(old.cards.values),
                                               config: old.config,
                                               joinStamp: old.joinStamp)
        box = fresh
        do {
            try await store.saveNow(json: StoreCodec.shared.encode(state: fresh),
                                    target: fresh.joinStamp.target)
            await store.saveWidgetSnapshot(json: widgetSnapshotJSON(for: fresh))
            refreshStats()
            pushWatchSnapshot()
        } catch {
            loadFailure = .resetFailed(reason: error.localizedDescription)
        }
    }

    // MARK: - Box queries

    /// Area keys in catalog default order (groups top-to-bottom).
    var areaNames: [String] {
        #if DEBUG
        // UI-test hook: `-uitest-noareas 1` hides the area sections so the
        // Box tab's settings block is reachable without scrolling.
        if UserDefaults.standard.bool(forKey: "uitest-noareas") { return [] }
        #endif
        guard let catalog, let stats else { return [] }
        let present = Set(stats.areas.map(\.name))
        return catalog.areaNames.filter(present.contains)
    }

    /// Area heading in the profile's source language, catalog-provided.
    func areaTitle(_ area: String) -> String {
        catalog?.areaTitle(area: area, lang: sourceLanguage) ?? area.capitalized
    }

    /// One Box browser section: an areas.json group with its present areas.
    struct AreaGroupSection: Identifiable {
        let id: String
        let title: String
        let areas: [String]
    }

    /// Manifest-ordered groups (title in the SOURCE language, en fallback),
    /// filtered to the areas actually present in this profile's box —
    /// mirrors `areaNames` (incl. its uitest hook); empty groups drop out.
    var areaGroupSections: [AreaGroupSection] {
        guard let catalog else { return [] }
        let present = Set(areaNames)
        return catalog.groups.compactMap { group in
            let areas = group.areas.filter(present.contains)
            guard !areas.isEmpty else { return nil }
            let title = group.titles[sourceLanguage] ?? group.titles["en"] ?? group.id.capitalized
            return AreaGroupSection(id: group.id, title: title, areas: areas)
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
            return (day, box.dailyStats[isoDayKey(for: day)]?.reviewCount ?? 0)
        }
    }

    /// Answer events already folded into today's dailyStats (includes retries).
    var reviewsDoneToday: Int {
        guard let box else { return 0 }
        return box.dailyStats[isoDayKey(for: Date())]?.reviewCount ?? 0
    }
}
