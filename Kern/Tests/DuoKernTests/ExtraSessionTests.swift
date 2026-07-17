import Foundation
import Testing
@testable import DuoKern

@Suite struct ExtraSessionTests {
    let calendar = Calendar(identifier: .gregorian)
    let day0 = Date(timeIntervalSince1970: 1_750_000_000)

    func word(_ n: Int) -> Card {
        Card(id: "w\(n)", kind: .noun, pair: .deSw, area: "kitchen",
             german: "Wort\(n)", translation: "neno\(n)", seedIndex: n)
    }

    func boxWithActive(_ count: Int, of total: Int? = nil, config: BoxConfig = BoxConfig(pair: .deSw)) -> BoxState {
        var state = BoxEngine.bootstrap(cards: (0..<(total ?? max(count, 1))).map(word), config: config)
        var t = day0
        for i in 0..<count {
            state = BoxEngine.enqueue(state: state, cardIDs: ["w\(i)"])
            state = BoxEngine.answer(state: state, cardID: "w\(i)", rating: .easy, now: t, calendar: calendar)
            t = t.addingTimeInterval(60)
        }
        return state
    }

    @Test func extraRoundNeverEmptyWhileBoxHasActiveCards() {
        let state = boxWithActive(8)
        // Well before anything is due: plain compose is empty of reviews,
        // but the extra round pulls review-ahead cards.
        let later = day0.addingTimeInterval(3600)
        let extra = BoxEngine.composeExtraSession(state: state, now: later, calendar: calendar)
        #expect(!extra.isEmpty)
        #expect(extra.reviews.count == 8)
    }

    @Test func enqueuedNewCardsAppearAndBypassExhaustedBudget() {
        var config = BoxConfig(pair: .deSw)
        config.newPerDay = 2
        var state = boxWithActive(2, of: 4, config: config) // budget for the day used up
        state = BoxEngine.enqueue(state: state, cardIDs: ["w2", "w3"])
        // Regular compose offers nothing new (budget exhausted)…
        let plan = BoxEngine.composeSession(state: state, now: day0.addingTimeInterval(600), calendar: calendar)
        #expect(plan.newWords.isEmpty)
        // …but the extra round carries the explicit enqueues,
        let extra = BoxEngine.composeExtraSession(state: state, now: day0.addingTimeInterval(600), calendar: calendar)
        #expect(extra.newWords == ["w2", "w3"])
        // and answering them actually introduces (budget bypass for enqueued).
        let after = BoxEngine.answer(state: state, cardID: "w2", rating: .good,
                                     now: day0.addingTimeInterval(700), calendar: calendar)
        #expect(after.scheduling[BoxState.schedulingKey(cardID: "w2", direction: .deToTarget)] != nil)
        #expect(after.enqueued == ["w3"])
    }

    @Test func nonEnqueuedIntroductionStillNoOpsWhenBudgetExhausted() {
        var config = BoxConfig(pair: .deSw)
        config.newPerDay = 1
        let state = boxWithActive(1, config: config)
        let after = BoxEngine.answer(state: state, cardID: "w0", rating: .good,
                                     now: day0, calendar: calendar) // w0 already scheduled → review, fine
        _ = after
        var fresh = BoxEngine.bootstrap(cards: [word(0), word(1)], config: config)
        fresh = BoxEngine.answer(state: fresh, cardID: "w0", rating: .good, now: day0, calendar: calendar)
        let blocked = BoxEngine.answer(state: fresh, cardID: "w1", rating: .good,
                                       now: day0.addingTimeInterval(60), calendar: calendar)
        #expect(blocked.scheduling[BoxState.schedulingKey(cardID: "w1", direction: .deToTarget)] == nil)
    }

    @Test func upcomingStepsFindsLearningStepWithinHorizonOnly() {
        var state = BoxEngine.bootstrap(cards: [word(0)], config: BoxConfig(pair: .deSw))
        state = BoxEngine.answer(state: state, cardID: "w0", rating: .good, now: day0, calendar: calendar)
        // .good on a new card → learning step due in 10 min.
        let soon = BoxEngine.upcomingSteps(state: state, now: day0, within: 15 * 60)
        #expect(soon.map(\.cardID) == ["w0"])
        let tooShort = BoxEngine.upcomingSteps(state: state, now: day0, within: 60)
        #expect(tooShort.isEmpty)
        let alreadyDue = BoxEngine.upcomingSteps(state: state, now: day0.addingTimeInterval(700), within: 900)
        #expect(alreadyDue.isEmpty) // due now is the drain loop's job, not upcoming
    }
}

@Suite struct AnswerNormalizerTests {
    @Test func normalizationRules() {
        #expect(AnswerNormalizer.normalize("  Die Spülmaschine! ") == "spülmaschine")
        #expect(AnswerNormalizer.normalize("E-Mail") == AnswerNormalizer.normalize("Email"))
        #expect(AnswerNormalizer.normalize("die") == "die") // bare article stays
        #expect(AnswerNormalizer.normalize("Guten   Morgen!") == "guten morgen")
    }

    @Test func alternativeTranslationsMatch() {
        #expect(AnswerNormalizer.matches(input: "стелаж", expected: "полиця / стелаж"))
        #expect(AnswerNormalizer.matches(input: "полиця", expected: "полиця / стелаж"))
        #expect(!AnswerNormalizer.matches(input: "", expected: "полиця / стелаж"))
        #expect(AnswerNormalizer.matches(input: "Kühlschrank", expected: "der Kühlschrank"))
    }

    @Test func typoToleranceScalesWithLength() {
        // ~10% of letters, minimum word length 5; transposition = 1 edit.
        #expect(AnswerNormalizer.evaluate(input: "Kuhlschrank", expected: "Kühlschrank")
                == .typo(corrected: "kühlschrank")) // diacritic slip rides the rule
        #expect(AnswerNormalizer.evaluate(input: "Spulmaschine", expected: "die Spülmaschine")
                == .typo(corrected: "spülmaschine"))
        #expect(AnswerNormalizer.evaluate(input: "firji", expected: "friji")
                == .typo(corrected: "friji")) // adjacent transposition, 5 letters
        #expect(AnswerNormalizer.evaluate(input: "kula", expected: "kile") == .wrong) // short words: exact only
        #expect(AnswerNormalizer.evaluate(input: "kula", expected: "kula") == .exact)
        // Two errors in a medium word stay wrong (tolerance 1 up to 19 letters).
        #expect(AnswerNormalizer.evaluate(input: "Spolmascine", expected: "Spülmaschine") == .wrong)
    }
}
