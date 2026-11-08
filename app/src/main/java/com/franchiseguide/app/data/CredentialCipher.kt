package com.franchiseguide.app.data

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Versioned authenticated envelope. The JCA provider generates a fresh IV for every write. */
internal class CredentialCipher(private val key: (create: Boolean) -> SecretKey) {
    companion object {
        const val PREFIX = "fgenc:v1:"
        private const val MAX_BYTES = 8 * 1024 * 1024
    }
    private fun aad(name: String): ByteArray {
        require(name.isNotEmpty() && name.length <= 512) { "Nom d'accès invalide." }
        return ("FranchiseGuide/credentials/v1/" + name).toByteArray(Charsets.UTF_8)
    }
    fun encrypt(name: String, value: String): String {
        val plain = value.toByteArray(Charsets.UTF_8)
        require(plain.size <= MAX_BYTES) { "Accès trop volumineux." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        cipher.updateAAD(aad(name))
        check(cipher.iv.size == 12) { "Paramètres du coffre incompatibles." }
        return PREFIX + Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(plain))
    }
    fun decrypt(name: String, value: String): String {
        require(value.startsWith(PREFIX) && value.length <= (MAX_BYTES + 28) * 4 / 3 + 32) { "Accès chiffré invalide." }
        val encoded = value.substring(PREFIX.length)
        val data = Base64.getDecoder().decode(encoded)
        require(data.size in 28..(MAX_BYTES + 28) && Base64.getEncoder().encodeToString(data) == encoded) { "Enveloppe du coffre invalide." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // Reading an existing envelope MUST NOT create a replacement key.
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        cipher.updateAAD(aad(name))
        val clear = cipher.doFinal(data.copyOfRange(12, data.size))
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(clear)).toString()
    }
}
