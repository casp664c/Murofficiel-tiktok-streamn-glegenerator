package dk.casp664c.streamkey

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class StreamProfile(val id: String, val name: String, val server: String, val key: String)

/** Encrypts secrets before they touch disk. */
interface SecretCipher {
    fun encrypt(plain: String): String

    /** Returns null if the value cannot be decrypted (e.g. the Keystore key was lost). */
    fun decrypt(sealed: String): String?
}

/** Minimal key/value storage so the repository can be unit-tested without Android. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

class PrefsStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

/** AES-256-GCM with a key that never leaves the Android Keystore. */
class KeystoreCipher(private val alias: String = "live_key_studio_profiles") : SecretCipher {
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    override fun decrypt(sealed: String): String? = try {
        val raw = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, raw, 0, 12))
        String(cipher.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}

/**
 * Saved streaming profiles. Layout in the store:
 *  - `profile.ids`        comma-separated profile ids, in creation order
 *  - `profile.<id>.name`  plain text
 *  - `profile.<id>.server` plain text (not secret)
 *  - `profile.<id>.key`   stream key, encrypted (only present if one was saved)
 */
class ProfileRepository(
    private val store: KeyValueStore,
    private val cipher: SecretCipher,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    companion object {
        const val MAX_PROFILES = 50
    }

    private fun ids(): List<String> =
        store.getString("profile.ids").orEmpty().split(',').filter { it.isNotBlank() }

    fun list(): List<StreamProfile> = ids().mapNotNull { id ->
        val name = store.getString("profile.$id.name") ?: return@mapNotNull null
        val server = store.getString("profile.$id.server") ?: return@mapNotNull null
        val sealed = store.getString("profile.$id.key")
        val key = if (sealed == null) "" else cipher.decrypt(sealed).orEmpty()
        StreamProfile(id, name, server, key)
    }

    /**
     * Creates a profile, or replaces the one with [existingId].
     * Throws [IllegalArgumentException] with a user-facing Danish message on invalid input.
     */
    fun save(name: String, server: String, key: String, existingId: String? = null): StreamProfile {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty() && cleanName.length <= 60 && cleanName.none { it.isISOControl() }) {
            "Giv profilen et navn på op til 60 tegn."
        }
        require(StreamSettings.validServer(server)) {
            "Serveren skal begynde med rtmp:// eller rtmps:// og have et værtsnavn."
        }
        val cleanKey = key.trim()
        require(cleanKey.isEmpty() || StreamSettings.validKey(cleanKey)) {
            "Streamnøglen må ikke indeholde linjeskift."
        }
        val current = ids()
        val id = existingId?.takeIf { it in current } ?: newId()
        if (id !in current) {
            require(current.size < MAX_PROFILES) { "Du kan højst gemme $MAX_PROFILES profiler." }
            store.putString("profile.ids", (current + id).joinToString(","))
        }
        store.putString("profile.$id.name", cleanName)
        store.putString("profile.$id.server", server.trim())
        if (cleanKey.isEmpty()) {
            store.remove("profile.$id.key")
        } else {
            store.putString("profile.$id.key", cipher.encrypt(cleanKey))
        }
        return StreamProfile(id, cleanName, server.trim(), cleanKey)
    }

    fun delete(id: String) {
        val current = ids()
        if (id !in current) return
        store.putString("profile.ids", (current - id).joinToString(","))
        store.remove("profile.$id.name")
        store.remove("profile.$id.server")
        store.remove("profile.$id.key")
    }
}

data class SavedAccount(val baseUrl: String, val username: String, val token: String)

/** Remembers the Altare login. The bearer token is stored encrypted. */
class AccountStore(private val store: KeyValueStore, private val cipher: SecretCipher) {
    fun load(): SavedAccount? {
        val url = store.getString("account.url") ?: return null
        val user = store.getString("account.user") ?: return null
        val sealed = store.getString("account.token") ?: return null
        val token = cipher.decrypt(sealed) ?: return null
        return SavedAccount(url, user, token)
    }

    fun save(account: SavedAccount) {
        store.putString("account.url", account.baseUrl)
        store.putString("account.user", account.username)
        store.putString("account.token", cipher.encrypt(account.token))
    }

    fun clear() {
        store.remove("account.url")
        store.remove("account.user")
        store.remove("account.token")
    }
}
