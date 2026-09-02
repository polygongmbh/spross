import SwiftUI
import SprossKern

/// The atlas drill: name the country, the people, the language — and say which
/// is spoken where.
///
/// This is the whole of what makes that page and that run different from the
/// dates one: kern's atlas machine (`CountryDrillRun`), the words the chrome
/// says about it, and the atlas under the start button. The screens themselves
/// are `DrillOverview` and `DrillRunView`.
///
/// A country's name is a pair rather than a property of the language being
/// learned, and the reference table is the join the drill grades against
/// (`CountryDrill.reference`) rather than a second table beside it.
enum CountryDrillFace: DrillFace {

    // MARK: - Who the drill is

    static var key: String { "countries" }

    static var resultTitle: LocalizedStringKey { "trainer.skill.countries" }

    static func title(_ language: String) -> LocalizedStringKey { "countries.title \(language)" }

    static var paceKey: LocalizedStringKey { "countries.pace" }

    static func bestLine(_ best: Int) -> LocalizedStringKey { "countries.best \(best.formatted())" }

    static var fastHintKey: LocalizedStringKey { "countries.fast.hint" }

    static var reverseHintKey: String { "countries.reverse.hint %@ %@" }

    // MARK: - The atlas

    static func content(_ catalog: Catalog?, source: String, target: String) -> CountryDrillContent? {
        catalog?.countryDrillContent(source: source, target: target)
    }

    /// Every Sprosse the ladder has — kern's ceiling, never a count written down
    /// beside it. The atlas ladder is the same nine whatever the pair carries:
    /// a tier nobody has authored yet repeats the pool below it.
    static func ceiling(_ content: CountryDrillContent?, reverse: Bool) -> Int {
        max(1, CountryDrill.shared.ceiling)
    }

    /// Each Sprosse names ONE new thing, because that is all a Sprosse brings
    /// (`CountryDrill`).
    static func sprossen(_ content: CountryDrillContent?, reverse: Bool) -> [DrillSprosse] {
        (1...ceiling(content, reverse: reverse)).map {
            DrillSprosse(title: sprosseTitle($0), hint: sprosseHint($0))
        }
    }

    static func fastUnlocked(best: Int, content: CountryDrillContent, reverse: Bool) -> Bool {
        CountryDrill.shared.fastUnlocked(bestLevel: best)
    }

    static func reference(model: AppModel, content: CountryDrillContent,
                          source: String, target: String) -> CountriesReference {
        CountriesReference(model: model, content: content, source: source, target: target)
    }

    // why: spelled out rather than interpolated — a key built with `\(sprosse)`
    // becomes the format string "countries.sprosse.%lld" and localizes nothing,
    // and these keys would stop being greppable from the catalog.
    private static func sprosseTitle(_ sprosse: Int) -> LocalizedStringKey {
        switch sprosse {
        case 1: return "countries.sprosse.1"
        case 2: return "countries.sprosse.2"
        case 3: return "countries.sprosse.3"
        case 4: return "countries.sprosse.4"
        case 5: return "countries.sprosse.5"
        case 6: return "countries.sprosse.6"
        case 7: return "countries.sprosse.7"
        case 8: return "countries.sprosse.8"
        default: return "countries.sprosse.9"
        }
    }

    private static func sprosseHint(_ sprosse: Int) -> LocalizedStringKey {
        switch sprosse {
        case 1: return "countries.sprosse.1.hint"
        case 2: return "countries.sprosse.2.hint"
        case 3: return "countries.sprosse.3.hint"
        case 4: return "countries.sprosse.4.hint"
        case 5: return "countries.sprosse.5.hint"
        case 6: return "countries.sprosse.6.hint"
        case 7: return "countries.sprosse.7.hint"
        case 8: return "countries.sprosse.8.hint"
        default: return "countries.sprosse.9.hint"
        }
    }

    // MARK: - The run

    static func answerLanguage(content: CountryDrillContent, reverse: Bool) -> String {
        CountryDrill.shared.answerLanguage(content: content, reverse: reverse)
    }

    static func open(content: CountryDrillContent, reverse: Bool, fast: Bool,
                     normalizer: AnswerNormalizer?, level: Int?) -> CountryDrillRunState {
        let config = CountryDrillRunConfig(content: content, reverse: reverse, fast: fast,
                                           normalizer: normalizer)
        guard let level else { return CountryDrillRun.shared.open(config: config, rng: drillRandom) }
        return CountryDrillRun.shared.openAt(config: config, level: Int32(level), rng: drillRandom)
    }

    static func snapshot(_ run: CountryDrillRunState) -> DrillSnapshot {
        DrillSnapshot(index: Int(run.index), level: Int(run.level),
                      streak: Int(run.streak), bestStreak: Int(run.bestStreak),
                      tally: run.tally, outcomes: run.outcomes, feedback: run.feedback,
                      offersFinish: run.offersFinish, finished: run.finished,
                      answerLanguage: run.answerLanguage, promptLanguage: run.promptLanguage,
                      ask: ask(run.task.kind), promptText: run.task.promptText,
                      promptEmoji: run.task.promptEmoji,
                      emojiIsGiveaway: run.task.emojiIsGiveaway,
                      display: run.task.display, gloss: run.task.gloss,
                      otherWord: run.otherWord)
    }

    static func reduce(_ run: CountryDrillRunState,
                       _ move: DrillMove) -> DrillStep<CountryDrillRunState> {
        let reduction = CountryDrillRun.shared.reduce(state: run, intent: intent(move),
                                                      rng: drillRandom)
        return DrillStep(run: reduction.state, effects: reduction.effects)
    }

    static func close(_ run: CountryDrillRunState,
                      standingRecord: Int) -> DrillEnd<CountryDrillRunState> {
        let closed = CountryDrillRun.shared.close(state: run, standingRecord: Int32(standingRecord))
        return DrillEnd(run: closed.state, summary: closed.summary,
                        bestLevel: Int(closed.bestLevel), effects: closed.effects)
    }

    private static func intent(_ move: DrillMove) -> CountryDrillIntent {
        switch move {
        case .typed(let text): return CountryDrillIntent.InputChanged(text: text)
        case .submitted(let text): return CountryDrillIntent.Submit(text: text)
        case .revealed: return CountryDrillIntent.Reveal.shared
        case .confirmed: return CountryDrillIntent.ConfirmPending.shared
        case .advanced: return CountryDrillIntent.AdvanceElapsed.shared
        }
    }

    /// What the question asks — the kind names the rule, and this is the only
    /// place it turns into words.
    private static func ask(_ kind: CountryTaskKind) -> LocalizedStringKey {
        switch kind {
        case .countryName: return "countries.ask.country"
        case .flagCountry: return "countries.ask.flag"
        case .languageName: return "countries.ask.language"
        case .nationality: return "countries.ask.nationality"
        case .spokenIn: return "countries.ask.spokenIn"
        case .spokenWhere: return "countries.ask.spokenWhere"
        }
    }

    #if DEBUG
    static var uitestLevelKey: String { "uitest-countries-level" }

    static var uitestBestKey: String { "uitest-countries-best" }

    static func seedStreak(_ run: CountryDrillRunState, _ streak: Int) -> CountryDrillRunState {
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

/// The "Länder" hub entry: the shared overview wearing the atlas face. The name
/// stays because it is what the hub, the stored key and the Android twin call
/// this page.
typealias CountriesOverview = DrillOverview<CountryDrillFace>
