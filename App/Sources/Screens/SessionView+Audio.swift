import SwiftUI
import SprossKern

/// AUDIO half of SessionView: the tap-to-replay affordances on the card.
/// State lives on SessionView; split out purely for file size.
///
/// What a card says aloud and when is the shared `Reader`'s, over kern's
/// `TurnState.reading` (`QuestionDriving.readAloud`); `Pronouncer` decides whether it may be heard.
extension SessionView {

    /// Tap-to-replay for a form — nil where the device can neither play nor
    /// speak it, so a word that cannot be heard grows no gesture that does
    /// nothing. The hit area on the card stands either way.
    func pronounceAction(for form: String) -> (() -> Void)? {
        guard let target = model.targetLanguage else { return nil }
        return model.pronounceAction(for: form, lang: target, article: spokenArticle(of: form))
    }

    /// Whether `form` is the word sounding right now — drives the small
    /// audio icon's pulse on the card headline.
    func isPronouncing(_ form: String) -> Bool {
        guard let target = model.targetLanguage else { return false }
        return model.isPronouncing(form, lang: target)
    }

    /// The article the voice says in front of `form` — the current card's, and
    /// only where `form` IS its canonical target: a rotated synonym, a typo
    /// correction and the source side all come back nil, which is exactly the
    /// ruling `CardDisplay.spokenArticle` makes.
    private func spokenArticle(of form: String, lang: String? = nil) -> String? {
        guard let card = model.currentCard, lang ?? card.target.lang == card.target.lang
        else { return nil }
        return CardDisplay.spokenArticle(of: card.target, shown: form)
    }

    private func pronunciation(of form: String) -> Pronunciation? {
        guard let target = model.targetLanguage, let catalog = model.catalog else { return nil }
        // The card's own article, so a word the pack recorded with one is heard
        // with it — the same ruling the live voice is handed just below.
        return catalog.pronunciation(lang: target, visibleForm: form,
                                     article: spokenArticle(of: form))
    }

    #if DEBUG
    /// `-uitest-pronounce <form>`: says one form and prints which branch
    /// answered it — the way `-uitest-sound` proves the chimes reached the
    /// bundle. The argument is a visible FORM, not a slug: the form is what
    /// the lookup is keyed by at runtime.
    func uitestPronounce(_ form: String) {
        guard let pronunciation = pronunciation(of: form) else { return }
        Pronouncer.shared.uitestProbe(pronunciation,
                                      recordingURL: model.audioURL(pronunciation.recordingPath))
    }
    #endif
}
