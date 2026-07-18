import AudioToolbox
import UIKit

// MARK: - DLSound
//
// Tiny feedback layer for the review loop: one soft system sound plus a
// matching haptic per event. System sounds follow the ring/silent switch
// by default (deliberately no AVAudioSession override) — the haptic still
// carries the feedback when the phone is muted.

enum DLSound {

    /// SIMToolkitPositiveACK — short, friendly rising double-tone.
    private static let correctID: SystemSoundID = 1054
    /// SIMToolkitNegativeACK — soft low tone, informative rather than punishing.
    private static let wrongID: SystemSoundID = 1053
    /// Tink — tiny neutral tap for revealing the answer.
    private static let revealID: SystemSoundID = 1057

    static func correct() {
        AudioServicesPlaySystemSound(correctID)
    }

    static func wrong() {
        AudioServicesPlaySystemSound(wrongID)
        // why: a single light tap on a wrong answer only — a gentle wake-up
        // cue, not the punishing `.error` buzz; correct answers stay haptic-free.
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    static func reveal() {
        AudioServicesPlaySystemSound(revealID)
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    #if DEBUG
    /// UI-test hook (`-uitest-sound 1`): plays each sound staggered with a
    /// completion probe. A firing completion means the system resolved the
    /// sound id and played it through — the audibility proxy in the simulator.
    static func uitestProbe() {
        let probes: [(String, SystemSoundID)] =
            [("correct", correctID), ("wrong", wrongID), ("reveal", revealID)]
        for (index, probe) in probes.enumerated() {
            DispatchQueue.main.asyncAfter(deadline: .now() + Double(index) * 0.8) {
                // why: AudioToolbox calls the completion on its own queue —
                // the closure must not assume main-actor isolation.
                AudioServicesPlaySystemSoundWithCompletion(probe.1) { @Sendable in
                    print("DLSound probe: \(probe.0) (id \(probe.1)) played to completion")
                }
            }
        }
    }
    #endif
}
