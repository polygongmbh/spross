import AVFoundation
import Foundation
import SprossKern

/// The listening run, driven: kern's reducer decides the whole playlist — what
/// is drawn, what is held back, what a pause does — and this turns the effects
/// it hands back into sound.
///
/// It touches NO box state: nothing is answered here, so no review is booked,
/// no streak moves and no card changes phase. The run state carries no
/// `BoxState` at all (`ListeningRun`), which is what makes that structural
/// rather than promised; all this side owes is the same restraint.
///
/// Every beat is armed off the PREVIOUS one's completion plus kern's gap, never
/// off a fixed schedule — a word lasts as long as it lasts, and a recording and
/// a synthesized reading do not last the same. `generation` is the guard that
/// mirrors `PronunciationPlayer.playback`: a skip, a pause or a close raises it,
/// and every armed beat checks it before it fires, so an interleaving tap can
/// never leave two chains walking the playlist at once.
@MainActor
@Observable
final class ListeningDriver {

    /// How long a beat may wait for a completion that never comes. Both audio
    /// branches CAN return silently — a file that will not open, a voice that
    /// went missing between the pool and the word — and a locked phone that
    /// went quiet has no way to say so. Longer than any word, shorter than a
    /// learner's patience.
    private static let watchdog: Duration = .seconds(20)

    private(set) var state: ListeningRunState
    /// The sleep timer, whole (`ListeningBedtime`) — the chip reads it, the run
    /// only asks it how loud to play and when to stop.
    let bedtime = ListeningBedtime()
    /// The bedtime arrived, or the pool was empty: the view leaves.
    private(set) var closed = false
    /// Whether this turn's meaning has been said yet — the card face follows the
    /// SOUND rather than a timer of its own, so what is read and what is heard
    /// arrive together.
    private(set) var revealed = false

    private let model: AppModel
    private var generation = 0
    /// The gap between two beats — cancelled whenever the generation turns.
    private var pending: Task<Void, Never>?
    private var interruption: NSObjectProtocol?

    init(model: AppModel) {
        self.model = model
        state = ListeningRun.shared.idle(candidates: ListeningAvailability(model: model).candidates)
        // why: the bedtime is a clock, not a decision — when it arrives the run
        // is simply over, wherever the beat chain had got to.
        bedtime.onExpire = { [weak self] in self?.expire() }
    }

    // MARK: - The run

    /// Takes the audio over, hangs the lock-screen controls, and starts.
    func open() {
        AudioSession.useListening()
        NowPlaying.shared.take(commands: .init(
            toggle: { [weak self] in self?.dispatch(ListeningIntent.TogglePause.shared) },
            next: { [weak self] in self?.dispatch(ListeningIntent.Skip.shared) },
            again: { [weak self] in self?.dispatch(ListeningIntent.Repeat.shared) }
        ))
        watchInterruptions()
        dispatch(ListeningIntent.Start.shared)
    }

    /// A phone call, a Siri turn, an alarm: the system takes the session, every
    /// beat after it would fall silent, and nothing announces the return. So the
    /// run PAUSES on the interruption and picks the current word up again where
    /// the system says it may — which is also what the lock screen's own play
    /// button would then do, by hand.
    private func watchInterruptions() {
        guard interruption == nil else { return }
        interruption = NotificationCenter.default.addObserver(
            forName: AVAudioSession.interruptionNotification, object: nil, queue: .main
        ) { [weak self] note in
            // why: the raw numbers cross the hop, never the Notification — it
            // carries a userInfo nobody can promise is Sendable.
            let kind = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt
            let options = note.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt
            MainActor.assumeIsolated { self?.interrupted(kind: kind, options: options) }
        }
    }

    private func interrupted(kind: UInt?, options: UInt?) {
        guard let type = kind.flatMap(AVAudioSession.InterruptionType.init(rawValue:)) else { return }
        switch type {
        case .began:
            if !state.paused { dispatch(ListeningIntent.TogglePause.shared) }
        case .ended:
            let resume = options.map(AVAudioSession.InterruptionOptions.init(rawValue:)) ?? []
            guard resume.contains(.shouldResume) else { return }
            AudioSession.resumeListening()
            if state.paused { dispatch(ListeningIntent.TogglePause.shared) }
        @unknown default:
            break
        }
    }

    /// Hands everything back: the run, the sound, the lock screen and the audio
    /// session — in that order, so nothing fires into a session already gone.
    func close() {
        dispatch(ListeningIntent.Close.shared)
        bedtime.stop()
        if let interruption {
            NotificationCenter.default.removeObserver(interruption)
            self.interruption = nil
        }
        NowPlaying.shared.release()
        AudioSession.endListening()
    }

    func togglePause() { dispatch(ListeningIntent.TogglePause.shared) }
    func skip() { dispatch(ListeningIntent.Skip.shared) }
    func again() { dispatch(ListeningIntent.Repeat.shared) }

    /// The bedtime arrived: the run is over and the screen leaves with it.
    private func expire() {
        close()
        closed = true
    }

    // MARK: - Kern

    private func dispatch(_ intent: ListeningIntent) {
        let reduction = ListeningRun.shared.reduce(state: state, intent: intent, rng: drillRandom)
        state = reduction.state
        // why: the EFFECTS, never a diff of the turn — Repeat leaves the state
        // identical and its only observable is the Play it asks for.
        for effect in reduction.effects { apply(effect) }
        NowPlaying.shared.show(turn: state.turn, paused: state.paused)
        // An empty pool opens on silence rather than on a card with nothing to
        // say; the Heute card gates on the same report, so this is a closed
        // door and not a screen.
        if state.active, state.turn == nil { closed = true }
    }

    private func apply(_ effect: ListeningEffect) {
        switch onEnum(of: effect) {
        case .play(let effect): play(effect.turn)
        case .stop: silence()
        }
    }

    // MARK: - One turn, three beats

    /// What one beat says and how long the run waits after it. The forms and
    /// every gap are kern's; all that is decided here is which language each
    /// side is spoken in.
    private struct Beat {
        let form: String
        let lang: String
        /// Target-side only: the meaning is the learner's own language, whose
        /// grammar is not what is being taught (`docs/read-aloud.md`).
        let article: String?
        let gapMs: Int64
        /// The meaning beat, which is where the card gives its answer.
        var reveals = false
    }

    private func beats(of turn: ListeningTurn) -> [Beat] {
        let target = model.targetLanguage ?? ""
        let source = model.sourceLanguage
        return [
            Beat(form: turn.targetForm, lang: target,
                 article: turn.spokenArticle, gapMs: turn.recallGapMs),
            Beat(form: turn.sourceForm, lang: source, article: nil,
                 gapMs: turn.echoGapMs, reveals: true),
            // why: the second saying of the word is where it and its meaning
            // meet — the beat the whole mode is for.
            Beat(form: turn.targetForm, lang: target,
                 article: turn.spokenArticle, gapMs: turn.turnGapMs),
        ]
    }

    private func play(_ turn: ListeningTurn) {
        generation += 1
        revealed = false
        walk(beats(of: turn), from: 0, generation: generation)
    }

    /// Says one beat, waits kern's gap on its completion, and walks on; past the
    /// last beat the turn is over and the reducer draws the next one.
    private func walk(_ beats: [Beat], from index: Int, generation gen: Int) {
        guard gen == generation, !bedtime.expired else { return }
        guard index < beats.count else {
            dispatch(ListeningIntent.Advance.shared)
            return
        }
        let beat = beats[index]
        if beat.reveals { revealed = true }
        say(beat, generation: gen) { [weak self] in
            self?.wait(beat.gapMs, generation: gen) {
                self?.walk(beats, from: index + 1, generation: gen)
            }
        }
    }

    private func say(_ beat: Beat, generation gen: Int, then next: @escaping () -> Void) {
        pending?.cancel()
        let once = Once { [weak self] in
            guard let self, gen == self.generation else { return }
            next()
        }
        guard let pronunciation = model.formPronunciation(beat.form, lang: beat.lang) else {
            once.fire()
            return
        }
        Pronouncer.shared.pronounce(pronunciation,
                                    recordingURL: model.audioURL(pronunciation.recordingPath),
                                    trigger: .listening, article: beat.article,
                                    fadeDb: bedtime.fadeDb, onFinish: { once.fire() })
        // why: a completion that never arrives would leave a pocketed phone
        // silent with no way to say so — the run walks on rather than stalls.
        pending = Task { @MainActor in
            try? await Task.sleep(for: Self.watchdog)
            guard !Task.isCancelled else { return }
            once.fire()
        }
    }

    private func wait(_ gapMs: Int64, generation gen: Int, then next: @escaping () -> Void) {
        pending?.cancel()
        pending = Task { @MainActor [weak self] in
            try? await Task.sleep(for: .milliseconds(gapMs))
            guard !Task.isCancelled, let self, gen == generation else { return }
            next()
        }
    }

    /// Silence, whatever it was asked for: the chain is abandoned by turning the
    /// generation, so a completion still in flight answers to nobody.
    private func silence() {
        generation += 1
        pending?.cancel()
        pending = nil
        Pronouncer.shared.stop()
    }
}

/// A callback that answers at most once, however many ways it is asked — the
/// completion and its watchdog both call the same thing and only one of them
/// may walk the chain on.
@MainActor
private final class Once {
    private var action: (() -> Void)?

    init(_ action: @escaping () -> Void) { self.action = action }

    func fire() {
        let action = self.action
        self.action = nil
        action?()
    }
}
