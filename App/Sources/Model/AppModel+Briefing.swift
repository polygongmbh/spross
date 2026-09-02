import Foundation
import SprossKern

// Handing the box's state to a conversation partner the app does not host, and
// reading back what that conversation turned up. The rules — which words a brief may
// name, how it reads, what a pasted answer means — are Kern's (`Briefing`, `Harvest`);
// this layer carries the clock, the clipboard and the share sheet.

extension AppModel {

    /// What the box would tell an assistant about this learner right now.
    ///
    /// A function rather than a property: it walks every card in the box, and a view
    /// that read it as state would rebuild the whole brief on each redraw. The sheet
    /// takes one copy when it opens and works off that.
    func makeBriefing() -> Briefing? {
        guard let box, let catalog else { return nil }
        return Briefings.shared.of(state: box, catalog: catalog, learnerName: learnerName)
    }

    /// Whether there is a conversation to be briefed at all — what hides the offer.
    /// Cheap enough for a view body: it asks the counts, never the words.
    var hasBriefing: Bool {
        guard let box else { return false }
        return box.scheduling.values.contains { !$0.suspended }
    }

    /// The words a pasted conversation brought home that the box does not already hold.
    func harvest(from pasted: String) -> [BriefWord] {
        guard let box else { return [] }
        return Harvest.shared.read(text: pasted, state: box)
    }

    /// Take the kept ones in as own words, in one write.
    ///
    /// One at a time off the state the last one returned, never in a batch against the
    /// state this started from: the id is minted against the ids already taken, and two
    /// words that fold alike would otherwise mint the same one (`Harvest.ownWord`).
    func keepHarvested(_ words: [BriefWord]) {
        guard !words.isEmpty else { return }
        let now = Date().epochMillis
        mutate { state in
            for word in words {
                let own = Harvest.shared.ownWord(state: state, word: word)
                state = BoxEngine.shared.addOwnWord(state: state, word: own, nowEpochMillis: now)
            }
        }
    }
}
