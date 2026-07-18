import Foundation
import Testing
import DuoKern

@Suite("Statistics, streak, direction scoping, session end")
struct BoxStatisticsTests {
    let cal = Box.calendar
    let now = Box.day1

    private func statsState(_ reviewDays: [Int]) -> BoxState {
        var state = Box.state(cards: [Box.word(1)])
        for day in reviewDays {
            state.dailyStats[Box.dayKey(Box.date(2026, 7, day))] = DayStats(reviews: 5, introduced: 0, activeCount: 1)
        }
        return state
    }

    @Test("streak: reviews d1, d2, gap d3, reviews d4 -> streak 3 (one-day forgiveness)")
    func streakSingleGapForgiven() {
        let state = statsState([1, 2, 4])
        let stats = BoxEngine.statistics(state: state, now: Box.date(2026, 7, 4), calendar: cal)
        #expect(stats.streak == 3)
    }

    @Test("streak: two-day gap breaks -> streak 1")
    func streakTwoDayGapBreaks() {
        let state = statsState([1, 2, 5]) // gap d3 + d4
        let stats = BoxEngine.statistics(state: state, now: Box.date(2026, 7, 5), calendar: cal)
        #expect(stats.streak == 1)
    }

    @Test("streak: today without reviews yet neither breaks nor consumes forgiveness")
    func streakTodayInProgress() {
        let state = statsState([2, 3, 4])
        let stats = BoxEngine.statistics(state: state, now: Box.date(2026, 7, 5), calendar: cal)
        #expect(stats.streak == 3)
        #expect(BoxEngine.statistics(state: statsState([]), now: now, calendar: cal).streak == 0)
    }

    @Test("direction scoping: forward scheduling is invisible to reverse statistics")
    func directionScoping() {
        var state = Box.state(cards: (1...3).map { Box.word($0) })
        for n in 1...3 {
            state = BoxEngine.answer(state: state, cardID: String(format: "w%02d", n),
                                     rating: .easy, now: now, calendar: cal)
        }
        let forward = BoxEngine.statistics(state: state, now: now, calendar: cal)
        #expect(forward.activeCount == 3)

        var reversed = state
        reversed.config.direction = .targetToDe
        let reverse = BoxEngine.statistics(state: reversed, now: now, calendar: cal)
        #expect(reverse.activeCount == 0)
        #expect(reverse.averageRetrievability == nil)
        // …and the same cards re-enter the reverse direction as new learning.
        // Composed next day: the daily budget is shared across directions,
        // and today's 3 forward introductions already consumed part of it.
        let nextDay = Box.date(2026, 7, 2)
        let plan = BoxEngine.composeSession(state: reversed, now: nextDay, calendar: cal)
        #expect(plan.newWords == ["w01", "w02", "w03"])
    }

    @Test("direction scoping: answering in one direction leaves the other untouched")
    func answerScopedToDirection() {
        var state = Box.state(cards: [Box.word(1)])
        state = BoxEngine.answer(state: state, cardID: "w01", rating: .good, now: now, calendar: cal)
        var reversed = state
        reversed.config.direction = .targetToDe
        reversed = BoxEngine.answer(state: reversed, cardID: "w01", rating: .again,
                                    now: now, calendar: cal)
        let forwardKey = BoxState.schedulingKey(cardID: "w01", direction: .deToTarget)
        let reverseKey = BoxState.schedulingKey(cardID: "w01", direction: .targetToDe)
        #expect(reversed.scheduling[forwardKey] == state.scheduling[forwardKey])
        #expect(reversed.scheduling[reverseKey]?.log.count == 1)
    }

    @Test("endSession appends DayStats and prunes newIntroduced to trailing 60 days")
    func endSessionAppendsAndPrunes() {
        var state = Box.state(cards: (1...3).map { Box.word($0) })
        state = BoxEngine.answer(state: state, cardID: "w01", rating: .easy, now: now, calendar: cal)
        state.newIntroduced["2026-01-01"] = 4 // stale, > 60 days back
        state.newIntroduced["2026-06-30"] = 2 // yesterday, kept

        state = BoxEngine.endSession(state: state, reviewsDone: 7, now: now, calendar: cal)
        let today = Box.dayKey(now)
        #expect(state.dailyStats[today] == DayStats(reviews: 7, introduced: 1, activeCount: 1))
        #expect(state.newIntroduced["2026-01-01"] == nil)
        #expect(state.newIntroduced["2026-06-30"] == 2)
        #expect(state.newIntroduced[today] == 1)

        // second session same day accumulates reviews
        state = BoxEngine.endSession(state: state, reviewsDone: 3, now: now, calendar: cal)
        #expect(state.dailyStats[today]?.reviews == 10)
    }

    @Test("dueCount, budget, and averageRetrievability reflect the current direction and clock")
    func headlineNumbers() throws {
        var state = Box.state(cards: (1...4).map { Box.word($0) })
        Box.inject(&state, Box.sched("w01", due: now.addingTimeInterval(-60), lastReview: now.addingTimeInterval(-86_400)))
        Box.inject(&state, Box.sched("w02", due: now.addingTimeInterval(86_400), lastReview: now))
        Box.inject(&state, Box.sched("w03", due: now, lastReview: now, suspended: true))

        let stats = BoxEngine.statistics(state: state, now: now, calendar: cal)
        #expect(stats.activeCount == 2)
        #expect(stats.dueCount == 1)
        #expect(stats.suspendedCount == 1)
        #expect(stats.newSlotsAvailable == 8) // empty learning pool, default maxLearning
        let avg = try #require(stats.averageRetrievability)
        #expect(avg > 0 && avg <= 1)
    }

    @Test("area statistics: totals, sitting, locked/unlocked phrases")
    func areaBreakdown() {
        var state = Box.state(cards: [
            Box.word(1, area: "kitchen"), Box.word(2, area: "kitchen"),
            Box.phrase("p-locked", components: ["w01", "w02"], area: "kitchen"),
            Box.phrase("p-free", components: [], area: "kitchen"),
            Box.word(3, area: "market"),
        ])
        let future = now.addingTimeInterval(5 * 86_400)
        Box.inject(&state, Box.sched("w01", stability: 5, due: future, lastReview: now))
        Box.inject(&state, Box.sched("w02", phase: .learning, stability: 1, due: future, lastReview: now))

        let stats = BoxEngine.statistics(state: state, now: now, calendar: cal)
        #expect(stats.areas.map(\.name) == ["kitchen", "market"])
        let kitchen = stats.areas[0]
        #expect(kitchen.total == 4)
        #expect(kitchen.active == 2)
        #expect(kitchen.sitting == 1) // only w01: review phase & stability >= 3
        #expect(kitchen.phrasesLocked == 1) // p-locked: w02 not stable yet
        #expect(kitchen.phrasesUnlocked == 1) // p-free has no components
        #expect(stats.areas[1] == AreaStatistics(name: "market", total: 1, active: 0,
                                                 sitting: 0, phrasesLocked: 0, phrasesUnlocked: 0))
    }
}
