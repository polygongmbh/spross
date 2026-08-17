package net.spross.app

import android.content.SharedPreferences

/** Persisted profile = (source, target) catalog language codes. */
class ProfileStore(private val prefs: SharedPreferences) {

    val source: String? get() = prefs.getString(KEY_SOURCE, null)
    val target: String? get() = prefs.getString(KEY_TARGET, null)

    fun set(source: String, target: String) {
        prefs.edit().putString(KEY_SOURCE, source).putString(KEY_TARGET, target).apply()
    }

    companion object {
        /**
         * The one preference file this app keeps. Named here because the home-screen
         * widget opens it too — it runs outside the model and still has to know which
         * language the chrome is spoken in.
         */
        const val PREFS_NAME = "spross"

        private const val KEY_SOURCE = "source"
        private const val KEY_TARGET = "target"
    }
}
