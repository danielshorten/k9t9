package com.shortendesign.k9keyboard.inputmode

import com.shortendesign.k9keyboard.KeyPressResult
import com.shortendesign.k9keyboard.util.Status

interface InputMode {
    val status: Status

    fun getKeyCodeResult(keyCode: Int): KeyPressResult?

    fun resolveCodeWord(codeWord: String, candidates: List<String>, final: Boolean = false): String?
}
