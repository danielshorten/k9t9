package com.shortendesign.k9keyboard.util

import java.util.*
import kotlin.collections.HashMap


class KeyCodeMapping(
    private val keyMap: Map<Int, Key>,
) {

    fun key(keyCode: Int): Key? {
        return keyMap[keyCode]
    }

    companion object {
        val basic = mapOf(
            7 to Key.N0,
            8 to Key.N1,
            9 to Key.N2,
            10 to Key.N3,
            11 to Key.N4,
            12 to Key.N5,
            13 to Key.N6,
            14 to Key.N7,
            15 to Key.N8,
            16 to Key.N9,
            17 to Key.STAR,
            18 to Key.POUND,
            67 to Key.DELETE,
            19 to Key.UP,
            20 to Key.DOWN,
            21 to Key.LEFT,
            22 to Key.RIGHT,
            66 to Key.SELECT
        )

        fun fromProperties(props: Properties): KeyCodeMapping {
            val keyMap = HashMap<Int, Key>()

            this.basic.forEach {
                val keyCode = props.getProperty("key.${it.value.name}")?.toInt() ?: it.key
                keyMap[keyCode] = it.value
            }

            return KeyCodeMapping(keyMap)
        }

        fun default(): KeyCodeMapping {
            return KeyCodeMapping(basic)
        }
    }
}
