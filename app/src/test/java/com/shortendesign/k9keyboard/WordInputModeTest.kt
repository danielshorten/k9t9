package com.shortendesign.k9keyboard

import com.shortendesign.k9keyboard.inputmode.WordInputMode
import com.shortendesign.k9keyboard.util.Key
import com.shortendesign.k9keyboard.util.KeyCodeMapping
import com.shortendesign.k9keyboard.util.LetterLayout
import com.shortendesign.k9keyboard.util.TestUtil
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import java.util.*

class WordInputModeTest {
    var mode: WordInputMode? = null

    @Before
    fun setup() {
        mode = WordInputMode(
            keypad = Keypad(KeyCodeMapping.basic, LetterLayout.enUS)
        )
    }

    private fun pressKeys(mode: WordInputMode, vararg keys: Key): List<KeyPressResult> {
        val results = LinkedList<KeyPressResult>()
        for (key in keys) {
            results.add(mode.getKeyPressResult(key))
        }
        return results
    }


    /**
     * Test pressing a single letter key
     */
    @Test
    fun testAddLetter() {
        val result = mode!!.getKeyPressResult(Key.N2)

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
        mode.getKeyPressResult(Key.N2)
        mode.resolveCodeWord("2", listOf("a"), true)

        val result = mode.getKeyPressResult(Key.BACK)

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
        val result = mode!!.getKeyPressResult(Key.BACK)

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
        mode.getKeyPressResult(Key.N3)
        mode.resolveCodeWord(
            "3", listOf("daniel")
        )
        mode.getKeyPressResult(Key.N2)
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
        val result = mode!!.getKeyPressResult(Key.STAR)

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
        val result1 = mode.getKeyPressResult(Key.STAR)
        val candidate2 = mode.resolveCodeWord("2255", candidates)
        mode.getKeyPressResult(Key.STAR)
        val candidate3 = mode.resolveCodeWord("2255", candidates)

        // ASSERT
        assertEquals("'next' should be consumed",
            true, result1.consumed)
        assertEquals("'next' should return a code word",
            "2255", result1.codeWord)
        assertEquals("First candidate should be 'call'",
            "call", candidate1)
        assertEquals("Second candidate should be 'ball'",
            "ball", candidate2)
        assertEquals("Third candidate should be 'call' again",
            "call", candidate3)
    }

    @Test
    fun testSpace() {
        // SETUP
        val mode = this.mode!!

        // EXECUTE
        // Press space
        val result = mode.getKeyPressResult(Key.N0)

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
        val spaceResult = mode.getKeyPressResult(Key.N0)

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
            "bala", candidate)
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
    fun testRegisterMaskDigit() {
        val startingMask = 0

        // Switch on index 0
        val mask1 = WordInputMode.registerMaskDigit(startingMask, 0)
        assertEquals("1", Integer.toBinaryString(mask1))

        // Switch on index 2 from previous mask
        val mask2 = WordInputMode.registerMaskDigit(mask1, 2)
        assertEquals("101", Integer.toBinaryString(mask2))

        // Switch off index 0 from previous mask
        val mask3 = WordInputMode.registerMaskDigit(mask2, 0, false)
        assertEquals("100", Integer.toBinaryString(mask3))
    }
}