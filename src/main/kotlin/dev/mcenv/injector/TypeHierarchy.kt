package dev.mcenv.injector

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes.ASM9
import java.util.jar.JarInputStream

internal class TypeHierarchy private constructor() {
    private val superTypes: MutableMap<String, MutableSet<String>> = hashMapOf()

    fun findSuperType(thisType: String, transform: (String) -> String?): String? {
        return superTypesOf(thisType).firstNotNullOfOrNull(transform)
    }

    private fun superTypesOf(thisType: String): Collection<String> {
        val superTypes = hashSetOf<String>()
        val worklist = mutableListOf(thisType)
        while (worklist.isNotEmpty()) {
            val current = worklist.removeLast()
            superTypes += current
            worklist += this.superTypes[current] ?: continue
        }
        return superTypes
    }

    private infix fun String.isSubtypeOf(superType: String) {
        superTypes.getOrPut(this) { hashSetOf() } += superType
    }

    companion object {
        fun fromJar(input: JarInputStream): TypeHierarchy {
            return TypeHierarchy().apply {
                val superTypeCollector = object : ClassVisitor(ASM9) {
                    override fun visit(
                        version: Int,
                        access: Int,
                        name: String,
                        signature: String?,
                        superName: String?,
                        interfaces: Array<out String>?,
                    ) {
                        superName?.let { name isSubtypeOf it }
                        interfaces?.forEach { name isSubtypeOf it }
                    }
                }

                while (true) {
                    val entry = input.nextEntry ?: break
                    if (entry.name.endsWith(".class")) {
                        ClassReader(input).accept(superTypeCollector, ClassReader.EXPAND_FRAMES)
                    }
                }
            }
        }
    }
}
