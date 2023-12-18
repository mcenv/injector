package injector

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes.ASM9
import java.util.jar.JarInputStream

class TypeHierarchy private constructor() {
    private val superTypes: MutableMap<String, MutableSet<String>> = hashMapOf()

    operator fun get(thisType: String): Collection<String> {
        val superTypes = hashSetOf<String>()
        val worklist = mutableListOf(thisType)
        while (worklist.isNotEmpty()) {
            val current = worklist.removeLast()
            superTypes += current
            worklist += this.superTypes[current] ?: continue
        }
        return superTypes
    }

    operator fun set(thisType: String, superType: String) {
        superTypes.getOrPut(thisType) { hashSetOf() } += superType
    }

    companion object {
        fun fromJar(input: JarInputStream): TypeHierarchy {
            return TypeHierarchy().also { hierarchy ->
                val visitor = object : ClassVisitor(ASM9) {
                    override fun visit(
                        version: Int,
                        access: Int,
                        name: String,
                        signature: String?,
                        superName: String?,
                        interfaces: Array<out String>?,
                    ) {
                        superName?.let { hierarchy[name] = it }
                        interfaces?.forEach { hierarchy[name] = it }
                    }
                }

                while (true) {
                    val entry = input.nextEntry ?: break
                    if (!entry.name.endsWith(".class")) {
                        continue
                    }
                    ClassReader(input).accept(visitor, ClassReader.EXPAND_FRAMES)
                }
            }
        }
    }
}
