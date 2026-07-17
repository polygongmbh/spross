extension Trainer {

    /// Adaptive difficulty ceiling per kind. Levels are 1-based; the app
    /// ramps up after consecutive successes and steps down on a miss.
    public static func maxLevel(kind: TrainerKind) -> Int {
        switch kind {
        case .numbers: return 10  // level == digit count (up to billions)
        case .years: return 3
        case .clock: return 4
        }
    }

    /// Level semantics:
    /// - numbers: level = digit count (1 → 0–9 … 10 → 1000000000–9999999999).
    /// - years: 1 recent decades (1990–2029), 2 modern century (1900–2099),
    ///   3 full historic range (1100–2099, German hundred-style variants).
    /// - clock: 1 full hours, 2 quarters, 3 five-minute steps up to :30,
    ///   4 all five-minute steps (incl. the >30 to-the-hour forms).
    public static func sample(kind: TrainerKind, language: TrainerLanguage, level: Int,
                              using rng: inout some RandomNumberGenerator) -> TrainerTask {
        let l = max(1, min(level, maxLevel(kind: kind)))
        switch kind {
        case .numbers:
            let lower = l == 1 ? 0 : pow10(l - 1)
            let upper = pow10(l) - 1
            return number(draw(lower...upper, &rng), language: language)
        case .years:
            let range: ClosedRange<Int>
            switch l {
            case 1: range = 1990...2029
            case 2: range = 1900...2099
            default: range = 1100...2099
            }
            return year(draw(range, &rng), language: language)
        case .clock:
            let minutes: [Int]
            switch l {
            case 1: minutes = [0]
            case 2: minutes = [0, 15, 30, 45]
            case 3: minutes = [0, 5, 10, 15, 20, 25, 30]
            default: minutes = [0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55]
            }
            let hour = draw(0...23, &rng)
            return clock(hour: hour, minute: minutes[draw(0...(minutes.count - 1), &rng)],
                         language: language)
        }
    }

    private static func pow10(_ n: Int) -> Int {
        var result = 1
        for _ in 0..<n { result *= 10 }
        return result
    }

    private static func draw(_ range: ClosedRange<Int>, _ rng: inout some RandomNumberGenerator) -> Int {
        range.lowerBound + Int(rng.next() % UInt64(range.count))
    }
}
