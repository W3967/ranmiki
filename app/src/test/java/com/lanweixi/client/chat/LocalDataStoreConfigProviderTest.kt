package com.lanweixi.client.chat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * P08a：DataStore 配置存取测试（不打印明文 Key）。
 */
@RunWith(RobolectricTestRunner::class)
class LocalDataStoreConfigProviderTest {

    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var provider: LocalDataStoreConfigProvider

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        file = File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create { file }
        provider = LocalDataStoreConfigProvider(dataStore)
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `default api key is empty`() = runTest {
        val config = provider.current()
        assertEquals("", config.apiKey)
        assertEquals(LocalDataStoreConfigProvider.DEFAULT_BASE_URL, config.baseUrl)
        assertEquals(LocalDataStoreConfigProvider.DEFAULT_MODEL, config.model)
    }

    @Test
    fun `save then read`() = runTest {
        provider.saveApiKey("sk-test-123")
        assertEquals("sk-test-123", provider.current().apiKey)
    }

    @Test
    fun `update then read latest`() = runTest {
        provider.saveApiKey("sk-old")
        provider.saveApiKey("sk-new")
        assertEquals("sk-new", provider.current().apiKey)
    }

    @Test
    fun `blank input clears key`() = runTest {
        provider.saveApiKey("sk-x")
        provider.saveApiKey("   ")
        assertEquals("", provider.current().apiKey)
    }

    @Test
    fun `save model then read`() = runTest {
        provider.saveModel("deepseek-reasoner")
        assertEquals("deepseek-reasoner", provider.current().model)
    }

    @Test
    fun `save base url then read`() = runTest {
        provider.saveBaseUrl("https://example.test/v1")
        assertEquals("https://example.test/v1", provider.current().baseUrl)
    }

    @Test
    fun `blank model falls back to default`() = runTest {
        provider.saveModel("deepseek-reasoner")
        provider.saveModel("   ")
        assertEquals("deepseek-chat", provider.current().model)
    }


    @Test
    fun `api key is encrypted at rest and decrypted on read`() = runTest {
        val cipher = object : ApiKeyCipher {
            override fun encrypt(plain: String): String = plain.reversed()
            override fun decrypt(encoded: String): String = encoded.reversed()
        }
        val encryptedProvider = LocalDataStoreConfigProvider(dataStore, cipher)

        encryptedProvider.saveApiKey("sk-secret")

        val raw = dataStore.data.first()[LocalDataStoreConfigProvider.API_KEY].orEmpty()
        assertTrue("raw should carry enc: prefix but was '$raw'", raw.startsWith("enc:"))
        assertEquals("sk-secret", encryptedProvider.current().apiKey)
        assertTrue("能解开就不该标 unreadable", !encryptedProvider.current().apiKeyUnreadable)
    }

    // ── P216：加密/解密失败不再是静默降级 ─────────────────

    @Test
    fun `encrypt failure throws and never stores plaintext`() = runTest {
        val brokenCipher = object : ApiKeyCipher {
            override fun encrypt(plain: String): String = throw IllegalStateException("keystore down")
            override fun decrypt(encoded: String): String = encoded
        }
        val provider = LocalDataStoreConfigProvider(dataStore, brokenCipher)

        val failed = runCatching { provider.saveApiKey("sk-plaintext-must-not-leak") }

        assertTrue("加密失败必须抛错", failed.exceptionOrNull() is ApiKeyEncryptException)
        val raw = dataStore.data.first()[LocalDataStoreConfigProvider.API_KEY].orEmpty()
        assertEquals("加密失败后不能落任何值（尤其不能是明文）", "", raw)
    }

    @Test
    fun `encrypt failure keeps the previous key intact`() = runTest {
        val goodCipher = object : ApiKeyCipher {
            override fun encrypt(plain: String): String = plain.reversed()
            override fun decrypt(encoded: String): String = encoded.reversed()
        }
        LocalDataStoreConfigProvider(dataStore, goodCipher).saveApiKey("sk-old")
        val before = dataStore.data.first()[LocalDataStoreConfigProvider.API_KEY].orEmpty()

        val brokenCipher = object : ApiKeyCipher {
            override fun encrypt(plain: String): String = throw RuntimeException("boom")
            override fun decrypt(encoded: String): String = encoded.reversed()
        }
        val failed = runCatching {
            LocalDataStoreConfigProvider(dataStore, brokenCipher).saveApiKey("sk-new")
        }

        assertTrue(failed.exceptionOrNull() is ApiKeyEncryptException)
        assertEquals("旧密文必须保持不变", before, dataStore.data.first()[LocalDataStoreConfigProvider.API_KEY])
        assertEquals("sk-old", LocalDataStoreConfigProvider(dataStore, goodCipher).current().apiKey)
    }

    @Test
    fun `decrypt failure is reported as unreadable instead of empty key`() = runTest {
        dataStore.edit { it[LocalDataStoreConfigProvider.API_KEY] = "enc:corrupted" }
        val brokenCipher = object : ApiKeyCipher {
            override fun encrypt(plain: String): String = plain
            override fun decrypt(encoded: String): String = throw IllegalStateException("bad ciphertext")
        }
        val provider = LocalDataStoreConfigProvider(dataStore, brokenCipher)

        val config = provider.current()

        assertEquals("", config.apiKey)
        assertTrue("解不开要能和「没配 Key」区分开", config.apiKeyUnreadable)
    }

    @Test
    fun `saving blank does not wipe an unreadable ciphertext`() = runTest {
        dataStore.edit { it[LocalDataStoreConfigProvider.API_KEY] = "enc:corrupted" }
        val brokenCipher = object : ApiKeyCipher {
            override fun encrypt(plain: String): String = plain
            override fun decrypt(encoded: String): String = throw IllegalStateException("bad ciphertext")
        }
        val provider = LocalDataStoreConfigProvider(dataStore, brokenCipher)

        val failed = runCatching { provider.saveApiKey("   ") }

        assertTrue("保存空值不能静默删掉解不开的旧密文", failed.exceptionOrNull() is ApiKeyDecryptException)
        assertEquals(
            "enc:corrupted",
            dataStore.data.first()[LocalDataStoreConfigProvider.API_KEY].orEmpty(),
        )
    }

    @Test
    fun `saving a new key over unreadable ciphertext is allowed`() = runTest {
        dataStore.edit { it[LocalDataStoreConfigProvider.API_KEY] = "enc:corrupted" }
        val repaired = object : ApiKeyCipher {
            override fun encrypt(plain: String): String = "new:" + plain
            override fun decrypt(encoded: String): String =
                if (encoded.startsWith("new:")) encoded.removePrefix("new:") else error("bad ciphertext")
        }
        val provider = LocalDataStoreConfigProvider(dataStore, repaired)

        provider.saveApiKey("sk-fresh")

        assertEquals("sk-fresh", provider.current().apiKey)
        assertTrue(!provider.current().apiKeyUnreadable)
    }

}
