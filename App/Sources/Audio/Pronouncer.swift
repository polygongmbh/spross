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
    }

    private static let mutedKey = "pronunciationMuted"

    /// One device-scoped flag (never per target language, never in the box —
    /// `withProductCalibration()` would reset it): silences AUTOPLAY only.
    /// Absent default = false, so reading aloud is on for fresh installs and
    /// upgrades alike; the top-bar toggle is the off switch.
    var muted: Bool {
        didSet {
            UserDefaults.standard.set(muted, forKey: Self.mutedKey)
            // why: muting is expected to take effect on the word in the air,
            // not only on the next card.
            if muted { stop() }
        }
    }

    private let player = PronunciationPlayer()
    private let speaker = Speaker()

    /// Identity of the pronunciation sounding right now, `lang|form` — nil
    /// when nothing is. Only one word plays at a time, but a screen can show
    /// several (the catalog list), so a UI icon compares its own key against
    /// this to know whether IT is the one pulsing.
    private(set) var playingKey: String?

    init() {
        muted = UserDefaults.standard.bool(forKey: Self.mutedKey)
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
        if trigger == .auto, muted || UIAccessibility.isVoiceOverRunning { return }
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
    /// Bypasses mute: it is a probe, not gameplay.
    func uitestProbe(_ pronunciation: Pronunciation, recordingURL: URL?) {
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
