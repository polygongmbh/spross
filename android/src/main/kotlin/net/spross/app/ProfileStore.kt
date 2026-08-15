package net.spross.app

import android.content.SharedPreferences

/** Persisted profile = (source, target) catalog language codes, and what the app still owes a first-time learner. */
class ProfileStore(private val prefs: SharedPreferences) {

    val source: String? get() = prefs.getString(KEY_SOURCE, null)
    val target: String? get() = prefs.getString(KEY_TARGET, null)

    /**
     * Whether a round still owes the three lines that teach it (`SessionCoach`). On disk
     * rather than in memory: an app killed mid-first-round would otherwise cost the
     * learner the one round that explains itself.
     */
    var coachPending: Boolean
        get() = prefs.getBoolean(KEY_COACH, false)
        set(value) = prefs.edit().putBoolean(KEY_COACH, value).apply()

    fun set(source: String, target: String) {
        prefs.edit().putString(KEY_SOURCE, source).putString(KEY_TARGET, target).apply()
    }

    private companion object {
        const val KEY_SOURCE = "source"
        const val KEY_TARGET = "target"
        const val KEY_COACH = "sessionCoachPending"
    }
}
