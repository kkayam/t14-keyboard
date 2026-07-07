package dev.koray.t14keyboard.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import dev.koray.t14keyboard.Prefs
import dev.koray.t14keyboard.R
import dev.koray.t14keyboard.lang.Language
import dev.koray.t14keyboard.lang.LanguageRepository

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var repository: LanguageRepository
    private lateinit var langList: LinearLayout
    private lateinit var timeoutLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        repository = LanguageRepository(this)
        langList = findViewById(R.id.lang_list)
        timeoutLabel = findViewById(R.id.timeout_label)

        findViewById<Button>(R.id.btn_enable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btn_select).setOnClickListener {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showInputMethodPicker()
        }
        findViewById<Button>(R.id.btn_import).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain", "*/*"))
            }
            startActivityForResult(intent, REQUEST_IMPORT)
        }

        setUpTimeoutSeek()
        setUpSwitch(R.id.sw_autocaps, prefs.autoCaps) { prefs.autoCaps = it }
        setUpSwitch(R.id.sw_dspace, prefs.doubleSpacePeriod) { prefs.doubleSpacePeriod = it }
        setUpSwitch(R.id.sw_strip, prefs.showStrip) { prefs.showStrip = it }
        refreshLanguageList()
    }

    private fun setUpTimeoutSeek() {
        val seek = findViewById<SeekBar>(R.id.timeout_seek)
        seek.progress = (prefs.timeoutMs - TIMEOUT_MIN).coerceIn(0, seek.max)
        timeoutLabel.text = getString(R.string.timeout_label, prefs.timeoutMs)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                timeoutLabel.text = getString(R.string.timeout_label, progress + TIMEOUT_MIN)
            }

            override fun onStartTrackingTouch(sb: SeekBar) {}

            override fun onStopTrackingTouch(sb: SeekBar) {
                prefs.timeoutMs = sb.progress + TIMEOUT_MIN
            }
        })
    }

    private fun setUpSwitch(id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        val sw = findViewById<Switch>(id)
        sw.isChecked = initial
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    private fun refreshLanguageList() {
        langList.removeAllViews()
        val languages = repository.loadAll()
        val enabled = prefs.enabledCodes
        languages.forEach { lang ->
            langList.addView(makeLanguageRow(lang, languages, enabled))
        }
    }

    private fun makeLanguageRow(
        lang: Language,
        languages: List<Language>,
        enabled: List<String>,
    ): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val check = CheckBox(this).apply {
            text = "${lang.name} (${lang.code})"
            isChecked = lang.code in enabled
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnCheckedChangeListener { box, checked ->
                val newEnabled = languages
                    .filter { l -> if (l.code == lang.code) checked else l.code in prefs.enabledCodes }
                    .map { it.code }
                if (newEnabled.isEmpty()) {
                    box.isChecked = true
                    Toast.makeText(context, "At least one language must stay enabled", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.enabledCodes = newEnabled
                    prefs.langRevision += 1
                }
            }
        }
        row.addView(check)
        if (!lang.builtin) {
            val delete = Button(this).apply {
                text = getString(R.string.delete)
                setOnClickListener {
                    repository.delete(lang)
                    prefs.enabledCodes = prefs.enabledCodes.filter { it != lang.code }
                    prefs.langRevision += 1
                    refreshLanguageList()
                }
            }
            row.addView(delete)
        }
        return row
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMPORT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalArgumentException("Could not read file")
            val lang = repository.import(json)
            prefs.enabledCodes = prefs.enabledCodes + lang.code
            prefs.langRevision += 1
            refreshLanguageList()
            Toast.makeText(this, "Imported ${lang.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val REQUEST_IMPORT = 1
        private const val TIMEOUT_MIN = 200
    }
}
