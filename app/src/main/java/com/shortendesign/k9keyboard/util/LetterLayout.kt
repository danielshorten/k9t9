package com.shortendesign.k9keyboard.util

object LetterLayout {
    val enUS = mapOf(
        Key.N0 to listOf('0'),
        Key.N1 to listOf('1', '\'','.','?','!',',','-','@','$',':','(',')'),
        Key.N2 to listOf('2', 'a','b','c'),
        Key.N3 to listOf('3', 'd','e','é','f'),
        Key.N4 to listOf('4', 'g','h','i'),
        Key.N5 to listOf('5', 'j','k','l'),
        Key.N6 to listOf('6', 'm','n','o'),
        Key.N7 to listOf('7', 'p','q','r','s'),
        Key.N8 to listOf('8', 't','u','v'),
        Key.N9 to listOf('9', 'w','x','y','z'),
        Key.NEXT to listOf('*'),
        Key.SHIFT to listOf('#'),
    )

    val nonAlphaNumeric = setOf('*','#','\'','.','?','!',',','-','@','$',':','(',')')
}