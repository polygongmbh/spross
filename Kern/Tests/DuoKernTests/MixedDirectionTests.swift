import Foundation
import Testing
@testable import DuoKern

@Suite struct MixedDirectionTests {
    let calendar = Calendar(identifier: .gregorian)
    let day0 = Date(timeIntervalSince1970: 1_750_000_000)

    func makeState(mixed: Bool) -> BoxState {
        let cards = (0..<6).map {
            Card(id: "c\($0)", kind: .noun, pair: .deSw, area: "kitchen",
                 german: "Wort\($0)", translation: "neno\($0)", seedIndex: $0)
        }
        var config = BoxConfig(pair: .deSw)
        config.mixedDirections = mixed
        return BoxEngine.bootstrap(cards: cards, config: config)
    }

    @Test func fixedModeAlwaysPrimaryDirection() {
        let state = makeState(mixed: false)
        for i in 0..<6 {
            #expect(BoxEngine.presentationDirection(state: state, cardID: "c\(i)") == .deToTarget)
        }
    }

    @Test func mixedModeAlternatesPerReviewAndVariesPerCard() {
        var state = makeState(mixed: true)
        let before = BoxEngine.presentationDirection(state: state, cardID: "c0")
        state = BoxEngine.answer(state: state, cardID: "c0", rating: .good, now: day0, calendar: calendar)
        let after = BoxEngine.presentationDirection(state: state, cardID: "c0")
        #expect(before != after) // one review flips the presentation

        // Across cards, first exposures are not all one-way (hash offset).
        let fresh = makeState(mixed: true)
        let firsts = Set((0..<6).map { BoxEngine.presentationDirection(state: fresh, cardID: "c\($0)") })
        #expect(firsts.count == 2)
    }

    @Test func deterministicAcrossCalls() {
        let state = makeState(mixed: true)
        for i in 0..<6 {
            let a = BoxEngine.presentationDirection(state: state, cardID: "c\(i)")
            let b = BoxEngine.presentationDirection(state: state, cardID: "c\(i)")
            #expect(a == b)
        }
    }

    @Test func oldStoreDocumentsDecodeWithMixedDefaultOn() throws {
        // A pre-mixedDirections config JSON must decode (key absent → true).
        let legacy = """
        {"pair":"de-sw","direction":"deToTarget","newPerDay":5,"dueSoftCap":30,
         "sessionCap":30,"desiredRetention":0.9,"phraseUnlockStability":3.0}
        """
        let config = try JSONDecoder().decode(BoxConfig.self, from: Data(legacy.utf8))
        #expect(config.mixedDirections == true)
    }
}

@Suite struct ReversePhraseTests {
    private func template(_ id: String) -> PhraseTemplate {
        PhraseTemplates.all.first { $0.id == id }!
    }

    @Test func reverseNumberShowsTargetAsksGermanDigits() {
        let task = PhraseSlots.reverseInstantiate(template: template("uk-num-hefte"), value: 21)
        #expect(task.prompt == "У мене є двадцять один зошит.")
        #expect(task.display == "Ich habe 21 Hefte.")
        #expect(task.accepted == ["Ich habe 21 Hefte."])
        #expect(task.language == .german)
    }

    @Test func reverseClockAcceptsPaddedAndBareHour() {
        let task = PhraseSlots.reverseInstantiate(template: template("sw-clock-zug"), hour: 8, minute: 5)
        #expect(task.prompt == "Treni inaondoka saa mbili na dakika tano asubuhi.")
        #expect(task.accepted.contains("Der Zug fährt um 08:05 Uhr ab."))
        #expect(task.accepted.contains("Der Zug fährt um 8:05 Uhr ab."))
    }

    @Test func reverseSampleMatchesReverseInstantiate() {
        var a = SplitMix64R(state: 7), b = SplitMix64R(state: 7)
        for t in PhraseTemplates.all {
            let sampled = PhraseSlots.reverseSample(template: t, using: &a)
            let forward = PhraseSlots.sample(template: t, using: &b)
            #expect(sampled.prompt == forward.display, Comment(rawValue: t.id))
            #expect(sampled.language == .german, Comment(rawValue: t.id))
        }
    }
}

private struct SplitMix64R: RandomNumberGenerator {
    var state: UInt64
    mutating func next() -> UInt64 {
        state &+= 0x9E3779B97F4A7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        return z ^ (z >> 31)
    }
}
