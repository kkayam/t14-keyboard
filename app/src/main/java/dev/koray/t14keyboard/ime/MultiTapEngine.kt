package dev.koray.t14keyboard.ime

import dev.koray.t14keyboard.lang.Language

/** Where the engine's output goes. Implemented by the IME service, faked in tests. */
interface InputSink {
    /** Show [text] as the pending (composing) character. */
    fun setComposing(text: String)

    /** Replace the composing region with [text] and end composition. */
    fun finishComposing(text: String)

    /** Commit [text] directly (no composition pending). */
    fun commitDirect(text: String)

    fun deleteBefore(count: Int)
    fun sendEnter()
    fun textBeforeCursor(max: Int): CharSequence
}

/**
 * Multi-tap state machine, independent of Android classes.
 *
 * Pressing a key starts a pending character shown as composing text; pressing the
 * same key again within [timeoutMs] cycles through that key's characters. Any other
 * key, a timeout, or a separator commits the pending character. Shift and the sym
 * layer (one-shot or locked) are handled here too.
 */
class MultiTapEngine(private val sink: InputSink) {

    enum class ShiftState { OFF, ONCE, LOCK }

    var timeoutMs: Long = 500
    var autoCaps: Boolean = true
    var doubleSpacePeriod: Boolean = true

    var language: Language = Language.EMPTY
        set(value) {
            commitPending()
            field = value
        }

    var symLayout: Language = Language.EMPTY
        set(value) {
            commitPending()
            field = value
        }

    /** Set for numeric/phone fields so digits come out on the first tap. */
    var forcedSym: Boolean = false
        set(value) {
            commitPending()
            field = value
        }

    var shift: ShiftState = ShiftState.OFF
        private set
    var symOneShot: Boolean = false
        private set
    var symLock: Boolean = false
        private set

    val symActive: Boolean get() = forcedSym || symLock || symOneShot

    private var pendingKey: String? = null
    private var pendingCycle: List<String> = emptyList()
    private var pendingIdx = 0
    private var pendingUpper = false
    private var pendingSymLayer = false
    private var lastTap = 0L
    private var lastSpace = 0L

    val hasPending: Boolean get() = pendingKey != null

    fun onLetterKey(keyId: String, now: Long) = press(keyId, now)

    fun onSpace(now: Long) {
        if (symActive) {
            press(KEY_SPACE, now)
            return
        }
        commitPending()
        if (doubleSpacePeriod && now - lastSpace <= DOUBLE_SPACE_MS) {
            val before = sink.textBeforeCursor(2).toString()
            if (before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                sink.deleteBefore(1)
                sink.commitDirect(". ")
                lastSpace = 0
                return
            }
        }
        sink.commitDirect(" ")
        lastSpace = now
    }

    fun onShift(now: Long) {
        if (symActive) {
            // The hardware shift key carries "#" on the sym layer.
            press(KEY_SHIFT, now)
            return
        }
        shift = when (shift) {
            ShiftState.OFF -> ShiftState.ONCE
            ShiftState.ONCE -> ShiftState.LOCK
            ShiftState.LOCK -> ShiftState.OFF
        }
    }

    fun onSymShort(now: Long) {
        if (symActive) {
            // The sym key itself carries "* +" on the sym layer.
            press(KEY_SYM, now)
            return
        }
        symOneShot = true
    }

    /** Long-press toggles sym lock. */
    fun onSymLong() {
        commitPending()
        symOneShot = false
        symLock = !symLock
    }

    fun onBackspace() {
        when {
            pendingKey != null -> {
                pendingKey = null
                sink.finishComposing("")
            }
            symOneShot -> symOneShot = false
            else -> sink.deleteBefore(1)
        }
    }

    fun onEnter() {
        commitPending()
        sink.sendEnter()
    }

    fun onTimeout() = commitPending()

    fun commitPending() {
        if (pendingKey == null) return
        val text = cased(pendingCycle[pendingIdx])
        pendingKey = null
        if (pendingSymLayer && symOneShot) symOneShot = false
        sink.finishComposing(text)
    }

    /** Called when input starts on a new field. */
    fun reset() {
        pendingKey = null
        shift = ShiftState.OFF
        symOneShot = false
        symLock = false
        lastSpace = 0
    }

    private fun press(keyId: String, now: Long) {
        val sameTap = pendingKey == keyId &&
            pendingSymLayer == symActive &&
            now - lastTap <= timeoutMs &&
            pendingCycle.size > 1
        if (sameTap) {
            pendingIdx = (pendingIdx + 1) % pendingCycle.size
            lastTap = now
            sink.setComposing(cased(pendingCycle[pendingIdx]))
            return
        }

        commitPending()
        val layer = if (symActive) symLayout else language
        val cycle = layer.keys[keyId] ?: return
        pendingKey = keyId
        pendingCycle = cycle
        pendingIdx = 0
        pendingSymLayer = symActive
        pendingUpper = when {
            shift != ShiftState.OFF -> {
                if (shift == ShiftState.ONCE) shift = ShiftState.OFF
                true
            }
            autoCaps && !symActive && isSentenceStart() -> true
            else -> false
        }
        lastTap = now
        sink.setComposing(cased(cycle[0]))
    }

    private fun cased(s: String) =
        if (pendingUpper) s.uppercase(language.locale) else s

    private fun isSentenceStart(): Boolean {
        val t = sink.textBeforeCursor(8).toString()
        val trimmed = t.trimEnd(' ')
        if (trimmed.isEmpty()) return true
        return trimmed.last() in ".!?…\n"
    }

    companion object {
        const val KEY_SYM = "SYM"
        const val KEY_SHIFT = "SHIFT"
        const val KEY_SPACE = "SPACE"
        const val DOUBLE_SPACE_MS = 600L
    }
}
