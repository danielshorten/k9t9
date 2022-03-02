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
): InputMode {
    private val LOG_TAG: String = "K9Word"
    val codeWord = StringBuilder()
    private var candidateIdx: Int = 0
    private var cachedCandidates: List<Word>? = null
    private var currentStatus = Status.WORD_CAP
    private var lastResolvedCodeWord: String? = null

    override val status: Status
        get() = this.currentStatus

    override fun getKeyCodeResult(keyCode: Int): KeyPressResult? {
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
                finishComposing()
                state(consumed = false)
            }
        }
    }

    private fun addLetter(key: Key): KeyPressResult {
        codeWord.append(key.code)
        return state(true, codeWord = codeWord.toString())
    }

    private fun deleteLetter(): KeyPressResult {
        var consumed = false
        if (isComposing()) {
            codeWord.deleteAt(codeWord.length - 1)
            consumed = codeWord.isNotEmpty()
        }
        return state(consumed, codeWord.toString())
    }

    private fun addSpace(): KeyPressResult {
        finishComposing()
        return state(consumed = true, word = " ")
    }

    private fun nextCandidate(): KeyPressResult {
        if (isComposing()) {
            candidateIdx++
        }
        return state(true)
    }

    private fun state(consumed: Boolean = true, codeWord: String = "", word: String? = null): KeyPressResult {
        var finalCodeWord: String? = codeWord
        if (finalCodeWord!!.isEmpty()) {
            finalCodeWord = this.codeWord.toString()
            if (finalCodeWord.isEmpty()) {
                finalCodeWord = null
            }
        }
        return KeyPressResult(
            consumed = consumed,
            codeWord = finalCodeWord,
            word = word
        )
    }

    private fun finishComposing(): String? {
        var word = cachedCandidates?.get(candidateIdx)?.word
        if (word != null && word.length > codeWord.length) {
            word = word.substring(0, codeWord.length)
        }
        cachedCandidates = null
        codeWord.clear()
        candidateIdx = 0
        return word
    }

    private fun isComposing(): Boolean {
        return codeWord.isNotEmpty()
    }

    override fun resolveCodeWord(codeWord: String, candidates: List<String>, final: Boolean): String? {
        if (!candidates.isEmpty()) {
            var candidateWord = when {
                candidateIdx < candidates.count() -> candidates[candidateIdx]
                candidateIdx >= candidates.count() -> candidates[0]
                else -> null
            } ?: return null
            // Reset the candidate index if it's falling out of bounds
            if (candidateIdx >= candidates.count() && candidates.count() > 0) {
                candidateIdx = 0
            }

            // Replace the code word
            //Log.d(LOG_TAG,"CODEWORD BEFORE: ${this.codeWord}")
            this.codeWord.replace(0, maxOf(this.codeWord.length, 0), codeWord)
            //Log.d(LOG_TAG,"CODEWORD AFTER: ${this.codeWord}")
            if (candidateWord.length > codeWord.length) {
                candidateWord = candidateWord.substring(0, codeWord.length)
            }
            lastResolvedCodeWord = codeWord
            return candidateWord
        }
        // If this was the final chance to resolve the code word, and we couldn't, reset to the last
        // resolved code word.
        if (final && lastResolvedCodeWord != null) {
            this.codeWord.replace(0, maxOf(this.codeWord.length, 0), lastResolvedCodeWord!!)
        }
        return null
    }
}