package com.franchiseguide.app.network

import com.franchiseguide.app.model.MediaItem
import com.franchiseguide.app.model.MediaKind
import com.franchiseguide.app.model.RatingSource
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

class RawgClient {
    fun searchGames(query: String, apiKey: String, pageSize: Int = 20, page: Int = 1): List<MediaItem> {
        val url = "https://api.rawg.io/api/games?key=${enc(apiKey)}&search=${enc(query)}&search_precise=true&page=${page.coerceIn(1,500)}&page_size=${pageSize.coerceIn(1, 40)}"
        val root = JSONObject(get(url))
        val results = root.optJSONArray("results") ?: error("RAWG : tableau de résultats manquant.")
        return buildList {
            for (i in 0 until results.length()) {
                val o = results.optJSONObject(i) ?: continue
                val id = o.optInt("id", 0)
                val title = o.optString("name").trim()
                if (id <= 0 || title.isBlank()) continue
                val released = parseDate(o.optString("released"))
                val platforms = platforms(o)
                val genres = names(o, "genres")
                add(
                    MediaItem(
                        id = "rawg-game-$id",
                        kind = MediaKind.GAME,
                        title = title,
                        subtitle = genres.take(3).joinToString(" · "),
                        year = released?.year,
                        releaseDate = released,
                        posterUrl = o.optString("background_image").takeIf { it.startsWith("http") },
                        backdropUrl = o.optString("background_image").takeIf { it.startsWith("http") },
                        catalogRating = o.optDouble("rating", 0.0).takeIf { it > 0.0 }?.let { (it * 2.0).coerceAtMost(10.0) },
                        catalogRatingVotes = o.optInt("ratings_count", 0).takeIf { it > 0 },
                        ratings = o.optDouble("rating", 0.0).takeIf { it > 0.0 }?.let { raw -> listOf(RatingSource("rawg", "RAWG", raw, 5.0, o.optInt("ratings_count", 0).takeIf { it > 0 }, "https://rawg.io/games/${o.optString("slug").ifBlank { id.toString() }}")) }.orEmpty(),
                        added = false,
                        source = "rawg",
                        sourceId = id.toString(),
                        platforms = platforms,
                        genres = genres.toSet(),
                        externalUrl = "https://rawg.io/games/${o.optString("slug").ifBlank { id.toString() }}"
                    )
                )
            }
        }
    }

    fun getGameDetails(sourceId: String, apiKey: String): MediaItem {
        val lookup = sourceId.substringAfterLast(':').trim().ifBlank { error("Identifiant RAWG invalide.") }
        val o = JSONObject(get("https://api.rawg.io/api/games/${enc(lookup)}?key=${enc(apiKey)}"))
        val id = o.optInt("id", 0).takeIf { it > 0 } ?: lookup.toIntOrNull() ?: error("Réponse RAWG sans identifiant.")
        val released = parseDate(o.optString("released"))
        val genres = names(o, "genres")
        val devs = names(o, "developers")
        val pubs = names(o, "publishers")
        val slug = o.optString("slug").ifBlank { id.toString() }
        return MediaItem(
            id = "rawg-game-$id",
            kind = MediaKind.GAME,
            title = o.optString("name").ifBlank { "Jeu RAWG $id" },
            subtitle = genres.take(3).joinToString(" · "),
            year = released?.year,
            releaseDate = released,
            overview = o.optString("description_raw"),
            posterUrl = o.optString("background_image").takeIf { it.startsWith("http") },
            backdropUrl = o.optString("background_image_additional").takeIf { it.startsWith("http") }
                ?: o.optString("background_image").takeIf { it.startsWith("http") },
            added = true,
            catalogRating = o.optDouble("rating", 0.0).takeIf { it > 0.0 }?.let { (it * 2.0).coerceAtMost(10.0) },
            catalogRatingVotes = o.optInt("ratings_count", 0).takeIf { it > 0 },
            ratings = o.optDouble("rating", 0.0).takeIf { it > 0.0 }?.let { raw -> listOf(RatingSource("rawg", "RAWG", raw, 5.0, o.optInt("ratings_count", 0).takeIf { it > 0 }, "https://rawg.io/games/$slug")) }.orEmpty(),
            creator = devs.joinToString(", ").takeIf { it.isNotBlank() },
            publisher = pubs.joinToString(", ").takeIf { it.isNotBlank() },
            platforms = platforms(o),
            genres = genres.toSet(),
            runtimeMinutes = o.optInt("playtime", 0).takeIf { it > 0 }?.times(60),
            externalUrl = "https://rawg.io/games/$slug",
            source = "rawg",
            sourceId = id.toString(),
            catalogComplete = true,
            catalogUpdatedAt = System.currentTimeMillis()
        )
    }

    fun discoverGames(apiKey: String, pageSize: Int = 30, page:Int=1): List<MediaItem> {
        val url = "https://api.rawg.io/api/games?key=${enc(apiKey)}&ordering=-added,-rating&page_size=${pageSize.coerceIn(1, 40)}&page=${page.coerceIn(1,100)}"
        val root = JSONObject(get(url))
        val results = root.optJSONArray("results") ?: error("RAWG : tableau de résultats manquant.")
        return buildList {
            for (i in 0 until results.length()) {
                val o = results.optJSONObject(i) ?: continue
                val id = o.optInt("id", 0); val title = o.optString("name").trim()
                if (id <= 0 || title.isBlank()) continue
                val released = parseDate(o.optString("released")); val raw = o.optDouble("rating", 0.0).takeIf { it > 0.0 }; val votes = o.optInt("ratings_count", 0).takeIf { it > 0 }; val slug = o.optString("slug").ifBlank { id.toString() }
                add(MediaItem(id="rawg-game-$id", kind=MediaKind.GAME, title=title, year=released?.year, releaseDate=released, posterUrl=o.optString("background_image").takeIf { it.startsWith("http") }, backdropUrl=o.optString("background_image").takeIf { it.startsWith("http") }, added=false, isSuggestion=true, catalogRating=raw?.let { (it*2.0).coerceAtMost(10.0) }, catalogRatingVotes=votes, ratings=raw?.let { listOf(RatingSource("rawg","RAWG",it,5.0,votes,"https://rawg.io/games/$slug")) }.orEmpty(), platforms=platforms(o), genres=names(o,"genres").toSet(), externalUrl="https://rawg.io/games/$slug", source="rawg", sourceId=id.toString()))
            }
        }
    }

    private fun platforms(o: JSONObject): Set<String> = buildSet {
        val arr = o.optJSONArray("platforms")
        if (arr != null) for (p in 0 until arr.length()) {
            arr.optJSONObject(p)?.optJSONObject("platform")?.optString("name")?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    private fun names(o: JSONObject, key: String): List<String> = buildList {
        val arr = o.optJSONArray(key)
        if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let(::add)
    }

    private fun parseDate(value: String): LocalDate? = value.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun get(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "FranchiseGuide/0.9")
        try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
            if (code !in 200..299) error("RAWG HTTP $code: $body")
            return body
        } finally {
            connection.disconnect()
        }
    }
}
