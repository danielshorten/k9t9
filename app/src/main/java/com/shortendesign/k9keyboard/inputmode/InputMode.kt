package com.shortendesign.k9keyboard.inputmode

import com.shortendesign.k9keyboard.KeyPressResult
import com.shortendesign.k9keyboard.util.Key
import com.shortendesign.k9keyboard.util.KeyCommandResolver
import com.shortendesign.k9keyboard.util.Status
import java.util.Properties

interface InputMode {
    val status: Status

    fun load(parent: KeyCommandResolver, properties: Properties?)

    fun getKeyCodeResult(key: Key, repeatCount: Int = 0, longPress: Boolean = false,
                         textBeforeCursor: CharSequence? = null,
                         textAfterCursor: CharSequence? = null): KeyPressResult?

    fun resolveCodeWord(codeWord: String, candidates: List<String>, final: Boolean = false,
                        resetToWord: String? = null): String?
}
