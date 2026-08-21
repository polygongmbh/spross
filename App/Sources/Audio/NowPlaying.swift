import MediaPlayer
import SprossKern

/// The listening run as the SYSTEM sees it: the word on the lock screen, and
/// the transport controls there and on a headphone button.
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

    static let shared = NowPlaying()

    private var commands: Commands?
    private var paused = false

    /// Hangs the controls. Idempotent: every target is dropped first, so a run
    /// opened twice never leaves the last one's closures on the command center.
    func take(commands: Commands) {
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

    /// The turn on the lock screen: the word as the title with the article the
    /// voice says, its meaning as the artist. A nil turn clears the card
    /// entirely rather than leaving the last word standing over silence.
    func show(turn: ListeningTurn?, paused: Bool) {
        self.paused = paused
        let center = MPNowPlayingInfoCenter.default()
        guard let turn else {
            center.nowPlayingInfo = nil
            center.playbackState = .stopped
            return
        }
        let title = turn.spokenArticle.map { "\($0) \(turn.targetForm)" } ?? turn.targetForm
        center.nowPlayingInfo = [
            MPMediaItemPropertyTitle: title,
            MPMediaItemPropertyArtist: turn.sourceForm,
            // why: a playlist that laps for as long as it is left alone has no
            // duration and no position — a scrubber over it would be a lie.
            MPNowPlayingInfoPropertyIsLiveStream: true,
            MPNowPlayingInfoPropertyPlaybackRate: paused ? 0.0 : 1.0,
        ]
        center.playbackState = paused ? .paused : .playing
    }

    /// The run is over: the card goes and the controls answer to nobody again.
    func release() {
        commands = nil
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
}
