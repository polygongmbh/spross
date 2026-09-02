import AVFoundation
import UIKit

// MARK: - Sound
//
// Tiny feedback layer for the review loop: one soft sound per event, and a
// haptic on a wrong answer only.
//
// The sounds are bundled files rather than Apple's built-in UISounds ids
// (1053/1054/1057 and friends). Those ids carry the system alert haptic with
// them on Taptic iPhones — it follows Sounds & Haptics › Haptics and there is
// no per-call opt-out, so a correct answer buzzed even though nothing here
// asked it to. Custom files never do that, which puts every vibration in this
// file back under our control.
//
// They are played by `AVAudioPlayer` on the app's OWN audio session, not by
// AudioToolbox's system-sound server. That server is a SEPARATE VOLUME DOMAIN:
// on iOS it follows the ringer, while everything an app plays for itself
// follows media. Nothing noticed while the app only ever chimed — the ringer
// was the only slider in play — but since the words got a voice the two are
// heard against each other, and a chime on the ringer sits below a word on
// media by whatever gap the two sliders happen to hold. At a low ringer it is
// not quiet, it is gone, and no amount of level in `scripts/sounds.py` reaches
// it. One domain for both is what makes those levels mean anything.
//
// The session is the app's own, never activated by hand, and the chimes play
// under the STANDING category (`AudioSession`) exactly as autoplay does: they
// follow the ring/silent switch the way the system-sound route used to
// guarantee, and they follow a hand-switched read-aloud past it. Only a
// deliberate tap on a word is louder than the phone — a chime never asks for
// that, because nobody ever asked for a chime.

@MainActor
enum Sound {

    /// Ascending major third — the positive confirmation people already know.
    private static let correctPlayer = load("correct")
    /// Descending minor third: down, but consonant.
    private static let wrongPlayer = load("wrong")
    /// One neutral note; revealing an answer is not a verdict.
    private static let revealPlayer = load("reveal")
    /// The correct interval carried on up to the octave — the finish screen only.
    private static let cheerPlayer = load("cheer")

    static func correct() {
        play(correctPlayer)
    }

    static func wrong() {
        play(wrongPlayer)
        // why: the only haptic in the app — a single light tap on a wrong
        // answer as a gentle wake-up cue, never on reveal or on a correct one.
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    static func reveal() {
        play(revealPlayer)
    }

    static func cheer() {
        play(cheerPlayer)
    }

    /// Loads the four players off the answering path, so the first chime of a
    /// session does not pay `prepareToPlay`'s buffer allocation on the very tap
    /// that fires it. Called beside `Pronouncer.warmUp()`, which pays the
    /// session's first activation the same way.
    static func warmUp() {
        _ = correctPlayer
        _ = wrongPlayer
        _ = revealPlayer
        _ = cheerPlayer
    }

    /// Readies a bundled sound once; `nil` (a missing resource) plays nothing.
    private static func load(_ name: String) -> AVAudioPlayer? {
        guard let url = Bundle.main.url(forResource: name, withExtension: "wav"),
              let player = try? AVAudioPlayer(contentsOf: url)
        else { return nil }
        player.prepareToPlay()
        return player
    }

    /// why: a second answer inside the first chime's tail rewinds it rather
    /// than being dropped — one player per sound, restarted on every fire.
    private static func play(_ player: AVAudioPlayer?) {
        guard let player else { return }
        AudioSession.useStanding()
        player.currentTime = 0
        player.play()
    }

    #if DEBUG
    /// UI-test hook (`-uitest-sound 1`): plays each sound staggered, reporting
    /// what the player did with it — the audibility proxy in the simulator, and
    /// the check that the files actually made it into the bundle.
    static func uitestProbe() {
        let probes: [(String, AVAudioPlayer?)] =
            [("correct", correctPlayer), ("wrong", wrongPlayer), ("reveal", revealPlayer),
             ("cheer", cheerPlayer)]
        for (index, probe) in probes.enumerated() {
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(index * 1400))
                guard let player = probe.1 else {
                    print("Sound probe: \(probe.0) MISSING from the bundle")
                    return
                }
                player.currentTime = 0
                guard player.play() else {
                    print("Sound probe: \(probe.0) REFUSED to start")
                    return
                }
                try? await Task.sleep(for: .seconds(player.duration))
                print("""
                    Sound probe: \(probe.0) played \
                    \(String(format: "%.2f", player.duration)) s to completion
                    """)
            }
        }
    }
    #endif
}
