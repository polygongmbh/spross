import Foundation
import SprossKern

// Read-side derivations: Home values, box browsing, presentation
// resolution, and the activity window. Every count is in cards
// (kern/README.md §3 — one schedule per card).

extension AppModel {

    // MARK: - Home-derived values

    // Every value below is one of `home`'s, taken when the box last moved
    // (`AppModel.refreshStats`). They read as properties because the screens
    // read them as facts — but each is a walk of the box, and two of them
    // compose a whole round, so none of them is derived here.

    /// Whether there is a round to sit down to — the offer's own answer.
    var hasRound: Bool { home?.offer.hasRound ?? false }

    /// Today's round as kern classified it. A box that has not loaded offers nothing.
    var homeOffer: SessionOffer {
        home?.offer ?? SessionOffer(kind: .nothing, reviews: 0, dueHeldBack: 0, ahead: 0, newCards: 0,
                                    shortRound: 0, doneToday: 0, streakExposed: false, dueNow: 0)
    }

    /// What the learner did today — reviews, first meetings, words that settled,
    /// and whether today's recall has fallen far enough to suggest stopping.
    var today: TodayReport? { home?.today }

    /// Cards that will be due by tomorrow evening (preview on the done state) —
    /// the horizon is kern's, not a second local-midnight derivation.
    var tomorrowDueCount: Int { Int(home?.tomorrowDue ?? 0) }

    /// What a done day says about the next one (`tomorrowNote`).
    var tomorrowNote: TomorrowNote { home?.tomorrow ?? .empty }

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

    /// Whether this card has cleared the growing bar — gate (a): phrase unlock,
    /// the drill pools, and the support a word gets on its way in.
    func hasArrived(_ cardID: String) -> Bool {
        guard let box else { return false }
        return BoxEngine.shared.hasArrived(state: box, cardId: cardID)
    }

    /// Whether a produce review of this card is typed or recalled: a word past the
    /// settled bar is typed only every other time (contract §3).
    func produceAnswer(for cardID: String) -> ProduceAnswer {
        let settled = box.map { BoxEngine.shared.hasSettled(state: $0, cardId: cardID) } ?? false
        return SprossKern.produceAnswer(cardId: cardID,
                                        reviewCount: Int32(scheduling(for: cardID)?.reviewCount ?? 0),
                                        settled: settled)
    }

    /// One card's standing, asked by name rather than found in the whole box's `growth`.
    func cardGrowth(_ cardID: String) -> CardGrowth? {
        guard let box else { return nil }
        return BoxEngine.shared.cardGrowth(state: box, cardId: cardID,
                                           nowEpochMillis: Date().epochMillis, tzId: currentTzId())
    }

    /// Which face carries the picture: the prompt only where it cannot give the
    /// answer away, otherwise the reveal (contract §3).
    func emojiCue(for card: Card) -> EmojiCue {
        SprossKern.emojiCue(role: presentationRole(for: card.id),
                                  arrived: hasArrived(card.id))
    }

    /// The form a turn asks with, and which inflected form it is (`turnPrompt`).
    func promptForm(for card: Card, role: PresentationRole, prompt: ProducePrompt) -> PromptForm {
        SprossKern.turnPrompt(card: card, role: role, prompt: prompt,
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
    ///
    /// One pass over every accepted form the join carries — thousands of
    /// normalized strings — so it is built on the first turn that asks and kept
    /// until `refreshStats()` retires it. A card that arrives after the box
    /// moved is still graded against the box standing now: everything that can
    /// move the join refreshes the stats with it.
    var produceGrader: CatalogAnswerGrader? {
        if let cachedProduceGrader { return cachedProduceGrader }
        guard let normalizer = answerNormalizer, let box else { return nil }
        let grader = CatalogAnswerGrader(normalizer: normalizer, cards: Array(box.cards.values))
        cachedProduceGrader = grader
        return grader
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

    /// Area heading, flavor line and icon — kern's `AreaNaming` names every shelf;
    /// only the own shelf's heading is chrome, read from the string catalog. The own
    /// shelf has no author to write it a flavor line.
    func areaTitle(_ area: String) -> String { naming.title(area: area) }
    func areaSubtitle(_ area: String) -> String? { naming.subtitle(area: area) }
    func areaEmoji(_ area: String) -> String { naming.emoji(area: area) }

    private var naming: AreaNaming { areaNaming ?? composedAreaNaming(catalog: nil) }

    func composedAreaNaming(catalog: Catalog?) -> AreaNaming {
        AreaNaming(catalog: catalog, source: catalog == nil ? nil : sourceLanguage,
                   ownTitle: ChromeStrings.string("box.own.shelf", locale: knownLocale),
                   ownSubtitle: nil)
    }

    /// The manifest's groups with the areas this box holds — `BoxBrowser.sections`.
    ///
    /// why: the empty `areaNames` carries the `-uitest-noareas` hook through to
    /// the groups, which is a test affordance kern has no business knowing about;
    /// with areas present the guard changes nothing (an empty box drops every
    /// group anyway).
    /// Held on the model as `areaGroupSections`: `BoxBrowser.sections` re-derives
    /// `areaNames` internally, and the browser reads the shelves three times a redraw.
    func composedAreaGroupSections() -> [AreaGroupSection] {
        guard let catalog, let stats, !areaNames.isEmpty else { return [] }
        return BoxBrowser.shared.sections(catalog: catalog, stats: stats, source: sourceLanguage)
    }

    /// What can carry `language`'s sound here — kern's rule (`AudioCapability`)
    /// over the two halves this side owns: the catalog it loaded and the voice
    /// table the synthesizer reports. Both are lookups; neither walks the join.
    func audioSources(_ language: String) -> AudioCapability {
        guard let catalog else { return .none }
        return audioCapability(catalog: catalog, language: language,
                               hasVoice: Pronouncer.shared.canSpeak(language: language))
    }

    /// Whether the listening card stands (`listeningOffered`). The playlist
    /// itself is dealt when a run opens (`ListeningDriver`).
    var listeningOffered: Bool {
        guard let target = targetLanguage else { return false }
        return SprossKern.listeningOffered(hasWords: box?.cards.isEmpty == false,
                                           source: audioSources(sourceLanguage),
                                           target: audioSources(target))
    }

    /// Whether a single word in the box can be said aloud here — `anyWordAudible`
    /// holds the answer. A device voice says yes without looking and nothing at
    /// all says no without looking; only a queue has to be asked card by card,
    /// because it covers the forms it recorded and no others.
    func composedAnyWordAudible() -> Bool {
        guard let target = targetLanguage else { return false }
        let sources = audioSources(target)
        if sources.hasVoice { return true }
        if sources.silent { return false }
        return box?.cards.values.contains { card in
            pronounceAction(for: card.target.text, lang: card.target.lang) != nil
        } ?? false
    }

    /// The fold the Box browser opens on (`BoxFold.opening`); nothing folds open before there are statistics.
    func openingFold(revealArea: String?) -> BoxFold {
        guard let stats else { return BoxFold(groups: [], areas: []) }
        return BoxFold.companion.opening(sections: areaGroupSections, stats: stats, revealArea: revealArea)
    }

    func areaStats(_ name: String) -> AreaStatistics? { areaStatsByName[name] }

    /// One card by id, or nil where this profile's join holds none — a suggestion's
    /// id, or a word written in a pair this box does not teach.
    func card(_ cardID: String) -> Card? { box?.cards[cardID] }

    func cards(inArea area: String) -> [Card] { cardsByArea[area] ?? [] }

    /// What the shelf's own control offers (`ShelfControl`).
    func shelfControl(area: String) -> ShelfControl {
        ShelfControl.companion.of(counts: shelves[area], stats: areaStats(area))
    }

    /// What one listed card's row has to state about itself. `queueOffered` is
    /// the row's context, not the card's: a search hit queues a single word, an
    /// area listing leaves that to the shelf's own control.
    func cardRowState(_ cardID: String, queueOffered: Bool) -> CardRowState {
        guard let box else { return CardRowState.Plain.shared }
        return BoxBrowser.shared.cardRowState(state: box, cardId: cardID,
                                              queueOffered: queueOffered)
    }

    // MARK: - Activity

    /// The strip's own fortnight, taken with the rest of the standing — `activity` holds it.
    func composedActivityWindow(now: Int64, tzId: String) -> [ActivityDay] {
        guard let box else { return [] }
        return BoxEngine.shared.activityWindow(state: box, days: 14, nowEpochMillis: now, tzId: tzId,
                                               otherLanguagesAnswerDays: otherLanguagesAnswerDays)
    }
}

extension SessionOffer {

    /// The String Catalog key naming this round. Which kind owns the words and
    /// which of its phrasings this round takes are kern's rulings
    /// (`session/SessionOffer.kt`); only the words themselves are ours. The clock
    /// goes in because a run today has not renewed takes the line over from late
    /// morning on.
    var headlineKey: String {
        let line = headline(nowEpochMillis: Date().epochMillis, tzId: currentTzId())
        return "home.offer.headline.\(Self.stem(line.kind)).\(line.variant)"
    }

    /// One string set per kind, keyed by the kind itself so a new kind cannot
    /// silently keep an old kind's words. Kern folds an empty round's kind onto
    /// `newSet` before it gets here, the done card speaking for that round.
    private static func stem(_ kind: HeadlineKind) -> String {
        switch kind {
        case .reviews: return "reviews"
        case .warmUp: return "warmUp"
        case .newSet: return "newSet"
        case .streakReminder: return "streakReminder"
        }
    }
}
