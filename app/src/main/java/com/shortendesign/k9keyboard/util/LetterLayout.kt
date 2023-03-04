package com.shortendesign.k9keyboard.util

object LetterLayout {
    val enUS = mapOf(
        Key.N0 to listOf('0'),
        Key.N1 to listOf('1', '.','?','!',',','-','\'','"','@','$','/','%',':','(',')'),
        Key.N2 to listOf('2', 'a','b','c','ä','æ','å','à','á','â','ã','ç'),
        Key.N3 to listOf('3', 'd','e','f','è','é','ê','ë','đ'),
        Key.N4 to listOf('4', 'g','h','i','ì','í','î','ï'),
        Key.N5 to listOf('5', 'j','k','l','£'),
        Key.N6 to listOf('6', 'm','n','o','ö','ø','ò','ó','ô','õ','õ'),
        Key.N7 to listOf('7', 'p','q','r','s','ß','$'),
        Key.N8 to listOf('8', 't','u','v','ù','ú','û','ü'),
        Key.N9 to listOf('9', 'w','x','y','z','ý','þ'),
        Key.STAR to listOf('*'),
        Key.POUND to listOf('#'),
    )

    // FIXME: Don't duplicate this list
    val nonAlphaNumeric = setOf('*','#','\'','"','.','?','!',',','-','@','$','/','%',':','(',')')
}