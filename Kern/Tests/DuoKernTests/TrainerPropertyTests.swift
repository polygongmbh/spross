import Testing
@testable import DuoKern

/// Deterministic SplitMix64 (local copy; the one in FSRSPropertyTests is private).
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

struct TrainerPropertyTests {

    @Test func sampledTasksAreWellFormed() {
        var rng = SplitMix64(state: 0xBEEF)
        for kind in TrainerKind.allCases {
            for language in TrainerLanguage.allCases {
                for _ in 0..<200 {
                    let task = Trainer.sample(kind: kind, language: language, using: &rng)
                    #expect(!task.prompt.isEmpty)
                    #expect(!task.accepted.isEmpty)
                    #expect(task.accepted.allSatisfy { !$0.isEmpty })
                    #expect(task.accepted.contains(task.display),
                            "\(kind)/\(language): display must be accepted (\(task.display))")
                    #expect(task.kind == kind && task.language == language)
                }
            }
        }
    }

    @Test func directGeneratorsAreWellFormedAcrossRanges() {
        for language in TrainerLanguage.allCases {
            for n in 0...1200 {
                let task = Trainer.number(n, language: language)
                #expect(task.accepted.contains(task.display), "n=\(n) \(language)")
                #expect(Set(task.accepted).count == task.accepted.count, "n=\(n) \(language)")
            }
            for y in stride(from: 1000, through: 2200, by: 7) {
                let task = Trainer.year(y, language: language)
                #expect(task.accepted.contains(task.display), "y=\(y) \(language)")
            }
            for h in 0..<24 {
                for m in stride(from: 0, to: 60, by: 5) {
                    let task = Trainer.clock(hour: h, minute: m, language: language)
                    #expect(task.accepted.contains(task.display), "\(h):\(m) \(language)")
                }
            }
        }
    }

    @Test func samplingIsDeterministicForSeededGenerator() {
        for kind in TrainerKind.allCases {
            var a = SplitMix64(state: 0xD00D)
            var b = SplitMix64(state: 0xD00D)
            for _ in 0..<100 {
                let ta = Trainer.sample(kind: kind, language: .german, using: &a)
                let tb = Trainer.sample(kind: kind, language: .german, using: &b)
                #expect(ta == tb)
            }
        }
    }

    @Test func sampledValuesStayInPortedRanges() {
        var rng = SplitMix64(state: 42)
        for _ in 0..<500 {
            let numberTask = Trainer.sample(kind: .numbers, language: .german, using: &rng)
            let n = try! #require(Int(numberTask.prompt))
            #expect((10...9999).contains(n))
            let yearTask = Trainer.sample(kind: .years, language: .german, using: &rng)
            let y = try! #require(Int(yearTask.prompt))
            #expect((1000...2200).contains(y))
            let clockTask = Trainer.sample(kind: .clock, language: .german, using: &rng)
            #expect(clockTask.prompt.count == 5 && clockTask.prompt.contains(":"))
        }
    }

    @Test func clockMinutesRoundToNearestFive() {
        #expect(Trainer.clock(hour: 9, minute: 58, language: .german).prompt == "10:00")
        #expect(Trainer.clock(hour: 23, minute: 58, language: .german).prompt == "00:00")
        #expect(Trainer.clock(hour: 9, minute: 3, language: .german).prompt == "09:05")
        #expect(Trainer.clock(hour: 9, minute: 2, language: .german).prompt == "09:00")
        #expect(Trainer.clock(hour: 9, minute: 12, language: .german).prompt == "09:10")
    }
}
