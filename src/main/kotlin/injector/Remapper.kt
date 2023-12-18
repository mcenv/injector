package injector

import org.objectweb.asm.commons.Remapper

internal class Remapper(
    private val mapping: Mapping,
    private val hierarchy: TypeHierarchy,
) : Remapper() {
    override fun mapMethodName(owner: String, name: String, descriptor: String): String {
        return hierarchy.findSuperType(owner) { mapping.mapMethod(it, name, mapMethodDesc(descriptor)) } ?: name
    }

    override fun mapRecordComponentName(owner: String, name: String, descriptor: String): String {
        return hierarchy.findSuperType(owner) { mapping.mapField(it, name) } ?: name
    }

    override fun mapFieldName(owner: String, name: String, descriptor: String): String {
        return hierarchy.findSuperType(owner) { mapping.mapField(it, name) } ?: name
    }

    override fun map(internalName: String): String {
        return mapping.mapClass(internalName) ?: internalName
    }
}
