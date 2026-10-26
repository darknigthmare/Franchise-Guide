package com.franchiseguide.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.franchiseguide.app.data.AppRepository
import com.franchiseguide.app.model.MediaItem
import com.franchiseguide.app.model.MediaKind
import com.franchiseguide.app.model.PendingChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Source integrated; requires Android SDK/device verification, not a mock WebView. */
@Composable
fun TraktLibraryScreen(repo:AppRepository,onSettings:()->Unit,onOpen:(MediaItem)->Unit) {
    val scope=rememberCoroutineScope()
    var kindName by rememberSaveable { mutableStateOf("SHOW") }
    var section by rememberSaveable { mutableStateOf("watchlist") }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) };var generation by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(false) };var loading by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf<String?>(null) };var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) };var unmapped by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf(repo.pendingTraktChanges()) }
    var resolve by remember { mutableStateOf<Pair<PendingChange,Boolean>?>(null) }
    var heldDecision by remember { mutableStateOf<Pair<PendingChange,Boolean>?>(null) }
    val kind=MediaKind.valueOf(kindName)
    fun load(more:Boolean=false) {
        if(loading)return
        val request=++generation;val requestedPage=if(more)page+1 else 1
        val selectedKind=kind;val selectedSection=section
        loading=true;error=null
        scope.launch {
            val outcome=repo.readTraktLibrary(selectedKind,selectedSection,requestedPage)
            if(request!=generation)return@launch
            loading=false
            outcome.onSuccess { answer ->
                results=((if(more)results else emptyList())+answer.items).distinctBy { it.id }
                page=answer.page;hasMore=answer.hasMore;unmapped=if(more)unmapped+answer.unmapped else answer.unmapped
            }.onFailure { error=it.message ?: "Lecture Trakt interrompue." }
        }
    }
    fun add(candidate:MediaItem) {
        if(adding!=null)return
        adding=candidate.id;error=null
        scope.launch {
            val result=repo.addCatalogItem(candidate)
            result.onSuccess { item ->
                // Download only: viewing/adding a remote title must not trigger an unsolicited write.
                val synced=withContext(Dispatchers.IO) { repo.syncFromTrakt(uploadLocal=false) }
                note=synced.fold({"Fiche et suivi Trakt récupérés."},{"Fiche ajoutée. Suivi Trakt à reprendre : ${it.message}"})
                adding=null;pending=repo.pendingTraktChanges();onOpen(item)
            }.onFailure { adding=null;error=it.message ?: "Ajout impossible." }
        }
    }
    LaunchedEffect(kindName,section) { generation++;loading=false;results=emptyList();page=0;hasMore=false;unmapped=0;load() }
    DisposableEffect(Unit) { onDispose { generation++ } }
    resolve?.let { (change,applied) ->
        AlertDialog(onDismissRequest={resolve=null},title={Text("Envoi d’historique incertain")},
            text={Text(if(applied) "As-tu vérifié que ce visionnage existe déjà sur Trakt ? Il sera retiré uniquement de la file locale." else "As-tu vérifié que ce visionnage n’existe pas sur Trakt ? Un nouvel essai peut créer un doublon.")},
            confirmButton={TextButton(onClick={runCatching { repo.resolveUncertainChange(change.id,applied) }.onFailure { error=it.message };pending=repo.pendingTraktChanges();resolve=null}) { Text("Confirmer après vérification") }},
            dismissButton={TextButton(onClick={resolve=null}) { Text("Annuler") }})
    }
    heldDecision?.let { (change,resume) ->
        AlertDialog(onDismissRequest={heldDecision=null},title={Text(if(resume) "Reprendre cet ancien envoi ?" else "Annuler cet envoi local ?")},
            text={Text("${change.title} · ${change.field}. " + if(resume) "Cette ancienne action peut modifier Trakt même si le titre ne figure plus dans la bibliothèque. Vérifie ton compte avant de confirmer." else "Seule cette action en attente sera retirée. Ni ta bibliothèque ni Trakt ne seront effacés.")},
            confirmButton={TextButton(onClick={runCatching { repo.resolveHeldChange(change.id,resume) }.onFailure { error=it.message };pending=repo.pendingTraktChanges();heldDecision=null}) { Text("Confirmer") }},
            dismissButton={TextButton(onClick={heldDecision=null}) { Text("Annuler") }})
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(kind==MediaKind.SHOW,{kindName="SHOW"},label={Text("Séries")})
            FilterChip(kind==MediaKind.MOVIE,{kindName="MOVIE"},label={Text("Films")})
            TextButton(onClick=onSettings) { Text("Compte & connexion") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("watchlist" to "À voir","watched" to "Vus","collection" to "Collection").forEach { (key,label) -> FilterChip(section==key,{section=key},label={Text(label)}) }
        }
        OutlinedTextField(query,{query=it},label={Text("Filtrer les pages chargées")},modifier=Modifier.fillMaxWidth().padding(12.dp),singleLine=true)
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it,Modifier.padding(12.dp),color=MaterialTheme.colorScheme.error);TextButton(onClick={load(page>0)},enabled=!loading) { Text("Réessayer la page") } }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=28.dp)) {
            item { Text("Les titres sans correspondance TMDb ne sont pas ajoutés automatiquement. Le bouton + récupère la fiche complète, les saisons et l’affiche.",Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall) }
            note?.let { item { Text(it,Modifier.padding(12.dp)) } }
            if(unmapped>0)item { Text("$unmapped référence(s) sans TMDb non affichée(s).",Modifier.padding(12.dp)) }
            items(results.filter { query.isBlank()||it.title.contains(query,true) },key={it.id}) { remote ->
                val existing=repo.media.firstOrNull { it.added&&it.kind==remote.kind&&it.tmdbId==remote.tmdbId }
                MediaRow(existing?:remote,if(existing!=null) "Déjà dans ma bibliothèque" else if(adding==remote.id) "Chargement de la fiche…" else "Ajouter depuis Trakt",
                    onClick={if(existing!=null)onOpen(existing) else if(adding==null)add(remote)},
                    onPrimaryAction=if(existing!=null||adding!=null)null else {{add(remote)}})
            }
            if(results.isEmpty()&&!loading&&error==null)item { Text("Aucun titre dans cette section.",Modifier.padding(16.dp)) }
            if(hasMore)item { OutlinedButton(onClick={load(true)},enabled=!loading,modifier=Modifier.fillMaxWidth().padding(12.dp)) { Text("Charger la page suivante") } }
            item { HorizontalDivider();Text("File d’envoi · ${pending.size}",Modifier.padding(12.dp),style=MaterialTheme.typography.titleMedium)
                TextButton(onClick={if(!loading)scope.launch { loading=true;val r=withContext(Dispatchers.IO){repo.syncFromTrakt()};loading=false;pending=repo.pendingTraktChanges();r.onSuccess{note=it}.onFailure{error=it.message} }},enabled=!loading&&adding==null) { Text("Synchroniser mon suivi") } }
            items(pending,key={"queue-${it.id}"}) { op ->
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("${op.title} · ${op.type} · TMDb ${op.tmdbId} · ${op.field}")
                    if(op.heldReason!=null) {
                        Text(op.heldReason, color=MaterialTheme.colorScheme.error)
                        TextButton(onClick={heldDecision=op to true}) { Text("Examiner avant envoi") }
                        TextButton(onClick={heldDecision=op to false}) { Text("Annuler cet envoi local") }
                    } else if(op.uncertain) {
                        Text("Accusé de réception incertain : aucun renvoi automatique.",color=MaterialTheme.colorScheme.error)
                        TextButton(onClick={resolve=op to true}) { Text("Déjà appliqué sur Trakt") }
                        TextButton(onClick={resolve=op to false}) { Text("Réessayer après vérification") }
                    }
                }
            }
        }
    }
}
