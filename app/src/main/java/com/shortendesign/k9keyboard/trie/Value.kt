package com.shortendesign.k9keyboard.trie


class Value(
    val value: String,
    val weight: Int = 0
):Comparable<Value> {

    override fun compareTo(other: Value): Int {
        if (this.value == other.value) {
            return 0
        }
        val alphaDiff = this.value.compareTo(other.value)
        val weightDiff = this.weight - other.weight
        return (-weightDiff * 2) + alphaDiff
    }
}
