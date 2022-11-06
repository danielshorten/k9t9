package com.shortendesign.k9keyboard.util

import java.util.*
import kotlin.collections.HashMap


class KeyCodeMapping(
    private val keyMap: Map<Int, Key>,
    private val shortCommandMap: Map<Key, Command>,
    private val longCommandMap: Map<Key, Command>
) {

    fun key(keyCode: Int): Key? {
        return keyMap[keyCode]
    }

    fun command(key: Key, long: Boolean = false): Command? {
        return when {
            long -> longCommandMap[key]
            else -> shortCommandMap[key]
        }
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

        val shortCommands = mapOf(
            Key.N0 to Command.SPACE,

            Key.N1 to Command.CHARACTER,
            Key.N2 to Command.CHARACTER,
            Key.N3 to Command.CHARACTER,
            Key.N4 to Command.CHARACTER,
            Key.N5 to Command.CHARACTER,
            Key.N6 to Command.CHARACTER,
            Key.N7 to Command.CHARACTER,
            Key.N8 to Command.CHARACTER,
            Key.N9 to Command.CHARACTER,

            Key.LEFT to Command.NAVIGATE,
            Key.RIGHT to Command.NAVIGATE,
            Key.UP to Command.NAVIGATE,
            Key.DOWN to Command.NAVIGATE,

            Key.STAR to Command.CYCLE_CANDIDATES,
            Key.POUND to Command.SHIFT_MODE,
            Key.DELETE to Command.DELETE,
            Key.SELECT to Command.SELECT
        )

        val longCommands = mapOf(
            Key.N0 to Command.NEWLINE,
            Key.POUND to Command.NEXT_MODE
        )

        fun fromProperties(props: Properties): KeyCodeMapping {
            val keyMap = HashMap<Int, Key>()
            val shortCommandMap = HashMap<Key, Command>()
            val longCommandMap = HashMap<Key, Command>()

            this.basic.forEach {
                val keyCode = props.getProperty("key.${it.value.name}")?.toInt() ?: it.key
                keyMap[keyCode] = it.value
            }
            this.shortCommands.forEach {
                val commandName = props.getProperty("command.short.${it.key.name}") ?: it.value.name
                shortCommandMap[it.key] = Command.valueOf(commandName)
            }
            this.longCommands.forEach {
                val commandName = props.getProperty("command.long.${it.key.name}") ?: it.value.name
                longCommandMap[it.key] = Command.valueOf(commandName)
            }
            return KeyCodeMapping(keyMap, shortCommandMap, longCommandMap)
        }

        fun default(): KeyCodeMapping {
            return KeyCodeMapping(basic, shortCommands, longCommands)
        }
    }
}
