package dev.koray.t14keyboard.ime

import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import dev.koray.t14keyboard.Prefs
import dev.koray.t14keyboard.lang.Language
import dev.koray.t14keyboard.lang.LanguageRepository

class T14InputMethodService : InputMethodService(), InputSink {

    private lateinit var prefs: Prefs
    private lateinit var repository: LanguageRepository
    private lateinit var engine: MultiTapEngine

    private var languages: List<Language> = emptyList()
    private var loadedRevision = -1
    private var strip: StatusStripView? = null

    private val handler = Handler(Looper.getMainLooper())
    private val commitRunnable = Runnable {
        engine.onTimeout()
        updateStrip()
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        applyPrefs()
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        repository = LanguageRepository(this)
        engine = MultiTapEngine(this)
        reloadLanguages()
        applyPrefs()
        prefs.sp.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDestroy() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        handler.removeCallbacks(commitRunnable)
        super.onDestroy()
    }

    // ---- lifecycle ----

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        handler.removeCallbacks(commitRunnable)
        if (prefs.langRevision != loadedRevision) reloadLanguages()
        engine.reset()
        engine.forcedSym = isNumericField(attribute)
        updateStrip()
    }

    override fun onFinishInput() {
        handler.removeCallbacks(commitRunnable)
        engine.reset()
        super.onFinishInput()
    }

    override fun onCreateInputView(): View {
        strip = StatusStripView(this)
        updateStrip()
        return strip!!
    }

    override fun onEvaluateInputViewShown(): Boolean = prefs.showStrip

    override fun onEvaluateFullscreenMode(): Boolean = false

    // ---- hardware keys ----

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isEditing()) return super.onKeyDown(keyCode, event)
        val now = event.eventTime

        letterKeyMap[keyCode]?.let { keyId ->
            if (event.repeatCount == 0) {
                engine.onLetterKey(keyId, now)
                scheduleTimeout()
                updateStrip()
            }
            return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_0 -> {
                if (event.repeatCount == 0) {
                    engine.onSpace(now)
                    scheduleTimeout()
                    updateStrip()
                }
                return true
            }
            KeyEvent.KEYCODE_DEL -> {
                engine.onBackspace() // key repeat = hold to delete
                updateStrip()
                return true
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.repeatCount == 0) {
                    handler.removeCallbacks(commitRunnable)
                    engine.onEnter()
                    updateStrip()
                }
                return true
            }
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                if (event.repeatCount == 0) {
                    engine.onShift(now)
                    scheduleTimeout()
                    updateStrip()
                }
                return true
            }
            KeyEvent.KEYCODE_SYM -> {
                if (event.repeatCount == 0) event.startTracking()
                return true
            }
            KeyEvent.KEYCODE_LANGUAGE_SWITCH, KeyEvent.KEYCODE_PICTSYMBOLS -> {
                if (event.repeatCount == 0) cycleLanguage()
                return true
            }
        }
        // Call keys, d-pad, home etc. pass through untouched.
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SYM && isEditing()) {
            engine.onSymLong()
            updateStrip()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!isEditing()) return super.onKeyUp(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_SYM -> {
                if (!event.isCanceled) {
                    engine.onSymShort(event.eventTime)
                    scheduleTimeout()
                    updateStrip()
                }
                return true
            }
        }
        if (letterKeyMap.containsKey(keyCode)) return true
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_LANGUAGE_SWITCH, KeyEvent.KEYCODE_PICTSYMBOLS,
            -> return true
        }
        return super.onKeyUp(keyCode, event)
    }

    // ---- InputSink ----

    override fun setComposing(text: String) {
        currentInputConnection?.setComposingText(text, 1)
    }

    override fun finishComposing(text: String) {
        currentInputConnection?.apply {
            setComposingText(text, 1)
            finishComposingText()
        }
    }

    override fun commitDirect(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    override fun deleteBefore(count: Int) {
        currentInputConnection?.deleteSurroundingText(count, 0)
    }

    override fun sendEnter() {
        val ei = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction = ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        val multiline = ei.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        if (!multiline && !noEnterAction &&
            action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    override fun textBeforeCursor(max: Int): CharSequence =
        currentInputConnection?.getTextBeforeCursor(max, 0) ?: ""

    // ---- helpers ----

    private fun isEditing(): Boolean {
        if (currentInputConnection == null) return false
        val inputType = currentInputEditorInfo?.inputType ?: 0
        return inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_NULL
    }

    private fun isNumericField(ei: EditorInfo?): Boolean =
        when ((ei?.inputType ?: 0) and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME,
            -> true
            else -> false
        }

    private fun scheduleTimeout() {
        handler.removeCallbacks(commitRunnable)
        if (engine.hasPending) {
            handler.postDelayed(commitRunnable, engine.timeoutMs)
        }
    }

    private fun reloadLanguages() {
        languages = repository.loadAll()
        loadedRevision = prefs.langRevision
        engine.symLayout = runCatching { repository.loadSym() }.getOrDefault(Language.EMPTY)
        setLanguageByCode(prefs.currentCode)
    }

    private fun enabledLanguages(): List<Language> {
        val enabled = prefs.enabledCodes
        val list = languages.filter { it.code in enabled }
            .sortedBy { enabled.indexOf(it.code) }
        return list.ifEmpty { languages }
    }

    private fun setLanguageByCode(code: String) {
        val list = enabledLanguages()
        if (list.isEmpty()) return
        engine.language = list.firstOrNull { it.code == code } ?: list.first()
    }

    private fun cycleLanguage() {
        val list = enabledLanguages()
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.code == engine.language.code }
        val next = list[(idx + 1).mod(list.size)]
        engine.language = next
        prefs.currentCode = next.code
        updateStrip()
        if (!prefs.showStrip) {
            Toast.makeText(this, next.name, Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyPrefs() {
        engine.timeoutMs = prefs.timeoutMs.toLong()
        engine.autoCaps = prefs.autoCaps
        engine.doubleSpacePeriod = prefs.doubleSpacePeriod
        updateInputViewShown()
    }

    private fun updateStrip() {
        strip?.update(
            langCode = engine.language.code.ifBlank { "?" },
            shift = engine.shift,
            symOneShot = engine.symOneShot,
            symLock = engine.symLock || engine.forcedSym,
        )
    }

    companion object {
        /**
         * Both letters printed on a physical key map to the same key id, so the
         * IME works whether the hardware reports the first or the second letter.
         */
        private val letterKeyMap = mapOf(
            KeyEvent.KEYCODE_Q to "Q", KeyEvent.KEYCODE_W to "Q",
            KeyEvent.KEYCODE_E to "E", KeyEvent.KEYCODE_R to "E",
            KeyEvent.KEYCODE_T to "T", KeyEvent.KEYCODE_Y to "T",
            KeyEvent.KEYCODE_U to "U", KeyEvent.KEYCODE_I to "U",
            KeyEvent.KEYCODE_O to "O", KeyEvent.KEYCODE_P to "O",
            KeyEvent.KEYCODE_A to "A", KeyEvent.KEYCODE_S to "A",
            KeyEvent.KEYCODE_D to "D", KeyEvent.KEYCODE_F to "D",
            KeyEvent.KEYCODE_G to "G", KeyEvent.KEYCODE_H to "G",
            KeyEvent.KEYCODE_J to "J", KeyEvent.KEYCODE_K to "J",
            KeyEvent.KEYCODE_L to "L",
            KeyEvent.KEYCODE_Z to "Z", KeyEvent.KEYCODE_X to "Z",
            KeyEvent.KEYCODE_C to "C", KeyEvent.KEYCODE_V to "C",
            KeyEvent.KEYCODE_B to "B", KeyEvent.KEYCODE_N to "B",
            KeyEvent.KEYCODE_M to "M",
        )
    }
}
