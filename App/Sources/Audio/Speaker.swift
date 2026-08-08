import AVFoundation
import SprossKern
import UIKit

/// Live speech synthesis for a target form — the fallback branch of
/// pronunciation: bundled recordings are canonical, the synthesizer answers
/// only where none matches.
///
/// Synthesis is LIVE, always. There is no write-to-file path here and there
/// must never be one: the system voices may be spoken, not shipped.
@MainActor
final class Speaker: NSObject {

    private let synthesizer = AVSpeechSynthesizer()
    /// language code → best installed voice, `nil` = looked and found none.
    private var voices: [String: AVSpeechSynthesisVoice?] = [:]
    /// Probe-only completion (§9); gameplay speech is fire-and-forget.
    private var onFinish: (@MainActor () -> Void)?

    override init() {
        super.init()
        // why: without this the synthesizer opens a session of its own and
        // answers to nothing the app set — TTS would talk through the
        // ring/silent switch while the recordings stayed politely silent, and
        // AudioSession's per-fire category would reach only half the words.
        synthesizer.usesApplicationAudioSession = true
        synthesizer.delegate = self
        // why: a voice installed in Settings while the app slept is invisible
        // to a cached table, and no API announces the download — so the table
        // is dropped on every foreground and rebuilt on the next question.
        NotificationCenter.default.addObserver(
            forName: UIApplication.willEnterForegroundNotification,
            object: nil, queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.voices.removeAll() }
        }
    }

    /// Whether this device can say anything at all in `language` — false for
    /// Swahili on iOS, which has no voice at any quality tier.
    func canSpeak(language: String) -> Bool { voice(for: language) != nil }

    /// The tier of the voice that would actually answer for `language`, `nil`
    /// where none would. iOS bundles only `.default` (compact) voices; the
    /// `.enhanced` and `.premium` ones are a free download nobody is told
    /// about, and the difference is not subtle — which is what the hint reads.
    func voiceQuality(for language: String) -> AVSpeechSynthesisVoiceQuality? {
        voice(for: language)?.quality
    }

    /// Speaks `text` in `language`; a language with no installed voice is a
    /// silent no-op rather than a wrong-voice reading.
    func speak(_ text: String, language: String, onFinish: (@MainActor () -> Void)? = nil) {
        guard let voice = voice(for: language) else { return }
        stop()
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = voice
        self.onFinish = onFinish
        synthesizer.speak(utterance)
    }

    func stop() {
        onFinish = nil
        synthesizer.stopSpeaking(at: .immediate)
    }

    // MARK: - Voice choice

    private func voice(for language: String) -> AVSpeechSynthesisVoice? {
        if let cached = voices[language] { return cached }
        let resolved = Self.bestVoice(for: language)
        voices[language] = resolved
        return resolved
    }

    /// The voice a bare language code is spoken in. WHICH candidate wins — the
    /// peninsular narrowing for Spanish included — is kern's
    /// (`catalog/VoiceSelection.kt`); enumerating the device's voices and
    /// reading Apple's own quality scale is ours.
    private static func bestVoice(for language: String) -> AVSpeechSynthesisVoice? {
        let installed = AVSpeechSynthesisVoice.speechVoices()
        let chosen = VoiceSelection.shared.select(
            language: language,
            candidates: installed.map {
                VoiceSelection.Candidate(languageTag: $0.language,
                                         quality: Int32($0.quality.rawValue),
                                         identifier: $0.identifier)
            }
        )
        guard let chosen else { return nil }
        return installed.first { $0.identifier == chosen.identifier }
    }
}

// MARK: - AVSpeechSynthesizerDelegate

extension Speaker: AVSpeechSynthesizerDelegate {
    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer,
                                       didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor [weak self] in
            guard let self else { return }
            let finished = onFinish
            onFinish = nil
            finished?()
        }
    }
}
