package injector

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.io.path.outputStream

@OptIn(ExperimentalSerializationApi::class)
fun unbundleServerJar(inputPath: Path, outputPath: Path, mapping: Mapping) {
    JarFile(inputPath.toFile()).use { inputJar ->
        JarOutputStream(outputPath.outputStream().buffered()).use { outputJar ->
            // TODO: check Bundler-Format in input jar

            val path = inputJar
                .getInputStream(inputJar.getEntry("META-INF/versions.list"))
                .bufferedReader()
                .use { it.readLine().split("\t")[2] }
            val hierarchy = JarInputStream(inputJar.getInputStream(inputJar.getEntry("META-INF/versions/$path")))
                .use(TypeHierarchy::fromJar)
            val remapper = Remapper(mapping, hierarchy)

            JarInputStream(inputJar.getInputStream(inputJar.getEntry("META-INF/versions/$path"))).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val entryName = entry.name
                    when {
                        entryName.startsWith("META-INF/") -> continue

                        entryName.endsWith(".json") || entryName.endsWith(".mcmeta") -> {
                            outputJar.putNextEntry(entry)
                            val element: JsonElement = Json.decodeFromStream(input)
                            Json.encodeToStream(element, outputJar)
                        }

                        entryName.endsWith(".class") -> {
                            val obfuscatedName = entryName.removeSuffix(".class")
                            val deobfuscatedName = remapper.map(obfuscatedName)
                            outputJar.putNextEntry(if (deobfuscatedName == obfuscatedName) entry else ZipEntry("$deobfuscatedName.class"))

                            val writer = ClassWriter(0)
                            ClassReader(input).accept(ClassRemapper(writer, remapper), ClassReader.EXPAND_FRAMES)
                            outputJar.write(writer.toByteArray())
                        }

                        else -> {
                            outputJar.putNextEntry(entry)
                            input.transferTo(outputJar)
                        }
                    }
                }
            }
        }
    }
}
