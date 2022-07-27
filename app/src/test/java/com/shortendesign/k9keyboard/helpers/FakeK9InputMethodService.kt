package com.shortendesign.k9keyboard.helpers

import com.shortendesign.k9keyboard.K9InputMethodService
import com.shortendesign.k9keyboard.entity.Word
import java.util.*


class FakeK9InputMethodService: K9InputMethodService {
    var candidates: List<Word>? = null
    var text = ""
    var composingText = ""
    var cursorPosition = 0

    override suspend fun findCandidates(word: String): List<Word> {
        return candidates!!
    }

    override fun commitText(text: String, cursorPosition: Int) {
        this.text = text
        this.cursorPosition = cursorPosition
    }
}