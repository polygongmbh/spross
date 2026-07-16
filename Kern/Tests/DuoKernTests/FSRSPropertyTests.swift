import Testing
@testable import DuoKern

/// Deterministic SplitMix64 so property/fuzz tests are reproducible.
private struct SplitMix64: RandomNumberGenerator {
    var state: UInt64
    mutating func next() -> UInt64 {
        state &+= 0x9E3779B97F4A7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        return z ^ (z >> 31)
    }
}

struct FSRSPropertyTests {
    let fsrs = FSRS()

    @Test func retrievabilityIsInUnitIntervalAndDecreasing() {
        for stability in [0.1, 0.5, 3.173, 15.7, 100, 36500] {
            let state = MemoryState(stability: stability, difficulty: 5)
            var previous = fsrs.retrievability(state: state, elapsedDays: 0)
            #expect(previous == 1) // no time elapsed -> certain recall
            for elapsed in [0.25, 0.5, 1, 2, 5, 10, 50, 365, 10000] {
                let r = fsrs.retrievability(state: state, elapsedDays: elapsed)
                #expect(r > 0 && r <= 1)
                #expect(r < previous, "R must strictly decrease (S=\(stability), t=\(elapsed))")
                previous = r
            }
        }
    }

    @Test func retrievabilityAtStabilityIsDesiredNinety() {
        for stability in [0.5, 3.173, 42.0, 365.0] {
            let state = MemoryState(stability: stability, difficulty: 5)
            #expect(abs(fsrs.retrievability(state: state, elapsedDays: stability) - 0.9) < 1e-12)
        }
    }

    @Test func goodAndEasyIncreaseStabilityOnReviewCards() {
        for difficulty in stride(from: 1.0, through: 10.0, by: 1.5) {
            for stability in [0.5, 3.0, 20.0, 200.0] {
                for elapsed in [1.0, stability, stability * 3] {
                    let state = MemoryState(stability: stability, difficulty: difficulty)
                    for rating in [Rating.good, .easy] {
                        let next = fsrs.nextState(state: state, elapsedDays: elapsed, rating: rating)
                        #expect(next.stability > stability,
                                "\(rating) must grow stability (D=\(difficulty), S=\(stability), t=\(elapsed))")
                    }
                }
            }
        }
    }

    @Test func againReducesStability() {
        for stability in [0.5, 3.0, 20.0, 200.0, 5000.0] {
            let state = MemoryState(stability: stability, difficulty: 6)
            // Long-term lapse and same-day again must both shrink stability.
            for elapsed in [0.0, 1.0, stability, stability * 2] {
                let next = fsrs.nextState(state: state, elapsedDays: elapsed, rating: .again)
                #expect(next.stability < stability, "again must reduce stability (S=\(stability), t=\(elapsed))")
                #expect(next.stability > 0)
            }
        }
    }

    @Test func difficultyStaysInBoundsUnderRandomReviews() {
        var rng = SplitMix64(state: 0xD1F5)
        for _ in 0..<50 {
            let first = Rating.allCases.randomElement(using: &rng)!
            var state = fsrs.initialState(rating: first)
            #expect((1...10).contains(state.difficulty))
            for _ in 0..<100 {
                let rating = Rating.allCases.randomElement(using: &rng)!
                let elapsed = Double.random(in: 0...400, using: &rng)
                state = fsrs.nextState(state: state, elapsedDays: elapsed, rating: rating)
                #expect(state.difficulty >= 1 && state.difficulty <= 10)
                #expect(state.stability > 0)
            }
        }
    }

    @Test func againLowersRepeatedGoodRaisesDifficultyMonotonically() {
        let state = MemoryState(stability: 10, difficulty: 5)
        let afterAgain = fsrs.nextState(state: state, elapsedDays: 10, rating: .again)
        let afterEasy = fsrs.nextState(state: state, elapsedDays: 10, rating: .easy)
        #expect(afterAgain.difficulty > state.difficulty) // failing makes a card harder
        #expect(afterEasy.difficulty < state.difficulty) // easy makes it easier
    }

    @Test func nextIntervalFuzzStaysWithinBounds() {
        var rng = SplitMix64(state: 0xF5A5)
        for _ in 0..<2000 {
            let stability = Double.random(in: 0.0001...1e6, using: &rng)
            let retention = Double.random(in: 0.001...1.0, using: &rng)
            let interval = fsrs.nextIntervalDays(stability: stability, desiredRetention: retention)
            #expect(interval >= 1)
            #expect(interval <= fsrs.parameters.maximumInterval)
            #expect(interval == interval.rounded(), "intervals are whole days")
        }
    }

    @Test func nextIntervalHonorsCustomMaximum() {
        let fsrs = FSRS(parameters: FSRSParameters(maximumInterval: 30))
        #expect(fsrs.nextIntervalDays(stability: 1000) == 30)
        #expect(fsrs.nextIntervalDays(stability: 0.001) == 1)
    }

    @Test func intervalEqualsStabilityAtDefaultRetention() {
        // With desiredRetention = 0.9 the interval modifier is exactly 1.
        for stability in [1.0, 7.0, 42.0, 128.0] {
            #expect(fsrs.nextIntervalDays(stability: stability) == stability.rounded())
        }
    }

    @Test func higherDesiredRetentionShortensIntervals() {
        let strict = fsrs.nextIntervalDays(stability: 100, desiredRetention: 0.97)
        let lax = fsrs.nextIntervalDays(stability: 100, desiredRetention: 0.8)
        #expect(strict < 100 && lax > 100)
    }

    @Test func hardPenaltyAndEasyBonusOrderStabilities() {
        let state = MemoryState(stability: 10, difficulty: 5)
        let byRating = [Rating.again, .hard, .good, .easy].map {
            fsrs.nextState(state: state, elapsedDays: 10, rating: $0).stability
        }
        #expect(byRating == byRating.sorted(), "stability must be monotone in rating")
        #expect(Set(byRating).count == 4)
    }
}
