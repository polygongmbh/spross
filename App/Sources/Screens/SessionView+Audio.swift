import SwiftUI
import SprossKern

/// AUDIO half of SessionView: when a card says its word and its meaning, and
/// which surface says them. State lives on SessionView; split out purely for
/// file size.
///
/// Kern decides WHAT each moment says (`TurnState.promptSaying` and
/// `answerSaying` — consumed here, never re-derived from the role) and
/// `Pronouncer` decides whether it may be heard. What is left is the timing,
/// and it is all here: every fire passes the one-shot guard, and the answer's
/// saying waits the feedback chime out while the advance beat waits it out in
/// turn (`answerVoice`).
extension SessionView {

    // MARK: - Autoplay

    /// Says what the card may say from frame one: the prompted target form, or
    /// the meaning a produce card asks by.
    func autoplayPrompt() {
        guard let turn = ensureTurn(),
              let saying = turn.promptSaying(saysMeaning: Pronouncer.shared.saysMeaning),
              claimAutoplay(turn.card.id, answer: false) else { return }
        speak(saying.form, lang: saying.lang, trigger: .auto)
    }

    /// Says the side the prompt held back, once the card has settled — a reveal,
    /// a hold on its correction, or a clean answer. The advance a clean answer
    /// arms waits for it (`answerVoice.said()`), so the word is never cut off by
    /// its own flip.
    func autoplayAnswer() {
        guard let turn,
              let saying = turn.answerSaying(saysMeaning: Pronouncer.shared.saysMeaning),
              claimAutoplay(turn.card.id, answer: true) else { return }
        answerVoice.speak(saying.form, lang: saying.lang, via: model,
                          article: spokenArticle(of: saying.form, lang: saying.lang))
    }

    /// The one-shot guard, asked by every autoplay path: each card says its
    /// prompt once and its answer once. The card-change hook fires nil→id for
    /// the FIRST card on top of `.onAppear`, and `settled` is not monotonic —
    /// without this the first card speaks twice and typing past the answer
    /// re-fires it. Cleared per card by `resetCardState()`.
    private func claimAutoplay(_ cardID: String, answer: Bool) -> Bool {
        spokenMoments.insert("\(cardID)|\(answer)").inserted
    }

    // MARK: - One fire

    /// Hands one visible form to the shared pronouncer: Kern resolves what to
    /// say and whether a bundled recording speaks that very form, the model
    /// turns its catalog path into a bundle URL.
    func speak(_ form: String, lang: String, trigger: Pronouncer.Trigger) {
        guard let pronunciation = model.formPronunciation(form, lang: lang,
                                                          article: spokenArticle(of: form, lang: lang))
        else { return }
        Pronouncer.shared.pronounce(pronunciation,
                                    recordingURL: model.audioURL(pronunciation.recordingPath),
                                    trigger: trigger, article: spokenArticle(of: form, lang: lang))
    }

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
