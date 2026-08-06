import Foundation
import SprossKern
import UIKit

// The app half of the audio boundary: Kern hands out catalog-relative PATHS
// and never opens a file, so turning a path into a bundle URL happens here —
// once, in the model both the session and the drills already hold.

extension AppModel {

    /// why: the bundled catalog is a folder reference — it cannot move under a
    /// running process, so the lookup is paid once instead of per card.
    private static let bundledCatalog: URL? =
        Bundle.main.url(forResource: "catalog", withExtension: nil)

    /// The catalog folder inside the app bundle — the same directory
    /// `loadCatalog()` reads its JSON through.
    var catalogDirectory: URL? { Self.bundledCatalog }

    /// Bundle URL for a recording path from Kern ("audio/uk/office.mp3").
    /// A nil path means no recording matched the visible form; it stays nil,
    /// and the caller falls back to the live voice.
    func audioURL(_ path: String?) -> URL? {
        guard let path, let directory = catalogDirectory else { return nil }
        return directory.appending(path: path)
    }

    /// WHEN this card's target form may be said out loud — Kern's rule,
    /// consumed rather than re-derived: both apps switch on this one cue
    /// instead of each testing the role in its own way.
    func pronunciationCue(for card: Card) -> PronunciationCue {
        SprossKern.pronunciationCue(role: presentationRole(for: card.id),
                                    prompt: producePrompt(for: card))
    }

    /// Whether a produce card asks by MEANING or by ear — Kern's rule again,
    /// with the one fact it cannot have: whether this device, right now, can
    /// say the word at all. Three ways it cannot, and each falls back to the
    /// source prompt rather than putting up a card with nothing in it: no
    /// recording and no voice, reading aloud switched off, and VoiceOver, which
    /// suppresses every autoplay so nothing may speak over the screen reader.
    func producePrompt(for card: Card) -> ProducePrompt {
        guard let pronunciation = formPronunciation(card.target.text, lang: card.target.lang)
        else { return .source }
        let audible = !Pronouncer.shared.muted
            && !UIAccessibility.isVoiceOverRunning
            && Pronouncer.shared.canPronounce(pronunciation,
                                              recordingURL: audioURL(pronunciation.recordingPath))
        return SprossKern.producePrompt(cardId: card.id,
                                        reviewCount: Int32(scheduling(for: card.id)?.reviewCount ?? 0),
                                        consolidated: isConsolidated(card.id),
                                        audible: audible)
    }

    // MARK: - Letters

    /// A letter's NAME, out of the letters pack. The one lookup that is NOT
    /// keyed by the visible form: what is written (р) and what is said («ер»)
    /// are different strings, so the manifest is addressed by the glyph.
    func letterPronunciation(name: String, glyph: String, lang: String) -> Pronunciation {
        // why: the whole recording, not just its path — the letters are the
        // quietest and latest-starting files we ship, and the drill is where
        // that is heard, so the analysis index travels with them.
        let recording = catalog?.letterRecording(lang: lang, glyph: glyph)
        return Pronunciation(form: name,
                             utterance: SprossKern.utterance(form: name),
                             lang: lang,
                             recordingPath: recording?.path,
                             gain: recording?.gain ?? 0,
                             leadMs: recording?.leadMs ?? 0)
    }

    /// A visible target form, through the same matched-form lookup the review
    /// cards use — a recording only ever plays over the word it actually says.
    func formPronunciation(_ form: String, lang: String) -> Pronunciation? {
        catalog?.pronunciation(lang: lang, visibleForm: form)
    }

    /// A form with NO recording looked up at all: the live voice reads what
    /// stands on screen. For text carrying no slug (an `exampleText` escape
    /// hatch) — a concept's recording may not be claimed for a different word.
    func spokenPronunciation(_ form: String, lang: String) -> Pronunciation {
        // why: 0/0 — the live voice is synthesized at the system's own
        // loudness and starts when it is asked to; there is nothing to correct.
        Pronunciation(form: form,
                      utterance: SprossKern.utterance(form: form),
                      lang: lang,
                      recordingPath: nil,
                      gain: 0,
                      leadMs: 0)
    }

    // MARK: - Tap-to-replay

    /// Tap-to-replay for a visible form in `lang` — nil where the device can
    /// neither play nor speak it, so a word with nothing to hear grows no
    /// gesture (and no icon) that does nothing. The shared entry point for
    /// every audio affordance outside a review card — SessionView keeps its
    /// own target-language shorthand, the catalog list calls this directly.
    func pronounceAction(for form: String, lang: String) -> (() -> Void)? {
        guard let pronunciation = catalog?.pronunciation(lang: lang, visibleForm: form) else { return nil }
        let recordingURL = audioURL(pronunciation.recordingPath)
        guard Pronouncer.shared.canPronounce(pronunciation, recordingURL: recordingURL) else { return nil }
        // why: a tap is a request, not autoplay — it speaks even while reading
        // aloud is switched off.
        return { Pronouncer.shared.pronounce(pronunciation, recordingURL: recordingURL, trigger: .tap) }
    }

    /// Whether `form` in `lang` is the word sounding right now — drives an
    /// audio icon's pulse.
    func isPronouncing(_ form: String, lang: String) -> Bool {
        guard let pronunciation = catalog?.pronunciation(lang: lang, visibleForm: form) else { return false }
        return Pronouncer.shared.playingKey == Pronouncer.key(for: pronunciation)
    }

    // MARK: - Forms the catalog has never heard of

    /// What says `form`, falling back to the live voice where the catalog holds
    /// no entry for it. The catalog still wins when it has one — a measured
    /// recording beats a synthesized reading of the same word.
    ///
    /// The drills GENERATE their answers ("dreihundertsiebenundvierzig", "son
    /// las tres y cuarto"): no catalog lists them, so the catalog-only lookup
    /// returns nothing and they were simply never spoken.
    func spokenOrRecorded(_ form: String, lang: String) -> Pronunciation {
        catalog?.pronunciation(lang: lang, visibleForm: form) ?? spokenPronunciation(form, lang: lang)
    }

    /// Tap-to-replay for any visible form, generated or catalogued. Still nil
    /// where the device can do neither — Swahili has no iOS voice, so an
    /// unrecorded Swahili form grows no icon rather than a silent one.
    func speakAction(for form: String, lang: String) -> (() -> Void)? {
        let pronunciation = spokenOrRecorded(form, lang: lang)
        let recordingURL = audioURL(pronunciation.recordingPath)
        guard Pronouncer.shared.canPronounce(pronunciation, recordingURL: recordingURL) else { return nil }
        // why: a tap is a request, not autoplay — it speaks even while reading
        // aloud is switched off.
        return { Pronouncer.shared.pronounce(pronunciation, recordingURL: recordingURL, trigger: .tap) }
    }

    /// Autoplay sibling of `speakAction` — obeys the read-aloud switch, where a
    /// tap outranks it.
    func speakAloud(_ form: String, lang: String) {
        let pronunciation = spokenOrRecorded(form, lang: lang)
        Pronouncer.shared.pronounce(pronunciation,
                                    recordingURL: audioURL(pronunciation.recordingPath),
                                    trigger: .auto)
    }

    func isSpeaking(_ form: String, lang: String) -> Bool {
        Pronouncer.shared.playingKey == Pronouncer.key(for: spokenOrRecorded(form, lang: lang))
    }

    /// What a letter-drill question SAYS, and out of which recording — the
    /// task's provenance decides that, and nothing else.
    func promptPronunciation(for task: LetterDrillTask) -> Pronunciation? {
        switch task.promptKind {
        case .name:
            guard let glyph = task.promptGlyph else { return nil }
            return letterPronunciation(name: task.promptText, glyph: glyph, lang: task.language)
        case .word:
            return formPronunciation(task.promptText, lang: task.language)
        case .plainText:
            // why: an alphabet `exampleText` carries no slug but the manifest records the
            // FORM (`texts{}`), so reference words like sechs and the pero/perro pair are
            // heard as a voice recorded them; synthesis is the fallback, not the rule.
            return formPronunciation(task.promptText, lang: task.language)
                ?? spokenPronunciation(task.promptText, lang: task.language)
        }
    }
}
