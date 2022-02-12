package com.shortendesign.k9keyboard

class KeyPressResult(
    // true if the keypress was consumed by the input method, false if it should be forwarded
    val consumed: Boolean,
    // cursor position
    val cursorPosition: Int,
    // current code word
    val codeWord: String?,
    // Actual string to compose
    val word: String? = null,
    // Delay before committing the word
    val commitDelay: Int = 0
) {}