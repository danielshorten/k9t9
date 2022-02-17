package com.shortendesign.k9keyboard.trie

import android.util.Log
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashSet

open class Node (
    val key: Char?,
    val parent: Node?
)
{
    val children = Array<Node?>(charSetSize()) { null }
    internal val values = TreeSet<Value>()

    fun getFullKey(): String {
        return fullKey(this)
    }

    companion object {
        private val LOG_TAG: String = "K9Word"
        var valueMap = HashMap<Char, Int>()

        fun charSetSize() = valueMap.keys.size

        fun setSupportedChars(chars: String) {
            valueMap.clear()
            chars.forEachIndexed { index, char ->
                valueMap.set(char, index)
            }
        }

        fun addKey(node: Node, key: String, value: Value, curChar: Int = 0) {
            if (curChar == key.length) {
                node.values.add(value)
            }
            else {
                Log.d(LOG_TAG, "KEY: $key, curChar: $curChar")
                val idx = valueMap[key[curChar]]!!
                var child = node.children[idx]
                if (child == null) {
                    child = Node(key[curChar], node)
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
                return node.values.elementAt(0).value
            }
            return null
        }

        fun fullKey(node: Node, currentKey: StringBuilder = java.lang.StringBuilder()): String {
            if (node.parent != null) {
                currentKey.append(node.key)
                return fullKey(node.parent, currentKey)
            }
            return currentKey.reverse().toString()
        }

        fun collectValues(
            nodes: Queue<Node>, values: SortedSet<Value>, count: Int = 1
        ): SortedSet<Value> {
            val nodesInQueue = nodes.size
            for (i in 0 until nodesInQueue) {
                val node = nodes.remove()
                values.addAll(node.values)
                node.children.forEach { child ->
                    if (child != null) {
                        nodes.add(child)
                    }
                }
            }
            if (values.size >= count || nodes.isEmpty()) {
                return values
            }
            return collectValues(nodes, values, count)
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
                            // Do nothing if this is the top of the branch we want to keep
                            node.getFullKey() == key -> return
                            // Otherwise we want to prune this part of the tree
                            else  -> {
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