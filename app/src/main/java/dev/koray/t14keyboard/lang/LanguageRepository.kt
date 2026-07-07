package dev.koray.t14keyboard.lang

import android.content.Context
import java.io.File
import java.util.Locale

/**
 * Loads languages from bundled assets and from user-imported files in
 * filesDir/languages/. A user file with the same code overrides the bundled one.
 */
class LanguageRepository(private val context: Context) {

    private val userDir = File(context.filesDir, "languages")

    /** All typing languages, bundled first, user imports after (overriding by code). */
    fun loadAll(): List<Language> {
        val langs = LinkedHashMap<String, Language>()
        context.assets.list(ASSET_DIR)?.sorted()?.forEach { fileName ->
            if (fileName.endsWith(".json") && fileName != SYM_FILE) {
                runCatching { langs.putLang(readAsset(fileName, builtin = true)) }
            }
        }
        userDir.listFiles { f -> f.extension == "json" && f.name != SYM_FILE }
            ?.sortedBy { it.name }
            ?.forEach { f ->
                runCatching { langs.putLang(Language.fromJson(f.readText(), builtin = false, fileName = f.name)) }
            }
        return langs.values.toList()
    }

    /** The symbol layer. A user-provided sym.json overrides the bundled one. */
    fun loadSym(): Language {
        val userSym = File(userDir, SYM_FILE)
        if (userSym.exists()) {
            runCatching { return Language.fromJson(userSym.readText(), builtin = false, fileName = SYM_FILE) }
        }
        return readAsset(SYM_FILE, builtin = true)
    }

    /** Validates and installs a language file. Throws [IllegalArgumentException] if invalid. */
    fun import(json: String): Language {
        val lang = Language.fromJson(json, builtin = false)
        userDir.mkdirs()
        val file = File(userDir, "${lang.code.lowercase(Locale.ROOT)}.json")
        file.writeText(json)
        return lang.copy(fileName = file.name)
    }

    /** Deletes a user-imported language file. Bundled languages cannot be deleted. */
    fun delete(lang: Language): Boolean =
        !lang.builtin && lang.fileName != null && File(userDir, lang.fileName).delete()

    private fun readAsset(fileName: String, builtin: Boolean): Language =
        context.assets.open("$ASSET_DIR/$fileName").bufferedReader().use {
            Language.fromJson(it.readText(), builtin, fileName)
        }

    private fun LinkedHashMap<String, Language>.putLang(lang: Language) {
        remove(lang.code)
        put(lang.code, lang)
    }

    companion object {
        private const val ASSET_DIR = "languages"
        private const val SYM_FILE = "sym.json"
    }
}
