package com.franchiseguide.app.data

import java.util.Base64
import javax.crypto.KeyGenerator

fun main() {
    val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    var creates = 0
    val cipher = CredentialCipher { create -> if(create) creates++; key }
    var count = 0
    fun expect(value: Boolean) { check(value); count++ }
    fun refused(block: () -> Unit) { expect(runCatching(block).isFailure) }
    for(text in listOf("", "jeton-secret", "é漢😀 ", "ligne\nfin", "x".repeat(65536))) {
        val envelope = cipher.encrypt("token", text)
        expect(envelope.startsWith(CredentialCipher.PREFIX))
        expect(cipher.decrypt("token", envelope) == text)
        expect(cipher.encrypt("token", text) != envelope)
        refused { cipher.decrypt("other", envelope) }
        val raw = Base64.getDecoder().decode(envelope.removePrefix(CredentialCipher.PREFIX))
        raw[raw.lastIndex] = (raw.last().toInt() xor 1).toByte()
        refused { cipher.decrypt("token", CredentialCipher.PREFIX + Base64.getEncoder().encodeToString(raw)) }
    }
    val envelope = cipher.encrypt("token", "keep me")
    val before = creates
    cipher.decrypt("token", envelope)
    expect(creates == before)
    val unavailable = CredentialCipher { create -> check(!create) { "READ CREATED A KEY" }; error("Unavailable") }
    refused { unavailable.decrypt("token", envelope) }
    for(raw in listOf("plaintext", "fgenc:v2:abc", "fgenc:v1:!!!", "fgenc:v1:", "fgenc:v1:"+"A".repeat(12), envelope+" ")) refused { cipher.decrypt("token", raw) }
    refused { cipher.encrypt("", "secret") }
    refused { cipher.encrypt("x".repeat(513), "secret") }
    val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    refused { CredentialCipher { otherKey }.decrypt("token", envelope) }
    println("CredentialCipherTest: $count assertions passed (JCA, not Android Keystore hardware).")
}
