package app.slothlobster.mailbox

import java.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM for mailbox files: "SLM1", a 12-byte nonce, then the ciphertext with its 16-byte
 * tag. The file type ("order", "catch", "status") is authenticated too, so a catch can't be
 * passed off as an order. Without the key a file is unreadable, and any change to it is caught.
 */
object MailboxCrypto {
    private val MAGIC = "SLM1".toByteArray()
    private const val NONCE = 12
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    fun newKey(): ByteArray = ByteArray(32).also(random::nextBytes)

    fun seal(key: ByteArray, type: String, plain: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad(type))
        return MAGIC + nonce + cipher.doFinal(plain)
    }

    /** The plain bytes, or null if the file isn't ours, was changed, or isn't finished yet. */
    fun open(key: ByteArray, type: String, sealed: ByteArray): ByteArray? {
        if (sealed.size < MAGIC.size + NONCE + 16) return null
        if (!sealed.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val nonce = sealed.copyOfRange(MAGIC.size, MAGIC.size + NONCE)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(aad(type))
            cipher.doFinal(sealed, MAGIC.size + NONCE, sealed.size - MAGIC.size - NONCE)
        }.getOrNull()
    }

    private fun aad(type: String) = "slothlobster-v1:$type".toByteArray()

    /** The key as a pairing code to type or paste into the Lobster: base64url, no padding. */
    fun pairingCode(key: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(key)

    fun fromPairingCode(code: String): ByteArray? = runCatching {
        Base64.getUrlDecoder().decode(code.trim().replace(" ", ""))
    }.getOrNull()?.takeIf { it.size == 32 }
}
