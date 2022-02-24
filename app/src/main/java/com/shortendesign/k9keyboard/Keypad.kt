package com.shortendesign.k9keyboard

import com.shortendesign.k9keyboard.util.Key
import com.shortendesign.k9keyboard.util.LetterLayout
import com.shortendesign.k9keyboard.util.MissingLetterCode
import java.lang.StringBuilder

class Keypad(
    // Map from Android keyCode to the Key that should be activated
    private val keyCodeMapping: Map<Int, Key>,
    private val letterLayout: Map<Key, List<Char>>
) {
    // Map from letters to the Key pressed to access them
    private lateinit var letterKeyMap: Map<Char, Key>
    private lateinit var keyIsLetterMap: Map<Key, Boolean>

    init {
        initKeyMaps(letterLayout)
    }

    fun getKey(keyCode: Int): Key? {
        return keyCodeMapping[keyCode]
    }

    fun isLetter(key: Key): Boolean {
        if (keyIsLetterMap[key] == true)
            return true

        return false
    }

    fun getDigit(key: Key): Char? {
        val letters = letterLayout[key] ?: return null
        return if (letters.isNotEmpty() && letters[0].isDigit()) letters[0] else null
    }

    fun isDelete(key: Key): Boolean {
        return key == Key.BACK
    }

    fun isNext(key: Key): Boolean {
        return key == Key.STAR
    }

    fun isSpace(key: Key): Boolean {
        return key == Key.N0
    }


    /**
     * Get the series of key character values required to represent a word
     */
    fun getCodeForWord(word: String): String {
        val builder = StringBuilder()
        for (letter in word) {
            val code = codeForLetter(letter)
                ?: throw MissingLetterCode("No code found for '$letter'")
            builder.append(code)
        }
        return builder.toString()
    }

    /**
     * Get the Key that is activated by a keyCode
     */
    fun keyForKeyCode(keyCode: Int): Key? {
        return keyCodeMapping[keyCode]
    }

    /**
     * Get the Key character value required to represent a letter
     */
    private fun codeForLetter(letter: Char): Char? {
        return letterKeyMap[letter]?.code
    }

    /***
     * Map all the letters in the keypad layout to the Key pressed to access it
     */
    fun initKeyMaps(layout: Map<Key, List<Char>>): Map<Char, Key> {
        val letterKeyMap = HashMap<Char, Key>()
        val keyIsLetterMap = HashMap<Key, Boolean>()
        this.letterKeyMap = letterKeyMap
        this.keyIsLetterMap = keyIsLetterMap
        for ((key, characters) in layout) {
            for (char in characters) {
                letterKeyMap[char] = key
                // If this letter isn't in non-alphanumeric => it should be alphanumeric
                if (!LetterLayout.nonAlphaNumeric.contains(char)) {
                    // Support mapping the uppercase character as well
                    letterKeyMap[char.uppercaseChar()] = key
                    keyIsLetterMap[key] = true
                }
                else if (keyIsLetterMap[key] == null) {
                    keyIsLetterMap[key] = false
                }
            }
        }
        return letterKeyMap
    }

}