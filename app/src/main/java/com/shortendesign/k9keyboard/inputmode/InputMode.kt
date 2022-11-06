package com.shortendesign.k9keyboard.inputmode

import com.shortendesign.k9keyboard.KeyPressResult
import com.shortendesign.k9keyboard.util.Command
import com.shortendesign.k9keyboard.util.Key
import com.shortendesign.k9keyboard.util.Status

interface InputMode {
    val status: Status

    fun getKeyCommandResult(command: Command, key: Key? = null, repeatCount: Int = 0,
                            longPress: Boolean = false, textBeforeCursor: CharSequence? = null,
                            textAfterCursor: CharSequence? = null): KeyPressResult?

    fun resolveCodeWord(codeWord: String, candidates: List<String>, final: Boolean = false,
                        resetToWord: String? = null): String?
}
