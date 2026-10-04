import Foundation
import SprossKern

// Searching the box. WHAT matches is Kern's rule (`BoxSearch`); this layer only
// supplies the area headings, which are a catalog lookup in the source language.

extension AppModel {

    /// Words and areas matching a typed query — nil before a box exists.
    func searchBox(_ query: String) -> BoxSearchResults? {
        guard let box else { return nil }
        return BoxSearch.shared.search(state: box, areas: searchableAreas, query: query)
    }

    /// The areas the browser lists, each under the heading the learner reads.
    private var searchableAreas: [SearchableArea] {
        areaNames.map { SearchableArea(area: $0, title: areaTitle($0)) }
    }

    /// Queue ONE word — what a search hit offers, where an area card queues a shelf.
    func queueCard(_ cardID: String) {
        guard box?.cards[cardID] != nil, scheduling(for: cardID) == nil,
              !isQueued(cardID)
        else { return }
        mutate { $0 = BoxEngine.shared.queue(state: $0, cardIds: [cardID]) }
    }

    /// Waiting in the priority queue: queued, not yet met.
    func isQueued(_ cardID: String) -> Bool {
        box?.queued.contains(cardID) ?? false
    }
}
