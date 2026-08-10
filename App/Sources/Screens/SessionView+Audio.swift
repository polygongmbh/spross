import SwiftUI
import SprossKern

/// AUDIO half of SessionView: when a card's target word is said out loud, and
/// which surface says it. State lives on SessionView; split out purely for
/// file size.
///
/// Kern decides WHETHER the form is on screen (`PronunciationCue` — consumed
/// here, never re-derived from the role) and `Pronouncer` decides whether it
/// may be heard. What is left is the timing, and it is all here: every fire
/// passes the one-shot guard, no fire ever touches an auto-advance timer, and
/// the produce paths wait the feedback chime out.
extension SessionView {

    // MARK: - Autoplay

    /// Says the prompt where the cue has the target standing on the card from
    /// frame one. A produce card plays nothing here — its word is the thing
    /// being asked for; `produceAudioTrigger` below arms the reveal instead.
    func autoplayPrompt(_ card: Card) {
        switch model.pronunciationCue(for: card) {
        case .upfront:
            // why: the PROMPTED form, never the canonical one — a rotated
            // synonym has to be heard as the word that is actually on screen.
            // A sound-prompted produce has nothing on screen at all, so what
            // plays is the very form it grades against.
            guard claimAutoplay(card.id) else { return }
            let form = model.producePrompt(for: card) == .sound
                ? card.target.text
                : model.promptForm(for: card)
            speak(form, trigger: .auto)
        case .onReveal:
            break
        }
    }

    /// True exactly where a PRODUCE card holds the learner on the answer: the
    /// typo correction, and the reveal a miss or "Aufdecken" opens.
    ///
    /// Role-gated, because `cardRevealed` is true on a recognition reveal and
    /// through the copy step too — speaking there would say the canonical word
    /// after a rotated synonym was prompted, the one thing the matched-form
    /// lookup exists to prevent, and say it twice besides.
    ///
    /// Deliberately WITHOUT `feedback == .correct`: an accepted answer flips on
    /// a beat shorter than a recording lasts, and `feedback` swings back and
    /// forth on every keystroke past the answer. A word cut off every time is
    /// worse than a word not played — the next recognition of it speaks in
    /// full, and a tap always does.
    var produceAudioTrigger: Bool {
        guard let card = model.currentCard,
              model.presentationRole(for: card.id) == .produce else { return false }
        return cardRevealed || almostHold != nil
    }

    /// Says the produce card's word once its transition has landed.
    ///
    /// A beat out (~300 ms): the correct/wrong/reveal chime belongs to DLSound
    /// and is never ducked or altered, so the word waits for it rather than
    /// talking over its own first syllable. Nothing waits on the word in turn —
    /// these are the two produce paths that carry no auto-advance at all.
    func autoplayProduceReveal() {
        guard let card = model.currentCard, claimAutoplay(card.id) else { return }
        // why: the correction box is the only place a typo's proper spelling
        // stands. Otherwise the bare target text — never `CardDisplay.citation`,
        // whose article is grammar decoration the audio never speaks, and the
        // very form a heard-instead hold already names.
        let form = typoCorrection ?? card.target.text
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(300))
            // why: a tap on through inside the beat takes the card with it —
            // the word armed for it must not follow the learner to the next.
            guard currentCardID == card.id else { return }
            speak(form, trigger: .auto)
        }
    }

    /// The one-shot guard, asked by every autoplay path. The card-change hook
    /// fires nil→id for the FIRST card on top of `.onAppear`, and the produce
    /// predicate is not monotonic — without this the first card speaks twice
    /// and typing past the answer re-fires the reveal. Cleared per card by
    /// `resetCardState()`.
    private func claimAutoplay(_ cardID: String) -> Bool {
        guard pronouncedCardID != cardID else { return false }
        pronouncedCardID = cardID
        return true
    }

    // MARK: - One fire

    /// Hands one visible form to the shared pronouncer: Kern resolves what to
    /// say and whether a bundled recording speaks that very form, the model
    /// turns its catalog path into a bundle URL.
    func speak(_ form: String, trigger: Pronouncer.Trigger) {
        guard let pronunciation = pronunciation(of: form) else { return }
        Pronouncer.shared.pronounce(pronunciation,
                                    recordingURL: model.audioURL(pronunciation.recordingPath),
                                    trigger: trigger)
    }

    /// Tap-to-replay for a form — nil where the device can neither play nor
    /// speak it, so a word that cannot be heard grows no gesture that does
    /// nothing. The hit area on the card stands either way.
    func pronounceAction(for form: String) -> (() -> Void)? {
        guard let target = model.targetLanguage else { return nil }
        return model.pronounceAction(for: form, lang: target)
    }

    /// Whether `form` is the word sounding right now — drives the small
    /// audio icon's pulse on the card headline.
    func isPronouncing(_ form: String) -> Bool {
        guard let target = model.targetLanguage else { return false }
        return model.isPronouncing(form, lang: target)
    }

    private func pronunciation(of form: String) -> Pronunciation? {
        guard let target = model.targetLanguage, let catalog = model.catalog else { return nil }
        return catalog.pronunciation(lang: target, visibleForm: form)
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

extension View {
    /// Tap-to-replay for the produce narration LINES — the typo correction and
    /// the other-word reveal, where a correct or nearly-correct answer leaves
    /// the card closed and the word stands nowhere else. No 44 pt floor here:
    /// these are running text under the card, and growing them would move a
    /// layout the reveal has just settled.
    @ViewBuilder
    func pronounceOnTap(_ pronounce: (() -> Void)?) -> some View {
        if let pronounce {
            contentShape(Rectangle())
                .onTapGesture(perform: pronounce)
                .accessibilityAction(named: Text("a11y.pronounce"), pronounce)
        } else {
            self
        }
    }
}
