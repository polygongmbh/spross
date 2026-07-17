import Foundation
import Testing
@testable import DuoKern

@Suite struct ReconcileTests {
    let calendar = Calendar(identifier: .gregorian)
    let day0 = Date(timeIntervalSince1970: 1_750_000_000)

    func card(_ id: String, seedIndex: Int = 0) -> Card {
        Card(id: id, kind: .noun, pair: .deSw, area: "kitchen",
             german: id, translation: "t-\(id)", seedIndex: seedIndex)
    }

    @Test func newSeedCardsAppearAndAreIntroducible() {
        var state = BoxEngine.bootstrap(cards: [card("a")], config: BoxConfig(pair: .deSw))
        state = BoxEngine.reconcileSeed(state: state, seed: [card("a"), card("b", seedIndex: 1)])
        #expect(state.cards["b"] != nil)
        let plan = BoxEngine.composeSession(state: state, now: day0, calendar: calendar)
        #expect(plan.newWords.contains("b"))
    }

    @Test func vanishedUnstudiedCardsAreDroppedButStudiedOrphansSurvive() {
        var state = BoxEngine.bootstrap(cards: [card("a"), card("b")], config: BoxConfig(pair: .deSw))
        state = BoxEngine.enqueue(state: state, cardIDs: ["a"])
        state = BoxEngine.answer(state: state, cardID: "a", rating: .good, now: day0, calendar: calendar)
        state = BoxEngine.reconcileSeed(state: state, seed: []) // everything vanished
        #expect(state.cards["a"] != nil)  // has history → kept
        #expect(state.cards["b"] == nil)  // never studied → dropped
    }

    @Test func changedCardContentUpdatesInPlace() {
        var state = BoxEngine.bootstrap(cards: [card("a")], config: BoxConfig(pair: .deSw))
        var updated = card("a")
        updated.translation = "corrected"
        state = BoxEngine.reconcileSeed(state: state, seed: [updated])
        #expect(state.cards["a"]?.translation == "corrected")
    }
}
