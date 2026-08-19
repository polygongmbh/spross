package net.spross.app

import android.content.SharedPreferences

/** Persisted profile = (source, target) catalog language codes, and who is learning. */
class ProfileStore(private val prefs: SharedPreferences) {

    val source: String? get() = prefs.getString(KEY_SOURCE, null)
    val target: String? get() = prefs.getString(KEY_TARGET, null)

    /**
     * What to call the learner — ONE name for the person, not one per language pair,
     * which is why it sits beside the pair rather than in a box document.
     *
     * Blank and absent are the same state: nothing is stored for an empty field, so the
     * greeting reads its own no-name wording rather than a placeholder standing in for one.
     */
    var name: String?
        get() = prefs.getString(KEY_NAME, null)?.trim()?.takeIf { it.isNotEmpty() }
        set(value) {
            val trimmed = value?.trim().orEmpty()
            val editor = prefs.edit()
            if (trimmed.isEmpty()) editor.remove(KEY_NAME) else editor.putString(KEY_NAME, trimmed)
            editor.apply()
        }

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
        private const val KEY_NAME = "learnerName"
    }
}
