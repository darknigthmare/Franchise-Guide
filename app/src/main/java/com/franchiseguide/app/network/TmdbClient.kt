package com.franchiseguide.app.network

import com.franchiseguide.app.model.Episode
import com.franchiseguide.app.model.MediaItem
import com.franchiseguide.app.model.MediaKind
import com.franchiseguide.app.model.PersonCredit
import com.franchiseguide.app.model.RatingSource
import com.franchiseguide.app.model.ShowStatus
import com.franchiseguide.app.model.SeasonInfo
import com.franchiseguide.app.model.PersonWork
import com.franchiseguide.app.model.PersonProfile
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

class TmdbClient {
    data class ShowBundle(val media: MediaItem, val episodes: List<Episode>)
    data class MovieBundle(val media: MediaItem, val collectionName: String?, val collection: List<MediaItem>)

    /** Resolve legacy imports by their provider ID, never by a title guess. */
    fun resolveId(item: MediaItem, apiKey: String, language: String): Int {
        item.tmdbId?.takeIf { it > 0 }?.let { return it }
        val external = when {
            item.kind == MediaKind.SHOW && item.tvdbId != null -> item.tvdbId.toString() to "tvdb_id"
            !item.imdbId.isNullOrBlank() -> item.imdbId!! to "imdb_id"
            else -> error("Aucun identifiant permettant de retrouver la fiche TMDb.")
        }
        val root = JSONObject(get("https://api.themoviedb.org/3/find/${enc(external.first)}?api_key=${enc(apiKey)}&external_source=${external.second}&language=${enc(language)}"))
        val results = root.optJSONArray(if(item.kind == MediaKind.SHOW) "tv_results" else "movie_results")
        require(results != null && results.length() == 1) { "Correspondance TMDb absente ou ambiguë : les données importées sont conservées." }
        return results!!.optJSONObject(0)?.optInt("id",0)?.takeIf { it > 0 } ?: error("Identifiant TMDb invalide.")
    }

    fun getPerson(id: Int, apiKey: String, language: String): PersonProfile {
        require(id > 0 && apiKey.isNotBlank()) { "Identifiant personne et clé TMDb requis." }
        val root = JSONObject(get("https://api.themoviedb.org/3/person/$id?api_key=${enc(apiKey)}&language=${enc(language)}&append_to_response=combined_credits"))
        require(root.optInt("id") == id && root.optString("name").isNotBlank()) { "Fiche personne incomplète." }
        val works = linkedMapOf<String,PersonWork>()
        val combined = root.optJSONObject("combined_credits") ?: JSONObject()
        listOf("cast", "crew").forEach { section ->
            val rows = combined.optJSONArray(section) ?: JSONArray()
            for(i in 0 until rows.length()) {
                val o = rows.optJSONObject(i) ?: continue
                val kind = when(o.optString("media_type")) { "tv" -> MediaKind.SHOW; "movie" -> MediaKind.MOVIE; else -> continue }
                val mid = o.optInt("id",0); if(mid <= 0) continue
                val title = o.optString(if(kind == MediaKind.SHOW) "name" else "title"); if(title.isBlank()) continue
                val date = o.optString(if(kind == MediaKind.SHOW) "first_air_date" else "release_date")
                val key = "tmdb-${kind.name.lowercase()}-$mid"
                val role = o.optString(if(section == "cast") "character" else "job").ifBlank { if(section == "cast") "Interprétation" else "Équipe" }
                val m = MediaItem(id=key,tmdbId=mid,kind=kind,title=title,year=date.take(4).toIntOrNull(),releaseDate=parseDate(date),
                    posterUrl=image(o.optString("poster_path"),"w342"),overview=o.optString("overview"),added=false,source="tmdb",sourceId=mid.toString())
                works[key] = PersonWork(m, (works[key]?.roles.orEmpty() + role).distinct())
            }
        }
        return PersonProfile(id,root.optString("name"),root.optString("biography"),image(root.optString("profile_path"),"w342"),
            root.optString("birthday").takeIf { it.isNotBlank() && it != "null" },root.optString("deathday").takeIf { it.isNotBlank() && it != "null" },
            root.optString("place_of_birth").takeIf { it.isNotBlank() && it != "null" },root.optString("known_for_department").takeIf { it.isNotBlank() },
            works.values.sortedWith(compareByDescending<PersonWork> { it.media.year ?: 0 }.thenBy { it.media.title }))
    }

    fun searchMulti(query: String, apiKey: String, language: String, kind: MediaKind? = null, page: Int = 1): List<MediaItem> {
        val encoded = enc(query)
        val searchType = when(kind){MediaKind.SHOW -> "tv";MediaKind.MOVIE -> "movie";else -> "multi"}
        val url = "https://api.themoviedb.org/3/search/$searchType?api_key=${enc(apiKey)}&language=${enc(language)}&query=$encoded&include_adult=false&page=${page.coerceIn(1,500)}"
        val root = JSONObject(get(url))
        val results = root.optJSONArray("results") ?: error("TMDb : tableau de résultats manquant.")
        return buildList {
            for (i in 0 until results.length()) {
                val o = results.optJSONObject(i) ?: continue
                val type = if(searchType != "multi") searchType else o.optString("media_type")
                if (type != "tv" && type != "movie") continue
                val kind = if (type == "tv") MediaKind.SHOW else MediaKind.MOVIE
                val title = if (kind == MediaKind.SHOW) o.optString("name") else o.optString("title")
                if (title.isBlank()) continue
                val date = if (kind == MediaKind.SHOW) o.optString("first_air_date") else o.optString("release_date")
                val id = o.optInt("id", 0)
                if (id <= 0) continue
                val score = o.optDouble("vote_average", 0.0).takeIf { o.has("vote_average") && !o.isNull("vote_average") }
                val votes = o.optInt("vote_count", 0).takeIf { it > 0 }
                add(
                    MediaItem(
                        id = "tmdb-${kind.name.lowercase()}-$id",
                        tmdbId = id,
                        kind = kind,
                        title = title,
                        originalTitle = if (kind == MediaKind.SHOW) o.optString("original_name").takeIf { it.isNotBlank() } else o.optString("original_title").takeIf { it.isNotBlank() },
                        year = date.take(4).toIntOrNull(),
                        releaseDate = parseDate(date),
                        overview = o.optString("overview"),
                        posterUrl = image(o.optString("poster_path"), "w500"),
                        backdropUrl = image(o.optString("backdrop_path"), "w1280"),
                        added = false,
                        catalogRating = score,
                        catalogRatingVotes = votes,
                        ratings = score?.let { listOf(RatingSource("tmdb", "TMDb", it, 10.0, votes, "https://www.themoviedb.org/${if (kind == MediaKind.SHOW) "tv" else "movie"}/$id")) }.orEmpty(),
                        originalLanguage = o.optString("original_language").takeIf { it.isNotBlank() },
                        source = "tmdb",
                        sourceId = id.toString()
                    )
                )
            }
        }
    }

    fun discover(kind: MediaKind, apiKey: String, language: String, region: String, yearMin: Int = 0, yearMax: Int = 0, originalLanguage: String = "", page: Int = 1): List<MediaItem> {
        require(kind == MediaKind.SHOW || kind == MediaKind.MOVIE)
        val type = if (kind == MediaKind.SHOW) "tv" else "movie"
        val dateKey = if(kind == MediaKind.SHOW) "first_air_date" else "primary_release_date"
        val filters = buildString { if(yearMin > 0) append("&$dateKey.gte=$yearMin-01-01"); if(yearMax > 0) append("&$dateKey.lte=$yearMax-12-31"); if(originalLanguage.isNotBlank()) append("&with_original_language=${enc(originalLanguage)}") }
        val root = JSONObject(get("https://api.themoviedb.org/3/discover/$type?api_key=${enc(apiKey)}&language=${enc(language)}&region=${enc(region)}&include_adult=false&sort_by=popularity.desc&page=${page.coerceIn(1,500)}$filters"))
        val array = root.optJSONArray("results") ?: error("TMDb : tableau de résultats manquant.")
        return buildList {
            for (i in 0 until minOf(30, array.length())) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optInt("id", 0); if (id <= 0) continue
                val title = if (kind == MediaKind.SHOW) o.optString("name") else o.optString("title")
                if (title.isBlank()) continue
                val date = if (kind == MediaKind.SHOW) o.optString("first_air_date") else o.optString("release_date")
                val score = o.optDouble("vote_average", 0.0).takeIf { o.has("vote_average") && !o.isNull("vote_average") }
                val votes = o.optInt("vote_count", 0).takeIf { it > 0 }
                add(MediaItem(
                    id = "tmdb-${kind.name.lowercase()}-$id", tmdbId = id, kind = kind, title = title,
                    year = date.take(4).toIntOrNull(), releaseDate = parseDate(date), overview = o.optString("overview"),
                    posterUrl = image(o.optString("poster_path"), "w500"), backdropUrl = image(o.optString("backdrop_path"), "w1280"),
                    added = false, isSuggestion = true, catalogRating = score, catalogRatingVotes = votes,
                    ratings = score?.let { listOf(RatingSource("tmdb", "TMDb", it, 10.0, votes, "https://www.themoviedb.org/$type/$id")) }.orEmpty(),
                    originalLanguage = o.optString("original_language").takeIf { it.isNotBlank() }, source = "tmdb", sourceId = id.toString(),
                    externalUrl = "https://www.themoviedb.org/$type/$id"
                ))
            }
        }
    }

    fun getShowBundle(tmdbId: Int, apiKey: String, language: String, includeSpecials: Boolean, region: String = "FR"): ShowBundle {
        val detail = JSONObject(get("https://api.themoviedb.org/3/tv/$tmdbId?api_key=${enc(apiKey)}&language=${enc(language)}&append_to_response=external_ids,content_ratings"))
        val title = detail.optString("name").ifBlank { "Série TMDb $tmdbId" }
        val runtime = detail.optJSONArray("episode_run_time")?.optInt(0, 45)?.takeIf { it > 0 } ?: 45
        val external = detail.optJSONObject("external_ids") ?: JSONObject()
        val genres = names(detail.optJSONArray("genres"))
        val network = names(detail.optJSONArray("networks")).joinToString(" / ").takeIf { it.isNotBlank() }
        val nextDate = detail.optJSONObject("next_episode_to_air")?.optString("air_date")?.let(::parseDate)
        val lastDate = parseDate(detail.optString("last_air_date"))
        val firstDate = parseDate(detail.optString("first_air_date"))
        val rawStatus = detail.optString("status").takeIf { it.isNotBlank() }
        val credits = peopleCredits("tv", tmdbId, apiKey, language)
        val score = detail.optDouble("vote_average", 0.0).takeIf { detail.has("vote_average") && !detail.isNull("vote_average") }
        val votes = detail.optInt("vote_count", 0).takeIf { it > 0 }
        val creator = names(detail.optJSONArray("created_by")).joinToString(" / ").takeIf { it.isNotBlank() }
        val countries = detail.optJSONArray("origin_country")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) } }.orEmpty()
        val production = names(detail.optJSONArray("production_companies")).toSet()
        val media = MediaItem(
            id = "tmdb-show-$tmdbId",
            tmdbId = tmdbId,
            tvdbId = external.optInt("tvdb_id", 0).takeIf { it > 0 },
            imdbId = external.optString("imdb_id").takeIf { it.isNotBlank() },
            kind = MediaKind.SHOW,
            title = title,
            originalTitle = detail.optString("original_name").takeIf { it.isNotBlank() && it != title },
            subtitle = network.orEmpty(),
            year = firstDate?.year,
            releaseDate = firstDate,
            lastAirDate = lastDate,
            overview = detail.optString("overview"),
            tagline = detail.optString("tagline").takeIf { it.isNotBlank() },
            homepage = detail.optString("homepage").takeIf { it.isNotBlank() },
            posterUrl = image(detail.optString("poster_path"), "w500"),
            backdropUrl = image(detail.optString("backdrop_path"), "w1280"),
            added = true,
            status = status(rawStatus.orEmpty()),
            statusRaw = rawStatus,
            nextAirDate = nextDate,
            network = network,
            contentRating = showCertification(detail.optJSONObject("content_ratings"), region),
            country = countries.joinToString(" / ").takeIf { it.isNotBlank() },
            creator = creator,
            seasonCount = detail.optInt("number_of_seasons", 0).takeIf { it > 0 },
            episodeCount = detail.optInt("number_of_episodes", 0).takeIf { it > 0 },
            productionCompanies = production,
            runtimeMinutes = runtime,
            genres = genres.toSet(),
            catalogRating = score,
            catalogRatingVotes = votes,
            ratings = score?.let { listOf(RatingSource("tmdb", "TMDb", it, 10.0, votes, "https://www.themoviedb.org/tv/$tmdbId")) }.orEmpty(),
            trailerUrl = trailer("tv", tmdbId, apiKey, language),
            providers = providers("tv", tmdbId, apiKey, region),
            people = credits.map { it.name }.distinct().take(16),
            credits = credits,
            originalLanguage = detail.optString("original_language").takeIf { it.isNotBlank() },
            externalUrl = "https://www.themoviedb.org/tv/$tmdbId",
            source = "tmdb",
            sourceId = tmdbId.toString(),
            catalogComplete = true,
            catalogUpdatedAt = System.currentTimeMillis()
        )

        val episodes = mutableListOf<Episode>()
        val seasonInfo = mutableListOf<SeasonInfo>()
        val seasons = detail.optJSONArray("seasons") ?: error("Liste des saisons absente : ajout annulé.")
        for (i in 0 until seasons.length()) {
            val summary = seasons.optJSONObject(i) ?: continue
            val seasonNumber = summary.optInt("season_number", -1)
            if (seasonNumber < 0) continue // Specials are fetched even when hidden in the UI.
            val seasonId = summary.optInt("id", 0).takeIf { it > 0 }
            val summaryPoster = image(summary.optString("poster_path"), "w500")
            val season = JSONObject(get("https://api.themoviedb.org/3/tv/$tmdbId/season/$seasonNumber?api_key=${enc(apiKey)}&language=${enc(language)}&append_to_response=watch/providers"))
            val seasonPoster = image(season.optString("poster_path"), "w500") ?: summaryPoster
            val eps = season.optJSONArray("episodes") ?: error("Saison $seasonNumber : épisodes absents.")
            val providerRoot = season.optJSONObject("watch/providers")?.optJSONObject("results")?.optJSONObject(region.uppercase())
            val seasonProviders = buildSet<String> {
                mapOf("flatrate" to "Abonnement", "free" to "Gratuit", "ads" to "Publicité", "rent" to "Location", "buy" to "Achat").forEach { (key,label) ->
                    val arr = providerRoot?.optJSONArray(key) ?: JSONArray()
                    for(j in 0 until arr.length()) arr.optJSONObject(j)?.optString("provider_name")?.takeIf { it.isNotBlank() }?.let { add("$it · $label") }
                }
            }
            seasonInfo.add(SeasonInfo(seasonNumber,season.optString("name", "Saison $seasonNumber"),season.optString("overview"),seasonPoster,eps.length(),seasonProviders))
            val seenIds = mutableSetOf<Int>();val seenNumbers = mutableSetOf<Int>()
            val expectedEpisodeCount = summary.optInt("episode_count", 0)
            require(expectedEpisodeCount <= 0 || eps.length() >= expectedEpisodeCount) {
                "Saison $seasonNumber incomplète : TMDb annonce $expectedEpisodeCount épisode(s), ${eps.length()} reçu(s). Réessaie l’actualisation."
            }
            for (e in 0 until eps.length()) {
                val o = eps.optJSONObject(e) ?: continue
                val epId = o.optInt("id", 0)
                val number = o.optInt("episode_number", e + 1)
                require(epId > 0 && number >= 0 && seenIds.add(epId) && seenNumbers.add(number)) { "Saison $seasonNumber : épisode invalide ou dupliqué." }
                episodes += Episode(
                    id = "tmdb-episode-$epId",
                    showId = media.id,
                    season = seasonNumber,
                    number = number,
                    title = o.optString("name").ifBlank { "Épisode $number" },
                    airDate = parseDate(o.optString("air_date")),
                    runtimeMinutes = o.optInt("runtime", runtime).takeIf { it > 0 } ?: runtime,
                    tmdbId = epId,
                    seasonTmdbId = seasonId,
                    overview = o.optString("overview"),
                    stillUrl = image(o.optString("still_path"), "w780"),
                    seasonPosterUrl = seasonPoster,
                    seasonProviders = seasonProviders,
                    catalogRating = o.optDouble("vote_average", 0.0).takeIf { o.has("vote_average") && !o.isNull("vote_average") },
                    catalogRatingVotes = o.optInt("vote_count", 0).takeIf { it > 0 }
                )
            }
        }
        require(episodes.count { it.season > 0 } >= detail.optInt("number_of_episodes", 0)) { "Série incomplète : des épisodes réguliers manquent." }
        return ShowBundle(media.copy(seasons = seasonInfo), episodes)
    }

    fun getMovieBundle(tmdbId: Int, apiKey: String, language: String, region: String = "FR"): MovieBundle {
        val detail = JSONObject(get("https://api.themoviedb.org/3/movie/$tmdbId?api_key=${enc(apiKey)}&language=${enc(language)}&append_to_response=external_ids,release_dates"))
        val external = detail.optJSONObject("external_ids") ?: JSONObject()
        val releaseDate = parseDate(detail.optString("release_date"))
        val genres = names(detail.optJSONArray("genres"))
        val credits = peopleCredits("movie", tmdbId, apiKey, language)
        val score = detail.optDouble("vote_average", 0.0).takeIf { detail.has("vote_average") && !detail.isNull("vote_average") }
        val votes = detail.optInt("vote_count", 0).takeIf { it > 0 }
        val media = MediaItem(
            id = "tmdb-movie-$tmdbId",
            tmdbId = tmdbId,
            imdbId = external.optString("imdb_id").takeIf { it.isNotBlank() } ?: detail.optString("imdb_id").takeIf { it.isNotBlank() },
            kind = MediaKind.MOVIE,
            title = detail.optString("title").ifBlank { "Film TMDb $tmdbId" },
            originalTitle = detail.optString("original_title").takeIf { it.isNotBlank() && it != detail.optString("title") },
            year = releaseDate?.year,
            releaseDate = releaseDate,
            overview = detail.optString("overview"),
            tagline = detail.optString("tagline").takeIf { it.isNotBlank() },
            homepage = detail.optString("homepage").takeIf { it.isNotBlank() },
            posterUrl = image(detail.optString("poster_path"), "w500"),
            backdropUrl = image(detail.optString("backdrop_path"), "w1280"),
            added = true,
            runtimeMinutes = detail.optInt("runtime", 0).takeIf { it > 0 },
            genres = genres.toSet(),
            contentRating = movieCertification(detail.optJSONObject("release_dates"), region),
            country = detail.optJSONArray("production_countries")?.let(::countryNames)?.joinToString(" / ")?.takeIf { it.isNotBlank() },
            productionCompanies = names(detail.optJSONArray("production_companies")).toSet(),
            catalogRating = score,
            catalogRatingVotes = votes,
            ratings = score?.let { listOf(RatingSource("tmdb", "TMDb", it, 10.0, votes, "https://www.themoviedb.org/movie/$tmdbId")) }.orEmpty(),
            trailerUrl = trailer("movie", tmdbId, apiKey, language),
            providers = providers("movie", tmdbId, apiKey, region),
            people = credits.map { it.name }.distinct().take(16),
            credits = credits,
            originalLanguage = detail.optString("original_language").takeIf { it.isNotBlank() },
            externalUrl = "https://www.themoviedb.org/movie/$tmdbId",
            source = "tmdb",
            sourceId = tmdbId.toString(),
            catalogComplete = true,
            catalogUpdatedAt = System.currentTimeMillis()
        )
        val collection = detail.optJSONObject("belongs_to_collection")
        val collectionName = collection?.optString("name")?.takeIf { it.isNotBlank() }
        val collectionItems = collection?.optInt("id", 0)?.takeIf { it > 0 }?.let { collectionId ->
            getCollection(collectionId, apiKey, language, collectionName.orEmpty())
        }.orEmpty()
        return MovieBundle(media, collectionName, collectionItems)
    }

    fun getMovieCollection(tmdbId: Int, apiKey: String, language: String, region: String = "FR"): Pair<String, List<MediaItem>>? {
        val bundle = getMovieBundle(tmdbId, apiKey, language, region)
        val name = bundle.collectionName ?: return null
        return name to bundle.collection
    }

    private fun getCollection(collectionId: Int, apiKey: String, language: String, fallbackName: String): List<MediaItem> {
        val root = JSONObject(get("https://api.themoviedb.org/3/collection/$collectionId?api_key=${enc(apiKey)}&language=${enc(language)}"))
        val name = root.optString("name").ifBlank { fallbackName }
        val parts = root.optJSONArray("parts") ?: return emptyList()
        return buildList {
            for (i in 0 until parts.length()) {
                val o = parts.optJSONObject(i) ?: continue
                val id = o.optInt("id", 0)
                val title = o.optString("title")
                if (id <= 0 || title.isBlank()) continue
                val date = parseDate(o.optString("release_date"))
                add(
                    MediaItem(
                        id = "tmdb-movie-$id",
                        tmdbId = id,
                        kind = MediaKind.MOVIE,
                        title = title,
                        year = date?.year,
                        releaseDate = date,
                        overview = o.optString("overview"),
                        posterUrl = image(o.optString("poster_path"), "w500"),
                        backdropUrl = image(o.optString("backdrop_path"), "w1280"),
                        added = false,
                        isSuggestion = true,
                        franchiseName = name,
                        source = "tmdb",
                        sourceId = id.toString(),
                        externalUrl = "https://www.themoviedb.org/movie/$id"
                    )
                )
            }
        }
    }

    private fun trailer(type: String, id: Int, apiKey: String, language: String): String? {
        val root = JSONObject(get("https://api.themoviedb.org/3/$type/$id/videos?api_key=${enc(apiKey)}&language=${enc(language)}"))
        val a = root.optJSONArray("results") ?: return null
        var fallback: String? = null
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            if (!o.optString("site").equals("YouTube", true)) continue
            val key = o.optString("key").takeIf { it.isNotBlank() } ?: continue
            val url = "https://www.youtube.com/watch?v=$key"
            if (o.optString("type").equals("Trailer", true) && o.optBoolean("official", false)) return url
            if (fallback == null && o.optString("type").equals("Trailer", true)) fallback = url
        }
        return fallback
    }

    private fun peopleCredits(type: String, id: Int, apiKey: String, language: String): List<PersonCredit> {
        val endpoint = if (type == "tv") "aggregate_credits" else "credits"
        val root = JSONObject(get("https://api.themoviedb.org/3/$type/$id/$endpoint?api_key=${enc(apiKey)}&language=${enc(language)}"))
        val out = mutableListOf<PersonCredit>()
        val cast = root.optJSONArray("cast") ?: JSONArray()
        for (i in 0 until minOf(24, cast.length())) {
            val o = cast.optJSONObject(i) ?: continue
            val personId = o.optInt("id", 0); val name = o.optString("name")
            if (personId <= 0 || name.isBlank()) continue
            val role = if (type == "tv") {
                val roles = o.optJSONArray("roles") ?: JSONArray()
                (0 until minOf(3, roles.length())).mapNotNull { roles.optJSONObject(it)?.optString("character")?.takeIf(String::isNotBlank) }.distinct().joinToString(" / ")
            } else o.optString("character")
            out += PersonCredit("tmdb-person-$personId", name, role, "Acting", image(o.optString("profile_path"), "w342"), "https://www.themoviedb.org/person/$personId")
        }
        val crew = root.optJSONArray("crew") ?: JSONArray()
        val priority = listOf("Creator", "Director", "Writer", "Screenplay", "Executive Producer", "Producer")
        for (job in priority) {
            for (i in 0 until crew.length()) {
                val o = crew.optJSONObject(i) ?: continue
                val personId = o.optInt("id", 0); val name = o.optString("name")
                if (personId <= 0 || name.isBlank()) continue
                val roles = if (type == "tv") {
                    val jobs = o.optJSONArray("jobs") ?: JSONArray()
                    (0 until jobs.length()).mapNotNull { jobs.optJSONObject(it)?.optString("job")?.takeIf(String::isNotBlank) }.distinct()
                } else listOfNotNull(o.optString("job").takeIf(String::isNotBlank))
                if (job !in roles) continue
                val dep = o.optString("department").ifBlank { "Crew" }
                val key = "tmdb-person-$personId-$job"
                if (out.none { it.id == key }) out += PersonCredit(key, name, roles.take(3).joinToString(" / "), dep, image(o.optString("profile_path"), "w342"), "https://www.themoviedb.org/person/$personId")
                if (out.size >= 36) break
            }
            if (out.size >= 36) break
        }
        return out.distinctBy { it.name to it.role }.take(36)
    }

    private fun providers(type: String, id: Int, apiKey: String, region: String): Set<String> {
        val root = JSONObject(get("https://api.themoviedb.org/3/$type/$id/watch/providers?api_key=${enc(apiKey)}"))
        val node = root.optJSONObject("results")?.optJSONObject(region.uppercase()) ?: return emptySet()
        return buildSet {
            listOf("flatrate", "free", "ads", "rent", "buy").forEach { key ->
                val arr = node.optJSONArray(key) ?: return@forEach
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.optString("provider_name")?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun showCertification(root: JSONObject?, region: String): String? {
        val array = root?.optJSONArray("results") ?: return null
        var fallback: String? = null
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val rating = o.optString("rating").takeIf { it.isNotBlank() } ?: continue
            if (o.optString("iso_3166_1").equals(region, true)) return rating
            if (fallback == null) fallback = rating
        }
        return fallback
    }

    private fun movieCertification(root: JSONObject?, region: String): String? {
        val array = root?.optJSONArray("results") ?: return null
        var fallback: String? = null
        for (i in 0 until array.length()) {
            val country = array.optJSONObject(i) ?: continue
            val releases = country.optJSONArray("release_dates") ?: continue
            for (r in 0 until releases.length()) {
                val cert = releases.optJSONObject(r)?.optString("certification")?.takeIf { it.isNotBlank() } ?: continue
                if (country.optString("iso_3166_1").equals(region, true)) return cert
                if (fallback == null) fallback = cert
            }
        }
        return fallback
    }

    private fun countryNames(array: JSONArray): List<String> = buildList {
        for (i in 0 until array.length()) array.optJSONObject(i)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add)
    }

    private fun names(array: JSONArray?): List<String> = buildList {
        if (array != null) for (i in 0 until array.length()) {
            array.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    private fun status(raw: String): ShowStatus = when (raw.lowercase()) {
        "returning series", "continuing" -> ShowStatus.CONTINUING
        "in production" -> ShowStatus.IN_PRODUCTION
        "planned", "upcoming" -> ShowStatus.UPCOMING
        "pilot" -> ShowStatus.PILOT
        "ended" -> ShowStatus.ENDED
        "canceled", "cancelled" -> ShowStatus.CANCELED
        else -> ShowStatus.UNKNOWN
    }

    private fun parseDate(value: String): LocalDate? = value.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
    private fun image(path: String, size: String): String? = path.takeIf { it.isNotBlank() && it != "null" }?.let { if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/$size$it" }
    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun get(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "FranchiseGuide/0.9 (Android)")
        try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
            if (code !in 200..299) error("TMDb HTTP $code: $body")
            return body
        } finally {
            connection.disconnect()
        }
    }
}
