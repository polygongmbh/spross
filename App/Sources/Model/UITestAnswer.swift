#if DEBUG
import Foundation

/// The two launch arguments every typed-answer surface honours: `-uitest-input xyz`
/// prefills the field, `-uitest-submit 1` sends it. Three screens read them — the
/// vocab session, the slot drill and the letter drill — so the argument names and
/// the beat before the submit are stated once here rather than three times.
///
/// What each screen does with them stays its own: the session submits the card it
/// happens to be showing, the letter drill the task it opened on.
@MainActor
enum UITestAnswer {
    /// `-uitest-input xyz` — nil where the run was launched without one.
    static var prefill: String? { UserDefaults.standard.string(forKey: "uitest-input") }

    /// `-uitest-submit 1` — sends the prefilled answer after the beat the field
    /// needs to mount and take it.
    static func submitAfterBeat(_ submit: @escaping @MainActor () -> Void) {
        guard UserDefaults.standard.bool(forKey: "uitest-submit") else { return }
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(600))
            submit()
        }
    }
}
#endif
