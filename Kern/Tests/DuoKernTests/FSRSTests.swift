import Foundation
import Testing
@testable import DuoKern

/// Golden vectors from the FSRS-5 reference implementation ts-fsrs v4.7.1:
/// https://github.com/open-spaced-repetition/ts-fsrs/blob/v4.7.1/__tests__/FSRSV5.test.ts
/// (default FSRS-5 weights, request_retention 0.9, enable_short_term = true).
struct FSRSGoldenTests {
    let fsrs = FSRS()

    // ts-fsrs "first repeat": stability/difficulty after the first rating.
    @Test func initialStabilityMatchesReference() {
        let expected: [Rating: Double] = [
            .again: 0.40255, .hard: 1.18385, .good: 3.173, .easy: 15.69105,
        ]
        for (rating, stability) in expected {
            #expect(abs(fsrs.initialState(rating: rating).stability - stability) < 1e-9)
        }
    }

    @Test func initialDifficultyMatchesReference() {
        let expected: [Rating: Double] = [
            .again: 7.1949, .hard: 6.48830527, .good: 5.28243442, .easy: 3.22450159,
        ]
        for (rating, difficulty) in expected {
            #expect(abs(fsrs.initialState(rating: rating).difficulty - difficulty) < 1e-6)
        }
    }

    // ts-fsrs "first repeat": Easy on a new card schedules 16 days.
    @Test func firstEasyIntervalMatchesReference() {
        let state = fsrs.initialState(rating: .easy)
        #expect(fsrs.nextIntervalDays(stability: state.stability) == 16)
    }

    // ts-fsrs "get retrievability": R = 0.89832125 for the Easy card at its due date.
    @Test func retrievabilityAtDueMatchesReference() {
        let state = fsrs.initialState(rating: .easy)
        let r = fsrs.retrievability(state: state, elapsedDays: 16)
        #expect(abs(r - 0.89832125) < 1e-6)
    }

    // ts-fsrs "ivl_history": reviews always happen exactly at the due date;
    // learning/relearning steps are due within minutes, so elapsedDays = 0 there.
    @Test func intervalHistoryMatchesReference() {
        let ratings: [Rating] = [
            .good, .good, .good, .good, .good, .good,
            .again, .again,
            .good, .good, .good, .good, .good,
        ]
        let expected: [Double] = [0, 4, 14, 44, 125, 328, 0, 0, 7, 16, 34, 71, 142]

        var phase = CardPhase.new
        var state = MemoryState(stability: 0, difficulty: 0)
        var elapsed = 0.0
        var history: [Double] = []
        for rating in ratings {
            state = phase == .new
                ? fsrs.initialState(rating: rating)
                : fsrs.nextState(state: state, elapsedDays: elapsed, rating: rating)
            phase = fsrs.nextPhase(current: phase, rating: rating)
            if phase == .review {
                let interval = fsrs.nextIntervalDays(stability: state.stability)
                history.append(interval)
                elapsed = interval
            } else {
                history.append(0)
                elapsed = 0
            }
        }
        #expect(history == expected)
    }

    // ts-fsrs "memory state": ratings [again, good, good, good, good, good, good]
    // at elapsed days [new, 0, 0, 1, 3, 8, 21]. The reference basic scheduler applies
    // the long-term formulas for review-phase cards even at elapsed 0 (retrievability 1),
    // so this vector drives the core formulas directly.
    @Test func memoryStateMatchesReference() {
        var state = fsrs.initialState(rating: .again) // learning
        // Learning-phase same-day review -> short-term path.
        state = MemoryState(
            stability: fsrs.shortTermStability(stability: state.stability, rating: .good),
            difficulty: fsrs.nextDifficulty(state.difficulty, rating: .good)
        ) // -> review
        for elapsed in [0.0, 1, 3, 8, 21] {
            let r = fsrs.forgettingCurve(elapsedDays: elapsed, stability: state.stability)
            state = MemoryState(
                stability: fsrs.recallStability(
                    difficulty: state.difficulty, stability: state.stability,
                    retrievability: r, rating: .good
                ),
                difficulty: fsrs.nextDifficulty(state.difficulty, rating: .good)
            )
        }
        #expect(abs(state.stability - 48.4848) < 1e-4)
        #expect(abs(state.difficulty - 7.0866) < 1e-4)
    }
}

struct FSRSPhaseTests {
    let fsrs = FSRS()

    @Test func phaseTransitionTable() {
        let cases: [(CardPhase, Rating, CardPhase)] = [
            (.new, .again, .learning), (.new, .hard, .learning),
            (.new, .good, .learning), (.new, .easy, .review),
            (.learning, .again, .learning), (.learning, .hard, .learning),
            (.learning, .good, .review), (.learning, .easy, .review),
            (.review, .again, .relearning), (.review, .hard, .review),
            (.review, .good, .review), (.review, .easy, .review),
            (.relearning, .again, .relearning), (.relearning, .hard, .relearning),
            (.relearning, .good, .review), (.relearning, .easy, .review),
        ]
        for (current, rating, expected) in cases {
            #expect(fsrs.nextPhase(current: current, rating: rating) == expected,
                    "\(current) + \(rating) should be \(expected)")
        }
    }
}

struct FSRSParametersTests {
    @Test func defaultsMatchSpec() {
        let params = FSRSParameters.default
        #expect(params.w.count == 19)
        #expect(params.desiredRetention == 0.9)
        #expect(params.maximumInterval == 365)
    }

    @Test func codableRoundTrip() throws {
        let params = FSRSParameters(desiredRetention: 0.85, maximumInterval: 100)
        let data = try JSONEncoder().encode(params)
        let decoded = try JSONDecoder().decode(FSRSParameters.self, from: data)
        #expect(decoded == params)
    }
}
