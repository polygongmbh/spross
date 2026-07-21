import Foundation
import Testing
import DuoKern

@Suite("Box growth: budget, health gate, introduction")
struct BoxGrowthTests {
    let cal = Box.calendar
    let now = Box.day1

    @Test("day-one bootstrap: empty box fills the learning pool despite 0 active cards")
    func dayOneBootstrap() {
        let state = Box.state(cards: (1...10).map { Box.word($0) }, Box.config(maxLearning: 5))
        let plan = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        #expect(plan.reviews.isEmpty)
        #expect(plan.unlockedPhrases.isEmpty)
        #expect(plan.newWords == ["w01", "w02", "w03", "w04", "w05"])
        #expect(!plan.isEmpty)
    }

    @Test("growth blocked when relearning share >= 20% with >= 10 active")
    func relearningGateBlocks() {
        var state = Box.state(cards: (1...12).map { Box.word($0) })
        let future = now.addingTimeInterval(5 * 86_400)
        for n in 1...8 {
            Box.inject(&state, Box.sched(String(format: "w%02d", n), due: future,
                                         lastReview: now.addingTimeInterval(-86_400)))
        }
        for n in 9...10 {
            Box.inject(&state, Box.sched(String(format: "w%02d", n),
                                         phase: .relearning, due: future,
                                         lastReview: now.addingTimeInterval(-86_400)))
        }
        // 2 of 10 active relearning = 20% → gate closed
        let plan = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        #expect(plan.newWords.isEmpty)
        #expect(plan.unlockedPhrases.isEmpty)
        let stats = BoxEngine.statistics(state: state, now: now, calendar: cal)
        #expect(stats.newSlotsAvailable == 0)

        // Drop to 1 of 10 (10%) → gate open again
        var healthy = state
        Box.inject(&healthy, Box.sched("w10", phase: .review, due: future,
                                       lastReview: now.addingTimeInterval(-86_400)))
        let plan2 = BoxEngine.composeSession(state: healthy, now: now, calendar: cal)
        #expect(plan2.newWords == ["w11", "w12"])
    }

    @Test("relearning sub-gate passes below 10 active cards (bootstrap clause)")
    func relearningGateSkippedWhenSmall() {
        var state = Box.state(cards: (1...8).map { Box.word($0) })
        let future = now.addingTimeInterval(5 * 86_400)
        for n in 1...2 {
            Box.inject(&state, Box.sched("w0\(n)", phase: .relearning, due: future,
                                         lastReview: now.addingTimeInterval(-86_400)))
        }
        for n in 3...5 {
            Box.inject(&state, Box.sched("w0\(n)", due: future, lastReview: now.addingTimeInterval(-86_400)))
        }
        // 2 of 5 relearning = 40%, but activeCount < 10 → still introduces
        let plan = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        #expect(plan.newWords == ["w06", "w07", "w08"])
    }

    @Test("growth blocked when projected post-session backlog >= dueSoftCap")
    func backlogGateBlocks() {
        var state = Box.state(cards: (1...70).map { Box.word($0) })
        for n in 1...61 {
            let id = String(format: "w%02d", n)
            Box.inject(&state, Box.sched(id, due: now.addingTimeInterval(-Double(n) * 60),
                                         lastReview: now.addingTimeInterval(-86_400)))
        }
        // 61 due − 30 sessionCap = 31 ≥ dueSoftCap 30 → closed; no slots reserved
        let plan = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        #expect(plan.newWords.isEmpty)
        #expect(plan.reviews.count == 30)
    }

    @Test("pool budget tracks the learning load: compose, answer some, recompose")
    func budgetAcrossRecomposition() {
        var state = Box.state(cards: (1...10).map { Box.word($0) }, Box.config(maxLearning: 5))
        let plan = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        #expect(plan.newWords.count == 5)

        // Answering .good moves each card into .learning, filling the pool.
        for id in plan.newWords.prefix(3) {
            state = BoxEngine.answer(state: state, cardID: id, rating: .good, now: now, calendar: cal)
        }
        let plan2 = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        #expect(plan2.newWords == ["w04", "w05"]) // 5 pool − 3 learning = 2 free

        for id in plan2.newWords {
            state = BoxEngine.answer(state: state, cardID: id, rating: .good, now: now, calendar: cal)
        }
        let plan3 = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        #expect(plan3.newWords.isEmpty) // pool full (5 in .learning)
    }

    @Test("pool refills as learning cards graduate to review")
    func poolRefillsOnGraduation() {
        var state = Box.state(cards: (1...10).map { Box.word($0) }, Box.config(maxLearning: 3))
        // Fill the pool: 3 cards into .learning.
        for id in ["w01", "w02", "w03"] {
            state = BoxEngine.answer(state: state, cardID: id, rating: .good, now: now, calendar: cal)
        }
        #expect(BoxEngine.composeSession(state: state, now: now, calendar: cal).newWords.isEmpty)

        // Graduate w01 (answer its learning step .good → .review) frees one slot.
        let step = now.addingTimeInterval(700)
        state = BoxEngine.answer(state: state, cardID: "w01", rating: .good, now: step, calendar: cal)
        let refilled = BoxEngine.composeSession(state: state, now: step, calendar: cal)
        #expect(refilled.newWords == ["w04"]) // one graduated → one new pulled in
    }

    @Test("introduction at first answer: composing without answering burns no budget")
    func compositionBurnsNoBudget() {
        let state = Box.state(cards: (1...10).map { Box.word($0) })
        var current = state
        for _ in 0..<5 {
            _ = BoxEngine.composeSession(state: current, now: now, calendar: cal)
        }
        #expect(current == state)
        #expect(current.newIntroduced.isEmpty)
        #expect(current.scheduling.isEmpty)
    }

    @Test("pool budget re-checked defensively at answer time; a full pool blocks introduction")
    func budgetDefensiveAtAnswer() {
        var state = Box.state(cards: (1...5).map { Box.word($0) }, Box.config(maxLearning: 2))
        state = BoxEngine.answer(state: state, cardID: "w01", rating: .good, now: now, calendar: cal)
        state = BoxEngine.answer(state: state, cardID: "w02", rating: .good, now: now, calendar: cal)
        // Pool full (2 in .learning) → introducing a third is a no-op.
        let blocked = BoxEngine.answer(state: state, cardID: "w03", rating: .good, now: now, calendar: cal)
        #expect(blocked == state)
        #expect(blocked.scheduling.count == 2)

        // Graduate w01 → a slot frees → the same answer now succeeds.
        let step = now.addingTimeInterval(700)
        var freed = BoxEngine.answer(state: state, cardID: "w01", rating: .good, now: step, calendar: cal)
        freed = BoxEngine.answer(state: freed, cardID: "w03", rating: .good, now: step, calendar: cal)
        #expect(freed.scheduling.count == 3)
    }

    @Test("enqueued ids lead within the pool budget; phrase enqueue pulls missing components first")
    func enqueuePriority() {
        var state = Box.state(cards: (1...10).map { Box.word($0) }, Box.config(maxLearning: 5))
        state = BoxEngine.enqueue(state: state, cardIDs: ["w07"])
        let plan = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        // w07 leads but shares the pool budget (5): 1 enqueued + 4 automatic = 5.
        #expect(plan.newWords == ["w07", "w01", "w02", "w03", "w04"])

        var withPhrase = Box.state(cards: (1...6).map { Box.word($0) }
            + [Box.phrase("p1", components: ["w05", "w06"])], Box.config(maxLearning: 5))
        withPhrase = BoxEngine.enqueue(state: withPhrase, cardIDs: ["p1"])
        #expect(withPhrase.enqueued == ["w05", "w06", "p1"])
        let plan2 = BoxEngine.composeSession(state: withPhrase, now: now, calendar: cal)
        // locked phrase never enters, even enqueued; its components lead, then
        // automatic fills the rest of the pool budget (2 enqueued + 3 automatic).
        #expect(plan2.newWords == ["w05", "w06", "w01", "w02", "w03"])
    }

    @Test("enqueued cards respect the load throttle: a full pool defers a pack, it is not dumped")
    func enqueuedRespectsLoadThrottle() {
        var state = Box.state(cards: (1...10).map { Box.word($0) }, Box.config(maxLearning: 2))
        // Pack three cards, but the pool budget is only 2 → at most 2 surface.
        state = BoxEngine.enqueue(state: state, cardIDs: ["w06", "w07", "w08"])
        let plan = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        #expect(plan.newWords == ["w06", "w07"]) // drips in at pool rate, not all three

        // Fill the pool by answering them → next session defers the rest.
        state = BoxEngine.answer(state: state, cardID: "w06", rating: .good, now: now, calendar: cal)
        state = BoxEngine.answer(state: state, cardID: "w07", rating: .good, now: now, calendar: cal)
        #expect(BoxEngine.composeSession(state: state, now: now, calendar: cal).newWords.isEmpty)
        #expect(state.enqueued == ["w08"]) // still waiting for a slot
    }

    @Test("answering an enqueued card removes it from the queue")
    func answerDequeues() {
        var state = Box.state(cards: (1...3).map { Box.word($0) })
        state = BoxEngine.enqueue(state: state, cardIDs: ["w02"])
        state = BoxEngine.answer(state: state, cardID: "w02", rating: .good, now: now, calendar: cal)
        #expect(state.enqueued.isEmpty)
    }
}
