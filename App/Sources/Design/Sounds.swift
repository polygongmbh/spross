import AudioToolbox
import UIKit

// MARK: - DLSound
//
// Tiny feedback layer for the review loop: one soft sound per event, and a
// haptic on a wrong answer only.
//
// The sounds are bundled files rather than Apple's built-in UISounds ids
// (1053/1054/1057 and friends). Those ids carry the system alert haptic with
// them on Taptic iPhones — it follows Sounds & Haptics › Haptics and there is
// no per-call opt-out, so a correct answer buzzed even though nothing here
// asked it to. Custom sounds never do that, which puts every vibration in
// this file back under our control.
//
// System sounds still follow the ring/silent switch (deliberately no
// AVAudioSession override), so a muted phone stays muted.

enum DLSound {

    /// Ascending major third — the positive confirmation people already know.
    private static let correctID = load("correct")
    /// Descending minor third: down, but consonant.
    private static let wrongID = load("wrong")
    /// One neutral note; revealing an answer is not a verdict.
    private static let revealID = load("reveal")
    /// The correct interval carried on up to the octave — the finish screen only.
    private static let cheerID = load("cheer")

    static func correct() {
        play(correctID)
    }

    static func wrong() {
        play(wrongID)
        // why: the only haptic in the app — a single light tap on a wrong
        // answer as a gentle wake-up cue, never on reveal or on a correct one.
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    static func reveal() {
        play(revealID)
    }

    static func cheer() {
        play(cheerID)
    }

    /// Registers a bundled sound once; `nil` (a missing resource) plays nothing.
    private static func load(_ name: String) -> SystemSoundID? {
        guard let url = Bundle.main.url(forResource: name, withExtension: "wav")
        else { return nil }
        var id: SystemSoundID = 0
        guard AudioServicesCreateSystemSoundID(url as CFURL, &id) == kAudioServicesNoError
        else { return nil }
        return id
    }

    private static func play(_ id: SystemSoundID?) {
        guard let id else { return }
        AudioServicesPlaySystemSound(id)
    }

    #if DEBUG
    /// UI-test hook (`-uitest-sound 1`): plays each sound staggered with a
    /// completion probe. A firing completion means the system resolved the
    /// sound id and played it through — the audibility proxy in the simulator,
    /// and the check that the files actually made it into the bundle.
    static func uitestProbe() {
        let probes: [(String, SystemSoundID?)] =
            [("correct", correctID), ("wrong", wrongID), ("reveal", revealID),
             ("cheer", cheerID)]
        for (index, probe) in probes.enumerated() {
            DispatchQueue.main.asyncAfter(deadline: .now() + Double(index) * 1.4) {
                guard let id = probe.1 else {
                    print("DLSound probe: \(probe.0) MISSING from the bundle")
                    return
                }
                // why: AudioToolbox calls the completion on its own queue —
                // the closure must not assume main-actor isolation.
                AudioServicesPlaySystemSoundWithCompletion(id) { @Sendable in
                    print("DLSound probe: \(probe.0) (id \(id)) played to completion")
                }
            }
        }
    }
    #endif
}
