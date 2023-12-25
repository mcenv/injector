package injector

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.Type

internal class ServerModNameModifier(
    private val serverModName: String,
    visitor: ClassVisitor,
) : ClassVisitor(ASM9, visitor) {
    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor {
        val parent = super.visitMethod(access, name, descriptor, signature, exceptions)
        return if (name == "getServerModName" && descriptor == getServerModName) {
            object : MethodVisitor(ASM9, parent) {
                override fun visitLdcInsn(value: Any?) {
                    super.visitLdcInsn(serverModName)
                }
            }
        } else {
            parent
        }
    }

    companion object {
        const val CLASS: String = "net/minecraft/server/MinecraftServer"
        private val getServerModName: String = Type.getMethodDescriptor(Type.getType(String::class.java))
    }
}
