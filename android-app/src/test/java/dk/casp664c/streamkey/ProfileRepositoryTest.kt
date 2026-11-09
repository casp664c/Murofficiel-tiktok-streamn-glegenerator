package dk.casp664c.streamkey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private class MemoryStore : KeyValueStore {
    val map = linkedMapOf<String, String>()
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}

/** Not real crypto; just proves the repository only ever stores what the cipher returns. */
private class FakeCipher(var broken: Boolean = false) : SecretCipher {
    override fun encrypt(plain: String) = "sealed:" + plain.reversed()
    override fun decrypt(sealed: String): String? =
        if (broken || !sealed.startsWith("sealed:")) null else sealed.removePrefix("sealed:").reversed()
}

class ProfileRepositoryTest {
    private var counter = 0
    private fun repo(store: MemoryStore = MemoryStore(), cipher: SecretCipher = FakeCipher()) =
        ProfileRepository(store, cipher) { "id${++counter}" }

    @Test fun savesAndListsProfilesInOrder() {
        val r = repo()
        r.save("Hjemme", "rtmps://live.example.com/app", "key-1")
        r.save("Ude", "rtmp://live.example.com/app", "")
        val list = r.list()
        assertEquals(listOf("Hjemme", "Ude"), list.map { it.name })
        assertEquals("key-1", list[0].key)
        assertEquals("", list[1].key)
    }

    @Test fun streamKeyIsNeverStoredInPlainText() {
        val store = MemoryStore()
        repo(store).save("A", "rtmps://live.example.com/app", "super-secret")
        assertFalse(store.map.values.any { it == "super-secret" })
        assertTrue(store.map.values.any { it.startsWith("sealed:") })
    }

    @Test fun updatingAProfileKeepsItsIdAndCanClearTheKey() {
        val r = repo()
        val first = r.save("A", "rtmps://live.example.com/app", "k")
        val second = r.save("A2", "rtmps://live.example.com/app", "", existingId = first.id)
        assertEquals(first.id, second.id)
        assertEquals(1, r.list().size)
        assertEquals("A2", r.list()[0].name)
        assertEquals("", r.list()[0].key)
    }

    @Test fun deleteRemovesEverythingForThatProfile() {
        val store = MemoryStore()
        val r = repo(store)
        val a = r.save("A", "rtmps://live.example.com/app", "k")
        r.save("B", "rtmps://live.example.com/app", "k2")
        r.delete(a.id)
        assertEquals(listOf("B"), r.list().map { it.name })
        assertTrue(store.map.keys.none { it.contains(a.id) })
    }

    @Test fun invalidInputIsRejectedWithAMessage() {
        val r = repo()
        listOf(
            Triple("", "rtmps://live.example.com/app", "k"),
            Triple("A", "https://example.com", "k"),
            Triple("A", "rtmps://live.example.com/app", "line1\nline2")
        ).forEach { (name, server, key) ->
            try {
                r.save(name, server, key)
                fail("Expected rejection for $name / $server")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.isNotBlank())
            }
        }
        assertEquals(0, r.list().size)
    }

    @Test fun profileLimitIsEnforced() {
        val r = repo()
        repeat(ProfileRepository.MAX_PROFILES) { r.save("P$it", "rtmps://live.example.com/app", "") }
        try {
            r.save("one too many", "rtmps://live.example.com/app", "")
            fail("Expected the limit to be enforced")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun unreadableKeysDegradeToEmptyInsteadOfCrashing() {
        val store = MemoryStore()
        val cipher = FakeCipher()
        repo(store, cipher).save("A", "rtmps://live.example.com/app", "k")
        cipher.broken = true
        assertEquals("", repo(store, cipher).list()[0].key)
    }

    @Test fun accountStoreEncryptsTheToken() {
        val store = MemoryStore()
        val accounts = AccountStore(store, FakeCipher())
        assertNull(accounts.load())
        accounts.save(SavedAccount("https://example.com", "cas", "tok-123"))
        assertFalse(store.map.values.any { it == "tok-123" })
        assertEquals("tok-123", accounts.load()?.token)
        accounts.clear()
        assertNull(accounts.load())
    }
}
