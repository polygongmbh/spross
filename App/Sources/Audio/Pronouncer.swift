import Foundation
import SprossKern
import UIKit

/// The one way anything in the app says a target word out loud: review cards
/// and (later) the letter drill both go through here, so the mute flag, the
/// VoiceOver gate and "recordings first" are decided in a single place.
///
/// Kern decides WHAT to say (`Pronunciation`: the form, the utterance, and the
/// catalog-relative path of a matching recording); this decides WHETHER and
/// WITH WHAT.
@Observable
@MainActor
final class Pronouncer {

    static let shared = Pronouncer()

    /// Where a fire came from. Autoplay may be silenced; a tap is a request.
    enum Trigger {
        case auto
        case tap
        /// An autoplay that carries the QUESTION itself (the letter drill):
        /// opening a screen whose only content is a sound IS the request, so
        /// neither mute reaches it. Only VoiceOver still holds it back.
        case essential
    }

    /// One device-scoped setting (never per target language, never in the box —
    /// `withProductCalibration()` would reset it): governs AUTOPLAY only, and
    /// starts at `.followsPhone`, so reading aloud is on for fresh installs
    /// while the silent switch keeps its say.
    var readAloud: ReadAloud {
        didSet {
            readAloud.store()
            // why: the phone's switch acts on the CATEGORY, so a setting
            // flipped mid-session has to reach the session, not just this flag.
            AudioSession.adopt(readAloud)
            // why: muting is expected to take effect on the word in the air,
            // not only on the next card.
            if readAloud == .off { stop() }
        }
    }

    /// Whether reading aloud is switched off IN THE APP — what the two toggles
    /// render. A phone silenced by its own switch is not this, and cannot be
    /// read at all.
    var muted: Bool { readAloud == .off }

    private let player = PronunciationPlayer()
    private let speaker = Speaker()

    /// Identity of the pronunciation sounding right now, `lang|form` — nil
    /// when nothing is. Only one word plays at a time, but a screen can show
    /// several (the catalog list), so a UI icon compares its own key against
    /// this to know whether IT is the one pulsing.
    private(set) var playingKey: String?

    init() {
        readAloud = .stored
    }

    /// What both switches call: turning reading aloud ON is itself a request to
    /// hear something, so from then on it outranks a silenced phone — the
    /// switch never claims a word the phone would eat.
    func setReadAloud(on: Bool) {
        readAloud = on ? .on : .off
    }

    static func key(for pronunciation: Pronunciation) -> String {
        "\(pronunciation.lang)|\(pronunciation.form)"
    }

    /// Whether the device has a voice for `language` at all (Swahili on iOS
    /// has none — those words are silent unless a recording matches).
    func canSpeak(language: String) -> Bool { speaker.canSpeak(language: language) }

    /// Whether this form can be heard at all — gates the tap-to-replay
    /// affordance so a word with neither a recording nor a voice grows no
    /// gesture that does nothing.
    func canPronounce(_ pronunciation: Pronunciation, recordingURL: URL?) -> Bool {
        recordingURL != nil || canSpeak(language: pronunciation.lang)
    }

    /// Says the form: the recording when one matched, else the live voice.
    func pronounce(_ pronunciation: Pronunciation, recordingURL: URL?, trigger: Trigger) {
        switch trigger {
        case .auto:
            if muted || UIAccessibility.isVoiceOverRunning { return }
            AudioSession.useStanding()
        case .essential:
            // why: the sound IS the question — either mute would leave a card
            // with nothing on it, and the replay tap breaks through the phone's
            // switch anyway, so deferring to it buys a tap per question and no
            // silence at all. VoiceOver alone still holds: it must not be
            // talked over, and the replay glyph takes its focus per task.
            if UIAccessibility.isVoiceOverRunning { return }
            AudioSession.useExplicit()
        case .tap:
            // why: a tap outranks BOTH mutes — the app's switch already let it
            // through, and the category is what lets it past the phone's.
            AudioSession.useExplicit()
        }
        // why: one word at a time — a new fire replaces whatever is sounding.
        stop()
        let key = Self.key(for: pronunciation)
        if let recordingURL {
            // why: the loudness and the dead air are the catalog's MEASUREMENTS
            // of bytes that stay the untouched transcode — playback is the one
            // place they are ever applied, and never the file.
            playingKey = key
            player.play(url: recordingURL, gainDb: pronunciation.gain, leadMs: pronunciation.leadMs) {
                [weak self] in self?.clearPlaying(key)
            }
            return
        }
        // Silent no-op when no voice exists for the language.
        guard canSpeak(language: pronunciation.lang) else { return }
        playingKey = key
        speaker.speak(pronunciation.utterance, language: pronunciation.lang) {
            [weak self] in self?.clearPlaying(key)
        }
    }

    func stop() {
        player.stop()
        speaker.stop()
        playingKey = nil
    }

    /// A stale finish — from a `stop()` or a newer word already sounding —
    /// answers to nobody.
    private func clearPlaying(_ key: String) {
        guard playingKey == key else { return }
        playingKey = nil
    }

    /// Plays the bundled silent clip once so the process's first audio-session
    /// activation happens here rather than on a focus-bearing transition
    /// (`PronunciationPlayer.warmUp`). Call it where nothing is typed.
    /// The asset is generated, not recorded:
    /// `ffmpeg -f lavfi -i anullsrc=r=44100:cl=mono -t 0.05 -b:a 32k silence.mp3`.
    func warmUp() {
        guard let url = Bundle.main.url(forResource: "silence", withExtension: "mp3")
        else { return }
        player.warmUp(url: url)
    }

    #if DEBUG
    /// UI-test hook (`-uitest-pronounce <form>`): says one form and prints
    /// which branch answered it — the audibility proxy in the simulator, and
    /// the check that the recording actually made it into the bundle.
    /// Bypasses both mutes: it is a probe, not gameplay.
    func uitestProbe(_ pronunciation: Pronunciation, recordingURL: URL?) {
        AudioSession.useExplicit()
        stop()
        if let recordingURL {
            let path = pronunciation.recordingPath ?? recordingURL.lastPathComponent
            player.play(url: recordingURL,
                        gainDb: pronunciation.gain, leadMs: pronunciation.leadMs) {
                print("Pronounce probe: recording \(path) played to completion")
            }
            return
        }
        guard canSpeak(language: pronunciation.lang) else {
            print("Pronounce probe: NO recording, NO voice for \(pronunciation.lang) — silent")
            return
        }
        speaker.speak(pronunciation.utterance, language: pronunciation.lang) {
            print("""
                Pronounce probe: TTS \(pronunciation.lang) \
                spoke "\(pronunciation.utterance)" to completion
                """)
        }
    }
    #endif
}
