package com.shortendesign.k9keyboard.trie

import java.util.*
import kotlin.collections.HashMap

open class Node (
    val key: Char?,
)
{
    val children = Array<Node?>(charSetSize()) { null }
    internal val values = LinkedList<String>()

    companion object {
        var valueMap = HashMap<Char, Int>()

        fun charSetSize() = valueMap.keys.size

        fun setSupportedChars(chars: String) {
            valueMap.clear()
            chars.forEachIndexed { index, char ->
                valueMap.set(char, index)
            }
        }

        fun addKey(node: Node, key: String, value: String, curChar: Int = 0) {
            if (curChar == key.length) {
                node.values.add(value)
            }
            else {
                val idx = valueMap[key[curChar]]!!
                var child = node.children[idx]
                if (child == null) {
                    child = Node(key[curChar])
                    node.children[idx] = child
                }
                addKey(child, key, value, curChar + 1)
            }
        }

        fun search(node: Node, key: String, curChar: Int = 0): Node? {
            if (curChar == key.length) {
                return node
            }
            val idx = valueMap[key[curChar]]!!
            if (node.children[idx] != null) {
                return search(node.children[idx]!!, key, curChar + 1)
            }
            return null
        }

        fun nextWord(node: Node?): String? {
            if (node == null) {
                return null
            }
            if (node.values.isEmpty()) {
                node.children.forEach {
                    if (it != null) {
                        return nextWord(it)
                    }
                }
            }
            else {
                return node.values[0]
            }
            return null
        }

        fun prune(key: String, maxDepth: Int, nodes: Queue<Node>, curDepth: Int = 0) {
            val nodesInQueue = nodes.size
            for (i in 0 until nodesInQueue) {
                val node = nodes.remove()
                node.children.forEachIndexed { index, child ->
                    if (child != null) {
                        when {
                            // We want to save at least this level of the tree
                            curDepth < maxDepth -> {
                                nodes.add(child)
                            }
                            // We haven't reached the full key, and the child we're looking at
                            // matches the next character in the key
                            curDepth < key.length && index == valueMap[key[curDepth]] -> {
                                nodes.add(child)
                            }
                            // Otherwise we want to prune this part of the tree
                            else -> {
                                node.children[index] = null
                            }
                        }
                    }
                }
            }
            if (!nodes.isEmpty()) {
                return prune(key, maxDepth, nodes, curDepth + 1)
            }
        }
    }
}