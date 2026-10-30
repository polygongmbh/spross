import SwiftUI
import SprossKern

/// Whether and when the app reminds the learner to practice:
/// off, at the times they usually practice, or at times they set.
/// The system's permission is asked for where a reminder is turned on,
/// and how loudly one arrives is the system settings' to say.
struct SettingsRemindersRow: View {
    let model: AppModel

    private var reminders: ReminderStanding { .shared }

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            Text("settings.reminders.title")
                .font(Theme.typography.headline)
                .foregroundStyle(Theme.colors.textPrimary)
            Picker("settings.reminders.title", selection: Binding(
                get: { reminders.mode },
                set: { choose($0, reminders.setMinutes) })) {
                Text("settings.reminders.option.off").tag(ReminderMode.off)
                Text("settings.reminders.option.usual").tag(ReminderMode.usual)
                Text("settings.reminders.option.set").tag(ReminderMode.fixed)
            }
            .pickerStyle(.segmented)
            hint(hintKey)
            if reminders.mode == .fixed {
                ForEach(reminders.setMinutes, id: \.self) { minute in
                    HStack {
                        DatePicker("", selection: time(minute), displayedComponents: .hourAndMinute)
                            .labelsHidden()
                        Spacer()
                        if reminders.setMinutes.count > 1 {
                            Button {
                                choose(.fixed, reminders.setMinutes.filter { $0 != minute })
                            } label: {
                                Image(systemName: "xmark")
                                    .foregroundStyle(Theme.colors.textSecondary)
                            }
                            .accessibilityLabel(Text("settings.reminders.remove \(label(minute))"))
                        }
                    }
                }
                Button("settings.reminders.add") {
                    let last = reminders.setMinutes.last ?? 0
                    choose(.fixed, reminders.setMinutes + [(last + 3 * 60) % (24 * 60)])
                }
                .font(Theme.typography.subheadline)
            }
            if reminders.mode != .off && !reminders.allowed {
                hint("settings.reminders.hint.denied")
            } else if reminders.mode != .off && reminders.paused {
                hint("settings.reminders.hint.paused")
            }
        }
    }

    private var hintKey: LocalizedStringKey {
        switch reminders.mode {
        case .usual: return "settings.reminders.hint.usual"
        case .fixed: return "settings.reminders.hint.set"
        default: return "settings.reminders.hint.off"
        }
    }

    private func hint(_ key: LocalizedStringKey) -> some View {
        Text(key)
            .font(Theme.typography.caption)
            .foregroundStyle(Theme.colors.textSecondary)
    }

    private func choose(_ mode: ReminderMode, _ minutes: [Int]) {
        reminders.choose(mode: mode, setMinutes: minutes, model: model)
    }

    /// One set time as a date today, which is what the system time picker edits.
    private func time(_ minute: Int) -> Binding<Date> {
        Binding(
            get: { Calendar.current.startOfDay(for: Date()).addingTimeInterval(TimeInterval(minute * 60)) },
            set: { picked in
                let parts = Calendar.current.dateComponents([.hour, .minute], from: picked)
                let next = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
                choose(.fixed, reminders.setMinutes.filter { $0 != minute } + [next])
            })
    }

    private func label(_ minute: Int) -> String {
        time(minute).wrappedValue.formatted(date: .omitted, time: .shortened)
    }
}
