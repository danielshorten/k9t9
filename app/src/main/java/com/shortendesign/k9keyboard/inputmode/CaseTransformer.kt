package com.shortendesign.k9keyboard.inputmode

import com.shortendesign.k9keyboard.util.Status
import java.lang.StringBuilder

class CaseTransformer (
    private var normalStatus: Status,
    private var capitalizedStatus: Status,
    private var allUppercaseStatus: Status,
    private var capitalizingStatuses: Set<Status> = setOf(capitalizedStatus, allUppercaseStatus)
) {
    private var currentStatus = capitalizedStatus
    private var typingSinceModeChange = false
    private var lastWordEndedSentence = false
    private var caseMask: UInt = 0u
    private val endOfSentence = """[.!]+\s*""".toRegex()

    val status: Status
        get() = this.currentStatus

    fun signalTyping(wordLength: Int) {
        typingSinceModeChange = true
        if (capitalizingStatuses.contains(currentStatus)) {
            caseMask = registerMaskDigit(caseMask, wordLength - 1)
        }
        if (currentStatus == capitalizedStatus) {
            currentStatus = normalStatus
        }
    }

    fun signalDelete(wordLength: Int) {
        caseMask = registerMaskDigit(caseMask, wordLength, false)
    }

    fun signalSpace() {
        if (lastWordEndedSentence) {
            currentStatus = capitalizedStatus
        }
    }

    fun signalEndOfSentence(ending: String) {
        lastWordEndedSentence = setOf(".","?","!").contains(ending)
    }

    fun shiftMode() {
        currentStatus = when (currentStatus) {
            normalStatus -> {
                if (typingSinceModeChange) {
                    typingSinceModeChange = false
                    capitalizedStatus
                }
                else {
                    typingSinceModeChange = false
                    allUppercaseStatus
                }
            }
            capitalizedStatus -> {
                typingSinceModeChange = false
                normalStatus
            }
            allUppercaseStatus -> {
                if (typingSinceModeChange) {
                    typingSinceModeChange = false
                    normalStatus
                }
                capitalizedStatus
            }
            else -> capitalizedStatus
        }
    }

    fun applyCaseMask(word: String): String {
        val builder = StringBuilder(word)
        builder.forEachIndexed { idx, char ->
            if ((caseMask shr idx) and 1u == 1u) {
                builder[idx] = char.uppercaseChar()
            }
        }
        return builder.toString()
    }

    fun init(word: String? = null, beforeText: CharSequence? = null): CaseTransformer {
        // Initialize the mask
        caseMask = 0u
        // If there's a word provided, use it to initialize the mask
        word?.forEachIndexed { idx, char ->
            caseMask = registerMaskDigit(caseMask, idx, char.isUpperCase())
        }
        // Set the current status based on the immediately preceding text
        currentStatus = if (beforeText != null) {
            if (beforeText == "" || endOfSentence.find(beforeText) != null) {
                capitalizedStatus
            } else {
                normalStatus
            }
        } else {
            currentStatus
        }
        // Facilitate chaining to a constructor
        return this
    }

    companion object {
        /**
         * Get a new mask from the given one by setting or clearing a binary digit at the specified
         * index.
         */
        private fun registerMaskDigit(mask: UInt, idx: Int, on: Boolean = true): UInt {
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
    }
}