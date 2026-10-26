package com.franchiseguide.app.data

import com.franchiseguide.app.model.Episode
import com.franchiseguide.app.model.MediaItem
import com.franchiseguide.app.model.MediaKind
import com.franchiseguide.app.model.ShowStatus
import com.franchiseguide.app.model.UserList
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

internal data class SeriesGuideListMember(val type: String, val externalId: String)
internal data class SeriesGuideListImport(val list: UserList, val members: List<SeriesGuideListMember>)
internal data class SeriesGuideImportBundle(
    val media: List<MediaItem>,
    val episodes: List<Episode>,
    val lists: List<SeriesGuideListImport>,
    val filesRead: Int
)

/**
 * Parses the public SeriesGuide JSON backup schema.
 * The official app exports separate JSON arrays for shows, movies and lists.
 */
internal object SeriesGuideImporter {
    fun parse(documents: List<String>): SeriesGuideImportBundle {
        val media = mutableListOf<MediaItem>()
        val episodes = mutableListOf<Episode>()
        val lists = mutableListOf<SeriesGuideListImport>()
        var filesRead = 0

        documents.filter { it.isNotBlank() }.forEach { raw ->
            val array = parseArray(raw)
            validateRows(array)
            if (array.length() == 0) return@forEach
            filesRead++
            when (detectType(array)) {
                DocType.SHOWS -> parseShows(array, media, episodes)
                DocType.MOVIES -> parseMovies(array, media)
                DocType.LISTS -> parseLists(array, lists)
                DocType.UNKNOWN -> error("Fichier SeriesGuide non reconnu : export Séries, Films ou Listes attendu.")
            }
        }
        if (filesRead == 0) error("Aucun fichier SeriesGuide lisible.")
        return SeriesGuideImportBundle(media, episodes, lists, filesRead)
    }

    private fun validateRows(rows:JSONArray) {
        for(i in 0 until rows.length()) {
            val o=rows.optJSONObject(i)?:error("Entrée SeriesGuide invalide : objet requis.")
            for(key in listOf("watched","skipped","collected","favorite","hidden","notify","in_collection","in_watchlist"))if(o.has(key)&&!o.isNull(key)){
                val v=o.opt(key)
                require(v is Boolean || v is Number && v.toDouble() in setOf(0.0,1.0)) { "Champ SeriesGuide $key invalide." }
                if(v is Number)o.put(key,v.toInt()==1)
            }
            for(key in listOf("plays","runtime","runtime_min","season","episode","last_watched_ms"))if(o.has(key)&&!o.isNull(key)){
                val v=o.opt(key)
                require(v is Number && v.toDouble().isFinite() && v.toDouble()>=0 && v.toDouble()==v.toLong().toDouble()) { "Champ numérique SeriesGuide $key invalide." }
            }
            for(key in listOf("first_aired","released_utc_ms"))if(o.has(key)&&!o.isNull(key))com.franchiseguide.app.model.ImportReviewRules.seriesGuideDate(o.opt(key))
            for(key in listOf("seasons","episodes","items"))if(o.has(key)&&!o.isNull(key))validateRows(o.optJSONArray(key)?:error("Tableau SeriesGuide $key invalide."))
        }
    }

    private enum class DocType { SHOWS, MOVIES, LISTS, UNKNOWN }

    private fun detectType(array: JSONArray): DocType {
        val first = array.optJSONObject(0) ?: return DocType.UNKNOWN
        return when {
            first.has("items") && (first.has("name") || first.has("list_id")) -> DocType.LISTS
            first.has("seasons") -> DocType.SHOWS
            first.has("released_utc_ms") || first.has("in_collection") || first.has("in_watchlist") ||
                (first.has("imdb_id") && first.has("watched")) -> DocType.MOVIES
            else -> DocType.UNKNOWN
        }
    }

    private fun parseShows(array: JSONArray, media: MutableList<MediaItem>, episodes: MutableList<Episode>) {
        for (i in 0 until array.length()) {
            val show = array.optJSONObject(i) ?: continue
            val tmdbId = show.optPositiveInt("tmdb_id")
            val tvdbId = show.optPositiveInt("tvdb_id")
            if (tmdbId == null && tvdbId == null) continue
            val id = if (tmdbId != null) "sg-show-tmdb-$tmdbId" else "sg-show-tvdb-$tvdbId"
            val title = show.optString("title").ifBlank { "Série SeriesGuide ${tmdbId ?: tvdbId}" }
            val runtime = show.optInt("runtime", 45).takeIf { it >= 0 } ?: 45
            val importedEpisodes = mutableListOf<Episode>()
            val seasons = show.optJSONArray("seasons") ?: JSONArray()

            for (s in 0 until seasons.length()) {
                val season = seasons.optJSONObject(s) ?: continue
                val seasonNumber = season.optInt("season", -1)
                val seasonTmdb = season.optPositiveInt("tmdb_id")
                val seasonTvdb = season.optPositiveInt("tvdb_id")
                val eps = season.optJSONArray("episodes") ?: JSONArray()
                for (e in 0 until eps.length()) {
                    val ep = eps.optJSONObject(e) ?: continue
                    val episodeNumber = ep.optInt("episode", 0)
                    val epTmdb = ep.optPositiveInt("tmdb_id")
                    val epTvdb = ep.optPositiveInt("tvdb_id")
                    if(epTmdb == null && epTvdb == null && (seasonNumber < 0 || episodeNumber <= 0)) continue
                    val epId = when {
                        epTmdb != null -> "sg-episode-tmdb-$epTmdb"
                        epTvdb != null -> "sg-episode-tvdb-$epTvdb"
                        else -> "$id-s$seasonNumber-e$episodeNumber"
                    }
                    importedEpisodes += Episode(
                        id = epId,
                        showId = id,
                        season = seasonNumber,
                        number = episodeNumber,
                        title = ep.optString("title").ifBlank { "Épisode $episodeNumber" },
                        airDate = com.franchiseguide.app.model.ImportReviewRules.seriesGuideDate(ep.opt("first_aired").takeUnless{it==JSONObject.NULL}),
                        watched = ep.optBoolean("watched", false) && !ep.optBoolean("skipped",false),
                        runtimeMinutes = if(ep.has("runtime")&&!ep.isNull("runtime"))ep.optInt("runtime",runtime) else runtime,
                        tmdbId = epTmdb,
                        tvdbId = epTvdb,
                        seasonTmdbId = seasonTmdb,
                        seasonTvdbId = seasonTvdb,
                        plays = if(ep.optBoolean("watched",false)&&!ep.optBoolean("skipped",false))maxOf(1,ep.optInt("plays",1)) else 0,
                        skipped = ep.optBoolean("skipped", false),
                        collected = ep.optBoolean("collected", false),
                        overview = ep.optString("overview"),
                        stillUrl = tmdbPoster(ep.optNullableString("image")),
                        rating = ep.optIntOrNull("rating_user")?.takeIf { it in 1..10 },
                        catalogRating = ep.optDouble("rating_tmdb",Double.NaN).takeIf { it.isFinite() },
                        catalogRatingVotes = ep.optPositiveInt("rating_tmdb_votes"),
                        seasonUnknown = !season.has("season"),numberUnknown = !ep.has("episode"),
                        userNote=ep.optNullableString("user_note"),lastWatchedAt=ep.optLong("last_watched_ms",0).takeIf { it>0 && ep.optBoolean("watched",false) && !ep.optBoolean("skipped",false) }
                    )
                }
            }
            episodes += importedEpisodes
            val watchable = importedEpisodes.filterNot { it.skipped }
            val inferredWatched = watchable.isNotEmpty() && watchable.all { it.watched }
            media += MediaItem(
                id = id,
                tmdbId = tmdbId,
                tvdbId = tvdbId,
                imdbId = show.optNullableString("imdb_id"),
                kind = MediaKind.SHOW,
                contentLanguage = com.franchiseguide.app.model.ReleaseRules.contentLanguage(show.optNullableString("language")),
                title = title,
                year = com.franchiseguide.app.model.ImportReviewRules.seriesGuideDate(show.opt("first_aired").takeUnless{it==JSONObject.NULL})?.year,
                releaseDate = com.franchiseguide.app.model.ImportReviewRules.seriesGuideDate(show.opt("first_aired").takeUnless{it==JSONObject.NULL}),
                overview = show.optString("overview"),
                posterUrl = tmdbPoster(show.optNullableString("poster")),
                added = true,
                watched = inferredWatched,
                favorite = show.optBoolean("favorite", false),
                hidden = show.optBoolean("hidden", false),
                rating = show.optIntOrNull("rating_user")?.takeIf { it in 1..10 },
                catalogRating = show.optDouble("rating_tmdb",Double.NaN).takeIf { it.isFinite() },
                catalogRatingVotes = show.optPositiveInt("rating_tmdb_votes"),
                status = parseStatus(show.optString("status")),
                network = show.optNullableString("network"),
                runtimeMinutes = runtime,
                genres = show.optString("genres").split('|').map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                notify = show.optBoolean("notify", true),
                lastWatchedAt = show.optLong("last_watched_ms", 0L).takeIf { it > 0 },
                source = if(tmdbId!=null) "tmdb" else "seriesguide",
                sourceId = tmdbId?.toString() ?: "tvdb:$tvdbId",
                userNote = show.optNullableString("user_note")
            )
        }
    }

    private fun parseMovies(array: JSONArray, media: MutableList<MediaItem>) {
        for (i in 0 until array.length()) {
            val movie = array.optJSONObject(i) ?: continue
            val tmdbId = movie.optPositiveInt("tmdb_id")
            val imdbId = movie.optNullableString("imdb_id")
            if (tmdbId == null && imdbId == null) continue
            val id = tmdbId?.let { "sg-movie-tmdb-$it" }
                ?: "sg-movie-imdb-${imdbId!!.replace(Regex("[^A-Za-z0-9]"), "")}" 
            val released = com.franchiseguide.app.model.ImportReviewRules.seriesGuideDate(movie.opt("released_utc_ms").takeUnless{it==JSONObject.NULL})
            media += MediaItem(
                id = id,
                tmdbId = tmdbId,
                imdbId = imdbId,
                kind = MediaKind.MOVIE,
                title = movie.optString("title").ifBlank { "Film SeriesGuide ${tmdbId ?: imdbId}" },
                year = released?.year,
                overview = movie.optString("overview"),
                posterUrl = tmdbPoster(movie.optNullableString("poster")),
                added = true,
                watched = movie.optBoolean("watched", false),
                collected = movie.optBoolean("in_collection", false),
                watchlist = movie.optBoolean("in_watchlist", false),
                runtimeMinutes = movie.optInt("runtime_min", 0).takeIf { it > 0 },
                releaseDate = released,
                source = if(tmdbId!=null) "tmdb" else "seriesguide",
                sourceId = tmdbId?.toString() ?: imdbId,
                plays = movie.optInt("plays", if (movie.optBoolean("watched", false)) 1 else 0)
            )
        }
    }

    private fun parseLists(array: JSONArray, lists: MutableList<SeriesGuideListImport>) {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val name = o.optString("name").ifBlank { "Liste SeriesGuide ${i + 1}" }
            val rawId = o.optString("list_id").ifBlank { name }
            val id = "seriesguide-list-${rawId.hashCode().toUInt().toString(16)}"
            val members = mutableListOf<SeriesGuideListMember>()
            val items = o.optJSONArray("items") ?: JSONArray()
            for (j in 0 until items.length()) {
                val item = items.optJSONObject(j) ?: continue
                val externalId = item.optString("externalId").ifBlank {
                    item.optInt("tvdb_id", 0).takeIf { it > 0 }?.toString().orEmpty()
                }
                val type = item.optString("type")
                if (externalId.isNotBlank() && type.isNotBlank()) members += SeriesGuideListMember(type, externalId)
            }
            lists += SeriesGuideListImport(UserList(id, name, "Importé depuis SeriesGuide"), members)
        }
    }

    private fun parseArray(raw: String): JSONArray {
        val text = raw.trim().removePrefix("\uFEFF")
        if (text.isBlank()) return JSONArray()
        if (text.startsWith("[")) return JSONArray(text)
        if (text.startsWith("{")) return JSONArray().put(JSONObject(text))
        error("JSON SeriesGuide invalide.")
    }

    private fun parseStatus(value: String): ShowStatus = when (value.lowercase()) {
        "continuing", "in_production", "pilot", "upcoming" -> ShowStatus.CONTINUING
        "ended" -> ShowStatus.ENDED
        "canceled", "cancelled" -> ShowStatus.CANCELED
        else -> ShowStatus.UNKNOWN
    }

    private fun parseIsoDate(value: String): LocalDate? {
        if (value.isBlank()) return null
        return runCatching { OffsetDateTime.parse(value).toLocalDate() }
            .recoverCatching { LocalDate.parse(value.take(10)) }
            .getOrNull()
    }

    private fun millisToDate(value: Long): LocalDate? = value.takeIf { it > 0 }?.let {
        runCatching { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
    }

    private fun tmdbPoster(path: String?): String? = path?.takeIf { it.isNotBlank() }?.let {
        if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w500$it"
    }

    private fun JSONObject.optPositiveInt(key: String): Int? = if (!has(key) || isNull(key)) null else optInt(key).takeIf { it > 0 }
    private fun JSONObject.optIntOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else optInt(key)
    private fun JSONObject.optNullableString(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
