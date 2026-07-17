import Testing
@testable import DuoKern
@testable import DuoKernTrainer

@Suite struct TrainerLevelTests {
    private struct SplitMix: RandomNumberGenerator {
        var state: UInt64
        mutating func next() -> UInt64 {
            state &+= 0x9E3779B97F4A7C15
            var z = state
            z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
            z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
            return z ^ (z >> 31)
        }
    }

    @Test func numberLevelIsDigitCount() {
        var rng = SplitMix(state: 1)
        for level in 1...10 {
            for _ in 0..<50 {
                let task = Trainer.sample(kind: .numbers, language: .german, level: level, using: &rng)
                #expect(task.prompt.count == level, Comment(rawValue: "level \(level): \(task.prompt)"))
            }
        }
    }

    @Test func numberSamplingBiasesZeros() {
        var rng = SplitMix(state: 11)
        var zeros = 0, total = 0
        for _ in 0..<400 {
            let prompt = Trainer.sample(kind: .numbers, language: .german, level: 5, using: &rng).prompt
            for d in prompt.dropFirst() { total += 1; if d == "0" { zeros += 1 } }
        }
        // ~40% expected; assert clearly above the 10% a uniform draw would give.
        #expect(Double(zeros) / Double(total) > 0.25)
    }

    @Test func swahiliDrillAcceptsNaLessForm() {
        var rng = SplitMix(state: 7)
        var sawConnector = false
        for _ in 0..<300 {
            let task = Trainer.sample(kind: .numbers, language: .swahili, level: 3, using: &rng)
            if task.accepted.count == 2 {
                sawConnector = true
                #expect(task.accepted[1] == task.accepted[0].replacingOccurrences(of: " na ", with: " "))
            }
            #expect(task.accepted.contains(task.display))
        }
        #expect(sawConnector, "expected some multi-part Swahili numbers with a na-less variant")
    }

    @Test func clockLevelsRestrictMinutes() {
        var rng = SplitMix(state: 2)
        for _ in 0..<80 {
            let l1 = Trainer.sample(kind: .clock, language: .german, level: 1, using: &rng)
            #expect(l1.prompt.hasSuffix(":00"))
            let l3 = Trainer.sample(kind: .clock, language: .swahili, level: 3, using: &rng)
            let minute = Int(l3.prompt.suffix(2))!
            #expect(minute <= 30)
        }
    }

    @Test func yearLevelsWidenRange() {
        var rng = SplitMix(state: 3)
        for _ in 0..<80 {
            let l1 = Int(Trainer.sample(kind: .years, language: .german, level: 1, using: &rng).prompt)!
            #expect((1990...2029).contains(l1))
            let l3 = Int(Trainer.sample(kind: .years, language: .german, level: 3, using: &rng).prompt)!
            #expect((1100...2099).contains(l3))
        }
    }

    @Test func levelClampsToValidBounds() {
        var rng = SplitMix(state: 4)
        let low = Trainer.sample(kind: .numbers, language: .german, level: -3, using: &rng)
        #expect(low.prompt.count == 1)
        let high = Trainer.sample(kind: .numbers, language: .german, level: 99, using: &rng)
        #expect(high.prompt.count == 10)
    }
}
