package injector

data class Mapping(
    val classMappings: Map<String, ClassMapping>,
)

data class ClassMapping(
    val name: String,
    val fieldNames: Map<String, String>,
    val methodNames: Map<MethodKey, String>,
)

data class MethodKey(
    val obfuscatedName: String,
    val descriptor: String,
)
