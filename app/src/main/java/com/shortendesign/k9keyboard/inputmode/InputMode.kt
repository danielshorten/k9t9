package com.shortendesign.k9keyboard.inputmode

import com.shortendesign.k9keyboard.KeyPressResult
import com.shortendesign.k9keyboard.util.Status

interface InputMode {
    val status: Status

    fun getKeyCodeResult(keyCode: Int): KeyPressResult?

    fun shouldRecomposeWord(beforeCursor: CharSequence?, afterCursor: CharSequence?): String?

    fun resolveCodeWord(codeWord: String, candidates: List<String>, final: Boolean = false): String?
}
