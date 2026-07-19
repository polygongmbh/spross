import Foundation
import Testing
import DuoKern

@Suite("Exposure card ranking")
struct ExposureTests {
    let now = Box.day1

    /// Full tier order: relearning → new/queued → learning → weakest review →
    /// upcoming seed-order new.
    @Test("tiers: relearning, queued-new, learning, weak review, upcoming")
    func tierOrder() {
        var state = Box.state(cards: (1...6).map { Box.word($0) })
        Box.inject(&state, Box.sched("w04", phase: .relearning, stability: 5, due: now, lastReview: now))
        Box.inject(&state, Box.sched("w03", phase: .learning, stability: 5, due: now, lastReview: now))
        Box.inject(&state, Box.sched("w01", phase: .review, stability: 2, due: now, lastReview: now))
        Box.inject(&state, Box.sched("w02", phase: .review, stability: 20, due: now, lastReview: now))
        state.enqueued = ["w05"]                 // queued new; w06 stays unscheduled/unqueued

        let ids = BoxEngine.exposureCards(state: state, now: now, limit: 10).map(\.id)
        #expect(ids == ["w04", "w05", "w03", "w01", "w02", "w06"])
    }

    @Test("new (enqueued) and relearning appear even with no review cards")
    func newAndRelearningIncluded() {
        var state = Box.state(cards: (1...3).map { Box.word($0) })
        Box.inject(&state, Box.sched("w01", phase: .relearning, stability: 3, due: now, lastReview: now))
        state.enqueued = ["w02"]

        let ids = BoxEngine.exposureCards(state: state, now: now, limit: 10).map(\.id)
        #expect(ids.prefix(2) == ["w01", "w02"])   // relearning first, then the queued-new card
        #expect(ids.contains("w03"))               // unscheduled card still previews as upcoming
    }

    @Test("suspended cards are excluded")
    func suspendedExcluded() {
        var state = Box.state(cards: [Box.word(1), Box.word(2)])
        Box.inject(&state, Box.sched("w01", phase: .review, stability: 4, due: now, lastReview: now, suspended: true))
        Box.inject(&state, Box.sched("w02", phase: .review, stability: 4, due: now, lastReview: now))

        let ids = BoxEngine.exposureCards(state: state, now: now, limit: 10).map(\.id)
        #expect(!ids.contains("w01"))
        #expect(ids.contains("w02"))
    }

    @Test("limit caps the result")
    func limitCaps() {
        let state = Box.state(cards: (1...10).map { Box.word($0) })
        #expect(BoxEngine.exposureCards(state: state, now: now, limit: 3).count == 3)
    }
}
