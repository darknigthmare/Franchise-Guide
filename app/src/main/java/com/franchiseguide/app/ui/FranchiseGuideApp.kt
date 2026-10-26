package com.franchiseguide.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.franchiseguide.app.data.AppRepository
import com.franchiseguide.app.model.FranchiseGroup
import com.franchiseguide.app.model.MediaItem
import com.franchiseguide.app.model.MediaKind
import com.franchiseguide.app.model.UserList
import com.franchiseguide.app.notifications.EpisodeNotificationScheduler
import com.franchiseguide.app.widget.DashboardWidgetProvider
import com.franchiseguide.app.sync.TraktSyncScheduler
import com.franchiseguide.app.ui.theme.FranchiseGuideTheme
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import com.franchiseguide.app.model.PreparedLibraryImport
import kotlinx.coroutines.withContext

private enum class RootTab(val label: String) {
    SHOWS("Séries"), MOVIES("Films"), GAMES("Jeux"), BOOKS("Livres"),
    FRANCHISES("Franchises"), CALENDAR("Agenda"), MORE("Plus")
}

private sealed interface Overlay {
    data class Detail(val item: MediaItem) : Overlay
    data class EpisodeSheet(val showId:String,val episodeId:String):Overlay
    data class Availability(val mediaId:String,val season:Int?=null):Overlay
    data class Episodes(val showId:String,val season:Int?=null,val action:String="watch") : Overlay
    data class Franchise(val group: FranchiseGroup) : Overlay
    data class Search(val kind: MediaKind? = null) : Overlay
    data object Settings : Overlay
    data object Today : Overlay
    data object Statistics : Overlay
    data object Discover : Overlay
    data object History : Overlay
    data object TraktLibrary : Overlay
    data object NextUp : Overlay
    data object Organize : Overlay
    data object Integrity : Overlay
    data class Subset(val mode: String) : Overlay
    data class ListDetail(val list: UserList) : Overlay
    data object ProtectedFiles : Overlay
    data object PersonalLists : Overlay
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FranchiseGuideApp(repo: AppRepository) {
    val context = LocalContext.current
    if(repo.isRecoveryOnly()) { RecoveryScreen(repo); return }
    var tab by remember { mutableStateOf(RootTab.SHOWS) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    val episodeBack=remember { arrayOfNulls<()->Unit>(1) }
    fun leaveOverlay(){val p=overlay;if(p is Overlay.EpisodeSheet){if(episodeBack[0]!=null)episodeBack[0]?.invoke() else overlay=repo.media.firstOrNull{it.id==p.showId&&it.added}?.let{Overlay.Detail(it)};return};overlay=if(p is Overlay.Episodes)repo.media.firstOrNull{it.id==p.showId&&it.added}?.let{Overlay.Detail(it)} else if(p is Overlay.Availability)(repo.media.firstOrNull{it.id==p.mediaId&&it.added}?:repo.suggestions.firstOrNull{it.id==p.mediaId})?.let{Overlay.Detail(it)} else null}
    BackHandler(enabled=overlay!=null){leaveOverlay()}
    var ioMessage by remember { mutableStateOf<String?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        ioMessage = if (granted) "Notifications autorisées." else "Notifications Android non autorisées ; tu peux les réactiver dans les réglages système."
        EpisodeNotificationScheduler.schedule(context.applicationContext, repo)
    }
    val notificationFingerprint = remember(repo.media.toList(), repo.episodes.toList()) {
        31 * repo.media.filter { it.kind.name == "SHOW" }.map { listOf(it.id,it.added,it.hidden,it.notify,it.title) }.hashCode() +
            repo.episodes.map { Triple(it.id, it.watched, it.skipped) }.hashCode()
    }
    val widgetFingerprint = remember(repo.media.toList(), repo.episodes.toList()) {
        31 * repo.media.map { Triple(it.id, it.added, it.hidden) }.hashCode() + repo.episodes.map { it.id to it.watched }.hashCode()
    }
    val traktFingerprint = remember(repo.media.toList(), repo.episodes.toList()) {
        31 * repo.media.map { Triple(it.id, it.watched, it.collected) to (it.watchlist to it.rating) }.hashCode() +
            repo.episodes.map { Triple(it.id, it.watched, it.collected) to it.plays }.hashCode()
    }

    LaunchedEffect(repo.settings.value.notificationsEnabled) {
        if (repo.settings.value.notificationsEnabled && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(notificationFingerprint, repo.settings.value.notificationsEnabled, repo.settings.value.notificationHour, repo.settings.value.upcomingDays, repo.settings.value.includeSpecials, repo.settings.value.includeHiddenInCalendar, repo.settings.value.hideEpisodeSpoilers) {
        EpisodeNotificationScheduler.schedule(context.applicationContext, repo)
    }
    LaunchedEffect(widgetFingerprint) {
        DashboardWidgetProvider.refresh(context.applicationContext)
    }
    LaunchedEffect(repo.settings.value.traktAutoSync, repo.settings.value.traktSyncIntervalHours, repo.syncMessage.value) {
        TraktSyncScheduler.schedule(context.applicationContext, repo)
    }
    LaunchedEffect(Unit) {
        if (repo.settings.value.catalogAutoRefresh && repo.needsCatalogRefresh()) {
            withContext(Dispatchers.IO) { repo.refreshIncompleteCatalog(maxItems = 12) }
                .onSuccess { ioMessage = it }
        }
        if (repo.shouldAutoSyncTrakt()) {
            val result = withContext(Dispatchers.IO) { repo.syncFromTrakt(uploadLocal = true) }
            result.onSuccess { ioMessage = it }.onFailure { ioMessage = "Synchro Trakt auto : ${it.message}" }
        }
    }
    LaunchedEffect(traktFingerprint, repo.settings.value.traktAutoSync) {
        if (repo.settings.value.traktAutoSync && repo.isTraktConnected() && repo.traktIsDirty()) {
            delay(2500)
            if (repo.traktIsDirty()) withContext(Dispatchers.IO) { repo.uploadToTrakt() }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) ioMessage = runCatching {
            writeText(context, uri, repo.exportJson())
            repo.noteExternalBackupProposed("json","franchiseguide_backup_v11.json")
            "Sauvegarde FranchiseGuide V11 proposée. Vérifie le fichier dans Aujourd’hui."
        }.getOrElse { it.message ?: "Erreur d'export" }
    }

    var pendingCalendar by remember { mutableStateOf<String?>(null) }
    val calendarExporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        val text=pendingCalendar;pendingCalendar=null
        if(uri!=null&&text!=null)ioMessage=runCatching { writeText(context,uri,text);"Calendrier .ics exporté. Aucun compte externe modifié." }.getOrElse { it.message?:"Export calendrier impossible." }
    }

    val importScope=rememberCoroutineScope()
    var preparedImport by remember { mutableStateOf<PreparedLibraryImport?>(null) }
    var importBusy by remember { mutableStateOf(false) }
    var importTask by remember { mutableStateOf<Job?>(null) }
    var importTicket by remember { mutableIntStateOf(0) }
    fun prepareFiles(uris:List<Uri>,seriesGuide:Boolean) {
        if(uris.isEmpty())return
        importTask?.cancel();val ticket=++importTicket;importBusy=true
        importTask=importScope.launch {
            try {
                val docs=withContext(Dispatchers.IO){com.franchiseguide.app.model.ImportReviewRules.readDocuments(uris.map { uri->{context.contentResolver.openInputStream(uri)?:error("Fichier inaccessible.")} },!seriesGuide)}
                val names=uris.mapIndexed { i,uri->uri.lastPathSegment?.substringAfterLast('/')?.take(250)?:"Fichier ${i+1}" }
                val review=withContext(Dispatchers.Default){if(seriesGuide)repo.prepareSeriesGuideImport(docs,names) else repo.preparePortableImport(docs.single(),names)}
                if(ticket==importTicket)review.onSuccess{preparedImport=it}.onFailure{ioMessage=it.message?:"Préparation refusée : aucun fichier appliqué."}
            }catch(e:CancellationException){throw e}catch(e:Exception){if(ticket==importTicket)ioMessage=e.message?:"Lecture impossible."}
            finally{if(ticket==importTicket)importBusy=false}
        }
    }
    val importLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)prepareFiles(listOf(uri),false)}
    val seriesGuideImportLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->prepareFiles(uris,true)}
    FranchiseGuideTheme(darkTheme = repo.settings.value.darkTheme) {
    if(importBusy)AlertDialog(onDismissRequest={importTicket++;importTask?.cancel();importBusy=false},title={Text("Préparer l’import")},text={Text("Lecture UTF-8 et validation des fichiers. La bibliothèque n’est pas modifiée.")},confirmButton={},dismissButton={TextButton(onClick={importTicket++;importTask?.cancel();importBusy=false}){Text("Annuler")}})
    preparedImport?.let { review->ImportReviewPanel(repo,review,onClose={preparedImport=null},onApplied={preparedImport=null;overlay=null;ioMessage=it}) }


        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(titleFor(tab, overlay)) },
                    navigationIcon = {
                        if (overlay != null) IconButton(onClick = { leaveOverlay() }) { Icon(Icons.Default.ArrowBack, "Retour") }
                    },
                    actions = {
                        if (overlay == null && tab != RootTab.MORE && tab != RootTab.CALENDAR) {
                            val scopedKind = when (tab) { RootTab.SHOWS -> MediaKind.SHOW; RootTab.MOVIES -> MediaKind.MOVIE; RootTab.GAMES -> MediaKind.GAME; RootTab.BOOKS -> MediaKind.BOOK; else -> null }
                            IconButton(onClick = { overlay = Overlay.Search(scopedKind) }) { Icon(Icons.Default.Search, "Rechercher") }
                        }
                        if (overlay == null) IconButton(onClick = { overlay = Overlay.Settings }) { Icon(Icons.Default.MoreVert, "Réglages") }
                    }
                )
            },
            bottomBar = {
                if (overlay == null) {
                    NavigationBar {
                        RootTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Icon(iconFor(item), item.label) },
                                label = { Text(item.label) },
                                alwaysShowLabel = tab == item
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (overlay == null) {
                    val kind = when (tab) { RootTab.SHOWS -> MediaKind.SHOW; RootTab.MOVIES -> MediaKind.MOVIE; RootTab.GAMES -> MediaKind.GAME; RootTab.BOOKS -> MediaKind.BOOK; else -> null }
                    if (kind != null) ExtendedFloatingActionButton(onClick = { overlay = Overlay.Search(kind) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Ajouter") })
                }
            },
            snackbarHost = {
                if (ioMessage != null) {
                    Snackbar(action = { TextButton(onClick = { ioMessage = null }) { Text("OK") } }) { Text(ioMessage!!) }
                }
            }
        ) { padding ->
            Surface(Modifier.padding(padding)) {
                when (val page = overlay) {
                    is Overlay.Detail -> MediaDetailScreen(repo, page.item, onEpisode={eid->overlay=Overlay.EpisodeSheet(page.item.id,eid)}, onManageEpisodes={id,season,action->overlay=Overlay.Episodes(id,season,action)},onAvailability={id,season->overlay=Overlay.Availability(id,season)}) { overlay = Overlay.Detail(it) }
                    is Overlay.Availability -> WatchAvailabilityScreen(repo,page.mediaId,page.season)
                    is Overlay.Episodes -> EpisodeManagerScreen(repo,page.showId,page.season,page.action){leaveOverlay()}
                    is Overlay.EpisodeSheet -> EpisodeSheetScreen(repo,page.showId,page.episodeId,
                        onClose={overlay=repo.media.firstOrNull{it.id==page.showId&&it.added}?.let{Overlay.Detail(it)}},
                        onNavigate={overlay=Overlay.EpisodeSheet(page.showId,it)},onManage={overlay=Overlay.Episodes(page.showId)},
                        onAvailability={overlay=Overlay.Availability(page.showId,it)},onOpenMedia={overlay=Overlay.Detail(it)},onBackBinding={episodeBack[0]=it})
                    is Overlay.Franchise -> FranchiseDetailScreen(repo, page.group) { overlay = Overlay.Detail(it) }
                    is Overlay.Search -> SearchScreen(repo, page.kind) { overlay = Overlay.Detail(it) }
                    Overlay.Settings -> SettingsScreen(repo,onImported={overlay=null})
                    Overlay.Today -> TodayPanel(repo){overlay=Overlay.Detail(it)}
                    Overlay.Statistics -> StatisticsScreen(repo)
                    Overlay.Discover -> DiscoverScreen(repo) { overlay = Overlay.Detail(it) }
                    Overlay.History -> ActivityScreen(repo) { overlay = Overlay.Detail(it) }
                    Overlay.TraktLibrary -> TraktLibraryScreen(repo, { overlay = Overlay.Settings }, { overlay = Overlay.Detail(it) })
                    Overlay.NextUp -> NextUpScreen(repo){overlay=Overlay.Detail(it)}
                    Overlay.Organize -> OrganizeScreen(repo){overlay=Overlay.Detail(it)}
                    Overlay.Integrity -> IntegrityScreen(repo)
                    is Overlay.Subset -> LibrarySubsetScreen(repo, page.mode) { overlay = Overlay.Detail(it) }
                    is Overlay.ListDetail -> UserListScreen(repo, page.list, { overlay = Overlay.Detail(it) }, { overlay = Overlay.PersonalLists })
                    Overlay.ProtectedFiles -> ProtectedBackupPanel(repo){preparedImport=it;overlay=null}
                    Overlay.PersonalLists -> PersonalListsScreen(repo){overlay=Overlay.ListDetail(it)}
                    null -> when (tab) {
                        RootTab.SHOWS -> ShowsScreen(repo, { overlay = Overlay.Detail(it) }, { overlay = Overlay.Search(MediaKind.SHOW) })
                        RootTab.MOVIES -> MoviesScreen(repo, { overlay = Overlay.Detail(it) }, { overlay = Overlay.Search(MediaKind.MOVIE) })
                        RootTab.GAMES -> GamesScreen(repo, { overlay = Overlay.Detail(it) }, { overlay = Overlay.Search(MediaKind.GAME) })
                        RootTab.BOOKS -> BooksScreen(repo, { overlay = Overlay.Detail(it) }, { overlay = Overlay.Search(MediaKind.BOOK) })
                        RootTab.FRANCHISES -> FranchiseScreen(repo) { overlay = Overlay.Franchise(it) }
                        RootTab.CALENDAR -> CalendarScreen(repo,onExport={text->pendingCalendar=text;calendarExporter.launch("franchiseguide-agenda.ics")}) { overlay = Overlay.Detail(it) }
                        RootTab.MORE -> MoreScreen(
                            repo = repo,
                            onOpenSettings = { overlay = Overlay.Settings },
                            onProtected = {overlay=Overlay.ProtectedFiles},
                            onExport = { exportLauncher.launch("franchiseguide_backup_v11.json") },
                            onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                            onImportSeriesGuide = { seriesGuideImportLauncher.launch(arrayOf("application/json", "text/plain")) },
                            onOpenList = { overlay = Overlay.ListDetail(it) },
                            onLists = {overlay=Overlay.PersonalLists},
                            onOpenMedia = { overlay = Overlay.Detail(it) },
                            onToday = { overlay = Overlay.Today },
                            onStatistics = { overlay = Overlay.Statistics },
                            onDiscover = { overlay = Overlay.Discover },
                            onHistory = { overlay = Overlay.History },
                            onSubset = { overlay = Overlay.Subset(it) },
                            onTraktLibrary = { overlay = Overlay.TraktLibrary },
                            onIntegrity = { overlay = Overlay.Integrity },
                            onNextUp = { overlay = Overlay.NextUp },
                            onOrganize = { overlay = Overlay.Organize }
                        )
                    }
                }
            }
        }
    }
}

private fun titleFor(tab: RootTab, overlay: Overlay?): String = when (overlay) {
    is Overlay.EpisodeSheet -> "Fiche épisode"
    is Overlay.Detail -> overlay.item.title
    is Overlay.Availability -> "Où regarder"
    is Overlay.Episodes -> "Gérer les épisodes"
    is Overlay.Franchise -> overlay.group.definition.name
    is Overlay.Search -> overlay.kind?.let { "Ajouter · ${mediaKindLabel(it)}" } ?: "Recherche"
    Overlay.Settings -> "Réglages"
    Overlay.Today -> "Aujourd’hui"
    Overlay.Statistics -> "Statistiques"
    Overlay.Discover -> "Découvrir"
    Overlay.History -> "Historique"
    Overlay.TraktLibrary -> "Bibliothèque Trakt"
    Overlay.NextUp -> "À regarder / reprendre"
    Overlay.Organize -> "Organiser ma bibliothèque"
    Overlay.Integrity -> "Intégrité des données"
    is Overlay.Subset -> when (overlay.mode) {
        "collection" -> "Collection"
        "watchlist" -> "À voir / lire / jouer"
        "favorites" -> "Favoris"
        "hidden" -> "Masqués"
        else -> "Bibliothèque"
    }
    is Overlay.ListDetail -> overlay.list.name
    Overlay.ProtectedFiles -> "Sauvegardes protégées"
    Overlay.PersonalLists -> "Mes listes personnelles"
    null -> tab.label
}

private fun iconFor(tab: RootTab) = when (tab) {
    RootTab.SHOWS -> Icons.Default.Tv
    RootTab.MOVIES -> Icons.Default.Movie
    RootTab.GAMES -> Icons.Default.SportsEsports
    RootTab.BOOKS -> Icons.Default.MenuBook
    RootTab.FRANCHISES -> Icons.Default.AccountTree
    RootTab.CALENDAR -> Icons.Default.CalendarMonth
    RootTab.MORE -> Icons.Default.MoreHoriz
}

private fun writeText(context: Context, uri: Uri, text: String) {
    context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(text) }
}

private fun readText(context: Context, uri: Uri): String = context.contentResolver.openInputStream(uri)!!.use { input ->
    com.franchiseguide.app.model.ReleaseRules.readImport(input)
}
