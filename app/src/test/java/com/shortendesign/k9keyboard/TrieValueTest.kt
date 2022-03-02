package com.shortendesign.k9keyboard

import com.shortendesign.k9keyboard.trie.Value
import org.junit.Test
import org.junit.Assert.*
import java.util.*

class TrieValueTest {

    @Test
    fun testCompare() {
        val set = TreeSet<Value>()

        set.add(Value("bed", 0))
        set.add(Value("add", 0))
        set.add(Value("bee", 10))
        set.addAll(sortedSetOf(Value("bed", 0), Value("bed", 0)))

        assertEquals(listOf("bee", "add", "bed"), set.sortedBy { -it.weight }.map{ value -> value.value})
    }
}