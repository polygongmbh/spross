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

    static var winsToAdvance: Int { Int(DateDrill.shared.winsToAdvance(fast: false)) }

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
        DateDrill.shared.fastUnlocked(bestSprosse: best, content: content, reverse: reverse)
    }

    static func fastPrice(_ content: DateDrillContent?, reverse: Bool) -> Int {
        content.map { Int(DateDrill.shared.fastPrice(content: $0, reverse: reverse)) } ?? 1
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
        DateDrill.shared.answerLanguage(content: content, reverse: reverse)
    }

    static func open(content: DateDrillContent, reverse: Bool, fast: Bool,
                     normalizer: AnswerNormalizer?, sprosse: Int?,
                     standingRecord: Int, cleared: Set<KotlinInt>) -> DateDrillRunState {
        let config = DateDrillRunConfig(content: content, reverse: reverse, fast: fast,
                                        normalizer: normalizer, standingRecord: Int32(standingRecord),
                                        cleared: cleared)
        guard let sprosse else { return DateDrillRun.shared.open(config: config, rng: drillRandom) }
        return DateDrillRun.shared.openAt(config: config, sprosse: Int32(sprosse), rng: drillRandom)
    }

    static func snapshot(_ run: DateDrillRunState) -> DrillSnapshot {
        DrillSnapshot(index: Int(run.index), sprosse: Int(run.sprosse),
                      answerStreak: Int(run.answerStreak), bestAnswerStreak: Int(run.bestAnswerStreak),
                      tally: run.tally, outcomes: run.outcomes, feedback: run.feedback,
                      finished: run.finished,
                      answerLanguage: run.answerLanguage, question: run.question,
                      choices: run.task.choices, digits: run.task.digits)
    }

    static func reduce(_ run: DateDrillRunState, _ move: DrillMove) -> DrillStep<DateDrillRunState> {
        let reduction = DateDrillRun.shared.reduce(state: run, intent: intent(move), rng: drillRandom)
        return DrillStep(run: reduction.state, effects: reduction.effects)
    }

    static func close(_ run: DateDrillRunState, standingRecord: Int) -> DrillEnd<DateDrillRunState> {
        let closed = DateDrillRun.shared.close(state: run, standingRecord: Int32(standingRecord))
        return DrillEnd(run: closed.state, summary: closed.summary,
                        bestSprosse: Int(closed.bestSprosse),
                        clearedSprossen: closed.clearedSprossen, effects: closed.effects)
    }

    private static func intent(_ move: DrillMove) -> DateDrillIntent {
        switch move {
        case .typed(let text): return DateDrillIntent.InputChanged(text: text)
        case .submitted(let text): return DateDrillIntent.Submit(text: text)
        case .confirmed: return DateDrillIntent.ConfirmPending.shared
        case .advanced: return DateDrillIntent.AdvanceElapsed.shared
        case .keptPracticing: return DateDrillIntent.KeepPracticing.shared
        }
    }

    #if DEBUG
    static var uitestSprosseKey: String { "uitest-dates-level" }

    static var uitestBestKey: String { "uitest-dates-best" }

    static func seedAnswerStreak(_ run: DateDrillRunState, _ answerStreak: Int) -> DateDrillRunState {
        run.doCopy(config: run.config, task: run.task, index: run.index,
                   sprosse: run.sprosse, bestSprosse: run.bestSprosse,
                   winsAtSprosse: run.winsAtSprosse,
                   core: run.core.doCopy(done: Int32(answerStreak + 6),
                                         answerStreak: Int32(answerStreak),
                                         bestAnswerStreak: Int32(max(answerStreak, 12)),
                                         missRun: run.core.missRun,
                                         outcomes: run.core.outcomes,
                                         solved: run.core.solved,
                                         slipped: run.core.slipped,
                                         solvedClean: run.core.solvedClean,
                                               pacing: run.core.pacing),
                   feedback: run.feedback,
                   otherWord: run.otherWord, seenKinds: run.seenKinds, finished: run.finished)
    }
    #endif
}

typealias DatesOverview = DrillOverview<DateDrillFace>
