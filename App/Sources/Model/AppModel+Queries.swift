import Foundation
import SprossKern

// Read-side derivations: Heute values, box browsing, presentation
// resolution, and Fortschritt aggregates. Every count is in cards
// (contract §4 — one schedule per card).

extension AppModel {

    // MARK: - Heute-derived values

    /// Whether there is a round to sit down to — kern counts due work the composed
    /// round could not carry, so a capped backlog never reads as "nothing".
    var sessionAvailable: Bool {
        guard let box else { return false }
        return SessionOffers.shared.sessionAvailable(state: box,
                                                     nowEpochMillis: Date().epochMillis,
                                                     tzId: currentTzId())
    }

    /// Today's round as kern classified it. A box that has not loaded offers nothing.
    var heuteOffer: SessionOffer {
        guard let box else {
            return SessionOffer(kind: .nothing, reviews: 0, dueHeldBack: 0, ahead: 0, fresh: 0)
        }
        return SessionOffers.shared.offer(state: box,
                                          nowEpochMillis: Date().epochMillis,
                                          tzId: currentTzId())
    }

    /// What the learner did today — reviews, first meetings, words that consolidated,
    /// and whether today's recall has fallen far enough to suggest stopping.
    var today: TodayReport? {
        guard let box else { return nil }
        return BoxEngine.shared.today(state: box,
                                      nowEpochMillis: Date().epochMillis,
                                      tzId: currentTzId())
    }

    /// Cards that will be due by tomorrow evening (preview on the done state) —
    /// the horizon is kern's, not a second local-midnight derivation.
    var tomorrowDueCount: Int {
        guard let box else { return 0 }
        let horizon = endOfTomorrow(nowEpochMillis: Date().epochMillis, tzId: currentTzId())
        return BoxEngine.shared.dueNow(state: box,
                                       nowEpochMillis: horizon.toEpochMilliseconds()).count
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

    /// Area keys in catalog default order (groups top-to-bottom) — the order the
    /// forest lays its groves out in, so adjacency still says which group an
    /// area belongs to now that nothing folds.
    var areaNames: [String] {
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

    /// The trailing days with their review counts AND their place in the current
    /// streak — one walk in kern, so the strip and the flame cannot disagree.
    func activityWindow(days: Int = 14, now: Date = Date()) -> [ActivityDay] {
        guard let box else { return [] }
        return streakWindow(dailyStats: box.dailyStats, days: Int32(days),
                            nowEpochMillis: now.epochMillis, tzId: currentTzId())
    }
}

extension SessionOffer {

    /// The String Catalog key naming this round. Which kind owns the words and
    /// which of its phrasings this round takes are kern's rulings
    /// (`session/SessionOffer.kt`); only the words themselves are ours.
    var headlineKey: String {
        "heute.session.\(Self.stem(headline.kind)).\(headline.variant)"
    }

    /// One string set per kind, keyed by the kind itself so a new kind cannot
    /// silently keep an old kind's words. `nothing` never reaches here — kern
    /// folds it onto `freshSet`, the done card speaking for an empty round —
    /// but naming it anyway keeps every path off a missing key.
    private static func stem(_ kind: SessionOfferKind) -> String {
        switch kind {
        case .reviews: return "reviews"
        case .warmUp: return "warmUp"
        case .freshSet, .nothing: return "freshSet"
        }
    }
}
