import SwiftUI
import SprossKern

/// The dates drill: the weekday names alone, the month names alone, the
/// day-of-month numeral alone — and then the whole spoken date assembled out of
/// them.
///
/// This is the whole of what makes that page and that run different from the
/// atlas's: kern's calendar machine (`DateDrillRun`), the words the chrome says
/// about it, and the calendar under the start button. The screens themselves
/// are `DrillOverview` and `DrillRunView`.
///
/// Named in TWO languages: the prompt side lends its weekday abbreviations and
/// its digit format, the answer side spells the date out, so the drill exists
/// only where the catalog carries a dates file on BOTH sides
/// (`Catalog.dateDrillContent`).
enum DateDrillFace: DrillFace {

    // MARK: - Who the drill is

    static var key: String { "dates" }

    static var resultTitle: LocalizedStringKey { "trainer.skill.dates" }

    static func title(_ language: String) -> LocalizedStringKey { "dates.title \(language)" }

    static var paceKey: LocalizedStringKey { "dates.pace" }

    static func bestLine(_ best: Int) -> LocalizedStringKey { "dates.best \(best.formatted())" }

    static var fastHintKey: LocalizedStringKey { "dates.fast.hint" }

    static var reverseHintKey: String { "dates.reverse.hint %@ %@" }

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

    static func sprossen(_ content: DateDrillContent?, reverse: Bool) -> [DrillSprosse] {
        guard let content else { return [] }
        let top = ceiling(content, reverse: reverse)
        guard top >= 1 else { return [] }
        return (1...top).map { sprosse in
            let kinds = DateDrill.shared.kinds(content: content, level: sprosse, reverse: reverse)
            return DrillSprosse(title: sprosseTitle(kinds), hint: sprosseHint(kinds))
        }
    }

    static func fastUnlocked(best: Int, content: DateDrillContent, reverse: Bool) -> Bool {
        DateDrill.shared.fastUnlocked(bestLevel: best, content: content, reverse: reverse)
    }

    static func reference(model: AppModel, content: DateDrillContent,
                          source: String, target: String) -> DatesReference {
        DatesReference(model: model, content: content, source: source, target: target)
    }

    // The catalog keys are indexed by KIND in full-ladder order — weekday, month,
    // day, day+month, date, date+year — because the ladder itself has no fixed
    // length: a pair without a year pattern skips index 6, and the number on
    // screen is the row's own position. A Sprosse carries every kind below it, so
    // the LAST one is what it introduced and what it is named for.
    // why: spelled out rather than interpolated — a key built with an index
    // becomes a format string and localizes nothing, and these keys would stop
    // being greppable from the catalog.
    private static func sprosseTitle(_ kinds: [DateTaskKind]) -> LocalizedStringKey {
        switch kinds.last {
        case .weekday: return "dates.sprosse.1"
        case .month: return "dates.sprosse.2"
        case .dayOfMonth: return "dates.sprosse.3"
        case .dayAndMonth: return "dates.sprosse.4"
        case .fullDate: return "dates.sprosse.5"
        default: return "dates.sprosse.6"
        }
    }

    private static func sprosseHint(_ kinds: [DateTaskKind]) -> LocalizedStringKey {
        switch kinds.last {
        case .weekday: return "dates.sprosse.1.hint"
        case .month: return "dates.sprosse.2.hint"
        case .dayOfMonth: return "dates.sprosse.3.hint"
        case .dayAndMonth: return "dates.sprosse.4.hint"
        case .fullDate: return "dates.sprosse.5.hint"
        default: return "dates.sprosse.6.hint"
        }
    }

    // MARK: - The run

    static func answerLanguage(content: DateDrillContent, reverse: Bool) -> String {
        DateDrill.shared.answerLanguage(content: content, reverse: reverse)
    }

    static func open(content: DateDrillContent, reverse: Bool, fast: Bool,
                     normalizer: AnswerNormalizer?, level: Int?) -> DateDrillRunState {
        let config = DateDrillRunConfig(content: content, reverse: reverse, fast: fast,
                                        normalizer: normalizer)
        guard let level else { return DateDrillRun.shared.open(config: config, rng: drillRandom) }
        return DateDrillRun.shared.openAt(config: config, level: Int32(level), rng: drillRandom)
    }

    static func snapshot(_ run: DateDrillRunState) -> DrillSnapshot {
        // A dates question carries no picture: the leading slot stays empty and
        // the prompt — a name, or a dated line in the prompt side's digits —
        // stands where the country's name would.
        DrillSnapshot(index: Int(run.index), level: Int(run.level),
                      streak: Int(run.streak), bestStreak: Int(run.bestStreak),
                      tally: run.tally, outcomes: run.outcomes, feedback: run.feedback,
                      offersFinish: run.offersFinish, finished: run.finished,
                      answerLanguage: run.answerLanguage, promptLanguage: run.promptLanguage,
                      ask: ask(run.task.kind), promptText: run.task.promptText,
                      promptIsAName: isAName(run.task.kind),
                      promptEmoji: nil, emojiIsGiveaway: false,
                      display: run.task.display, gloss: nil, otherWord: run.otherWord)
    }

    static func reduce(_ run: DateDrillRunState, _ move: DrillMove) -> DrillStep<DateDrillRunState> {
        let reduction = DateDrillRun.shared.reduce(state: run, intent: intent(move), rng: drillRandom)
        return DrillStep(run: reduction.state, effects: reduction.effects)
    }

    static func close(_ run: DateDrillRunState, standingRecord: Int) -> DrillEnd<DateDrillRunState> {
        let closed = DateDrillRun.shared.close(state: run, standingRecord: Int32(standingRecord))
        return DrillEnd(run: closed.state, summary: closed.summary,
                        bestLevel: Int(closed.bestLevel), effects: closed.effects)
    }

    private static func intent(_ move: DrillMove) -> DateDrillIntent {
        switch move {
        case .typed(let text): return DateDrillIntent.InputChanged(text: text)
        case .submitted(let text): return DateDrillIntent.Submit(text: text)
        case .revealed: return DateDrillIntent.Reveal.shared
        case .confirmed: return DateDrillIntent.ConfirmPending.shared
        case .advanced: return DateDrillIntent.AdvanceElapsed.shared
        }
    }

    /// Whether the question is asked with a NAME. The weekday and month Sprossen
    /// are: they show `Montag` and want the other language's word for it, so the
    /// name may be heard like any other. The three assembled kinds and the day
    /// are not — `3.` and `Mo, 3.3.` are renderings whose READING is exactly the
    /// answer owed, and a voice saying one would hand it over.
    private static func isAName(_ kind: DateTaskKind) -> Bool {
        switch kind {
        case .weekday, .month: return true
        default: return false
        }
    }

    /// What the question asks — the kind names the rule, and this is the only
    /// place it turns into words. The three assembled kinds share one sentence:
    /// what changes between them is on the card, not in the ask.
    private static func ask(_ kind: DateTaskKind) -> LocalizedStringKey {
        switch kind {
        case .weekday: return "dates.ask.weekday"
        case .month: return "dates.ask.month"
        case .dayOfMonth: return "dates.ask.day"
        case .dayAndMonth, .fullDate, .fullDateWithYear: return "dates.ask.date"
        }
    }

    #if DEBUG
    static var uitestLevelKey: String { "uitest-dates-level" }

    static var uitestBestKey: String { "uitest-dates-best" }

    static func seedStreak(_ run: DateDrillRunState, _ streak: Int) -> DateDrillRunState {
        run.doCopy(config: run.config, task: run.task, index: run.index,
                   level: run.level, bestLevel: run.bestLevel,
                   winsAtLevel: run.winsAtLevel,
                   core: run.core.doCopy(done: Int32(streak + 6),
                                         streak: Int32(streak),
                                         bestStreak: Int32(max(streak, 12)),
                                         missRun: run.core.missRun,
                                         outcomes: run.core.outcomes,
                                         solved: run.core.solved),
                   feedback: run.feedback,
                   otherWord: run.otherWord, finished: run.finished)
    }
    #endif
}

typealias DatesOverview = DrillOverview<DateDrillFace>
