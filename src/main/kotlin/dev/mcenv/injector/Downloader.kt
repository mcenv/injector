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

private val versionManifestUrl: URL = urlOf("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
private val json: Json = Json { ignoreUnknownKeys = true }

@OptIn(ExperimentalSerializationApi::class)
internal fun getPackage(id: String): Package {
    val manifest = getVersionManifest()
    val version = manifest.versions.firstOrNull { it.id == id }
        ?: error("Not found: $id ")
    return version.url.openStream().useWithDigest(sha1Digest, version.sha1, json::decodeFromStream)
        ?: error("Failed verification: $id")
}

internal fun download(download: Package.Downloads.Download): ByteArray {
    val bytes = ByteArrayOutputStream()
    download.url.openStream()
        .useWithDigest(sha1Digest, download.sha1) { it.transferTo(bytes) }
        ?.takeIf { bytes.size().toLong() == download.size }
        ?: error("Failed verification: ${download.url}")
    return bytes.toByteArray()
}

@OptIn(ExperimentalSerializationApi::class)
private fun getVersionManifest(): VersionManifest {
    return versionManifestUrl.openStream().use(json::decodeFromStream)
}

@Suppress("NOTHING_TO_INLINE")
private inline fun urlOf(string: String): URL {
    return URI(string).toURL()
}

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
