package com.franchiseguide.app.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.franchiseguide.app.franchise.FranchiseEngine
import com.franchiseguide.app.model.*
import com.franchiseguide.app.network.OpenLibraryClient
import com.franchiseguide.app.network.PublicCatalogClient
import com.franchiseguide.app.network.RawgClient
import com.franchiseguide.app.network.TmdbClient
import com.franchiseguide.app.network.TraktClient
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException

class AppRepository private constructor(context: Context) {
    companion object {
        @Volatile private var instance: AppRepository? = null
        fun getInstance(context: Context): AppRepository = instance ?: synchronized(this) {
            instance ?: AppRepository(context.applicationContext).also { instance = it }
        }
    }
    private val prefs = context.getSharedPreferences("franchise_guide", Context.MODE_PRIVATE)
    private val credentials = context.getSharedPreferences("franchise_guide_credentials", Context.MODE_PRIVATE)
    val gaming = mutableStateOf(GamingState())
    val media = mutableStateListOf<MediaItem>()
    val episodes = mutableStateListOf<Episode>()
    val suggestions = mutableStateListOf<MediaItem>()
    val userLists = mutableStateListOf<UserList>()
    val customFranchises = mutableStateListOf<FranchiseDefinition>()
    private var settingsLoadProblem:String?=null
    private var settingsRecoveryRaw:String?=null
    val settings = mutableStateOf(loadSettings())
    val syncMessage = mutableStateOf<String?>(null)
    private var checkInRecoveryRaw:String?=null
    private var checkInLoadProblem:String?=null
    val traktCheckIn = mutableStateOf(loadTraktCheckIn())
    private var backupSafetyProblem:String?=null
    val externalBackup = mutableStateOf(loadExternalBackup())
    val recoveryMessage = mutableStateOf<String?>(settingsLoadProblem)
    private var originalRecoveryState: String? = settingsRecoveryRaw
    fun isRecoveryOnly(): Boolean = recoveryMessage.value != null
    fun rawRecoveryExport(): String? = originalRecoveryState
    private fun requireHealthy() { check(!isRecoveryOnly()) { "Bibliothèque en récupération : aucune écriture autorisée." } }
    fun traktUsername(): String? = credentials.getString("trakt_username", null)

    private val tmdb = TmdbClient()
    private val rawg = RawgClient()
    private val publicCatalog=PublicCatalogClient()
    private val openLibrary = OpenLibraryClient()
    private val trakt = TraktClient()

    private val syncLock = Mutex()
    private val checkInLock = Mutex()
    @Volatile private var authGeneration = 0
    @Volatile private var libraryGeneration = 0
    private var lastTracked = emptyList<TrackingRecord>()
    private var savingBackup = false

    init {
        val loaded = load()
        // An unrecognized or corrupt state must NEVER be replaced by an empty library.
        if (!isRecoveryOnly()) {
            migrateLegacyCredentials()
            if (!loaded) {
                userLists.add(UserList("watch-later", "À voir / lire / jouer"))
                save(markTraktDirty = false)
            }
        }
        lastTracked = TrackingRules.records(media.toList(), episodes.toList())
        if(checkInLoadProblem!=null)syncMessage.value=checkInLoadProblem
    }

    private fun migrateLegacyCredentials() {
        if (credentials.getString("settings", null) == null) {
            prefs.getString("settings", null)?.let { credentials.edit().putString("settings", it).apply() }
        }
        val editor = credentials.edit()
        var changed = false
        listOf("trakt_access_token", "trakt_refresh_token", "trakt_device_code", "trakt_username", "trakt_display_name", "trakt_verification_url", "trakt_user_code").forEach { key ->
            if (credentials.getString(key, null) == null) prefs.getString(key, null)?.let { editor.putString(key, it); changed = true }
        }
        listOf("trakt_token_expires", "trakt_device_expires", "trakt_last_upload", "trakt_last_sync", "trakt_device_interval").forEach { key ->
            if (credentials.getLong(key, 0L) == 0L) prefs.getLong(key, 0L).takeIf { it > 0L }?.let { editor.putLong(key, it); changed = true }
        }
        if (changed) editor.apply()
        // Remove legacy credentials from the backup-eligible state preferences after migration.
        prefs.edit()
            .remove("settings")
            .remove("trakt_access_token")
            .remove("trakt_refresh_token")
            .remove("trakt_device_code")
            .remove("trakt_token_expires")
            .remove("trakt_device_expires")
            .remove("trakt_last_upload")
            .remove("trakt_last_sync")
            .remove("trakt_username")
            .remove("trakt_display_name")
            .remove("trakt_verification_url")
            .remove("trakt_user_code")
            .remove("trakt_device_interval")
            .apply()
    }

    fun shows(): List<MediaItem> = visible(MediaKind.SHOW)
    fun movies(): List<MediaItem> = visible(MediaKind.MOVIE)
    fun games(): List<MediaItem> = visible(MediaKind.GAME)
    fun books(): List<MediaItem> = visible(MediaKind.BOOK)
    fun hiddenItems(): List<MediaItem> = media.filter { it.added && it.hidden }.sortedBy { it.title.lowercase() }
    private fun visible(kind: MediaKind): List<MediaItem> {
        val items=media.filter { it.kind == kind && it.added && !it.hidden }
        return sortedLibrary(if(kind == MediaKind.SHOW) TrackingRules.filterStatus(items,settings.value.statusFilters) else items,kind)
    }
    fun sortedLibrary(items: List<MediaItem>,kind: MediaKind): List<MediaItem> {
        val sort=settings.value.librarySort[kind.name.lowercase()] ?: LibrarySortPreference()
        return TrackingRules.sort(items,sort.mode,sort.ascending,episodes.filter { settings.value.includeSpecials || it.season != 0 })
    }
    fun setLibrarySort(kind: MediaKind,mode: SortMode,ascending: Boolean) = updateSettings(settings.value.copy(librarySort=settings.value.librarySort + (kind.name.lowercase() to LibrarySortPreference(mode,ascending))))
    fun cycleStatusFilter(status: ShowStatus) {
        val current=settings.value.statusFilters[status.name] ?: 0
        updateSettings(settings.value.copy(statusFilters=settings.value.statusFilters + (status.name to when(current){0 -> 1;1 -> -1;else -> 0})))
    }
    fun clearStatusFilters() = updateSettings(settings.value.copy(statusFilters=emptyMap()))

    fun integrityReport(): IntegrityReport {
        val issues = mutableListOf<IntegrityIssue>()
        val mediaIds = media.groupingBy { it.id }.eachCount()
        mediaIds.filterValues { it > 1 }.forEach { (id, _) -> issues += IntegrityIssue("critical", "MEDIA_ID_DUPLICATE", "Identifiant média en double : $id") }
        val episodeIds = episodes.groupingBy { it.id }.eachCount()
        episodeIds.filterValues { it > 1 }.forEach { (id, _) -> issues += IntegrityIssue("critical", "EPISODE_ID_DUPLICATE", "Identifiant épisode en double : $id") }
        val validMedia = media.map { it.id }.toSet()
        episodes.filter { it.showId !in validMedia }.forEach { e ->
            val tracked = e.watched || e.skipped || e.collected || e.plays > 0 || e.rating != null || e.watchHistory.isNotEmpty() || !e.userNote.isNullOrBlank()
            issues += IntegrityIssue(if (tracked) "warning" else "repairable", "ORPHAN_EPISODE", "Épisode orphelin ${e.id}${if (tracked) " avec suivi local" else " sans suivi"}")
        }
        val validLists = userLists.map { it.id }.toSet()
        media.forEach { m -> m.listIds.filter { it !in validLists }.forEach { id -> issues += IntegrityIssue("repairable", "LIST_REF_ORPHAN", "${m.title} référence une liste inexistante : $id") } }
        val gameIds = media.filter { it.kind == MediaKind.GAME }.map { it.id }.toSet()
        gaming.value.sets.filter { it.mediaId !in gameIds }.forEach { set -> issues += IntegrityIssue("warning", "GAMING_SET_ORPHAN", "Relevé trophées sans jeu : ${set.title}") }
        val groups = media.filter { it.added }.groupBy { item ->
            val title = java.text.Normalizer.normalize(item.title, java.text.Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "").lowercase().replace("[^a-z0-9]+".toRegex(), " ").trim()
            item.kind.name + "|" + title
        }
        val duplicates = mutableListOf<DuplicateCandidate>()
        groups.values.filter { it.size > 1 }.forEach { group ->
            for (i in 0 until group.lastIndex) for (j in i + 1 until group.size) {
                val a = group[i]; val b = group[j]
                if (a.year != null && b.year != null && a.year != b.year) continue
                val conflicts = buildList {
                    if (a.tmdbId != null && b.tmdbId != null && a.tmdbId != b.tmdbId) add("TMDb ${a.tmdbId} ≠ ${b.tmdbId}")
                    if (a.tvdbId != null && b.tvdbId != null && a.tvdbId != b.tvdbId) add("TVDb ${a.tvdbId} ≠ ${b.tvdbId}")
                    if (!a.imdbId.isNullOrBlank() && !b.imdbId.isNullOrBlank() && a.imdbId != b.imdbId) add("IMDb ${a.imdbId} ≠ ${b.imdbId}")
                    val sa = a.steam?.appId; val sb = b.steam?.appId
                    if (sa != null && sb != null && sa != sb) add("Steam $sa ≠ $sb")
                }
                duplicates += DuplicateCandidate(a.id, b.id, a.title, a.kind, conflicts.isEmpty(), conflicts)
            }
        }
        return IntegrityReport(issues, duplicates)
    }

    @Synchronized
    fun repairIntegritySafely(): IntegrityReport {
        requireHealthy();check(syncLock.tryLock()) { "Synchronisation en cours. Réessaie ensuite." }
        val beforeMedia=media.toList();val beforeEpisodes=episodes.toList()
        try {
            createLocalBackup("Avant réparation sûre").getOrThrow()
            val lists=userLists.map { it.id }.toSet();val parents=media.map { it.id }.toSet()
            media.indices.forEach { i->media[i]=media[i].copy(listIds=media[i].listIds.filter { it in lists }.toSet()) }
            episodes.removeAll { e -> e.showId !in parents&&!e.watched&&!e.skipped&&!e.collected&&e.plays<=0&&e.rating==null&&e.watchHistory.isEmpty()&&e.userNote.isNullOrBlank() }
            save(false);return integrityReport()
        }catch(e:Exception){media.clear();media.addAll(beforeMedia);episodes.clear();episodes.addAll(beforeEpisodes);throw e}finally{syncLock.unlock()}
    }

    fun isTraktConnected(): Boolean = credentials.getString("trakt_access_token", "").orEmpty().isNotBlank() && credentials.getString("trakt_app_ref_v31",null)==prefs.getString("trakt_app_ref_v31",null)
    fun traktAccountLabel(): String? = credentials.getString("trakt_display_name", null)?.takeIf { it.isNotBlank() }
        ?: credentials.getString("trakt_username", null)?.takeIf { it.isNotBlank() }
    fun traktIsDirty(): Boolean = credentials.getBoolean("trakt_dirty", false)
    fun shouldAutoSyncTrakt(): Boolean {
        val cfg = settings.value
        if (isRecoveryOnly() || !cfg.traktAutoSync || !isTraktConnected() || cfg.traktClientId.isBlank()) return false
        val last = credentials.getLong("trakt_last_sync", 0L)
        val age = System.currentTimeMillis() - last
        return last <= 0L || age >= cfg.traktSyncIntervalHours.coerceIn(1, 168) * 3_600_000L || traktIsDirty()
    }

    fun isShowCatalogComplete(showId: String): Boolean {
        val show = media.firstOrNull { it.id == showId && it.kind == MediaKind.SHOW } ?: return false
        if(!show.catalogComplete) return false
        val eps = episodes.filter { it.showId == showId }
        return eps.count { it.season > 0 } >= (show.episodeCount ?: 0) && eps.map { it.season to it.number }.distinct().size == eps.size
    }

    fun history(): List<MediaItem> = media.filter { it.added && it.lastWatchedAt != null }
        .sortedByDescending { it.lastWatchedAt ?: 0L }

    fun upcomingEpisodes(): List<Episode> = episodes.filter(::episodeAllowed)
        .filter { TrackingRules.withinCalendar(it.airDate, LocalDate.now(), false, settings.value.upcomingDays) }.sortedBy { it.airDate }

    fun recentEpisodes(): List<Episode> = episodes.filter(::episodeAllowed)
        .filter { TrackingRules.withinCalendar(it.airDate, LocalDate.now(), true, settings.value.recentDays) }.sortedByDescending { it.airDate }

    private fun episodeAllowed(e: Episode): Boolean {
        if (!settings.value.includeSpecials && e.season == 0) return false
        val show = media.firstOrNull { it.id == e.showId } ?: return false
        return show.added && (settings.value.includeHiddenInCalendar || !show.hidden)
    }

    fun nextEpisode(showId: String): Episode? = episodes.asSequence()
        .filter { it.showId == showId && !it.watched && !it.skipped && (settings.value.includeSpecials || it.season != 0) }
        .minWithOrNull(compareBy<Episode> { it.season }.thenBy { it.number })

    fun remainingCount(showId: String): Int = episodes.count {
        it.showId == showId && !it.watched && !it.skipped && (settings.value.includeSpecials || it.season != 0) &&
            (it.airDate == null || !it.airDate.isAfter(LocalDate.now()))
    }

    fun seasonProgress(showId: String): List<SeasonProgress> {
        val today = LocalDate.now()
        val bySeason=episodes.filter { it.showId==showId && (settings.value.includeSpecials || it.season!=0) }.groupBy { it.season }.toMutableMap()
        media.firstOrNull { it.id==showId }?.seasons?.filter { settings.value.includeSpecials || it.number!=0 }?.forEach { bySeason.putIfAbsent(it.number,emptyList()) }
        return bySeason.toSortedMap()
            .map { (season, eps) ->
                val released = eps.count { it.airDate == null || !it.airDate.isAfter(today) }
                val watched = eps.count { it.watched }
                val skipped = eps.count { it.skipped }
                val collected = eps.count { it.collected }
                SeasonProgress(
                    season = season, total = eps.size, watched = watched,
                    remaining = eps.count { !it.watched && !it.skipped },
                    skipped = skipped, collected = collected, released = released, unreleased = (eps.size - released).coerceAtLeast(0),
                    minutesWatched = eps.filter { it.watched }.sumOf { it.runtimeMinutes.toLong() * maxOf(1, it.plays) },
                    posterUrl = eps.firstNotNullOfOrNull { it.seasonPosterUrl } ?: media.firstOrNull { it.id==showId }?.seasons?.firstOrNull { it.number==season }?.posterUrl
                )
            }
    }

    fun statistics(): StatisticsSnapshot {
        val addedMedia = media.filter { it.added&&!it.isSuggestion }
        val showList = addedMedia.filter { it.kind == MediaKind.SHOW }
        val movieList = addedMedia.filter { it.kind == MediaKind.MOVIE }
        val gameList = addedMedia.filter { it.kind == MediaKind.GAME }
        val bookList = addedMedia.filter { it.kind == MediaKind.BOOK }
        val addedIds=addedMedia.map { it.id }.toSet()
        val trackedEpisodes = episodes.filter { e -> e.showId in addedIds && (settings.value.includeSpecials || e.season != 0) }
        val watchedEpisodes = trackedEpisodes.filter { it.watched&&!it.skipped }
        val uniqueEpisodeMinutes = watchedEpisodes.sumOf { it.runtimeMinutes.toLong() }
        val totalEpisodeMinutes = watchedEpisodes.sumOf { it.runtimeMinutes.toLong() * maxOf(1, it.plays) }
        val watchedMovies = movieList.filter { it.watched }
        val uniqueMovieMinutes = watchedMovies.sumOf { (it.runtimeMinutes ?: 0).toLong() }
        val totalMovieMinutes = watchedMovies.sumOf { (it.runtimeMinutes ?: 0).toLong() * maxOf(1, it.plays) }
        val activityDates = buildList<Long> {
            addAll(watchedEpisodes.flatMap(TrackingRules::knownHistory))
            addAll(addedMedia.filter { it.kind != MediaKind.SHOW }.flatMap(TrackingRules::knownHistory))
        }.filter { it<=System.currentTimeMillis() }.map { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        val dates = activityDates.distinct().sorted()
        fun streakStats(days: List<LocalDate>): Pair<Int, Int> {
            if (days.isEmpty()) return 0 to 0
            var longest = 1; var run = 1
            for (i in 1 until days.size) {
                run = if (days[i - 1].plusDays(1) == days[i]) run + 1 else 1
                if (run > longest) longest = run
            }
            val today = LocalDate.now(); val last = days.last()
            var current = if (last == today || last == today.minusDays(1)) 1 else 0
            if (current > 0) {
                for (i in days.lastIndex downTo 1) {
                    if (days[i - 1].plusDays(1) == days[i]) current++ else break
                }
            }
            return current to longest
        }
        val (currentStreak, longestStreak) = streakStats(dates)
        val topGenres = addedMedia.asSequence()
            .filter { it.watched || (it.kind == MediaKind.SHOW && episodes.any { e -> e.showId == it.id && e.watched && !e.skipped }) }
            .flatMap { it.genres.asSequence() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(8).map { it.key to it.value }
        val topFranchises = franchiseGroups().sortedByDescending { it.addedCount }.take(8).map { it.definition.name to it.addedCount }
        val recent = buildMap<LocalDate, Int> {
            activityDates.filter { !it.isBefore(LocalDate.now().minusDays(29)) }.forEach { put(it, (get(it) ?: 0) + 1) }
        }.entries.sortedBy { it.key }.map { it.key to it.value }
        val finishedShows = showList.count { show ->
            val eps = trackedEpisodes.filter { it.showId == show.id && !it.skipped }
            eps.isNotEmpty() && eps.all { it.watched } && show.status in setOf(ShowStatus.ENDED, ShowStatus.CANCELED)
        }
        return StatisticsSnapshot(
            mediaTotal = addedMedia.size, showsTotal = showList.size, showsFinished = finishedShows, showsContinuing = showList.count { it.status in setOf(ShowStatus.CONTINUING, ShowStatus.IN_PRODUCTION, ShowStatus.UPCOMING, ShowStatus.PILOT) },
            episodesTotal = trackedEpisodes.size, episodesWatched = watchedEpisodes.size, episodePlays = watchedEpisodes.sumOf { maxOf(1, it.plays) }, episodeRewatches = watchedEpisodes.sumOf { (maxOf(1, it.plays) - 1).coerceAtLeast(0) },
            moviesTotal = movieList.size, moviesWatched = watchedMovies.size, moviePlays = watchedMovies.sumOf { maxOf(1, it.plays) }, movieRewatches = watchedMovies.sumOf { (maxOf(1, it.plays) - 1).coerceAtLeast(0) },
            gamesTotal = gameList.size, gamesCompleted = gameList.count { it.watched }, booksTotal = bookList.size, booksRead = bookList.count { it.watched }, pagesRead = bookList.sumOf { if (it.progressUnit.trim().lowercase() in setOf("page","pages")) maxOf(0,it.progressCurrent).toLong() else 0L },
            uniqueVideoMinutes = uniqueEpisodeMinutes + uniqueMovieMinutes, totalVideoMinutesWithRewatches = totalEpisodeMinutes + totalMovieMinutes,
            franchisesTotal = franchiseGroups().size, franchisesComplete = franchiseGroups().count { it.totalCount > 0 && it.addedCount == it.totalCount },
            favorites = addedMedia.count { it.favorite }, collected = addedMedia.count { it.collected } + trackedEpisodes.count { it.collected }, watchlist = addedMedia.count { it.watchlist },
            currentStreakDays = currentStreak, longestStreakDays = longestStreak, topGenres = topGenres, topFranchises = topFranchises, recentActivityDays = recent
        )
    }

    fun allFranchiseDefinitions(): List<FranchiseDefinition> = FranchiseRules.definitions(franchiseState(),FranchiseEngine.builtIns)
    fun franchiseGroups(): List<FranchiseGroup> = FranchiseEngine.groups(media, suggestions, allFranchiseDefinitions())

    /** Organisation locale : aucun changement de visionnage ni opération Trakt ajoutée. */
    @Synchronized
    fun applyLocalOrganization(plan:LibraryRules.Plan,generation:Int,undo:Boolean=false):Result<String> = runCatching {
        requireHealthy();check(generation==libraryGeneration) { "Bibliothèque remplacée : prépare un nouvel aperçu." }
        check(syncLock.tryLock()) { "Synchronisation en cours. Réessaie après sa fin." }
        try {
            val before=media.toList();val next=LibraryRules.apply(before,userLists.toList(),plan,undo)
            if(plan.patches.isNotEmpty()) {
                createLocalBackup(if(undo)"Avant annulation d’organisation" else "Avant organisation groupée").getOrThrow()
                try {media.clear();media.addAll(next);save(false)}catch(e:Exception){media.clear();media.addAll(before);throw e}
            }
            "${plan.patches.size} titre(s) mis à jour localement."
        }finally{syncLock.unlock()}
    }

    @Synchronized
    fun markNextViewed(mediaId:String,episodeId:String?,query:LibraryRules.Query,generation:Int):Result<String> = runCatching {
        requireHealthy();check(generation==libraryGeneration) { "La bibliothèque a changé." }
        check(syncLock.tryLock()) { "Synchronisation en cours. Réessaie ensuite." }
        try {
            val live=LibraryRules.nextUp(media.toList(),episodes.toList(),query.copy(today=LocalDate.now())).records.firstOrNull{it.mediaId==mediaId}
            require(live!=null&&live.status=="ready"&&live.episodeId==episodeId&&(live.kind==MediaKind.MOVIE||episodeId!=null)) { "La progression a changé : aucun autre épisode marqué." }
            val mi=media.indexOfFirst{it.id==mediaId};val oldMedia=media[mi]
            if(episodeId==null) {
                require(!oldMedia.watched);media[mi]=TrackingRules.completed(oldMedia,true)
                try{save()}catch(e:Exception){media[mi]=oldMedia;throw e}
            }else {
                val ei=episodes.indexOfFirst{it.id==episodeId&&it.showId==mediaId};require(ei>=0)
                val old=episodes[ei];require(!old.watched&&!old.skipped)
                val fresh=TrackingRules.episode(old,"watch");episodes[ei]=fresh;media[mi]=oldMedia.copy(lastWatchedAt=maxOf(oldMedia.lastWatchedAt?:0L,fresh.lastWatchedAt?:0L))
                try{save()}catch(e:Exception){episodes[ei]=old;media[mi]=oldMedia;throw e}
            }
            "Visionnage enregistré ; la suite est recalculée."
        }finally{syncLock.unlock()}
    }

    fun toggleFavorite(id: String) = updateMedia(id) { it.copy(favorite = !it.favorite) }
    fun toggleCollected(id: String) = updateMedia(id) { it.copy(collected = !it.collected) }
    fun toggleWatchlist(id: String) = updateMedia(id) { it.copy(watchlist = !it.watchlist) }
    fun toggleNotify(id: String) = updateMedia(id) { it.copy(notify = !it.notify) }
    fun setRating(id: String, rating: Int?) = updateMedia(id) { it.copy(rating = rating?.coerceIn(1, 10)) }
    fun setUserNote(id: String, note: String) = updateMedia(id) { it.copy(userNote = note.trim().take(2000).takeIf(String::isNotBlank)) }
    fun hide(id: String) = updateMedia(id) { it.copy(hidden = true) }
    fun unhide(id: String) = updateMedia(id) { it.copy(hidden = false) }
    fun remove(id: String) = updateMedia(id) { it.copy(added = false) }
    fun markMovieWatched(id: String, watched: Boolean = true) = markCompleted(id, watched)

    fun markCompleted(id:String,completed:Boolean=true)=updateMedia(id) { old -> if(completed)ProgressRules.finish(old) else ProgressRules.clear(old) }
    fun replayMedia(id:String)=updateMedia(id) { ProgressRules.finish(it,replay=true) }
    fun restartProgress(id:String)=updateMedia(id,ProgressRules::restart)
    fun setProgress(id:String,current:Int,total:Int?=null,unit:String?=null)=updateMedia(id) { item -> ProgressRules.update(item,current,total?:item.progressTotal,unit?:item.progressUnit) }
    fun bookEditions(work:String,offset:Int=0):BookEditionPage = openLibrary.editions(work,offset)
    fun bookIsbnSearch(code:String):IsbnSearchResult=openLibrary.isbnSearch(code)
    fun bookIsbnEdition(c:IsbnCandidate):BookEdition=openLibrary.isbnEdition(c)
    fun applyBookSelection(plan:BookSelectionPlan,generation:Int) {
        requireHealthy();check(syncLock.tryLock()){ "Synchronisation en cours." }
        try {check(generation==libraryGeneration){"Bibliothèque modifiée. Rouvre le panneau."};val i=media.indexOfFirst{it.id==plan.before.id};require(i>=0);val old=media[i];val next=BookSelectionRules.apply(old,plan)
            createLocalBackup("Avant choix d’une édition personnelle").getOrThrow();media[i]=next
            try{save(false)}catch(e:Exception){media[i]=old;throw e}
        }finally{syncLock.unlock()}
    }

    fun applyBookPageTotal(plan:BookPagePlan) {
        requireHealthy();check(syncLock.tryLock()){ "Synchronisation en cours. Réessaie ensuite." }
        try { val index=media.indexOfFirst{it.id==plan.mediaId};require(index>=0);val old=media[index];val next=BookEditionRules.apply(old,plan)
            createLocalBackup("Avant changement du total de pages").getOrThrow();media[index]=next
            try{save(false)}catch(e:Exception){media[index]=old;throw e}
        } finally {syncLock.unlock()}
    }
    fun setPersonalProgress(id:String,current:Int,total:Int,unit:String,status:String,platform:String?,format:String?,minutes:Int?)=updateMedia(id) { item -> ProgressRules.update(item,current,total,unit,status,platform,format,minutes) }
    fun insights(query:InsightsRules.Query=InsightsRules.Query()):InsightsRules.Analysis=InsightsRules.analyse(media.toList(),episodes.toList(),gaming.value,query)

    /** Explicit addition only: never edits/removes an already published viewing. */
    @Synchronized
    fun addDatedViewing(mediaId:String,episodeId:String?,at:Long,expectedGeneration:Int):Result<String> = runCatching {
        requireHealthy();check(expectedGeneration==libraryGeneration) { "La bibliothèque a changé. Rouvre la fiche." }
        val mi=media.indexOfFirst { it.id==mediaId&&it.added&&!it.isSuggestion };require(mi>=0) { "Titre introuvable." }
        val oldMedia=media[mi]
        if(episodeId==null) {
            val next=ActivityRules.add(oldMedia,at);media[mi]=next
            try { save() } catch(e:Exception) { media[mi]=oldMedia;throw e }
        } else {
            val ei=episodes.indexOfFirst { it.id==episodeId&&it.showId==mediaId };require(ei>=0) { "Épisode introuvable." }
            val old=episodes[ei];val next=ActivityRules.add(old,at);episodes[ei]=next
            media[mi]=oldMedia.copy(lastWatchedAt=episodes.filter { it.showId==mediaId }.maxOfOrNull { it.lastWatchedAt?:0L }?.takeIf { it>0 })
            try { save() } catch(e:Exception) { episodes[ei]=old;media[mi]=oldMedia;throw e }
        }
        "Visionnage daté enregistré sans déplacer les dates précédentes."
    }

    @Synchronized
    fun saveEpisodePersonal(plan:EpisodePersonalPlan,generation:Int,account:String) {
        requireHealthy();check(generation==libraryGeneration&&account==episodeBatchAccount()){ "La bibliothèque ou le compte a changé. Rouvre la fiche." }
        check(syncLock.tryLock()){ "Synchronisation en cours. Réessaie ensuite." }
        val before=episodes.toList()
        try { val result=EpisodeDetailRules.apply(EpisodeDetailRules.State(media.toList(),before),plan)
            episodes.clear();episodes.addAll(result.episodes);save()
        }catch(e:Exception){episodes.clear();episodes.addAll(before);throw e}finally{syncLock.unlock()}
    }

    fun episodeBatchAccount()=isTraktConnected().toString()+":"+credentials.getString("trakt_username","").orEmpty()
    fun prepareEpisodeBatch(showId:String,ids:List<String>,action:String,includeUndatedFuture:Boolean=false)=EpisodeBatchRules.plan(EpisodeBatchRules.State(media.toList(),episodes.toList()),showId,ids,action,includeUndatedFuture)
    @Synchronized
    fun applyEpisodeBatch(plan:EpisodeBatchRules.Plan,generation:Int,account:String):String {
        requireHealthy();check(generation==libraryGeneration&&account==episodeBatchAccount()){ "La bibliothèque ou le compte a changé. Prépare un nouvel aperçu." }
        check(syncLock.tryLock()){ "Synchronisation en cours. Réessaie ensuite." }
        val before=EpisodeBatchRules.State(media.toList(),episodes.toList())
        try {
            val next=EpisodeBatchRules.apply(before,plan)
            if(plan.counts.changed==0)return "Tous les épisodes sont déjà dans cet état ou protégés."
            createLocalBackup("Avant action sur les épisodes").getOrThrow()
            media.clear();media.addAll(next.media);episodes.clear();episodes.addAll(next.episodes)
            save()
            return "${plan.counts.changed} épisode(s) modifié(s), ${plan.counts.unchanged} inchangé(s)."
        }catch(e:Exception){media.clear();media.addAll(before.media);episodes.clear();episodes.addAll(before.episodes);throw e}
        finally { syncLock.unlock() }
    }

    @Synchronized
    private fun changeEpisodeSafely(id:String,transform:(Episode)->Episode) {
        requireHealthy();check(syncLock.tryLock()){ "Synchronisation en cours. Réessaie ensuite." }
        val before=EpisodeBatchRules.State(media.toList(),episodes.toList())
        try {
            val index=episodes.indexOfFirst{it.id==id};if(index<0)return
            val old=episodes[index];require(media.any{it.id==old.showId&&it.added}){ "Série retirée." }
            val next=transform(old);if(next==old)return
            if(EpisodeBatchRules.hasHistory(old)&&!EpisodeBatchRules.hasHistory(next))createLocalBackup("Avant effacement de visionnages d’épisode").getOrThrow()
            episodes[index]=next;updateShowProgress(old.showId);save()
        }catch(e:Exception){media.clear();media.addAll(before.media);episodes.clear();episodes.addAll(before.episodes);throw e}
        finally{syncLock.unlock()}
    }
    fun markEpisodeWatched(id:String,watched:Boolean=true)=changeEpisodeSafely(id){TrackingRules.episode(it,if(watched)"watch" else "unwatch")}
    fun replayEpisode(id:String)=changeEpisodeSafely(id){TrackingRules.episode(it,"replay")}
    fun toggleEpisodeSkipped(id:String)=changeEpisodeSafely(id){TrackingRules.episode(it,"skip")}
    fun toggleEpisodeCollected(id:String)=changeEpisodeSafely(id){it.copy(collected=!it.collected)}

    fun markSeasonWatched(showId:String,season:Int,watched:Boolean=true) {
        val ids=episodes.filter{it.showId==showId&&it.season==season}.map{it.id};if(ids.isEmpty())return
        applyEpisodeBatch(prepareEpisodeBatch(showId,ids,if(watched)"watch" else "unwatch"),libraryGeneration,episodeBatchAccount())
    }
    fun markSeasonCollected(showId:String,season:Int,collected:Boolean=true) {
        val ids=episodes.filter{it.showId==showId&&it.season==season}.map{it.id};if(ids.isEmpty())return
        applyEpisodeBatch(prepareEpisodeBatch(showId,ids,if(collected)"collect" else "uncollect"),libraryGeneration,episodeBatchAccount())
    }
    fun markSeasonSkipped(showId:String,season:Int,skipped:Boolean=true) {
        val ids=episodes.filter{it.showId==showId&&it.season==season}.map{it.id};if(ids.isEmpty())return
        applyEpisodeBatch(prepareEpisodeBatch(showId,ids,if(skipped)"skip" else "unskip"),libraryGeneration,episodeBatchAccount())
    }
    fun markAllEpisodesWatched(showId:String,watched:Boolean=true) {
        val ids=episodes.filter{it.showId==showId&&(settings.value.includeSpecials||it.season!=0)}.map{it.id};if(ids.isEmpty())return
        applyEpisodeBatch(prepareEpisodeBatch(showId,ids,if(watched)"watch" else "unwatch"),libraryGeneration,episodeBatchAccount())
    }

    private fun updateShowProgress(showId: String) {
        val watchable = episodes.filter { it.showId == showId && !it.skipped && (settings.value.includeSpecials || it.season != 0) && (it.airDate == null || !it.airDate.isAfter(LocalDate.now())) }
        val last = episodes.filter { it.showId == showId }.maxOfOrNull { it.lastWatchedAt ?: 0L }?.takeIf { it > 0 }
        val idx = media.indexOfFirst { it.id == showId }
        if (idx >= 0) media[idx] = media[idx].copy(watched = watchable.isNotEmpty() && watchable.all { it.watched }, lastWatchedAt = last)
    }

    fun addSuggestion(item: MediaItem) {
        val prepared = attachFranchise(item.copy(added = true, isSuggestion = false, addedAt = System.currentTimeMillis()))
        upsertMedia(prepared)
        suggestions.removeAll { sameIdentity(it, prepared) }
        save()
    }

    fun addOrUpdate(item: MediaItem) {
        val prepared = attachFranchise(item.copy(isSuggestion = false, addedAt = item.addedAt.takeIf { it > 0 } ?: System.currentTimeMillis()))
        upsertMedia(prepared)
        suggestions.removeAll { sameIdentity(it, prepared) }
        save()
    }

    suspend fun personDetails(id: Int): Result<PersonProfile> = runCatching {
        val cfg=settings.value
        withContext(Dispatchers.IO) { tmdb.getPerson(id,cfg.tmdbApiKey.trim(),cfg.language) }
    }

    /** Network work is staged before a single UI-thread commit. Keep the user's latest tracking. */
    suspend fun addCatalogItem(item: MediaItem): Result<MediaItem> = runCatching {
        val libraryVersion=libraryGeneration
        requireHealthy()
        val cfg = settings.value
        val contentLanguage=ReleaseRules.contentLanguage(item.contentLanguage)
        val language=contentLanguage?:cfg.language
        val existed = withContext(Dispatchers.Main) { media.any { it.id == item.id && it.added } }
        var incomingEpisodes: List<Episode> = emptyList()
        var collection: List<MediaItem> = emptyList()
        var collectionName: String? = null
        val incoming = withContext(Dispatchers.IO) {
            if(item.source in PublicCatalogClient.sources){
                val bundle=publicCatalog.details(item,cfg.region);incomingEpisodes=bundle.episodes;bundle.media
            } else if(item.kind in setOf(MediaKind.SHOW,MediaKind.MOVIE) && (item.source in setOf("tmdb","seriesguide") || item.tmdbId != null)) {
                require(cfg.tmdbApiKey.isNotBlank()) { "Clé TMDb requise." }
                val id = tmdb.resolveId(item,cfg.tmdbApiKey.trim(),language)
                if(item.kind == MediaKind.SHOW) {
                    val bundle = tmdb.getShowBundle(id,cfg.tmdbApiKey.trim(),language,cfg.includeSpecials,cfg.region)
                    incomingEpisodes = bundle.episodes
                    enrichWithTraktMetadata(bundle.media).copy(contentLanguage=contentLanguage)
                } else {
                    val bundle = tmdb.getMovieBundle(id,cfg.tmdbApiKey.trim(),language,cfg.region)
                    collection = bundle.collection; collectionName = bundle.collectionName
                    enrichWithTraktMetadata(bundle.media).copy(contentLanguage=contentLanguage)
                }
            } else when(item.source) {
                "rawg" -> { require(cfg.rawgApiKey.isNotBlank()) { "Clé RAWG requise." }; rawg.getGameDetails(item.sourceId ?: error("Identifiant RAWG manquant."),cfg.rawgApiKey.trim()) }
                "openlibrary" -> openLibrary.getBookDetails(item.sourceId ?: error("Identifiant Open Library manquant."))
                else -> item
            }
        }
        withContext(Dispatchers.Main) {
            check(libraryVersion==libraryGeneration) { "La bibliothèque a été remplacée pendant le chargement. Aucun ajout tardif appliqué." }
            check(!existed || media.any { it.id == item.id && it.added }) { "L’élément a été retiré pendant le chargement. Actualisation annulée." }
            val oldMedia=media.toList(); val oldEpisodes=episodes.toList(); val oldSuggestions=suggestions.toList(); val oldFranchises=customFranchises.toList()
            try {
                val latest = media.firstOrNull { sameIdentity(it,item) || sameIdentity(it,incoming) } ?: item
                if(latest.added && latest.source!=incoming.source && latest.source !in setOf("local","seriesguide"))return@withContext latest
                var final = attachFranchise(mergeMedia(latest.copy(added=true),incoming))
                var def = final.franchiseId?.let { id -> allFranchiseDefinitions().firstOrNull { it.id==id } }
                if(!final.franchiseManual && def == null && !collectionName.isNullOrBlank()) def=ensureCustomFranchise(collectionName!!,listOf(collectionName!!))
                if(!final.franchiseManual && def != null) final=final.copy(franchiseId=def.id,franchiseName=def.name)
                final=upsertMedia(final)
                incomingEpisodes.forEach { upsertEpisode(it.copy(showId=final.id)) }
                if(final.kind==MediaKind.SHOW) updateShowProgress(final.id)
                collection.filter { !final.franchiseManual }.forEach { candidate ->
                    val tagged=if(def!=null) candidate.copy(franchiseId=def.id,franchiseName=def.name) else attachFranchise(candidate)
                    if(media.none { sameIdentity(it,tagged) }) upsertSuggestion(tagged)
                }
                suggestions.removeAll { sameIdentity(it,final) }
                save()
                media.first { it.id==final.id }
            } catch(e:Exception) {
                media.clear();media.addAll(oldMedia);episodes.clear();episodes.addAll(oldEpisodes)
                suggestions.clear();suggestions.addAll(oldSuggestions);customFranchises.clear();customFranchises.addAll(oldFranchises)
                throw e
            }
        }
    }

    suspend fun refreshMedia(id: String): Result<MediaItem> {
        val current = media.firstOrNull { it.id == id } ?: return Result.failure(IllegalArgumentException("Élément introuvable."))
        return addCatalogItem(current.copy(added = true))
    }

    suspend fun setContentLanguage(id:String,language:String?):Result<MediaItem> = runCatching {
        val current=media.firstOrNull { it.id==id }?:error("Titre introuvable.")
        require(current.kind in setOf(MediaKind.SHOW,MediaKind.MOVIE))
        addCatalogItem(current.copy(contentLanguage=ReleaseRules.contentLanguage(language))).getOrThrow()
    }

    fun needsCatalogRefresh(): Boolean = media.any { item ->
        item.added && item.source in setOf("tmdb", "rawg", "openlibrary", "tvmaze", "itunes", "freetogame") &&
            (!item.catalogComplete || (item.kind == MediaKind.SHOW && !isShowCatalogComplete(item.id)))
    }

    suspend fun refreshIncompleteCatalog(maxItems: Int = 40): Result<String> = runCatching {
        requireHealthy()
        val candidates = media.filter { item ->
            item.added && item.source in setOf("tmdb", "rawg", "openlibrary", "tvmaze", "itunes", "freetogame") &&
                (!item.catalogComplete || (item.kind == MediaKind.SHOW && !isShowCatalogComplete(item.id)))
        }.take(maxItems.coerceIn(1, 100))
        var ok = 0; val failures = mutableListOf<String>()
        for (item in candidates) {
            val result = addCatalogItem(item.copy(added = true))
            if (result.isSuccess) ok++ else failures += "${item.title}: ${result.exceptionOrNull()?.message ?: "erreur"}"
        }
        buildString {
            append("Bibliothèque complétée : $ok/${candidates.size} fiche(s).")
            if (failures.isNotEmpty()) append(" ${failures.size} échec(s) : ${failures.take(3).joinToString(" · ")}")
        }
    }

    private fun enrichWithTraktMetadata(item: MediaItem): MediaItem {
        val tmdbId = item.tmdbId ?: return item
        val clientId = settings.value.traktClientId.trim()
        if (clientId.isBlank() || item.kind !in setOf(MediaKind.SHOW, MediaKind.MOVIE)) return item
        val meta = runCatching { trakt.publicMetadata(tmdbId, item.kind, clientId) }.getOrNull() ?: return item
        val traktRating = meta.rating?.let { RatingSource("trakt", "Trakt", it, 10.0, meta.votes, meta.url) }
        val ratings = (item.ratings.filterNot { it.source.equals("trakt", true) } + listOfNotNull(traktRating)).distinctBy { it.source.lowercase() }
        val mappedStatus = when (meta.status?.lowercase()) {
            "returning series", "continuing" -> ShowStatus.CONTINUING
            "in production" -> ShowStatus.IN_PRODUCTION
            "planned", "upcoming" -> ShowStatus.UPCOMING
            "pilot" -> ShowStatus.PILOT
            "ended" -> ShowStatus.ENDED
            "canceled", "cancelled" -> ShowStatus.CANCELED
            else -> item.status
        }
        return item.copy(
            ratings = ratings,
            status = if (item.kind == MediaKind.SHOW) mappedStatus else item.status,
            statusRaw = item.statusRaw ?: meta.status,
            network = item.network ?: meta.network,
            contentRating = item.contentRating ?: meta.certification,
            runtimeMinutes = item.runtimeMinutes ?: meta.runtimeMinutes,
            genres = item.genres + meta.genres,
            releaseSchedule = item.releaseSchedule ?: meta.airs
        )
    }

    private fun personalListState()=ListRules.State(media.toList(),suggestions.toList(),userLists.toList())
    @Synchronized
    private fun changePersonalList(transform:(ListRules.State)->ListRules.State,backup:Boolean=false) {
        requireHealthy();check(syncLock.tryLock()){ "Synchronisation en cours. Réessaie ensuite." };val before=personalListState()
        try {
            val next=transform(before)
            if(backup)createLocalBackup("Avant modification d’une liste personnelle").getOrThrow()
            media.clear();media.addAll(next.media);suggestions.clear();suggestions.addAll(next.suggestions);userLists.clear();userLists.addAll(next.lists)
            save(markTraktDirty=false)
        }catch(e:Exception){media.clear();media.addAll(before.media);suggestions.clear();suggestions.addAll(before.suggestions);userLists.clear();userLists.addAll(before.lists);throw e}
        finally { syncLock.unlock() }
    }
    fun addList(name:String,description:String=""):String? {
        val id=UUID.randomUUID().toString();changePersonalList({ListRules.create(it,UserList(id,name,description))});return id
    }
    fun updateList(id:String,name:String,description:String,sortMode:SortMode,ascending:Boolean) {
        changePersonalList({s->ListRules.edit(s,ListRules.selected(s,id).copy(name=name,description=description,sortMode=sortMode,ascending=ascending,manualOrder=false))})
    }
    fun savePersonalList(next:UserList,expected:UserList){changePersonalList({ListRules.edit(it,next,expected)})}
    fun deleteList(id:String){changePersonalList({ListRules.remove(it,id)},backup=true)}
    fun deletePersonalList(expected:UserList){changePersonalList({ListRules.remove(it,expected.id,expected)},backup=true)}
    fun duplicatePersonalList(expected:UserList,name:String):String {
        val id=UUID.randomUUID().toString();changePersonalList({ListRules.duplicate(it,expected.id,id,name,expected)});return id
    }
    fun listItems(list:UserList)=ListRules.sorted(personalListState(),list.id,episodes.toList())
    fun personalListOrder(id:String)=ListRules.orderedIds(personalListState(),id)
    fun preparePersonalMembers(id:String,chosen:List<String>)=ListRules.plan(personalListState(),id,chosen)
    fun applyPersonalMembers(plan:ListRules.Plan){changePersonalList({ListRules.apply(it,plan)},backup=true)}
    fun movePersonalMember(id:String,mediaId:String,position:Int,expected:List<String>){changePersonalList({ListRules.move(it,id,mediaId,position,expected)})}
    fun toggleList(itemId:String,listId:String) {
        changePersonalList({s->
            require(s.media.any{it.id==itemId&&ListRules.own(it)}){"Titre retiré."}
            val ids=ListRules.members(s,listId).map{it.id}
            ListRules.apply(s,ListRules.plan(s,listId,if(itemId in ids)ids-itemId else ids+itemId))
        })
    }

    private fun franchiseState()=FranchiseRules.State(media.toList(),suggestions.toList(),customFranchises.toList())
    @Synchronized
    private fun changeFranchise(transform:(FranchiseRules.State)->FranchiseRules.State,backup:Boolean=false) {
        requireHealthy();check(syncLock.tryLock()){ "Synchronisation en cours. Réessaie ensuite." }
        val before=franchiseState()
        try {
            val next=transform(before)
            if(backup)createLocalBackup("Avant suppression de franchise").getOrThrow()
            media.clear();media.addAll(next.media);suggestions.clear();suggestions.addAll(next.suggestions);customFranchises.clear();customFranchises.addAll(next.custom)
            save(markTraktDirty=false)
        }catch(e:Exception){media.clear();media.addAll(before.media);suggestions.clear();suggestions.addAll(before.suggestions);customFranchises.clear();customFranchises.addAll(before.custom);throw e}
        finally { syncLock.unlock() }
    }
    fun addCustomFranchise(name:String,aliasesText:String=""):String? {
        val id="custom-${UUID.randomUUID()}"
        val aliases=aliasesText.split(',', ';', '\n').map{it.trim()}.filter{it.isNotEmpty()}.distinct()
        changeFranchise({FranchiseRules.put(it,FranchiseEngine.builtIns,FranchiseDefinition(id,name.trim(),listOf(name.trim().take(160)),aliases,custom=true))})
        return id
    }
    fun updateCustomFranchise(id:String,name:String,aliasesText:String="") {
        val aliases=aliasesText.split(',', ';', '\n').map{it.trim()}.filter{it.isNotEmpty()}.distinct()
        changeFranchise({s->val old=allFranchiseDefinitions().firstOrNull{it.id==id}?:error("Franchise retirée.");FranchiseRules.put(s,FranchiseEngine.builtIns,old.copy(name=name.trim(),queryTerms=listOf(name.trim().take(160)),aliases=aliases),true)})
    }
    fun deleteCustomFranchise(id:String) { changeFranchise({FranchiseRules.remove(it,FranchiseEngine.builtIns,id)},backup=true) }
    fun assignFranchise(itemId:String,franchiseId:String?) { changeFranchise({FranchiseRules.assign(it,FranchiseEngine.builtIns,itemId,franchiseId)}) }
    fun detectFranchise(itemId:String) { changeFranchise({FranchiseRules.assign(it,FranchiseEngine.builtIns,itemId,null,true)}) }
    fun moveInFranchise(id:String,mediaId:String,position:Int) { changeFranchise({FranchiseRules.move(it,FranchiseEngine.builtIns,id,mediaId,position)}) }
    fun keepFranchiseSuggestion(id:String,candidate:MediaItem,expected:FranchiseDefinition,generation:Int) {
        changeFranchise({check(generation==libraryGeneration&&allFranchiseDefinitions().firstOrNull{d->d.id==id}==expected){"Bibliothèque ou définition modifiée : relance la recherche."};FranchiseRules.suggest(it,FranchiseEngine.builtIns,id,candidate)})
    }
    fun dismissFranchiseSuggestion(id:String) { changeFranchise({s->require(s.suggestions.any{it.id==id});s.copy(suggestions=s.suggestions.filterNot{it.id==id})}) }

    private fun ensureCustomFranchise(name: String, queryTerms: List<String>, aliases: List<String> = emptyList()): FranchiseDefinition {
        allFranchiseDefinitions().firstOrNull { it.name.equals(name, true) }?.let { return it }
        val slug = name.lowercase().replace("[^a-z0-9]+".toRegex(), "-").trim('-').ifBlank { "franchise" }
        var id = "custom-$slug"
        var suffix = 2
        while (allFranchiseDefinitions().any { it.id == id }) id = "custom-$slug-${suffix++}"
        val def = FranchiseDefinition(id, name.trim(), queryTerms.distinct(), aliases.distinct(), "Franchise personnalisée.", custom = true)
        customFranchises.add(def)
        return def
    }

    @Synchronized
    fun updateSettings(next:AppSettings) {
        requireHealthy();val value=SettingsRules.normalize(next);val before=settings.value
        val changed=SettingsRules.privateChanged(before,value);val appChange=SettingsRules.traktChanged(before,value)
        val locked=if(appChange)syncLock.tryLock() else false
        check(!appChange||locked){"Synchronisation Trakt en cours : attends sa fin avant de changer son application."}
        try {
            val oldRef=prefs.getString("settings_ref_v31",null);val priorRef=prefs.getString("settings_previous_ref_v31",null)
            val oldPublic=prefs.getString("public_preferences_v18",null);val oldApp=prefs.getString("trakt_app_ref_v31",null)
            val ref=if(changed)"slot-"+UUID.randomUUID().toString() else oldRef
            val json=settingsToJson(value)
            if(changed)check(credentials.edit().putString("settings_slot_"+ref,json.toString()).commit()){"Accès non enregistrés ; l’ancienne sélection reste utilisée."}
            for(key in listOf("tmdbApiKey","rawgApiKey","traktClientId","traktClientSecret"))json.remove(key)
            val edit=prefs.edit().putString("public_preferences_v18",json.toString())
            if(changed)edit.putString("settings_ref_v31",ref).putString("settings_previous_ref_v31",oldRef)
            if(appChange)edit.putString("trakt_app_ref_v31",ref)
            if(!edit.commit()) {
                // commit(false) may still alter the in-process cache, so restore selectors too.
                val restored=prefs.edit().putString("public_preferences_v18",oldPublic).putString("settings_ref_v31",oldRef).putString("settings_previous_ref_v31",priorRef).putString("trakt_app_ref_v31",oldApp).commit()
                if(!restored){recoveryMessage.value="Réglages non enregistrés et reprise impossible : écriture bloquée. Exporte la bibliothèque.";originalRecoveryState=prefs.getString("state",null)}
                error("Réglages non enregistrés. La session n’a pas été volontairement déconnectée.")
            }
            settings.value=value
            if(appChange)disconnectTrakt()
            if(changed&&priorRef!=null&&priorRef!=oldRef&&SettingsRules.validReference(priorRef))credentials.edit().remove("settings_slot_"+priorRef).apply()
        } finally {if(locked)syncLock.unlock()}
    }

    suspend fun searchCatalog(query:String,kinds:Set<MediaKind> = MediaKind.entries.toSet(),page:Int=1):Result<List<MediaItem>> = runCatching {
        val q=query.trim();require(q.length in 2..300) { "Entre au moins deux caractères." }
        val cfg=settings.value;val found=mutableListOf<MediaItem>();val errors=mutableListOf<String>();var successes=0
        kinds.forEach { kind -> runCatching {
            val rows=when(kind) {
                MediaKind.SHOW,MediaKind.MOVIE -> if(cfg.tmdbApiKey.isNotBlank())tmdb.searchMulti(q,cfg.tmdbApiKey.trim(),cfg.language,kind,page) else publicCatalog.search(q,kind,page,cfg.region)
                MediaKind.GAME -> if(cfg.rawgApiKey.isNotBlank())rawg.searchGames(q,cfg.rawgApiKey.trim(),page=page) else publicCatalog.search(q,kind,page,cfg.region)
                MediaKind.BOOK -> openLibrary.searchBooks(q,language=cfg.language,page=page)
            };found+=rows;successes++
        }.onFailure { errors+=(it.message?:"Catalogue indisponible.") } }
        if(successes==0)error(errors.joinToString(" · "))
        if(errors.isNotEmpty())syncMessage.value="Recherche partielle : ${errors.joinToString(" · ")}"
        found.map(::attachFranchise).distinctBy(FranchiseEngine::stableKey)
    }

    suspend fun searchTmdb(query: String): Result<List<MediaItem>> = searchCatalog(query, setOf(MediaKind.SHOW, MediaKind.MOVIE))

    suspend fun discoverCatalog(kinds: Set<MediaKind> = MediaKind.entries.toSet(),page:Int=1): Result<List<MediaItem>> = runCatching {
        require(page in 1..DiscoveryRules.MAX_PAGES)
        val cfg = settings.value
        val out = mutableListOf<MediaItem>()
        if (cfg.tmdbApiKey.isNotBlank()) {
            if (MediaKind.SHOW in kinds) out += tmdb.discover(MediaKind.SHOW, cfg.tmdbApiKey.trim(), cfg.language, cfg.region,cfg.discoverYearMin,cfg.discoverYearMax,cfg.discoverLanguage,page)
            if (MediaKind.MOVIE in kinds) out += tmdb.discover(MediaKind.MOVIE, cfg.tmdbApiKey.trim(), cfg.language, cfg.region,cfg.discoverYearMin,cfg.discoverYearMax,cfg.discoverLanguage,page)
        }
        if(cfg.tmdbApiKey.isBlank() && MediaKind.SHOW in kinds)out+=publicCatalog.discover(MediaKind.SHOW,page)
        if (MediaKind.GAME in kinds) out += if(cfg.rawgApiKey.isNotBlank())rawg.discoverGames(cfg.rawgApiKey.trim(),page=page) else publicCatalog.discover(MediaKind.GAME,page)
        if (MediaKind.BOOK in kinds) out += openLibrary.discoverBooks(cfg.language,page=page)
        out.map(::attachFranchise).distinctBy(FranchiseEngine::stableKey)
    }

    suspend fun scanFranchisePage(session:FranchiseScanRules.Session,kind:MediaKind):Result<FranchiseScanRules.Page> = runCatching {
        requireHealthy();val current=allFranchiseDefinitions().firstOrNull{it.id==session.definition.id}?:error("Franchise retirée.");require(current==session.definition){"La définition a changé. Relance le scanner."};val cursor=FranchiseScanRules.current(session,kind);require(cursor.term.isNotBlank()){"Cette catégorie est terminée."};val rows=searchCatalog(cursor.term,setOf(kind),cursor.page).getOrThrow();FranchiseScanRules.Page(rows,rows.size>=20)
    }

    fun commitFranchiseScan(session:FranchiseScanRules.Session,expected:FranchiseDefinition,generation:Int):Int {
        var inserted=0
        changeFranchise({state->check(generation==libraryGeneration){"Bibliothèque remplacée : aucun lot enregistré."};val current=allFranchiseDefinitions().firstOrNull{it.id==expected.id}?:error("Franchise retirée.");check(current==expected){"Définition modifiée : relance le scanner."};val plan=FranchiseScanRules.plan(session,state,FranchiseEngine.builtIns,current);inserted=plan.inserted;plan.state})
        return inserted
    }

    suspend fun refreshFranchiseSuggestions(definition: FranchiseDefinition): Result<Int> = runCatching {
        val cfg = settings.value
        val found = linkedMapOf<String, MediaItem>()
        val terms = (definition.queryTerms + definition.aliases).distinct().take(8)
        terms.forEach { term ->
            searchCatalog(term).getOrThrow().forEach { addFranchiseCandidate(found,definition,it) }
        }
        val existingKeys = media.map(FranchiseEngine::stableKey).toSet()
        var inserted = 0
        found.values.filterNot { FranchiseEngine.stableKey(it) in existingKeys }.forEach { candidate ->
            val before = suggestions.size
            upsertSuggestion(candidate)
            if (suggestions.size > before) inserted++
        }
        save()
        inserted
    }

    private fun addFranchiseCandidate(target: MutableMap<String, MediaItem>, definition: FranchiseDefinition, raw: MediaItem) {
        if (!FranchiseEngine.belongsTo(definition, raw)) return
        val tagged = raw.copy(added = false, isSuggestion = true, franchiseId = definition.id, franchiseName = definition.name)
        target[FranchiseEngine.stableKey(tagged)] = tagged
    }

    private fun currentTraktAppRef():String?=prefs.getString("trakt_app_ref_v31",null)
    fun traktCheckInSession():TraktCheckInSession?=traktCheckIn.value
    fun traktCheckInRecoveryRaw():String?=checkInRecoveryRaw
    fun traktCheckInTiming(now:Long=System.currentTimeMillis()):TraktCheckInTiming?=traktCheckIn.value?.let{TraktCheckInRules.timing(it,now)}
    private fun requireCheckInContext(s:TraktCheckInSession) {
        requireHealthy();check(!syncLock.isLocked) { "Synchronisation Trakt en cours. Réessaie après sa fin." }
        check(isTraktConnected()) { "Reconnecte le compte Trakt associé à ce check-in." }
        val account=traktUsername()?:error("Reconnecte le compte Trakt associé à ce check-in.")
        check(TraktCheckInRules.sameContext(s,account,currentTraktAppRef())) { "Ce check-in appartient à un autre compte ou une autre application Trakt. Aucun envoi effectué." }
    }
    private data class CheckInOperation(val session:TraktCheckInSession,val auth:Int,val library:Int,var expected:TraktCheckInSession?=session)
    private fun checkInOperation(s:TraktCheckInSession):CheckInOperation {requireCheckInContext(s);return CheckInOperation(s,authGeneration,libraryGeneration)}
    private fun requireCheckInOperation(op:CheckInOperation) {
        requireCheckInContext(op.session)
        check(op.auth==authGeneration&&op.library==libraryGeneration&&traktCheckIn.value==op.expected) { "La session, le compte ou la bibliothèque a changé. Vérifie le check-in avant de poursuivre." }
    }
    private fun persistCheckInOperation(value:TraktCheckInSession?,op:CheckInOperation) {requireCheckInOperation(op);persistTraktCheckIn(value);op.expected=value}
    private fun rememberCheckIn(value:TraktCheckInSession,op:CheckInOperation) {runCatching{persistCheckInOperation(value,op)}}
    private fun requireCheckInHistoryClear(kind:String,tmdbId:Int) {check(outbox().none{it.key=="$kind:$tmdbId"&&it.field=="history"}) { "Un visionnage de ce titre attend une synchronisation. Résous-le avant le check-in." }}
    @Synchronized private fun persistTraktCheckIn(value:TraktCheckInSession?) {
        value?.let(TraktCheckInRules::validate)
        val edit=prefs.edit();if(value==null)edit.remove("trakt_checkin_v35") else edit.putString("trakt_checkin_v35",checkInToJson(value).toString())
        check(edit.commit()) { "Session de check-in non sauvegardée. Aucune nouvelle requête distante." }
        traktCheckIn.value=value
    }
    private fun startCheckIn(target:TraktCheckInTarget):TraktCheckInSession {
        requireHealthy();check(!syncLock.isLocked) { "Synchronisation Trakt en cours. Réessaie après sa fin." }
        check(checkInLoadProblem==null) { "Récupère ou oublie explicitement la session illisible avant de continuer." }
        check(traktCheckIn.value==null) { "Un check-in existe déjà. Termine, vérifie ou annule-le depuis Aujourd’hui." }
        requireCheckInHistoryClear(target.kind,target.tmdbId)
        val account=traktUsername()?:error("Connecte d’abord ton compte Trakt.")
        val value=TraktCheckInRules.create(target,account,currentTraktAppRef())
        persistTraktCheckIn(value);return value
    }
    suspend fun traktCheckInMedia(mediaId:String):Result<String> = checkInLock.withLock {
        runCatching {
            val cfg=settings.value;require(cfg.traktClientId.isNotBlank()) { "Ajoute ton Client ID Trakt dans Réglages." }
            val initial=startCheckIn(TraktCheckInRules.movie(media.toList(),mediaId));val op=checkInOperation(initial)
            try {
                val token=validTraktToken(cfg)?:error("Connecte d’abord ton compte Trakt.")
                requireCheckInOperation(op)
                val remote=withContext(Dispatchers.IO){trakt.startCheckInMovie(initial.tmdbId,token,cfg.traktClientId.trim())}
                persistCheckInOperation(TraktCheckInRules.started(initial,remote),op)
                "Check-in Trakt commencé pour ${initial.title}. Le visionnage local ne sera ajouté qu’après confirmation."
            }catch(e:CancellationException){throw e}catch(e:Exception){rememberCheckIn(TraktCheckInRules.uncertain(initial,"start",e.message?:"Réponse distante inconnue"),op);throw e}
        }.onFailure{if(it is CancellationException)throw it}
    }
    suspend fun traktCheckInEpisode(episodeId:String):Result<String> = checkInLock.withLock {
        runCatching {
            val cfg=settings.value;require(cfg.traktClientId.isNotBlank()) { "Ajoute ton Client ID Trakt dans Réglages." }
            val initial=startCheckIn(TraktCheckInRules.episode(media.toList(),episodes.toList(),episodeId));val op=checkInOperation(initial)
            try {
                val token=validTraktToken(cfg)?:error("Connecte d’abord ton compte Trakt.")
                requireCheckInOperation(op)
                val remote=withContext(Dispatchers.IO){trakt.startCheckInEpisode(initial.tmdbId,token,cfg.traktClientId.trim())}
                persistCheckInOperation(TraktCheckInRules.started(initial,remote),op)
                "Check-in Trakt commencé : ${initial.title} ${initial.code.orEmpty()}."
            }catch(e:CancellationException){throw e}catch(e:Exception){rememberCheckIn(TraktCheckInRules.uncertain(initial,"start",e.message?:"Réponse distante inconnue"),op);throw e}
        }.onFailure{if(it is CancellationException)throw it}
    }
    @Synchronized private fun applyCheckInLocally(session:TraktCheckInSession,watchedAt:Long) {
        val beforeMedia=media.toList();val beforeEpisodes=episodes.toList();val next=TraktCheckInRules.apply(TraktCheckInState(beforeMedia,beforeEpisodes),session,watchedAt)
        try {media.clear();media.addAll(next.media);episodes.clear();episodes.addAll(next.episodes);save(false)}
        catch(e:Exception){media.clear();media.addAll(beforeMedia);episodes.clear();episodes.addAll(beforeEpisodes);throw e}
    }
    private suspend fun cleanupConfirmedCheckIn(session:TraktCheckInSession,cfg:AppSettings,token:String,op:CheckInOperation):String {
        val cleanup=TraktCheckInRules.confirmed(session);persistCheckInOperation(cleanup,op)
        return try {requireCheckInOperation(op);withContext(Dispatchers.IO){trakt.cancelCheckIn(token,cfg.traktClientId.trim())};persistCheckInOperation(null,op);"Visionnage confirmé localement et sur Trakt ; check-in nettoyé."}
        catch(e:CancellationException){throw e}catch(e:Exception){
            if(e is TraktClient.HttpFailure&&e.status==404){persistCheckInOperation(null,op);"Visionnage confirmé ; aucun check-in distant restant."}
            else{rememberCheckIn(cleanup.copy(lastError=(e.message?:"Nettoyage distant impossible").take(800)),op);"Visionnage confirmé. Le nettoyage du check-in distant reste à réessayer depuis Aujourd’hui."}
        }
    }
    suspend fun finishTraktCheckIn():Result<String> = checkInLock.withLock {
        runCatching {
            val current=traktCheckIn.value?:error("Aucun check-in actif.");requireCheckInContext(current);require(current.status !in setOf("uncertain-start","uncertain-cancel")) { "État distant incertain : vérifie ou annule avant de terminer." }
            val op=checkInOperation(current);val cfg=settings.value;val token=validTraktToken(cfg)?:error("Reconnecte ton compte Trakt.");requireCheckInOperation(op)
            val mode=TraktCheckInRules.completionMode(current)
            if(mode=="cleanup")return@runCatching cleanupConfirmedCheckIn(current,cfg,token,op)
            requireCheckInHistoryClear(current.kind,current.tmdbId)
            var finishing=TraktCheckInRules.beginFinish(current);persistCheckInOperation(finishing,op)
            if(mode=="verify") {
                val rows=try {withContext(Dispatchers.IO){trakt.recentCheckInHistory(finishing.kind,token,cfg.traktClientId.trim())}}
                catch(e:CancellationException){throw e}catch(e:Exception){rememberCheckIn(TraktCheckInRules.uncertain(finishing,"finish","Historique distant illisible : ${e.message}"),op);return@runCatching "Fin distante non vérifiée. Aucun second visionnage envoyé ; réessaie Vérifier."}
                requireCheckInOperation(op)
                val watched=TraktCheckInRules.recentMatch(finishing,rows.map{it.tmdbId to it.watchedAt})
                if(watched==null){persistCheckInOperation(TraktCheckInRules.uncertain(finishing,"finish","Aucun historique correspondant visible. Aucun ajout envoyé pour éviter un doublon."),op);return@runCatching "Historique Trakt pas encore visible. Aucun visionnage local ou distant ajouté."}
                finishing=TraktCheckInRules.remoteHistoryFinish(finishing,watched);persistCheckInOperation(finishing,op)
            } else {
                try {withContext(Dispatchers.IO){trakt.addCheckInHistory(finishing.kind,finishing.tmdbId,finishing.finishAt!!,token,cfg.traktClientId.trim())}}
                catch(e:CancellationException){throw e}catch(e:Exception){
                    val rejected=e is TraktClient.HttpFailure&&e.status in setOf(400,401,403,404,410,412,413,415,422,423,429)
                    rememberCheckIn(if(rejected)finishing.copy(status="active",finishAt=null,lastError=e.message?.take(800)) else TraktCheckInRules.uncertain(finishing,"finish",e.message?:"Réponse historique inconnue"),op);throw e
                }
                requireCheckInOperation(op)
            }
            requireCheckInHistoryClear(finishing.kind,finishing.tmdbId)
            try {applyCheckInLocally(finishing,finishing.finishAt!!)}catch(e:Exception){rememberCheckIn(TraktCheckInRules.uncertain(finishing,"finish","Historique distant confirmé ; enregistrement local à vérifier : ${e.message}"),op);throw e}
            cleanupConfirmedCheckIn(finishing,cfg,token,op)
        }.onFailure{if(it is CancellationException)throw it}
    }
    suspend fun verifyTraktCheckIn():Result<String> = checkInLock.withLock {
        runCatching {
            val current=traktCheckIn.value?:error("Aucun check-in à vérifier.");val op=checkInOperation(current);val cfg=settings.value;val token=validTraktToken(cfg)?:error("Reconnecte ton compte Trakt.");requireCheckInOperation(op)
            if(TraktCheckInRules.completionMode(current)=="cleanup")return@runCatching cleanupConfirmedCheckIn(current,cfg,token,op)
            requireCheckInHistoryClear(current.kind,current.tmdbId)
            val rows=withContext(Dispatchers.IO){trakt.recentCheckInHistory(current.kind,token,cfg.traktClientId.trim())};requireCheckInOperation(op)
            val watched=TraktCheckInRules.recentMatch(current,rows.map{it.tmdbId to it.watchedAt})?:error("Aucun visionnage correspondant trouvé dans l’historique récent Trakt. Aucun suivi local modifié.")
            val finishing=TraktCheckInRules.remoteHistoryFinish(current,watched);persistCheckInOperation(finishing,op);requireCheckInHistoryClear(current.kind,current.tmdbId);applyCheckInLocally(finishing,watched);cleanupConfirmedCheckIn(finishing,cfg,token,op)
        }.onFailure{if(it is CancellationException)throw it}
    }
    suspend fun cancelTraktCheckIn():Result<String> = checkInLock.withLock {
        runCatching {
            val current=traktCheckIn.value?:error("Aucun check-in actif.");val op=checkInOperation(current);val cfg=settings.value;val token=validTraktToken(cfg)?:error("Reconnecte ton compte Trakt.");requireCheckInOperation(op)
            try {withContext(Dispatchers.IO){trakt.cancelCheckIn(token,cfg.traktClientId.trim())};persistCheckInOperation(null,op);"Check-in Trakt annulé. Aucun visionnage local ajouté."}
            catch(e:CancellationException){throw e}catch(e:Exception){if(e is TraktClient.HttpFailure&&e.status==404){persistCheckInOperation(null,op);"Aucun check-in distant restant."}else{rememberCheckIn(TraktCheckInRules.uncertain(current,"cancel",e.message?:"Annulation distante inconnue"),op);throw e}}
        }.onFailure{if(it is CancellationException)throw it}
    }
    suspend fun retryTraktCheckInCleanup():Result<String> = checkInLock.withLock {
        runCatching {val current=traktCheckIn.value?:error("Aucun check-in à nettoyer.");require(TraktCheckInRules.completionMode(current)=="cleanup"){"Le check-in n’est pas encore confirmé."};val op=checkInOperation(current);val cfg=settings.value;val token=validTraktToken(cfg)?:error("Reconnecte ton compte Trakt.");requireCheckInOperation(op);cleanupConfirmedCheckIn(current,cfg,token,op)}.onFailure{if(it is CancellationException)throw it}
    }
    fun forgetLocalTraktCheckIn():Result<String> = runCatching {
        requireHealthy();check(checkInLock.tryLock()) { "Une opération de check-in est en cours. Attends son résultat." }
        try{persistTraktCheckIn(null);checkInLoadProblem=null;"Référence locale oubliée. Aucun suivi ou historique distant modifié."}finally{checkInLock.unlock()}
    }

    suspend fun traktComments(mediaId: String): Result<List<CommunityComment>> {
        val item = media.firstOrNull { it.id == mediaId } ?: return Result.failure(IllegalArgumentException("Élément introuvable."))
        val tmdbId = item.tmdbId ?: return Result.success(emptyList())
        val clientId = settings.value.traktClientId.trim()
        if (clientId.isBlank() || item.kind !in setOf(MediaKind.SHOW, MediaKind.MOVIE)) return Result.success(emptyList())
        return runCatching { trakt.publicComments(tmdbId, item.kind, clientId) }
    }

    private fun reviewDraftKey(account: String, mediaId: String) = "trakt_review_draft_v10:$account:$mediaId"
    fun readReviewDraft(mediaId: String, account: String = traktUsername().orEmpty()): ReviewDraft = runCatching {
        val o = JSONObject(prefs.getString(reviewDraftKey(account,mediaId),null) ?: return ReviewDraft())
        ReviewDraft(o.optString("text"),o.optBoolean("spoiler"),o.optLong("editingId",0).takeIf { it>0 },o.optBoolean("uncertain"))
    }.getOrDefault(ReviewDraft())
    fun saveReviewDraft(mediaId:String,draft:ReviewDraft,account:String=traktUsername().orEmpty()) {
        requireHealthy()
        require(draft.text.length<=10_000) { "Brouillon trop long." }
        val o=JSONObject().put("text",draft.text).put("spoiler",draft.spoiler).put("editingId",draft.editingId?:JSONObject.NULL).put("uncertain",draft.uncertain)
        check(prefs.edit().putString(reviewDraftKey(account,mediaId),o.toString()).commit()) { "Brouillon non sauvegardé : publication annulée." }
    }
    suspend fun readTraktReviews(mediaId:String,page:Int):Result<TraktClient.ReviewPage> = runCatching {
        requireHealthy()
        val item=media.firstOrNull { it.id==mediaId } ?: error("Titre absent.")
        val cfg=settings.value; val generation=authGeneration
        val result=withContext(Dispatchers.IO) { trakt.reviewPage(item.tmdbId?:error("TMDb ID absent."),item.kind,cfg.traktClientId,page) }
        check(generation==authGeneration) { "Compte modifié : réponse ignorée." }
        result
    }.onFailure { if(it is CancellationException)throw it }

    /** Never retried: the persisted uncertain draft must be reconciled by the user after a lost response. */
    suspend fun publishTraktReview(mediaId:String,draft:ReviewDraft):Result<CommunityComment> = runCatching {
        requireHealthy()
        check(!draft.uncertain) { "Vérifie d’abord sur Trakt si la précédente publication a abouti." }
        ReleaseRules.reviewText(draft.text)
        val item=media.firstOrNull { it.id==mediaId } ?: error("Titre absent.")
        val cfg=settings.value; val generation=authGeneration; val account=traktUsername()?:error("Connecte ton compte Trakt.")
        // Persist *before* any potentially successful write.
        saveReviewDraft(mediaId,draft.copy(uncertain=true),account)
        try {
            val result=withContext(Dispatchers.IO) {
                val token=validTraktToken(cfg)?:error("Reconnecte ton compte Trakt.")
                trakt.writeReview(item.tmdbId?:error("TMDb ID absent."),item.kind,draft,token,cfg.traktClientId,account)
            }
            check(generation==authGeneration) { "Compte modifié : vérifie la publication sur Trakt." }
            saveReviewDraft(mediaId,ReviewDraft(),account)
            result
        } catch(e:Exception) {
            if(e is TraktClient.HttpFailure && e.status in 400..499)saveReviewDraft(mediaId,draft.copy(uncertain=false),account)
            throw e
        }
    }.onFailure { if(it is CancellationException)throw it }

    suspend fun deleteTraktReview(id:Long):Result<Unit> = runCatching {
        requireHealthy()
        val cfg=settings.value;val generation=authGeneration;val account=traktUsername()?:error("Compte non connecté.")
        withContext(Dispatchers.IO) { trakt.deleteReview(id,validTraktToken(cfg)?:error("Reconnecte ton compte."),cfg.traktClientId,account) }
        check(generation==authGeneration) { "Compte modifié : vérifie la suppression sur Trakt." }
    }.onFailure { if(it is CancellationException)throw it }

    suspend fun readTraktLibrary(kind:MediaKind,section:String,page:Int):Result<TraktClient.LibraryPage> = runCatching {
        val cfg=settings.value;val generation=authGeneration
        val result=withContext(Dispatchers.IO) {
            require(cfg.traktClientId.isNotBlank()) { "Configure ton Client ID dans les réglages." }
            val token=validTraktToken(cfg) ?: error("Connecte ton compte Trakt dans les réglages.")
            trakt.libraryPage(token,cfg.traktClientId,kind,section,page)
        }
        check(generation==authGeneration) { "Le compte a été déconnecté pendant la lecture." }
        result
    }.onFailure { if(it is CancellationException)throw it }

    suspend fun beginTraktDeviceAuth(): Result<TraktClient.DeviceCode> {
        val cfg = settings.value
        if (cfg.traktClientId.isBlank()) return Result.failure(IllegalStateException("Ajoute ton Client ID Trakt dans Réglages."))
        if (cfg.traktClientSecret.isBlank()) return Result.failure(IllegalStateException("Ajoute ton Client secret Trakt : il est requis par le Device Flow officiel."))
        return runCatching {
            authGeneration++
            val generation=authGeneration
            val code = trakt.requestDeviceCode(cfg.traktClientId.trim())
            check(generation == authGeneration) { "Connexion annulée." }
            credentials.edit()
                .putString("trakt_device_code", code.deviceCode)
                .putString("trakt_user_code", code.userCode)
                .putString("trakt_verification_url", code.verificationUrl)
                .putLong("trakt_device_expires", System.currentTimeMillis() + code.expiresIn * 1000L)
                .putLong("trakt_device_interval", code.interval.toLong())
                .apply()
            syncMessage.value = "Trakt : code ${code.userCode} · ${code.verificationUrl}"
            code
        }
    }

    suspend fun awaitTraktDeviceAuth(code: TraktClient.DeviceCode): Result<String> {
        val cfg = settings.value
        return runCatching {
            val generation=authGeneration
            var interval = code.interval.coerceAtLeast(1)
            val deadline = System.currentTimeMillis() + code.expiresIn * 1000L
            while (System.currentTimeMillis() < deadline) {
                delay(interval * 1000L)
                check(generation == authGeneration) { "Connexion annulée." }
                when (val poll = trakt.pollDeviceCode(code.deviceCode, cfg.traktClientId.trim(), cfg.traktClientSecret.trim())) {
                    is TraktClient.DevicePoll.Authorized -> {
                        check(generation == authGeneration) { "Connexion annulée." }
                        val profile = trakt.userProfile(poll.token.accessToken, cfg.traktClientId.trim())
                        check(generation == authGeneration) { "Connexion annulée." }
                        prepareTraktAccount(profile.username)
                        storeTraktToken(poll.token)
                        credentials.edit()
                            .putString("trakt_username", profile.username)
                            .putString("trakt_display_name", profile.displayName.orEmpty())
                            .remove("trakt_device_code").remove("trakt_device_expires").remove("trakt_user_code").remove("trakt_verification_url").remove("trakt_device_interval")
                            .apply()
                        val sync = syncFromTrakt(uploadLocal = true).getOrElse { "Compte connecté, synchro initiale à relancer : ${it.message}" }
                        val who = profile.displayName ?: profile.username
                        val message = "Trakt connecté${who?.let { " · $it" } ?: ""}. $sync"
                        syncMessage.value = message
                        return@runCatching message
                    }
                    TraktClient.DevicePoll.Pending -> Unit
                    TraktClient.DevicePoll.SlowDown -> interval += 2
                    TraktClient.DevicePoll.Denied -> error("Connexion Trakt refusée.")
                    TraktClient.DevicePoll.Expired -> error("Le code Trakt a expiré. Relance la connexion.")
                    TraktClient.DevicePoll.Invalid -> error("Code appareil Trakt invalide.")
                    TraktClient.DevicePoll.AlreadyUsed -> error("Ce code Trakt a déjà été utilisé. Relance la connexion.")
                    is TraktClient.DevicePoll.Failed -> error("Trakt HTTP ${poll.code}: ${poll.body}")
                }
            }
            error("Le code Trakt a expiré. Relance la connexion.")
        }
    }

    suspend fun syncFromTrakt(uploadLocal: Boolean = true): Result<String> = syncLock.withLock {
        if(isRecoveryOnly()) return@withLock Result.failure(IllegalStateException("Récupération en lecture seule."))
        if(checkInLock.isLocked)return@withLock Result.failure(IllegalStateException("Un check-in est en cours. Réessaie après sa fin."))
        runCatching {
            val cfg=settings.value
            require(cfg.traktClientId.isNotBlank()) { "Configure le Client ID Trakt." }
            val generation=authGeneration
            val token=validTraktToken(cfg) ?: error("Connecte ton compte Trakt.")
            check(generation==authGeneration) { "Compte déconnecté." }
            val snapshot=trakt.fetchSnapshot(token,cfg.traktClientId)
            check(generation == authGeneration) { "Compte déconnecté." }
            reconcileUncertain(snapshot)
            applyTraktSnapshot(snapshot)
            save(markTraktDirty=false)
            val sent=if(uploadLocal) flushOutbox(token,cfg.traktClientId,generation) else 0
            check(generation == authGeneration) { "Compte déconnecté." }
            credentials.edit().putLong("trakt_last_sync",System.currentTimeMillis()).putBoolean("trakt_dirty",outbox().isNotEmpty()).apply()
            "Trakt synchronisé : $sent modification(s) envoyée(s), ${outbox().size} en attente."
        }.onSuccess { syncMessage.value=it;logDiagnostic("info","Synchronisation Trakt terminée.") }
         .onFailure { if(it is CancellationException) throw it;syncMessage.value="Synchronisation interrompue : ${it.message}";logDiagnostic("warn",syncMessage.value.orEmpty()) }
    }

    suspend fun uploadToTrakt(): Result<String> = syncLock.withLock {
        runCatching {
            requireHealthy();check(!checkInLock.isLocked) { "Un check-in est en cours. Réessaie après sa fin." }
            val cfg=settings.value;val generation=authGeneration
            val token=validTraktToken(cfg) ?: error("Connecte ton compte Trakt.")
            val sent=flushOutbox(token,cfg.traktClientId,generation)
            "$sent modification(s) envoyée(s). La bibliothèque complète n’est pas renvoyée."
        }.onFailure { if(it is CancellationException) throw it;logDiagnostic("warn",it.message.orEmpty()) }
    }

    suspend fun finishTraktDeviceAuth(): Result<String> {
        val cfg = settings.value
        val generation=authGeneration
        val deviceCode = credentials.getString("trakt_device_code", "").orEmpty()
        if (deviceCode.isBlank()) return Result.failure(IllegalStateException("Aucun code appareil en attente."))
        if (System.currentTimeMillis() > credentials.getLong("trakt_device_expires", Long.MAX_VALUE)) return Result.failure(IllegalStateException("Le code appareil a expiré. Relance la connexion."))
        return runCatching {
            require(cfg.traktClientSecret.isNotBlank()) { "Ajoute le Client secret Trakt pour terminer l'autorisation." }
            val token = trakt.exchangeDeviceCode(deviceCode, cfg.traktClientId, cfg.traktClientSecret)
            check(generation==authGeneration) { "Connexion annulée." }
            val profile = trakt.userProfile(token.accessToken, cfg.traktClientId.trim())
            check(generation==authGeneration) { "Connexion annulée." }
            prepareTraktAccount(profile.username)
            storeTraktToken(token)
            credentials.edit().putString("trakt_username", profile.username).putString("trakt_display_name", profile.displayName.orEmpty()).remove("trakt_device_code").remove("trakt_device_expires").remove("trakt_user_code").remove("trakt_verification_url").remove("trakt_device_interval").apply()
            val message = "Compte Trakt connecté${profile?.displayName?.let { " · $it" } ?: profile?.username?.let { " · $it" } ?: ""}."
            syncMessage.value = message
            message
        }
    }

    fun cancelTraktAuth() { authGeneration++;credentials.edit().remove("trakt_device_code").apply() }

    fun disconnectTrakt() {
        cancelTraktAuth()
        credentials.edit().remove("trakt_access_token").remove("trakt_refresh_token").remove("trakt_token_expires").remove("trakt_device_code").remove("trakt_device_expires").remove("trakt_last_upload").remove("trakt_last_sync").remove("trakt_dirty").remove("trakt_username").remove("trakt_display_name").remove("trakt_user_code").remove("trakt_verification_url").remove("trakt_device_interval").apply()
        syncMessage.value = "Compte Trakt déconnecté."
    }

    @Synchronized
    private fun validTraktToken(cfg: AppSettings): String? {
        val access = credentials.getString("trakt_access_token", "").orEmpty()
        if (access.isBlank() || !isTraktConnected()) return null
        val expires = credentials.getLong("trakt_token_expires", Long.MAX_VALUE)
        if (System.currentTimeMillis() + 60_000L < expires) return access
        val refresh = credentials.getString("trakt_refresh_token", "").orEmpty()
        if (refresh.isBlank() || cfg.traktClientSecret.isBlank()) return access
        val generation=authGeneration
        val token = try { trakt.refreshToken(refresh, cfg.traktClientId, cfg.traktClientSecret) } catch(e: TraktClient.HttpFailure) {
            if(generation==authGeneration&&e.status in setOf(400,401)) disconnectTrakt()
            throw e
        }
        check(generation == authGeneration) { "Compte déconnecté pendant le renouvellement." }
        storeTraktToken(token)
        return token.accessToken
    }

    private fun storeTraktToken(token: TraktClient.Token) {
        credentials.edit().putString("trakt_app_ref_v31",prefs.getString("trakt_app_ref_v31",null)).putString("trakt_access_token", token.accessToken).putString("trakt_refresh_token", token.refreshToken).putLong("trakt_token_expires", token.expiresAtMillis).apply()
    }

    private fun applyTraktSnapshot(snapshot:TraktClient.SyncSnapshot) {
        val remote=remoteRecords(snapshot);val byRemote=remote.associateBy { it.key }
        val baseline=baselineRecords().associateBy { it.key };val pending=outbox()
        val merged=TrackingRules.records(media.toList(),episodes.toList()).associate { local ->
            local.key to (byRemote[local.key]?.let { TrackingRules.reconcile(local,it,baseline[local.key],pending) } ?: local)
        }
        media.indices.forEach { i ->
            val m=media[i];val r=merged["${m.kind.name.lowercase()}:${m.tmdbId}"]
            if(r!=null) media[i]=m.copy(watched=if(m.kind==MediaKind.MOVIE) r.watched else m.watched,plays=if(m.kind==MediaKind.MOVIE) r.plays else m.plays,lastWatchedAt=if(m.kind==MediaKind.MOVIE) r.lastWatchedAt else m.lastWatchedAt,collected=if(m.kind==MediaKind.MOVIE) r.collected else m.collected,watchlist=r.watchlist,rating=r.rating)
        }
        episodes.indices.forEach { i -> val e=episodes[i];val r=merged["episode:${e.tmdbId}"]
            if(r!=null) episodes[i]=e.copy(watched=r.watched,plays=r.plays,lastWatchedAt=r.lastWatchedAt,collected=r.collected,rating=r.rating)
        }
        media.filter { it.kind==MediaKind.SHOW }.forEach { updateShowProgress(it.id) }
        writeBaseline(remote)
    }

    private fun maxNullable(a: Long?, b: Long?): Long? = listOfNotNull(a, b).maxOrNull()

    fun importSeriesGuide(documents:List<String>):Result<SeriesGuideImportSummary> = prepareSeriesGuideImport(documents).mapCatching { review ->
        commitReviewedImport(review,false).getOrThrow();review.seriesGuideSummary!!
    }

    private fun seriesGuideListMediaIndex(member: SeriesGuideListMember): Int {
        val ext = member.externalId
        return when (member.type.lowercase()) {
            "tmdb-show" -> media.indexOfFirst { it.kind == MediaKind.SHOW && it.tmdbId?.toString() == ext }
            "show" -> media.indexOfFirst { it.kind == MediaKind.SHOW && it.tvdbId?.toString() == ext }
            "movie", "tmdb-movie" -> media.indexOfFirst { it.kind == MediaKind.MOVIE && it.tmdbId?.toString() == ext }
            "imdb-movie" -> media.indexOfFirst { it.kind == MediaKind.MOVIE && it.imdbId.equals(ext, true) }
            "episode" -> episodes.firstOrNull { it.tvdbId?.toString() == ext }?.showId?.let { showId -> media.indexOfFirst { it.id == showId } } ?: -1
            "season" -> episodes.firstOrNull { it.seasonTvdbId?.toString() == ext }?.showId?.let { showId -> media.indexOfFirst { it.id == showId } } ?: -1
            else -> -1
        }
    }


    fun gamingLibraryEpoch():Int = libraryGeneration

    fun updateGaming(transform:(GamingState)->GamingState):Result<Unit> = runCatching {
        requireHealthy();val before=gaming.value
        val next=GamingRules.validate(transform(before),media.filter { it.kind==MediaKind.GAME }.map { it.id }.toSet())
        try { gaming.value=next;save(false) } catch(e:Exception) { gaming.value=before;throw e }
    }
    fun createAchievementList(mediaId:String,title:String,platform:String):Result<String> = runCatching {
        val id=java.util.UUID.randomUUID().toString()
        val set=AchievementSet(id,mediaId,"manual",platform,"local-"+id,title,groups=listOf(AchievementGroup("base","Jeu de base","base")),source="manual")
        updateGaming { it.copy(sets=it.sets+set) }.getOrThrow();id
    }
    fun addPersonalAchievement(setId:String,title:String,description:String,grade:String="achievement",dlc:String=""):Result<Unit> = updateGaming { g->
        g.copy(sets=g.sets.map { s->if(s.id!=setId)s else {
            require(s.source=="manual") { "Une liste API/importée ne se modifie pas comme un catalogue officiel." }
            val group=if(dlc.isBlank())s.groups.first { it.kind=="base" } else s.groups.find { it.kind=="dlc"&&it.name==dlc }?:AchievementGroup(java.util.UUID.randomUUID().toString(),dlc,"dlc")
            s.copy(groups=if(s.groups.any { it.id==group.id })s.groups else s.groups+group,achievements=s.achievements+GameAchievement(java.util.UUID.randomUUID().toString(),title,description,group.id,grade))
        } })
    }
    fun setAchievementNote(setId:String,achievementId:String,note:String,done:Boolean,pinned:Boolean):Result<Unit> = updateGaming { g->
        require(g.sets.any { it.id==setId&&it.achievements.any { a->a.id==achievementId } }) { "Cette liste ou ce succès a changé. Rouvre la fiche avant de modifier la note." }
        g.copy(sets=g.sets.map { s->if(s.id!=setId)s else s.copy(achievements=s.achievements.map { a->if(a.id!=achievementId)a else a.copy(note=note,localDone=done,pinned=pinned) }) })
    }
    fun importAchievements(mediaId:String,raw:String):Result<Unit> = runCatching {
        requireHealthy();require(raw.length<=8*1024*1024) { "Fichier de trophées trop volumineux." }
        val root=JSONObject(raw.removePrefix("\uFEFF"));require(root.optString("format")=="franchiseguide-achievements"&&root.optInt("version")==1) { "Format FranchiseGuide Achievements v1 requis." }
        val source=GamingCodec.readSet(root.optJSONObject("set")?:error("Liste absente."))
        val incoming=GamingCodec.readAccountReference(root,source.provider,java.util.UUID.randomUUID().toString())
        var accounts=gaming.value.accounts
        val account=incoming?.let {
            val existing=accounts.find { it.provider==source.provider&&it.externalId.equals(incoming.externalId,source.provider=="psn") }
            if(existing==null)incoming.also { accounts=accounts+it }
            else existing.copy(username=existing.username?:incoming.username).also { updated->accounts=accounts.map { if(it.id==existing.id)updated else it } }
        }
        var fresh=source.copy(id=java.util.UUID.randomUUID().toString(),mediaId=mediaId,accountId=account?.id,source="file-import",lastSyncAt=null,syncError="")
        gaming.value.sets.find { GamingRules.identity(it)==GamingRules.identity(fresh) }?.let { fresh=GamingRules.merge(it,fresh) }
        val next=GamingState(accounts=accounts,sets=gaming.value.sets.filter { it.id!=fresh.id }+fresh)
        GamingRules.validate(next,media.filter { it.kind==MediaKind.GAME }.map { it.id }.toSet())
        createLocalBackup("Avant import de trophées").getOrThrow();updateGaming { next }.getOrThrow()
    }
    fun removeAchievementSet(setId:String):Result<Unit> = runCatching {
        createLocalBackup("Avant suppression de trophées").getOrThrow();updateGaming { it.copy(sets=it.sets.filter { s->s.id!=setId }) }.getOrThrow()
    }

    fun externalBackupStatus(now:Long=System.currentTimeMillis()):ExternalBackupStatus=BackupSafetyRules.status(externalBackup.value,now)
    fun backupSafetyProblem():String?=backupSafetyProblem
    @Synchronized private fun persistExternalBackup(value:ExternalBackupRecord) {BackupSafetyRules.validate(value);check(prefs.edit().putString("external_backup_v35",backupToJson(value).toString()).commit()){"Rappel de sauvegarde non enregistré."};externalBackup.value=value}
    fun noteExternalBackupProposed(kind:String,fileName:String):Result<String> = runCatching {requireHealthy();persistExternalBackup(BackupSafetyRules.proposed(externalBackup.value,kind,fileName,System.currentTimeMillis()));"Fichier proposé. Vérifie sa présence avant de le confirmer comme sauvegarde externe."}
    fun verifyExternalBackup():Result<String> = runCatching {requireHealthy();persistExternalBackup(BackupSafetyRules.verified(externalBackup.value,System.currentTimeMillis()));"Sauvegarde externe marquée comme vérifiée sur cet appareil."}
    fun snoozeExternalBackup(days:Int=7):Result<String> = runCatching {requireHealthy();persistExternalBackup(BackupSafetyRules.snooze(externalBackup.value,days,System.currentTimeMillis()));"Rappel reporté de $days jours, sans prétendre qu’un fichier a été vérifié."}
    fun todaySnapshot(limit:Int=12):TodaySnapshot=TodayRules.build(media.toList(),episodes.toList(),settings.value,outbox(),traktCheckIn.value,externalBackupStatus(),LocalDate.now(),limit)

    fun exportJson(): String {
        requireHealthy()
        val root = JSONObject()
        root.put("schema", 11)
        root.put("gaming", GamingCodec.write(gaming.value))
        root.put("app", "FranchiseGuide")
        root.put("createdAt", Instant.now().toString())
        root.put("media", JSONArray().apply { media.forEach { put(mediaToJson(it)) } })
        root.put("episodes", JSONArray().apply { episodes.forEach { put(episodeToJson(it)) } })
        root.put("suggestions", JSONArray().apply { suggestions.forEach { put(mediaToJson(it)) } })
        root.put("lists", JSONArray().apply { userLists.forEach { put(listToJson(it)) } })
        root.put("customFranchises", JSONArray().apply { customFranchises.forEach { put(franchiseToJson(it)) } })
        root.put("preferences", JSONObject().put("language", settings.value.language).put("region", settings.value.region).put("darkTheme", settings.value.darkTheme).put("includeSpecials", settings.value.includeSpecials).put("recentDays", settings.value.recentDays).put("upcomingDays", settings.value.upcomingDays).put("includeHiddenInCalendar", settings.value.includeHiddenInCalendar).put("notificationsEnabled", settings.value.notificationsEnabled).put("notificationHour", settings.value.notificationHour).put("detailJingleEnabled", settings.value.detailJingleEnabled).put("hideEpisodeSpoilers", settings.value.hideEpisodeSpoilers).put("showCastImages", settings.value.showCastImages).put("statisticsIncludeRewatches", settings.value.statisticsIncludeRewatches).put("traktAutoSync", settings.value.traktAutoSync).put("traktSyncIntervalHours", settings.value.traktSyncIntervalHours))
        root.optJSONObject("preferences")?.let { writeV8Preferences(it,settings.value) }
        return root.toString(2)
    }

    fun importJson(raw:String):Result<Unit> = preparePortableImport(raw).mapCatching { commitReviewedImport(it,true).getOrThrow();Unit }

    @Synchronized
    fun protectedState():Pair<Int,ImportSnapshot> { requireHealthy();return libraryGeneration to currentImportSnapshot() }
    @Synchronized
    fun checkProtectedState(expected:Pair<Int,ImportSnapshot>) { requireHealthy();check(expected.first==libraryGeneration && expected.second==currentImportSnapshot()) { "La bibliothèque ou ses réglages ont changé. Recommence la protection ou l’import." } }

    private fun currentImportSnapshot()=ImportSnapshot(media.toList(),episodes.toList(),suggestions.toList(),userLists.toList(),customFranchises.toList(),gaming.value,settings.value)

    @Synchronized
    fun preparePortableImport(raw:String,names:List<String> = listOf("Sauvegarde JSON")):Result<PreparedLibraryImport> = runCatching {
        requireHealthy();ImportReviewRules.checkDocuments(listOf(raw),true)
        val before=currentImportSnapshot();val generation=libraryGeneration;val root=JSONObject(raw.removePrefix("\uFEFF"))
        validateStoredRoot(root)
        val loadedMedia=jsonObjectArray(root,"media").map(::jsonToMedia);val loadedEpisodes=jsonObjectArray(root,"episodes").map(::jsonToEpisode)
        val loadedGaming=GamingCodec.readPortableImport(root.optJSONObject("gaming"));GamingRules.validate(loadedGaming,loadedMedia.filter{it.kind==MediaKind.GAME}.map{it.id}.toSet())
        val showIds=loadedMedia.filter{it.kind==MediaKind.SHOW}.map{it.id}.toSet();require(loadedEpisodes.all{it.showId in showIds}) { "Épisodes sans série dans le fichier." }
        val candidate=ImportSnapshot(loadedMedia,loadedEpisodes,jsonObjectArray(root,"suggestions").map(::jsonToMedia),jsonObjectArray(root,"lists").map(::jsonToList),jsonObjectArray(root,"customFranchises").map(::jsonToFranchise),loadedGaming,importedSettings(before.settings,root.optJSONObject("preferences")))
        PreparedLibraryImport("portable",names.toList(),before,candidate,generation)
    }

    @Synchronized
    fun prepareSeriesGuideImport(documents:List<String>,names:List<String> = documents.indices.map { "Export SeriesGuide ${it+1}" }):Result<PreparedLibraryImport> = runCatching {
        requireHealthy();ImportReviewRules.checkDocuments(documents,false)
        val before=currentImportSnapshot();val generation=libraryGeneration;val bundle=SeriesGuideImporter.parse(documents)
        val ms=before.media.toMutableList();val es=before.episodes.toMutableList();val ls=before.lists.toMutableList();val mapping=mutableMapOf<String,String>()
        for(item in bundle.media){
            val idx=ms.indexOfFirst{sameIdentity(it,item)}
            val fresh=attachFranchise(item)
            if(idx>=0)require(!ReliabilityRules.mediaConflict(ms[idx],fresh)) { "Identifiants média contradictoires : import annulé." }
            val merged=if(idx<0)fresh else mergeMedia(ms[idx],fresh).copy(contentLanguage=ms[idx].contentLanguage?:fresh.contentLanguage,rating=ms[idx].rating?:fresh.rating,userNote=ms[idx].userNote?:fresh.userNote,runtimeMinutes=ms[idx].runtimeMinutes?:fresh.runtimeMinutes)
            if(idx<0)ms.add(merged) else ms[idx]=merged;mapping[item.id]=merged.id
        }
        for(item in bundle.episodes){val fresh=item.copy(showId=mapping[item.showId]?:item.showId)
            val byId=es.filter { it.showId==fresh.showId&&((fresh.tmdbId!=null&&it.tmdbId==fresh.tmdbId)||(fresh.tvdbId!=null&&it.tvdbId==fresh.tvdbId)) }
            require(byId.size<=1&&byId.none{ReliabilityRules.episodeConflict(it,fresh)}) { "Identifiants épisode contradictoires : import annulé." }
            val idx=es.indexOfFirst{TrackingRules.sameEpisode(it,fresh)}
            if(idx<0)es.add(fresh) else es[idx]=mergeEpisode(es[idx],fresh).copy(runtimeMinutes=es[idx].runtimeMinutes)
        }
        var linked=0;var skipped=0
        for(imported in bundle.lists){
            val old=ls.firstOrNull{it.id==imported.list.id||it.name.equals(imported.list.name,true)};val list=old?:imported.list
            if(old==null)ls.add(list)
            for(member in imported.members){
                val ext=member.externalId;val m=when(member.type.lowercase()){
                    "tmdb-show"->ms.firstOrNull{it.kind==MediaKind.SHOW&&it.tmdbId?.toString()==ext}
                    "show"->ms.firstOrNull{it.kind==MediaKind.SHOW&&it.tvdbId?.toString()==ext}
                    "movie","tmdb-movie"->ms.firstOrNull{it.kind==MediaKind.MOVIE&&it.tmdbId?.toString()==ext}
                    "imdb-movie"->ms.firstOrNull{it.kind==MediaKind.MOVIE&&it.imdbId.equals(ext,true)}
                    "episode","season"->es.firstOrNull{if(member.type.lowercase()=="episode")it.tvdbId?.toString()==ext else it.seasonTvdbId?.toString()==ext}?.let{e->ms.firstOrNull{it.id==e.showId}}
                    else->null
                }
                if(m==null)skipped++ else{val idx=ms.indexOfFirst{it.id==m.id};ms[idx]=m.copy(listIds=m.listIds+list.id);linked++}
            }
        }
        val buckets=es.groupBy{it.showId};val normalized=ms.map { m->if(m.kind!=MediaKind.SHOW)m else {
            val episodes=buckets[m.id].orEmpty();val watchable=episodes.filter{!it.skipped&&(before.settings.includeSpecials||it.season!=0)&&(it.airDate==null||!it.airDate.isAfter(LocalDate.now()))}
            m.copy(watched=watchable.isNotEmpty()&&watchable.all{it.watched},lastWatchedAt=maxNullable(m.lastWatchedAt,episodes.maxOfOrNull{it.lastWatchedAt?:0L}?.takeIf{it>0}))
        } }
        val summary=SeriesGuideImportSummary(bundle.media.count{it.kind==MediaKind.SHOW},bundle.media.count{it.kind==MediaKind.MOVIE},bundle.episodes.size,bundle.lists.size,linked,skipped,bundle.filesRead)
        PreparedLibraryImport("seriesguide",names.toList(),before,before.copy(media=normalized,episodes=es.toList(),lists=ls.toList()),generation,summary)
    }

    fun importComparison(review:PreparedLibraryImport)=ImportReviewRules.compare(review.baseline,review.candidate)
    private fun applyImportSnapshot(x:ImportSnapshot) {
        media.clear();media.addAll(x.media);episodes.clear();episodes.addAll(x.episodes);suggestions.clear();suggestions.addAll(x.suggestions)
        userLists.clear();userLists.addAll(x.lists);customFranchises.clear();customFranchises.addAll(x.franchises);gaming.value=x.gaming;settings.value=x.settings
    }
    @Synchronized
    fun commitReviewedImport(review:PreparedLibraryImport,importPreferences:Boolean=false):Result<String> {
        if(!syncLock.tryLock())return Result.failure(IllegalStateException("Synchronisation en cours : l’import n’a pas été appliqué."))
        try{return runCatching {
            requireHealthy();ImportReviewRules.guard(review,currentImportSnapshot(),libraryGeneration)
            val before=currentImportSnapshot();val candidate=review.candidate.copy(settings=if(importPreferences&&review.mode=="portable")review.candidate.settings else before.settings)
            createLocalBackup("Avant import vérifié").getOrThrow();libraryGeneration++
            try{applyImportSnapshot(candidate);save(false,outbox().map{it.copy(heldReason="Import ou restauration vérifiée")},true)}
            catch(e:Exception){applyImportSnapshot(before);lastTracked=TrackingRules.records(before.media,before.episodes);throw e}
            "Import vérifié enregistré. Les anciennes opérations Trakt sont suspendues, sans suppression distante."
        }}finally{syncLock.unlock()}
    }

    private fun importedSettings(base:AppSettings,pref:JSONObject?):AppSettings {
        if(pref==null)return base
        return base.copy(
                        language = pref.optString("language", base.language),
                        region = pref.optString("region", base.region),
                        darkTheme = pref.optBoolean("darkTheme", base.darkTheme),
                        includeSpecials = pref.optBoolean("includeSpecials", base.includeSpecials),
                        recentDays = pref.optInt("recentDays", base.recentDays),
                        upcomingDays = pref.optInt("upcomingDays", base.upcomingDays),
                        includeHiddenInCalendar = pref.optBoolean("includeHiddenInCalendar", base.includeHiddenInCalendar),
                        notificationsEnabled = pref.optBoolean("notificationsEnabled", base.notificationsEnabled),
                        notificationHour = pref.optInt("notificationHour", base.notificationHour).coerceIn(0, 23),
                        detailJingleEnabled = pref.optBoolean("detailJingleEnabled", base.detailJingleEnabled),
                        hideEpisodeSpoilers = pref.optBoolean("hideEpisodeSpoilers", base.hideEpisodeSpoilers),
                        showCastImages = pref.optBoolean("showCastImages", base.showCastImages),
                        statisticsIncludeRewatches = pref.optBoolean("statisticsIncludeRewatches", base.statisticsIncludeRewatches),
                        traktAutoSync = pref.optBoolean("traktAutoSync", base.traktAutoSync),
                        traktSyncIntervalHours = pref.optInt("traktSyncIntervalHours", base.traktSyncIntervalHours).coerceIn(1, 168),
                        statusFilters = readStatusFilters(pref),librarySort = readLibrarySort(pref),
                        autoBackupEnabled= pref.optBoolean("autoBackupEnabled",true),autoBackupMinutes=pref.optInt("autoBackupMinutes",30).coerceIn(1,1440),
                        catalogAutoRefresh=pref.optBoolean("catalogAutoRefresh",true),detailJingleVolume=pref.optInt("detailJingleVolume",42).coerceIn(0,100),
                        discoverYearMin=pref.optInt("discoverYearMin",0),discoverYearMax=pref.optInt("discoverYearMax",0),discoverLanguage=pref.optString("discoverLanguage", "")
        )
    }

    private fun upsertMedia(prepared: MediaItem): MediaItem {
        val index = media.indexOfFirst { sameIdentity(it, prepared) }
        return if (index >= 0) {
            val merged = mergeMedia(media[index], prepared.copy(id = media[index].id))
            media[index] = merged; merged
        } else { media.add(prepared); prepared }
    }

    private fun upsertSuggestion(candidate: MediaItem) {
        val prepared = candidate.copy(added = false, isSuggestion = true)
        val idx = suggestions.indexOfFirst { sameIdentity(it, prepared) }
        if (idx >= 0) suggestions[idx] = mergeMedia(suggestions[idx], prepared).copy(added = false, isSuggestion = true) else suggestions.add(prepared)
    }

    private fun upsertEpisode(fresh: Episode) {
        val idx = episodes.indexOfFirst { if(fresh.id.startsWith("tvmaze-episode-"))PublicTvDetailRules.sameEpisode(it,fresh) else TrackingRules.sameEpisode(it,fresh) }
        if (idx >= 0) episodes[idx] = mergeEpisode(episodes[idx], fresh.copy(id = episodes[idx].id)) else episodes.add(fresh)
    }

    private fun mergeEpisode(old:Episode,fresh:Episode):Episode {
        val watched=old.watched||fresh.watched
        val dates=ReliabilityRules.unionHistory(old,fresh)
        return fresh.copy(id=old.id,watched=watched,
            tmdbId=fresh.tmdbId ?: old.tmdbId,tvdbId=fresh.tvdbId ?: old.tvdbId,
            seasonTmdbId=fresh.seasonTmdbId ?: old.seasonTmdbId,seasonTvdbId=fresh.seasonTvdbId ?: old.seasonTvdbId,
            airDate=fresh.airDate ?: old.airDate,stillUrl=fresh.stillUrl ?: old.stillUrl,overview=fresh.overview.ifBlank{old.overview},
            title=if(fresh.numberUnknown&&!old.numberUnknown)old.title else fresh.title.ifBlank{old.title},plays=if(watched)maxOf(1,old.plays,fresh.plays,dates.size) else 0,
            skipped=!watched&&(old.skipped||fresh.skipped),collected=old.collected||fresh.collected,
            rating=old.rating?:fresh.rating,userNote=old.userNote?:fresh.userNote,lastWatchedAt=maxNullable(old.lastWatchedAt,fresh.lastWatchedAt),
            watchHistory=dates,
            season=if(fresh.seasonUnknown&&!old.seasonUnknown)old.season else fresh.season,
            number=if(fresh.numberUnknown&&!old.numberUnknown)old.number else fresh.number,
            seasonUnknown=old.seasonUnknown&&fresh.seasonUnknown,numberUnknown=old.numberUnknown&&fresh.numberUnknown)
    }

    private fun updateMedia(id: String, transform: (MediaItem) -> MediaItem) {
        val index = media.indexOfFirst { it.id == id }
        if (index >= 0) { val old=media[index];media[index] = transform(old);try { save() } catch(e:Exception) { media[index]=old;throw e } }
    }

    private fun attachFranchise(item: MediaItem): MediaItem {
        if(item.franchiseManual)return item
        if (item.franchiseId != null && allFranchiseDefinitions().any { it.id == item.franchiseId }) return item
        val inferred = FranchiseEngine.inferFranchise(item, allFranchiseDefinitions()) ?: return item
        return item.copy(franchiseId = inferred.id, franchiseName = inferred.name)
    }

    private fun sameIdentity(a: MediaItem, b: MediaItem): Boolean {
        if (a.kind != b.kind) return false
        if (a.id == b.id) return true
        if (a.tmdbId != null && b.tmdbId != null) return a.tmdbId == b.tmdbId
        if (!a.imdbId.isNullOrBlank() && !b.imdbId.isNullOrBlank() && a.imdbId.equals(b.imdbId, true)) return true
        if (a.tvdbId != null && b.tvdbId != null) return a.tvdbId == b.tvdbId
        if (!a.sourceId.isNullOrBlank() && !b.sourceId.isNullOrBlank() && a.source == b.source) return a.sourceId == b.sourceId
        return false
    }

    private fun mergeMedia(old: MediaItem, fresh: MediaItem): MediaItem = MediaIdentityCodec.preserveAliases(old,fresh.copy(
        id = old.id,
        steam = fresh.steam ?: old.steam,
        trackingStatus=old.trackingStatus?:fresh.trackingStatus,playtimeMinutes=old.playtimeMinutes?:fresh.playtimeMinutes,personalPlatform=old.personalPlatform?:fresh.personalPlatform,personalFormat=old.personalFormat?:fresh.personalFormat,
        added = old.added || fresh.added,
        watched = old.watched || fresh.watched,
        favorite = old.favorite || fresh.favorite,
        hidden = old.hidden || fresh.hidden,
        collected = old.collected || fresh.collected,
        watchlist = old.watchlist || fresh.watchlist,
        rating = fresh.rating ?: old.rating,
        catalogRating = fresh.catalogRating ?: old.catalogRating,
        catalogRatingVotes = fresh.catalogRatingVotes ?: old.catalogRatingVotes,
        ratings = (fresh.ratings + old.ratings).distinctBy { it.source.lowercase() },
        statusRaw = fresh.statusRaw ?: old.statusRaw,
        lastAirDate = fresh.lastAirDate ?: old.lastAirDate,
        contentRating = fresh.contentRating ?: old.contentRating,
        tagline = fresh.tagline ?: old.tagline,
        homepage = fresh.homepage ?: old.homepage,
        country = fresh.country ?: old.country,
        releaseSchedule = fresh.releaseSchedule ?: old.releaseSchedule,
        originalTitle = fresh.originalTitle ?: old.originalTitle,
        seasonCount = fresh.seasonCount ?: old.seasonCount,
        episodeCount = fresh.episodeCount ?: old.episodeCount,
        productionCompanies = old.productionCompanies + fresh.productionCompanies,
        listIds = old.listIds + fresh.listIds,
        progressCurrent = if(old.trackingStatus!=null)old.progressCurrent else maxOf(old.progressCurrent, fresh.progressCurrent),
        progressTotal = if(old.trackingStatus!=null)old.progressTotal else maxOf(old.progressTotal, fresh.progressTotal),
        progressUnit = if(old.trackingStatus!=null)old.progressUnit else fresh.progressUnit.ifBlank { old.progressUnit },
        started = old.started || fresh.started,
        plays = maxOf(old.plays, fresh.plays),
        userNote = fresh.userNote ?: old.userNote,
        creator = fresh.creator ?: old.creator,
        publisher = fresh.publisher ?: old.publisher,
        platforms = old.platforms + fresh.platforms,
        genres = old.genres + fresh.genres,
        runtimeMinutes = fresh.runtimeMinutes ?: old.runtimeMinutes,
        externalUrl = fresh.externalUrl ?: old.externalUrl,
        trailerUrl = fresh.trailerUrl ?: old.trailerUrl,
        providers = old.providers + fresh.providers,
        people = (old.people + fresh.people).distinct().take(24),
        credits = (fresh.credits + old.credits).distinctBy { it.id to it.role },
        notify = old.notify,
        addedAt = minOf(old.addedAt, fresh.addedAt),
        lastWatchedAt = maxNullable(old.lastWatchedAt, fresh.lastWatchedAt),
        franchiseId = if(old.franchiseManual)old.franchiseId else fresh.franchiseId ?: old.franchiseId,
        franchiseName = if(old.franchiseManual)old.franchiseName else fresh.franchiseName ?: old.franchiseName,
        catalogComplete = fresh.catalogComplete || old.catalogComplete,
        catalogUpdatedAt = maxNullable(old.catalogUpdatedAt, fresh.catalogUpdatedAt),
        watchHistory = (TrackingRules.knownHistory(old)+TrackingRules.knownHistory(fresh)).distinct().sorted(),
        franchiseManual = old.franchiseManual,
        seasons = fresh.seasons.ifEmpty { old.seasons }
    ))

    private fun validateStoredRoot(root: JSONObject) {
        ReleaseRules.schema(if (root.has("schema")) root.opt("schema") else null)
        require(!root.has("gaming")||root.optJSONObject("gaming")!=null) { "Module trophées invalide." }
        GamingCodec.read(root.optJSONObject("gaming"))
        require(root.optJSONArray("media") != null) { "La bibliothèque media doit être un tableau." }
        listOf("episodes", "suggestions", "lists", "customFranchises").forEach { key ->
            require(!root.has(key) || root.optJSONArray(key) != null) { "Tableau $key invalide." }
        }
        require(!root.has("preferences") || root.optJSONObject("preferences") != null) { "Préférences invalides." }
        listOf("media","episodes","suggestions","lists","customFranchises").forEach { key ->
            val rows=root.optJSONArray(key)?:JSONArray()
            require(rows.length()<=if(key=="episodes")200_000 else 50_000) { "Bibliothèque trop volumineuse." }
            val ids=mutableSetOf<String>()
            for(i in 0 until rows.length()) {
                val row=rows.optJSONObject(i)?:error("Entrée $key[$i] invalide : aucun élément ne sera ignoré silencieusement.")
                val id=row.opt("id")
                require(id is String && id.isNotBlank() && ids.add(id)) { "Identifiant $key absent ou dupliqué." }
                if(key=="media"||key=="suggestions") {
                    require(row.opt("title") is String) { "Titre invalide." }
                    require(row.optString("kind").uppercase() in setOf("SHOW","MOVIE","GAME","BOOK")) { "Type de média inconnu : original conservé." }
                }
            }
        }
    }

    private fun load(): Boolean {
        val raw=prefs.getString("state",null) ?: return false
        return runCatching {
            val root=JSONObject(raw)
            validateStoredRoot(root)
            val loadedMedia=jsonObjectArray(root,"media").map(::jsonToMedia)
            val loadedEpisodes=jsonObjectArray(root,"episodes").map(::jsonToEpisode)
            val loadedSuggestions=jsonObjectArray(root,"suggestions").map(::jsonToMedia)
            val loadedLists=jsonObjectArray(root,"lists").map(::jsonToList)
            val loadedFranchises=jsonObjectArray(root,"customFranchises").map(::jsonToFranchise)
            val loadedGaming=GamingCodec.read(root.optJSONObject("gaming"))
            GamingRules.validate(loadedGaming,loadedMedia.filter { it.kind==MediaKind.GAME }.map { it.id }.toSet())
            require(loadedMedia.map { it.id }.distinct().size==loadedMedia.size) { "Identifiants dupliqués." }
            require(loadedEpisodes.all { e->loadedMedia.any { it.id==e.showId && it.kind==MediaKind.SHOW } }) { "Épisodes sans série." }
            gaming.value=loadedGaming
            media.addAll(loadedMedia);episodes.addAll(loadedEpisodes);suggestions.addAll(loadedSuggestions);userLists.addAll(loadedLists);customFranchises.addAll(loadedFranchises)
            media.indices.forEach { i->media[i]=attachFranchise(media[i]) }
            suggestions.indices.forEach { i->suggestions[i]=attachFranchise(suggestions[i]) }
            true
        }.getOrElse {
            originalRecoveryState = raw
            media.clear(); episodes.clear(); suggestions.clear(); userLists.clear(); customFranchises.clear()
            recoveryMessage.value = "Bibliothèque illisible ou version non prise en charge. L’original est intact et en lecture seule. Exporte la copie brute puis ouvre-la avec une version compatible."
            syncMessage.value = recoveryMessage.value
            // No SharedPreferences commit, migration, backup rotation or diagnostic write here.
            true
        }
    }

    @Synchronized
    private fun save(markTraktDirty:Boolean=true,pendingOverride:List<PendingChange>?=null,resetBaseline:Boolean=false) {
        requireHealthy()
        val current=TrackingRules.records(media.toList(),episodes.toList())
        val queue=(pendingOverride?:outbox()).toMutableList()
        if(markTraktDirty&&isTraktConnected())for(op in TrackingRules.changes(lastTracked,current)) {
            if(op.field!="history")queue.removeAll { it.key==op.key&&it.field==op.field&&!it.uncertain&&it.heldReason==null }
            queue.add(op)
        }
        // One SharedPreferences transaction prevents a saved library without its outbox.
        val json=exportJson()
        val publicPrefs=JSONObject(json).getJSONObject("preferences").toString()
        val edit=prefs.edit().putString("state",json).putString("public_preferences_v18",publicPrefs).putString("trakt_outbox_v8",outboxJson(queue))
        if(resetBaseline)edit.putBoolean("trakt_reset_baseline_v9",true)
        check(edit.commit()) { "Enregistrement local impossible : exporte une sauvegarde." }
        credentials.edit().putBoolean("trakt_dirty",queue.isNotEmpty()).apply()
        lastTracked=current
        if(!savingBackup&&settings.value.autoBackupEnabled&&System.currentTimeMillis()-prefs.getLong("last_backup",0)>=settings.value.autoBackupMinutes.coerceIn(1,1440)*60000L)createLocalBackup("Automatique")
    }

    private fun checkInToJson(v:TraktCheckInSession)=JSONObject().apply {put("version",v.version);put("id",v.id);put("account",v.account);put("appRef",v.appRef?:JSONObject.NULL);put("kind",v.kind);put("localId",v.localId);put("parentId",v.parentId?:JSONObject.NULL);put("tmdbId",v.tmdbId);put("title",v.title);put("code",v.code?:JSONObject.NULL);put("runtimeMinutes",v.runtimeMinutes?:JSONObject.NULL);put("identity",v.identity);put("startedAt",v.startedAt);put("remoteStartedAt",v.remoteStartedAt?:JSONObject.NULL);put("expectedEndAt",v.expectedEndAt?:JSONObject.NULL);put("status",v.status);put("finishAt",v.finishAt?:JSONObject.NULL);put("lastError",v.lastError?:JSONObject.NULL);put("historyApplied",v.historyApplied)}
    // Android's JSONObject.NULL is coerced to the text "null" by optString.
    // Keep absent optional values null so persisted movie sessions remain valid.
    private fun jsonToCheckIn(o:JSONObject)=TraktCheckInRules.validate(TraktCheckInSession(version=o.getInt("version"),id=o.getString("id"),account=o.getString("account"),appRef=if(o.isNull("appRef"))null else o.getString("appRef").takeIf{it.isNotBlank()},kind=o.getString("kind"),localId=o.getString("localId"),parentId=if(o.isNull("parentId"))null else o.getString("parentId").takeIf{it.isNotBlank()},tmdbId=o.getInt("tmdbId"),title=o.getString("title"),code=if(o.isNull("code"))null else o.getString("code").takeIf{it.isNotBlank()},runtimeMinutes=o.optInt("runtimeMinutes",0).takeIf{it>0},identity=o.getString("identity"),startedAt=o.getLong("startedAt"),remoteStartedAt=o.optLong("remoteStartedAt",0).takeIf{it>0},expectedEndAt=o.optLong("expectedEndAt",0).takeIf{it>0},status=o.getString("status"),finishAt=o.optLong("finishAt",0).takeIf{it>0},lastError=if(o.isNull("lastError"))null else o.getString("lastError").takeIf{it.isNotBlank()},historyApplied=o.optBoolean("historyApplied")))
    private fun loadTraktCheckIn():TraktCheckInSession? {val raw=prefs.getString("trakt_checkin_v35",null)?:return null;return runCatching{require(raw.length<=65536);jsonToCheckIn(JSONObject(raw))}.getOrElse{checkInRecoveryRaw=raw;checkInLoadProblem="Session de check-in locale illisible : exporte-la ou oublie-la explicitement. Aucun suivi local appliqué.";null}}
    private fun backupToJson(v:ExternalBackupRecord)=JSONObject().apply{put("version",v.version);put("proposedAt",v.proposedAt?:JSONObject.NULL);put("proposedKind",v.proposedKind?:JSONObject.NULL);put("fileName",v.fileName?:JSONObject.NULL);put("verifiedAt",v.verifiedAt?:JSONObject.NULL);put("snoozedUntil",v.snoozedUntil?:JSONObject.NULL)}
    private fun loadExternalBackup():ExternalBackupRecord {val raw=prefs.getString("external_backup_v35",null)?:return ExternalBackupRecord();return runCatching{require(raw.length<=16384);val o=JSONObject(raw);BackupSafetyRules.validate(ExternalBackupRecord(version=o.optInt("version",1),proposedAt=o.optLong("proposedAt",0).takeIf{it>0},proposedKind=o.optString("proposedKind").takeIf{it.isNotBlank()},fileName=o.optString("fileName").takeIf{it.isNotBlank()},verifiedAt=o.optLong("verifiedAt",0).takeIf{it>0},snoozedUntil=o.optLong("snoozedUntil",0).takeIf{it>0}))}.getOrElse{backupSafetyProblem="Rappel de sauvegarde illisible ; aucune vérification externe supposée.";ExternalBackupRecord()}}

    private fun loadSettings():AppSettings {
        var raw:String?=null
        return try {
            val ref=prefs.getString("settings_ref_v31",null)
            if(ref!=null){check(SettingsRules.validReference(ref)){"Référence des réglages invalide."};raw=credentials.getString("settings_slot_"+ref,null);check(raw!=null){"Accès sélectionnés absents. Aucun autre accès n’est choisi automatiquement."}}
            else raw=credentials.getString("settings",null)?:prefs.getString("settings",null)
            val base=raw?.let{jsonToSettings(JSONObject(it))}?:AppSettings()
            importedSettings(base,prefs.getString("public_preferences_v18",null)?.let(::JSONObject))
        }catch(e:Exception){settingsLoadProblem="Réglages locaux illisibles : lecture seule. "+e.message;settingsRecoveryRaw=raw?:prefs.getString("state",null);AppSettings()}
    }

    private fun mediaToJson(m: MediaItem) = JSONObject().apply {
        MediaIdentityCodec.writeInto(this,m);PersonalProgressCodec.writeInto(this,m)
        put("contentLanguage",m.contentLanguage?:JSONObject.NULL);put("steam",GamingCodec.writeSteam(m.steam))
        put("watchHistory",JSONArray(TrackingRules.knownHistory(m)));put("franchiseManual",m.franchiseManual)
        put("id", m.id); put("tmdbId", m.tmdbId ?: JSONObject.NULL); put("tvdbId", m.tvdbId ?: JSONObject.NULL); put("imdbId", m.imdbId ?: JSONObject.NULL)
        put("kind", m.kind.name); put("title", m.title); put("subtitle", m.subtitle); put("year", m.year ?: JSONObject.NULL); put("overview", m.overview)
        put("posterUrl", m.posterUrl ?: JSONObject.NULL); put("backdropUrl", m.backdropUrl ?: JSONObject.NULL); put("added", m.added); put("watched", m.watched); put("favorite", m.favorite); put("hidden", m.hidden); put("collected", m.collected); put("watchlist", m.watchlist)
        put("rating", m.rating ?: JSONObject.NULL); put("catalogRating", m.catalogRating ?: JSONObject.NULL); put("catalogRatingVotes", m.catalogRatingVotes ?: JSONObject.NULL); put("ratings", JSONArray().apply { m.ratings.forEach { put(ratingToJson(it)) } }); put("status", m.status.name); put("statusRaw", m.statusRaw ?: JSONObject.NULL); put("nextAirDate", m.nextAirDate?.toString() ?: JSONObject.NULL); put("lastAirDate", m.lastAirDate?.toString() ?: JSONObject.NULL); put("network", m.network ?: JSONObject.NULL); put("contentRating", m.contentRating ?: JSONObject.NULL); put("tagline", m.tagline ?: JSONObject.NULL); put("homepage", m.homepage ?: JSONObject.NULL); put("country", m.country ?: JSONObject.NULL); put("releaseSchedule", m.releaseSchedule ?: JSONObject.NULL); put("originalTitle", m.originalTitle ?: JSONObject.NULL); put("seasonCount", m.seasonCount ?: JSONObject.NULL); put("episodeCount", m.episodeCount ?: JSONObject.NULL); put("productionCompanies", JSONArray(m.productionCompanies.toList())); put("franchiseId", m.franchiseId ?: JSONObject.NULL); put("franchiseName", m.franchiseName ?: JSONObject.NULL)
        put("isSuggestion", m.isSuggestion); put("listIds", JSONArray(m.listIds.toList())); put("source", m.source); put("sourceId", m.sourceId ?: JSONObject.NULL); put("catalogComplete", m.catalogComplete); put("catalogUpdatedAt", m.catalogUpdatedAt ?: JSONObject.NULL); put("creator", m.creator ?: JSONObject.NULL); put("publisher", m.publisher ?: JSONObject.NULL)
        put("platforms", JSONArray(m.platforms.toList())); put("progressCurrent", m.progressCurrent); put("progressTotal", m.progressTotal); put("progressUnit", m.progressUnit); put("started", m.started); put("plays", m.plays); put("userNote", m.userNote ?: JSONObject.NULL)
        put("runtimeMinutes", m.runtimeMinutes ?: JSONObject.NULL); put("genres", JSONArray(m.genres.toList())); put("releaseDate", m.releaseDate?.toString() ?: JSONObject.NULL); put("originalLanguage", m.originalLanguage ?: JSONObject.NULL); put("externalUrl", m.externalUrl ?: JSONObject.NULL); put("trailerUrl", m.trailerUrl ?: JSONObject.NULL); put("providers", JSONArray(m.providers.toList())); put("people", JSONArray(m.people)); put("credits", JSONArray().apply { m.credits.forEach { put(personToJson(it)) } }); put("notify", m.notify); put("addedAt", m.addedAt); put("lastWatchedAt", m.lastWatchedAt ?: JSONObject.NULL); put("seasons",JSONArray().apply { m.seasons.forEach { put(seasonToJson(it)) } })
    }

    private fun jsonToMedia(o: JSONObject) = PersonalProgressCodec.readInto(o,MediaIdentityCodec.readInto(o,MediaItem(
        steam=GamingCodec.readSteam(o),contentLanguage=ReleaseRules.contentLanguage(o.optNullableString("contentLanguage")),
        watchHistory=jsonArray(o,"watchHistory").mapNotNull { (it as? Number)?.toLong() }.filter { it>0 }.distinct().sorted(),franchiseManual=o.optBoolean("franchiseManual",false),
        id = o.getString("id"), tmdbId = o.optIntOrNull("tmdbId"), tvdbId = o.optIntOrNull("tvdbId"), imdbId = o.optNullableString("imdbId"),
        kind = runCatching { MediaKind.valueOf(o.getString("kind").uppercase()) }.getOrDefault(MediaKind.MOVIE), title = o.getString("title"), subtitle = o.optString("subtitle"), year = o.optIntOrNull("year"), overview = o.optString("overview"),
        posterUrl = o.optNullableString("posterUrl"), backdropUrl = o.optNullableString("backdropUrl"), added = o.optBoolean("added", true), watched = o.optBoolean("watched"), favorite = o.optBoolean("favorite"), hidden = o.optBoolean("hidden"), collected = o.optBoolean("collected"), watchlist = o.optBoolean("watchlist"),
        rating = o.optIntOrNull("rating"), catalogRating = if (!o.has("catalogRating") || o.isNull("catalogRating")) null else o.optDouble("catalogRating", 0.0).takeIf { it >= 0.0 }, catalogRatingVotes = o.optIntOrNull("catalogRatingVotes"), ratings = jsonObjectArray(o, "ratings").map(::jsonToRating),
        status = TrackingRules.normalizedStatus(o.optNullableString("statusRaw") ?: o.optString("status", "UNKNOWN")), statusRaw = o.optNullableString("statusRaw"), nextAirDate = o.optDate("nextAirDate"), lastAirDate = o.optDate("lastAirDate"), network = o.optNullableString("network"), contentRating = o.optNullableString("contentRating"), tagline = o.optNullableString("tagline"), homepage = o.optNullableString("homepage"), country = o.optNullableString("country"), releaseSchedule = o.optNullableString("releaseSchedule"), originalTitle = o.optNullableString("originalTitle"), seasonCount = o.optIntOrNull("seasonCount"), episodeCount = o.optIntOrNull("episodeCount"), productionCompanies = jsonArray(o, "productionCompanies").map { it.toString() }.toSet(),
        franchiseId = o.optNullableString("franchiseId") ?: o.optNullableString("franchise"), franchiseName = o.optNullableString("franchiseName"), isSuggestion = o.optBoolean("isSuggestion"), listIds = jsonArray(o, "listIds").map { it.toString() }.toSet(),
        source = o.optString("source", "local"), sourceId = o.optNullableString("sourceId"), catalogComplete = o.optBoolean("catalogComplete", false), catalogUpdatedAt = o.optLongOrNull("catalogUpdatedAt"), creator = o.optNullableString("creator"), publisher = o.optNullableString("publisher"), platforms = jsonArray(o, "platforms").map { it.toString() }.toSet(), progressCurrent = o.optInt("progressCurrent", 0), progressTotal = o.optInt("progressTotal", 0), progressUnit = o.optString("progressUnit"), started = o.optBoolean("started"), plays = o.optInt("plays", if (o.optBoolean("watched")) 1 else 0), userNote = o.optNullableString("userNote"),
        runtimeMinutes = o.optIntOrNull("runtimeMinutes"), genres = jsonArray(o, "genres").map { it.toString() }.toSet(), releaseDate = o.optDate("releaseDate"), originalLanguage = o.optNullableString("originalLanguage"), externalUrl = o.optNullableString("externalUrl"), trailerUrl = o.optNullableString("trailerUrl"), providers = jsonArray(o, "providers").map { it.toString() }.toSet(), people = jsonArray(o, "people").map { it.toString() }, credits = jsonObjectArray(o, "credits").map(::jsonToPerson), notify = o.optBoolean("notify", true), addedAt = o.optLong("addedAt", System.currentTimeMillis()), lastWatchedAt = o.optLongOrNull("lastWatchedAt"), seasons = jsonObjectArray(o,"seasons").map(::jsonToSeason)
    )))

    private fun episodeToJson(e: Episode) = JSONObject().apply {
        put("userNote",e.userNote?:JSONObject.NULL)
        put("watchHistory",JSONArray(TrackingRules.knownHistory(e)));put("seasonUnknown",e.seasonUnknown);put("numberUnknown",e.numberUnknown)
        put("id", e.id); put("showId", e.showId); put("season", e.season); put("number", e.number); put("title", e.title); put("airDate", e.airDate?.toString() ?: JSONObject.NULL); put("watched", e.watched); put("runtimeMinutes", e.runtimeMinutes); put("tmdbId", e.tmdbId ?: JSONObject.NULL); put("tvdbId", e.tvdbId ?: JSONObject.NULL); put("seasonTmdbId", e.seasonTmdbId ?: JSONObject.NULL); put("seasonTvdbId", e.seasonTvdbId ?: JSONObject.NULL); put("plays", e.plays); put("skipped", e.skipped); put("collected", e.collected); put("overview", e.overview); put("stillUrl", e.stillUrl ?: JSONObject.NULL); put("seasonPosterUrl", e.seasonPosterUrl ?: JSONObject.NULL); put("catalogRating", e.catalogRating ?: JSONObject.NULL); put("catalogRatingVotes", e.catalogRatingVotes ?: JSONObject.NULL); put("lastWatchedAt", e.lastWatchedAt ?: JSONObject.NULL); put("seasonProviders",JSONArray(e.seasonProviders.toList())); put("rating",e.rating ?: JSONObject.NULL)
    }

    private fun jsonToEpisode(o: JSONObject) = Episode(
        userNote=o.optNullableString("userNote"),
        watchHistory=jsonArray(o,"watchHistory").mapNotNull { (it as? Number)?.toLong() }.filter { it>0 }.distinct().sorted(),seasonUnknown=o.optBoolean("seasonUnknown",false),numberUnknown=o.optBoolean("numberUnknown",false),
        id = o.getString("id"), showId = o.getString("showId"), season = o.getInt("season"), number = if (o.has("number")) o.getInt("number") else o.optInt("num", 0), title = o.getString("title"), airDate = o.optDate("airDate") ?: o.optDate("date"), watched = o.optBoolean("watched") && !o.optBoolean("skipped"), runtimeMinutes = o.optInt("runtimeMinutes", 45), tmdbId = o.optIntOrNull("tmdbId"), tvdbId = o.optIntOrNull("tvdbId"), seasonTmdbId = o.optIntOrNull("seasonTmdbId"), seasonTvdbId = o.optIntOrNull("seasonTvdbId"), plays = o.optInt("plays", if (o.optBoolean("watched")) 1 else 0), skipped = o.optBoolean("skipped"), collected = o.optBoolean("collected"), overview = o.optString("overview"), stillUrl = o.optNullableString("stillUrl"), seasonPosterUrl = o.optNullableString("seasonPosterUrl"), catalogRating = if (!o.has("catalogRating") || o.isNull("catalogRating")) null else o.optDouble("catalogRating", 0.0).takeIf { it >= 0.0 }, catalogRatingVotes = o.optIntOrNull("catalogRatingVotes"), lastWatchedAt = o.optLongOrNull("lastWatchedAt"), seasonProviders = jsonArray(o,"seasonProviders").map { it.toString() }.toSet(), rating = o.optIntOrNull("rating")
    )

    private fun listToJson(l: UserList) = JSONObject().put("id", l.id).put("name", l.name).put("description", l.description).put("sortMode", l.sortMode.name).put("ascending", l.ascending).put("manualOrder",l.manualOrder).put("sortOrder",JSONArray(l.sortOrder))
    private fun jsonToList(o:JSONObject):UserList {
        for(key in listOf("ascending","manualOrder"))require(!o.has(key)||o.isNull(key)||o.opt(key) is Boolean){"Option de liste invalide."}
        require(!o.has("sortOrder")||o.optJSONArray("sortOrder")!=null){"Ordre personnel invalide."}
        val order=jsonArray(o,"sortOrder");require(order.size<=50_000&&order.all{it is String&&it.isNotEmpty()}&&order.toSet().size==order.size){"Ordre personnel en double ou invalide."}
        return ListRules.definition(UserList(o.getString("id"),o.getString("name"),o.optString("description"),runCatching{SortMode.valueOf(o.optString("sortMode","TITLE").uppercase())}.getOrDefault(SortMode.TITLE),o.optBoolean("ascending",true),o.optBoolean("manualOrder",false),order.map{it.toString()}))
    }
    private fun franchiseToJson(f: FranchiseDefinition) = JSONObject().put("id", f.id).put("name", f.name).put("queryTerms", JSONArray(f.queryTerms)).put("aliases", JSONArray(f.aliases)).put("description", f.description).put("custom", true)
    private fun jsonToFranchise(o: JSONObject) = FranchiseRules.validate(FranchiseDefinition(o.getString("id"), o.getString("name"), jsonArray(o, "queryTerms").map { it.toString() }, jsonArray(o, "aliases").map { it.toString() }, o.optString("description"), custom = true))

    private fun settingsToJson(s: AppSettings) = JSONObject().apply {
        put("tmdbApiKey", s.tmdbApiKey); put("traktClientId", s.traktClientId); put("traktClientSecret", s.traktClientSecret); put("rawgApiKey", s.rawgApiKey); put("language", s.language); put("region", s.region); put("darkTheme", s.darkTheme); put("includeSpecials", s.includeSpecials); put("recentDays", s.recentDays); put("upcomingDays", s.upcomingDays); put("includeHiddenInCalendar", s.includeHiddenInCalendar); put("notificationsEnabled", s.notificationsEnabled); put("notificationHour", s.notificationHour); put("detailJingleEnabled", s.detailJingleEnabled); put("hideEpisodeSpoilers", s.hideEpisodeSpoilers); put("showCastImages", s.showCastImages); put("statisticsIncludeRewatches", s.statisticsIncludeRewatches); put("traktAutoSync", s.traktAutoSync); put("traktSyncIntervalHours", s.traktSyncIntervalHours); writeV8Preferences(this,s)
    }

    private fun jsonToSettings(o: JSONObject) = AppSettings(
        tmdbApiKey = o.optString("tmdbApiKey"), traktClientId = o.optString("traktClientId"), traktClientSecret = o.optString("traktClientSecret"), rawgApiKey = o.optString("rawgApiKey"), language = o.optString("language", "fr-FR"), region = o.optString("region", "FR"), darkTheme = o.optBoolean("darkTheme"), includeSpecials = o.optBoolean("includeSpecials"), recentDays = o.optInt("recentDays", 30), upcomingDays = o.optInt("upcomingDays", 60), includeHiddenInCalendar = o.optBoolean("includeHiddenInCalendar", false), notificationsEnabled = o.optBoolean("notificationsEnabled", true), notificationHour = o.optInt("notificationHour", 9).coerceIn(0, 23), detailJingleEnabled = o.optBoolean("detailJingleEnabled", true), hideEpisodeSpoilers = o.optBoolean("hideEpisodeSpoilers", false), showCastImages = o.optBoolean("showCastImages", true), statisticsIncludeRewatches = o.optBoolean("statisticsIncludeRewatches", true), traktAutoSync = o.optBoolean("traktAutoSync", true), traktSyncIntervalHours = o.optInt("traktSyncIntervalHours", 6).coerceIn(1, 168),
        statusFilters = readStatusFilters(o), librarySort = readLibrarySort(o),
        autoBackupEnabled = o.optBoolean("autoBackupEnabled",true),autoBackupMinutes = o.optInt("autoBackupMinutes",30).coerceIn(1,1440),
        catalogAutoRefresh = o.optBoolean("catalogAutoRefresh",true),detailJingleVolume = o.optInt("detailJingleVolume",42).coerceIn(0,100),
        discoverYearMin = o.optInt("discoverYearMin",0),discoverYearMax = o.optInt("discoverYearMax",0),discoverLanguage = o.optString("discoverLanguage", "")
    )

    // Local-only journal. It is deliberately excluded from portable library exports.
    @Synchronized
    private fun outbox(): List<PendingChange> {
        val raw=prefs.getString("trakt_outbox_v8",null) ?: credentials.getString("trakt_outbox_v7","[]") ?: "[]"
        val a=JSONArray(raw)
        return (0 until a.length()).map { i ->
            val o=a.getJSONObject(i)
            PendingChange(o.getString("id"),o.getString("type"),o.getInt("tmdbId"),o.optString("title"),o.getString("field"),o.optBoolean("enabled"),o.optIntOrNull("rating"),o.optLongOrNull("watchedAt"),o.optBoolean("uncertain"),o.optNullableString("heldReason"))
        }
    }
    @Synchronized
    private fun outboxJson(items:List<PendingChange>):String {
        val a=JSONArray()
        items.forEach { o->a.put(JSONObject().put("id",o.id).put("type",o.type).put("tmdbId",o.tmdbId).put("title",o.title).put("field",o.field).put("enabled",o.enabled).put("rating",o.rating?:JSONObject.NULL).put("watchedAt",o.watchedAt?:JSONObject.NULL).put("uncertain",o.uncertain).put("heldReason",o.heldReason?:JSONObject.NULL)) }
        return a.toString()
    }
    private fun writeOutbox(items:List<PendingChange>) {
        check(prefs.edit().putString("trakt_outbox_v8",outboxJson(items)).commit()) { "Impossible d’enregistrer la file Trakt." }
        credentials.edit().putBoolean("trakt_dirty",items.isNotEmpty()).apply()
    }
    @Synchronized
    private fun prepareTraktAccount(username:String) {
        val previous=credentials.getString("trakt_journal_account",null)
        if(previous != username) {
            if(!previous.isNullOrBlank())check(prefs.edit().putString("trakt_queue_account_"+previous,outboxJson(outbox())).commit()) { "Archivage de la file impossible." }
            val restored=prefs.getString("trakt_queue_account_"+username,"[]")?:"[]"
            check(prefs.edit().putString("trakt_outbox_v8",restored).commit()) { "Restauration de la file impossible." }
            credentials.edit().remove("trakt_baseline_v7").putString("trakt_journal_account",username).remove("trakt_last_sync").commit()
        }
        lastTracked=TrackingRules.records(media.toList(),episodes.toList())
    }
    fun pendingTraktChanges(): List<PendingChange> = outbox()
    @Synchronized
    fun resolveUncertainChange(id:String,alreadyApplied:Boolean) {
        check(syncLock.tryLock()) { "Une synchronisation est en cours." }
        try {

        val queue=outbox()
        require(queue.any { it.id==id && it.uncertain }) { "Cette opération n’attend pas de décision." }
        writeOutbox(if(alreadyApplied) queue.filterNot { it.id==id } else queue.map { if(it.id==id) it.copy(uncertain=false) else it })
        logDiagnostic("info","Décision manuelle sur un envoi Trakt incertain.")
        } finally { syncLock.unlock() }
    }
    @Synchronized
    fun resolveHeldChange(id:String,resume:Boolean) {
        check(syncLock.tryLock()) { "Une synchronisation est en cours." }
        try {

        val queue=outbox()
        require(queue.any { it.id==id && it.heldReason!=null }) { "Cette action n’est pas suspendue." }
        writeOutbox(if(resume)queue.map { if(it.id==id) it.copy(heldReason=null) else it } else queue.filterNot { it.id==id })
        logDiagnostic("info","Décision locale sur une ancienne action Trakt suspendue.")
        } finally { syncLock.unlock() }
    }
    @Synchronized
    private fun replacePending(op:PendingChange) { writeOutbox(outbox().map { if(it.id==op.id) op else it }) }
    @Synchronized
    private fun acknowledgePending(id:String) { writeOutbox(outbox().filterNot { it.id==id }) }
    private fun flushOutbox(token:String,clientId:String,generation:Int):Int {
        var sent=0
        for(original in outbox()) {
            if(outbox().none { it.id==original.id })continue
            check(generation==authGeneration) { "Compte déconnecté." }
            check(original.heldReason==null) { "Des actions Trakt sont suspendues après un import. Examine-les dans Bibliothèque Trakt avant de les reprendre." }
            check(!original.uncertain) { "Un envoi de l’historique est incertain. Vérifie-le dans les réglages Trakt avant de continuer." }
            // Commit this marker BEFORE POST. A process crash must not duplicate a viewing.
            val op=if(original.field=="history" && original.enabled) original.copy(uncertain=true) else original
            if(op.uncertain) replacePending(op)
            try {
                trakt.sendChange(token,clientId,op)
                check(generation==authGeneration) { "Compte déconnecté pendant l’envoi." }
                acknowledgePending(op.id);sent++
            } catch(e:Exception) {
                // A definitive HTTP rejection can be retried. A lost acknowledgement cannot.
                if(e is TraktClient.HttpFailure && IntegrationRules.definitiveHistoryRejection(e.status) && op.uncertain) replacePending(op.copy(uncertain=false))
                throw e
            }
        }
        return sent
    }
    private fun remoteRecords(snapshot:TraktClient.SyncSnapshot):List<TrackingRecord> {
        val movieHistory=snapshot.watchedMovies.associateBy { it.tmdbId }
        val movieCollection=snapshot.collectionMovies.map { it.tmdbId }.toSet()
        val watch=snapshot.watchlist.map { it.kind.name.lowercase()+":"+it.tmdbId }.toSet()
        val ratings=snapshot.ratings.associate { it.kind.name.lowercase()+":"+it.tmdbId to it.rating }
        val parentIds=media.filter { it.kind==MediaKind.SHOW }.associate { it.id to it.tmdbId }
        fun key(show:Int?,season:Int,number:Int)="$show:$season:$number"
        val eh=snapshot.watchedEpisodes.associateBy { key(it.showTmdbId,it.season,it.episode) }
        val ec=snapshot.collectionEpisodes.map { key(it.showTmdbId,it.season,it.episode) }.toSet()
        return media.filter { it.tmdbId!=null && it.kind in setOf(MediaKind.MOVIE,MediaKind.SHOW) }.map { m ->
            val type=m.kind.name.lowercase();val k="$type:${m.tmdbId}";val h=if(m.kind==MediaKind.MOVIE) movieHistory[m.tmdbId] else null
            TrackingRecord(type,m.tmdbId!!,m.title,h!=null,h?.plays ?: 0,h?.lastWatchedAt,m.kind==MediaKind.MOVIE && m.tmdbId in movieCollection,k in watch,ratings[k])
        } + episodes.filter { it.tmdbId!=null }.map { e ->
            val k=key(parentIds[e.showId],e.season,e.number);val h=eh[k]
            TrackingRecord("episode",e.tmdbId!!,e.title,h!=null,h?.plays ?: 0,h?.lastWatchedAt,k in ec,rating=snapshot.episodeRatings[e.tmdbId])
        }
    }
    private fun reconcileUncertain(snapshot:TraktClient.SyncSnapshot) {
        val remote=remoteRecords(snapshot).associateBy { it.key }
        outbox().filter { it.heldReason==null && it.uncertain && it.field=="history" && it.enabled }.forEach { op ->
            val r=remote[op.key]
            if(r?.watched==true && r.lastWatchedAt!=null && op.watchedAt!=null && r.lastWatchedAt/1000==op.watchedAt/1000) acknowledgePending(op.id)
        }
    }
    private fun trackingToJson(x:TrackingRecord) = JSONObject().put("type",x.type).put("tmdbId",x.tmdbId).put("title",x.title).put("watched",x.watched).put("plays",x.plays).put("lastWatchedAt",x.lastWatchedAt ?: JSONObject.NULL).put("collected",x.collected).put("watchlist",x.watchlist).put("rating",x.rating ?: JSONObject.NULL)
    private fun trackingFromJson(o:JSONObject)=TrackingRecord(o.getString("type"),o.getInt("tmdbId"),o.optString("title"),o.optBoolean("watched"),o.optInt("plays"),o.optLongOrNull("lastWatchedAt"),o.optBoolean("collected"),o.optBoolean("watchlist"),o.optIntOrNull("rating"))
    private fun baselineRecords():List<TrackingRecord> {
        if(prefs.getBoolean("trakt_reset_baseline_v9",false))return emptyList()
        val a=JSONArray(credentials.getString("trakt_baseline_v7","[]") ?: "[]")
        return (0 until a.length()).map { trackingFromJson(a.getJSONObject(it)) }
    }
    private fun writeBaseline(values:List<TrackingRecord>) {
        val a=JSONArray();values.forEach { a.put(trackingToJson(it)) }
        check(credentials.edit().putString("trakt_baseline_v7",a.toString()).commit()) { "Point de synchronisation non enregistré." }
        check(prefs.edit().remove("trakt_reset_baseline_v9").commit()) { "Réinitialisation du point de synchronisation non enregistrée." }
    }
    fun logDiagnostic(level:String,message:String) {
        val now=System.currentTimeMillis()
        val safe=message.replace(Regex("(?i)(api[_-]?key|access_token|refresh_token|client_secret|authorization)([=:\\s]+)[^\\s&,]+"),"$1$2[masqué]").take(600)
        val old=runCatching { JSONArray(prefs.getString("diagnostics_v7","[]")) }.getOrDefault(JSONArray())
        val entries=(0 until old.length()).mapNotNull { old.optJSONObject(it) }.filter { now-it.optLong("at") < 3*86_400_000L }.takeLast(199)
        val a=JSONArray();entries.forEach { a.put(it) };a.put(JSONObject().put("at",now).put("level",level).put("message",safe))
        prefs.edit().putString("diagnostics_v7",a.toString()).apply()
    }
    fun diagnosticsText():String {
        val a=runCatching { JSONArray(prefs.getString("diagnostics_v7","[]")) }.getOrDefault(JSONArray())
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.reversed().joinToString("\n\n") { "${Instant.ofEpochMilli(it.optLong("at"))} · ${it.optString("level")}\n${it.optString("message")}" }.ifBlank { "Aucun événement enregistré." }
    }
    @Synchronized
    fun createLocalBackup(reason:String="Manuelle"):Result<Unit> = runCatching {
        requireHealthy()
        if(savingBackup) return@runCatching
        savingBackup=true
        try {
            val old=runCatching { JSONArray(prefs.getString("backups_v7","[]")) }.getOrDefault(JSONArray())
            val a=JSONArray();(maxOf(0,old.length()-4) until old.length()).forEach { a.put(old.get(it)) }
            a.put(JSONObject().put("at",System.currentTimeMillis()).put("reason",reason).put("state",JSONObject(exportJson())))
            check(prefs.edit().putString("backups_v7",a.toString()).putLong("last_backup",System.currentTimeMillis()).commit()) { "Sauvegarde locale impossible." }
        } finally { savingBackup=false }
    }
    fun localBackupSummaries():List<Pair<Int,String>> {
        val a=runCatching { JSONArray(prefs.getString("backups_v7","[]")) }.getOrDefault(JSONArray())
        return (0 until a.length()).reversed().map { i -> val o=a.getJSONObject(i);i to "${o.optString("reason")} · ${Instant.ofEpochMilli(o.optLong("at"))}" }
    }
    fun prepareLocalBackupImport(index:Int):Result<PreparedLibraryImport> = runCatching {
        val list=JSONArray(prefs.getString("backups_v7","[]"));val raw=list.getJSONObject(index).getJSONObject("state").toString()
        preparePortableImport(raw,listOf("Copie locale ${index+1}")).getOrThrow()
    }
    fun restoreLocalBackup(index:Int):Result<Unit> = runCatching {
        val a=JSONArray(prefs.getString("backups_v7","[]"));val raw=a.getJSONObject(index).getJSONObject("state").toString()
        importJson(raw).getOrThrow()
    }
    private fun seasonToJson(x:SeasonInfo)=JSONObject().put("number",x.number).put("title",x.title).put("overview",x.overview).put("posterUrl",x.posterUrl ?: JSONObject.NULL).put("episodeCount",x.episodeCount).put("providers",JSONArray(x.providers.toList()))
    private fun jsonToSeason(o:JSONObject)=SeasonInfo(o.optInt("number"),o.optString("title"),o.optString("overview"),o.optNullableString("posterUrl"),o.optInt("episodeCount"),jsonArray(o,"providers").map { it.toString() }.toSet())
    private fun writeV8Preferences(o:JSONObject,s:AppSettings) {
        val filters=JSONObject();s.statusFilters.forEach { (k,v) -> filters.put(k,v) }
        val sorts=JSONObject();s.librarySort.forEach { (k,v) -> sorts.put(k,JSONObject().put("mode",v.mode.name).put("ascending",v.ascending)) }
        o.put("statusFilters",filters).put("librarySort",sorts).put("autoBackupEnabled",s.autoBackupEnabled).put("autoBackupMinutes",s.autoBackupMinutes).put("catalogAutoRefresh",s.catalogAutoRefresh).put("detailJingleVolume",s.detailJingleVolume).put("discoverYearMin",s.discoverYearMin).put("discoverYearMax",s.discoverYearMax).put("discoverLanguage",s.discoverLanguage)
    }
    private fun readStatusFilters(o:JSONObject):Map<String,Int> {
        val f=o.optJSONObject("statusFilters") ?: return emptyMap()
        return f.keys().asSequence().map { TrackingRules.normalizedStatus(it).name to f.optInt(it).coerceIn(-1,1) }.toMap()
    }
    private fun readLibrarySort(o:JSONObject):Map<String,LibrarySortPreference> {
        val f=o.optJSONObject("librarySort") ?: return emptyMap()
        return f.keys().asSequence().map { k -> val v=f.optJSONObject(k) ?: JSONObject();k.lowercase() to LibrarySortPreference(runCatching { SortMode.valueOf(v.optString("mode","TITLE").uppercase()) }.getOrDefault(SortMode.TITLE),v.optBoolean("ascending",true)) }.toMap()
    }

    private fun personToJson(p: PersonCredit) = JSONObject().put("id", p.id).put("name", p.name).put("role", p.role).put("department", p.department).put("profileUrl", p.profileUrl ?: JSONObject.NULL).put("externalUrl", p.externalUrl ?: JSONObject.NULL)
    private fun jsonToPerson(o: JSONObject) = PersonCredit(o.optString("id", o.optString("name")), o.optString("name"), o.optString("role"), o.optString("department"), o.optNullableString("profileUrl"), o.optNullableString("externalUrl"))
    private fun ratingToJson(r: RatingSource) = JSONObject().put("source", r.source).put("label", r.label).put("score", r.score).put("maxScore", r.maxScore).put("votes", r.votes ?: JSONObject.NULL).put("externalUrl", r.externalUrl ?: JSONObject.NULL)
    private fun jsonToRating(o: JSONObject) = RatingSource(o.optString("source"), o.optString("label", o.optString("source")), o.optDouble("score", 0.0), o.optDouble("maxScore", 10.0), o.optIntOrNull("votes"), o.optNullableString("externalUrl"))
    private fun jsonObjectArray(root: JSONObject, key: String): List<JSONObject> { val a = root.optJSONArray(key) ?: JSONArray(); return (0 until a.length()).mapNotNull { a.optJSONObject(it) } }

    private fun jsonArray(root: JSONObject, key: String): List<Any> { val a = root.optJSONArray(key) ?: JSONArray(); return (0 until a.length()).map { a.get(it) } }
    private fun JSONObject.optNullableString(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    private fun JSONObject.optIntOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else optInt(key)
    private fun JSONObject.optLongOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else optLong(key).takeIf { it > 0 }
    private fun JSONObject.optDate(key: String): LocalDate? = optNullableString(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
