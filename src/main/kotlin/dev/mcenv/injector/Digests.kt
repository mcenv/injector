package dev.mcenv.injector

import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest

internal inline fun <T> InputStream.useWithDigest(
    digest: MessageDigest,
    expectedHash: String,
    block: (input: InputStream) -> T,
): T? {
    return DigestInputStream(this, digest).use { input ->
        val result = block(input)
        val actualHash = digest.digest().toHashString()
        digest.reset()
        if (expectedHash == actualHash) {
            result
        } else {
            null
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
internal fun ByteArray.toHashString(): String {
    return joinToString("") { it.toHexString() }
}
