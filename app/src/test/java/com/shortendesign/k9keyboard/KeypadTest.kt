package com.shortendesign.k9keyboard

import com.shortendesign.k9keyboard.util.KeyCodeMapping
import com.shortendesign.k9keyboard.util.LetterLayout
import org.junit.Test
import org.junit.Assert.assertEquals


class KeypadTest {

    @Test
    fun getCodeForWord() {
        val keypad = Keypad(KeyCodeMapping.basic, LetterLayout.enUS)
        assertEquals(keypad.getCodeForWord("ball"), "2255")
    }

    @Test
    fun getCodeForWordWithAccents() {
        val keypad = Keypad(KeyCodeMapping.basic, LetterLayout.enUS)
        assertEquals(keypad.getCodeForWord("émigré"), "364473")
    }

    @Test
    fun getCodeForCapitalizedWord() {
        val keypad = Keypad(KeyCodeMapping.basic, LetterLayout.enUS)
        assertEquals(keypad.getCodeForWord("I'm"), "416")
    }
}