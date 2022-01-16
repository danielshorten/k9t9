package com.shortendesign.k9keyboard.util

import com.shortendesign.k9keyboard.entity.Word

object TestUtil {
    fun createWord(word: String, code: String, locale: String = "en_US", frequency: Int = 1) = Word(
        word = word,
        code = code,
        length = word.length,
        frequency = frequency,
        locale = locale
    )
}