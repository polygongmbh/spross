import AVFoundation
import SprossKern

/// Plays one bundled recording at a time, under the catalog's ANALYSIS INDEX:
/// `gainDb` decibels from the analysis target, and `leadMs` of dead air to
/// skip at its head.
///
/// why an engine and not `AVAudioPlayer`: what ships is the untouched Commons
/// transcode — re-encoding it is an adaptation under BY-SA — so the loudness
/// the packs never agreed on is corrected HERE, at playback, out of our own
/// measurement of those bytes. The uk letters need up to +20 dB of it, and
/// `AVAudioPlayer.volume` only ever attenuates; it cannot start a file late
/// either. So: one player node through one EQ into the main mixer, and
/// nothing else.
///
/// The audio session's CATEGORY is chosen per fire (`AudioSession`) — that is
/// the only lever over the ring/silent switch — but the session is NEVER
/// activated by hand: `setActive` is main-thread synchronous, and the review
/// loop cannot afford a synchronous hitch on a transition that carries the
/// keyboard. Activation stays implicit — starting the engine performs it — see
/// `warmUp(url:)` for where the first one is paid.
@MainActor
final class PronunciationPlayer {

    /// How often one word may be put back. A tap costs one rebuild; a second is
    /// a device changing route under a learner, and a third is a fight nobody
    /// wins by restarting.
    private static let rearmLimit = 2

    private let engine = AVAudioEngine()
    private let node = AVAudioPlayerNode()
    private let equalizer = AVAudioUnitEQ()
    /// The ONE format the graph is wired in. An effect that is already running
    /// refuses to be re-wired into another one (`-10868`), so the wiring is
    /// fixed and the player node converts each file into it — measured: the
    /// mono warm-up clip arrives at its own level, unchanged. All 1126 shipped
    /// recordings are 44.1 kHz stereo and convert not at all. nil is
    /// unreachable; then nothing ever sounds.
    private let wiring = AVAudioFormat(standardFormatWithSampleRate: 44_100, channels: 2)
    /// Probe-only completion (§9); gameplay playback is fire-and-forget.
    private var onFinish: (@MainActor () -> Void)?
    /// Which playback a pending completion belongs to — a scheduled segment
    /// calls back after `stop()` as well, and that one answers to nobody.
    private var playback = 0
    private var warmedUp = false
    /// The word in the air, kept whole so it can be put back. The engine's I/O
    /// is rebuilt under us on a route change — headphones, and now the category
    /// flip a tap performs (`AudioSession`) — which drops a segment scheduled a
    /// moment earlier without it ever sounding. That is a word that has to be
    /// tapped twice: the first tap paid for the route change, the second found
    /// the session already right. Recovery used to be the NEXT word; it is this
    /// one.
    private var pending: Request?
    /// Re-arms spent on `pending` — a rebuild that arrives in a run must not
    /// become a loop of restarts.
    private var rearms = 0

    private struct Request {
        let url: URL
        let gainDb: Double
        let leadMs: Int64
        /// The listening run's bedtime ramp, applied OVER the analysis index —
        /// see `play(url:gainDb:leadMs:fadeDb:onFinish:)`.
        let fadeDb: Double
        let onFinish: (@MainActor () -> Void)?
    }

    /// Builds the graph and only the graph: nothing here touches the audio
    /// session, so a screen that merely reads the mute flag pays nothing.
    init() {
        guard let wiring else { return }
        engine.attach(node)
        engine.attach(equalizer)
        engine.connect(node, to: equalizer, format: wiring)
        engine.connect(equalizer, to: engine.mainMixerNode, format: wiring)
        // why: the engine stops itself when its I/O is rebuilt and takes any
        // scheduled segment with it — the word is put back here rather than
        // left for the next one to recover.
        NotificationCenter.default.addObserver(
            forName: .AVAudioEngineConfigurationChange, object: engine, queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.rearm() }
        }
    }

    /// Plays `url` under its analysis index, replacing whatever was sounding.
    /// A file that will not open simply stays silent — the word is never worth
    /// an error surface.
    ///
    /// `fadeDb` is the one level that is NOT a measurement: kern's bedtime ramp
    /// (`listeningGainDb`), added after the index has been clamped, because the
    /// clamp bounds how far a MEASUREMENT may be trusted and not a level kern
    /// chose. 0 everywhere outside a listening run.
    func play(url: URL, gainDb: Double = 0, leadMs: Int64 = 0, fadeDb: Double = 0,
              onFinish: (@MainActor () -> Void)? = nil) {
        rearms = 0
        play(Request(url: url, gainDb: gainDb, leadMs: leadMs, fadeDb: fadeDb, onFinish: onFinish))
    }

    private func play(_ request: Request) {
        stop()
        let (url, onFinish) = (request.url, request.onFinish)
        guard let file = try? AVAudioFile(forReading: url), file.length > 0, running()
        else { return }
        pending = request
        let rate = file.processingFormat.sampleRate
        // How far a player may trust the analysis index is kern's (`catalog/Playback.kt`);
        // frames and the EQ's own ±24 dB headroom are this device's business.
        let headMs = Playback.shared.headMs(leadMs: request.leadMs,
                                            durationMs: Int64(Double(file.length) / rate * 1000))
        let head = AVAudioFramePosition(Double(headMs) / 1000 * rate)
        equalizer.globalGain = Float(Playback.shared.gainDb(measured: request.gainDb) + request.fadeDb)
        self.onFinish = onFinish
        let current = playback
        node.scheduleSegment(file, startingFrame: head,
                             frameCount: AVAudioFrameCount(file.length - head), at: nil,
                             completionCallbackType: .dataPlayedBack) { [weak self] _ in
            // why: the callback lands on an audio thread, and everything it
            // reaches — the probe's line included — is the main actor's.
            Task { @MainActor in self?.finish(current) }
        }
        node.play()
    }

    func stop() {
        playback += 1
        onFinish = nil
        pending = nil
        node.stop()
    }

    /// Puts the word back after the engine's I/O was rebuilt under it. Nothing
    /// pending means nothing was dropped — a rebuild between words is exactly
    /// the case `running()` already covers.
    private func rearm() {
        guard let request = pending, rearms < Self.rearmLimit else { return }
        rearms += 1
        play(request)
    }

    /// Pays the process's FIRST audio-session activation — implicit, on the
    /// main thread, inside `AVAudioEngine.start()` — with an inaudible clip,
    /// so it never lands on a produce reveal with the keyboard up and the
    /// answer field focused (the focus discipline in design.md is fragile
    /// about exactly that moment). The clip runs the whole path, scheduling
    /// and conversion included. Once per process; later calls do nothing.
    func warmUp(url: URL) {
        guard !warmedUp else { return }
        warmedUp = true
        play(url: url)
    }

    /// Whether there is an audio path at all, starting the engine where it is
    /// not running yet. Started, it is LEFT running: stopping it between words
    /// would hand `warmUp`'s activation cost back to the very next card. A
    /// route change (headphones in, or the category a tap raises) stops the
    /// engine on its own — a word already in the air is put back by `rearm()`,
    /// and one that had not started yet finds the engine stopped here.
    private func running() -> Bool {
        guard wiring != nil else { return false }
        if engine.isRunning { return true }
        engine.prepare()
        return (try? engine.start()) != nil
    }

    /// One playback's completion, on the main actor — a stale one (a `stop()`
    /// or a newer word has happened since) stays silent.
    private func finish(_ playback: Int) {
        guard playback == self.playback else { return }
        // why: a word that has been heard is not put back by a later rebuild.
        pending = nil
        let finished = onFinish
        onFinish = nil
        finished?()
    }
}
