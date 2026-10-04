import SwiftUI
import SprossKern

/// The drilling half of the letters overview: the four formats a run walks
/// through, where this learner's run opens, and the button that starts it.
/// State lives on LettersOverview; split out purely for file size.
///
/// There is no ladder to earn here — the letter drill books no review (D12) —
/// but the circles wear its record: a format some run climbed off clean is
/// forest, and the format the run OPENS on, above the words the learner already
/// holds and above that record, is filled. Dictation exists only once enough
/// held words can be played back.
extension LettersOverview {

    var practiceSection: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.lg) {
            DrillHeading("trainer.overview.practice")
            VStack(alignment: .leading, spacing: Theme.spacing.lg) {
                ForEach(Self.formats, id: \.self) { formatRow($0) }
            }
            .panelSurface()
            startButton
            if !drillAvailable {
                Text("letters.unavailable")
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    /// The run's shape, in the order it is climbed.
    static var formats: [LetterFormat] {
        [.choiceEasy, .choiceConfusable, .typed, .dictation]
    }

    // MARK: - What a run asks

    /// One format: what it asks, and whether this run will get there. The format
    /// the run OPENS on wears the filled circle — every learner starts somewhere
    /// different, and the page should not make them guess where. The rows are
    /// not tapped: the run walks the ladder by itself from that format.
    private func formatRow(_ format: LetterFormat) -> some View {
        let open = reachable(format)
        let entry = open && format == availability?.openingFormat(cleared)
        let mark = formatMark(format, entry: entry)
        let fresh = unlocking.contains(DrillUnlockMark.shared.row(format: format))
        let step = (Self.formats.firstIndex(of: format) ?? 0) + 1
        return HStack(alignment: .center, spacing: Theme.spacing.md) {
            if open {
                UnlockingMark(fresh: fresh) { SprosseCircle(number: step, mark: mark) }
            } else {
                Image(systemName: "lock.fill")
                    .font(.title3)
                    .foregroundStyle(Theme.colors.textSecondary)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(Self.title(format))
                    .font(Theme.typography.headline)
                    .foregroundStyle(open ? Theme.colors.textPrimary : Theme.colors.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                // Only dictation states a price: where the drill cannot run at all,
                // every format is out of reach for the one reason the line under the
                // button gives.
                if !open, drillAvailable {
                    Text("letters.stage.dictation.locked")
                        .font(Theme.typography.caption)
                        .foregroundStyle(Theme.colors.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 0)
        }
        .unlockWash(fresh)
        // why: one format is one VoiceOver stop — the mark and the name describe a
        // single thing, and the value says what the filled circle says.
        .accessibilityElement(children: .combine)
        .accessibilityValue(Text(entry ? LocalizedStringKey("a11y.trainer.sprosse.entry") : (mark.a11y ?? "")))
    }

    /// Climbed off clean beats where the run opens; the rest are outlines.
    private func formatMark(_ format: LetterFormat, entry: Bool) -> SprosseMark {
        if availability?.formatCleared(format, cleared) == true { return .cleared }
        return entry ? .reached : .untouched
    }

    private static func title(_ format: LetterFormat) -> LocalizedStringKey {
        LocalizedStringKey(titleName(format))
    }

    static func titleName(_ format: LetterFormat) -> String {
        switch format {
        case .choiceEasy: return "letters.stage.choiceEasy"
        case .choiceConfusable: return "letters.stage.choiceConfusable"
        case .typed: return "letters.stage.typed"
        case .dictation: return "letters.stage.dictation"
        }
    }

    // MARK: - How far this device reaches

    /// The drill exists where the alphabet does AND this device can actually ask
    /// something: a bundled letter recording, or a voice for the language.
    /// Swahili on the iPhone has neither, so its page is the alphabet alone.
    ///
    /// It does NOT turn on reading aloud being switched on. Hiding a whole
    /// feature behind a one-tap-fixable state is how a feature stops being
    /// found; the drill says so on its own prompt card instead (§6.1).
    var drillAvailable: Bool { availability?.drillAvailable ?? false }

    /// Dictation needs a pool of playable words the learner already holds; below
    /// that floor the ramp stops one Sprosse short of it, so the row is a padlock
    /// with its price rather than a format that never arrives.
    func reachable(_ format: LetterFormat) -> Bool {
        guard let availability, availability.drillAvailable else { return false }
        return format != .dictation || availability.dictationAvailable
    }

    // MARK: - Los

    private var startButton: some View {
        Button {
            start()
        } label: {
            Text("trainer.overview.start")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(PrimaryButtonStyle())
        .disabled(!drillAvailable)
    }
}
