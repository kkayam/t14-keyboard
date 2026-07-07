package dev.koray.t14keyboard.ime

import dev.koray.t14keyboard.lang.Language
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MultiTapEngineTest {

    private class FakeSink : InputSink {
        val committed = StringBuilder()
        var composing = ""
        var enterCount = 0

        override fun setComposing(text: String) {
            composing = text
        }

        override fun finishComposing(text: String) {
            committed.append(text)
            composing = ""
        }

        override fun commitDirect(text: String) {
            committed.append(text)
        }

        override fun deleteBefore(count: Int) {
            repeat(count) {
                if (committed.isNotEmpty()) committed.deleteCharAt(committed.length - 1)
            }
        }

        override fun sendEnter() {
            enterCount++
        }

        override fun textBeforeCursor(max: Int): CharSequence =
            (committed.toString() + composing).takeLast(max)

        /** Committed text plus the pending composing char — what the user sees. */
        val text: String get() = committed.toString() + composing
    }

    private lateinit var sink: FakeSink
    private lateinit var engine: MultiTapEngine
    private var now = 1_000L

    @Before
    fun setUp() {
        sink = FakeSink()
        engine = MultiTapEngine(sink)
        engine.language = EN
        engine.symLayout = SYM
        engine.timeoutMs = 500
        engine.autoCaps = false
    }

    private fun tap(key: String, gap: Long = 100) {
        now += gap
        engine.onLetterKey(key, now)
    }

    private fun space(gap: Long = 100) {
        now += gap
        engine.onSpace(now)
    }

    private fun shift() = engine.onShift(now)

    private fun sym(gap: Long = 100) {
        now += gap
        engine.onSymShort(now)
    }

    // ---- basic multi-tap ----

    @Test
    fun `different keys commit previous letter`() {
        tap("Q")
        tap("E")
        assertEquals("qe", sink.text)
    }

    @Test
    fun `same key within timeout cycles`() {
        tap("Q")
        tap("Q")
        assertEquals("w", sink.text)
    }

    @Test
    fun `cycle wraps around`() {
        tap("Q")
        tap("Q")
        tap("Q")
        assertEquals("q", sink.text)
    }

    @Test
    fun `timeout commits pending`() {
        tap("Q")
        engine.onTimeout()
        tap("Q")
        assertEquals("qq", sink.text)
    }

    @Test
    fun `tap after timeout window starts a new letter`() {
        tap("Q")
        tap("Q", gap = 600)
        assertEquals("qq", sink.text)
    }

    @Test
    fun `single-char cycle repeats the letter immediately`() {
        tap("M")
        tap("M")
        assertEquals("mm", sink.text)
    }

    @Test
    fun `backspace removes pending letter`() {
        tap("Q")
        engine.onBackspace()
        assertEquals("", sink.text)
    }

    @Test
    fun `backspace deletes committed text`() {
        tap("Q")
        engine.onTimeout()
        engine.onBackspace()
        assertEquals("", sink.text)
    }

    @Test
    fun `enter commits pending and sends enter`() {
        tap("Q")
        engine.onEnter()
        assertEquals("q", sink.text)
        assertEquals(1, sink.enterCount)
    }

    // ---- space ----

    @Test
    fun `space commits pending and inserts space`() {
        tap("Q")
        space()
        assertEquals("q ", sink.text)
    }

    @Test
    fun `double space inserts period`() {
        tap("Q")
        space()
        space(gap = 200)
        assertEquals("q. ", sink.text)
    }

    @Test
    fun `double space disabled inserts two spaces`() {
        engine.doubleSpacePeriod = false
        tap("Q")
        space()
        space(gap = 200)
        assertEquals("q  ", sink.text)
    }

    @Test
    fun `slow double space inserts two spaces`() {
        tap("Q")
        space()
        space(gap = 2_000)
        assertEquals("q  ", sink.text)
    }

    // ---- shift ----

    @Test
    fun `shift once capitalizes only next letter`() {
        shift()
        tap("Q")
        tap("E")
        assertEquals("Qe", sink.text)
    }

    @Test
    fun `shift keeps case while cycling`() {
        shift()
        tap("Q")
        tap("Q")
        assertEquals("W", sink.text)
    }

    @Test
    fun `double shift is caps lock`() {
        shift()
        shift()
        tap("Q")
        tap("E")
        assertEquals("QE", sink.text)
    }

    @Test
    fun `triple shift turns shift off`() {
        shift()
        shift()
        shift()
        tap("Q")
        assertEquals("q", sink.text)
    }

    @Test
    fun `auto caps capitalizes sentence starts`() {
        engine.autoCaps = true
        tap("T")
        engine.onTimeout()
        space()
        tap("E")
        engine.onTimeout()
        space()
        space(gap = 100) // double space -> ". "
        tap("G")
        assertEquals("T e. G", sink.text)
    }

    // ---- sym layer ----

    @Test
    fun `sym one-shot outputs symbol then returns to letters`() {
        sym()
        tap("Z")
        engine.onTimeout()
        tap("Z")
        assertEquals("@z", sink.text)
    }

    @Test
    fun `sym one-shot supports multi-tap on symbol cycle`() {
        sym()
        tap("Z")
        tap("Z")
        engine.onTimeout()
        tap("Z")
        assertEquals("&z", sink.text)
    }

    @Test
    fun `sym key types star and plus while sym active`() {
        sym()
        sym() // sym while armed -> "*"
        sym() // cycle -> "+"
        engine.onTimeout()
        assertEquals("+", sink.text)
    }

    @Test
    fun `sym long press locks symbol layer`() {
        engine.onSymLong()
        tap("E")
        tap("T")
        tap("U")
        assertEquals("123", sink.text)
        engine.onSymLong()
        tap("Q")
        assertEquals("123q", sink.text)
    }

    @Test
    fun `shift key types hash in sym mode`() {
        engine.onSymLong()
        shift()
        engine.onTimeout()
        assertEquals("#", sink.text)
    }

    @Test
    fun `space key types zero in sym mode`() {
        engine.onSymLong()
        space()
        engine.onTimeout()
        assertEquals("0", sink.text)
    }

    @Test
    fun `forced sym makes first tap a digit`() {
        engine.forcedSym = true
        tap("E")
        tap("B")
        assertEquals("18", sink.text)
    }

    @Test
    fun `backspace cancels armed sym one-shot`() {
        tap("Q")
        engine.onTimeout()
        sym()
        engine.onBackspace() // cancels one-shot, keeps text
        assertEquals("q", sink.text)
        tap("E")
        assertEquals("qe", sink.text)
    }

    // ---- languages ----

    @Test
    fun `turkish key cycles through accented letters`() {
        engine.language = TR
        tap("U")
        tap("U")
        tap("U")
        tap("U")
        assertEquals("ı", sink.text)
    }

    @Test
    fun `turkish i uppercases with dot`() {
        engine.language = TR
        shift()
        tap("U")
        tap("U")
        assertEquals("İ", sink.text)
    }

    @Test
    fun `switching language commits pending`() {
        tap("Q")
        engine.language = TR
        tap("C")
        tap("C")
        tap("C")
        assertEquals("qç", sink.text)
    }

    private companion object {
        val EN = Language(
            name = "English", code = "EN", locale = Locale.ROOT,
            keys = mapOf(
                "Q" to listOf("q", "w"), "E" to listOf("e", "r"), "T" to listOf("t", "y"),
                "U" to listOf("u", "i"), "O" to listOf("o", "p"), "A" to listOf("a", "s"),
                "D" to listOf("d", "f"), "G" to listOf("g", "h"), "J" to listOf("j", "k"),
                "L" to listOf("l"), "Z" to listOf("z", "x"), "C" to listOf("c", "v"),
                "B" to listOf("b", "n"), "M" to listOf("m"),
            ),
            builtin = true,
        )

        val TR = Language(
            name = "Türkçe", code = "TR", locale = Locale.forLanguageTag("tr"),
            keys = mapOf(
                "Q" to listOf("q", "w"), "E" to listOf("e", "r"), "T" to listOf("t", "y"),
                "U" to listOf("u", "i", "ü", "ı"), "O" to listOf("o", "p", "ö"),
                "A" to listOf("a", "s", "ş"), "D" to listOf("d", "f"),
                "G" to listOf("g", "h", "ğ"), "J" to listOf("j", "k"), "L" to listOf("l"),
                "Z" to listOf("z", "x"), "C" to listOf("c", "v", "ç"),
                "B" to listOf("b", "n"), "M" to listOf("m"),
            ),
            builtin = true,
        )

        val SYM = Language(
            name = "Symbols", code = "SYM", locale = Locale.ROOT,
            keys = mapOf(
                "Q" to listOf("!", "(", ")"), "E" to listOf("1"), "T" to listOf("2"),
                "U" to listOf("3"), "O" to listOf(".", ":", ";"),
                "A" to listOf("?", "/", "\\"), "D" to listOf("4"), "G" to listOf("5"),
                "J" to listOf("6"), "L" to listOf(",", "´", "'"),
                "Z" to listOf("@", "&", "%"), "C" to listOf("7"), "B" to listOf("8"),
                "M" to listOf("9"),
                "SYM" to listOf("*", "+", "="), "SHIFT" to listOf("#", "-"),
                "SPACE" to listOf("0"),
            ),
            builtin = true,
        )
    }
}
