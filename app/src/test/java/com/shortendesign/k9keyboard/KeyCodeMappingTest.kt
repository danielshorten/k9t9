package com.shortendesign.k9keyboard

import com.shortendesign.k9keyboard.util.Key
import com.shortendesign.k9keyboard.util.KeyCodeMapping
import com.shortendesign.k9keyboard.util.LetterLayout
import org.junit.Test
import org.junit.Assert.assertEquals
import java.util.*


class KeyCodeMappingTest {

    @Test
    fun fromProperties() {
        val props = Properties();
        props.setProperty("key.DELETE", "4")
        val mapping = KeyCodeMapping.fromProperties(props)

        assertEquals(
            "Should have the custom DELETE keycode",
            Key.DELETE, mapping.key(4)
        )
        assertEquals(
            "Should have the default number 1 code",
            Key.N1, mapping.key(8)
        )
    }
}