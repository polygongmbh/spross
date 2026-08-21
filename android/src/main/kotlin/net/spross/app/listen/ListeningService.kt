package net.spross.app.listen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import net.spross.app.R
import net.spross.app.SprossActivity

/**
 * What keeps a listening run playing with the screen off, and puts it on the lock screen.
 *
 * That is the point of the mode: it is for the hours the phone is in a pocket, and one that
 * stopped at the lock screen would be a mode for staring at a phone that is already speaking
 * (`docs/surfaces.md`). Two platform facts make it work. A FOREGROUND service is what keeps
 * the process out of the reaper's way while nothing is on screen; a [MediaSession] is what
 * makes the run a PLAYER as far as the system is concerned, which is what puts the word on
 * the lock screen and routes a headphone button at it.
 *
 * It plays nothing itself. Every control here goes through [ListeningBridge] to the same
 * reducer the on-screen buttons drive, so the shade and the screen cannot disagree.
 *
 * The wake lock is the gap between the beats: an active run is silent for two and a half
 * seconds at a time, and a phone that dozed in one of those gaps would wake up to a playlist
 * that had stopped halfway through a word.
 */
class ListeningService : Service() {

    private var session: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var foreground = false

    /** The mode's name in the learner's language, kept for the channel a null state leaves. */
    private var title = BRAND

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession(this, TAG).apply {
            setCallback(object : MediaSession.Callback() {
                // Play and pause are one intent: kern's run has one toggle, and a lock
                // screen that could reach a state the screen cannot is a second machine.
                override fun onPlay() {
                    ListeningBridge.controls?.togglePause()
                }

                override fun onPause() {
                    ListeningBridge.controls?.togglePause()
                }

                override fun onSkipToNext() {
                    ListeningBridge.controls?.skip()
                }

                // why: "previous" is the nearest thing a transport bar has to "again", and a
                // playlist with no history has nothing else to give it.
                override fun onSkipToPrevious() {
                    ListeningBridge.controls?.repeat()
                }

                override fun onStop() {
                    ListeningBridge.controls?.close()
                }
            })
            isActive = true
        }
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG)
            .apply { setReferenceCounted(false); acquire(WAKE_CEILING_MS) }
        ListeningBridge.observe(::show)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The shade's own buttons. They arrive here rather than at the session because a
        // notification action is a PendingIntent, and they land in the same one place.
        when (intent?.action) {
            ACTION_TOGGLE -> ListeningBridge.controls?.togglePause()
            ACTION_SKIP -> ListeningBridge.controls?.skip()
            ACTION_AGAIN -> ListeningBridge.controls?.repeat()
            ACTION_CLOSE -> ListeningBridge.controls?.close()
        }
        // why: a service started with startForegroundService owes a notification within
        // seconds whatever else happened — including a close that already emptied the state.
        show(ListeningBridge.nowPlaying)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ListeningBridge.observe(null)
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        session?.isActive = false
        session?.release()
        session = null
        super.onDestroy()
    }

    /** The whole of what this service does: draw the run, or take it down. */
    private fun show(now: ListeningNowPlaying?) {
        now?.let { title = it.title }
        channel()
        publishSession(now)
        val notification = notification(now)
        if (foreground) {
            (getSystemService(NotificationManager::class.java)).notify(NOTIFICATION_ID, notification)
        } else {
            enterForeground(notification)
            foreground = true
        }
        // A run that has ended has nothing left to keep the process up for.
        if (now == null) stopSelf()
    }

    private fun enterForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Named for the mode in the learner's own language, and re-declared on every show:
     * `createNotificationChannel` updates the name of a channel that already exists, which is
     * how a profile switched to another known language renames it without an uninstall.
     */
    private fun channel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, title, NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // Nobody asked for a sound or a buzz from a mode that is already speaking.
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun publishSession(now: ListeningNowPlaying?) {
        val session = session ?: return
        session.setMetadata(
            MediaMetadata.Builder()
                // The word is the title and its meaning the artist: that is the pair every
                // lock screen already knows how to lay out, largest line first.
                .putString(MediaMetadata.METADATA_KEY_TITLE, now?.target.orEmpty())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, now?.meaning.orEmpty())
                .putString(MediaMetadata.METADATA_KEY_ALBUM, now?.title ?: title)
                .build(),
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP,
                )
                .setState(
                    when {
                        now == null -> PlaybackState.STATE_STOPPED
                        now.paused -> PlaybackState.STATE_PAUSED
                        else -> PlaybackState.STATE_PLAYING
                    },
                    // why: a playlist of spoken words has no playhead to scrub — the run
                    // holds beats, not a position, so none is claimed.
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    1f,
                )
                .build(),
        )
    }

    private fun notification(now: ListeningNowPlaying?): Notification {
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_sprout)
            .setContentTitle(now?.target ?: title)
            .setContentText(now?.meaning.orEmpty())
            .setSubText(now?.title ?: title)
            .setContentIntent(openApp())
            .setDeleteIntent(command(ACTION_CLOSE))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            // why: the word changes every few seconds — an alert per turn would be a phone
            // buzzing its way through a walk.
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
        if (now != null) {
            builder.addAction(action(R.drawable.ic_listen_again, now.repeatLabel, ACTION_AGAIN))
            builder.addAction(
                if (now.paused) {
                    action(R.drawable.ic_listen_play, now.resumeLabel, ACTION_TOGGLE)
                } else {
                    action(R.drawable.ic_listen_pause, now.pauseLabel, ACTION_TOGGLE)
                },
            )
            builder.addAction(action(R.drawable.ic_listen_next, now.skipLabel, ACTION_SKIP))
            builder.addAction(action(R.drawable.ic_listen_close, now.closeLabel, ACTION_CLOSE))
            builder.setOngoing(!now.paused)
        }
        session?.let {
            val style = Notification.MediaStyle().setMediaSession(it.sessionToken)
            // Again, play/pause, next — the three a collapsed shade has room for, named only
            // where there are actions to name: a run already over carries none at all.
            if (now != null) style.setShowActionsInCompactView(0, 1, 2)
            builder.style = style
        }
        return builder.build()
    }

    private fun action(icon: Int, label: String, action: String): Notification.Action =
        Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, icon),
            label,
            command(action),
        ).build()

    private fun command(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, ListeningService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Tapping the notification opens the run it is about, never the app's front door. */
    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, SprossActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val TAG = "SprossListening"
        private const val WAKE_TAG = "spross:listening"
        private const val CHANNEL_ID = "listening"
        private const val NOTIFICATION_ID = 1

        /** The brand name, which needs no translating — the manifest labels the widget so too. */
        private const val BRAND = "Spross"

        private const val ACTION_TOGGLE = "net.spross.app.listen.TOGGLE"
        private const val ACTION_SKIP = "net.spross.app.listen.SKIP"
        private const val ACTION_AGAIN = "net.spross.app.listen.AGAIN"
        private const val ACTION_CLOSE = "net.spross.app.listen.CLOSE"

        /**
         * A ceiling on the wake lock rather than a lock held forever: a run that somehow
         * outlived its own teardown would otherwise hold the CPU up until the battery went.
         * Well past the longest bedtime kern offers, so it never cuts a run short.
         */
        private const val WAKE_CEILING_MS = 4L * 60 * 60 * 1000

        /** Started from a tap, so the foreground start is one the platform allows. */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, ListeningService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ListeningService::class.java))
        }
    }
}
