package dev.mcenv.injector

import org.objectweb.asm.commons.Remapper

internal class Remapper(
    private val mappings: Mappings,
    private val hierarchy: TypeHierarchy,
) : Remapper() {
    override fun mapMethodName(owner: String, name: String, descriptor: String): String {
        return hierarchy.findSuperType(owner) { mappings.mapMethod(it, name, mapMethodDesc(descriptor)) } ?: name
    }

    override fun mapRecordComponentName(owner: String, name: String, descriptor: String): String {
        return hierarchy.findSuperType(owner) { mappings.mapField(it, name) } ?: name
    }

    override fun mapFieldName(owner: String, name: String, descriptor: String): String {
        return hierarchy.findSuperType(owner) { mappings.mapField(it, name) } ?: name
    }

    override fun map(internalName: String): String {
        return mappings.mapClass(internalName) ?: internalName
    }
}
