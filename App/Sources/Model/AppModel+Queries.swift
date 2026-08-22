import Foundation
import SprossKern
import WidgetKit

// Read-side derivations: Heute values, box browsing, presentation
// resolution, and Fortschritt aggregates. Every count is in cards
// (kern/README.md §3 — one schedule per card).

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
            return SessionOffer(kind: .nothing, reviews: 0, dueHeldBack: 0, ahead: 0, fresh: 0, shortRound: 0)
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

    /// Whether this card has landed — the one bar behind the stats display, the
    /// session-summary "gefestigt" tally, and the support a word gets on its way in.
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
                                  consolidated: isConsolidated(card.id))
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

    /// The SOURCE language's grading, for the one turn typed in it: a card asked
    /// by ear owes what the word MEANS, and the articles and typo budget it is
    /// measured under are that language's own (`kern/docs/presentation.md`).
    var meaningNormalizer: AnswerNormalizer? {
        guard let info = languageInfo(sourceLanguage) else { return nil }
        return AnswerNormalizer(answerLanguage: info)
    }

    /// The same grading with the whole join in view: a form the catalog owns
    /// elsewhere is that word, never a typo of this card's answer (`kern/docs/grading.md`).
    /// Built per grading pass — one pass over the join's accepted forms, and
    /// only ever on a submit tap.
    var produceGrader: CatalogAnswerGrader? {
        guard let normalizer = answerNormalizer, let box else { return nil }
        return CatalogAnswerGrader(normalizer: normalizer, cards: Array(box.cards.values))
    }

    // MARK: - Box actions

    /// "Pack in die Box": enqueue exactly the cards the shelf's own count
    /// promised. One predicate answers both (`BoxBrowser.enqueueableCardIds`),
    /// so a control can never name a number the pack does not add.
    func enqueueArea(_ area: String) {
        guard let box else { return }
        let ids = BoxBrowser.shared.enqueueableCardIds(state: box, area: area)
        guard !ids.isEmpty else { return }
        mutate { $0 = BoxEngine.shared.enqueue(state: $0, cardIds: ids) }
    }

    func setSuspended(cardID: String, suspended: Bool) {
        mutate { $0 = BoxEngine.shared.setSuspended(state: $0, cardId: cardID, suspended: suspended) }
    }

    /// Destructive fresh start: every schedule and tally goes, the join, the
    /// user's config (budget) and their own words stay — which of those a reset
    /// keeps is the engine's ruling, not this layer's (`kern/docs/grading.md`).
    func resetBox() async {
        guard let old = box else { return }
        let fresh = BoxEngine.shared.reset(state: old)
        box = fresh
        do {
            try await store.saveNow(json: StoreCodec.shared.encode(state: fresh),
                                    target: fresh.joinStamp.target)
            await store.saveWidgetSnapshot(json: widgetSnapshotJSON(for: fresh))
            // why: the wiped box is written but the tile keeps drawing the old words
            // until its timeline is rebuilt.
            WidgetCenter.shared.reloadTimelines(ofKind: "SprossWordWidget")
            refreshStats()
            pushWatchSnapshot()
        } catch {
            loadFailure = .resetFailed(reason: error.localizedDescription)
        }
    }

    // MARK: - Box queries

    /// Area keys in catalog default order, the learner's own words last —
    /// which areas the browser lists is `BoxBrowser.areaNames`.
    var areaNames: [String] {
        #if DEBUG
        // UI-test hook: `-uitest-noareas 1` hides the area sections so the
        // Box tab's settings block is reachable without scrolling.
        if UserDefaults.standard.bool(forKey: "uitest-noareas") { return [] }
        #endif
        guard let catalog, let stats else { return [] }
        return BoxBrowser.shared.areaNames(catalog: catalog, stats: stats)
    }

    /// Area heading in the profile's source language, catalog-provided. The
    /// learner's own area is chrome, so it reads from the string catalog instead.
    func areaTitle(_ area: String) -> String {
        if area == ownArea { return DLChrome.string("box.ownWords", locale: knownLocale) }
        return catalog?.areaTitle(area: area, lang: sourceLanguage) ?? area.capitalized
    }

    /// The flavor clause under the heading, in the same language — optional content,
    /// so nil is the ordinary answer for an area that authors none. The learner's own
    /// area has no author to write one.
    func areaSubtitle(_ area: String) -> String? {
        guard area != ownArea else { return nil }
        return catalog?.areaSubtitle(area: area, lang: sourceLanguage)
    }

    /// Area icon, language-neutral: the catalog owns its own (`areas.json`), Kern
    /// owns the one area the catalog cannot. A neutral box for anything else.
    func areaEmoji(_ area: String) -> String {
        if area == ownArea { return OwnWords.shared.EMOJI }
        return catalog?.areaEmoji(area: area) ?? "📦"
    }

    /// The manifest's groups with the areas this box holds — `BoxBrowser.sections`.
    ///
    /// why: the empty `areaNames` carries the `-uitest-noareas` hook through to
    /// the groups, which is a test affordance kern has no business knowing about;
    /// with areas present the guard changes nothing (an empty box drops every
    /// group anyway).
    var areaGroupSections: [AreaGroupSection] {
        guard let catalog, let stats, !areaNames.isEmpty else { return [] }
        return BoxBrowser.shared.sections(catalog: catalog, stats: stats, source: sourceLanguage)
    }

    /// The group the Box browser opens on — `BoxBrowser.defaultExpandedGroupId`.
    var defaultExpandedGroupID: String? {
        guard let stats else { return nil }
        return BoxBrowser.shared.defaultExpandedGroupId(sections: areaGroupSections, stats: stats)
    }

    func areaStats(_ name: String) -> AreaStatistics? {
        stats?.areas.first { $0.name == name }
    }

    func cards(inArea area: String) -> [Card] {
        guard let box else { return [] }
        return BoxBrowser.shared.cardsInArea(state: box, area: area)
    }

    /// What "Pack in die Box" would actually add to this shelf.
    func enqueueableCount(area: String) -> Int {
        guard let box else { return 0 }
        return Int(BoxBrowser.shared.enqueueableCount(state: box, area: area))
    }

    /// What one listed card's row has to state about itself. `packOffered` is
    /// the row's context, not the card's: a search hit packs a single word, an
    /// area listing leaves that to the shelf's own control.
    func cardRowState(_ cardID: String, packOffered: Bool) -> CardRowState {
        guard let box else { return CardRowState.Plain.shared }
        return BoxBrowser.shared.cardRowState(state: box, cardId: cardID,
                                              packOffered: packOffered)
    }

    // MARK: - Fortschritt

    /// The trailing days with their review counts AND their place in the current
    /// streak — one walk in kern, so the strip and the flame cannot disagree.
    /// Merges in every OTHER target language's `dailyStats` first: the streak is
    /// one commitment across languages, not one per language (`AppModel.swift`'s
    /// `otherLanguagesDailyStats`, `Statistics.mergeDailyStats`).
    func activityWindow(days: Int = 14, now: Date = Date()) -> [ActivityDay] {
        guard let box else { return [] }
        let combined = mergeDailyStats(dailyStatsByLanguage: otherLanguagesDailyStats + [box.dailyStats])
        return streakWindow(dailyStats: combined, days: Int32(days),
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
