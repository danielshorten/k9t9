package com.shortendesign.k9keyboard.trie

import android.util.Log
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
        if (root != null) {
            Node.prune(key, depth, LinkedBlockingQueue<Node>(listOf(root)))
        }
    }

    fun getCandidates(key: String, count: Int = 1, maxLength: Int = 0): List<String> {
        //Log.d("K9Input", "getCandidates()")
        val node = find(key) ?: return emptyList()
        val values = Node.collectValues(LinkedBlockingQueue(listOf(node)), TreeSet(), count, maxLength)
        val sortedValues = values.sortedBy{ -it.weight}
        //Log.d("K9Input", "${sortedValues.map { value -> value.weight }}")
        return sortedValues.map { value -> value.value }
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
