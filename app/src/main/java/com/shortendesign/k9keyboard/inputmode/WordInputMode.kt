package com.shortendesign.k9keyboard.inputmode

import com.shortendesign.k9keyboard.KeyPressResult
import com.shortendesign.k9keyboard.Keypad
import com.shortendesign.k9keyboard.util.Key
import com.shortendesign.k9keyboard.util.Status
import java.lang.StringBuilder

class WordInputMode(
    private val keypad: Keypad,
): InputMode {
    private val LOG_TAG: String = "K9Word"
    val codeWord = StringBuilder()
    private var caseMask: UInt = 0u
    private var candidateIdx: Int = 0
    private var currentStatus = Status.WORD_CAP
    private var lastResolvedCodeWord: String? = null
    private var lastWordWasPeriod = false
    private var typingSinceModeChange = false

    override val status: Status
        get() = this.currentStatus

    private val shouldRecomposeBeforeRegex = """([\w]+)$""".toRegex()
    private val shouldRecomposeAfterRegex = """^([\w]+)""".toRegex()

    override fun shouldRecomposeWord(beforeCursor: CharSequence?, afterCursor: CharSequence?): String? {

        val beforeMatches = if (beforeCursor != null) shouldRecomposeBeforeRegex.find(beforeCursor) else null
        val afterMatches = if (afterCursor != null) shouldRecomposeAfterRegex.find(afterCursor) else null
        if (beforeMatches == null || afterMatches == null) {
            return null
        }
        return beforeMatches.groups[0]?.value + afterMatches.groups[0]?.value
    }

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
            keypad.isShift(key) -> {
                shiftMode()
            }
            else -> {
                var isConsumed = false;
                if (codeWord.isNotEmpty()) {
                    finishComposing()
                    // Treat DPAD_RIGH1T as consumed to allow pressing right to end composing but not
                    // send it on as a keypress to move to the next input or something annoying.
                    isConsumed = key == Key.RIGHT
                }
                state(isConsumed)
            }
        }
    }

    private fun addLetter(key: Key): KeyPressResult {
        codeWord.append(key.code)
        typingSinceModeChange = true
        if (setOf(Status.WORD_CAP, Status.WORD_UPPER).contains(currentStatus)) {
            caseMask = registerMaskDigit(caseMask, codeWord.length - 1)
        }
        if (currentStatus == Status.WORD_CAP) {
            currentStatus = Status.WORD
        }
        //Log.d(LOG_TAG, "MASK: ${Integer.toBinaryString(caseMask.toInt())}")
        return state(true, codeWord = codeWord.toString())
    }

    private fun deleteLetter(): KeyPressResult {
        var consumed = false
        if (isComposing()) {
            codeWord.deleteAt(codeWord.length - 1)
            caseMask = registerMaskDigit(caseMask, codeWord.length, false)
            consumed = codeWord.isNotEmpty()
            // If we've deleted the whole word we were composing, reset the candidate index
            if (!consumed) {
                candidateIdx = 0
            }
        }
        return state(consumed, codeWord.toString())
    }

    private fun addSpace(): KeyPressResult {
        finishComposing()
        if (lastWordWasPeriod) {
            currentStatus = Status.WORD_CAP
        }
        return state(consumed = true, word = " ")
    }

    private fun nextCandidate(): KeyPressResult {
        if (isComposing()) {
            candidateIdx++
        }
        return state(true)
    }

    private fun shiftMode(): KeyPressResult {
        var consumed = true

        currentStatus = when (currentStatus) {
            Status.WORD -> {
                if (typingSinceModeChange) {
                    typingSinceModeChange = false
                    Status.WORD_CAP
                }
                else {
                    typingSinceModeChange = false
                    Status.WORD_UPPER
                }
            }
            Status.WORD_CAP -> {
                typingSinceModeChange = false
                Status.WORD
            }
            Status.WORD_UPPER -> {
                if (typingSinceModeChange) {
                    typingSinceModeChange = false
                    Status.WORD
                } else {
                    consumed = false
                    Status.WORD_UPPER
                }
            }
            else -> {
                consumed = false
                currentStatus
            }
        }
        if (!consumed) {
            finishComposing()
        }
        return state(consumed)
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

    private fun finishComposing() {
        codeWord.clear()
        caseMask = 0u
        candidateIdx = 0
    }

    private fun isComposing(): Boolean {
        return codeWord.isNotEmpty()
    }

    private fun checkForPeriod(candidateWord: String) {
        lastWordWasPeriod = setOf(".","?","!").contains(candidateWord)
    }

    override fun resolveCodeWord(codeWord: String, candidates: List<String>, final: Boolean): String? {
        if (!candidates.isEmpty()) {
            // Get the candidate at the correct index, based on which one we've chosen
            var candidateWord = when {
                candidateIdx < candidates.count() -> candidates[candidateIdx]
                candidateIdx >= candidates.count() -> candidates[0]
                else -> null
            } ?: return null
            // Reset the candidate index if it's falling out of bounds
            if (candidateIdx >= candidates.count() && candidates.count() > 0) {
                candidateIdx = 0
            }

            // Replace the code word with the one we're resolving.
            // This handles the case where we've recorded a bunch of key presses but haven't been
            // able to resolve them to any actual word.  We keep the codeWord for the ones we found.
            this.codeWord.replace(0, maxOf(this.codeWord.length, 0), codeWord)
            // Truncate the word if it's longer than the actual number of keys that have been
            // pressed.
            if (candidateWord.length > codeWord.length) {
                candidateWord = candidateWord.substring(0, codeWord.length)
            }

            candidateWord = applyCaseMask(candidateWord, caseMask)

            lastResolvedCodeWord = codeWord
            checkForPeriod(candidateWord)
            return candidateWord
        }
        // If this was the final chance to resolve the code word, and we couldn't, reset to the last
        // resolved code word.
        if (final && lastResolvedCodeWord != null) {
            this.codeWord.replace(0, maxOf(this.codeWord.length, 0), lastResolvedCodeWord!!)
        }
        return null
    }

    companion object {
        /**
         * Get a new mask from the given one by setting or clearing a binary digit at the specified
         * index.
         */
        fun registerMaskDigit(mask: UInt, idx: Int, on: Boolean = true): UInt {
            return when(on) {
                // ORing with shifted bit to switch it on
                true -> mask or (1u shl idx)
                false -> if (mask shr idx and 1u == 1u)
                            // XORing with shifted bit to switch it off
                            // Only do XOR if the bit is on in the first place
                            mask xor (1u shl idx)
                        else
                            mask
            }
        }

        fun applyCaseMask(word: String, mask: UInt): String {
            val builder = StringBuilder(word)
            builder.forEachIndexed { idx, char ->
                if ((mask shr idx) and 1u == 1u) {
                    builder[idx] = char.uppercaseChar()
                }
            }
            return builder.toString()
        }
    }
}