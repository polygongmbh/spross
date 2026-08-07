import SwiftUI

// MARK: - Drill chrome
//
// What the two endless drills — the slot drill and the letter drill — put
// around whatever they happen to be asking. Their state machines stay apart
// (a heard glyph and a typed numeral share no grammar); only the frame does.

extension SessionScaffold {
    /// The chrome of an ENDLESS run, which has no total to count toward.
    /// Position and total move together, so the bar fills as the run grows
    /// instead of breaking past a fixed end, and the counter reads
    /// "clean/answered" rather than "position/total".
    static func endless(answered: Int,
                        outcomes: [SessionOutcome],
                        showsMuteButton: Bool = false,
                        onClose: @escaping () -> Void,
                        @ViewBuilder content: () -> Content) -> SessionScaffold {
        SessionScaffold(position: answered + 1,
                        total: answered + 1,
                        outcomes: outcomes,
                        counter: "\(outcomes.filter { $0 != .wrong }.count)/\(answered)",
                        showsMuteButton: showsMuteButton,
                        onClose: onClose,
                        content: content)
    }
}
