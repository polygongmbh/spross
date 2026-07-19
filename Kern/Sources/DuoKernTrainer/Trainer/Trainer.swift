/// Procedural slot trainers (numbers, years, clock times) for German,
/// Swahili and Ukrainian. Pure generators — Kern never self-randomizes;
/// sampling takes an injected RNG. Ported from the prototype
/// `NumbersTrainer.tsx` / `ClockTrainer.tsx` (Ukrainian is new).
public enum TrainerLanguage: String, Codable, Sendable, CaseIterable { case german, swahili, ukrainian }

public enum TrainerKind: String, Codable, Sendable, CaseIterable { case numbers, years, clock }

public struct TrainerTask: Sendable, Equatable {
    public var kind: TrainerKind
    public var language: TrainerLanguage
    public var prompt: String        // what the UI shows: "347", "1978", "14:35"
    public var accepted: [String]    // all accepted answers (normalize-insensitively compared by UI)
    public var display: String       // canonical answer for reveal
    public var gloss: String?        // e.g. saa-system explanation or regional note
}

public enum Trainer {

    public static func number(_ n: Int, language: TrainerLanguage) -> TrainerTask {
        let accepted: [String]
        switch language {
        case .german: accepted = [GermanNumbers.cardinal(n)]
        case .swahili: accepted = [SwahiliNumbers.cardinal(n)]
        case .ukrainian: accepted = UkrainianNumbers.variants(n)
        }
        return TrainerTask(kind: .numbers, language: language, prompt: String(n),
                           accepted: accepted, display: accepted[0], gloss: nil)
    }

    /// german: hundred-style variants; swahili/ukrainian: plain number reading.
    public static func year(_ y: Int, language: TrainerLanguage) -> TrainerTask {
        let display: String
        let accepted: [String]
        switch language {
        case .german:
            display = GermanNumbers.year(y)
            accepted = GermanNumbers.yearVariants(y)
        case .swahili:
            display = SwahiliNumbers.cardinal(y)
            accepted = [display]
        case .ukrainian:
            let variants = UkrainianNumbers.variants(y)
            display = variants[0]
            accepted = variants
        }
        return TrainerTask(kind: .years, language: language, prompt: String(y),
                           accepted: accepted, display: display, gloss: nil)
    }

    /// `hour`/`minute` are normalized into range; minute is taken exactly
    /// (any 0...59 — the language clocks spell or read out non-round minutes).
    public static func clock(hour: Int, minute: Int, language: TrainerLanguage) -> TrainerTask {
        let h = ((hour % 24) + 24) % 24
        let m = ((minute % 60) + 60) % 60
        let prompt = String(format: "%02d:%02d", h, m)

        let display: String
        let accepted: [String]
        let gloss: String?
        switch language {
        case .german:
            (display, accepted, gloss) = GermanClock.task(hours: h, minutes: m)
        case .swahili:
            display = SwahiliClock.time(hours: h, minutes: m)
            accepted = SwahiliClock.accepted(hours: h, minutes: m)
            gloss = SwahiliClock.gloss
        case .ukrainian:
            (display, accepted, gloss) = UkrainianClock.task(hours: h, minutes: m)
        }
        return TrainerTask(kind: .clock, language: language, prompt: prompt,
                           accepted: accepted, display: display, gloss: gloss)
    }

    /// Deterministic sampling with an injected RNG.
    /// Biases ported from the prototype: numbers favor 2–3 digits
    /// (mid-session band of its progressive difficulty), years cluster
    /// around 1950–2050 with rarer historic outliers, clock uses any
    /// hour and any minute.
    public static func sample(kind: TrainerKind, language: TrainerLanguage,
                              using rng: inout some RandomNumberGenerator) -> TrainerTask {
        switch kind {
        case .numbers:
            let r = Double.random(in: 0..<1, using: &rng)
            let n: Int
            if r < 0.35 { n = Int.random(in: 10...99, using: &rng) }
            else if r < 0.75 { n = Int.random(in: 100...999, using: &rng) }
            else { n = Int.random(in: 1000...9999, using: &rng) }
            return number(n, language: language)
        case .years:
            let r = Double.random(in: 0..<1, using: &rng)
            let y: Int
            if r < 0.55 { y = Int.random(in: 1950...2050, using: &rng) }
            else if r < 0.85 { y = Int.random(in: 1700...2200, using: &rng) }
            else { y = Int.random(in: 1000...2199, using: &rng) }
            return year(y, language: language)
        case .clock:
            let h = Int.random(in: 0...23, using: &rng)
            let m = Int.random(in: 0...59, using: &rng)
            return clock(hour: h, minute: m, language: language)
        }
    }
}
