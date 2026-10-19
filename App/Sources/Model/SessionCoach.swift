import SwiftUI
import SprossKern

/// The words for kern's `CoachLine`s — which line stands where is `Coaching`'s.
///
/// It runs for the one round onboarding opened — all of it,
/// including whatever "Weiter üben" adds to that same run.
/// The flag behind it (`AppModel.coachPending`) is memory only,
/// so an app killed mid-round comes back without the lines; spending it is `closeSession`'s.
enum SessionCoach {

    static func key(_ line: CoachLine) -> LocalizedStringKey {
        switch line {
        case .recognize: "session.coach.recognize"
        case .grade: "session.coach.grade"
        case .write: "session.coach.write"
        }
    }
}
