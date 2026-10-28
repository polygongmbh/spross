import AVFoundation
import SprossKern

/// The audio session's category, which decides whether the ring/silent switch silences a sound:
/// `.ambient` obeys it, `.playback` does not, and no API reads its position.
/// A sound the learner asked for plays under `.playback`, one the app fires by itself under `standing`,
/// which is `.playback` only while reading aloud is switched on by hand.
@MainActor
enum AudioSession {

    /// The category for sounds the app fires by itself.
    private(set) static var standing: AVAudioSession.Category = .ambient

    /// Applies the read-aloud setting's category at once, so a mid-session change reaches the next word.
    static func adopt(_ setting: ReadAloud) {
        standing = setting == .on ? .playback : .ambient
        use(standing)
    }

    /// Before an autoplay or a feedback chime.
    static func useStanding() { use(standing) }

    /// Before a tap, which plays through the silent switch.
    static func useExplicit() { use(.playback) }

    /// Whether the volume is too low to hear a word (kern `isVolumeLow`), for `VolumeHint`;
    /// it never withholds a sound.
    static var volumeIsLow: Bool {
        SprossKern.isVolumeLow(fraction: Double(AVAudioSession.sharedInstance().outputVolume))
    }

    /// Whether a listening run currently owns the session.
    private static var listening = false

    /// The listening run takes the audio over: `.playback` in `.spokenAudio` mode without `.mixWithOthers`,
    /// so other audio stops (`docs/audio.md`).
    /// Activated by hand, as background audio with the screen locked needs an active session.
    static func useListening() {
        guard !listening else { return }
        listening = true
        change { session in
            try? session.setCategory(.playback, mode: .spokenAudio, options: [])
            try? session.setActive(true)
        }
    }

    /// Reactivates the run's session after an interruption, which keeps the category but ends the activation.
    static func resumeListening() {
        guard listening else { return }
        change { try? $0.setActive(true) }
    }

    /// Hands the audio back: `notifyOthersOnDeactivation` lets interrupted audio resume,
    /// and the standing category returns for the next sound.
    static func endListening() {
        guard listening else { return }
        listening = false
        let standing = standing
        change { session in
            try? session.setActive(false, options: .notifyOthersOnDeactivation)
            try? session.setCategory(standing,
                                     options: standing == .playback ? [.mixWithOthers] : [])
        }
    }

    /// `.playback` gets `.mixWithOthers` so one word does not stop other audio;
    /// `.ambient` mixes already and rejects the option.
    private static func use(_ category: AVAudioSession.Category) {
        // why: a sound fired during a run would otherwise hand the run's session back.
        guard !listening else { return }
        change { session in
            // why: a redundant set still posts a route change.
            guard session.category != category else { return }
            try? session.setCategory(category, options: category == .playback ? [.mixWithOthers] : [])
        }
    }

    /// Changes to the session run in call order on one serial queue, off the main thread:
    /// setting a category on an active session or activating it blocks until the system answers.
    private nonisolated static let queue = DispatchQueue(label: "spross.audio-session", qos: .userInitiated)

    private nonisolated static func change(_ body: @escaping @Sendable (AVAudioSession) -> Void) {
        queue.async { body(.sharedInstance()) }
    }
}

// MARK: - ReadAloud

/// The read-aloud setting. A choice holds for this launch only:
/// the silent switch cannot be read, so a stored mute would leave the app quiet with nothing saying why.
enum ReadAloud: String {
    /// The default each launch: reads aloud, and the silent switch may silence it.
    case followsPhone
    /// Silenced in the app.
    case off
    /// Reads aloud through the silent switch too.
    case on
}

extension ReadAloud {

    /// `.followsPhone` unless a launch argument says otherwise (`-readAloud off`, `scripts/run-sim.sh --mute`);
    /// only the argument domain is read, so nothing stored outlives a launch.
    static var atLaunch: ReadAloud {
        let arguments = UserDefaults.standard.volatileDomain(forName: UserDefaults.argumentDomain)
        return (arguments["readAloud"] as? String).flatMap(ReadAloud.init(rawValue:)) ?? .followsPhone
    }
}

// MARK: - VoiceSource

/// Which voice says a target word: a bundled recording where one matches, or the synthesizer.
/// Stored per target language, since how the system voice compares with the recordings differs by language;
/// a language with no voice is never offered the choice.
enum VoiceSource: String {
    /// The default: recordings first, the synthesizer for the rest.
    case recordings
    /// The synthesizer for everything, so every word sounds alike and the article is spoken;
    /// recordings only where the language has no voice.
    case tts
}

extension VoiceSource {

    private static func key(_ language: String) -> String { "voiceSource.\(language)" }

    static func stored(for language: String) -> VoiceSource {
        if let raw = UserDefaults.standard.string(forKey: key(language)),
           let stored = VoiceSource(rawValue: raw) {
            return stored
        }
        return .recordings
    }

    func store(for language: String) {
        UserDefaults.standard.set(rawValue, forKey: Self.key(language))
    }
}
