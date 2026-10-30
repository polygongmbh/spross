package net.spross.app.reminder

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.spross.kern.box.BoxState
import net.spross.kern.reminder.ReminderMode
import net.spross.kern.reminder.Reminders
import net.spross.kern.store.StoredBoxes

/** The reminder choice as the settings row shows it, and the re-arming every save asks of it. */
class ReminderStanding(private val context: Context, private val store: ReminderStore) {

    var mode by mutableStateOf(store.mode)
        private set

    /** The set times, as minutes after local midnight, earliest first. */
    var setMinutes by mutableStateOf(store.setMinutes)
        private set

    /** Whether ignored reminders have stopped themselves ([Reminders.STOP_AFTER_DAYS]). */
    var paused by mutableStateOf(false)
        private set

    fun choose(mode: ReminderMode, setMinutes: List<Int>, nowEpochMillis: Long) {
        val sorted = setMinutes.distinct().sorted()
        store.set(mode, sorted, nowEpochMillis)
        this.mode = mode
        this.setMinutes = sorted
    }

    /** Arms kern's plan for [state] in place of the reminders armed before. Runs off the main thread. */
    fun replan(state: BoxState, boxes: StoredBoxes, nowEpochMillis: Long, tzId: String) {
        val plan = Reminders.plan(store.mode, store.setMinutes, store.enabledAt, state, boxes, nowEpochMillis, tzId)
        ReminderAlarms.arm(context, store, plan.fires)
        Handler(Looper.getMainLooper()).post { paused = plan.paused }
    }
}
