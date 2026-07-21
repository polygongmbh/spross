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

    @Test func enqueuedNewCardsRespectPoolBudgetInBothRounds() {
        var config = BoxConfig(pair: .deSw)
        config.maxLearning = 2 // pool budget of 2 — enqueued lead, but within it
        var state = boxWithActive(0, of: 6, config: config)
        state = BoxEngine.enqueue(state: state, cardIDs: ["w2", "w3", "w4"])
        let t = day0.addingTimeInterval(600)
        // Both rounds surface enqueued cards, but only up to the pool budget (2).
        let plan = BoxEngine.composeSession(state: state, now: t, calendar: calendar)
        #expect(plan.newWords == ["w2", "w3"])
        let extra = BoxEngine.composeExtraSession(state: state, now: t, calendar: calendar)
        #expect(extra.newWords == ["w2", "w3"])
        // Answering introduces within budget; a filled pool then defers the rest.
        var after = BoxEngine.answer(state: state, cardID: "w2", rating: .good,
                                     now: t.addingTimeInterval(100), calendar: calendar)
        after = BoxEngine.answer(state: after, cardID: "w3", rating: .good,
                                 now: t.addingTimeInterval(200), calendar: calendar)
        #expect(after.enqueued == ["w4"])
        // Pool now full (2 in .learning) → w4 stays queued, not introduced.
        let blocked = BoxEngine.answer(state: after, cardID: "w4", rating: .good,
                                       now: t.addingTimeInterval(300), calendar: calendar)
        #expect(blocked.scheduling[BoxState.schedulingKey(cardID: "w4", direction: .deToTarget)] == nil)
        #expect(blocked.enqueued == ["w4"])
    }

    @Test func nonEnqueuedIntroductionStillNoOpsWhenPoolFull() {
        var config = BoxConfig(pair: .deSw)
        config.maxLearning = 1
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

    @Test func endlessGivesDueCardsAndNewButNeverPullsAhead() {
        var config = BoxConfig(pair: .deSw)
        config.maxLearning = 3
        var state = BoxEngine.bootstrap(cards: (0..<5).map(word), config: config)
        state = BoxEngine.enqueue(state: state, cardIDs: ["w0"])
        // w0 → learning, its next step is due in 10 min (not now).
        state = BoxEngine.answer(state: state, cardID: "w0", rating: .good, now: day0, calendar: calendar)

        // 1 min in: w0 is NOT due yet, so endless must not re-show it…
        let soon = BoxEngine.composeEndless(state: state, now: day0.addingTimeInterval(60))
        #expect(!soon.reviews.contains("w0"))
        // …it just keeps introducing new cards while the pool has room (3 − 1 = 2).
        #expect(soon.newWords == ["w1", "w2"])

        // Once w0's step is genuinely due, it comes back as a review.
        let later = BoxEngine.composeEndless(state: state, now: day0.addingTimeInterval(700))
        #expect(later.reviews == ["w0"])
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

    @Test func swahiliVerbPrefixIsOptional() {
        #expect(AnswerNormalizer.evaluate(input: "pika", expected: "kupika", optionalPrefix: "ku") == .exact)
        #expect(AnswerNormalizer.evaluate(input: "kupika", expected: "kupika", optionalPrefix: "ku") == .exact)
        // Typo tolerance applies to the bare stem too; reveal shows the full form.
        #expect(AnswerNormalizer.evaluate(input: "fanya mazoezi", expected: "kufanya mazoezi", optionalPrefix: "ku")
                == .exact)
        // Without the prefix option nothing changes (German "kuscheln" ≠ "scheln").
        #expect(AnswerNormalizer.evaluate(input: "pika", expected: "kupika") == .wrong)
        // Prefix never applies when the target doesn't start with it.
        #expect(AnswerNormalizer.evaluate(input: "ahani", expected: "samahani", optionalPrefix: "ku") == .wrong)
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

    @Test func esszettFoldsToDoubleS() {
        // ß ≡ ss on both sides, independent of the typo budget (ß→ss is 2 edits,
        // which would blow the budget on short words like "weiß").
        #expect(AnswerNormalizer.normalize("Straße") == "strasse")
        #expect(AnswerNormalizer.normalize("Fußball") == AnswerNormalizer.normalize("Fussball"))
        #expect(AnswerNormalizer.evaluate(input: "heissen", expected: "heißen") == .exact)
        #expect(AnswerNormalizer.evaluate(input: "heißen", expected: "heissen") == .exact)
        #expect(AnswerNormalizer.evaluate(input: "weiss", expected: "weiß") == .exact)
        #expect(AnswerNormalizer.matches(input: "Strasse", expected: "Straße"))
    }

    // MARK: - Article & stray-leading-word rule (design §Review UX)

    @Test func wrongArticleCountsAsTypoNotFailure() {
        // Right word, wrong recognized article → typo (still counts).
        if case .typo = AnswerNormalizer.evaluate(input: "das Tisch", expected: "Tisch", expectedArticle: "der") {
        } else { Issue.record("wrong article should be a typo") }
        // Correct article → clean exact; missing article → still exact.
        #expect(AnswerNormalizer.evaluate(input: "der Tisch", expected: "Tisch", expectedArticle: "der") == .exact)
        #expect(AnswerNormalizer.evaluate(input: "Tisch", expected: "Tisch", expectedArticle: "der") == .exact)
        // No expected article → article never checked.
        #expect(AnswerNormalizer.evaluate(input: "das Tisch", expected: "Tisch") == .exact)
    }

    @Test func mistypedLeadingArticleIsTypoInAnyLanguage() {
        // "dee" isn't a real article, so normalize won't strip it — the stray
        // short leading word rule recovers it as a typo rather than a failure.
        if case .typo = AnswerNormalizer.evaluate(input: "dee Tisch", expected: "Tisch", expectedArticle: "der") {
        } else { Issue.record("mistyped article should be a typo") }
        // Works without any article context too (generalization).
        if case .typo = AnswerNormalizer.evaluate(input: "el nyumba", expected: "nyumba") {
        } else { Issue.record("stray short leading word should be a typo") }
        // A long stray leading word is not silently forgiven.
        #expect(AnswerNormalizer.evaluate(input: "großes nyumba", expected: "nyumba") == .wrong)
        // Never strips the only word.
        #expect(AnswerNormalizer.evaluate(input: "dee", expected: "nyumba") == .wrong)
    }
}
