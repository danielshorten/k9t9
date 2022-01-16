package com.shortendesign.k9keyboard

import com.shortendesign.k9keyboard.util.Key
import com.shortendesign.k9keyboard.util.LetterLayout
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
            builder.append(codeForLetter(letter))
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
                if (!LetterLayout.nonAlphaNumeric.contains(char)) {
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