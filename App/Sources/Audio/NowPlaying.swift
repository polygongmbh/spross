import MediaPlayer
import SprossKern

/// The listening run as the SYSTEM sees it: the card on the lock screen and in
/// the Dynamic Island, and the transport controls there and on a headphone
/// button.
///
/// They drive the very same reducer the on-screen buttons drive — a mode meant
/// for a pocket cannot have two ideas of what "next" means. Nothing here holds
/// any run state of its own beyond what it was last shown, so it can never
/// disagree with the run about what is playing.
@MainActor
final class NowPlaying {

    /// What the lock screen may ask for. Play and pause arrive as two commands
    /// where kern has one intent, so this side holds the last known `paused` and
    /// only toggles where it would actually change something — a play button
    /// that pauses is worse than a play button that does nothing.
    struct Commands {
        let toggle: () -> Void
        let next: () -> Void
        let again: () -> Void
    }

    /// What the run IS, for as long as it runs: the app and the mode over the
    /// pair of languages the box joins.
    ///
    /// It is the card's headline because it is the part that HOLDS STILL. A
    /// title that renamed itself every few seconds is a card nobody can read
    /// from a pocket or a car dashboard, and the system treats a changed title
    /// as a changed track; the word that is playing goes on the line beneath,
    /// where it belongs — a track inside the run rather than the run itself.
    struct Run {
        /// "Spross · Wörter hören".
        let title: String
        /// "Deutsch – Suaheli", known first: the pair, in the order the mode
        /// says them.
        let languages: String
    }

    /// How long a bedtime has been running and how long it runs for — the pair
    /// the lock screen draws its progress bar from. A run without one has none:
    /// a playlist that laps has nothing to be a fraction of.
    typealias Bedtime = (elapsed: TimeInterval, total: TimeInterval)

    static let shared = NowPlaying()

    private var commands: Commands?
    private var run: Run?
    private var paused = false
    /// Built once and held: the icon is decoded from the asset catalog, and a
    /// card redrawn on every word must not pay for that three times a turn.
    private lazy var artwork: MPMediaItemArtwork? = Self.appIcon().map { icon in
        MPMediaItemArtwork(boundsSize: icon.size) { _ in icon }
    }

    /// Hangs the controls and names the run. Idempotent: every target is
    /// dropped first, so a run opened twice never leaves the last one's
    /// closures on the command center.
    func take(run: Run, commands: Commands) {
        self.run = run
        self.commands = commands
        let center = MPRemoteCommandCenter.shared()
        clear(center)
        center.togglePlayPauseCommand.addTarget { [weak self] _ in
            self?.commands?.toggle()
            return .success
        }
        center.playCommand.addTarget { [weak self] _ in
            guard let self else { return .commandFailed }
            if paused { commands.toggle() }
            return .success
        }
        center.pauseCommand.addTarget { [weak self] _ in
            guard let self else { return .commandFailed }
            if !paused { commands.toggle() }
            return .success
        }
        center.nextTrackCommand.addTarget { [weak self] _ in
            self?.commands?.next()
            return .success
        }
        // why: there is no track behind this one — a playlist that laps has no
        // "back", and the nearest thing a learner reaching for it wants is the
        // word they just heard, said again.
        center.previousTrackCommand.addTarget { [weak self] _ in
            self?.commands?.again()
            return .success
        }
        for command in [center.togglePlayPauseCommand, center.playCommand, center.pauseCommand,
                        center.nextTrackCommand, center.previousTrackCommand] {
            command.isEnabled = true
        }
    }

    /// The card: the run's own name and its languages, the app icon beside
    /// them, and the word in the air on the album line — the TARGET word, with
    /// the article the voice says, so what is read and what is heard cannot
    /// disagree. A nil turn clears the card entirely rather than leaving the
    /// last word standing over silence.
    func show(turn: ListeningTurn?, paused: Bool, bedtime: Bedtime?) {
        self.paused = paused
        let center = MPNowPlayingInfoCenter.default()
        guard let turn, let run else {
            center.nowPlayingInfo = nil
            center.playbackState = .stopped
            return
        }
        let word = turn.spokenArticle.map { "\($0) \(turn.targetForm)" } ?? turn.targetForm
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: run.title,
            MPMediaItemPropertyArtist: run.languages,
            MPMediaItemPropertyAlbumTitle: word,
            MPNowPlayingInfoPropertyPlaybackRate: paused ? 0.0 : 1.0,
        ]
        if let artwork { info[MPMediaItemPropertyArtwork] = artwork }
        if let bedtime {
            // why: a bedtime is the one thing this run has a LENGTH of, so it is
            // the one thing worth a progress bar — how much of it has played and
            // how much is left, without opening the app to read the chip.
            info[MPMediaItemPropertyPlaybackDuration] = bedtime.total
            info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = bedtime.elapsed
        } else {
            // why: a playlist that laps for as long as it is left alone has no
            // duration and no position — a scrubber over it would be a lie.
            info[MPNowPlayingInfoPropertyIsLiveStream] = true
        }
        center.nowPlayingInfo = info
        center.playbackState = paused ? .paused : .playing
    }

    /// The run is over: the card goes and the controls answer to nobody again.
    func release() {
        commands = nil
        run = nil
        clear(MPRemoteCommandCenter.shared())
        let center = MPNowPlayingInfoCenter.default()
        center.nowPlayingInfo = nil
        center.playbackState = .stopped
    }

    private func clear(_ center: MPRemoteCommandCenter) {
        for command in [center.togglePlayPauseCommand, center.playCommand, center.pauseCommand,
                        center.nextTrackCommand, center.previousTrackCommand] {
            command.removeTarget(nil)
        }
    }

    /// The app icon, which is the only artwork a run over the learner's OWN
    /// words could have — there is no cover for a playlist the box composed.
    ///
    /// The asset catalog answers first and answers at full size; the bundle's
    /// rendered icon files are the fallback, and they are named in `Info.plist`
    /// rather than guessed at.
    private static func appIcon() -> UIImage? {
        if let icon = UIImage(named: "AppIcon") { return icon }
        guard let icons = Bundle.main.infoDictionary?["CFBundleIcons"] as? [String: Any],
              let primary = icons["CFBundlePrimaryIcon"] as? [String: Any],
              let files = primary["CFBundleIconFiles"] as? [String],
              let last = files.last
        else { return nil }
        return UIImage(named: last)
    }
}
