package com.shortendesign.k9keyboard

import com.shortendesign.k9keyboard.inputmode.WordInputMode
import com.shortendesign.k9keyboard.util.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import java.util.*

class WordInputModeTest {
    var mode: WordInputMode? = null

    @Before
    fun setup() {
        val mode = WordInputMode(
            keypad = Keypad(KeyCodeMapping.default(), LetterLayout.enUS)
        )
        mode.load(KeyCommandResolver.getBasic(), null, "")
        this.mode = mode
    }

    private fun pressKeys(mode: WordInputMode, vararg keys: Key): List<KeyPressResult> {
        val results = LinkedList<KeyPressResult>()
        for (key in keys) {
            results.add(mode.getKeyCodeResult(key)!!)
        }
        return results
    }


    /**
     * Test pressing a single letter key
     */
    @Test
    fun testAddLetter() {
        val result = mode!!.getKeyCodeResult(Key.N2)

        assertEquals(true, result.consumed)
        //assertEquals(1, result.cursorPosition)
        assertEquals("2", result.codeWord)
    }

    /**
     * Test deleting a letter while composing
     */
    @Test
    fun testDeleteLetter() {
        val mode = this.mode!!
        mode.getKeyCodeResult(Key.N2)
        mode.resolveCodeWord("2", listOf("a"), true)

        val result = mode.getKeyCodeResult(Key.DELETE)

        // Consumed is false because we're deleting the last character in the word we're composing,
        // so we delegate to the input method to delete and reset things for us.
        assertEquals(false, result.consumed)
        //assertEquals(0, result.cursorPosition)
        assertEquals(null, result.codeWord)
    }

    /**
     * Test deleting a letter while not composing
     */
    @Test
    fun testDeleteLetterNoText() {
        val result = mode!!.getKeyCodeResult(Key.DELETE)

        assertEquals(false, result.consumed)
        //assertEquals(0, result.cursorPosition)
        assertEquals(null, result.codeWord)
    }

    /**
     * Test that resolveCodeWord properly updates the mode's code word
     */
    @Test
    fun testResolveCodeWordReplacesCodeWord() {
        val mode = this.mode!!
        mode.getKeyCodeResult(Key.N3)
        mode.resolveCodeWord(
            "3", listOf("daniel")
        )
        mode.getKeyCodeResult(Key.N2)
        mode.resolveCodeWord(
            "32", listOf("daniel")
        )
        assertEquals("Code word should be '32'",
            "32", mode.codeWord.toString())
    }

    /**
     * Test advancing to next candidate while not composing
     */
    @Test
    fun testNextCandidateNotComposing() {
        val result = mode!!.getKeyCodeResult(Key.STAR)

        assertEquals(true, result.consumed)
        //assertEquals(0, result.cursorPosition)
        assertEquals(null, result.codeWord)
    }

    /**
     * Test advancing to next candidate
     */
    @Test
    fun testNextCandidate() {
        val mode = this.mode!!
        pressKeys(mode, Key.N2, Key.N2, Key.N5, Key.N5)

        val candidates = listOf("call", "ball")

        val candidate1 = mode.resolveCodeWord("2255", candidates)
        val result1 = mode.getKeyCodeResult(Key.STAR)
        val candidate2 = mode.resolveCodeWord("2255", candidates)
        mode.getKeyCodeResult(Key.STAR)
        val candidate3 = mode.resolveCodeWord("2255", candidates)

        // ASSERT
        assertEquals("'next' should be consumed",
            true, result1.consumed)
        assertEquals("'next' should return a code word",
            "2255", result1.codeWord)
        assertEquals("First candidate should be 'call'",
            "Call", candidate1)
        assertEquals("Second candidate should be 'ball'",
            "Ball", candidate2)
        assertEquals("Third candidate should be 'call' again",
            "Call", candidate3)
    }

    @Test
    fun testSpace() {
        // SETUP
        val mode = this.mode!!

        // EXECUTE
        // Press space
        val result = mode.getKeyCodeResult(Key.N0)

        assertEquals(true, result.consumed)
        //assertEquals(1, result.cursorPosition)
        assertEquals(null, result.codeWord)
    }

    @Test
    fun testSpaceWithWord() {
        // SETUP
        val mode = this.mode!!
        // Type a word
        pressKeys(mode, Key.N2, Key.N2, Key.N5, Key.N5)

        // EXECUTE
        // Press space
        val spaceResult = mode.getKeyCodeResult(Key.N0)

        // First space should come back with the word
        assertEquals(true, spaceResult.consumed)
        assertEquals(null, spaceResult.codeWord)
        assertEquals(" ", spaceResult.word)
    }

    @Test
    fun testPartialCandidate() {
        // SETUP
        val mode = this.mode!!

        // Type a part of a longer word
        pressKeys(mode, Key.N2, Key.N2, Key.N5, Key.N2)
        // Resolve the candidate
        val candidate = mode.resolveCodeWord(
            "2252",
            listOf("balance")
        )

        assertEquals("the word should just be the letters that were typed",
            "Bala", candidate)
    }

    @Test
    fun testApostrophe() {
        // SETUP
        val mode = this.mode!!

        // Type a word with an apostrophe
        pressKeys(mode, Key.N4, Key.N1, Key.N6)
        val candidate = mode.resolveCodeWord(
            "416",
            listOf("I'm")
        )

        assertEquals("The candidate should be I'm",
            "I'm", candidate)
    }

    @Test
    fun testNavigateLeftEndOfWord() {
        val result = mode?.getKeyCodeResult(
            Key.LEFT,
            0,
            false,
            "This is the text before the cursor",
            ". And this is the text after the cursor."
        )

        assertTrue(
            "Navigating left at the end of a word should start recomposing",
            result!!.recomposing)
        assertEquals(
            "The recomposing word should be correct",
            "cursor", result.word)
    }

    @Test
    fun testNavigateLeftMiddleOfWord() {
        val result = mode?.getKeyCodeResult(
            Key.LEFT,
            0,
            false,
            "This is the text bef",
            "ore the cursor. And this is the text after the cursor."
        )

        assertTrue(
            "Navigating left in the middle of a word should start recomposing",
            result!!.recomposing)
        assertEquals(
            "The recomposing word should be correct",
            result.word, "before")
    }

    @Test
    fun testNavigateLeftBetweenWords() {
        val result = mode?.getKeyCodeResult(
            Key.LEFT,
            0,
            false,
            "This is the text before the cursor.",
            " And this is the text after the cursor."
        )

        assertFalse(
            "Navigating left between words shouldn't trigger recomposing",
            result!!.recomposing)
        assertEquals(
            "There shouldn't be a recomposing word",
            result.word, null)
    }

    @Test
    fun testNavigateRightAfterLinebreak() {
        val result = mode?.getKeyCodeResult(
            Key.RIGHT,
            0,
            false,
            "This is the\ntext before the\ncursor.\n",
            "Porcupine"
        )

        assertTrue(
            "Navigating right after a linebreak should trigger recomposing",
            result!!.recomposing)
        assertEquals(
            "There shouldn't be a recomposing word",
            "Porcupine", result.word)
    }

    @Test
    fun testUndoPreviousSpaceCommandOnNewline() {
        // This test assumes that space is mapped to the 0 key (short press) and newline is mapped
        // to the 0 key (long press)
        val shortSpaceResult = mode?.getKeyCodeResult(
            Key.N0,
            longPress = false
        )

        val longSpaceResult = mode?.getKeyCodeResult(
            Key.N0,
            longPress = true
        )

        assertEquals(
            "Short press should produce a space",
            " ",
            shortSpaceResult?.word
        )
        assertEquals(
            "Long press should produce a newline",
            "\n",
            longSpaceResult?.word
        )
        assertEquals(
            "Long press should request to delete the previous space",
            -1,
            longSpaceResult?.cursorOffset
        )
    }

    @Test
    fun testDontUndoPreviousSpaceCommandOnSpace() {
        // This test assumes that space is mapped to the 0 key (short press) and newline is mapped
        // to the 0 key (long press)
        val shortSpaceResult = mode?.getKeyCodeResult(
            Key.N0,
            longPress = false
        )

        val shortSpaceResult2 = mode?.getKeyCodeResult(
            Key.N0,
            longPress = false
        )

        assertEquals(
            "First space should produce a space",
            " ",
            shortSpaceResult?.word
        )
        assertEquals(
            "Second space should produce a space",
            " ",
            shortSpaceResult2?.word
        )
        assertEquals(
            "Second space should not ask to delete previous space",
            0,
            shortSpaceResult2?.cursorOffset
        )
    }
}
