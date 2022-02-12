package com.shortendesign.k9keyboard.trie

import java.util.*
import java.util.concurrent.LinkedBlockingQueue

class T9Trie {
    var root: Node? = null
    var currentNode: Node? = null

    fun add(key: String, value: String) {
        val root = this.root ?: this.initRoot()
        Node.addKey(root, key, value)
    }

    fun get(key: String): String? {
        return Node.nextWord(find(key))
    }

    fun prune(key: String, depth: Int = 1) {
        Node.prune(key, depth, LinkedBlockingQueue<Node>(listOf(root)))
    }

    fun getCandidates(key: String, count: Int = 1): List<String> {
        val node = find(key) ?: return emptyList()
        val candidates = LinkedList<String>()
        candidates.addAll(node.values.take(count))
        if (candidates.count() < count) {
            for (child in node.children) {
                if (child != null) {
                    candidates.addAll(child.values.take(count - candidates.count()))
                }
                if (candidates.count() == count) {
                    break
                }
            }
        }
        return candidates
    }

    fun find(key: String): Node? {
        return if (this.root == null) null else Node.search(this.root!!, key)
    }

    fun clear() {
        this.root = null
    }

    private fun initRoot(): Node {
        val root = Node(null)
        this.root = root
        return root
    }
}
