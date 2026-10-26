package com.franchiseguide.app.model

import java.time.LocalDate

enum class MediaKind { SHOW, MOVIE, GAME, BOOK }
enum class ShowStatus { CONTINUING, UPCOMING, IN_PRODUCTION, PILOT, ENDED, CANCELED, UNKNOWN }
enum class SortMode { TITLE, YEAR, RECENTLY_WATCHED, ADDED, PROGRESS, NEXT_EPISODE, STATUS, REMAINING, RATING }

data class LibrarySortPreference(val mode: SortMode = SortMode.TITLE, val ascending: Boolean = true)
data class SeasonInfo(val number: Int, val title: String, val overview: String = "", val posterUrl: String? = null, val episodeCount: Int = 0, val providers: Set<String> = emptySet())

data class PersonCredit(
    val id: String,
    val name: String,
    val role: String = "",
    val department: String = "",
    val profileUrl: String? = null,
    val externalUrl: String? = null
)


data class CommunityComment(
    val id: Long,
    val author: String,
    val text: String,
    val review: Boolean = false,
    val spoiler: Boolean = false,
    val likes: Int = 0,
    val createdAt: String? = null,
    val username: String? = null
)

data class RatingSource(
    val source: String,
    val label: String,
    val score: Double,
    val maxScore: Double = 10.0,
    val votes: Int? = null,
    val externalUrl: String? = null
) {
    val normalized10: Double get() = if (maxScore <= 0.0) score else score / maxScore * 10.0
}

data class Episode(
    val id: String,
    val showId: String,
    val season: Int,
    val number: Int,
    val title: String,
    val airDate: LocalDate?,
    val watched: Boolean = false,
    val runtimeMinutes: Int = 45,
    val tmdbId: Int? = null,
    val tvdbId: Int? = null,
    val seasonTmdbId: Int? = null,
    val seasonTvdbId: Int? = null,
    val plays: Int = if (watched) 1 else 0,
    val skipped: Boolean = false,
    val collected: Boolean = false,
    val overview: String = "",
    val stillUrl: String? = null,
    val seasonPosterUrl: String? = null,
    val catalogRating: Double? = null,
    val catalogRatingVotes: Int? = null,
    val lastWatchedAt: Long? = null,
    val seasonProviders: Set<String> = emptySet(),
    val rating: Int? = null,
    val watchHistory: List<Long> = emptyList(),
    val seasonUnknown: Boolean = false,
    val numberUnknown: Boolean = false,
    val userNote: String? = null
) {
    val code: String get() = if(seasonUnknown || numberUnknown) "Numérotation à compléter" else "S%02dE%02d".format(season, number)
}

data class MediaItem(
    val id: String,
    val tmdbId: Int? = null,
    val kind: MediaKind,
    val title: String,
    val subtitle: String = "",
    val year: Int? = null,
    val overview: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val added: Boolean = true,
    val watched: Boolean = false,
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val collected: Boolean = false,
    val watchlist: Boolean = false,
    val rating: Int? = null,
    val catalogRating: Double? = null,
    val catalogRatingVotes: Int? = null,
    val ratings: List<RatingSource> = emptyList(),
    val status: ShowStatus = ShowStatus.UNKNOWN,
    val statusRaw: String? = null,
    val nextAirDate: LocalDate? = null,
    val lastAirDate: LocalDate? = null,
    val network: String? = null,
    val contentRating: String? = null,
    val tagline: String? = null,
    val homepage: String? = null,
    val country: String? = null,
    val releaseSchedule: String? = null,
    val originalTitle: String? = null,
    val seasonCount: Int? = null,
    val episodeCount: Int? = null,
    val productionCompanies: Set<String> = emptySet(),
    val franchiseId: String? = null,
    val franchiseName: String? = null,
    val isSuggestion: Boolean = false,
    val listIds: Set<String> = emptySet(),
    val tvdbId: Int? = null,
    val imdbId: String? = null,
    val source: String = "local",
    val sourceId: String? = null,
    val catalogComplete: Boolean = false,
    val catalogUpdatedAt: Long? = null,
    val creator: String? = null,
    val publisher: String? = null,
    val platforms: Set<String> = emptySet(),
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val progressUnit: String = "",
    val started: Boolean = false,
    val plays: Int = if (watched) 1 else 0,
    val userNote: String? = null,
    val runtimeMinutes: Int? = null,
    val genres: Set<String> = emptySet(),
    val releaseDate: LocalDate? = null,
    val originalLanguage: String? = null,
    val externalUrl: String? = null,
    val trailerUrl: String? = null,
    val providers: Set<String> = emptySet(),
    val people: List<String> = emptyList(),
    val credits: List<PersonCredit> = emptyList(),
    val notify: Boolean = true,
    val addedAt: Long = System.currentTimeMillis(),
    val lastWatchedAt: Long? = null,
    val seasons: List<SeasonInfo> = emptyList(),
    val watchHistory: List<Long> = emptyList(),
    val franchiseManual: Boolean = false,
    val contentLanguage: String? = null,
    val steam: SteamGameMetadata? = null,
    // Additional catalogue identities retained when Web/PC combines two media cards.
    // They never replace source/sourceId or represent a signed-in account.
    val rawgId: String? = null,
    val openLibraryId: String? = null,
    val steamAppId: String? = null,
    val retroGameId: String? = null,
    val trackingStatus: String? = null,
    val playtimeMinutes: Int? = null,
    val personalPlatform: String? = null,
    val personalFormat: String? = null,
    val selectedEdition: SavedBookEdition? = null
) {
    val progressRatio: Float
        get() = if (progressTotal <= 0) 0f else (progressCurrent.toFloat() / progressTotal.toFloat()).coerceIn(0f, 1f)

    val isInProgress: Boolean
        get() = if(ProgressRules.applicable(this))ProgressRules.status(this)=="active" else !watched && (started || progressCurrent > 0)
}

data class SeasonProgress(
    val season: Int,
    val total: Int,
    val watched: Int,
    val remaining: Int,
    val skipped: Int,
    val collected: Int,
    val released: Int,
    val unreleased: Int,
    val minutesWatched: Long,
    val posterUrl: String?
) {
    val completion: Float get() = if (total <= 0) 0f else watched.toFloat() / total.toFloat()
}

data class StatisticsSnapshot(
    val mediaTotal: Int,
    val showsTotal: Int,
    val showsFinished: Int,
    val showsContinuing: Int,
    val episodesTotal: Int,
    val episodesWatched: Int,
    val episodePlays: Int,
    val episodeRewatches: Int,
    val moviesTotal: Int,
    val moviesWatched: Int,
    val moviePlays: Int,
    val movieRewatches: Int,
    val gamesTotal: Int,
    val gamesCompleted: Int,
    val booksTotal: Int,
    val booksRead: Int,
    val pagesRead: Long,
    val uniqueVideoMinutes: Long,
    val totalVideoMinutesWithRewatches: Long,
    val franchisesTotal: Int,
    val franchisesComplete: Int,
    val favorites: Int,
    val collected: Int,
    val watchlist: Int,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val topGenres: List<Pair<String, Int>>,
    val topFranchises: List<Pair<String, Int>>,
    val recentActivityDays: List<Pair<LocalDate, Int>>
)

data class UserList(
    val id: String,
    val name: String,
    val description: String = "",
    val sortMode: SortMode = SortMode.TITLE,
    val ascending: Boolean = true,
    val manualOrder: Boolean = false,
    val sortOrder: List<String> = emptyList()
)

data class FranchiseDefinition(
    val id: String,
    val name: String,
    val queryTerms: List<String>,
    val aliases: List<String> = emptyList(),
    val description: String = "",
    val sortOrder: List<String> = emptyList(),
    val custom: Boolean = false
)

data class FranchiseGroup(
    val definition: FranchiseDefinition,
    val items: List<MediaItem>,
    val addedCount: Int,
    val totalCount: Int
) {
    val missingCount: Int get() = (totalCount - addedCount).coerceAtLeast(0)
    val completion: Float get() = if (totalCount == 0) 0f else addedCount.toFloat() / totalCount
    val kinds: Set<MediaKind> get() = items.map { it.kind }.toSet()
}

data class AppSettings(
    val tmdbApiKey: String = "",
    val traktClientId: String = "",
    val traktClientSecret: String = "",
    val rawgApiKey: String = "",
    val language: String = "fr-FR",
    val region: String = "FR",
    val darkTheme: Boolean = false,
    val includeSpecials: Boolean = false,
    val recentDays: Int = 30,
    val upcomingDays: Int = 60,
    val includeHiddenInCalendar: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val notificationHour: Int = 9,
    val detailJingleEnabled: Boolean = true,
    val hideEpisodeSpoilers: Boolean = false,
    val showCastImages: Boolean = true,
    val statisticsIncludeRewatches: Boolean = true,
    val traktAutoSync: Boolean = true,
    val traktSyncIntervalHours: Int = 6,
    val statusFilters: Map<String, Int> = emptyMap(),
    val librarySort: Map<String, LibrarySortPreference> = emptyMap(),
    val autoBackupEnabled: Boolean = true,
    val autoBackupMinutes: Int = 30,
    val catalogAutoRefresh: Boolean = true,
    val detailJingleVolume: Int = 42,
    val discoverYearMin: Int = 0,
    val discoverYearMax: Int = 0,
    val discoverLanguage: String = ""
)

data class SeriesGuideImportSummary(
    val shows: Int = 0,
    val movies: Int = 0,
    val episodes: Int = 0,
    val lists: Int = 0,
    val linkedListItems: Int = 0,
    val skippedListItems: Int = 0,
    val filesRead: Int = 0
) {
    fun human(): String = buildString {
        append("Import SeriesGuide terminé : ")
        append("$shows série(s), $movies film(s), $episodes épisode(s), $lists liste(s)")
        if (linkedListItems > 0 || skippedListItems > 0) {
            append(" · $linkedListItems élément(s) de liste relié(s)")
            if (skippedListItems > 0) append(" · $skippedListItems ignoré(s)")
        }
    }
}

/** Filmography metadata comes from TMDb, not title-guessing. */
data class PersonWork(val media: MediaItem, val roles: List<String>)
data class PersonProfile(val id:Int,val name:String,val biography:String,val profileUrl:String?,
    val birthday:String?,val deathday:String?,val birthplace:String?,val department:String?,val works:List<PersonWork>)
data class DatedActivity(val id:String,val mediaId:String,val title:String,val parentTitle:String,val type:String,val at:Long)

data class IntegrityIssue(
    val severity: String,
    val code: String,
    val message: String
)

data class DuplicateCandidate(
    val firstId: String,
    val secondId: String,
    val title: String,
    val kind: MediaKind,
    val safeToMerge: Boolean,
    val conflicts: List<String> = emptyList()
)

data class IntegrityReport(
    val issues: List<IntegrityIssue>,
    val duplicateCandidates: List<DuplicateCandidate>
) {
    val criticalCount: Int get() = issues.count { it.severity == "critical" }
    val warningCount: Int get() = issues.count { it.severity == "warning" }
    val repairableCount: Int get() = issues.count { it.severity == "repairable" }
    val healthy: Boolean get() = issues.isEmpty() && duplicateCandidates.isEmpty()
}
