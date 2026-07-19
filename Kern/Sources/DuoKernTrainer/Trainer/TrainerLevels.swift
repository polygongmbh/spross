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
    ///   4 any minute (incl. the >30 to-the-hour forms).
    public static func sample(kind: TrainerKind, language: TrainerLanguage, level: Int,
                              using rng: inout some RandomNumberGenerator) -> TrainerTask {
        let l = max(1, min(level, maxLevel(kind: kind)))
        switch kind {
        case .numbers:
            var task = number(drawNumber(digits: l, &rng), language: language)
            // why: speakers routinely drop "na" in longer Swahili numbers,
            // so the drill accepts the connector-less spelling too.
            if language == .swahili {
                task.accepted = SwahiliNumbers.acceptedVariants(Int(task.prompt) ?? 0)
            }
            return task
        case .years:
            let range: ClosedRange<Int>
            switch l {
            case 1: range = 1990...2029
            case 2: range = 1900...2099
            default: range = 1100...2099
            }
            return year(draw(range, &rng), language: language)
        case .clock:
            let hour = draw(0...23, &rng)
            let minute: Int
            switch l {
            case 1: minute = 0
            case 2: minute = [0, 15, 30, 45][draw(0...3, &rng)]
            case 3: minute = draw(0...30, &rng)
            default: minute = draw(0...59, &rng)
            }
            return clock(hour: hour, minute: minute, language: language)
        }
    }

    /// Level-sized number with zeros biased to ~40% on the non-leading
    /// digits, so the drill favours rounder values (less tedious than
    /// typing arbitrary long numbers). The leading digit stays 1–9 so the
    /// value keeps exactly `digits` digits.
    private static func drawNumber(digits: Int, _ rng: inout some RandomNumberGenerator) -> Int {
        guard digits > 1 else { return draw(0...9, &rng) }
        var value = draw(1...9, &rng)
        for _ in 1..<digits {
            let d = rng.next() % 10 < 4 ? 0 : Int(1 + rng.next() % 9)
            value = value * 10 + d
        }
        return value
    }

    private static func draw(_ range: ClosedRange<Int>, _ rng: inout some RandomNumberGenerator) -> Int {
        range.lowerBound + Int(rng.next() % UInt64(range.count))
    }
}
