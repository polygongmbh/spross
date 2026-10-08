import Foundation
import SprossKern
import UIKit

/// The one way anything in the app says a target word out loud: review cards,
/// drills and the letter drill all go through here, so kern's mute and
/// VoiceOver gate (`PronounceTrigger.held`) and its branch (`soundBranch`) are
/// asked in a single place.
///
/// Kern decides WHAT to say (`Pronunciation`: the form, the utterance, and the
/// catalog-relative path of a matching recording); this decides WHETHER and
/// WITH WHAT.
@Observable
@MainActor
final class Pronouncer {

    static let shared = Pronouncer()

    /// Where a fire came from — kern's, with the rule on what holds each back.
    typealias Trigger = PronounceTrigger

    /// One device-wide setting (never per target language, never in the box):
    /// governs AUTOPLAY only, and every launch starts at `.followsPhone`, so
    /// reading aloud is on while the silent switch keeps its say.
    var readAloud: ReadAloud {
        didSet {
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

    /// Which voice answers a target word — recordings or the synthesizer —
    /// per LANGUAGE: the picked source belongs to the language it was picked
    /// for, so a pack worth hearing in one target does not silence a better
    /// system voice in the next. The read-aloud switch above only mutes, it
    /// never changes this. Holds only what this launch has SET; everything
    /// else answers from `VoiceSource.stored(for:)`, and reading it here is
    /// what makes the picker follow a change.
    private var pickedSources: [String: VoiceSource] = [:]

    /// Whether a review card also says the learner's own side — the meaning it
    /// asks by or reveals. One device-wide choice, on until turned off; the
    /// mute silences it with everything else.
    var saysMeaning: Bool = UserDefaults.standard.object(forKey: "audio.saysMeaning") as? Bool ?? true {
        didSet { UserDefaults.standard.set(saysMeaning, forKey: "audio.saysMeaning") }
    }

    private let player = PronunciationPlayer()
    private let speaker = Speaker()

    /// Identity of the pronunciation sounding right now, `lang|form` — nil
    /// when nothing is. Only one word plays at a time, but a screen can show
    /// several (the catalog list), so a UI icon compares its own key against
    /// this to know whether IT is the one pulsing.
    private(set) var playingKey: String?

    init() {
        readAloud = .atLaunch
    }

    /// The source in force for `language`.
    func voiceSource(for language: String) -> VoiceSource {
        pickedSources[language] ?? .stored(for: language)
    }

    func setVoiceSource(_ source: VoiceSource, for language: String) {
        pickedSources[language] = source
        source.store(for: language)
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

    /// A voice exists for `language`, and it is the compact one iOS ships with
    /// — the only state where pointing at the voice download helps. A language
    /// with no voice at all has nothing to upgrade to, and one already on
    /// `.enhanced`/`.premium` is done.
    func hasOnlyBasicVoice(language: String) -> Bool {
        speaker.voiceQuality(for: language) == .default
    }

    /// Whether this form can be heard at all — gates the tap-to-replay
    /// affordance so a word with neither a recording nor a voice grows no
    /// gesture that does nothing.
    func canPronounce(_ pronunciation: Pronunciation, recordingURL: URL?) -> Bool {
        recordingURL != nil || canSpeak(language: pronunciation.lang)
    }

    /// Says the form: the recording when one matched, else the live voice.
    ///
    /// `article` is the TARGET-side article the synthesizer says in front of the
    /// word, already vetted by `shownArticle` — a rotated synonym arrives here
    /// as nil. It reaches the live voice ONLY (`spokenTargetForm`): a bundled
    /// recording says what was recorded, and re-cutting one is an edit to bytes
    /// the app never edits (`docs/read-aloud.md`).
    ///
    /// `fadeDb` is the listening run's bedtime ramp and 0 everywhere else;
    /// `onFinish` is what lets a run arm its next beat off this one.
    /// `recordingOnly` skips the Speech preference: the credits screen plays
    /// the file it credits, never a voice in its place.
    func pronounce(_ pronunciation: Pronunciation, recordingURL: URL?, trigger: Trigger,
                   article: String? = nil, fadeDb: Double = 0,
                   recordingOnly: Bool = false,
                   onFinish: (@MainActor () -> Void)? = nil) {
        if trigger.held(muted: muted, readsScreenAloud: UIAccessibility.isVoiceOverRunning) { return }
        switch trigger {
        case .auto:
            AudioSession.useStanding()
        case .listening:
            // why: the run took the audio OVER for its whole length
            // (`AudioSession.useListening`), and a per-word category would hand
            // it back mid-turn.
            break
        case .essential, .tap:
            // why: the category is what lets a request past the phone's switch.
            AudioSession.useExplicit()
        }
        // why: one word at a time — a new fire replaces whatever is sounding.
        stop()
        let key = Self.key(for: pronunciation)
        let lang = pronunciation.lang
        let prefersSpeech = voiceSource(for: lang) == .tts
        var branch = soundBranch(prefersSpeech: prefersSpeech, recordingOnly: recordingOnly,
                                 hasVoice: canSpeak(language: lang),
                                 hasRecording: recordingURL != nil)
        if branch == .recording, let recordingURL {
            // why: the loudness and the dead air are the catalog's MEASUREMENTS
            // of bytes that stay the untouched transcode — playback is the one
            // place they are ever applied, and never the file.
            playingKey = key
            let started = player.play(url: recordingURL, gainDb: pronunciation.gain, capDb: pronunciation.cap,
                                      leadMs: pronunciation.leadMs,
                                      gate: pronunciation.gate?.doubleValue, fadeDb: fadeDb) { [weak self] in
                self?.clearPlaying(key)
                onFinish?()
            }
            if started { return }
            playingKey = nil
            branch = soundBranch(prefersSpeech: prefersSpeech, recordingOnly: recordingOnly,
                                 hasVoice: canSpeak(language: lang), hasRecording: false)
            // why: a recording that failed to open still ends, so a listening run moves on.
            if branch != .speech { onFinish?() }
        }
        guard branch == .speech else { return }
        say(key: key, text: spoken(pronunciation, article: article),
            language: lang, fadeDb: fadeDb, onFinish: onFinish)
    }

    /// The synthesized branch — the only one the article reaches.
    private func say(key: String, text: String, language: String, fadeDb: Double,
                     onFinish: (@MainActor () -> Void)?) {
        playingKey = key
        speaker.speak(text, language: language,
                      volume: Self.volume(fadeDb: fadeDb)) { [weak self] in
            self?.clearPlaying(key)
            onFinish?()
        }
    }

    /// What the live voice is handed: the form with its article where the card
    /// has one to say, kern's string in both cases (`spokenTargetForm` folds in
    /// `utterance`'s stem trim, so a prefixed form can never skip it).
    private func spoken(_ pronunciation: Pronunciation, article: String?) -> String {
        guard let article else { return pronunciation.utterance }
        // why: form for both — the caller already ran `shownArticle` against the
        // canonical target text, and passing the form as its own canonical says
        // "this one is spoken with the article" without re-deciding it here.
        return spokenTargetForm(article: article,
                                shownForm: pronunciation.form,
                                targetText: pronunciation.form)
    }

    /// The level as the linear factor `AVSpeechUtterance.volume` wants — the
    /// synthesized twin of the equalizer's gain, so the output level and one
    /// fade reach both branches alike.
    private static func volume(fadeDb: Double) -> Float {
        Float(Playback.shared.linear(db: fadedGainDb(gainDb: 0, capDb: 0, fadeDb: fadeDb)))
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
            player.play(url: recordingURL, gainDb: pronunciation.gain,
                        leadMs: pronunciation.leadMs, gate: pronunciation.gate?.doubleValue) {
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
