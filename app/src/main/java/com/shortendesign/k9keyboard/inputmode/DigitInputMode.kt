package com.shortendesign.k9keyboard.inputmode

import com.shortendesign.k9keyboard.KeyPressResult
import com.shortendesign.k9keyboard.Keypad
import com.shortendesign.k9keyboard.util.Key
import com.shortendesign.k9keyboard.util.Status

class NumberInputMode (
    private val keypad: Keypad
): InputMode {
    override val status: Status
        get() = Status.NUM

    override fun getKeyCodeResult(keyCode: Int, repeatCount: Int, textBeforeCursor: CharSequence?, textAfterCursor: CharSequence?): KeyPressResult? {
        val key = keypad.getKey(keyCode) ?: return null
        return getKeyPressResult(key)
    }

    override fun resolveCodeWord(codeWord: String, candidates: List<String>, final: Boolean,
                                 resetToWord: String?): String? {
        throw NotImplementedError("This mode does not resolve code words")
    }

    fun getKeyPressResult(key: Key): KeyPressResult {
        val digit = keypad.getDigit(key)
        var consumed = false
        var word: String? = null
        if (digit != null) {
            consumed = true
            word = digit.toString()
        }
        return KeyPressResult(
            consumed,
            null,
            word = word
        )
    }
}