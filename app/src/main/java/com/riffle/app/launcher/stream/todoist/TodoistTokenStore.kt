package com.riffle.app.launcher.stream.todoist

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A Todoist personal API token. [toString] is redacted so the token cannot reach a log, a crash message or a
 * diagnostic through string formatting; the only way to the text is [reveal], used to build the Authorization header.
 */
class TodoistToken(private val value: String) {
    init {
        require(value.isNotBlank()) { "A token cannot be blank." }
    }

    fun reveal(): String = value

    override fun toString(): String = "TodoistToken(redacted)"

    override fun equals(other: Any?): Boolean = other is TodoistToken && other.value == value

    override fun hashCode(): Int = 0
}

/** Where the one token lives. Blocking (a Keystore operation): call off the main thread. */
interface TodoistTokenStore {
    fun read(): TodoistToken?

    fun save(token: TodoistToken)

    fun clear()
}

/** Encrypts and decrypts the token's bytes. [decrypt] is null when the blob cannot be read (key lost or changed). */
interface TokenCipher {
    fun encrypt(plain: ByteArray): ByteArray

    fun decrypt(blob: ByteArray): ByteArray?
}

/**
 * The device-local file that holds the encrypted token. It must never be part of Android Auto Backup or device
 * transfer (`res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` exclude it), and the in-app backup
 * document has no field for it. The key is in the Android Keystore and cannot leave the device, so a copy of the
 * file elsewhere is unreadable anyway.
 */
internal const val TODOIST_TOKEN_PREFS_NAME = "riffle_todoist_token"
private const val TOKEN_KEY = "token"

/** The token, encrypted with [cipher], in [prefs]. Reads are cached in memory after the first. */
class EncryptedTodoistTokenStore(
    private val prefs: SharedPreferences,
    private val cipher: TokenCipher,
) : TodoistTokenStore {
    private val lock = Any()
    private var loaded = false
    private var cached: TodoistToken? = null

    override fun read(): TodoistToken? =
        synchronized(lock) {
            if (!loaded) {
                cached = load()
                loaded = true
            }
            cached
        }

    override fun save(token: TodoistToken) {
        synchronized(lock) {
            val blob = cipher.encrypt(token.reveal().toByteArray(Charsets.UTF_8))
            prefs.edit().putString(TOKEN_KEY, Base64.getEncoder().encodeToString(blob)).commit()
            cached = token
            loaded = true
        }
    }

    override fun clear() {
        synchronized(lock) {
            prefs.edit().remove(TOKEN_KEY).commit()
            cached = null
            loaded = true
        }
    }

    @Suppress("ReturnCount")
    private fun load(): TodoistToken? {
        val text = prefs.getString(TOKEN_KEY, null) ?: return null
        val plain =
            runCatching { cipher.decrypt(Base64.getDecoder().decode(text)) }.getOrNull()
                ?: return null.also { prefs.edit().remove(TOKEN_KEY).commit() }
        return runCatching { TodoistToken(String(plain, Charsets.UTF_8)) }.getOrNull()
    }
}

private const val KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "riffle_todoist_token_key"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val IV_BYTES = 12
private const val TAG_BITS = 128

/** AES-256-GCM with a Keystore key that never leaves the device. Blob = 12-byte IV followed by the ciphertext. */
class AndroidKeystoreTokenCipher : TokenCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray? =
        runCatching {
            require(blob.size > IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
            cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
        }.getOrNull()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}

internal fun androidTodoistTokenStore(context: Context): TodoistTokenStore =
    EncryptedTodoistTokenStore(
        context.applicationContext.getSharedPreferences(TODOIST_TOKEN_PREFS_NAME, Context.MODE_PRIVATE),
        AndroidKeystoreTokenCipher(),
    )
