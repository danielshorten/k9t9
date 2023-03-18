package com.shortendesign.k9keyboard.util

import java.util.Properties
import kotlin.collections.HashMap

class KeyCommandResolver (
    private val shortCommandMap: HashMap<Key, Command> = HashMap(),
    private val longCommandMap: HashMap<Key, Command> = HashMap(),
    var parentResolver: KeyCommandResolver? = null
) {
    fun getCommand(key: Key, longPress: Boolean = false): Command? {
        val command = when {
            longPress -> longCommandMap[key]
            else -> shortCommandMap[key]
        }
        return when (command) {
            null -> parentResolver?.getCommand(key, longPress)
            else -> command
        }
    }
    fun setCommand(key: Key, command: Command, longPress: Boolean = false) {
        val parentCommand = parentResolver?.getCommand(key, longPress)
        if (parentCommand == null || command != parentCommand) {
            shortCommandMap[key] = command
        }
    }
    fun overrideFromProperties(properties: Properties, namespace: String) {
        mapOf(
            "short" to shortCommandMap,
            "long" to longCommandMap
        ).forEach { (pressType, commandMap) ->
            commandMap.forEach {
                val commandName =
                    properties.getProperty("${namespace}.${pressType}${it.key.name}") ?: it.value.name
                val command = getCommand(it.key, pressType == "long")
                if (command == null || command.name != commandName) {
                    shortCommandMap[it.key] = Command.valueOf(commandName)
                }
            }
        }
    }

    companion object {
        fun getBasic(): KeyCommandResolver {
            return KeyCommandResolver(
                HashMap(mapOf(
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

                    Key.POUND to Command.SHIFT_MODE,
                    Key.DELETE to Command.DELETE,
                    Key.SELECT to Command.SELECT

                )),
                HashMap(mapOf(
                    Key.POUND to Command.NEXT_MODE
                ))
            )
        }

        fun fromProperties(properties: Properties): KeyCommandResolver {
            val resolver = getBasic()
            resolver.overrideFromProperties(properties, "command")
            return resolver
        }
    }
}
