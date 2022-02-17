package com.shortendesign.k9keyboard.trie

class Value(
    val value: String,
    val weight: Int = 0
):Comparable<Value> {
    override fun compareTo(other: Value): Int {
        if (this.value.equals(other.value)) {
            return 0
        }
        return if (this.weight - other.weight == 0) 1 else this.weight - other.weight
    }
}
