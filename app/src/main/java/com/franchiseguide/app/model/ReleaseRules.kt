package com.franchiseguide.app.model

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Rules shared by the native recovery and review flows; no Android or network dependency. */
object ReleaseRules {
    const val MAX_IMPORT_BYTES = 32 * 1024 * 1024
    fun schema(value: Any?): Int {
        if (value == null) return 1
        require(value is Number && value.toDouble().isFinite() && value.toDouble() == value.toInt().toDouble() && value.toInt() in 1..11) {
            "Version de sauvegarde non prise en charge : original conservé."
        }
        return value.toInt()
    }
    fun contentLanguage(raw: String?): String? {
        val code=raw?.trim().orEmpty()
        if(code.isEmpty())return null
        require(code.matches(Regex("[a-z]{2}(-[A-Z]{2})?"))) { "Utilise un code comme fr-FR ou en-US, ou laisse vide." }
        return code
    }
    data class ReviewText(val text: String, val words: Int, val review: Boolean)
    fun reviewText(raw: String): ReviewText {
        val text = raw.trim()
        val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
        require(words >= 5) { "Trakt demande au moins cinq mots." }
        require(text.length <= 10_000) { "Brouillon limité à 10 000 caractères dans cette version." }
        return ReviewText(text, words, words >= 200)
    }
    fun reviewPath(kind: MediaKind, id: Int, page: Int): String {
        require(kind == MediaKind.SHOW || kind == MediaKind.MOVIE) { "Avis disponibles pour les films et séries." }
        require(id > 0 && page in 1..500) { "Identifiant ou page invalide." }
        return "/${if (kind == MediaKind.SHOW) "shows" else "movies"}/$id/comments/likes?page=$page&limit=20&extended=full"
    }
    fun ownsReview(username: String?, author: String?): Boolean = !username.isNullOrBlank() && !author.isNullOrBlank() && username.equals(author, true)
    fun readImport(input: InputStream, maxBytes: Int = MAX_IMPORT_BYTES): String {
        require(maxBytes > 0)
        val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            require(out.size().toLong() + count <= maxBytes) { "Fichier trop volumineux : limite de 32 Mio par import." }
            out.write(buffer, 0, count)
        }
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(out.toByteArray())).toString().removePrefix("\uFEFF")
    }
}

data class ReviewDraft(val text: String = "", val spoiler: Boolean = false, val editingId: Long? = null, val uncertain: Boolean = false)
