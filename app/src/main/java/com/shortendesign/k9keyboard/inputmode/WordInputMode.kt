package com.shortendesign.k9keyboard.inputmode

import com.shortendesign.k9keyboard.KeyPressResult
import com.shortendesign.k9keyboard.Keypad
import com.shortendesign.k9keyboard.entity.Word
import com.shortendesign.k9keyboard.util.Key
import java.lang.StringBuilder

class WordInputMode(
    private val keypad: Keypad,
) {
    private var cursorPosition: Int = 0
    private val codeWord = StringBuilder()
    private var candidateIdx: Int = 0
    private var cachedCandidates: List<Word>? = null

    fun setCursorPosition(position: Int) {
        cursorPosition = position
    }

    fun getKeyCodeResult(keyCode: Int): KeyPressResult? {
        val key = keypad.getKey(keyCode) ?: return null
        return getKeyPressResult(key)
    }

    fun getKeyPressResult(key: Key): KeyPressResult {
        return when {
            keypad.isSpace(key) -> {
                addSpace()
            }
            keypad.isLetter(key) -> {
                addLetter(key)
            }
            keypad.isDelete(key) -> {
                deleteLetter()
            }
            keypad.isNext(key) -> {
                nextCandidate()
            }
            else -> {
                clear()
                state(false)
            }
        }
    }

    private fun addLetter(key: Key): KeyPressResult {
        codeWord.append(key.code)
        cursorPosition++
        return state(true)
    }

    private fun deleteLetter(): KeyPressResult {
        var consumed = false
        if (isComposing()) {
            codeWord.deleteAt(codeWord.length - 1)
            cursorPosition--
            consumed = true
        }
        return state(consumed)
    }

    private fun addSpace(): KeyPressResult {
        cursorPosition++
        clear()
        return state(word = " ")
    }

    private fun nextCandidate(): KeyPressResult {
        if (isComposing()) {
            if (candidateIdx + 1 == cachedCandidates?.count()) {
                candidateIdx = 0
            } else {
                candidateIdx++
            }
        }
        return state(true)
    }

    private fun state(consumed: Boolean = true, word: String? = null): KeyPressResult {
        return KeyPressResult(
            consumed = consumed,
            cursorPosition = cursorPosition,
            codeWord = if (codeWord.isEmpty())  null else codeWord.toString(),
            word = word,
            candidateIdx = candidateIdx
        )
    }

    private fun clear() {
        codeWord.clear()
        candidateIdx = 0
    }

    private fun isComposing(): Boolean {
        return codeWord.isNotEmpty()
    }

    fun getComposingText(candidates: List<Word>): String? {
        if (!candidates.isEmpty()) {
            cachedCandidates = candidates
            var candidateWord = candidates[candidateIdx].word
            if (candidateWord.length > codeWord.length) {
                candidateWord = candidateWord.substring(0, codeWord.length)
            }
            return candidateWord
        }
        cachedCandidates = null
        return null
    }
}