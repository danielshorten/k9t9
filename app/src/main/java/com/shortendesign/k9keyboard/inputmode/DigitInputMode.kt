package com.shortendesign.k9keyboard.inputmode

import com.shortendesign.k9keyboard.KeyPressResult
import com.shortendesign.k9keyboard.Keypad
import com.shortendesign.k9keyboard.util.Command
import com.shortendesign.k9keyboard.util.Key
import com.shortendesign.k9keyboard.util.Status

class NumberInputMode (
    private val keypad: Keypad
): InputMode {
    override val status: Status
        get() = Status.NUM

    override fun getKeyCodeResult(command: Command, key: Key?, repeatCount: Int,
                                  longPress: Boolean, textBeforeCursor: CharSequence?,
                                  textAfterCursor: CharSequence?): KeyPressResult {
        // Swallow regular keypress repeats that arent navigate or delete commands
        if (!longPress && repeatCount > 0 && !setOf(Command.NAVIGATE, Command.DELETE).contains(command)) {
            return KeyPressResult(true, null)
        }

        return when (command) {
            Command.CHARACTER -> handleDigit(key!!)
            Command.SHIFT_MODE -> KeyPressResult(true, null)
            else -> KeyPressResult(false, null)
        }
    }

    private fun handleDigit(key: Key): KeyPressResult {
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

    override fun resolveCodeWord(codeWord: String, candidates: List<String>, final: Boolean,
                                 resetToWord: String?): String? {
        throw NotImplementedError("This mode does not resolve code words")
    }
}