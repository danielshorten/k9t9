package com.shortendesign.k9keyboard

import com.shortendesign.k9keyboard.inputmode.CaseTransformer
import com.shortendesign.k9keyboard.util.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

class CaseTransformerTest {
    var transformer: CaseTransformer? = null

    @Before
    fun setup() {
        val transformer = CaseTransformer(Status.WORD, Status.WORD_CAP, Status.WORD_UPPER)
        this.transformer = transformer
    }

    @Test
    fun testTypingShiftDelete() {
        // Mask with length 1 - should capitalize first letter by default
        transformer?.signalTyping(1)
        assertEquals("A", transformer?.applyCaseMask("a"))

        // Type another letter
        transformer?.signalTyping(2)
        // Shift mode
        transformer?.shiftMode()
        // Type another letter
        transformer?.signalTyping(3)
        assertEquals("AaA", transformer?.applyCaseMask("aaa"))

        // Delete typing
        transformer?.signalDelete(2)
        transformer?.signalDelete(1)
        transformer?.signalDelete(0)
        // Mask should be blank
        assertEquals("aaa", transformer?.applyCaseMask("aaa"))
    }

    @Test
    fun testRegisterMaskDigitSwitchingOffZero() {
        transformer?.init("Aaaa")

        // Attempt to switch off index 5
        transformer?.signalDelete(6)
        // Mask should be unchanged
        assertEquals("Aaaa", transformer?.applyCaseMask("aaaa"))
    }
}
