package dev.mcenv.injector

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.net.URI
import java.net.URL

@Serializable
data class VersionManifest(
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

@Serializable
data class Package(
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

object URLSerializer : KSerializer<URL> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("URL", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: URL) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): URL {
        return URI(decoder.decodeString()).toURL()
    }
}
