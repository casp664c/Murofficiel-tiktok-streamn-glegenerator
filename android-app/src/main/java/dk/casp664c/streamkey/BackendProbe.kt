package dk.casp664c.streamkey

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object BackendProbe {
    fun isValidHttpsBase(raw: String): Boolean = try {
        val u = URI(raw.trim())
        u.scheme.equals("https", ignoreCase = true) &&
            !u.host.isNullOrBlank() &&
            u.userInfo == null && u.fragment == null &&
            u.query == null
    } catch (_: Exception) {
        false
    }

    /** No keys, cookies or access tokens are transmitted. */
    fun check(baseUrl: String): String {
        if (!isValidHttpsBase(baseUrl)) {
            return "Indtast en gyldig HTTPS-adresse til din egen backend."
        }
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(baseUrl.trim().trimEnd('/') + "/health")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode != 200) {
                "Serveren svarede med HTTP " + connection.responseCode + "."
            } else {
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                if (json.optString("service") == "live-key-studio-backend" &&
                    json.optString("status") == "ok"
                ) {
                    "Forbindelsen virker. Backend " + json.optString("version", "ukendt") +
                        " (TikTok-generering ikke aktiv)."
                } else {
                    "Forbindelse oprettet, men dette er ikke vores backend."
                }
            }
        } catch (_: Exception) {
            "Kunne ikke få forbindelse. Kontrollér HTTPS, adresse og serverstatus."
        } finally {
            connection?.disconnect()
        }
    }
}
