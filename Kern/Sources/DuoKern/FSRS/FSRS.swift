import Foundation

/// FSRS-5 scheduler math. Pure functions over `MemoryState`; no clocks or dates —
/// callers pass elapsed time in (possibly fractional) days.
///
/// Formulas follow the reference implementation ts-fsrs v4.7.1
/// (`src/fsrs/algorithm.ts`, "FSRS-5.0") with short-term (same-day) reviews enabled.
public struct FSRS: Sendable {
    public let parameters: FSRSParameters

    /// Power-law forgetting-curve decay. Fixed in FSRS-5 (trainable only in FSRS-6).
    static let decay: Double = -0.5
    /// factor = 0.9^(1/decay) − 1, so R(t = S) = 0.9 exactly.
    static let factor: Double = 19.0 / 81.0
    static let minStability: Double = 0.01
    static let maxStability: Double = 36500.0
    /// Initial stability lower bound (reference clamps S0 to ≥ 0.1).
    static let minInitialStability: Double = 0.1

    public init(parameters: FSRSParameters = .default) {
        self.parameters = parameters
    }

    // MARK: - Public API

    /// Memory state after the very first review of a card.
    public func initialState(rating: Rating) -> MemoryState {
        MemoryState(
            stability: initialStability(rating),
            difficulty: initialDifficulty(rating)
        )
    }

    /// Memory state after a subsequent review.
    ///
    /// `elapsedDays < 1` takes the short-term (same-day) path:
    /// stability changes by `e^{w17·(G−3+w18)}` and the forgetting curve is not applied,
    /// matching the reference's same-day review handling (t == 0 in whole-day units).
    public func nextState(state: MemoryState, elapsedDays: Double, rating: Rating) -> MemoryState {
        let s = max(state.stability, Self.minStability)
        let d = clampDifficulty(state.difficulty)
        let t = max(elapsedDays, 0)

        let newStability: Double
        if t < 1 {
            newStability = shortTermStability(stability: s, rating: rating)
        } else if rating == .again {
            let r = forgettingCurve(elapsedDays: t, stability: s)
            // why: cap post-lapse stability so a lapse can never leave the card
            // stronger than the same-day-again outcome would (reference behavior).
            let sameDayFloor = s / exp(parameters.w[17] * parameters.w[18])
            newStability = min(
                max(sameDayFloor, Self.minStability),
                forgetStability(difficulty: d, stability: s, retrievability: r)
            )
        } else {
            let r = forgettingCurve(elapsedDays: t, stability: s)
            newStability = recallStability(difficulty: d, stability: s, retrievability: r, rating: rating)
        }
        return MemoryState(
            stability: newStability,
            difficulty: nextDifficulty(d, rating: rating)
        )
    }

    /// Probability of recall after `elapsedDays` given the card's stability. In (0, 1].
    public func retrievability(state: MemoryState, elapsedDays: Double) -> Double {
        forgettingCurve(
            elapsedDays: max(elapsedDays, 0),
            stability: max(state.stability, Self.minStability)
        )
    }

    /// Next review interval in whole days (reference rounds half-up),
    /// clamped to `1...maximumInterval`.
    public func nextIntervalDays(stability: Double, desiredRetention: Double? = nil) -> Double {
        let retention = desiredRetention ?? parameters.desiredRetention
        precondition(retention > 0 && retention <= 1, "desiredRetention must be in (0, 1]")
        // I(r, s) = s · (r^{1/decay} − 1) / factor; equals s when r = 0.9.
        let modifier = (pow(retention, 1 / Self.decay) - 1) / Self.factor
        let interval = (stability * modifier).rounded(.toNearestOrAwayFromZero)
        return min(max(interval, 1), parameters.maximumInterval)
    }

    /// Card phase transition on review (reference basic-scheduler state machine).
    public func nextPhase(current: CardPhase, rating: Rating) -> CardPhase {
        switch current {
        case .new:
            return rating == .easy ? .review : .learning
        case .learning, .relearning:
            return (rating == .good || rating == .easy) ? .review : current
        case .review:
            return rating == .again ? .relearning : .review
        }
    }

    // MARK: - FSRS-5 core formulas (internal for golden-vector tests)

    /// R(t, S) = (1 + factor · t / S)^decay
    func forgettingCurve(elapsedDays: Double, stability: Double) -> Double {
        pow(1 + Self.factor * elapsedDays / stability, Self.decay)
    }

    /// S0(G) = w_{G−1}, clamped to ≥ 0.1.
    func initialStability(_ rating: Rating) -> Double {
        max(parameters.w[rating.rawValue - 1], Self.minInitialStability)
    }

    /// D0(G) = w4 − e^{(G−1)·w5} + 1, unclamped (mean reversion uses the raw value).
    func rawInitialDifficulty(_ rating: Rating) -> Double {
        parameters.w[4] - exp(Double(rating.rawValue - 1) * parameters.w[5]) + 1
    }

    func initialDifficulty(_ rating: Rating) -> Double {
        clampDifficulty(rawInitialDifficulty(rating))
    }

    /// Linear damping ΔD·(10−D)/9, then mean reversion toward D0(easy):
    /// D′ = w7·D0(4) + (1−w7)·(D + ΔD·(10−D)/9) with ΔD = −w6·(G−3).
    func nextDifficulty(_ difficulty: Double, rating: Rating) -> Double {
        let deltaD = -parameters.w[6] * Double(rating.rawValue - 3)
        let damped = difficulty + deltaD * (10 - difficulty) / 9
        let reverted = parameters.w[7] * rawInitialDifficulty(.easy)
            + (1 - parameters.w[7]) * damped
        return clampDifficulty(reverted)
    }

    /// S′_r = S·(1 + e^{w8}·(11−D)·S^{−w9}·(e^{w10·(1−R)}−1)·hardPenalty·easyBonus)
    func recallStability(
        difficulty: Double, stability: Double, retrievability: Double, rating: Rating
    ) -> Double {
        let hardPenalty = rating == .hard ? parameters.w[15] : 1
        let easyBonus = rating == .easy ? parameters.w[16] : 1
        let growth = exp(parameters.w[8])
            * (11 - difficulty)
            * pow(stability, -parameters.w[9])
            * (exp((1 - retrievability) * parameters.w[10]) - 1)
            * hardPenalty
            * easyBonus
        return clampStability(stability * (1 + growth))
    }

    /// S′_f = w11·D^{−w12}·((S+1)^{w13}−1)·e^{w14·(1−R)}
    func forgetStability(difficulty: Double, stability: Double, retrievability: Double) -> Double {
        clampStability(
            parameters.w[11]
                * pow(difficulty, -parameters.w[12])
                * (pow(stability + 1, parameters.w[13]) - 1)
                * exp((1 - retrievability) * parameters.w[14])
        )
    }

    /// Same-day review: S′_s = S·e^{w17·(G−3+w18)}
    func shortTermStability(stability: Double, rating: Rating) -> Double {
        clampStability(
            stability * exp(parameters.w[17] * (Double(rating.rawValue) - 3 + parameters.w[18]))
        )
    }

    private func clampDifficulty(_ difficulty: Double) -> Double {
        min(max(difficulty, 1), 10)
    }

    private func clampStability(_ stability: Double) -> Double {
        min(max(stability, Self.minStability), Self.maxStability)
    }
}
