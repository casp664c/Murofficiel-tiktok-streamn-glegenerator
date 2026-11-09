package dk.casp664c.streamkey

import java.net.URI

/** Format checks only; never claims that TikTok has accepted the credentials. */
object StreamSettings {
    fun validServer(raw: String): Boolean {
        val value = raw.trim()
        if (value.any { it == '\n' || it == '\r' }) return false
        return try {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase()
            (scheme == "rtmp" || scheme == "rtmps") &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null &&
                uri.fragment == null
        } catch (_: Exception) {
            false
        }
    }

    fun validKey(raw: String): Boolean =
        raw.isNotBlank() && raw.none { it == '\n' || it == '\r' }

    fun combinedUrl(server: String, key: String): String? {
        if (!validServer(server) || !validKey(key)) return null
        val s = server.trim()
        // A query string may be part of an RTMP server address. Do not guess
        // whether the stream key should be inserted before or after it.
        if ('?' in s) return null
        return s.trimEnd('/') + "/" + key.trim()
    }
}
