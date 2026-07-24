package net.spross.app

import android.content.SharedPreferences

/** Persisted profile = (source, target) catalog language codes. */
class ProfileStore(private val prefs: SharedPreferences) {

    val source: String? get() = prefs.getString(KEY_SOURCE, null)
    val target: String? get() = prefs.getString(KEY_TARGET, null)

    fun set(source: String, target: String) {
        prefs.edit().putString(KEY_SOURCE, source).putString(KEY_TARGET, target).apply()
    }

    private companion object {
        const val KEY_SOURCE = "source"
        const val KEY_TARGET = "target"
    }
}
