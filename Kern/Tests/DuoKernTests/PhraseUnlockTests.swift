import Foundation
import Testing
import DuoKern

@Suite("Phrase unlock fast path")
struct PhraseUnlockTests {
    let cal = Box.calendar
    let now = Box.day1

    private func seeded() -> BoxState {
        Box.state(cards: [
            Box.word(1), Box.word(2), Box.word(3),
            Box.phrase("p1", components: ["w01", "w02"]),
        ])
    }

    @Test("end to end: components to review with stability >= 3 unlock the phrase within budget")
    func endToEndUnlock() {
        var state = seeded()

        // Locked while components are unscheduled: phrase never proposed
        let plan1 = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        #expect(plan1.unlockedPhrases.isEmpty)
        #expect(plan1.newWords == ["w01", "w02", "w03"])

        // .easy graduates straight to review with stability w3 ≈ 15.7 ≥ 3
        state = BoxEngine.answer(state: state, cardID: "w01", rating: .easy, now: now, calendar: cal)

        // One stable component is not enough — ALL must be stable
        let plan2 = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        #expect(plan2.unlockedPhrases.isEmpty)

        state = BoxEngine.answer(state: state, cardID: "w02", rating: .easy, now: now, calendar: cal)
        let plan3 = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        #expect(plan3.unlockedPhrases == ["p1"])
        // Unlocked phrase consumes the new budget ahead of words (3 left of 5)
        #expect(plan3.newWords == ["w03"])
        #expect(plan3.unlockedPhrases.count + plan3.newWords.count <= 3)
    }

    @Test("suspended component blocks the unlock")
    func suspendedComponentBlocks() {
        var state = seeded()
        state = BoxEngine.answer(state: state, cardID: "w01", rating: .easy, now: now, calendar: cal)
        state = BoxEngine.answer(state: state, cardID: "w02", rating: .easy, now: now, calendar: cal)
        state = BoxEngine.setSuspended(state: state, cardID: "w01", suspended: true)

        let plan = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        #expect(plan.unlockedPhrases.isEmpty)

        // Reviving the component restores eligibility
        state = BoxEngine.setSuspended(state: state, cardID: "w01", suspended: false)
        let revived = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        #expect(revived.unlockedPhrases == ["p1"])
    }

    @Test("component below unlock stability keeps the phrase locked")
    func lowStabilityBlocks() {
        var state = seeded()
        let future = now.addingTimeInterval(5 * 86_400)
        Box.inject(&state, Box.sched("w01", stability: 10, due: future, lastReview: now))
        Box.inject(&state, Box.sched("w02", stability: 2.9, due: future, lastReview: now))
        let plan = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        #expect(plan.unlockedPhrases.isEmpty)

        Box.inject(&state, Box.sched("w02", stability: 3.0, due: future, lastReview: now))
        let unlocked = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        #expect(unlocked.unlockedPhrases == ["p1"])
    }

    @Test("component in learning phase (not review) keeps the phrase locked")
    func learningPhaseBlocks() {
        var state = seeded()
        let future = now.addingTimeInterval(5 * 86_400)
        Box.inject(&state, Box.sched("w01", stability: 10, due: future, lastReview: now))
        Box.inject(&state, Box.sched("w02", phase: .learning, stability: 10, due: future, lastReview: now))
        let plan = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        #expect(plan.unlockedPhrases.isEmpty)
    }

    @Test("zero-component phrases follow normal seed order, never the fast path")
    func zeroComponentPhraseSeedOrder() {
        let state = Box.state(cards: [
            Box.word(1), Box.word(2),
            Box.phrase("p-empty", components: []),
        ])
        let plan = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        #expect(plan.unlockedPhrases.isEmpty)
        // words before phrases within the area
        #expect(plan.newWords == ["w01", "w02", "p-empty"])
    }

    @Test("real importer output: fixture phrase unlocks once its components are stable")
    func fixtureImportUnlock() throws {
        let conceptsURL = try #require(Bundle.module.url(forResource: "concepts", withExtension: "json",
                                                         subdirectory: "Fixtures/catalog"))
        let cards = try CatalogImporter.importCatalog(directory: conceptsURL.deletingLastPathComponent(),
                                                      pair: .deSw)
        let phrase = try #require(cards.first { $0.kind == .phrase && !$0.componentIDs.isEmpty })

        var state = BoxEngine.bootstrap(cards: cards, config: Box.config(maxLearning: 20))
        let future = now.addingTimeInterval(10 * 86_400)
        for componentID in phrase.componentIDs {
            Box.inject(&state, Box.sched(componentID, stability: 5, due: future, lastReview: now))
        }
        let plan = BoxEngine.composeSession(state: state, now: now, calendar: cal)
        #expect(plan.unlockedPhrases.contains(phrase.id))
    }
}
