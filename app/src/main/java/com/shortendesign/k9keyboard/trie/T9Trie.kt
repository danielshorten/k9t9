package com.shortendesign.k9keyboard.trie

import java.util.*
import java.util.concurrent.LinkedBlockingQueue

class T9Trie {
    var root: Node? = null
    var currentNode: Node? = null

    fun add(key: String, value: String, weight: Int = 0) {
        val root = this.root ?: this.initRoot()
        Node.addKey(root, key, Value(value, weight))
    }

    fun get(key: String): String? {
        return Node.nextWord(find(key))
    }

    fun prune(key: String, depth: Int = 1) {
        Node.prune(key, depth, LinkedBlockingQueue<Node>(listOf(root)))
    }

    fun getCandidates(key: String, count: Int = 1): List<String> {
        val node = find(key) ?: return emptyList()
        val values = Node.collectValues(LinkedBlockingQueue(listOf(node)), TreeSet(), count)
        return values.map { value -> value.value }
    }

    fun find(key: String): Node? {
        return if (this.root == null) null else Node.search(this.root!!, key)
    }

    fun clear() {
        this.root = null
    }

    private fun initRoot(): Node {
        val root = Node(null, null)
        this.root = root
        return root
    }
}
