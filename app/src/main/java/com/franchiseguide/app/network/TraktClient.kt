package com.franchiseguide.app.network

import com.franchiseguide.app.model.ActivityRules
import com.franchiseguide.app.model.TraktCheckInRules
import com.franchiseguide.app.model.CommunityComment
import com.franchiseguide.app.model.Episode
import com.franchiseguide.app.model.MediaItem
import com.franchiseguide.app.model.MediaKind
import com.franchiseguide.app.model.PendingChange
import com.franchiseguide.app.model.ReleaseRules
import com.franchiseguide.app.model.ReviewDraft
import com.franchiseguide.app.model.IntegrationRules
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant

class TraktClient {
    data class DeviceCode(val deviceCode: String, val userCode: String, val verificationUrl: String, val expiresIn: Int, val interval: Int)
    data class UserProfile(val username: String, val displayName: String?)
    sealed interface DevicePoll {
        data class Authorized(val token: Token) : DevicePoll
        data object Pending : DevicePoll
        data object Invalid : DevicePoll
        data object AlreadyUsed : DevicePoll
        data object Expired : DevicePoll
        data object Denied : DevicePoll
        data object SlowDown : DevicePoll
        data class Failed(val code: Int, val body: String) : DevicePoll
    }
    private data class HttpResult(val code: Int, val body: String, val pageCount: Int = 0)
    class HttpFailure(val status: Int): java.io.IOException("Trakt HTTP $status" + when(status){423 -> " : compte verrouillé";429 -> " : limite de requêtes, réessayer plus tard";401 -> " : reconnecter le compte";else -> ""})
    data class Token(val accessToken: String, val refreshToken: String, val expiresIn: Long, val createdAt: Long) {
        val expiresAtMillis: Long get() = (createdAt + expiresIn) * 1000L
    }
    data class WatchedMovie(val tmdbId: Int, val plays: Int, val lastWatchedAt: Long?)
    data class WatchedEpisode(val showTmdbId: Int, val season: Int, val episode: Int, val plays: Int, val lastWatchedAt: Long?)
    data class CollectionMovie(val tmdbId: Int)
    data class CollectionEpisode(val showTmdbId: Int, val season: Int, val episode: Int)
    data class WatchlistRef(val tmdbId: Int, val kind: MediaKind)
    data class RatingRef(val tmdbId: Int, val kind: MediaKind, val rating: Int)
    data class SyncSnapshot(
        val watchedMovies: List<WatchedMovie>,
        val watchedEpisodes: List<WatchedEpisode>,
        val collectionMovies: List<CollectionMovie>,
        val collectionEpisodes: List<CollectionEpisode>,
        val watchlist: List<WatchlistRef>,
        val ratings: List<RatingRef>,
        val episodeRatings: Map<Int,Int> = emptyMap()
    )
    data class CheckInHistoryEntry(val tmdbId:Int,val watchedAt:Long)
    data class UploadSummary(val history: Int = 0, val collection: Int = 0, val watchlist: Int = 0, val ratings: Int = 0)
    data class PublicMetadata(
        val traktId: Int,
        val rating: Double?,
        val votes: Int?,
        val status: String?,
        val network: String?,
        val certification: String?,
        val runtimeMinutes: Int?,
        val genres: Set<String>,
        val airs: String?,
        val url: String?
    )

    fun publicMetadata(tmdbId: Int, kind: MediaKind, clientId: String): PublicMetadata? {
        if (clientId.isBlank() || kind !in setOf(MediaKind.SHOW, MediaKind.MOVIE)) return null
        val type = if (kind == MediaKind.SHOW) "show" else "movie"
        val raw = get("https://api.trakt.tv/search/tmdb/$tmdbId?type=$type&extended=full", clientId, "")
        val array = JSONArray(raw)
        val hit = array.optJSONObject(0) ?: return null
        val node = hit.optJSONObject(type) ?: return null
        val ids = node.optJSONObject("ids") ?: JSONObject()
        val traktId = ids.optInt("trakt", 0)
        if (traktId <= 0) return null
        val rating = if(node.has("rating") && !node.isNull("rating")) node.optDouble("rating",0.0).takeIf { it >= 0.0 } else null
        val votes = node.optInt("votes", 0).takeIf { it > 0 }
        val genres = buildSet {
            val g = node.optJSONArray("genres") ?: JSONArray()
            for (i in 0 until g.length()) g.optString(i).takeIf { it.isNotBlank() }?.let(::add)
        }
        val airsNode = node.optJSONObject("airs")
        val airs = airsNode?.let { a ->
            listOfNotNull(a.optString("day").takeIf { it.isNotBlank() }, a.optString("time").takeIf { it.isNotBlank() }, a.optString("timezone").takeIf { it.isNotBlank() }).joinToString(" · ").takeIf { it.isNotBlank() }
        }
        return PublicMetadata(
            traktId = traktId,
            rating = rating,
            votes = votes,
            status = node.optString("status").takeIf { it.isNotBlank() },
            network = node.optString("network").takeIf { it.isNotBlank() },
            certification = node.optString("certification").takeIf { it.isNotBlank() },
            runtimeMinutes = node.optInt("runtime", 0).takeIf { it > 0 },
            genres = genres,
            airs = airs,
            url = "https://trakt.tv/search/tmdb/$tmdbId?id_type=${if (kind == MediaKind.SHOW) "show" else "movie"}"
        )
    }

    fun startCheckInMovie(tmdbId:Int,accessToken:String,clientId:String):Long? = startCheckIn("movie",tmdbId,accessToken,clientId)
    fun startCheckInEpisode(tmdbId:Int,accessToken:String,clientId:String):Long? = startCheckIn("episode",tmdbId,accessToken,clientId)
    private fun startCheckIn(kind:String,tmdbId:Int,accessToken:String,clientId:String):Long? {
        require(kind in setOf("movie","episode")&&tmdbId>0)
        val body=JSONObject().put(kind,mediaNode(tmdbId)).put("app_version","0.36.0").put("app_date","2026-10-26").toString()
        val root=JSONObject(post("https://api.trakt.tv/checkin",body,clientId,accessToken))
        return isoMillis(root.optString("started_at"))
    }
    /** Compatibility wrapper for older UI call sites; new repository uses the typed start methods. */
    fun checkInMovie(tmdbId:Int,accessToken:String,clientId:String):String = startCheckInMovie(tmdbId,accessToken,clientId)?.let{Instant.ofEpochMilli(it).toString()}?:"check-in"
    fun checkInEpisode(tmdbId:Int,accessToken:String,clientId:String):String = startCheckInEpisode(tmdbId,accessToken,clientId)?.let{Instant.ofEpochMilli(it).toString()}?:"check-in"
    fun addCheckInHistory(kind:String,tmdbId:Int,watchedAt:Long,accessToken:String,clientId:String) {
        require(kind in setOf("movie","episode")&&tmdbId>0&&watchedAt>0)
        val plural=if(kind=="episode")"episodes" else "movies"
        val node=mediaNode(tmdbId).put("watched_at",Instant.ofEpochMilli(watchedAt).toString())
        val root=JSONObject(post("https://api.trakt.tv/sync/history",JSONObject().put(plural,JSONArray().put(node)).toString(),clientId,accessToken))
        val notFound=root.optJSONObject("not_found")
        require(notFound==null||!notFound.has(plural)||notFound.opt(plural) is JSONArray) { "Réponse not_found Trakt invalide." }
        val missing=notFound?.optJSONArray(plural)
        require(missing==null||missing.length()==0) { "Identifiant non reconnu par Trakt." }
        val added=root.optJSONObject("added")?.opt(plural);val existing=root.optJSONObject("existing")?.opt(plural)
        require((added==null||added is Number)&&(existing==null||existing is Number)&&TraktCheckInRules.historyAcknowledged(added as? Number,existing as? Number)) { "Trakt n’a pas confirmé exactement ce visionnage. Vérifie l’historique distant." }
    }
    fun recentCheckInHistory(kind:String,accessToken:String,clientId:String,limit:Int=100):List<CheckInHistoryEntry> {
        require(kind in setOf("movie","episode")&&limit in 1..100)
        val plural=if(kind=="episode")"episodes" else "movies"
        val rows=JSONArray(get("https://api.trakt.tv/users/me/history/$plural?limit=$limit",clientId,accessToken))
        require(rows.length()<=limit) { "Historique Trakt trop volumineux." }
        return buildList {
            for(i in 0 until rows.length()) {
                val row=rows.getJSONObject(i);val node=row.optJSONObject(kind)?:error("Ligne historique Trakt invalide.")
                val id=node.optJSONObject("ids")?.optInt("tmdb",0)?:0;val at=isoMillis(row.optString("watched_at"))
                require(id>0&&at!=null) { "Historique Trakt invalide." };add(CheckInHistoryEntry(id,at))
            }
        }
    }
    fun cancelCheckIn(accessToken: String, clientId: String) { request("DELETE", "https://api.trakt.tv/checkin", null, clientId, accessToken) }

    fun publicComments(tmdbId: Int, kind: MediaKind, clientId: String, limit: Int = 12): List<CommunityComment> {
        if (clientId.isBlank() || kind !in setOf(MediaKind.SHOW, MediaKind.MOVIE)) return emptyList()
        val meta = publicMetadata(tmdbId, kind, clientId) ?: return emptyList()
        val type = if (kind == MediaKind.SHOW) "shows" else "movies"
        val raw = get("https://api.trakt.tv/$type/${meta.traktId}/comments/likes?limit=${limit.coerceIn(1, 30)}&extended=full", clientId, "")
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val user = o.optJSONObject("user") ?: JSONObject()
                val text = o.optString("comment").takeIf { it.isNotBlank() } ?: continue
                add(CommunityComment(
                    id = o.optLong("id", i.toLong()),
                    author = user.optString("name").takeIf { it.isNotBlank() } ?: user.optString("username").ifBlank { "Trakt" },
                    text = text,
                    review = o.optBoolean("review", false),
                    spoiler = o.optBoolean("spoiler", false),
                    likes = o.optInt("likes", 0),
                    createdAt = o.optString("created_at").takeIf { it.isNotBlank() }
                ))
            }
        }
    }

    data class ReviewPage(val items:List<CommunityComment>,val page:Int,val hasMore:Boolean)
    private fun parseReview(o:JSONObject):CommunityComment {
        val id=o.optLong("id",0);require(id>0) { "Identifiant d’avis invalide." }
        val user=o.optJSONObject("user")?:JSONObject()
        return CommunityComment(id,user.optString("name").ifBlank { user.optString("username").ifBlank { "Trakt" } },
            o.getString("comment"),o.optBoolean("review"),o.optBoolean("spoiler"),o.optInt("likes",0),
            o.optString("created_at").takeIf { it.isNotBlank() },user.optString("username").takeIf { it.isNotBlank() })
    }
    fun reviewPage(tmdbId:Int,kind:MediaKind,clientId:String,page:Int):ReviewPage {
        require(clientId.isNotBlank()) { "Configure le Client ID Trakt." }
        require(page in 1..500)
        val meta=publicMetadata(tmdbId,kind,clientId)?:return ReviewPage(emptyList(),page,false)
        val result=requestResult("GET","https://api.trakt.tv"+ReleaseRules.reviewPath(kind,meta.traktId,page),null,clientId,null)
        if(result.code !in 200..299)throw HttpFailure(result.code)
        val arr=JSONArray(result.body)
        val items=(0 until arr.length()).map { parseReview(arr.getJSONObject(it)) }.distinctBy { it.id }
        return ReviewPage(items,page,if(result.pageCount>0)page<result.pageCount else arr.length()>=20)
    }
    private fun requireReviewOwner(id:Long,token:String,clientId:String,username:String) {
        require(id>0)
        val review=parseReview(JSONObject(get("https://api.trakt.tv/comments/$id",clientId,token)))
        require(ReleaseRules.ownsReview(username,review.username)) { "Seul l’auteur peut modifier ou supprimer cet avis." }
    }
    fun writeReview(tmdbId:Int,kind:MediaKind,draft:ReviewDraft,token:String,clientId:String,username:String):CommunityComment {
        val content=ReleaseRules.reviewText(draft.text)
        require(tmdbId>0 && (kind==MediaKind.MOVIE || kind==MediaKind.SHOW))
        val body=JSONObject().put("comment",content.text).put("spoiler",draft.spoiler).put("review",content.review)
        val raw=if(draft.editingId!=null) {
            requireReviewOwner(draft.editingId,token,clientId,username)
            request("PUT","https://api.trakt.tv/comments/${draft.editingId}",body.toString(),clientId,token)
        } else {
            body.put(if(kind==MediaKind.SHOW)"show" else "movie",mediaNode(tmdbId))
            post("https://api.trakt.tv/comments",body.toString(),clientId,token)
        }
        return parseReview(JSONObject(raw))
    }
    fun deleteReview(id:Long,token:String,clientId:String,username:String) {
        requireReviewOwner(id,token,clientId,username)
        request("DELETE","https://api.trakt.tv/comments/$id",null,clientId,token)
    }

    fun requestDeviceCode(clientId: String): DeviceCode {
        val root = JSONObject(post("https://auth.trakt.tv/oauth/device/code", JSONObject().put("client_id", clientId).toString(), clientId, null))
        return DeviceCode(root.getString("device_code"), root.getString("user_code"), root.getString("verification_url"), root.getInt("expires_in"), root.getInt("interval"))
    }

    fun pollDeviceCode(deviceCode: String, clientId: String, clientSecret: String): DevicePoll {
        val body = JSONObject().put("code", deviceCode).put("client_id", clientId).put("client_secret", clientSecret).toString()
        val r = requestResult("POST", "https://auth.trakt.tv/oauth/device/token", body, clientId, null)
        return when (r.code) {
            200 -> DevicePoll.Authorized(tokenFrom(JSONObject(r.body)))
            400 -> DevicePoll.Pending
            404 -> DevicePoll.Invalid
            409 -> DevicePoll.AlreadyUsed
            410 -> DevicePoll.Expired
            418 -> DevicePoll.Denied
            429 -> DevicePoll.SlowDown
            else -> DevicePoll.Failed(r.code, r.body)
        }
    }

    fun exchangeDeviceCode(deviceCode: String, clientId: String, clientSecret: String): Token =
        when (val r = pollDeviceCode(deviceCode, clientId, clientSecret)) {
            is DevicePoll.Authorized -> r.token
            DevicePoll.Pending -> error("Trakt: autorisation toujours en attente.")
            DevicePoll.Invalid -> error("Trakt: code appareil invalide.")
            DevicePoll.AlreadyUsed -> error("Trakt: code appareil déjà utilisé.")
            DevicePoll.Expired -> error("Trakt: code appareil expiré.")
            DevicePoll.Denied -> error("Trakt: autorisation refusée.")
            DevicePoll.SlowDown -> error("Trakt: requêtes trop rapides.")
            is DevicePoll.Failed -> error("Trakt HTTP ${r.code}: ${r.body}")
        }

    fun refreshToken(refreshToken: String, clientId: String, clientSecret: String): Token {
        val body = JSONObject()
            .put("refresh_token", refreshToken)
            .put("client_id", clientId)
            .put("client_secret", clientSecret)
            .put("grant_type", "refresh_token")
            .put("redirect_uri", "urn:ietf:wg:oauth:2.0:oob")
            .toString()
        return tokenFrom(JSONObject(post("https://auth.trakt.tv/oauth/token", body, clientId, null)))
    }

    fun userProfile(accessToken: String, clientId: String): UserProfile {
        val root = JSONObject(get("https://api.trakt.tv/users/settings", clientId, accessToken))
        val user = root.optJSONObject("user") ?: error("Profil Trakt absent.")
        val username = user.optString("username").takeIf { it.isNotBlank() } ?: error("Nom d’utilisateur Trakt absent.")
        return UserProfile(username, user.optString("name").takeIf { it.isNotBlank() })
    }

    data class LibraryPage(val items:List<MediaItem>,val page:Int,val hasMore:Boolean,val unmapped:Int)

    /** Read-only, one page per request. Merely viewing this list never writes to Trakt. */
    fun libraryPage(accessToken:String,clientId:String,kind:MediaKind,section:String,page:Int):LibraryPage {
        val path=IntegrationRules.traktLibraryPath(kind,section,page)
        val response=requestResult("GET","https://api.trakt.tv"+path,null,clientId,accessToken)
        if(response.code !in 200..299) throw HttpFailure(response.code)
        val array=JSONArray(response.body); val items=mutableListOf<MediaItem>();var skipped=0
        val type=if(kind==MediaKind.SHOW) "show" else "movie"
        for(i in 0 until array.length()) {
            val node=array.optJSONObject(i)?.optJSONObject(type)
            val ids=node?.optJSONObject("ids");val tmdb=ids?.optInt("tmdb",0)?:0
            if(node==null || tmdb<=0) { skipped++; continue }
            items.add(MediaItem(id="tmdb-$type-$tmdb",tmdbId=tmdb,kind=kind,
                title=node.optString("title").ifBlank { "Titre TMDb $tmdb" },
                year=node.optInt("year",0).takeIf { it>0 },
                imdbId=ids?.optString("imdb")?.takeIf { it.isNotBlank() },
                source="tmdb",sourceId=tmdb.toString(),added=false,isSuggestion=true))
        }
        return LibraryPage(items.distinctBy { it.id },page,
            if(response.pageCount>0)page<response.pageCount else array.length()>=25,skipped)
    }

    fun fetchSnapshot(accessToken: String, clientId: String): SyncSnapshot {
        val watchedMovies = parseWatchedMovies(getPaged("https://api.trakt.tv/sync/watched/movies", clientId, accessToken))
        val watchedEpisodes = parseWatchedShows(getPaged("https://api.trakt.tv/sync/watched/shows?specials=true", clientId, accessToken))
        val collectionMovies = parseCollectionMovies(getPaged("https://api.trakt.tv/sync/collection/movies", clientId, accessToken))
        val collectionEpisodes = parseCollectionShows(getPaged("https://api.trakt.tv/sync/collection/shows", clientId, accessToken))
        val watchlist = buildList {
            addAll(parseWatchlist(getPaged("https://api.trakt.tv/sync/watchlist/movies", clientId, accessToken), MediaKind.MOVIE))
            addAll(parseWatchlist(getPaged("https://api.trakt.tv/sync/watchlist/shows", clientId, accessToken), MediaKind.SHOW))
        }
        val ratings = buildList {
            addAll(parseRatings(getPaged("https://api.trakt.tv/sync/ratings/movies", clientId, accessToken), MediaKind.MOVIE))
            addAll(parseRatings(getPaged("https://api.trakt.tv/sync/ratings/shows", clientId, accessToken), MediaKind.SHOW))
        }
        val episodeArray=JSONArray(getPaged("https://api.trakt.tv/sync/ratings/episodes",clientId,accessToken))
        val episodeRatings=buildMap<Int,Int> {
            for(i in 0 until episodeArray.length()) {
                val o=episodeArray.optJSONObject(i) ?: continue
                val id=o.optJSONObject("episode")?.optJSONObject("ids")?.optInt("tmdb",0) ?: 0
                val rating=o.optInt("rating",0)
                if(id>0 && rating in 1..10) put(id,rating)
            }
        }
        return SyncSnapshot(watchedMovies, watchedEpisodes, collectionMovies, collectionEpisodes, watchlist, ratings,episodeRatings)
    }

    /** Sends a single journal operation. Caller durably acknowledges it before sending the next. */
    fun sendChange(accessToken: String, clientId: String, op: PendingChange) {
        require(op.field in setOf("history","collection","watchlist","ratings"))
        require(op.type in setOf("show","movie","episode") && op.tmdbId > 0)
        val node = mediaNode(op.tmdbId)
        if(op.field == "history" && op.enabled) node.put("watched_at", instantOrUnknown(op.watchedAt))
        if(op.field == "ratings" && op.enabled) node.put("rating", op.rating?.coerceIn(1,10) ?: error("Note absente"))
        val body = JSONObject().put(op.plural, JSONArray().put(node))
        val response = JSONObject(post("https://api.trakt.tv" + op.path, body.toString(), clientId, accessToken))
        val missing = response.optJSONObject("not_found")
        if(missing != null && listOf("movies","shows","episodes").any { (missing.optJSONArray(it)?.length() ?: 0) > 0 }) throw HttpFailure(422)
        require(com.franchiseguide.app.model.ActivityRules.acknowledgedCounts(listOf("added","updated","deleted","existing").map { response.optJSONObject(it)?.opt(op.plural) as? Number })) { "Accusé Trakt incomplet : vérification nécessaire." }
    }

    private fun getPaged(url: String, clientId: String, token: String): String {
        val result = JSONArray()
        for(page in 1..500) {
            val separator = if(url.contains("?")) "&" else "?"
            val response = requestResult("GET", "$url${separator}page=$page&limit=100", null, clientId, token)
            if(response.code !in 200..299) throw HttpFailure(response.code)
            val values = JSONArray(response.body)
            for(i in 0 until values.length()) result.put(values.get(i))
            if(response.pageCount > 0 && page >= response.pageCount) return result.toString()
            if(response.pageCount == 0 && values.length() < 100) return result.toString()
        }
        error("Pagination Trakt trop longue : aucune réponse partielle n'a été appliquée.")
    }

    private fun parseWatchedMovies(raw: String): List<WatchedMovie> = buildList {
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val tmdb = o.optJSONObject("movie")?.optJSONObject("ids")?.optInt("tmdb", 0) ?: 0
            if (tmdb > 0) add(WatchedMovie(tmdb, o.optInt("plays", 1).coerceAtLeast(1), isoMillis(o.optString("last_watched_at"))))
        }
    }

    private fun parseWatchedShows(raw: String): List<WatchedEpisode> = buildList {
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val show = array.optJSONObject(i) ?: continue
            val showTmdb = show.optJSONObject("show")?.optJSONObject("ids")?.optInt("tmdb", 0) ?: 0
            if (showTmdb <= 0) continue
            val seasons = show.optJSONArray("seasons") ?: continue
            for (s in 0 until seasons.length()) {
                val season = seasons.optJSONObject(s) ?: continue
                val number = season.optInt("number", -1)
                val eps = season.optJSONArray("episodes") ?: continue
                for (e in 0 until eps.length()) {
                    val ep = eps.optJSONObject(e) ?: continue
                    val epNo = ep.optInt("number", -1)
                    if (number >= 0 && epNo > 0) add(WatchedEpisode(showTmdb, number, epNo, ep.optInt("plays", 1).coerceAtLeast(1), isoMillis(ep.optString("last_watched_at"))))
                }
            }
        }
    }

    private fun parseCollectionMovies(raw: String): List<CollectionMovie> = buildList {
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val tmdb = array.optJSONObject(i)?.optJSONObject("movie")?.optJSONObject("ids")?.optInt("tmdb", 0) ?: 0
            if (tmdb > 0) add(CollectionMovie(tmdb))
        }
    }

    private fun parseCollectionShows(raw: String): List<CollectionEpisode> = buildList {
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val show = array.optJSONObject(i) ?: continue
            val showTmdb = show.optJSONObject("show")?.optJSONObject("ids")?.optInt("tmdb", 0) ?: 0
            if (showTmdb <= 0) continue
            val seasons = show.optJSONArray("seasons") ?: continue
            for (s in 0 until seasons.length()) {
                val season = seasons.optJSONObject(s) ?: continue
                val seasonNo = season.optInt("number", -1)
                val eps = season.optJSONArray("episodes") ?: continue
                for (e in 0 until eps.length()) {
                    val epNo = eps.optJSONObject(e)?.optInt("number", -1) ?: -1
                    if (seasonNo >= 0 && epNo > 0) add(CollectionEpisode(showTmdb, seasonNo, epNo))
                }
            }
        }
    }

    private fun parseWatchlist(raw: String, kind: MediaKind): List<WatchlistRef> = buildList {
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val node = if (kind == MediaKind.MOVIE) o.optJSONObject("movie") else o.optJSONObject("show")
            val tmdb = node?.optJSONObject("ids")?.optInt("tmdb", 0) ?: 0
            if (tmdb > 0) add(WatchlistRef(tmdb, kind))
        }
    }

    private fun parseRatings(raw: String, kind: MediaKind): List<RatingRef> = buildList {
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val node = if (kind == MediaKind.MOVIE) o.optJSONObject("movie") else o.optJSONObject("show")
            val tmdb = node?.optJSONObject("ids")?.optInt("tmdb", 0) ?: 0
            val rating = o.optInt("rating", 0)
            if (tmdb > 0 && rating in 1..10) add(RatingRef(tmdb, kind, rating))
        }
    }

    private fun mediaNode(tmdbId: Int): JSONObject = JSONObject().put("ids", JSONObject().put("tmdb", tmdbId))
    private fun instantOrUnknown(value: Long?): String = value?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).toString() } ?: "unknown"
    private fun isoMillis(value: String): Long? = value.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    private fun tokenFrom(root: JSONObject) = Token(root.getString("access_token"), root.optString("refresh_token"), root.optLong("expires_in", 604800L), root.optLong("created_at", Instant.now().epochSecond))

    private fun get(url: String, clientId: String, token: String): String = request("GET", url, null, clientId, token)
    private fun post(url: String, body: String, clientId: String, token: String?): String = request("POST", url, body, clientId, token)

    private fun request(method: String, url: String, body: String?, clientId: String, token: String?): String {
        val result = requestResult(method, url, body, clientId, token)
        if (result.code !in 200..299) throw HttpFailure(result.code)
        return result.body
    }

    private fun requestResult(method: String, url: String, body: String?, clientId: String, token: String?): HttpResult {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15_000
        connection.readTimeout = 25_000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "FranchiseGuide/0.35 (Android)")
        connection.setRequestProperty("trakt-api-version", "2")
        if (clientId.isNotBlank()) connection.setRequestProperty("trakt-api-key", clientId)
        if (!token.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
        return try {
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            HttpResult(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty(), connection.getHeaderField("X-Pagination-Page-Count")?.toIntOrNull() ?: 0)
        } finally {
            connection.disconnect()
        }
    }
}
