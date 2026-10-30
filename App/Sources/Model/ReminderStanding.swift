import Foundation
import Observation
import SprossKern
import UserNotifications

/// The practice reminders: the learner's choice as the settings row shows it,
/// and kern's plan (`Reminders`) posted as local notifications on every save of the box.
///
/// The choice lives in UserDefaults beside the profile; how loudly a reminder arrives is the system settings' to say.
@Observable
@MainActor
final class ReminderStanding {

    static let shared = ReminderStanding()

    private(set) var mode: ReminderMode
    /// The set times, as minutes after local midnight, earliest first.
    private(set) var setMinutes: [Int]
    /// Whether ignored reminders have stopped themselves (`Reminders.STOP_AFTER_DAYS`).
    private(set) var paused = false
    /// Whether the system lets the app post notifications at all.
    private(set) var allowed = true

    private init() {
        mode = Self.storedMode
        setMinutes = Self.storedMinutes
    }

    /// Takes the learner's choice, asks for the system's permission where a reminder is turned on,
    /// and arms it at once through `model`, without waiting for the next save.
    func choose(mode: ReminderMode, setMinutes: [Int], model: AppModel) {
        let sorted = Array(Set(setMinutes)).sorted()
        let defaults = UserDefaults.standard
        if self.mode == .off && mode != .off { defaults.set(Int(Date().epochMillis), forKey: Keys.enabledAt) }
        defaults.set(mode.name, forKey: Keys.mode)
        defaults.set(sorted, forKey: Keys.times)
        self.mode = mode
        self.setMinutes = sorted
        Task {
            if mode != .off {
                _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])
            }
            await refreshAllowed()
            model.replanReminders()
        }
    }

    /// why: on every return, so a permission changed in the system settings shows on the row.
    func refreshAllowed() async {
        allowed = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus != .denied
    }

    /// Posts kern's plan for `state` in place of every reminder posted before. Runs on the box store's actor.
    nonisolated static func replan(state: BoxState, boxes: StoredBoxes) {
        let defaults = UserDefaults.standard
        let plan = Reminders.shared.plan(
            mode: storedMode,
            setMinutes: storedMinutes.map { KotlinInt(int: Int32($0)) },
            enabledAtEpochMillis: Int64(defaults.integer(forKey: Keys.enabledAt)),
            state: state, boxes: boxes,
            nowEpochMillis: Date().epochMillis, tzId: currentTzId())
        let locale = Locale(identifier: LanguageChoices.shared.chromeLanguage(source: state.joinStamp.source))
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: (0..<Self.maxPending).map(Self.identifier))
        for (index, fire) in plan.fires.enumerated() {
            let content = UNMutableNotificationContent()
            content.title = ChromeStrings.string("reminder.title", locale: locale)
            content.body = fire.dueCount > 0
                ? String(format: ChromeStrings.string("reminder.body.due %lld", locale: locale),
                         locale: locale, Int(fire.dueCount))
                : ChromeStrings.string("reminder.body.fresh", locale: locale)
            content.sound = .default
            let date = Date(epochMillis: fire.atEpochMillis)
            let trigger = UNCalendarNotificationTrigger(
                dateMatching: Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: date),
                repeats: false)
            center.add(UNNotificationRequest(identifier: identifier(index), content: content, trigger: trigger))
        }
        let paused = plan.paused
        Task { @MainActor in shared.paused = paused }
    }

    private nonisolated static var storedMode: ReminderMode {
        let raw = UserDefaults.standard.string(forKey: Keys.mode)
        return [ReminderMode.usual, ReminderMode.fixed].first { $0.name == raw } ?? .off
    }

    private nonisolated static var storedMinutes: [Int] {
        UserDefaults.standard.array(forKey: Keys.times) as? [Int] ?? [19 * 60]
    }

    /// More than kern's horizon ever plans, far under the system's 64 pending.
    private nonisolated static let maxPending = 48

    private nonisolated static func identifier(_ index: Int) -> String { "reminder.\(index)" }

    private enum Keys {
        static let mode = "reminder.mode"
        static let times = "reminder.times"
        static let enabledAt = "reminder.enabledAt"
    }
}
