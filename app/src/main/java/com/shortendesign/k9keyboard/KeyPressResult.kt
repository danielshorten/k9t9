package com.shortendesign.k9keyboard

import com.shortendesign.k9keyboard.util.Command

class KeyPressResult(
    // true if the keypress was consumed by the input method, false if it should be forwarded
    val consumed: Boolean,
    val command: Command?,
    // current code word
    val codeWord: String?,
    // Actual string to compose
    val word: String? = null,
    // Whether the keypress began recomposing
    val recomposing: Boolean = false,
    //
    val deleteBefore: Int? = null,
    val deleteAfter: Int? = null,
    val cursorOffset: Int = 0,
    // Delay before committing the word
    val commitDelay: Long = 0
) {}