package com.shortendesign.k9keyboard

import com.shortendesign.k9keyboard.util.Status

class KeyPressResult(
    // true if the keypress was consumed by the input method, false if it should be forwarded
    val consumed: Boolean,
    // cursor position
    val cursorPosition: Int,
    // current code word
    val codeWord: String?,
    // Literal word to add
    val word: String?,
    // Which of the candidates is selected
    val candidateIdx: Int,
) {

}