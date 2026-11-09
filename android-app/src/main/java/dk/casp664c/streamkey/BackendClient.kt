package dk.casp664c.streamkey

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Err(val message: String) : ApiResult<Nothing>
}

data class ApiSession(val token: String, val username: String)
data class RemoteProfile(val id: String, val name: String, val server: String, val hasKey: Boolean)

object ApiParsing {
    fun session(json: JSONObject) =
        ApiSession(json.getString("token"), json.getJSONObject("user").getString("username"))

    fun profile(json: JSONObject) = RemoteProfile(
        json.getString("id"),
        json.getString("name"),
        json.getString("server"),
        json.optBoolean("hasKey", false)
    )

    fun errorMessage(status: Int, code: String?): String = when (code) {
        "INVALID_CREDENTIALS" -> "Forkert brugernavn eller adgangskode."
        "REGISTRATION_CLOSED" -> "Oprettelse af konti er lukket på serveren."
        "INVALID_INVITE_CODE" -> "Ugyldig invitationskode."
        "USERNAME_TAKEN" -> "Brugernavnet er optaget."
        "INVALID_USERNAME" -> "Brugernavn: 3-32 tegn (a-z, 0-9, _ . -)."
        "INVALID_PASSWORD" -> "Adgangskoden skal være 10-128 tegn."
        "RATE_LIMITED" -> "For mange forsøg. Vent lidt og prøv igen."
        "UNAUTHORIZED" -> "Din session er udløbet. Log ind igen."
        "KEY_STORAGE_DISABLED" -> "Serveren har ikke slået nøglelagring til."
        "PROFILE_LIMIT" -> "Du har nået grænsen for profiler på serveren."
        "NOT_FOUND", "NO_KEY_STORED" -> "Findes ikke på serveren."
        "KEY_DECRYPT_FAILED" -> "Serveren kunne ikke læse den gemte nøgle."
        else -> "Serveren svarede med fejl (HTTP $status)."
    }
}

/** Talks to the user's own Altare backend. HTTPS only; passwords are never stored. */
class BackendClient(baseUrl: String) {
    private val base = baseUrl.trim().trimEnd('/')

    init {
        require(BackendProbe.isValidHttpsBase(baseUrl)) { "Backend-adressen skal være en gyldig HTTPS-adresse." }
    }

    private fun <T> call(
        method: String,
        path: String,
        token: String? = null,
        body: JSONObject? = null,
        parse: (JSONObject) -> T
    ): ApiResult<T> {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 6000
                readTimeout = 8000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                if (token != null) setRequestProperty("Authorization", "Bearer $token")
                if (method == "POST" || method == "PUT") {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write((body ?: JSONObject()).toString().toByteArray(Charsets.UTF_8)) }
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (status in 200..299) {
                ApiResult.Ok(parse(json))
            } else {
                ApiResult.Err(ApiParsing.errorMessage(status, json.optString("error")))
            }
        } catch (_: JSONException) {
            ApiResult.Err("Uventet svar fra serveren.")
        } catch (_: IOException) {
            ApiResult.Err("Kunne ikke få forbindelse til serveren.")
        } finally {
            connection?.disconnect()
        }
    }

    fun login(username: String, password: String): ApiResult<ApiSession> = call(
        "POST", "/api/v1/auth/login",
        body = JSONObject().put("username", username.trim()).put("password", password),
        parse = ApiParsing::session
    )

    fun register(username: String, password: String, inviteCode: String): ApiResult<ApiSession> {
        val body = JSONObject().put("username", username.trim()).put("password", password)
        if (inviteCode.isNotBlank()) body.put("inviteCode", inviteCode.trim())
        return call("POST", "/api/v1/auth/register", body = body, parse = ApiParsing::session)
    }

    fun logout(token: String): ApiResult<Unit> =
        call("POST", "/api/v1/auth/logout", token = token) { }

    fun listProfiles(token: String): ApiResult<List<RemoteProfile>> =
        call("GET", "/api/v1/profiles", token = token) { json ->
            val array = json.getJSONArray("profiles")
            (0 until array.length()).map { ApiParsing.profile(array.getJSONObject(it)) }
        }

    fun createProfile(token: String, name: String, server: String, streamKey: String?): ApiResult<RemoteProfile> {
        val body = JSONObject().put("name", name.trim()).put("server", server.trim())
        if (!streamKey.isNullOrBlank()) body.put("streamKey", streamKey.trim())
        return call("POST", "/api/v1/profiles", token = token, body = body, parse = ApiParsing::profile)
    }

    /** Fetches the decrypted key on demand; list endpoints never contain it. */
    fun revealKey(token: String, id: String): ApiResult<StreamProfile> =
        call("POST", "/api/v1/profiles/$id/reveal-key", token = token) { json ->
            StreamProfile(json.getString("id"), "", json.getString("server"), json.getString("streamKey"))
        }

    fun deleteProfile(token: String, id: String): ApiResult<Unit> =
        call("DELETE", "/api/v1/profiles/$id", token = token) { }
}
