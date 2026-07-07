package dev.koray.t14keyboard.lang

import org.json.JSONObject
import java.util.Locale

/**
 * A language is simply a per-key ordered character cycle. Key ids are the base
 * letter printed first on each physical key ("Q" for the q/w key, …) plus the
 * special ids SYM, SHIFT and SPACE used by the symbol layer.
 */
data class Language(
    val name: String,
    val code: String,
    val locale: Locale,
    val keys: Map<String, List<String>>,
    val builtin: Boolean,
    val fileName: String? = null,
) {
    companion object {
        val VALID_KEYS = setOf(
            "Q", "E", "T", "U", "O",
            "A", "D", "G", "J", "L",
            "Z", "C", "B", "M",
            "SYM", "SHIFT", "SPACE",
        )

        val EMPTY = Language("", "", Locale.ROOT, emptyMap(), builtin = true)

        /** Parses and validates a language JSON file. Throws [IllegalArgumentException] on invalid input. */
        fun fromJson(json: String, builtin: Boolean, fileName: String? = null): Language {
            val o = try {
                JSONObject(json)
            } catch (e: Exception) {
                throw IllegalArgumentException("Not valid JSON: ${e.message}")
            }
            val name = o.optString("name")
            require(name.isNotBlank()) { "\"name\" is required" }
            val code = o.optString("code").uppercase(Locale.ROOT)
            require(code.isNotBlank() && code.length <= 4) { "\"code\" is required (max 4 chars)" }
            val locale = if (o.has("locale")) Locale.forLanguageTag(o.getString("locale")) else Locale.ROOT

            val keysObj = o.optJSONObject("keys")
                ?: throw IllegalArgumentException("\"keys\" object is required")
            val keys = mutableMapOf<String, List<String>>()
            for (k in keysObj.keys()) {
                require(k in VALID_KEYS) { "Unknown key id \"$k\" (valid: ${VALID_KEYS.joinToString()})" }
                val arr = keysObj.getJSONArray(k)
                require(arr.length() > 0) { "Key \"$k\" has an empty cycle" }
                keys[k] = List(arr.length()) { i ->
                    val s = arr.getString(i)
                    require(s.isNotEmpty()) { "Key \"$k\" contains an empty string" }
                    s
                }
            }
            require(keys.isNotEmpty()) { "\"keys\" must not be empty" }
            return Language(name, code, locale, keys, builtin, fileName)
        }
    }
}
