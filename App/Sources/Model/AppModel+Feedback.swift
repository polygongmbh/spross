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

    /// Words the learner wrote down with only one half, newest last. They join
    /// nothing and are never scheduled (`OwnWords.cards`), so no card row can list
    /// them — this is the only place they are visible.
    var suggestions: [OwnWord] {
        guard let box, let target = targetLanguage else { return [] }
        return box.ownWords.filter { $0.isSuggestion(source: sourceLanguage, target: target) }
    }

    /// The half a suggestion does carry, whichever language it is in.
    func suggestionText(_ word: OwnWord) -> String {
        word.texts[targetLanguage ?? ""] ?? word.texts[sourceLanguage] ?? ""
    }

    /// Whether there is anything to copy or send at all — what grays the actions out.
    /// `onlyNew` measures against the last time the learner took a copy.
    func hasFeedback(onlyNew: Bool) -> Bool {
        guard let box else { return false }
        return Feedback.shared.hasAnything(state: box, since: onlyNew ? box.lastExportAt : nil)
    }

    /// Whether a copy has ever been taken — what makes "only what is new" an offer
    /// worth making rather than a second name for "everything".
    var hasExportedBefore: Bool { box?.lastExportAt != nil }

    /// The suggestions and the reports as text — the same one the mail carries, so the
    /// clipboard can never come back with less than the Send button would have sent.
    func reportText(onlyNew: Bool) -> String {
        guard let box else { return "" }
        return Feedback.shared.reportText(state: box, since: onlyNew ? box.lastExportAt : nil)
    }

    /// A mail to the maintainer carrying the suggestions and the reports.
    /// Nil when there is nothing to say.
    func reportMailURL(onlyNew: Bool) -> URL? {
        guard hasFeedback(onlyNew: onlyNew) else { return nil }
        let body = reportText(onlyNew: onlyNew)
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
    /// measures against. Taking everything still marks it: either way the learner
    /// has now seen the lot.
    func markExported() {
        mutate { $0 = BoxEngine.shared.markExported(state: $0, nowEpochMillis: Date().epochMillis) }
    }
}
