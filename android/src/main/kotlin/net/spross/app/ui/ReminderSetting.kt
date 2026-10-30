package net.spross.app.ui

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.locale
import net.spross.kern.reminder.ReminderMode

/**
 * Whether and when the app reminds the learner to practice: off, at the times they usually practice, or at times they set.
 * The system's notification permission is asked for where a reminder is turned on, and how loudly one arrives is the system settings' to say.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderSetting(model: AppModel) {
    val chrome: Chrome = model.chrome
    val reminders = model.reminders
    val context = LocalContext.current
    // why: read on every composition, so a return from the system settings shows what was changed there.
    var answered by remember { mutableIntStateOf(0) }
    val allowed = answered >= 0 && context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { answered++ }
    // why: -1 adds a time, any other index edits the time standing there.
    var editing by remember { mutableIntStateOf(NOT_EDITING) }
    val is24Hour = DateFormat.is24HourFormat(context)
    val format = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", chrome.locale)
    fun label(minute: Int) = LocalTime.of(minute / 60, minute % 60).format(format)

    fun choose(mode: ReminderMode, minutes: List<Int> = reminders.setMinutes) {
        model.chooseReminders(mode, minutes)
        val needsAsking = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (mode != ReminderMode.Off && needsAsking) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        Text(chrome.settingsRemindersTitle, style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = ReminderMode.entries
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == reminders.mode,
                    onClick = { choose(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    val text = when (option) {
                        ReminderMode.Off -> chrome.settingsRemindersOptionOff
                        ReminderMode.Usual -> chrome.settingsRemindersOptionUsual
                        ReminderMode.Fixed -> chrome.settingsRemindersOptionSet
                    }
                    Text(text, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        SettingHint(
            when (reminders.mode) {
                ReminderMode.Off -> chrome.settingsRemindersHintOff
                ReminderMode.Usual -> chrome.settingsRemindersHintUsual
                ReminderMode.Fixed -> chrome.settingsRemindersHintSet
            }
        )
        if (reminders.mode == ReminderMode.Fixed) {
            reminders.setMinutes.forEachIndexed { index, minute ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { editing = index }, contentPadding = SETTINGS_BUTTON_PADDING) {
                        Text(label(minute), style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.weight(1f))
                    if (reminders.setMinutes.size > 1) {
                        IconButton(onClick = { choose(ReminderMode.Fixed, reminders.setMinutes - minute) }) {
                            Icon(
                                SprossIcons.Close,
                                contentDescription = chrome.settingsRemindersRemove.format(label(minute)),
                                tint = Theme.colors.textSecondary,
                            )
                        }
                    }
                }
            }
            TextButton(onClick = { editing = ADDING }, contentPadding = SETTINGS_BUTTON_PADDING) {
                Text(chrome.settingsRemindersAdd)
            }
        }
        if (reminders.mode != ReminderMode.Off && !allowed) SettingHint(chrome.settingsRemindersHintDenied)
        else if (reminders.mode != ReminderMode.Off && reminders.paused) SettingHint(chrome.settingsRemindersHintPaused)
    }

    if (editing != NOT_EDITING) {
        val start = reminders.setMinutes.getOrNull(editing) ?: ((reminders.setMinutes.lastOrNull() ?: 0) + 3 * 60) % (24 * 60)
        val picker = rememberTimePickerState(start / 60, start % 60, is24Hour)
        AlertDialog(
            onDismissRequest = { editing = NOT_EDITING },
            text = { TimePicker(state = picker) },
            confirmButton = {
                TextButton(onClick = {
                    val picked = picker.hour * 60 + picker.minute
                    val kept = if (editing == ADDING) reminders.setMinutes else reminders.setMinutes - reminders.setMinutes[editing]
                    choose(ReminderMode.Fixed, kept + picked)
                    editing = NOT_EDITING
                }) { Text(chrome.commonDone) }
            },
            dismissButton = {
                TextButton(onClick = { editing = NOT_EDITING }) { Text(chrome.commonCancel) }
            },
        )
    }
}

private const val NOT_EDITING = -2
private const val ADDING = -1
