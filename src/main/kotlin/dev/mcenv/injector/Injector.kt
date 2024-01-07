package dev.mcenv.injector

import org.objectweb.asm.ClassVisitor

interface Injector {
    val className: String

    fun inject(visitor: ClassVisitor): ClassVisitor
}
