import Foundation
import SprossKern

// Telling whoever maintains the catalog what is wrong with it, and what is missing
// from it. The rules — what a report holds, what counts as new since the last one,
// how the text reads — are Kern's (`ReportedIssue`, `Feedback`); this layer carries
// the clock, the clipboard and the mail app.

extension AppModel {

    /// The problem filed against this card, if any.
    func reportedIssue(for cardID: String) -> ReportedIssue? {
        box?.reportedIssues[cardID]
    }

    /// File a content problem against one card. `learnerInput` is whatever they had
    /// typed — always carried, never asked about (`ReportedIssue`).
    func reportIssue(cardID: String, comment: String?, learnerInput: String?) {
        mutate {
            $0 = BoxEngine.shared.reportIssue(
                state: $0, cardId: cardID, comment: comment,
                learnerInput: learnerInput, nowEpochMillis: Date().epochMillis
            )
        }
    }

    /// Withdraw a report; the card's schedule is untouched either way.
    func dismissReportedIssue(cardID: String) {
        mutate { $0 = BoxEngine.shared.dismissReportedIssue(state: $0, cardId: cardID) }
    }

    /// Whether this card can carry a report at all — what decides if the action is
    /// offered. The rule is Kern's (`Feedback.isReportable`).
    func isReportable(_ cardID: String) -> Bool {
        Feedback.shared.isReportable(cardId: cardID)
    }

    /// Problems filed against CATALOG cards — what the Box lists back for review.
    /// Which ones and in which order is Kern's (`Feedback.catalogIssues`), so the
    /// Box list and the exported text can never disagree about either.
    var catalogReports: [ReportedIssue] {
        guard let box else { return [] }
        return Feedback.shared.catalogIssues(state: box)
    }

    /// The half a suggestion does carry, whichever language it is in. A suggestion
    /// joins nothing and is never scheduled (`OwnWords.cards`), so the box holds no
    /// card to read it off, and the one text it has is the one to show.
    func suggestionText(_ word: OwnWord) -> String {
        word.languages.first.flatMap { word.texts[$0] } ?? ""
    }

    /// A pair this profile cannot study, read in the order kern names its languages —
    /// both halves the learner wrote, neither of them the known or the learning side here.
    func otherPairText(_ word: OwnWord) -> String {
        word.languages.compactMap { word.texts[$0] }.joined(separator: " → ")
    }

    /// Which pair it IS written in, which is the whole of why it has no card.
    func otherPairLanguages(_ word: OwnWord) -> String {
        word.languages.joined(separator: " → ")
    }

    /// Whether there is anything to copy or send at all — what grays the actions out.
    /// `onlyNew` measures against the last time the learner took a copy, `scope` against
    /// how much of what they wrote the catalog is owed.
    func hasFeedback(onlyNew: Bool, scope: FeedbackScope = .everything) -> Bool {
        guard let box else { return false }
        return Feedback.shared.hasAnything(state: box, since: onlyNew ? box.lastExportAt : nil,
                                           scope: scope)
    }

    /// Whether a copy has ever been taken — what makes "only what is new" an offer
    /// worth making rather than a second name for "everything".
    var hasExportedBefore: Bool { box?.lastExportAt != nil }

    /// What goes out as text — the same one the mail carries, so the clipboard can never
    /// come back with less than the Send button would have sent.
    func reportText(onlyNew: Bool, scope: FeedbackScope) -> String {
        guard let box else { return "" }
        return Feedback.shared.reportText(state: box, since: onlyNew ? box.lastExportAt : nil,
                                          scope: scope)
    }

    /// A mail to the maintainer carrying the same. Nil when there is nothing to say.
    func reportMailURL(onlyNew: Bool, scope: FeedbackScope) -> URL? {
        guard hasFeedback(onlyNew: onlyNew, scope: scope) else { return nil }
        let body = reportText(onlyNew: onlyNew, scope: scope)
        var components = URLComponents()
        components.scheme = "mailto"
        components.path = Legal.contactAddress
        components.queryItems = [
            URLQueryItem(name: "subject", value: Feedback.shared.MAIL_SUBJECT),
            URLQueryItem(name: "body", value: body),
        ]
        return components.url
    }

    /// Record that a copy has just been taken — what a later "only what is new"
    /// measures against. Whether this scope moves the stamp at all is kern's
    /// (`BoxEngine.markExported`).
    ///
    /// Stamped rather than mutated: `mutate` re-walks the whole box for the statistics,
    /// the orchard and the activity strip and re-encodes it for the watch and the widget,
    /// and the stamp feeds none of them — it is read by this file alone. Paying for all
    /// of that is what made the copy button hang, and what left the mail button's share
    /// of it landing as the app came back.
    func markExported(scope: FeedbackScope) {
        stamp { BoxEngine.shared.markExported(state: $0, nowEpochMillis: Date().epochMillis,
                                              scope: scope) }
    }

    /// How many entries a clear would take: the suggestions, the notes and the filed
    /// reports. Kern's count, not the screen's — a word written in both languages is
    /// study material and is never in it (`Feedback.clearableCount`).
    var clearableCount: Int {
        guard let box else { return 0 }
        return Int(Feedback.shared.clearableCount(state: box))
    }

    /// Empty the outbox: every suggestion, every note and every report go, the pairs stay.
    func clearFeedback() {
        mutate { $0 = BoxEngine.shared.clearFeedback(state: $0) }
    }
}
