package injector

import org.objectweb.asm.commons.Remapper

class Remapper(
    private val mapping: Mapping,
    private val hierarchy: TypeHierarchy,
) : Remapper() {
    override fun mapMethodName(owner: String, name: String, descriptor: String): String {
        return hierarchy[owner].firstNotNullOfOrNull {
            mapping.classMappings[it]?.methodNames?.get(MethodKey(name, descriptor))
        } ?: name
    }

    override fun mapRecordComponentName(owner: String, name: String, descriptor: String): String {
        return hierarchy[owner].firstNotNullOfOrNull {
            mapping.classMappings[it]?.fieldNames?.get(name)
        } ?: name
    }

    override fun mapFieldName(owner: String, name: String, descriptor: String): String {
        return hierarchy[owner].firstNotNullOfOrNull {
            mapping.classMappings[it]?.fieldNames?.get(name)
        } ?: name
    }

    override fun map(internalName: String): String {
        return mapping.classMappings[internalName]?.name ?: internalName
    }
}
