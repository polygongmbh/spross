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

    /// Whether there is a box to brief — every box is, the unopened one as a beginner's talk.
    var hasBriefing: Bool { box != nil }

    /// Every word a pasted conversation brought home, each against what the box
    /// already has of it (`HarvestKind`), grouped new-first.
    func harvest(from pasted: String) -> [HarvestWord] {
        guard let box else { return [] }
        return Harvest.shared.read(text: pasted, state: box)
    }

    /// Take the kept ones in as own words, queued, in one write (`Harvest.keep`).
    func keepHarvested(_ words: [BriefWord]) {
        guard !words.isEmpty else { return }
        let now = Date().epochMillis
        mutate { state in
            state = Harvest.shared.keep(state: state, words: words, nowEpochMillis: now)
        }
    }

    /// Every pair a word-list file holds, each against the box (`WordListFile`).
    func harvest(fromFile text: String) -> [HarvestWord] {
        guard let box else { return [] }
        return WordListFile.shared.read(text: text, state: box)
    }
}
