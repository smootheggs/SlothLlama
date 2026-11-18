package app.localmind.assistant.data.lobster

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
 * Keeps the mailbox key wrapped by a key in Android's Keystore (in the tablet's secure hardware,
 * never readable by any app, this one included). Only the wrapped form is saved in preferences,
 * so a copy of the app's files alone doesn't reveal the mailbox key. Automatic backups keep their
 * password the same way, under their own [alias].
 */
class KeyVault(private val prefs: SharedPreferences, private val alias: String = ALIAS) {

    fun load(): ByteArray? {
        prefs.getString(KEY_WRAPPED, null)?.let { wrapped ->
            return runCatching { unwrap(Base64.getDecoder().decode(wrapped)) }.getOrNull()
        }
        // Before 1.14 the key was kept as plain text: move it into the vault.
        val plain = prefs.getString(KEY_PLAIN, null)?.let(MailboxCrypto::fromPairingCode) ?: return null
        save(plain)
        return plain
    }

    fun save(key: ByteArray) {
        val wrapped = Base64.getEncoder().encodeToString(wrap(key))
        prefs.edit().putString(KEY_WRAPPED, wrapped).remove(KEY_PLAIN).apply()
    }

    private fun wrap(key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, vaultKey())
        return cipher.iv + cipher.doFinal(key)
    }

    private fun unwrap(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, vaultKey(), GCMParameterSpec(128, data, 0, IV))
        return cipher.doFinal(data, IV, data.size - IV)
    }

    private fun vaultKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "slothlobster-mailbox"
        const val KEY_WRAPPED = "key_wrapped"
        const val KEY_PLAIN = "key"
        const val IV = 12
    }
}
