package dev.mcenv.injector

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URL
import java.security.MessageDigest

private val versionManifestUrl: URL = urlOf("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
private val digest: MessageDigest = MessageDigest.getInstance("SHA-1")
private val json: Json = Json { ignoreUnknownKeys = true }

internal fun getDownloads(id: String): Downloads {
    val `package` = getPackage(id)
    val server = download(id, `package`.downloads.server)
    val serverMappings = download(id, `package`.downloads.serverMappings)
    return Downloads(server, serverMappings)
}

@OptIn(ExperimentalSerializationApi::class)
private fun getPackage(id: String): Package {
    val manifest = getVersionManifest()
    val version = manifest.versions.firstOrNull { it.id == id }
        ?: throw IllegalArgumentException("Version $id not found")
    return version.url.openStream().useWithDigest(digest, version.sha1, json::decodeFromStream)
        ?: throw IllegalStateException("Version $id failed verification")
}

@OptIn(ExperimentalSerializationApi::class)
private fun getVersionManifest(): VersionManifest {
    return versionManifestUrl.openStream().use(json::decodeFromStream)
}

private fun download(id: String, download: Package.Downloads.Download): ByteArray {
    val output = ByteArrayOutputStream()
    download.url.openStream().useWithDigest(digest, download.sha1) { it.transferTo(output) }
        ?: throw IllegalStateException("Version $id failed verification")
    if (output.size().toLong() != download.size) {
        throw IllegalStateException("Version $id failed verification")
    }
    return output.toByteArray()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun urlOf(string: String): URL {
    return URI(string).toURL()
}

internal class Downloads(
    val server: ByteArray,
    val serverMappings: ByteArray,
)

@Serializable
internal data class Package(
    val downloads: Downloads,
) {
    @Serializable
    data class Downloads(
        val server: Download,
        @SerialName("server_mappings") val serverMappings: Download,
    ) {
        @Serializable
        data class Download(
            val sha1: String,
            val size: Long,
            val url: @Serializable(URLSerializer::class) URL,
        )
    }
}

@Serializable
internal data class VersionManifest(
    val latest: Latest,
    val versions: List<Version>,
) {
    @Serializable
    data class Latest(
        val release: String,
        val snapshot: String,
    )

    @Serializable
    data class Version(
        val id: String,
        val url: @Serializable(URLSerializer::class) URL,
        val sha1: String,
    )
}

private object URLSerializer : KSerializer<URL> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("URL", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: URL) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): URL {
        return urlOf(decoder.decodeString())
    }
}
