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


    @Test
    fun testAddLetter() {
        val result = mode!!.getKeyPressResult(Key.N2)

        assertEquals(true, result.consumed)
        assertEquals(1, result.cursorPosition)
        assertEquals("2", result.codeWord)
        assertEquals(0, result.candidateIdx)
    }

    @Test
    fun testDeleteLetter() {
        mode!!.getKeyPressResult(Key.N2)
        val result = mode!!.getKeyPressResult(Key.BACK)

        assertEquals(true, result.consumed)
        assertEquals(0, result.cursorPosition)
        assertEquals(null, result.codeWord)
        assertEquals(0, result.candidateIdx)
    }

    @Test
    fun testDeleteLetterNoText() {
        val result = mode!!.getKeyPressResult(Key.BACK)

        assertEquals(false, result.consumed)
        assertEquals(0, result.cursorPosition)
        assertEquals(null, result.codeWord)
        assertEquals(0, result.candidateIdx)
    }

    @Test
    fun testNextCandidateNotComposing() {
        val result = mode!!.getKeyPressResult(Key.STAR)

        assertEquals(true, result.consumed)
        assertEquals(0, result.cursorPosition)
        assertEquals(null, result.codeWord)
        assertEquals(0, result.candidateIdx)

    }

    @Test
    fun testNextCandidate() {
        val mode = this.mode!!
        pressKeys(mode, Key.N2, Key.N2, Key.N5, Key.N5)

        val candidates = listOf(
            TestUtil.createWord("call", "2255"),
            TestUtil.createWord("ball", "2255")
        )

        val candidate1 = mode.getComposingText(candidates)
        val result = mode.getKeyPressResult(Key.STAR)
        val candidate2 = mode.getComposingText(candidates)

        assertEquals(true, result.consumed)
        assertEquals(4, result.cursorPosition)
        assertEquals("2255", result.codeWord)
        assertEquals(1, result.candidateIdx)
        assertEquals("call", candidate1)
        assertEquals("ball", candidate2)
    }

    @Test
    fun testSpace() {
        // SETUP
        val mode = this.mode!!

        // EXECUTE
        // Press space
        val result = mode.getKeyPressResult(Key.N0)

        assertEquals(true, result.consumed)
        assertEquals(1, result.cursorPosition)
        assertEquals(null, result.codeWord)
        assertEquals(0, result.candidateIdx)
        assertEquals(" ", result.word)
    }

    @Test
    fun testSpaceWithWord() {
        // SETUP
        val mode = this.mode!!
        // Type a word
        pressKeys(mode, Key.N2, Key.N2, Key.N5, Key.N5)
        // Send the mode candidates for the word
        mode.getComposingText(listOf(TestUtil.createWord("call", "2255")))

        // EXECUTE
        // Press space
        val firstSpaceResult = mode.getKeyPressResult(Key.N0)
        val secondSpaceResult = mode.getKeyPressResult(Key.N0)

        // First space should come back with the word
        assertEquals(true, firstSpaceResult.consumed)
        assertEquals(5, firstSpaceResult.cursorPosition)
        assertEquals(null, firstSpaceResult.codeWord)
        assertEquals(0, firstSpaceResult.candidateIdx)
        assertEquals("call ", firstSpaceResult.word)

        // Next space should just be a space
        assertEquals(true, secondSpaceResult.consumed)
        assertEquals(6, secondSpaceResult.cursorPosition)
        assertEquals(null, secondSpaceResult.codeWord)
        assertEquals(0, secondSpaceResult.candidateIdx)
        assertEquals(" ", secondSpaceResult.word)
    }
}