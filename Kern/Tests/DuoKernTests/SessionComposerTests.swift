import Foundation
import Testing
import DuoKern

@Suite("Session composition: caps, ordering, determinism")
struct SessionComposerTests {
    let cal = Box.calendar
    let now = Box.day1

    /// 40 due review cards (staggered past dues) + 10 fresh words.
    private func backloggedState() -> BoxState {
        var state = Box.state(cards: (1...50).map { Box.word($0) })
        for n in 1...40 {
            let id = String(format: "w%02d", n)
            Box.inject(&state, Box.sched(id, due: now.addingTimeInterval(-Double(n) * 3_600),
                                         lastReview: now.addingTimeInterval(-10 * 86_400)))
        }
        return state
    }

    @Test("slot reservation: 40 due, sessionCap 30 -> 25 reviews + 5 new slots")
    func slotReservation() {
        let state = backloggedState()
        let plan = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        #expect(plan.reviews.count == 25)
        #expect(plan.unlockedPhrases.count + plan.newWords.count == 5)
        #expect(plan.newWords == ["w41", "w42", "w43", "w44", "w45"])
    }

    @Test("reviews are oldest due first; ties broken by card id")
    func reviewOrdering() {
        let state = backloggedState()
        let plan = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        // w40 has the oldest due (now − 40h), then w39, …
        #expect(plan.reviews.first == "w40")
        #expect(plan.reviews == (0..<25).map { String(format: "w%02d", 40 - $0) })

        var tied = Box.state(cards: [Box.word(1), Box.word(2)])
        let due = now.addingTimeInterval(-3_600)
        Box.inject(&tied, Box.sched("w02", due: due, lastReview: now.addingTimeInterval(-86_400)))
        Box.inject(&tied, Box.sched("w01", due: due, lastReview: now.addingTimeInterval(-86_400)))
        #expect(BoxEngine.dueNow(state: tied, now: now) == ["w01", "w02"])
    }

    @Test("no reservation without new work: reviews fill the whole sessionCap")
    func noReservationWithoutBudget() {
        // maxLearning 0 → no growth budget and no enqueued cards → no reserve.
        var state = Box.state(cards: (1...50).map { Box.word($0) }, Box.config(maxLearning: 0))
        for n in 1...40 {
            let id = String(format: "w%02d", n)
            Box.inject(&state, Box.sched(id, due: now.addingTimeInterval(-Double(n) * 3_600),
                                         lastReview: now.addingTimeInterval(-10 * 86_400)))
        }
        let plan = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        #expect(plan.reviews.count == 30)
        #expect(plan.newWords.isEmpty)
        #expect(plan.unlockedPhrases.isEmpty)
    }

    @Test("new cards never exceed remaining session capacity")
    func newCappedBySessionCapacity() {
        var state = Box.state(cards: (1...40).map { Box.word($0) },
                              Box.config(maxLearning: 20, sessionCap: 30))
        for n in 1...28 {
            let id = String(format: "w%02d", n)
            Box.inject(&state, Box.sched(id, due: now.addingTimeInterval(-Double(n) * 60),
                                         lastReview: now.addingTimeInterval(-5 * 86_400)))
        }
        let plan = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        // reviewCap = 30 − min(20, 5) = 25 → 25 reviews; 30 − 25 = 5 slots for new
        #expect(plan.reviews.count == 25)
        #expect(plan.newWords.count == 5)
    }

    @Test("determinism: same state + now -> identical plans, regardless of input order")
    func determinism() {
        let cards = (1...30).map { Box.word($0, area: $0 % 2 == 0 ? "b" : "a") }
        let state = Box.state(cards: cards)
        let shuffledState = Box.state(cards: cards.shuffled())

        let plan1 = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        let plan2 = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        let plan3 = BoxEngine.composeSession(state: shuffledState, now: now, calendar: cal)
        #expect(plan1 == plan2)
        #expect(plan1 == plan3)
    }

    @Test("drain loop scenario: failed cards cycle back until nothing is due")
    func drainLoop() {
        var state = Box.state(cards: [Box.word(1), Box.word(2)], Box.config(maxLearning: 2))
        var t = now

        // Introduce both; w01 fails, w02 passes
        state = BoxEngine.answer(state: state, cardID: "w01", rating: .again, now: t, calendar: cal)
        state = BoxEngine.answer(state: state, cardID: "w02", rating: .good, now: t, calendar: cal)

        // 1 min later only w01's again-step is due; failing again re-queues it
        t = t.addingTimeInterval(60)
        #expect(BoxEngine.dueNow(state: state, now: t) == ["w01"])
        state = BoxEngine.answer(state: state, cardID: "w01", rating: .again, now: t, calendar: cal)

        // its next 1-min step comes back at t+120
        t = now.addingTimeInterval(120)
        #expect(BoxEngine.dueNow(state: state, now: t) == ["w01"])
        state = BoxEngine.answer(state: state, cardID: "w01", rating: .good, now: t, calendar: cal)
        // good on learning graduates w01 to review — out of the drain window

        // 10 min after intro w02's step is due
        t = now.addingTimeInterval(600)
        #expect(BoxEngine.dueNow(state: state, now: t) == ["w02"])
        state = BoxEngine.answer(state: state, cardID: "w02", rating: .good, now: t, calendar: cal)

        // nothing due -> session over
        #expect(BoxEngine.dueNow(state: state, now: now.addingTimeInterval(660)).isEmpty)
    }

    @Test("isEmpty reflects an all-empty plan")
    func isEmptyFlag() {
        let empty = BoxEngine.composeSession(state: Box.state(cards: []), now: now, calendar: cal)
        #expect(empty.isEmpty)
    }
}
