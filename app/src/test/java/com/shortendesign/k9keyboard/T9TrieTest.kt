package com.shortendesign.k9keyboard

import com.shortendesign.k9keyboard.trie.Node
import com.shortendesign.k9keyboard.trie.T9Trie
import org.junit.Test
import org.junit.Assert.*

class T9TrieTest {

    @Test
    fun testAddAndGet() {
        val trie = T9Trie()
        Node.setSupportedChars("123456789")
        trie.add("2255", "ball")
        trie.add("364473", "émigré")

        trie.add("22", "ab")
        trie.add("23", "ad")
        trie.add("24", "ah")
        trie.add("26", "am")
        trie.add("26", "an")
        trie.add("27", "as")
        trie.add("28", "at")
        trie.add("23", "be")
        trie.add("29", "by")
        trie.add("22", "cc")

        assertEquals("ball", trie.get("2255"))
        assertEquals("émigré", trie.get("364473"))
        assertEquals("ab", trie.get("2"))
    }

    @Test
    fun testGetFullKey() {
        val trie = T9Trie()
        Node.setSupportedChars("123456789")
        trie.add("2333464", "bedding")
        val node = trie.find("2333464")

        assertEquals("2333464", node!!.getFullKey())
    }

    @Test
    fun testGetMissing() {
        val trie = T9Trie()
        Node.setSupportedChars("123456789")
        trie.add("2255", "ball")

        assertEquals(null, trie.get("2256"))
    }

    @Test
    fun testGetCandidates() {
        val trie = T9Trie()
        Node.setSupportedChars("123456789")

        trie.add("2", "a")
        trie.add("23", "ad")
        trie.add("23", "be")
        trie.add("233", "bed", 10)
        trie.add("233", "add")
        trie.add("233", "bee")
        trie.add("2333464", "bedding")
        trie.add("233333", "bedded")
        trie.add("2336684787", "adenovirus")
        trie.prune("233", 3)

        val candidates = trie.getCandidates("233", 5)
        val candidatesMaxLength = trie.getCandidates("233", 5, 3)

        assertEquals(
            listOf("bed", "add", "bedded", "bedding", "bee"),
            candidates
        )
        assertEquals(
            listOf("bed", "add", "bee", "ade"),
            candidatesMaxLength
        )
    }

    @Test
    fun testPrune() {
        val trie = T9Trie()
        Node.setSupportedChars("123456789")

        trie.add("2", "a")
        trie.add("22", "ab")
        trie.add("23", "ad")
        trie.add("23", "be")
        trie.add("233", "bed")
        trie.add("233", "add")
        trie.add("233", "bee")
        trie.add("2337", "beer")
        trie.add("24", "ah")
        trie.add("26", "am")
        trie.add("26", "an")
        trie.add("27", "as")
        trie.add("28", "at")

        trie.prune("2337", 1)

        assertEquals("a", trie.get("2"))
        assertEquals(null, trie.get("22"))
        assertEquals("ad", trie.get("23"))
        assertEquals("beer", trie.get("2337"))
    }
}