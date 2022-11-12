package com.shortendesign.k9keyboard.inputmode

import com.shortendesign.k9keyboard.KeyPressResult
import com.shortendesign.k9keyboard.Keypad
import com.shortendesign.k9keyboard.util.Command
import com.shortendesign.k9keyboard.util.Key
import com.shortendesign.k9keyboard.util.Status

class LetterInputMode (
    private val keypad: Keypad
): InputMode {
    private var currentStatus = Status.ALPHA_CAP

    private var charIdx = -1

    override val status: Status
        get() = this.currentStatus

    override fun getKeyCommandResult(command: Command, key: Key?, repeatCount: Int,
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
        charIdx++
        val letter = keypad.getCharacter(key, charIdx)
        return KeyPressResult(
            true,
            null,
            word = letter.toString(),
            commitDelay = 1
        )
    }

    override fun resolveCodeWord(codeWord: String, candidates: List<String>, final: Boolean,
                                 resetToWord: String?): String? {
        charIdx = -1
        return null
    }
}