import AVFoundation

/// WHICH volume domain a sound plays in — and therefore whether the phone's
/// ring/silent switch is allowed to swallow it.
///
/// Two mutes can silence this app and they sit at different layers: the
/// read-aloud switch is ours, in code, and the hardware switch is the session
/// CATEGORY's — `.ambient` obeys it, `.playback` does not. No API reads the
/// switch's position (Sounds.swift doctrine), so the only way to reach a
/// learner through it is to have asked for `.playback` BEFORE playing.
///
/// The rule, one line: a sound the learner asked for is `.playback`; a sound
/// the app decided to make on its own is `.ambient`, and the phone stays its
/// authority. Turning reading aloud on by hand is itself an asking — that is
/// what `standing` carries, so the app's own switch never claims a word the
/// phone would eat.
@MainActor
enum AudioSession {

    /// The category everything the app fires BY ITSELF plays under.
    private(set) static var standing: AVAudioSession.Category = .ambient

    /// Takes the read-aloud setting's category as the standing one and applies
    /// it at once: a switch flipped mid-session is expected to reach the next
    /// word, not the next launch.
    static func adopt(_ setting: ReadAloud) {
        standing = setting == .on ? .playback : .ambient
        use(standing)
    }

    /// Before an autoplay or a feedback chime — the phone decides, unless the
    /// learner has already said out loud that it should not.
    static func useStanding() { use(standing) }

    /// Before a deliberate tap: a request is answered whatever either switch
    /// says.
    static func useExplicit() { use(.playback) }

    /// Whether a listening run currently owns the session.
    private static var listening = false

    /// The listening run, which is the one surface that takes the audio OVER
    /// instead of mixing into it: `.playback` in `.spokenAudio` mode and
    /// deliberately WITHOUT `.mixWithOthers`, so whatever podcast was playing
    /// stops rather than plays under the words. A mode meant to be listened to
    /// that lands on top of something else is a mode nobody hears — it
    /// interrupts as a player, not as a notification (`docs/read-aloud.md`).
    ///
    /// Activated by hand, unlike every other sound in the app: a run keeps
    /// playing with the screen locked, and background audio needs a session
    /// that is actually active rather than one the engine activates per word.
    static func useListening() {
        guard !listening else { return }
        listening = true
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .spokenAudio, options: [])
        try? session.setActive(true)
    }

    /// After a phone call or a Siri turn took the session away: the category is
    /// still ours, the ACTIVATION is not, and nothing sounds until it is asked
    /// for again. Not `useListening`, which is a no-op once the run holds the
    /// session — this is the one thing that has to be redone.
    static func resumeListening() {
        guard listening else { return }
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    /// The run is over: hand the audio back. `notifyOthersOnDeactivation` is
    /// what makes the interrupted podcast resume, and the standing category is
    /// put back so the next tap or chime finds the session it expects.
    static func endListening() {
        guard listening else { return }
        listening = false
        let session = AVAudioSession.sharedInstance()
        try? session.setActive(false, options: .notifyOthersOnDeactivation)
        try? session.setCategory(standing,
                                 options: standing == .playback ? [.mixWithOthers] : [])
    }

    /// why: `.mixWithOthers` under `.playback` — that category interrupts other
    /// audio by default, and an app that stops someone's music to say one word
    /// is not what `.ambient` promised. The option is rejected on `.ambient`,
    /// which mixes inherently, so it is passed only where it is legal.
    private static func use(_ category: AVAudioSession.Category) {
        // why: a run owns the session for its whole length — a stray fire from
        // under the cover must not hand the audio back mid-turn.
        guard !listening else { return }
        let session = AVAudioSession.sharedInstance()
        // why: a redundant set still posts a route change; a run of autoplays
        // must not pay one per word.
        guard session.category != category else { return }
        try? session.setCategory(category, options: category == .playback ? [.mixWithOthers] : [])
    }
}

// MARK: - ReadAloud

/// The read-aloud setting, three states rather than a flag — the middle one is
/// the default, and the reason a switch of our own still earns its place next
/// to the phone's.
enum ReadAloud: String {
    /// Untouched: the app reads aloud and the silent switch may silence it.
    /// Fresh installs start here, which is why reading aloud is on by default
    /// without ever overruling a phone that was set to quiet.
    case followsPhone
    /// Silenced in the app, whatever the phone says.
    case off
    /// Asked for by hand: read aloud through a silenced phone too.
    case on
}

extension ReadAloud {

    private static let key = "readAloud"
    /// The boolean this setting grew out of — a device that had muted keeps
    /// its silence across the split.
    private static let legacyMutedKey = "pronunciationMuted"

    static var stored: ReadAloud {
        if let raw = UserDefaults.standard.string(forKey: key),
           let stored = ReadAloud(rawValue: raw) {
            return stored
        }
        return UserDefaults.standard.bool(forKey: legacyMutedKey) ? .off : .followsPhone
    }

    func store() {
        UserDefaults.standard.set(rawValue, forKey: Self.key)
    }
}
