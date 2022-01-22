package com.shortendesign.k9keyboard.inputmode

import android.util.Log
import com.shortendesign.k9keyboard.KeyPressResult
import com.shortendesign.k9keyboard.Keypad
import com.shortendesign.k9keyboard.entity.Word
import com.shortendesign.k9keyboard.util.Key
import com.shortendesign.k9keyboard.util.Status
import java.lang.StringBuilder

class WordInputMode(
    private val keypad: Keypad,
) {
    private val LOG_TAG: String = "K9Word"
    private var cursorPosition: Int = 0
    private val codeWord = StringBuilder()
    private var candidateIdx: Int = 0
    private var cachedCandidates: List<Word>? = null
    private var currentStatus = Status.WORD_CAP

    val status: Status
        get() = this.currentStatus

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
                Log.d(LOG_TAG, "Space")
                addSpace()
            }
            keypad.isLetter(key) -> {
                Log.d(LOG_TAG, "Letter")
                addLetter(key)
            }
            keypad.isDelete(key) -> {
                Log.d(LOG_TAG, "Delete")
                deleteLetter()
            }
            keypad.isNext(key) -> {
                Log.d(LOG_TAG, "Next")
                nextCandidate()
            }
            else -> {
                state(consumed = false, word = finishComposing())
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
        // Get the final candidate word
        var word = finishComposing()
        // Add a space
        word = if (word != null) "$word " else " "

        return state(consumed = true, word = word)
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
            codeWord = if (codeWord.isEmpty()) null else codeWord.toString(),
            word = word,
            candidateIdx = candidateIdx,
        )
    }

    private fun finishComposing(): String? {
        val word = cachedCandidates?.get(candidateIdx)?.word
        cachedCandidates = null
        codeWord.clear()
        candidateIdx = 0
        return word
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