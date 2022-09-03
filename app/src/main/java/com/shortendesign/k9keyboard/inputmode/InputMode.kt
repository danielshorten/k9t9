package com.shortendesign.k9keyboard.inputmode

import com.shortendesign.k9keyboard.KeyPressResult
import com.shortendesign.k9keyboard.util.Status

interface InputMode {
    val status: Status

    fun getKeyCodeResult(keyCode: Int, textBeforeCursor: CharSequence?, textAfterCursor: CharSequence?): KeyPressResult?

    fun resolveCodeWord(codeWord: String, candidates: List<String>, final: Boolean = false,
                        resetToWord: String? = null): String?
}
