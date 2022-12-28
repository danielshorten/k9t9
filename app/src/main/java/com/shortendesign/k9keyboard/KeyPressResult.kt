package com.shortendesign.k9keyboard

class KeyPressResult(
    // true if the keypress was consumed by the input method, false if it should be forwarded
    val consumed: Boolean,
    // current code word
    val codeWord: String?,
    // Actual string to compose
    val word: String? = null,
    // Whether the keypress began recomposing
    val recomposing: Boolean = false,
    //
    val cursorOffset: Int = 0,
    // Delay before committing the word
    val commitDelay: Long = 0
) {}