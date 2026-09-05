package net.spross.app

import android.content.SharedPreferences

/**
 * SharedPreferences over a plain map, so a store's own rules can be tested on the JVM.
 *
 * [SharedPreferences] and its editor are interfaces, so the framework needs nothing here
 * but the reads and writes a store actually makes; the map may be handed to a second
 * store, which is what a relaunch looks like from the store's side.
 */
class FakePrefs(private val values: MutableMap<String, Any?> = mutableMapOf()) : SharedPreferences {

    override fun getAll(): MutableMap<String, *> = values

    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getString(key: String, defValue: String?): String? =
        values[key] as? String ?: defValue

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST")
        (values[key] as? MutableSet<String> ?: defValues)

    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Edit()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    /** Staged like the real one: nothing reaches the map until apply or commit. */
    private inner class Edit : SharedPreferences.Editor {
        private val staged = mutableMapOf<String, Any?>()
        private val dropped = mutableSetOf<String>()
        private var wipe = false

        override fun putString(key: String, value: String?) = stage(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?) = stage(key, values)
        override fun putInt(key: String, value: Int) = stage(key, value)
        override fun putLong(key: String, value: Long) = stage(key, value)
        override fun putFloat(key: String, value: Float) = stage(key, value)
        override fun putBoolean(key: String, value: Boolean) = stage(key, value)

        override fun remove(key: String): SharedPreferences.Editor {
            dropped += key
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            wipe = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (wipe) values.clear()
            values.keys.removeAll(dropped)
            values.putAll(staged)
        }

        private fun stage(key: String, value: Any?): SharedPreferences.Editor {
            staged[key] = value
            return this
        }
    }
}
