package com.shortendesign.k9keyboard

import com.shortendesign.k9keyboard.util.Key
import com.shortendesign.k9keyboard.util.KeyCodeMapping
import com.shortendesign.k9keyboard.util.LetterLayout
import org.junit.Test
import org.junit.Assert.assertEquals


class KeypadTest {

    @Test
    fun getCodeForWord() {
        val keypad = Keypad(KeyCodeMapping.default(), LetterLayout.enUS)
        assertEquals(keypad.getCodeForWord("ball"), "2255")
    }

    @Test
    fun getCodeForWordWithAccents() {
        val keypad = Keypad(KeyCodeMapping.default(), LetterLayout.enUS)
        assertEquals(keypad.getCodeForWord("émigré"), "364473")
    }

    @Test
    fun getCodesForWordsWithSpecialCharacters() {
        val keypad = Keypad(KeyCodeMapping.default(), LetterLayout.enUS)
        assertEquals(keypad.getCodeForWord("tip-off"), "8471633")
    }

    @Test
    fun getCodeForCapitalizedWord() {
        val keypad = Keypad(KeyCodeMapping.default(), LetterLayout.enUS)
        assertEquals(keypad.getCodeForWord("I'm"), "416")
    }

    @Test
    fun getCharacter() {
        val keypad = Keypad(KeyCodeMapping.default(), LetterLayout.enUS)
        // Test that idx wraps around to the first letter again
        assertEquals('a', keypad.getCharacter(Key.N2, 0))
        assertEquals('a', keypad.getCharacter(Key.N2, 3))
    }
}