package injector

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

@OptIn(ExperimentalSerializationApi::class)
@Suppress("NAME_SHADOWING")
fun modifyServerJar(
    inputPath: Path,
    outputPath: Path,
    mapping: Mapping,
    serverModName: String,
    visitors: Map<String, (ClassVisitor) -> ClassVisitor>,
) {
    val modifiedVersionBytes = ByteArrayOutputStream()
    val digest = MessageDigest.getInstance("SHA-256") // TODO: make hash deterministic

    val (versionId, versionPath, versionEntryName) = JarFile(inputPath.toFile()).use { inputJar ->
        val bundlerFormat = inputJar.manifest.mainAttributes.getValue("Bundler-Format")
        if (bundlerFormat != "1.0") {
            error("Unsupported Bundler-Format: $bundlerFormat")
        }

        val (_, id, path) = inputJar
            .getInputStream(inputJar.getEntry("META-INF/versions.list"))
            .bufferedReader()
            .use { it.readLine().split("\t") }
        val versionEntryName = "META-INF/versions/$path"
        val hierarchy = JarInputStream(inputJar.getInputStream(inputJar.getEntry(versionEntryName)))
            .use(TypeHierarchy::fromJar)
        val remapper = Remapper(mapping, hierarchy)
        val visitors = HashMap(visitors).also { visitors ->
            // TODO: don't overwrite existing visitor
            visitors[ServerModNameModifier.CLASS] = { ServerModNameModifier(serverModName, it) }
        }

        JarOutputStream(DigestOutputStream(modifiedVersionBytes, digest)).use { outputJar ->
            JarInputStream(inputJar.getInputStream(inputJar.getEntry(versionEntryName))).use { inputJar ->
                while (true) {
                    val entry = inputJar.nextEntry ?: break
                    val entryName = entry.name
                    when {
                        entryName.startsWith("META-INF/") -> continue

                        entryName.endsWith(".json") || entryName.endsWith(".mcmeta") -> {
                            outputJar.putNextEntry(entry)
                            val element: JsonElement = Json.decodeFromStream(inputJar)
                            Json.encodeToStream(element, outputJar)
                        }

                        entryName.endsWith(".class") -> {
                            val obfuscatedName = entryName.removeSuffix(".class")
                            val deobfuscatedName = remapper.map(obfuscatedName)
                            outputJar.putNextEntry(if (deobfuscatedName == obfuscatedName) entry else ZipEntry("$deobfuscatedName.class"))

                            val writer = ClassWriter(0)
                            val visitor = /* visitors[deobfuscatedName]?.invoke(writer) ?: */ writer
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

        Triple(id, path, versionEntryName)
    }

    JarInputStream(inputPath.inputStream().buffered()).use { inputJar ->
        JarOutputStream(outputPath.outputStream().buffered()).use { outputJar ->
            outputJar.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            inputJar.manifest.write(outputJar)

            val modifiedVersionFileEntry = "${
                digest.digest().joinToString("") { "%02x".format(it) }
            }\t$versionId\t$versionPath".encodeToByteArray()

            while (true) {
                val entry = inputJar.nextEntry ?: break
                outputJar.putNextEntry(entry)
                when (entry.name) {
                    versionEntryName -> modifiedVersionBytes.writeTo(outputJar)

                    "META-INF/versions.list" -> outputJar.write(modifiedVersionFileEntry)

                    "version.json" -> Json.encodeToStream(Json.decodeFromStream<JsonElement>(inputJar), outputJar)

                    else -> inputJar.transferTo(outputJar)
                }
            }
        }
    }
}
