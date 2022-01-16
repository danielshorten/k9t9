package com.shortendesign.k9keyboard

import com.shortendesign.k9keyboard.entity.Word

interface K9InputMethodService {

    suspend fun findCandidates(word: String): List<Word>

    fun setComposingText(text: String, cursorPosition: Int)

    fun commitText(text: String, cursorPosition: Int)
}