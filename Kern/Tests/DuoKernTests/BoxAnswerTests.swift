import Foundation
import Testing
import DuoKern

@Suite("Answering: FSRS scheduling, drain loop, leeches")
struct BoxAnswerTests {
    let cal = Box.calendar
    let now = Box.day1

    @Test("good on a new card schedules a 10-min step; dueNow surfaces it after 10 min, not before")
    func drainLoopTenMinuteStep() {
        var state = Box.state(cards: [Box.word(1)])
        state = BoxEngine.answer(state: state, cardID: "w01", rating: .good, now: now, calendar: cal)

        let sched = state.scheduling[BoxState.schedulingKey(cardID: "w01", direction: .deToTarget)]!
        #expect(sched.phase == .learning)
        #expect(sched.due == now.addingTimeInterval(600))

        #expect(BoxEngine.dueNow(state: state, now: now).isEmpty)
        #expect(BoxEngine.dueNow(state: state, now: now.addingTimeInterval(599)).isEmpty)
        #expect(BoxEngine.dueNow(state: state, now: now.addingTimeInterval(600)) == ["w01"])
    }

    @Test("again on a learning card schedules a 1-min step and stays in learning")
    func againOneMinuteStep() {
        var state = Box.state(cards: [Box.word(1)])
        state = BoxEngine.answer(state: state, cardID: "w01", rating: .again, now: now, calendar: cal)
        let sched = state.scheduling.values.first!
        #expect(sched.phase == .learning)
        #expect(sched.due == now.addingTimeInterval(60))
        #expect(sched.lapses == 0) // lapses only count for review-phase cards
    }

    @Test("good on a learning card graduates to review with a day-scale interval")
    func learningGraduatesToReview() {
        var state = Box.state(cards: [Box.word(1)])
        state = BoxEngine.answer(state: state, cardID: "w01", rating: .good, now: now, calendar: cal)
        let later = now.addingTimeInterval(600)
        state = BoxEngine.answer(state: state, cardID: "w01", rating: .good, now: later, calendar: cal)

        let sched = state.scheduling.values.first!
        #expect(sched.phase == .review)
        let due = sched.due!
        #expect(due >= later.addingTimeInterval(86_400)) // at least 1 day out
        #expect(sched.log.count == 2)
        #expect(sched.log.last!.elapsedDays > 0)
    }

    @Test("easy on a new card goes straight to review")
    func easyGraduatesImmediately() {
        var state = Box.state(cards: [Box.word(1)])
        state = BoxEngine.answer(state: state, cardID: "w01", rating: .easy, now: now, calendar: cal)
        let sched = state.scheduling.values.first!
        #expect(sched.phase == .review)
        #expect(sched.due! >= now.addingTimeInterval(86_400))
        #expect(sched.memory!.stability >= 3)
    }

    @Test("again on a review card lapses to relearning with a 1-min step")
    func reviewLapse() {
        var state = Box.state(cards: [Box.word(1)])
        Box.inject(&state, Box.sched("w01", due: now.addingTimeInterval(-3_600),
                                     lastReview: now.addingTimeInterval(-10 * 86_400)))
        state = BoxEngine.answer(state: state, cardID: "w01", rating: .again, now: now, calendar: cal)
        let sched = state.scheduling.values.first!
        #expect(sched.phase == .relearning)
        #expect(sched.lapses == 1)
        #expect(sched.suspended == false)
        #expect(sched.due == now.addingTimeInterval(60))
    }

    @Test("leech: 8th lapse auto-suspends; excluded from dueNow and activeCount")
    func leechAutoSuspend() {
        var state = Box.state(cards: [Box.word(1), Box.word(2)])
        Box.inject(&state, Box.sched("w01", due: now.addingTimeInterval(-3_600),
                                     lastReview: now.addingTimeInterval(-5 * 86_400), lapses: 7))
        Box.inject(&state, Box.sched("w02", due: now.addingTimeInterval(-3_600),
                                     lastReview: now.addingTimeInterval(-5 * 86_400)))

        state = BoxEngine.answer(state: state, cardID: "w01", rating: .again, now: now, calendar: cal)
        let sched = state.scheduling[BoxState.schedulingKey(cardID: "w01", direction: .deToTarget)]!
        #expect(sched.lapses == 8)
        #expect(sched.suspended)

        #expect(BoxEngine.dueNow(state: state, now: now) == ["w02"])
        let stats = BoxEngine.statistics(state: state, now: now, calendar: cal)
        #expect(stats.activeCount == 1)
        #expect(stats.suspendedCount == 1)
        #expect(BoxEngine.composeSession(state: state, now: now, calendar: cal).reviews == ["w02"])
    }

    @Test("elapsedDays comes from the last log entry, never from due")
    func elapsedFromLastLog() {
        var state = Box.state(cards: [Box.word(1)])
        let lastReview = now.addingTimeInterval(-2 * 86_400)
        // due far in the past on purpose — must not affect elapsed
        Box.inject(&state, Box.sched("w01", due: now.addingTimeInterval(-9 * 86_400),
                                     lastReview: lastReview))
        state = BoxEngine.answer(state: state, cardID: "w01", rating: .good, now: now, calendar: cal)
        let entry = state.scheduling.values.first!.log.last!
        #expect(abs(entry.elapsedDays - 2.0) < 0.001)
    }

    @Test("every answer appends a log entry, including same-day retries")
    func logAppendedAlways() {
        var state = Box.state(cards: [Box.word(1)])
        var t = now
        for rating in [Rating.good, .again, .again, .good] {
            state = BoxEngine.answer(state: state, cardID: "w01", rating: rating, now: t, calendar: cal)
            t = t.addingTimeInterval(120)
        }
        let sched = state.scheduling.values.first!
        #expect(sched.log.count == 4)
        #expect(sched.log.map(\.rating) == [.good, .again, .again, .good])
    }

    @Test("answering an unknown card id is a no-op")
    func unknownCardNoop() {
        let state = Box.state(cards: [Box.word(1)])
        let after = BoxEngine.answer(state: state, cardID: "nope", rating: .good, now: now, calendar: cal)
        #expect(after == state)
    }
}
