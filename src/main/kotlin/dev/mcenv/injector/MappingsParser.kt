package dev.mcenv.injector

import dev.mcenv.injector.Mappings.ClassMapping
import dev.mcenv.injector.Mappings.MethodKey
import java.io.Reader

internal class MappingsParser private constructor() {
    private lateinit var line: String
    private var cursor: Int = 0

    fun parseMappings(reader: Reader): Mappings {
        val classMappings = hashMapOf<String, ClassMapping>()
        lateinit var fieldNames: MutableMap<String, String>
        lateinit var methodNames: MutableMap<MethodKey, String>

        fun parseMethodMapping(type: String, deobfuscatedName: String) {
            skip("(")
            val descriptor = StringBuilder("(")
            while (peek() != ')') {
                val parameterType = parseDescriptor { it == ',' || it == ')' }
                descriptor.append(parameterType)
                when (peek()) {
                    ',' -> skip(",")
                    ')' -> break
                }
            }
            descriptor.append(')')
            descriptor.append(type)
            skip(") -> ")
            val obfuscatedName = remaining()
            methodNames[MethodKey(obfuscatedName, descriptor.toString())] = deobfuscatedName
        }

        reader.useLines { lines ->
            for (line in lines) {
                this.line = line
                cursor = 0

                when (peek()) {
                    '#' -> continue
                    ' ' -> {
                        skip("    ")
                        when (peek()) {
                            in '0'..'9' -> {
                                read({ it == ':' })
                                skip(":")
                                read({ it == ':' })
                                skip(":")
                                val type = parseDescriptor { it == ' ' }
                                skip(" ")
                                val deobfuscatedName = parseWord { it == ' ' || it == '(' }
                                if (deobfuscatedName.startsWith("lambda$")) {
                                    continue
                                }
                                parseMethodMapping(type, deobfuscatedName)
                            }

                            else -> {
                                val type = parseDescriptor { it == ' ' }
                                skip(" ")
                                val deobfuscatedName = parseWord { it == ' ' || it == '(' }
                                when (peek()) {
                                    ' ' -> {
                                        skip(" -> ")
                                        val obfuscatedName = remaining()
                                        fieldNames[obfuscatedName] = deobfuscatedName
                                    }

                                    '(' -> parseMethodMapping(type, deobfuscatedName)
                                }
                            }
                        }
                    }

                    else -> {
                        val deobfuscatedClassName = parseInternalName { it == ' ' }
                        skip(" -> ")
                        val obfuscatedClassName = parseInternalName { it == ':' }
                        fieldNames = hashMapOf()
                        methodNames = hashMapOf()
                        classMappings[obfuscatedClassName] =
                            ClassMapping(deobfuscatedClassName, fieldNames, methodNames)
                    }
                }
            }
        }

        return Mappings(classMappings)
    }

    private inline fun parseInternalName(predicate: (Char) -> Boolean): String {
        val internalName = StringBuilder()
        read(predicate) {
            when (it) {
                '.' -> internalName.append('/')
                else -> internalName.append(it)
            }
        }
        return internalName.toString()
    }

    private inline fun parseDescriptor(predicate: (Char) -> Boolean): String {
        val name = when (val name = parseInternalName { predicate(it) || it == '[' }) {
            "void" -> "V"
            "boolean" -> "Z"
            "char" -> "C"
            "byte" -> "B"
            "short" -> "S"
            "int" -> "I"
            "float" -> "F"
            "long" -> "J"
            "double" -> "D"
            else -> "L$name;"
        }
        val descriptor = StringBuilder()
        read(predicate) {
            descriptor.append('[')
            skip("]")
        }
        descriptor.append(name)
        return descriptor.toString()
    }

    private inline fun parseWord(predicate: (Char) -> Boolean): String {
        val start = cursor
        read(predicate)
        return line.substring(start, cursor)
    }

    private inline fun read(predicate: (Char) -> Boolean, block: (Char) -> Unit = {}) {
        while (true) {
            val char = peek()
            if (predicate(char)) {
                return
            }
            block(char)
            cursor++
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun skip(string: String) {
        cursor += string.length
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun peek(): Char {
        return line[cursor]
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun remaining(): String {
        return line.substring(cursor)
    }

    companion object {
        fun parse(reader: Reader): Mappings {
            return MappingsParser().parseMappings(reader)
        }
    }
}
