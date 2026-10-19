import SwiftUI
import SprossKern

/// The dates drill: the weekday names alone, the month names alone, the
/// day-of-month numeral alone — and then the whole spoken date assembled out of
/// them.
///
/// Named in TWO languages: the prompt side lends its weekday abbreviations and
/// its digit format, the answer side spells the date out, so the drill exists
/// only where the catalog carries a dates file on BOTH sides
/// (`Catalog.dateDrillContent`).
enum DateDrillFace: DrillFace {

    // MARK: - Who the drill is

    static var key: String { "dates" }
    static var drill: Drill { .dates }
    static func storageKey(source: String, target: String) -> String {
        DateDrill.shared.storageKey(source: source, target: target)
    }

    static var resultTitle: LocalizedStringKey { "trainer.drill.dates" }

    static func title(_ language: String) -> LocalizedStringKey { "dates.title \(language)" }

    static var winsToAdvance: Int { Int(PairDrill.shared.winsToAdvance(fast: false)) }

    /// Turned round the calendar asks for a DATE, written in digits rather than
    /// in either language — which is a different sentence, not a swapped one.
    static func reverseHintKey(reverse: Bool) -> String {
        reverse ? "dates.reverse.hint.back %@ %@" : "dates.reverse.hint %@ %@"
    }

    // MARK: - The calendars

    static func content(_ catalog: Catalog?, source: String, target: String) -> DateDrillContent? {
        catalog?.dateDrillContent(source: source, target: target)
    }

    /// Not one fixed number: how tall the ladder is depends on what the pair's
    /// content carries (no `dateWithYear` pattern, no year Sprosse) and which
    /// way round the run asks (reversed, only the name Sprossen stand).
    static func ceiling(_ content: DateDrillContent?, reverse: Bool) -> Int {
        content.map { DateDrill.shared.ceiling(content: $0, reverse: reverse) } ?? 1
    }

    static func sprossen(_ content: DateDrillContent?, reverse: Bool) -> [LocalizedStringKey] {
        guard let content else { return [] }
        let top = ceiling(content, reverse: reverse)
        guard top >= 1 else { return [] }
        return (1...top).map { sprosse in
            sprosseTitle(DateDrill.shared.kinds(content: content, sprosse: sprosse, reverse: reverse))
        }
    }

    static func fastUnlocked(best: Int, content: DateDrillContent, reverse: Bool) -> Bool {
        PairDrill.shared.fastUnlocked(bestSprosse: best, top: ceiling(content, reverse: reverse))
    }

    static func fastPrice(_ content: DateDrillContent?, reverse: Bool) -> Int {
        PairDrill.shared.fastPrice(top: ceiling(content, reverse: reverse))
    }

    static func reference(model: AppModel, content: DateDrillContent,
                          source: String, target: String) -> DatesReference {
        DatesReference(model: model, content: content, source: source, target: target)
    }

    // The catalog keys are indexed by KIND in full-ladder order — the tapped
    // names, weekday, month, day+month, date, date+year — because the ladder
    // itself has no fixed length: a pair without a year pattern skips index 6,
    // and the number on screen is the row's own position. A Sprosse carries
    // every kind below it, so the LAST one is what it introduced and is named for.
    // why: a key per kind, spelled out — an interpolated one is a format string
    // and localizes nothing (`docs/design.md`, chrome keys).
    // why: internal, not private — the calendar under the ladder heads its two
    // groups with the same Sprosse names.
    static func sprosseTitle(_ kinds: [DateTaskKind]) -> LocalizedStringKey {
        switch kinds.last {
        case .nameChoice: return "dates.sprosse.1"
        case .weekday: return "dates.sprosse.2"
        case .month: return "dates.sprosse.3"
        case .dayAndMonth: return "dates.sprosse.4"
        case .fullDate: return "dates.sprosse.5"
        default: return "dates.sprosse.6"
        }
    }

    // MARK: - The run

    static func answerLanguage(content: DateDrillContent, reverse: Bool) -> String {
        PairDrill.shared.answerLanguage(source: content.source, target: content.target, reverse: reverse)
    }

    static func open(content: DateDrillContent, reverse: Bool, fast: Bool,
                     normalizer: AnswerNormalizer?, sprosse: Int?,
                     standingRecord: Int, cleared: Set<KotlinInt>) -> PairDrillRunState<DateDrillTask> {
        let config = DateDrillRunConfig(content: content, reverse: reverse, fast: fast,
                                        normalizer: normalizer, standingRecord: Int32(standingRecord),
                                        cleared: cleared)
        guard let sprosse else { return DateDrillRun.shared.open(config: config, rng: drillRandom) }
        return DateDrillRun.shared.openAt(config: config, sprosse: Int32(sprosse), rng: drillRandom)
    }

    static func snapshot(_ run: PairDrillRunState<DateDrillTask>) -> DrillSnapshot {
        DrillSnapshot(sprosse: Int(run.sprosse), answerLanguage: run.answerLanguage,
                      choices: run.task.choices, digits: run.task.digits)
    }

    static func reduce(_ run: PairDrillRunState<DateDrillTask>, _ move: DrillMove) -> DrillStep<PairDrillRunState<DateDrillTask>> {
        let reduction = DateDrillRun.shared.reduce(state: run, intent: move.pairIntent, rng: drillRandom)
        return DrillStep(run: reduction.state, effects: reduction.effects)
    }

    static func close(_ run: PairDrillRunState<DateDrillTask>, standingRecord: Int) -> DrillEnd<PairDrillRunState<DateDrillTask>> {
        let closed = DateDrillRun.shared.close(state: run, standingRecord: Int32(standingRecord))
        return DrillEnd(run: closed.state, summary: closed.summary,
                        bookings: closed.bookings(), effects: closed.effects)
    }

    #if DEBUG
    static var uitestSprosseKey: String { "uitest-dates-level" }

    static var uitestBestKey: String { "uitest-dates-best" }

    static func seedAnswerStreak(_ run: PairDrillRunState<DateDrillTask>,
                                 _ answerStreak: Int) -> PairDrillRunState<DateDrillTask> {
        seededAnswerStreak(run, answerStreak)
    }
    #endif
}

typealias DatesOverview = DrillOverview<DateDrillFace>
