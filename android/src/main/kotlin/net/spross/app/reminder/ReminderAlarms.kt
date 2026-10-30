package net.spross.app.reminder

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import net.spross.app.Chrome
import net.spross.app.ProfileStore
import net.spross.app.R
import net.spross.app.SprossActivity
import net.spross.app.countLine
import net.spross.kern.reminder.ReminderFire

/**
 * Kern's reminder plan as alarms: each one posts a notification when it goes off.
 * Inexact on purpose — a reminder minutes late is still on time, and an exact alarm costs the learner a permission.
 */
object ReminderAlarms {

    private const val ACTION_FIRE = "net.spross.app.reminder.FIRE"
    private const val EXTRA_DUE = "due"
    private const val CHANNEL_ID = "reminders"
    private const val NOTIFICATION_ID = 2
    private const val WINDOW_MILLIS = 10 * 60 * 1000L

    /** Arms [fires] in place of every reminder armed before. */
    fun arm(context: Context, store: ReminderStore, fires: List<ReminderFire>) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        store.armed.indices.drop(fires.size).forEach { alarms.cancel(pending(context, it, 0)) }
        fires.forEachIndexed { index, fire ->
            alarms.setWindow(AlarmManager.RTC_WAKEUP, fire.atEpochMillis, WINDOW_MILLIS, pending(context, index, fire.dueCount))
        }
        store.armed = fires
    }

    private fun pending(context: Context, index: Int, due: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        index,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_DUE, due),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(ProfileStore.PREFS_NAME, Context.MODE_PRIVATE)
        val store = ReminderStore(prefs)
        when (intent.action) {
            ACTION_FIRE -> post(context, ProfileStore(prefs).source, intent.getIntExtra(EXTRA_DUE, 0))
            // why: a reboot clears every alarm, and the box is not read until the app opens.
            Intent.ACTION_BOOT_COMPLETED ->
                arm(context, store, store.armed.filter { it.atEpochMillis > System.currentTimeMillis() })
        }
    }

    private fun post(context: Context, source: String?, due: Int) {
        val chrome = Chrome.forSource(source ?: "en")
        val manager = context.getSystemService(NotificationManager::class.java)
        // why: re-declared on every post, so a switched known language renames the channel in the system settings.
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, chrome.reminderChannel, NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, SprossActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val body = if (due > 0) chrome.countLine(chrome.reminderBodyDueOne, chrome.reminderBodyDue, due) else chrome.reminderBodyFresh
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_sprout)
            .setContentTitle(chrome.reminderTitle)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        if (manager.areNotificationsEnabled()) manager.notify(NOTIFICATION_ID, notification)
    }
}

/** Where an armed reminder goes off, and where a reboot arms them again. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = ReminderAlarms.onReceive(context, intent)
}
