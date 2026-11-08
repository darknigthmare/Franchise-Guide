package com.franchiseguide.app.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Protects credential strings only. Public library data stays in its existing storage. */
internal class ProtectedCredentials(
    private val backing: SharedPreferences,
    private val cipher: CredentialCipher
) {
    companion object {
        private const val MARKER = "__fg_credentials_encryption_version"
        private const val ALIAS = "franchiseguide.credentials.aes.v1"
        fun open(context: Context): ProtectedCredentials = ProtectedCredentials(
            context.getSharedPreferences("franchise_guide_credentials", Context.MODE_PRIVATE),
            CredentialCipher { create ->
                val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                val existing = store.getKey(ALIAS, null)
                when {
                    existing is SecretKey -> existing
                    !create -> error("Clé du coffre indisponible.")
                    existing != null -> error("Clé du coffre incompatible.")
                    else -> KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                        init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setRandomizedEncryptionRequired(true).build())
                    }.generateKey()
                }
            }
        )
    }
    var problem: String? by mutableStateOf(null)
        private set

    init {
        try {
            val snapshot = backing.all
            val marked = snapshot[MARKER]
            require(marked == null || marked == 1) { "Version du coffre inconnue." }
            // Verify every encrypted value before migrating anything; never erase an unreadable vault.
            snapshot.forEach { (name, value) ->
                if (name == MARKER) return@forEach
                require(value is String || value is Long || value is Boolean) { "Type d'accès incompatible." }
                if (value is String) {
                    if (value.startsWith(CredentialCipher.PREFIX)) cipher.decrypt(name, value)
                    else require(marked == null && !value.startsWith("fgenc:")) { "Enveloppe d'accès absente ou inconnue." }
                }
            }
            if (marked == null) {
                val encrypted = snapshot.filterValues { it is String && !it.startsWith(CredentialCipher.PREFIX) }
                    .mapValues { (name, value) -> cipher.encrypt(name, value as String) }
                val editor = backing.edit()
                encrypted.forEach { (name, value) -> editor.putString(name, value) }
                check(editor.putInt(MARKER, 1).commit()) { "Migration du coffre non enregistrée." }
                encrypted.forEach { (name, _) -> check(cipher.decrypt(name, backing.getString(name, null)!!) == snapshot[name]) }
            }
        } catch (_: Exception) { fail() }
    }

    private fun fail() {
        // Do not put raw JSON, key names, provider messages or tokens in diagnostics.
        problem = "Coffre d'accès indisponible. Les originaux sont conservés ; synchronisation et modifications sont bloquées. Exportez la bibliothèque avant toute réinitialisation."
    }
    @Synchronized fun getString(name: String, default: String?): String? {
        if (problem != null) return default
        return try { backing.getString(name, null)?.let { cipher.decrypt(name, it) } ?: default }
        catch (_: Exception) { fail(); default }
    }
    @Synchronized fun getLong(name: String, default: Long): Long {
        if (problem != null) return default
        return try { backing.getLong(name, default) } catch (_: Exception) { fail(); default }
    }
    @Synchronized fun getBoolean(name: String, default: Boolean): Boolean {
        if (problem != null) return default
        return try { backing.getBoolean(name, default) } catch (_: Exception) { fail(); default }
    }
    fun edit() = Editor()
    inner class Editor {
        private val values = linkedMapOf<String, Any?>()
        private var used = false
        fun putString(name: String, value: String?) = apply { values[name] = value }
        fun putLong(name: String, value: Long) = apply { values[name] = value }
        fun putBoolean(name: String, value: Boolean) = apply { values[name] = value }
        fun remove(name: String) = apply { values[name] = null }
        fun commit(): Boolean = synchronized(this@ProtectedCredentials) {
            if (problem != null || used) return@synchronized false
            used = true
            try {
                // Prepare every ciphertext before touching the preferences transaction.
                val encoded = values.mapValues { (name, value) ->
                    require(name != MARKER)
                    if (value is String) cipher.encrypt(name, value) else value
                }
                val edit = backing.edit()
                encoded.forEach { (name, value) -> when (value) {
                    null -> edit.remove(name)
                    is String -> edit.putString(name, value)
                    is Long -> edit.putLong(name, value)
                    is Boolean -> edit.putBoolean(name, value)
                    else -> error("Type d'accès inconnu.")
                } }
                if (!edit.commit()) { fail(); false } else true
            } catch (_: Exception) { fail(); false }
        }
        // Existing callers used apply(): require an acknowledged durable write instead.
        fun apply() { check(commit()) { "Accès non enregistrés. Les opérations distantes sont suspendues." } }
    }
}
