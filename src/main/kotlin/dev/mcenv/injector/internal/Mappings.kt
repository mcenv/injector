package dev.mcenv.injector.internal

internal class Mappings(
    private val classMappings: Map<String, ClassMapping>,
) {
    fun mapClass(name: String): String? {
        return classMappings[name]?.name
    }

    fun mapField(owner: String, name: String): String? {
        return classMappings[owner]?.fieldNames?.get(name)
    }

    fun mapMethod(owner: String, name: String, descriptor: String): String? {
        return classMappings[owner]?.methodNames?.get(MethodKey(name, descriptor))
    }

    class ClassMapping(
        val name: String,
        val fieldNames: Map<String, String>,
        val methodNames: Map<MethodKey, String>,
    )

    data class MethodKey(
        private val obfuscatedName: String,
        private val descriptor: String,
    )
}
