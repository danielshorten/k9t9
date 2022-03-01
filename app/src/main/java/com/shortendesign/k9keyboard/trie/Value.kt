package com.shortendesign.k9keyboard.trie


class Value(
    val value: String,
    val weight: Int = 0
):Comparable<Value> {

    override fun compareTo(other: Value): Int {
        return this.value.compareTo(other.value)
    }
}
