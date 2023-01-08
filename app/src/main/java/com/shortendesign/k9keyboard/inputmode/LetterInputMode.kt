package com.shortendesign.k9keyboard.inputmode

import com.shortendesign.k9keyboard.KeyPressResult
import com.shortendesign.k9keyboard.Keypad
import com.shortendesign.k9keyboard.util.Command
import com.shortendesign.k9keyboard.util.Key
import com.shortendesign.k9keyboard.util.KeyCommandResolver
import com.shortendesign.k9keyboard.util.Status
import java.util.*

class LetterInputMode (
    private val keypad: Keypad
): InputMode {
    private var currentStatus = Status.ALPHA_CAP

    private var charIdx = -1
    private var currentKey: Key? = null
    private var keyCommandResolver: KeyCommandResolver? = null

    override val status: Status
        get() = this.currentStatus

    override fun load(parent: KeyCommandResolver, properties: Properties?) {
        if (keyCommandResolver != null)  {
            return
        }
        val resolver = KeyCommandResolver(
            hashMapOf(
                Key.STAR to Command.CHARACTER,
            ),
            hashMapOf(
                Key.N0 to Command.CHARACTER,
                Key.N1 to Command.CHARACTER,
                Key.N2 to Command.CHARACTER,
                Key.N3 to Command.CHARACTER,
                Key.N4 to Command.CHARACTER,
                Key.N5 to Command.CHARACTER,
                Key.N6 to Command.CHARACTER,
                Key.N7 to Command.CHARACTER,
                Key.N8 to Command.CHARACTER,
                Key.N9 to Command.CHARACTER
            ),
            parent
        )
        if (properties != null) {
            resolver.overrideFromProperties(properties, "command.word")
        }
        keyCommandResolver = resolver
    }

    override fun getKeyCodeResult(keyCode: Int, repeatCount: Int,
                                  longPress: Boolean, textBeforeCursor: CharSequence?,
                                  textAfterCursor: CharSequence?): KeyPressResult {
        val key = keypad.getKey(keyCode)
        val command = if (key != null) keyCommandResolver?.getCommand(key, longPress) else null
        // Swallow regular keypress repeats that arent navigate or delete commands
        if (!longPress && repeatCount > 0 && !setOf(Command.NAVIGATE, Command.DELETE).contains(command)) {
            return KeyPressResult(true, null, null)
        }

        return when (command) {
            Command.CHARACTER -> handleCharacter(key!!, longPress)
            Command.SHIFT_MODE -> KeyPressResult(true, null, null)
            Command.SPACE -> addSpace(command == Command.NEWLINE)
            else -> KeyPressResult(false, null, null)
        }
    }

    private fun handleCharacter(key: Key, longPress: Boolean): KeyPressResult {
        if (key != currentKey) {
            charIdx = 0
        }
        else {
            charIdx++
        }
        val delay = when {
            (key != currentKey) || longPress -> 0L
            else -> 700L
        }
        val offset = when {
            longPress -> -1
            key != currentKey -> 700
            else -> 0
        }
        val character = if (!longPress) keypad.getCharacter(key, charIdx) else keypad.getDigit(key)
        currentKey = key
        return KeyPressResult(
            true,
            null,
            null,
            word = character.toString(),
            commitDelay = delay,
            cursorOffset = offset
        )
    }

    private fun addSpace(newline: Boolean = false): KeyPressResult {
//        if (lastWordWasPeriod) {
//            currentStatus = Status.WORD_CAP
//        }
        return KeyPressResult(
            true,
            null,
            null,
            word = " "
        )
    }


    override fun resolveCodeWord(codeWord: String, candidates: List<String>, final: Boolean,
                                 resetToWord: String?): String? {
        charIdx = -1
        currentKey = null
        return null
    }
}