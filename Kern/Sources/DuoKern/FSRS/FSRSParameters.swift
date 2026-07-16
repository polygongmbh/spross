import Foundation

/// FSRS-5 model parameters: the 19 trained weights plus scheduling knobs.
///
/// Weight semantics and defaults follow the reference implementation
/// ts-fsrs v4.7.1 (`src/fsrs/default.ts`, FSRS-5.0), which matches the
/// fsrs4anki wiki "The Algorithm" FSRS-5 definition:
/// - w0–w3: initial stability per first rating (again/hard/good/easy)
/// - w4, w5: initial difficulty D0(G) = w4 − e^{(G−1)·w5} + 1
/// - w6, w7: difficulty update (linear damping) and mean reversion toward D0(easy)
/// - w8–w10: stability growth on successful recall
/// - w11–w14: post-lapse stability
/// - w15: hard penalty, w16: easy bonus
/// - w17, w18: same-day (short-term) stability
public struct FSRSParameters: Codable, Sendable, Equatable {
    /// The 19 FSRS-5 weights w0…w18.
    public var w: [Double]
    /// Target probability of recall at the moment a card comes due. In (0, 1].
    public var desiredRetention: Double
    /// Upper bound for scheduled intervals, in days.
    public var maximumInterval: Double

    /// Default FSRS-5 weights (ts-fsrs v4.7.1 `default_w`).
    public static let defaultWeights: [Double] = [
        0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046,
        1.54575, 0.1192, 1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315,
        2.9898, 0.51655, 0.6621,
    ]

    public static let `default` = FSRSParameters()

    public init(
        w: [Double] = Self.defaultWeights,
        desiredRetention: Double = 0.9,
        maximumInterval: Double = 365
    ) {
        precondition(w.count == 19, "FSRS-5 requires exactly 19 weights, got \(w.count)")
        precondition(desiredRetention > 0 && desiredRetention <= 1,
                     "desiredRetention must be in (0, 1]")
        precondition(maximumInterval >= 1, "maximumInterval must be >= 1 day")
        self.w = w
        self.desiredRetention = desiredRetention
        self.maximumInterval = maximumInterval
    }
}
