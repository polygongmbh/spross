import AVFoundation

/// Plays one bundled recording at a time.
///
/// The audio session is configured once at launch (`SprossApp.init`,
/// `.ambient`) and NEVER activated by hand: `setActive` is main-thread
/// synchronous, and the review loop cannot afford a synchronous hitch on a
/// transition that carries the keyboard. Activation stays implicit — see
/// `warmUp(url:)` for where the first one is paid.
@MainActor
final class PronunciationPlayer: NSObject {

    private var player: AVAudioPlayer?
    /// Probe-only completion (§9); gameplay playback is fire-and-forget.
    private var onFinish: (@MainActor () -> Void)?
    private var warmUpPlayer: AVAudioPlayer?

    /// Plays `url`, replacing whatever was sounding. A file that will not open
    /// simply stays silent — the word is never worth an error surface.
    func play(url: URL, onFinish: (@MainActor () -> Void)? = nil) {
        stop()
        guard let next = try? AVAudioPlayer(contentsOf: url) else { return }
        next.delegate = self
        player = next
        self.onFinish = onFinish
        next.play()
    }

    func stop() {
        onFinish = nil
        player?.stop()
        player = nil
    }

    /// Pays the process's FIRST audio-session activation — implicit, on the
    /// main thread — with an inaudible clip, so it never lands on a produce
    /// reveal with the keyboard up and the answer field focused (the focus
    /// discipline in design.md is fragile about exactly that moment).
    /// Once per process; later calls do nothing.
    func warmUp(url: URL) {
        guard warmUpPlayer == nil, let clip = try? AVAudioPlayer(contentsOf: url) else { return }
        clip.volume = 0
        warmUpPlayer = clip
        clip.play()
    }
}

// MARK: - AVAudioPlayerDelegate

extension PronunciationPlayer: AVAudioPlayerDelegate {
    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor [weak self] in
            guard let self else { return }
            let finished = onFinish
            onFinish = nil
            self.player = nil
            finished?()
        }
    }
}
