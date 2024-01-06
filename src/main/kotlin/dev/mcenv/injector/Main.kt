package dev.mcenv.injector

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

fun OutputStream.writeModifiedServer(
    id: String,
    injectors: Map<String, (ClassVisitor) -> ClassVisitor> = emptyMap(),
) {
    val `package` = getPackage(id)
    val bundlerBytes = download(`package`.downloads.server)
    val versionEntryName = "META-INF/versions/$id/server-$id.jar"

    val versionBytes = JarInputStream(ByteArrayInputStream(bundlerBytes)).use { inputJar ->
        val bundlerFormat = inputJar.manifest.mainAttributes.getValue("Bundler-Format")
        if (bundlerFormat != "1.0") {
            error("Unsupported Bundler-Format: $bundlerFormat")
        }

        while (true) {
            val entry = inputJar.nextEntry ?: break
            if (entry.name == versionEntryName) {
                return@use ByteArrayOutputStream().use {
                    inputJar.transferTo(it)
                    it.toByteArray()
                }
            }
        }

        error("$versionEntryName not found")
    }

    val mappingsBytes = download(`package`.downloads.serverMappings)
    val mappings = ByteArrayInputStream(mappingsBytes).reader().use(MappingsParser::parse)
    val hierarchy = JarInputStream(ByteArrayInputStream(versionBytes)).use(TypeHierarchy::fromJar)
    val remapper = Remapper(mappings, hierarchy)
    val modifiedVersionBytes = ByteArrayOutputStream()

    JarInputStream(ByteArrayInputStream(versionBytes)).use { inputJar ->
        JarOutputStream(DigestOutputStream(modifiedVersionBytes, sha1Digest)).use { outputJar ->
            while (true) {
                val entry = inputJar.nextEntry ?: break
                val entryName = entry.name
                when {
                    entryName.startsWith("META-INF/") -> continue

                    entryName.endsWith(".json") || entryName.endsWith(".mcmeta") -> {
                        outputJar.putNextEntry(entry)
                        minifyJson(inputJar, outputJar)
                    }

                    entryName.endsWith(".class") -> {
                        val obfuscatedName = entryName.removeSuffix(".class")
                        val deobfuscatedName = remapper.map(obfuscatedName)
                        outputJar.putNextEntry(if (deobfuscatedName == obfuscatedName) entry else ZipEntry("$deobfuscatedName.class"))

                        // TODO: make hash deterministic?
                        val writer = ClassWriter(0)
                        val visitor = injectors[deobfuscatedName]?.invoke(writer) ?: writer
                        ClassReader(inputJar).accept(ClassRemapper(visitor, remapper), ClassReader.EXPAND_FRAMES)
                        outputJar.write(writer.toByteArray())
                    }

                    else -> {
                        outputJar.putNextEntry(entry)
                        inputJar.transferTo(outputJar)
                    }
                }
            }
        }
    }

    JarInputStream(ByteArrayInputStream(bundlerBytes)).use { inputJar ->
        JarOutputStream(this).let { outputJar ->
            outputJar.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            inputJar.manifest.write(outputJar)

            val modifiedVersionFileEntry =
                "${sha1Digest.digest().toHashString()}\t$id\t$id/server-$id.jar".encodeToByteArray()

            while (true) {
                val entry = inputJar.nextEntry ?: break
                outputJar.putNextEntry(entry)
                when (entry.name) {
                    "META-INF/versions.list" -> outputJar.write(modifiedVersionFileEntry)
                    "version.json" -> minifyJson(inputJar, outputJar)
                    versionEntryName -> modifiedVersionBytes.writeTo(outputJar)
                    else -> inputJar.transferTo(outputJar)
                }
            }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun minifyJson(input: InputStream, output: OutputStream) {
    Json.encodeToStream(Json.decodeFromStream<JsonElement>(input), output)
}
