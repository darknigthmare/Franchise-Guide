package com.franchiseguide.app.ui

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.franchiseguide.app.R
import com.franchiseguide.app.data.AppRepository
import com.franchiseguide.app.model.*
import com.franchiseguide.app.network.PublicCatalogClient
import com.franchiseguide.app.network.TraktClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun ShowsScreen(repo: AppRepository, onOpen: (MediaItem) -> Unit, onSearch: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var nextMessage by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("all") }
    val tabs = listOf("Ajoutées", "Historique", "À venir", "Récentes")
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) { tabs.forEachIndexed { i, label -> Tab(tab == i, { tab = i }, text = { Text(label) }) } }
        when (tab) {
            0 -> {
                FilledTonalButton(onClick = onSearch, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Ajouter / rechercher une série") }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("all" to "Toutes", "favorite" to "Favoris", "unwatched" to "À regarder", "continuing" to "En cours").forEach { (id, label) ->
                        FilterChip(filter == id, { filter = id }, label = { Text(label) })
                    }
                }
                LibraryControls(repo,MediaKind.SHOW)
                val shows = repo.shows().filter { show -> when (filter) { "favorite" -> show.favorite; "unwatched" -> repo.remainingCount(show.id) > 0; "continuing" -> show.status == ShowStatus.CONTINUING; else -> true } }
                if (shows.isEmpty()) EmptyState({ Icon(Icons.Default.Tv, null) }, "Aucune série", "Ajoute une série depuis la recherche ou change les filtres.")
                else {
                    val nextOptions=LibraryRules.Query(includeSpecials=repo.settings.value.includeSpecials,hideSpoilers=repo.settings.value.hideEpisodeSpoilers)
                    val nextItems=LibraryRules.nextUp(shows,repo.episodes.toList(),nextOptions).records.associateBy{it.mediaId}
                    nextMessage?.let{Text(it,Modifier.padding(12.dp))}
                    LazyColumn { items(shows, key = { it.id }) { show ->
                        val next=nextItems[show.id]
                        val subtitle=next?.let{ "${it.remaining} restant(s) chargé(s) · "+(if(it.episodeId!=null)it.title.removePrefix(show.title+" · ") else it.code.orEmpty()) }?:"Ouvrir la fiche"
                        MediaRow(show,subtitle,next?.date?.toString(),{onOpen(show)},if(next?.status=="ready"&&next.episodeId!=null)({nextMessage=repo.markNextViewed(show.id,next.episodeId,nextOptions,repo.gamingLibraryEpoch()).fold({it},{it.message})})else null)
                    } }
                }
            }
            1 -> {
                val history = repo.history().filter { it.kind == MediaKind.SHOW }
                if (history.isEmpty()) EmptyState({ Icon(Icons.Default.History, null) }, "Pas encore d'historique", "Les séries avec des épisodes regardés apparaîtront ici.")
                else LazyColumn { items(history, key = { it.id }) { MediaRow(it, "Dernière activité de visionnage", onClick = { onOpen(it) }) } }
            }
            2 -> EpisodeFeed(repo.upcomingEpisodes(), repo, onOpen)
            else -> EpisodeFeed(repo.recentEpisodes(), repo, onOpen)
        }
    }
}

@Composable
private fun EpisodeFeed(feed: List<Episode>, repo: AppRepository, onOpen: (MediaItem) -> Unit) {
    var confirmId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    confirmId?.let { id -> AlertDialog(onDismissRequest={confirmId=null},title={Text("Effacer les visionnages de cet épisode ?")},text={Text("Les dates de lecture connues seront retirées. Une copie locale de sécurité doit réussir. Une suppression peut rejoindre Trakt si le compte est connecté.")},confirmButton={TextButton(onClick={confirmId=null;runCatching{repo.markEpisodeWatched(id,false)}.onFailure{error=it.message}}){Text("Confirmer l’effacement")}},dismissButton={TextButton(onClick={confirmId=null}){Text("Annuler")}}) }
    if (feed.isEmpty()) { EmptyState({ Icon(Icons.Default.Event, null) }, "Rien pour le moment", "Les épisodes datés apparaîtront ici."); return }
    Column {
        error?.let { Text(it,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error) }
        LazyColumn { items(feed, key = { it.id }) { episode ->
            val show = repo.media.firstOrNull { it.id == episode.showId } ?: return@items
            MediaRow(show, EpisodeBatchRules.label(episode,repo.settings.value.hideEpisodeSpoilers)+(if(episode.watched) " · Vu" else ""), episode.airDate?.toString(), { onOpen(show) }, { if(episode.watched)confirmId=episode.id else runCatching{repo.markEpisodeWatched(episode.id,true)}.onFailure{error=it.message} })
        } }
    }
}

@Composable
fun MoviesScreen(repo: AppRepository, onOpen: (MediaItem) -> Unit, onSearch: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Films", "Vus", "À voir", "À venir", "Collection")
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab) { tabs.forEachIndexed { i, label -> Tab(tab == i, { tab = i }, text = { Text(label) }) } }
        val filtered = when (tab) {
            1 -> repo.movies().filter { it.watched }
            2 -> repo.movies().filter { it.watchlist }
            3 -> repo.movies().filter { it.releaseDate?.isAfter(java.time.LocalDate.now()) == true }
            4 -> repo.movies().filter { it.collected }
            else -> repo.movies()
        }
        MediaLibraryList(filtered, MediaKind.MOVIE, onOpen, onSearch, repo)
    }
}

@Composable
fun GamesScreen(repo: AppRepository, onOpen: (MediaItem) -> Unit, onSearch: () -> Unit) = ProgressMediaScreen(repo.games(), MediaKind.GAME, listOf("Jeux", "Parcours terminés", "En cours", "À jouer", "En pause", "Abandonnés"), repo, onOpen, onSearch)

@Composable
fun BooksScreen(repo: AppRepository, onOpen: (MediaItem) -> Unit, onSearch: () -> Unit) = ProgressMediaScreen(repo.books(), MediaKind.BOOK, listOf("Livres", "Parcours lus", "En cours", "À lire", "En pause", "Abandonnés"), repo, onOpen, onSearch)

@Composable
private fun ProgressMediaScreen(sourceItems: List<MediaItem>, kind: MediaKind, tabs: List<String>, repo: AppRepository, onOpen: (MediaItem) -> Unit, onSearch: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab) { tabs.forEachIndexed { i, label -> Tab(tab == i, { tab = i }, text = { Text(label) }) } }
        val filtered = when (tab) {
            1 -> sourceItems.filter { ProgressRules.status(it)=="completed" }
            2 -> sourceItems.filter { it.isInProgress }
            3 -> sourceItems.filter { ProgressRules.status(it)=="planned" }
            4 -> sourceItems.filter { ProgressRules.status(it)=="paused" }
            5 -> sourceItems.filter { ProgressRules.status(it)=="abandoned" }
            else -> sourceItems
        }
        MediaLibraryList(filtered, kind, onOpen, onSearch, repo)
    }
}

@Composable
private fun MediaLibraryList(libraryItems: List<MediaItem>, kind: MediaKind, onOpen: (MediaItem) -> Unit, onSearch: () -> Unit, repo: AppRepository) {
    Column(Modifier.fillMaxSize()) {
        FilledTonalButton(onClick = onSearch, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Ajouter / rechercher ${when (kind) { MediaKind.MOVIE -> "un film"; MediaKind.GAME -> "un jeu"; MediaKind.BOOK -> "un livre"; else -> "un titre" }}") }
        LibraryControls(repo,kind)
        if (libraryItems.isEmpty()) EmptyState({ Icon(mediaKindIcon(kind), null) }, "Aucun ${mediaKindLabel(kind).lowercase()}", "Utilise + Ajouter pour enrichir ta bibliothèque.")
        else LazyColumn(Modifier.weight(1f)) { items(libraryItems, key = { it.id }) { item -> MediaRow(item, statusText(item), onClick = { onOpen(item) }, onPrimaryAction = { if (item.watched) repo.replayMedia(item.id) else repo.markCompleted(item.id, true) }) } }
    }
}

private fun statusText(item: MediaItem): String = when (item.kind) {
    MediaKind.MOVIE -> buildString { append(if (item.watched) "Vu${if (item.plays > 1) " ${item.plays}×" else ""}" else if (item.watchlist) "À voir" else "Non vu"); if (item.collected) append(" · Collection"); item.rating?.let { append(" · $it/10") } }
    MediaKind.GAME,MediaKind.BOOK -> ProgressRules.label(item)+progressLabel(item)+(if(item.watched)" · ${maxOf(1,item.plays)} fin(s) historique(s)" else "")
    MediaKind.SHOW -> item.subtitle
}
private fun progressLabel(item: MediaItem): String = if (item.progressTotal > 0) " · ${item.progressCurrent}/${item.progressTotal}${item.progressUnit.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""}" else ""

@Composable
fun FranchiseScreen(repo:AppRepository,onOpenGroup:(FranchiseGroup)->Unit)=UniverseListScreen(repo,onOpenGroup)
@Composable
fun FranchiseDetailScreen(repo:AppRepository,group:FranchiseGroup,onOpen:(MediaItem)->Unit)=UniverseDetailScreen(repo,group,onOpen)

@Composable
fun MediaDetailScreen(repo: AppRepository, item: MediaItem, onManageEpisodes: (String,Int?,String)->Unit = {_,_,_->}, onAvailability:(String,Int?)->Unit = {_,_->}, onEpisode:(String)->Unit = {}, onOpen: (MediaItem) -> Unit = {}) {
    var selectedPerson by remember(item.id) { mutableStateOf<Int?>(null) }
    var selectedPersonProvider by remember(item.id){mutableStateOf("tmdb")}
    selectedPerson?.let { personId -> PersonDialog(repo,personId,{selectedPerson=null},onOpen,provider=selectedPersonProvider) }
    val current = repo.media.firstOrNull { it.id == item.id } ?: item
    val scope = rememberCoroutineScope(); val uriHandler = LocalUriHandler.current; val context = LocalContext.current
    var progressText by rememberSaveable(current.id, current.progressCurrent) { mutableStateOf(current.progressCurrent.toString()) }
    var totalText by rememberSaveable(current.id, current.progressTotal) { mutableStateOf(if (current.progressTotal > 0) current.progressTotal.toString() else "") }
    var ratingText by rememberSaveable(current.id, current.rating) { mutableStateOf(current.rating?.toString().orEmpty()) }
    var noteText by rememberSaveable(current.id, current.userNote) { mutableStateOf(current.userNote.orEmpty()) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }; var refreshing by remember { mutableStateOf(false) }; var franchiseDialog by remember { mutableStateOf(false) }; var franchiseError by remember { mutableStateOf<String?>(null) }
    var traktComments by remember(current.id) { mutableStateOf<List<CommunityComment>>(emptyList()) }; var commentsLoading by remember(current.id) { mutableStateOf(false) }; var commentsLoaded by remember(current.id) { mutableStateOf(false) }; var commentsError by remember(current.id) { mutableStateOf<String?>(null) }
    var detailTab by remember(current.id) { mutableIntStateOf(0) }
    val seasonProgress = if (current.kind == MediaKind.SHOW) repo.seasonProgress(current.id) else emptyList()
    var expandedSeason by remember(current.id) { mutableStateOf(repo.nextEpisode(current.id)?.season ?: seasonProgress.firstOrNull()?.season) }

    DisposableEffect(current.id, repo.settings.value.detailJingleEnabled) {
        var player: MediaPlayer? = null
        if (repo.settings.value.detailJingleEnabled) {
            player = runCatching { MediaPlayer.create(context, R.raw.detail_signature) }.getOrNull()?.apply {
                val volume=repo.settings.value.detailJingleVolume.coerceIn(0,100)/100f; setVolume(volume,volume)
                setOnCompletionListener { mp -> runCatching { mp.release() }; player = null }
                start()
            }
        }
        onDispose { player?.let { runCatching { it.stop() }; runCatching { it.release() } }; player = null }
    }

    if(franchiseDialog)AlertDialog(onDismissRequest={franchiseDialog=false},title={Text("Classer dans une franchise")},text={LazyColumn(Modifier.heightIn(max=420.dp)){
        item{Text("Un choix manuel, y compris Aucune, reste prioritaire. Aucun suivi ni envoi Trakt n’est modifié.");franchiseError?.let{Text(it,color=MaterialTheme.colorScheme.error)}}
        item{TextButton(onClick={runCatching{repo.assignFranchise(current.id,null)}.onSuccess{franchiseDialog=false}.onFailure{franchiseError=it.message}}){Text("Aucune · choix manuel")}}
        item{TextButton(onClick={runCatching{repo.detectFranchise(current.id)}.onSuccess{franchiseDialog=false}.onFailure{franchiseError=it.message}}){Text("Réactiver la détection par titre")}}
        items(repo.allFranchiseDefinitions(),key={it.id}){def->TextButton(onClick={runCatching{repo.assignFranchise(current.id,def.id)}.onSuccess{franchiseDialog=false}.onFailure{franchiseError=it.message}}){Text(def.name+(if(current.franchiseId==def.id)" · choisi" else ""))}}
    }},confirmButton={TextButton(onClick={franchiseDialog=false}){Text("Fermer")}})

    var confirmation by remember { mutableStateOf<Pair<String,()->Unit>?>(null) }
    confirmation?.let { (text,action) -> AlertDialog(onDismissRequest={confirmation=null},title={Text("Confirmer")},text={Text(text)},confirmButton={TextButton(onClick={confirmation=null;runCatching(action).onFailure{refreshMessage=it.message?:"Action impossible."}}){Text("Confirmer")}},dismissButton={TextButton(onClick={confirmation=null}){Text("Annuler")}}) }
    LazyColumn {
        item { PublicCatalogClient.sourceNotice(current.source)?.let { Text(it,Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall) } }
        if(current.kind==MediaKind.MOVIE&&current.added&&!current.isSuggestion)item { DatedViewingButton(repo,current.id) }
        item {
            Box {
                MediaBackdrop(current)
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 82.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
                    MediaPoster(current, Modifier.width(112.dp).height(164.dp)); Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(current.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        current.tagline?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                        val meta = listOfNotNull(
                            current.year?.toString(), current.contentRating,
                            current.status.takeIf { current.kind == MediaKind.SHOW }?.let { showStatusLabel(it, current.statusRaw) },
                            current.runtimeMinutes?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60}h${(it % 60).toString().padStart(2, '0')}" else "$it min" },
                            current.network
                        ).distinct().joinToString(" · ")
                        if (meta.isNotBlank()) Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp)); AssistChip(onClick = {}, label = { Text(mediaKindLabel(current.kind)) }, leadingIcon = { Icon(mediaKindIcon(current.kind), null) })
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = { repo.toggleFavorite(current.id) }) { Icon(if (current.favorite) Icons.Default.Star else Icons.Default.StarBorder, "Favori", tint = if (current.favorite) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
                    IconButton(onClick = { repo.toggleCollected(current.id) }) { Icon(Icons.Default.Inventory2, "Collection", tint = if (current.collected) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
                    if (current.kind != MediaKind.SHOW) IconButton(onClick = { if (current.watched) repo.replayMedia(current.id) else repo.markCompleted(current.id, true) }) { Icon(if (current.watched) Icons.Default.Replay else Icons.Default.CheckCircleOutline, if (current.watched) "Revoir" else "Terminer") }
                    if (current.kind == MediaKind.MOVIE && current.tmdbId != null && repo.settings.value.traktClientId.isNotBlank()) IconButton(onClick = { scope.launch { refreshMessage = withContext(Dispatchers.IO) { repo.traktCheckInMedia(current.id) }.fold({ it }, { it.message ?: "Check-in Trakt impossible" }) } }) { Icon(Icons.Default.CloudUpload, "Check-in Trakt") }
                    IconButton(onClick = { repo.toggleWatchlist(current.id) }) { Icon(if (current.watchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "À faire", tint = if (current.watchlist) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
                    if (current.kind == MediaKind.SHOW) IconButton(onClick = { repo.toggleNotify(current.id) }) { Icon(if (current.notify) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff, if (current.notify) "Notifications actives" else "Notifications coupées") }
                    IconButton(onClick = { franchiseDialog = true }) { Icon(Icons.Default.AccountTree, "Franchise") }
                    current.trailerUrl?.let { url -> IconButton(onClick = { uriHandler.openUri(url) }) { Icon(Icons.Default.PlayCircle, "Bande-annonce") } }
                    if (current.source in setOf("tmdb", "rawg", "openlibrary")) IconButton(onClick = { refreshing = true; refreshMessage = null; scope.launch { val r = withContext(Dispatchers.IO) { repo.refreshMedia(current.id) }; refreshing = false; refreshMessage = r.fold({ "Fiche mise à jour." }, { it.message ?: "Mise à jour impossible" }) } }) { Icon(Icons.Default.Refresh, "Actualiser") }
                    IconButton(onClick = { if (current.hidden) repo.unhide(current.id) else repo.hide(current.id) }) { Icon(if (current.hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, if (current.hidden) "Réafficher" else "Masquer") }
                }
                if(current.kind in setOf(MediaKind.SHOW,MediaKind.MOVIE)&&current.tmdbId!=null) TextButton(onClick={onAvailability(current.id,null)}){Text("Où regarder · pays et saisons")}
                refreshMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                if (current.kind == MediaKind.SHOW) {
                    val complete = repo.isShowCatalogComplete(current.id)
                    Text(if (complete) "Données série complètes · ${repo.episodes.count { it.showId == current.id }} épisode(s) chargés" else "Données série incomplètes · utilise Actualiser toutes les données", style = MaterialTheme.typography.bodySmall, color = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            }
        }

        item {
            val tabs = if (current.kind == MediaKind.SHOW) listOf("Aperçu", "Saisons", "Détails") else listOf("Aperçu", "Détails")
            ScrollableTabRow(selectedTabIndex = detailTab.coerceIn(0, tabs.lastIndex), edgePadding = 8.dp) {
                tabs.forEachIndexed { index, label -> Tab(detailTab == index, onClick = { detailTab = index }, text = { Text(label) }) }
            }
        }

        if (detailTab == 0) {
            item {
                Column(Modifier.padding(16.dp)) {
                    if (current.overview.isNotBlank()) Text(current.overview, style = MaterialTheme.typography.bodyLarge)
                    if (current.genres.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp)); Text("Genres", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { current.genres.sorted().forEach { genre -> AssistChip(onClick = {}, label = { Text(genre) }) } }
                    }
                    Spacer(Modifier.height(12.dp)); Text("Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(7.dp)); RatingsRow(current) { uriHandler.openUri(it) }
                    if (current.providers.isNotEmpty()) { Spacer(Modifier.height(14.dp)); Text("Streaming / achat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(current.providers.sorted().joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (current.credits.isNotEmpty() || current.people.isNotEmpty()) {
                item { Text("Casting & équipe", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                item {
                    val credits = if (current.credits.isNotEmpty()) current.credits else current.people.mapIndexed { i, name -> PersonCredit("legacy-$i", name) }
                    CreditsCarousel(credits, repo.settings.value.showCastImages) { person ->
                        selectedPersonProvider=if(person.id.startsWith("tvmaze-person-"))"tvmaze" else "tmdb"
                        val id=person.id.removePrefix("tmdb-person-").removePrefix("tvmaze-person-").substringBefore('-').toIntOrNull()
                        if(id!=null) selectedPerson=id else person.externalUrl?.let(uriHandler::openUri)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (current.kind in setOf(MediaKind.SHOW, MediaKind.MOVIE) && repo.settings.value.traktClientId.isNotBlank()) {
                item { TraktReviewsPanel(repo,current) }
            }
            if(current.kind in setOf(MediaKind.SHOW,MediaKind.MOVIE)) item { ContentLanguagePanel(repo,current) }
            item {
                Column(Modifier.padding(16.dp)) {
                    Text("Notes personnelles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(ratingText, { ratingText = it.filter(Char::isDigit).take(2) }, Modifier.width(110.dp), label = { Text("Note /10") }, singleLine = true); Button(onClick = { repo.setRating(current.id, ratingText.toIntOrNull()); repo.setUserNote(current.id, noteText) }) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("Enregistrer") } }
                    Spacer(Modifier.height(6.dp)); OutlinedTextField(noteText, { noteText = it.take(2000) }, Modifier.fillMaxWidth(), label = { Text("Note personnelle") }, minLines = 2, maxLines = 5)
                }
            }
            if(current.kind==MediaKind.BOOK) item { BookEditionsButton(repo,current) }
            if (current.kind in setOf(MediaKind.GAME,MediaKind.BOOK) && current.added && !current.isSuggestion) item { PersonalProgressPanel(repo,current) }
            if (current.kind == MediaKind.GAME) item { GamingAchievementsPanel(repo,current) }
            if (repo.userLists.isNotEmpty()) item {
                Column(Modifier.padding(16.dp)) { Text("Listes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); repo.userLists.forEach { list -> Row(Modifier.fillMaxWidth().clickable { repo.toggleList(current.id, list.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = list.id in current.listIds, onCheckedChange = { repo.toggleList(current.id, list.id) }); Text(list.name) } } }
            }
        }

        if(current.kind==MediaKind.SHOW&&current.added&&!current.isSuggestion)item { FilledTonalButton(onClick={onManageEpisodes(current.id,null,"watch")},modifier=Modifier.fillMaxWidth().padding(16.dp)){Text("Gérer / sélectionner les épisodes")} }
        if (current.kind == MediaKind.SHOW && detailTab == 1) {
            if (seasonProgress.isEmpty()) item { Text("Aucune saison locale. Utilise Actualiser pour charger les saisons et épisodes depuis TMDb.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                val total = seasonProgress.sumOf { it.total }; val watched = seasonProgress.sumOf { it.watched }; val remaining = seasonProgress.sumOf { it.remaining }
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("${seasonProgress.count { it.season > 0 }} saison(s)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); ProgressPill(watched, total) }
                    Text("$remaining épisode(s) restant(s) · ${seasonProgress.sumOf { it.skipped }} ignoré(s) · ${seasonProgress.sumOf { it.collected }} en collection", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            seasonProgress.forEach { sp ->
                item {
                    SeasonProgressCard(
                        sp, expandedSeason == sp.season,
                        onToggle = { expandedSeason = if (expandedSeason == sp.season) null else sp.season },
                        onWatchAll = { onManageEpisodes(current.id,sp.season,if(sp.watched==sp.total)"unwatch" else "watch") },
                        onCollectAll = { onManageEpisodes(current.id,sp.season,if(sp.collected<sp.total)"collect" else "uncollect") },
                        onSkipAll = { onManageEpisodes(current.id,sp.season,if(sp.skipped<sp.total-sp.watched)"skip" else "unskip") }
                    )
                }
                if (expandedSeason == sp.season) {
                    val seasonInfo=current.seasons.firstOrNull { it.number==sp.season }
                    if(current.tmdbId!=null) item { TextButton(onClick={onAvailability(current.id,sp.season)}){Text("Où regarder cette saison")} }
                    if(seasonInfo?.providers?.isNotEmpty()==true) item { Text("Ancien relevé de saison · ${seasonInfo.providers.joinToString(" · ")}\nPays/date non conservés ; JustWatch via TMDb. Vérifie les offres actuelles.",Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall) }
                    val eps = repo.episodes.filter { it.showId == current.id && it.season == sp.season }.sortedBy { it.number }
                    items(eps, key = { it.id }) { episode ->
                        EpisodeRow(
                            episode,
                            repo.settings.value.hideEpisodeSpoilers,
                            { if(episode.watched) confirmation="Effacer les visionnages de cet épisode ? Les dates connues seront retirées ; une suppression peut rejoindre Trakt si le compte est connecté." to { repo.markEpisodeWatched(episode.id,false) } else runCatching{repo.markEpisodeWatched(episode.id,true)}.onFailure{refreshMessage=it.message} },
                            { if(!episode.skipped&&EpisodeBatchRules.hasHistory(episode))refreshMessage="Historique protégé : un épisode vu ne sera pas ignoré." else runCatching{repo.toggleEpisodeSkipped(episode.id)}.onFailure{refreshMessage=it.message} },
                            { runCatching{repo.toggleEpisodeCollected(episode.id)}.onFailure{refreshMessage=it.message} },
                            { runCatching{repo.replayEpisode(episode.id)}.onFailure{refreshMessage=it.message} },
                            if (episode.tmdbId != null && repo.settings.value.traktClientId.isNotBlank()) ({ scope.launch { refreshMessage = withContext(Dispatchers.IO) { repo.traktCheckInEpisode(episode.id) }.fold({ it }, { it.message ?: "Check-in Trakt impossible" }) } }) else null
                        )
                        TextButton(onClick={onEpisode(episode.id)}){Text("Fiche épisode · ma note et distribution")}
                        DatedViewingButton(repo,current.id,episode.id)
                    }
                }
            }
        }

        if ((current.kind == MediaKind.SHOW && detailTab == 2) || (current.kind != MediaKind.SHOW && detailTab == 1)) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Détails", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    DetailFact("Statut", if (current.kind == MediaKind.SHOW) showStatusLabel(current.status, current.statusRaw) else if (current.watched) "Terminé / vu" else "Dans la bibliothèque")
                    current.originalTitle?.let { DetailFact("Titre original", it) }
                    current.network?.let { DetailFact("Chaîne / réseau", it) }
                    current.creator?.let { DetailFact(if (current.kind == MediaKind.BOOK) "Auteur" else "Création / studio", it) }
                    current.publisher?.let { DetailFact("Éditeur", it) }
                    current.contentRating?.let { DetailFact("Classification", it) }
                    current.releaseDate?.let { DetailFact("Première sortie", it.toString()) }
                    current.lastAirDate?.let { DetailFact("Dernière diffusion", it.toString()) }
                    current.nextAirDate?.let { DetailFact("Prochain épisode", it.toString()) }
                    current.releaseSchedule?.let { DetailFact("Diffusion", it) }
                    if (current.kind == MediaKind.SHOW) {
                        current.seasonCount?.let { DetailFact("Saisons", it.toString()) }
                        DetailFact("Épisodes", (current.episodeCount ?: repo.episodes.count { it.showId == current.id }).toString())
                    }
                    current.country?.let { DetailFact("Pays", it) }
                    current.originalLanguage?.let { DetailFact("Langue originale", it) }
                    if (current.productionCompanies.isNotEmpty()) DetailFact("Production", current.productionCompanies.sorted().joinToString(" · "))
                    if (current.platforms.isNotEmpty()) DetailFact("Plateformes", current.platforms.sorted().joinToString(" · "))
                    if (current.providers.isNotEmpty()) DetailFact("Ancien relevé de services · à revérifier", current.providers.sorted().joinToString(" · "))
                    val ids = listOfNotNull(current.tmdbId?.let { "TMDb $it" }, current.tvdbId?.let { "TVDb $it" }, current.imdbId?.let { "IMDb $it" }, current.sourceId?.let { "${current.source}: $it" }).joinToString(" · ")
                    if (ids.isNotBlank()) DetailFact("Identifiants", ids)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        current.externalUrl?.let { url -> AssistChip(onClick = { uriHandler.openUri(url) }, label = { Text("Source") }, leadingIcon = { Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp)) }) }
                        current.homepage?.let { url -> AssistChip(onClick = { uriHandler.openUri(url) }, label = { Text("Site officiel") }, leadingIcon = { Icon(Icons.Default.Language, null, Modifier.size(16.dp)) }) }
                        current.imdbId?.let { imdb -> AssistChip(onClick = { uriHandler.openUri("https://www.imdb.com/title/$imdb/") }, label = { Text("IMDb") }) }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(18.dp)); TextButton(onClick = { repo.remove(current.id) }, modifier = Modifier.padding(16.dp)) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text("Retirer de la bibliothèque") } }
    }
}

@Composable
internal fun TraktCommentCard(comment: CommunityComment) {
    var revealed by rememberSaveable(comment.id) { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.author, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(buildString { if (comment.review) append("Review · "); append("♥ ${comment.likes}") }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(5.dp))
            Text(if (comment.spoiler && !revealed) "Spoiler Trakt masqué." else comment.text, style = MaterialTheme.typography.bodyMedium)
            if(comment.spoiler) TextButton(onClick={revealed=!revealed}) { Text(if(revealed) "Masquer le spoiler" else "Révéler le spoiler") }
            comment.createdAt?.let { Text(it.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun DetailFact(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.bodyLarge) }
}

@Composable
fun SearchScreen(repo: AppRepository, initialKind: MediaKind? = null, onOpen: (MediaItem) -> Unit) {
    val scope = rememberCoroutineScope(); var query by rememberSaveable { mutableStateOf("") }; var kind by remember(initialKind) { mutableStateOf(initialKind) }; var loading by remember { mutableStateOf(false) }; var addingId by remember { mutableStateOf<String?>(null) }; var error by remember { mutableStateOf<String?>(null) }; var results by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) }; var generation by remember { mutableIntStateOf(0) }; var canLoadMore by remember { mutableStateOf(false) }
    fun invalidate() { generation++; loading=false; results=emptyList(); page=0; canLoadMore=false; error=null }
    fun search(more: Boolean = false) {
        if(query.isBlank() || loading) return
        val request=++generation; val nextPage=if(more)page+1 else 1; val submitted=query.trim(); val selected=kind
        loading=true; error=null
        scope.launch {
            val r=withContext(Dispatchers.IO) { repo.searchCatalog(submitted,selected?.let { setOf(it) } ?: MediaKind.entries.toSet(),nextPage) }
            if(request != generation) return@launch
            loading=false
            r.onSuccess { found ->
                val prior=if(more)results else emptyList(); val added=found.filter { f -> prior.none { it.id==f.id } }
                results=(prior+found).distinctBy { it.id };page=nextPage;canLoadMore=added.isNotEmpty() && nextPage < 500
            }.onFailure { error=it.message }
        }
    }
    fun add(result: MediaItem) { if(addingId!=null) return; addingId = result.id; error = null; scope.launch { val r = withContext(Dispatchers.IO) { repo.addCatalogItem(result) }; addingId = null; r.onSuccess { onOpen(it) }.onFailure { error = it.message ?: "Ajout impossible" } } }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(query, { query = it; invalidate() }, Modifier.fillMaxWidth().padding(16.dp), label = { Text(kind?.let { "Rechercher ${mediaKindLabel(it).lowercase()}" } ?: "Série, film, jeu ou livre") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { IconButton(enabled = query.isNotBlank() && !loading, onClick = { search() }) { Icon(Icons.Default.ArrowForward, null) } }, singleLine = true)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(selected = kind == null, onClick = { kind = null; invalidate() }, label = { Text("Tout") }); MediaKind.entries.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k; invalidate() }, label = { Text(when (k) { MediaKind.SHOW -> "Séries"; MediaKind.MOVIE -> "Films"; MediaKind.GAME -> "Jeux"; MediaKind.BOOK -> "Livres" }) }, leadingIcon = { Icon(mediaKindIcon(k), null, Modifier.size(16.dp)) }) } }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth()); error?.let { AssistChip(onClick = { search(page>0) }, label = { Text("Réessayer : $it") }, leadingIcon = { Icon(Icons.Default.Info, null) }, modifier = Modifier.padding(horizontal = 16.dp)) }
        val local = repo.media.filter { query.isNotBlank() && it.added && it.title.contains(query, true) && (kind == null || it.kind == kind) }
        if (results.isEmpty() && !loading && local.isEmpty()) EmptyState({ Icon(Icons.Default.Search, null) }, "Recherche multi-catalogue", "TMDb pour séries/films, RAWG pour jeux et Open Library pour livres. Les clés se règlent dans Réglages.")
        else LazyColumn {
            if (local.isNotEmpty()) { item { Text("Dans ta bibliothèque", Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }; items(local, key = { "local-${it.id}" }) { MediaRow(it, statusText(it), onClick = { onOpen(it) }) } }
            if (results.isNotEmpty()) { item { Text("Catalogue", Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }; items(results, key = { "${it.kind}:${it.source}:${it.sourceId ?: it.id}" }) { result ->
                val existing = repo.media.firstOrNull { c -> c.kind == result.kind && ((result.tmdbId != null && c.tmdbId == result.tmdbId) || (result.sourceId != null && c.source == result.source && c.sourceId == result.sourceId)) && c.added }
                val shown = if (existing != null) result.copy(added = true) else result
                MediaRow(shown, when { existing != null -> "Déjà ajouté"; addingId == result.id -> "Ajout et récupération des détails…"; else -> "Ajouter à la bibliothèque" }, onClick = { if (existing != null) onOpen(existing) else add(result) }, onPrimaryAction = if (existing != null || addingId == result.id) null else {{ add(result) }})
            } }
            if(canLoadMore) item { OutlinedButton(onClick={search(true)},enabled=!loading,modifier=Modifier.fillMaxWidth().padding(16.dp)) { Text("Charger la page suivante") } }
        }
    }
}

@Composable
fun StatisticsScreen(repo:AppRepository)=InsightsPanel(repo)

@Composable
private fun StatsRanking(title: String, rows: List<Pair<String, Int>>) {
    Column { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(7.dp)); rows.forEachIndexed { i, (name, value) -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text("${i + 1}.", Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(name, Modifier.weight(1f)); Text(value.toString(), fontWeight = FontWeight.Bold) } } }
}

@Composable
fun DiscoverScreen(repo: AppRepository, onOpen: (MediaItem) -> Unit) {
    val scope=rememberCoroutineScope()
    var kind by remember { mutableStateOf(MediaKind.SHOW) }
    var all by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) }
    var generation by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    fun load(next:Int) {
        if(loading||next !in 1..DiscoveryRules.MAX_PAGES)return
        val token=++generation
        loading=true;error=null
        scope.launch {
            val r=withContext(Dispatchers.IO) { repo.discoverCatalog(setOf(kind),next) }
            if(token!=generation)return@launch
            loading=false
            r.onSuccess { pageRows ->
                runCatching { DiscoveryRules.accept(if(next==1)emptyList() else all,pageRows,next) }.onSuccess { result ->
                    all=result.items;page=next;more=pageRows.isNotEmpty()&&!result.reachedLimit
                }.onFailure { error=it.message?:"Réponse catalogue invalide." }
            }.onFailure { error=it.message?:"Catalogue indisponible." }
        }
    }
    LaunchedEffect(kind) { generation++;all=emptyList();page=0;more=true;loading=false;load(1) }
    val settings=repo.settings.value
    val visible=runCatching { DiscoveryRules.filtered(all,DiscoveryRules.Query(kind,settings.discoverYearMin,settings.discoverYearMax,settings.discoverLanguage)) }.getOrDefault(all)
        .filter { it.title.contains(query,ignoreCase=true) || it.creator?.contains(query,ignoreCase=true)==true }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            MediaKind.entries.forEach { k -> FilterChip(kind==k,{kind=k},label={Text(mediaKindLabel(k))},leadingIcon={Icon(mediaKindIcon(k),null,Modifier.size(16.dp))}) }
        }
        OutlinedTextField(query,{query=it.take(180)},label={Text("Rechercher parmi les références reçues")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp))
        Text("${visible.size} référence(s) correspondante(s) · ${all.size} reçue(s) · $page page(s) fournisseur consultée(s). Filtres sur les pages reçues uniquement.",Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall)
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text("Page ${page+1} : $it · Les résultats précédents restent disponibles.",Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error) }
        if(kind==MediaKind.MOVIE&&settings.tmdbApiKey.isBlank())Text("Sans TMDb, les films Apple se recherchent par titre depuis Films → + Ajouter ; aucune liste de tendances n'est supposée.",Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall)
        LazyColumn(modifier=Modifier.fillMaxSize()) {
            if(visible.isEmpty()&&!loading)item { Text("Aucune référence parmi les pages reçues. Tu peux poursuivre si une page suivante est disponible.",Modifier.padding(16.dp)) }
            items(visible,key={ "discover:${it.kind}:${it.source}:${it.sourceId}" }) { candidate ->
                val existing=repo.media.firstOrNull { it.added && it.kind==candidate.kind && ((candidate.tmdbId!=null&&it.tmdbId==candidate.tmdbId)||(candidate.sourceId!=null&&it.source==candidate.source&&it.sourceId==candidate.sourceId)) }
                MediaRow(if(existing!=null)candidate.copy(added=true,isSuggestion=false) else candidate,
                    if(existing!=null)"Déjà dans la bibliothèque" else if(candidate.source in PublicCatalogClient.sources)"Catalogue public · sans clé" else "Catalogue disponible",
                    onClick={ if(existing!=null)onOpen(existing) else scope.launch { withContext(Dispatchers.IO) { repo.addCatalogItem(candidate) }.onSuccess(onOpen).onFailure { error=it.message } } })
            }
            if(!loading&&error!=null)item { Button(onClick={load(page+1)},modifier=Modifier.fillMaxWidth().padding(12.dp)) { Text("Réessayer cette page") } }
            else if(!loading&&more)item { OutlinedButton(onClick={load(page+1)},modifier=Modifier.fillMaxWidth().padding(12.dp)) { Text("Lire la page fournisseur suivante") } }
        }
    }
}

@Composable
fun ActivityScreen(repo:AppRepository,onOpen:(MediaItem)->Unit)=DatedInsightsPanel(repo,onOpen)

@Composable
fun LibrarySubsetScreen(repo: AppRepository, mode: String, onOpen: (MediaItem) -> Unit) {
    val items = when (mode) {
        "hidden" -> repo.hiddenItems()
        "collection" -> repo.media.filter { it.added && it.collected }
        "watchlist" -> repo.media.filter { it.added && it.watchlist }
        "favorites" -> repo.media.filter { it.added && it.favorite }
        else -> emptyList()
    }.sortedBy { it.title.lowercase() }
    if (items.isEmpty()) { EmptyState({ Icon(Icons.Default.Inventory2, null) }, "Aucun élément", "Cette vue se remplira selon tes actions sur les fiches."); return }
    LazyColumn { items(items, key = { it.id }) { m -> MediaRow(m, statusText(m), onClick = { onOpen(m) }, onPrimaryAction = if (mode == "hidden") {{ repo.unhide(m.id) }} else null) } }
}

@Composable
fun MoreScreen(
    repo: AppRepository,
    onOpenSettings: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onImportSeriesGuide: () -> Unit,
    onProtected: () -> Unit,
    onOpenList: (UserList) -> Unit,
    onLists: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onToday: () -> Unit,
    onStatistics: () -> Unit,
    onDiscover: () -> Unit,
    onHistory: () -> Unit,
    onSubset: (String) -> Unit,
    onTraktLibrary: () -> Unit,
    onNextUp: () -> Unit,
    onOrganize: () -> Unit,
    onIntegrity: () -> Unit
) {
    var showNewList by remember { mutableStateOf(false) }; var newListName by remember { mutableStateOf("") }; var newListDesc by remember { mutableStateOf("") }; var showAbout by remember { mutableStateOf(false) }
    var showNewFranchise by remember { mutableStateOf(false) }; var franchiseName by remember { mutableStateOf("") }; var aliases by remember { mutableStateOf("") }; var editingFranchise by remember { mutableStateOf<FranchiseDefinition?>(null) }
    val stats = repo.statistics()
    if (showAbout) AlertDialog(onDismissRequest = { showAbout = false }, title = { Text("À propos & crédits") }, text = { Text("FranchiseGuide 0.36.0 est une application indépendante inspirée des workflows de SeriesGuide (GPL-3.0-or-later).\n\nThis product uses the TMDB API but is not endorsed or certified by TMDB.\n\nLes données jeux peuvent provenir de RAWG, les livres d’Open Library et la synchronisation optionnelle de Trakt. Le jingle de fiche est une création originale FranchiseGuide et ne reprend pas l’audio de Plex. Aucune clé ou jeton tiers n'est intégré au projet.") }, confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Fermer") } })
    if (showNewList) PersonalListEditor(repo,null,{showNewList=false}){showNewList=false;onOpenList(it)}
    if(showNewFranchise)UniverseEditor(repo,editingFranchise){showNewFranchise=false;editingFranchise=null}
    var deleteUniverse by remember{mutableStateOf<String?>(null)};var universeError by remember{mutableStateOf<String?>(null)}
    deleteUniverse?.let{id->AlertDialog(onDismissRequest={deleteUniverse=null},title={Text("Supprimer ce classement ?")},text={Column{Text("Titres, épisodes, historiques, notes et suggestions sont conservés. Une copie locale doit réussir avant suppression du classement.");universeError?.let{Text(it)}}},confirmButton={TextButton(onClick={runCatching{repo.deleteCustomFranchise(id)}.onSuccess{deleteUniverse=null}.onFailure{universeError=it.message}}){Text("Supprimer le classement")}},dismissButton={TextButton(onClick={deleteUniverse=null}){Text("Annuler")}})}


    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { OutlinedButton(onClick=onProtected,modifier=Modifier.fillMaxWidth().padding(16.dp)){Text("Sauvegarde protégée · exporter / importer")} }
        item { ElevatedCard(Modifier.fillMaxWidth()) { Column {
            ListItem(headlineContent={Text("Aujourd’hui")},supportingContent={Text("Agenda, prochain titre, check-in Trakt et sauvegarde externe")},leadingContent={Icon(Icons.Default.Today,null)},modifier=Modifier.clickable(onClick=onToday))
            ListItem(headlineContent={Text("À regarder / reprendre")},supportingContent={Text("Le prochain épisode et les titres à poursuivre")},modifier=Modifier.clickable(onClick=onNextUp))
            ListItem(headlineContent={Text("Organiser ma bibliothèque")},supportingContent={Text("Sélection, favoris, masqués, listes et annulation")},modifier=Modifier.clickable(onClick=onOrganize))
        } } }
        item { Column(Modifier.padding(16.dp)) { Text("Tableau de bord", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { StatCard("${stats.mediaTotal}", "éléments", Modifier.weight(1f)); StatCard("${stats.episodesWatched}", "épisodes vus", Modifier.weight(1f)); StatCard("${stats.moviesWatched}", "films vus", Modifier.weight(1f)) }; Spacer(Modifier.height(10.dp)); FilledTonalButton(onClick = onStatistics, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.BarChart, null); Spacer(Modifier.width(8.dp)); Text("Ouvrir les statistiques détaillées") } } }
        item { Text("Explorer", Modifier.padding(16.dp, 10.dp, 16.dp, 4.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { ListItem(headlineContent = { Text("Découvrir") }, supportingContent = { Text("Tendances TMDb, RAWG et suggestions de livres") }, leadingContent = { Icon(Icons.Default.Explore, null) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }, modifier = Modifier.clickable(onClick = onDiscover)); ListItem(headlineContent = { Text("Historique") }, supportingContent = { Text("Épisodes, films, jeux et livres, avec revisionnages") }, leadingContent = { Icon(Icons.Default.History, null) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }, modifier = Modifier.clickable(onClick = onHistory)); ListItem(headlineContent = { Text("Collection") }, supportingContent = { Text("${stats.collected} éléments/épisodes collectés") }, leadingContent = { Icon(Icons.Default.Inventory2, null) }, modifier = Modifier.clickable { onSubset("collection") }); ListItem(headlineContent = { Text("À voir / lire / jouer") }, supportingContent = { Text("${stats.watchlist} éléments") }, leadingContent = { Icon(Icons.Default.Bookmark, null) }, modifier = Modifier.clickable { onSubset("watchlist") }); ListItem(headlineContent = { Text("Favoris") }, supportingContent = { Text("${stats.favorites} éléments") }, leadingContent = { Icon(Icons.Default.Star, null) }, modifier = Modifier.clickable { onSubset("favorites") }); ListItem(headlineContent = { Text("Éléments masqués") }, supportingContent = { Text("${repo.hiddenItems().size} élément(s) masqué(s)") }, leadingContent = { Icon(Icons.Default.VisibilityOff, null) }, modifier = Modifier.clickable { onSubset("hidden") }) }
        item { Text("Listes", Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); TextButton(onClick = { showNewList = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Icon(Icons.Default.Add, null); Text("Nouvelle liste") }; TextButton(onClick=onLists){Text("Gérer toutes mes listes")} }
        items(repo.userLists, key = { it.id }) { list -> ListItem(headlineContent = { Text(list.name) }, supportingContent = { Text("${repo.listItems(list).size} élément(s)${list.description.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}") }, leadingContent = { Icon(Icons.Default.PlaylistPlay, null) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }, modifier = Modifier.clickable { onOpenList(list) }) }
        item { Text("Franchises personnalisées", Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); TextButton(onClick = { editingFranchise = null; franchiseName = ""; aliases = ""; showNewFranchise = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Icon(Icons.Default.Add, null); Text("Nouvelle franchise") } }
        items(repo.customFranchises, key = { it.id }) { f -> ListItem(headlineContent = { Text(f.name) }, supportingContent = { Text((f.aliases + f.queryTerms).distinct().joinToString(" · ")) }, leadingContent = { Icon(Icons.Default.AccountTree, null) }, trailingContent = { Row { IconButton(onClick = { editingFranchise = f; franchiseName = f.name; aliases = f.aliases.joinToString(", "); showNewFranchise = true }) { Icon(Icons.Default.Edit, "Modifier") }; IconButton(onClick = { deleteUniverse=f.id;universeError=null }) { Icon(Icons.Default.DeleteOutline, "Supprimer") } } }) }
        item { Text("Données & application", Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); ListItem(headlineContent = { Text("Exporter FranchiseGuide") }, supportingContent = { Text("Sauvegarde V11 inter-plateformes, sans secrets") }, leadingContent = { Icon(Icons.Default.FileUpload, null) }, modifier = Modifier.clickable(onClick = onExport)); ListItem(headlineContent = { Text("Importer FranchiseGuide") }, supportingContent = { Text("Compatible sauvegardes V1–V11 et Web") }, leadingContent = { Icon(Icons.Default.FileDownload, null) }, modifier = Modifier.clickable(onClick = onImport)); ListItem(headlineContent = { Text("Importer depuis SeriesGuide") }, supportingContent = { Text("Séries, films et listes JSON ; import transactionnel") }, leadingContent = { Icon(Icons.Default.MoveToInbox, null) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }, modifier = Modifier.clickable(onClick = onImportSeriesGuide)); ListItem(headlineContent = { Text("Bibliothèque Trakt") }, supportingContent = { Text("Titres vus, à voir, collection et file d’envoi") }, leadingContent = { Icon(Icons.Default.Sync, null) }, modifier = Modifier.clickable(onClick = onTraktLibrary)); ListItem(headlineContent = { Text("Intégrité des données") }, supportingContent = { Text("Doublons, épisodes orphelins et références de listes") }, leadingContent = { Icon(Icons.Default.BuildCircle, null) }, modifier = Modifier.clickable(onClick = onIntegrity)); ListItem(headlineContent = { Text("Réglages et comptes") }, supportingContent = { Text("TMDb, Trakt, RAWG, jingle, spoilers, notifications") }, leadingContent = { Icon(Icons.Default.Settings, null) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }, modifier = Modifier.clickable(onClick = onOpenSettings)); ListItem(headlineContent = { Text("À propos & crédits") }, supportingContent = { Text("SeriesGuide, TMDb, RAWG, Open Library, Trakt et jingle original") }, leadingContent = { Icon(Icons.Default.Info, null) }, modifier = Modifier.clickable { showAbout = true }) }
    }
}


@Composable
fun IntegrityScreen(repo: AppRepository) {
    var report by remember { mutableStateOf(repo.integrityReport()) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text("Intégrité des données", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Analyse locale. Les réparations automatiques sont limitées aux références sans ambiguïté ; aucune fusion de doublons n'est effectuée sans contrôle manuel.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("${report.criticalCount}", "critiques", Modifier.weight(1f))
                    StatCard("${report.warningCount}", "alertes", Modifier.weight(1f))
                    StatCard("${report.repairableCount}", "réparables", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = { report = repo.repairIntegritySafely() }, enabled = report.repairableCount > 0, modifier = Modifier.fillMaxWidth()) { Text("Réparer les références sûres") }
            }
        }
        if (report.issues.isEmpty()) item { Text("Aucun problème structurel détecté.", Modifier.padding(16.dp)) }
        items(report.issues) { issue ->
            ListItem(
                headlineContent = { Text(issue.code) },
                supportingContent = { Text(issue.message) },
                leadingContent = { Icon(if (issue.severity == "critical") Icons.Default.ErrorOutline else Icons.Default.WarningAmber, null) }
            )
        }
        if (report.duplicateCandidates.isNotEmpty()) {
            item { Text("Doublons candidats", Modifier.padding(16.dp, 20.dp, 16.dp, 6.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(report.duplicateCandidates) { d ->
                ListItem(
                    headlineContent = { Text(d.title) },
                    supportingContent = { Text(if (d.safeToMerge) "Identifiants compatibles · fusion manuelle disponible sur Web/PC" else "Fusion bloquée · ${d.conflicts.joinToString()}") },
                    leadingContent = { Icon(Icons.Default.MergeType, null) }
                )
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) { ElevatedCard(modifier) { Column(Modifier.padding(12.dp)) { Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }

@Composable
fun UserListScreen(repo:AppRepository,list:UserList,onOpen:(MediaItem)->Unit,onDeleted:()->Unit)=PersonalListScreen(repo,list,onOpen,onDeleted)

@Composable
fun SettingsScreen(repo: AppRepository,onImported:()->Unit={}) {
    var advancedServices by remember { mutableStateOf(false) }
    var backupReview by remember { mutableStateOf<PreparedLibraryImport?>(null) }
    backupReview?.let { review->ImportReviewPanel(repo,review,onClose={backupReview=null},onApplied={backupReview=null;onImported()}) }
    val scope = rememberCoroutineScope(); val initial = repo.settings.value; val uriHandler = LocalUriHandler.current
    var tmdb by remember { mutableStateOf(initial.tmdbApiKey) }; var rawg by remember { mutableStateOf(initial.rawgApiKey) }; var traktId by remember { mutableStateOf(initial.traktClientId) }; var traktSecret by remember { mutableStateOf(initial.traktClientSecret) }; var language by remember { mutableStateOf(initial.language) }; var region by remember { mutableStateOf(initial.region) }; var dark by remember { mutableStateOf(initial.darkTheme) }; var specials by remember { mutableStateOf(initial.includeSpecials) }; var recentDays by remember { mutableStateOf(initial.recentDays.toString()) }; var upcomingDays by remember { mutableStateOf(initial.upcomingDays.toString()) }; var hiddenCalendar by remember { mutableStateOf(initial.includeHiddenInCalendar) }; var notifications by remember { mutableStateOf(initial.notificationsEnabled) }; var notificationHour by remember { mutableStateOf(initial.notificationHour.toString()) }; var jingle by remember { mutableStateOf(initial.detailJingleEnabled) }; var spoilers by remember { mutableStateOf(initial.hideEpisodeSpoilers) }; var castImages by remember { mutableStateOf(initial.showCastImages) }; var rewatchStats by remember { mutableStateOf(initial.statisticsIncludeRewatches) }; var traktAutoSync by remember { mutableStateOf(initial.traktAutoSync) }; var traktHours by remember { mutableStateOf(initial.traktSyncIntervalHours.toString()) }; var devicePrompt by remember { mutableStateOf<TraktClient.DeviceCode?>(null) }; var message by remember { mutableStateOf<String?>(repo.syncMessage.value) }; var busy by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(initial.detailJingleVolume.toFloat()) }
    var backupsEnabled by remember { mutableStateOf(initial.autoBackupEnabled) }
    var backupMinutes by remember { mutableStateOf(initial.autoBackupMinutes.toString()) }
    var confirm by remember { mutableStateOf<Pair<String,()->Unit>?>(null) }
    var showDiagnostics by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { repo.cancelTraktAuth() } }
    fun current() = repo.settings.value.copy(tmdbApiKey=tmdb.trim(),traktClientId=traktId.trim(),traktClientSecret=traktSecret.trim(),rawgApiKey=rawg.trim(),language=language.trim(),region=region.trim(),darkTheme=dark,includeSpecials=specials,recentDays=SettingsRules.number(recentDays,"Jours récents",0,36500),upcomingDays=SettingsRules.number(upcomingDays,"Jours à venir",0,36500),includeHiddenInCalendar=hiddenCalendar,notificationsEnabled=notifications,notificationHour=SettingsRules.number(notificationHour,"Heure",0,23),detailJingleEnabled=jingle,hideEpisodeSpoilers=spoilers,showCastImages=castImages,statisticsIncludeRewatches=rewatchStats,traktAutoSync=traktAutoSync,traktSyncIntervalHours=SettingsRules.number(traktHours,"Intervalle Trakt",1,168),detailJingleVolume=volume.toInt(),autoBackupEnabled=backupsEnabled,autoBackupMinutes=SettingsRules.number(backupMinutes,"Sauvegardes",1,1440))
    confirm?.let { (text,action)-> AlertDialog(onDismissRequest={confirm=null},title={Text("Confirmer")},text={Text(text)},confirmButton={TextButton(onClick={confirm=null;action()}){Text("Confirmer")}},dismissButton={TextButton(onClick={confirm=null}){Text("Annuler")}}) }
    if(showDiagnostics) AlertDialog(onDismissRequest={showDiagnostics=false},title={Text("Diagnostic local")},text={LazyColumn(Modifier.heightIn(max=420.dp)){item{Text(repo.diagnosticsText(),style=MaterialTheme.typography.bodySmall)}}},confirmButton={TextButton(onClick={showDiagnostics=false}){Text("Fermer")}})

    fun saveForm():Boolean=runCatching { repo.updateSettings(current());true }.getOrElse { message=it.message?:"Réglages non enregistrés.";false }
    fun task(block: suspend () -> Result<String>) { if(!saveForm())return; busy = true; scope.launch { message = withContext(Dispatchers.IO) { block() }.fold({ it }, { it.message ?: "Erreur" }); busy = false } }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Catalogues automatiques", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { Text("Sans clé : TVmaze pour les séries, Open Library pour les livres, FreeToGame pour les jeux gratuits PC et navigateur, Apple/iTunes pour une recherche de films limitée sans copie des affiches promotionnelles. Les données restent attribuées à leur fournisseur.") }
        item { TextButton(onClick={advancedServices=!advancedServices}) { Text(if(advancedServices)"Masquer les accès avancés" else "Options avancées · accès éditeur / personnels") } }
        if(advancedServices) item { OutlinedTextField(tmdb, { tmdb = it }, Modifier.fillMaxWidth(), label = { Text("Clé API TMDb · séries et films") }, singleLine = true) }
        if(advancedServices) item { OutlinedTextField(rawg, { rawg = it }, Modifier.fillMaxWidth(), label = { Text("Clé API RAWG · jeux vidéo") }, singleLine = true) }
        item { Text("Open Library est utilisé pour les livres sans clé API. Les clés restent hors des sauvegardes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { OutlinedButton(enabled = !busy && repo.needsCatalogRefresh(), onClick = { task { repo.refreshIncompleteCatalog() } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.ImageSearch, null); Spacer(Modifier.width(8.dp)); Text("Compléter miniatures et fiches existantes") } }
        item { Text("Trakt", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if(advancedServices) item { OutlinedTextField(traktId, { traktId = it }, Modifier.fillMaxWidth(), label = { Text("Client ID Trakt") }, singleLine = true) }
        if(advancedServices) item { OutlinedTextField(traktSecret, { traktSecret = it }, Modifier.fillMaxWidth(), label = { Text("Client secret Trakt") }, singleLine = true, visualTransformation = PasswordVisualTransformation()) }
        item { Text(if (repo.isTraktConnected()) "Connecté : ${repo.traktAccountLabel() ?: "compte Trakt"}" else "Compte Trakt non connecté", style = MaterialTheme.typography.bodyMedium, color = if (repo.isTraktConnected()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(enabled = !busy, onClick = {
                if(!saveForm())return@FilledTonalButton
                if(traktId.isBlank()||traktSecret.isBlank()){message="Connexion Trakt non activée dans cette construction Android. Accès d’application à configurer par l’éditeur ; les catalogues publics restent utilisables.";return@FilledTonalButton}
                busy = true
                scope.launch {
                    val start = withContext(Dispatchers.IO) { repo.beginTraktDeviceAuth() }
                    start.onSuccess { code ->
                        devicePrompt = code; message = "Code Trakt : ${code.userCode}. Autorise FranchiseGuide dans la page Trakt ouverte."
                        runCatching { uriHandler.openUri(code.verificationUrl) }.onFailure { message="Ouvre manuellement : ${code.verificationUrl}" }
                        val connected = withContext(Dispatchers.IO) { repo.awaitTraktDeviceAuth(code) }
                        message = connected.fold({ it }, { it.message ?: "Connexion Trakt impossible" })
                    }.onFailure { message = it.message ?: "Connexion Trakt impossible" }
                    busy = false
                }
            }) { Icon(Icons.Default.AccountCircle, null); Spacer(Modifier.width(6.dp)); Text(if (repo.isTraktConnected()) "Reconnecter" else "Se connecter à Trakt") }
            OutlinedButton(enabled = !busy && repo.isTraktConnected(), onClick = { task { repo.syncFromTrakt(true) } }) { Icon(Icons.Default.Sync, null); Spacer(Modifier.width(4.dp)); Text("Synchroniser") }
            OutlinedButton(enabled = !busy && repo.isTraktConnected(), onClick = { task { repo.uploadToTrakt() } }) { Text("Envoyer local") }
            if (repo.isTraktConnected()) TextButton(onClick = { repo.disconnectTrakt(); message = repo.syncMessage.value }) { Text("Déconnecter") }
        } }
        item { devicePrompt?.let { code -> ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("Code d’activation", fontWeight = FontWeight.Bold); Text(code.userCode, style = MaterialTheme.typography.headlineMedium); Text(code.verificationUrl, color = MaterialTheme.colorScheme.primary); Text("FranchiseGuide vérifie automatiquement l’autorisation toutes les ${code.interval}s.", style = MaterialTheme.typography.bodySmall) } } } }
        if(busy && devicePrompt!=null) item { TextButton(onClick={repo.cancelTraktAuth();devicePrompt=null;busy=false;message="Connexion annulée."}){Text("Annuler la connexion") } }
        item { Text("${repo.pendingTraktChanges().size} modification(s) en attente. Une première connexion récupère Trakt sans renvoyer tout l’historique local.",style=MaterialTheme.typography.bodySmall) }
        items(repo.pendingTraktChanges().filter { it.uncertain },key={it.id}) { op ->
            ElevatedCard { Column(Modifier.padding(12.dp)) {
                Text("Envoi incertain · ${op.title}",fontWeight=FontWeight.Bold)
                Text("Vérifie ce visionnage dans Trakt avant de choisir.")
                TextButton(onClick={confirm="Ce visionnage existe déjà sur Trakt ?" to {repo.resolveUncertainChange(op.id,true);message="Envoi confirmé manuellement."}}){Text("Déjà présent sur Trakt")}
                TextButton(onClick={confirm="Confirme que ce visionnage est absent de Trakt. Le renvoyer peut créer un doublon." to {repo.resolveUncertainChange(op.id,false);message="Opération prête à être renvoyée."}}){Text("Absent : réessayer")}
            } }
        }
        item { SettingSwitch("Synchronisation Trakt automatique", traktAutoSync) { traktAutoSync = it } }
        item { OutlinedTextField(traktHours, { traktHours = it.take(8) }, Modifier.fillMaxWidth(), label = { Text("Intervalle de synchro Trakt (heures)") }, singleLine = true, enabled = traktAutoSync) }
        item { message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) } }
        item { Text("Expérience", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { SettingSwitch("Jingle original à l’ouverture d’une fiche", jingle) { jingle = it } }
        item { Text("Volume · ${volume.toInt()} %"); Slider(value=volume,onValueChange={volume=it},valueRange=0f..100f,enabled=jingle) }
        item { SettingSwitch("Masquer miniatures/synopsis des épisodes non vus", spoilers) { spoilers = it } }
        item { SettingSwitch("Afficher les miniatures du casting", castImages) { castImages = it } }
        item { SettingSwitch("Inclure les revisionnages dans le temps des statistiques", rewatchStats) { rewatchStats = it } }
        item { SettingSwitch("Thème sombre", dark) { dark = it } }
        item { Text("Calendrier et épisodes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(language, { language = it }, Modifier.weight(1f), label = { Text("Langue") }, singleLine = true); OutlinedTextField(region, { region = it }, Modifier.weight(1f), label = { Text("Région") }, singleLine = true) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(recentDays, { recentDays = it.take(8) }, Modifier.weight(1f), label = { Text("Jours récents · 0 = illimité") }, singleLine = true); OutlinedTextField(upcomingDays, { upcomingDays = it.take(8) }, Modifier.weight(1f), label = { Text("Jours à venir · 0 = illimité") }, singleLine = true) } }
        item { SettingSwitch("Inclure les épisodes spéciaux", specials) { specials = it } }
        item { SettingSwitch("Inclure les séries masquées dans l'agenda", hiddenCalendar) { hiddenCalendar = it } }
        item { SettingSwitch("Notifications des nouveaux épisodes", notifications) { notifications = it } }
        item { OutlinedTextField(notificationHour, { notificationHour = it.take(8) }, Modifier.fillMaxWidth(), label = { Text("Heure des notifications (0–23)") }, singleLine = true, enabled = notifications) }
        item { Text("Sauvegardes locales",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold) }
        item { SettingSwitch("Snapshots automatiques (5 conservés)",backupsEnabled){backupsEnabled=it} }
        item { OutlinedTextField(backupMinutes,{backupMinutes=it.filter(Char::isDigit).take(4)},label={Text("Intervalle minimum en minutes")},modifier=Modifier.fillMaxWidth()) }
        item { OutlinedButton(onClick={message=repo.createLocalBackup().fold({"Sauvegarde créée."},{it.message})}){Text("Créer une sauvegarde maintenant")} }
        items(repo.localBackupSummaries(),key={it.first}) { (i,label)-> TextButton(enabled=!busy,onClick={busy=true;scope.launch { val r=withContext(Dispatchers.Default){repo.prepareLocalBackupImport(i)};busy=false;r.onSuccess{backupReview=it}.onFailure{message=it.message} }}){Text(label)} }
        item { OutlinedButton(onClick={showDiagnostics=true}){Text("Afficher le diagnostic local")} }
        item { Button(onClick = { if(saveForm())message = "Réglages enregistrés." }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Enregistrer") } }
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f)); Switch(checked, onChange) } }


@Composable
private fun LibraryControls(repo:AppRepository,kind:MediaKind) {
    var menu by remember { mutableStateOf(false) }
    val pref=repo.settings.value.librarySort[kind.name.lowercase()] ?: LibrarySortPreference()
    fun label(m:SortMode)=when(m){SortMode.TITLE->"Titre";SortMode.YEAR->"Année";SortMode.ADDED->"Date d’ajout";SortMode.RECENTLY_WATCHED->"Activité";SortMode.PROGRESS->"Progression";SortMode.NEXT_EPISODE->"Prochain épisode";SortMode.STATUS->"Statut";SortMode.REMAINING->"Restants";SortMode.RATING->"Note"}
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Box { AssistChip(onClick={menu=true},label={Text("Trier : ${label(pref.mode)}")});DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
            SortMode.entries.forEach { mode->DropdownMenuItem(text={Text(label(mode))},onClick={repo.setLibrarySort(kind,mode,pref.ascending);menu=false}) }
        } }
        AssistChip(onClick={repo.setLibrarySort(kind,pref.mode,!pref.ascending)},label={Text(if(pref.ascending) "Croissant ↑" else "Décroissant ↓")})
    }
    if(kind==MediaKind.SHOW) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            ShowStatus.entries.forEach { st->val flag=repo.settings.value.statusFilters[st.name] ?: 0
                AssistChip(onClick={repo.cycleStatusFilter(st)},label={Text("${when(flag){1->"+ ";-1->"− ";else->""}}${showStatusLabel(st)}")})
            }
            TextButton(onClick={repo.clearStatusFilters()}){Text("Réinitialiser")}
        }
        Text("Statuts : toucher pour inclure, retoucher pour exclure.",Modifier.padding(horizontal=16.dp),style=MaterialTheme.typography.bodySmall)
    }
}
