package dev.koray.t14keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView

/** One slim row: language code plus shift/sym indicators. Nothing else. */
@SuppressLint("ViewConstructor")
class StatusStripView(context: Context) : TextView(context) {

    init {
        val pad = (resources.displayMetrics.density * 10).toInt()
        setPadding(pad, pad / 2, pad, pad / 2)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.MONOSPACE
        setTextColor(0xFFE8EAED.toInt())
        setBackgroundColor(0xFF202124.toInt())
    }

    fun update(
        langCode: String,
        shift: MultiTapEngine.ShiftState,
        symOneShot: Boolean,
        symLock: Boolean,
    ) {
        val parts = mutableListOf(langCode)
        when (shift) {
            MultiTapEngine.ShiftState.ONCE -> parts += "⇧"
            MultiTapEngine.ShiftState.LOCK -> parts += "⇪ CAPS"
            MultiTapEngine.ShiftState.OFF -> {}
        }
        if (symLock) parts += "SYM" else if (symOneShot) parts += "sym"
        text = parts.joinToString("  ")
    }
}
