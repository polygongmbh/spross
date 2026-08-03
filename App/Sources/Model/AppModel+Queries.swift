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
                                                     nowEpochMillis: Date().epochMillis,
                                                     tzId: currentTzId())
    }

    var dueNowCount: Int {
        guard let box else { return 0 }
        return BoxEngine.shared.dueNow(state: box, nowEpochMillis: Date().epochMillis).count
    }

    var sessionAvailable: Bool {
        !(todayPlan?.isEmpty ?? true) || dueNowCount > 0
    }

    /// What carries the round — whichever side of it outweighs the other. Due work, a
    /// light warm-up, and an offer of new words read very differently to a learner, so
    /// Heute names which one it is instead of calling all three "a session".
    enum SessionOffer: String {
        /// Recall outweighs the new words, and there is enough of it to lead.
        case reviews
        /// Recall outweighs the new words but amounts to a token one or two.
        case warmUp
        /// First sights outnumber everything there is to recall.
        case freshSet
        case nothing
    }

    /// One composition, everything Heute needs from it —
    /// `todayPlan` recomposes on every access,
    /// so the screen takes this snapshot once per render.
    struct HeuteOffer {
        let kind: SessionOffer
        /// Reviews this round actually takes (capped), not the whole backlog.
        let sessionReviews: Int
        /// Due cards the session cap holds back for a later round.
        let dueHeldBack: Int
        /// Cards pulled forward to fill a short round out (kern's session floor).
        let aheadCount: Int
        let freshCount: Int

        /// Fewer due cards than this and recall is a warm-up, never the round's headline.
        static let reviewsLeadFrom = 3

        /// Which headline names this round: one string set per kind, keyed by the kind
        /// itself so a new kind cannot silently keep an old kind's words.
        ///
        /// The variant turns on the round's SHAPE, never on the clock: a learner does
        /// several rounds in a day and one repeated line reads as a screen that never
        /// moved, while a line re-rolling between renders reads as a glitch — and
        /// `heuteOffer` recomposes on every access.
        var headlineKey: String {
            // The done card speaks for an empty round, so `nothing` has no words of its
            // own; naming it anyway keeps every path off a missing key.
            let named = kind == .nothing ? SessionOffer.freshSet : kind
            return "heute.session.\(named.rawValue).\(variant(outOf: 3))"
        }

        /// FNV-1a over the counts, not `hashValue`: Swift seeds that per process, so the
        /// same round would headline differently after every launch.
        private func variant(outOf count: Int) -> Int {
            var hash: UInt64 = 0xcbf2_9ce4_8422_2325
            for value in [sessionReviews, aheadCount, freshCount] {
                hash = (hash ^ UInt64(truncatingIfNeeded: value)) &* 0x100_0000_01b3
            }
            // why: FNV leaves its low bits barely mixed, and the modulo reads exactly those.
            hash ^= hash >> 33
            return Int(hash % UInt64(count))
        }
    }

    var heuteOffer: HeuteOffer {
        guard let plan = todayPlan else {
            return HeuteOffer(kind: .nothing, sessionReviews: 0,
                              dueHeldBack: 0, aheadCount: 0, freshCount: 0)
        }
        let reviews = plan.reviews.count
        let ahead = plan.ahead.count
        let fresh = Int(plan.freshCount)
        let kind: SessionOffer
        if plan.isEmpty {
            kind = .nothing
        } else if fresh > reviews + ahead {
            kind = .freshSet
        } else if reviews >= HeuteOffer.reviewsLeadFrom {
            kind = .reviews
        } else {
            kind = .warmUp
        }
        return HeuteOffer(kind: kind,
                          sessionReviews: reviews,
                          dueHeldBack: max(0, dueNowCount - reviews),
                          aheadCount: ahead,
                          freshCount: fresh)
    }

    /// What the learner did today — reviews, first meetings, words that consolidated,
    /// and whether today's recall has fallen far enough to suggest stopping.
    var today: TodayReport? {
        guard let box else { return nil }
        return BoxEngine.shared.today(state: box,
                                      nowEpochMillis: Date().epochMillis,
                                      tzId: currentTzId())
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

    /// Whether this card has never been answered — the review that TEACHES the
    /// word rather than testing it (always recognition, contract §3).
    func isFirstExposure(_ cardID: String) -> Bool {
        (scheduling(for: cardID)?.reviewCount ?? 0) == 0
    }

    /// Whether this card has settled — the fast bar behind presentation support.
    func isSettled(_ cardID: String) -> Bool {
        guard let box else { return false }
        return BoxEngine.shared.isSettled(state: box, cardId: cardID)
    }

    /// Whether this card has genuinely consolidated — the stricter bar behind
    /// the stats display and the session-summary "gefestigt" tally.
    func isConsolidated(_ cardID: String) -> Bool {
        guard let box else { return false }
        return BoxEngine.shared.isConsolidated(state: box, cardId: cardID)
    }

    /// The words the learner already holds, in seed order — the pool the
    /// letter drill dictates from. A pure bridge: WHICH cards count as
    /// consolidated is Kern's rule (`consolidatedCardIds`), and the drill's own
    /// filters (single word, audible) sit in LetterDrillAvailability.
    func consolidatedCards() -> [Card] {
        guard let box else { return [] }
        return BoxEngine.shared.consolidatedCardIds(state: box).compactMap { box.cards[$0] }
    }

    /// Which face carries the picture: the prompt only where it cannot give the
    /// answer away, otherwise the reveal (contract §3).
    func emojiCue(for card: Card) -> EmojiCue {
        SprossKern.emojiCue(role: presentationRole(for: card.id),
                                  settled: isSettled(card.id),
                                  reviewCount: scheduling(for: card.id)?.reviewCount ?? 0)
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

    /// The same grading with the whole join in view: a form the catalog owns
    /// elsewhere is that word, never a typo of this card's answer (kern §6).
    /// Built per grading pass — one pass over the join's accepted forms, and
    /// only ever on a submit tap.
    var produceGrader: CatalogAnswerGrader? {
        guard let normalizer = answerNormalizer, let box else { return nil }
        return CatalogAnswerGrader(normalizer: normalizer, cards: Array(box.cards.values))
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

    /// Destructive fresh start: every schedule and tally goes, the join, the
    /// user's config (budget) and their own words stay — which of those a reset
    /// keeps is the engine's ruling, not this layer's (kern §6).
    func resetBox() async {
        guard let old = box else { return }
        let fresh = BoxEngine.shared.reset(state: old)
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
        let fromCatalog = catalog.areaNames.filter(present.contains)
        // why: the manifest cannot list an area the catalog does not own, so the
        // learner's own words follow every catalog area — where their seed order
        // puts them anyway.
        guard present.contains(ownArea) else { return fromCatalog }
        return fromCatalog + [ownArea]
    }

    /// Area heading in the profile's source language, catalog-provided. The
    /// learner's own area is chrome, so it reads from the string catalog instead.
    func areaTitle(_ area: String) -> String {
        if area == ownArea { return DLChrome.string("box.ownWords", locale: knownLocale) }
        return catalog?.areaTitle(area: area, lang: sourceLanguage) ?? area.capitalized
    }

    /// Area icon, language-neutral: the catalog owns its own (`areas.json`), Kern
    /// owns the one area the catalog cannot. A neutral box for anything else.
    func areaEmoji(_ area: String) -> String {
        if area == ownArea { return OwnWords.shared.EMOJI }
        return catalog?.areaEmoji(area: area) ?? "📦"
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

    /// The group the Box browser opens on: the first one holding cards already
    /// in learning — where the learner left off, and the only group whose
    /// numbers have anything to say. A box nothing has been started in falls
    /// back to the first group, so the screen never opens fully folded.
    var defaultExpandedGroupID: String? {
        let sections = areaGroupSections
        let started = sections.first { section in
            section.areas.contains { (areaStats($0)?.activeCards ?? 0) > 0 }
        }
        return started?.id ?? sections.first?.id
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
}
