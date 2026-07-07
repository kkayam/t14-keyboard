package dev.koray.t14keyboard

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {

    val sp: SharedPreferences =
        context.getSharedPreferences("t14", Context.MODE_PRIVATE)

    var timeoutMs: Int
        get() = sp.getInt(KEY_TIMEOUT, 500)
        set(v) = sp.edit().putInt(KEY_TIMEOUT, v).apply()

    var autoCaps: Boolean
        get() = sp.getBoolean(KEY_AUTO_CAPS, true)
        set(v) = sp.edit().putBoolean(KEY_AUTO_CAPS, v).apply()

    var doubleSpacePeriod: Boolean
        get() = sp.getBoolean(KEY_DOUBLE_SPACE, true)
        set(v) = sp.edit().putBoolean(KEY_DOUBLE_SPACE, v).apply()

    var showStrip: Boolean
        get() = sp.getBoolean(KEY_SHOW_STRIP, true)
        set(v) = sp.edit().putBoolean(KEY_SHOW_STRIP, v).apply()

    /** Ordered codes of languages enabled for the switch key. */
    var enabledCodes: List<String>
        get() = sp.getString(KEY_ENABLED, "EN,TR")!!.split(',').filter { it.isNotBlank() }
        set(v) = sp.edit().putString(KEY_ENABLED, v.joinToString(",")).apply()

    var currentCode: String
        get() = sp.getString(KEY_CURRENT, "EN")!!
        set(v) = sp.edit().putString(KEY_CURRENT, v).apply()

    /** Bumped whenever language files change so the IME knows to reload them. */
    var langRevision: Int
        get() = sp.getInt(KEY_LANG_REV, 0)
        set(v) = sp.edit().putInt(KEY_LANG_REV, v).apply()

    companion object {
        const val KEY_TIMEOUT = "timeoutMs"
        const val KEY_AUTO_CAPS = "autoCaps"
        const val KEY_DOUBLE_SPACE = "doubleSpacePeriod"
        const val KEY_SHOW_STRIP = "showStrip"
        const val KEY_ENABLED = "enabledCodes"
        const val KEY_CURRENT = "currentCode"
        const val KEY_LANG_REV = "langRevision"
    }
}
