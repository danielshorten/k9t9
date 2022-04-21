package com.shortendesign.k9keyboard

import com.shortendesign.k9keyboard.entity.Word

interface K9InputMethodService {

    suspend fun findCandidates(word: String): List<Word>

    fun commitText(text: String, cursorPosition: Int)
}