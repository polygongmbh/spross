package net.spross.app.reminder

import android.content.SharedPreferences
import net.spross.kern.reminder.ReminderFire
import net.spross.kern.reminder.ReminderMode

/**
 * The learner's reminder choice, and the reminders last armed —
 * kept so a reboot, which clears every alarm, can arm them again without the box.
 */
class ReminderStore(private val prefs: SharedPreferences) {

    val mode: ReminderMode
        get() = prefs.getString(KEY_MODE, null)?.let { runCatching { ReminderMode.valueOf(it) }.getOrNull() }
            ?: ReminderMode.Off

    /** The set times, as minutes after local midnight. */
    val setMinutes: List<Int>
        get() = prefs.getString(KEY_TIMES, null)?.split(',')?.mapNotNull { it.toIntOrNull() }
            ?: listOf(DEFAULT_MINUTE)

    /** When reminders were last turned on; what counts as ignoring them starts here. */
    val enabledAt: Long get() = prefs.getLong(KEY_ENABLED_AT, 0L)

    fun set(mode: ReminderMode, setMinutes: List<Int>, nowEpochMillis: Long) {
        val editor = prefs.edit()
            .putString(KEY_MODE, mode.name)
            .putString(KEY_TIMES, setMinutes.joinToString(","))
        if (this.mode == ReminderMode.Off && mode != ReminderMode.Off) editor.putLong(KEY_ENABLED_AT, nowEpochMillis)
        editor.apply()
    }

    var armed: List<ReminderFire>
        get() = prefs.getString(KEY_ARMED, null).orEmpty().split(';').mapNotNull { entry ->
            val (at, due) = entry.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            ReminderFire(at.toLongOrNull() ?: return@mapNotNull null, due.toIntOrNull() ?: 0)
        }
        set(value) {
            prefs.edit().putString(KEY_ARMED, value.joinToString(";") { "${it.atEpochMillis}:${it.dueCount}" }).apply()
        }

    private companion object {
        const val KEY_MODE = "reminder.mode"
        const val KEY_TIMES = "reminder.times"
        const val KEY_ENABLED_AT = "reminder.enabledAt"
        const val KEY_ARMED = "reminder.armed"
        const val DEFAULT_MINUTE = 19 * 60
    }
}
