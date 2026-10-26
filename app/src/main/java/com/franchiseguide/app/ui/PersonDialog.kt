package com.franchiseguide.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.franchiseguide.app.data.AppRepository
import com.franchiseguide.app.model.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Native person details; catalogue selection follows the full-media insertion pathway. */
@Composable
fun PersonDialog(repo: AppRepository, personId: Int, onDismiss: () -> Unit, onOpen: (MediaItem) -> Unit, provider:String="tmdb") {
    val scope=rememberCoroutineScope()
    val uri=LocalUriHandler.current
    var profile by remember(personId,provider) { mutableStateOf<PersonProfile?>(null) }
    var error by remember(personId,provider) { mutableStateOf<String?>(null) }
    var loading by remember(personId,provider) { mutableStateOf(true) }
    var adding by remember(personId,provider) { mutableStateOf(false) }
    var filter by rememberSaveable(personId,provider) { mutableStateOf("") }
    var kind by remember(personId,provider) { mutableStateOf<MediaKind?>(null) }
    fun load() { loading=true;error=null;scope.launch {
        try { profile=if(provider=="tvmaze")withContext(kotlinx.coroutines.Dispatchers.IO){com.franchiseguide.app.network.PublicTvDetailsClient().person(personId)} else repo.personDetails(personId).getOrThrow() } catch(e:kotlinx.coroutines.CancellationException){throw e} catch(e:Exception){error=e.message}
        loading=false
    } }
    LaunchedEffect(personId,provider) { load() }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(modifier=Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(profile?.name ?: "Fiche personne",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    TextButton(onClick=onDismiss) { Text("Fermer") }
                }
                if(loading||adding) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { message -> Row(Modifier.padding(12.dp)) {
                    Text(message,color=MaterialTheme.colorScheme.error,modifier=Modifier.weight(1f))
                    TextButton(onClick={load()},enabled=!loading) { Text("Réessayer") }
                } }
                profile?.let { p ->
                    OutlinedTextField(filter,{filter=it},label={Text("Filtrer la filmographie")},modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp),singleLine=true)
                    Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        FilterChip(kind==null,{kind=null},label={Text("Tout")})
                        if(provider!="tvmaze")FilterChip(kind==MediaKind.MOVIE,{kind=MediaKind.MOVIE},label={Text("Films")})
                        FilterChip(kind==MediaKind.SHOW,{kind=MediaKind.SHOW},label={Text("Séries")})
                    }
                    val works=p.works.filter { (kind==null||it.media.kind==kind) && (it.media.title.contains(filter,true)||it.roles.any{role->role.contains(filter,true)}) }
                    LazyColumn(Modifier.weight(1f)) {
                        item {
                            Column(Modifier.padding(16.dp)) {
                                if(p.profileUrl!=null) AsyncImage(model=p.profileUrl,contentDescription=p.name,modifier=Modifier.size(100.dp,148.dp))
                                Text(listOfNotNull(p.department,p.birthday,p.birthplace,p.deathday?.let {"Décès : $it"}).joinToString(" · "),style=MaterialTheme.typography.bodySmall)
                                if(p.biography.isNotBlank()) Text(p.biography,Modifier.padding(top=12.dp))
                                if(provider=="tvmaze") { Text("Données TVmaze · CC BY-SA · uniquement séries créditées");TextButton(onClick={uri.openUri("https://www.tvmaze.com/people/$personId")}) { Text("Voir la source TVmaze") } }
                                Text("${works.size} œuvre(s)",Modifier.padding(top=12.dp),style=MaterialTheme.typography.titleMedium)
                            }
                        }
                        items(works,key={it.media.id}) { work ->
                            val existing=repo.media.firstOrNull { it.added&&it.kind==work.media.kind&&((work.media.tmdbId!=null&&it.tmdbId==work.media.tmdbId)||(work.media.sourceId!=null&&it.source==work.media.source&&it.sourceId==work.media.sourceId)) }
                            fun choose() { if(adding) return
                                if(existing!=null) { onDismiss();onOpen(existing) }
                                else { adding=true;error=null;scope.launch {
                                    repo.addCatalogItem(work.media).onSuccess { onDismiss();onOpen(it) }.onFailure { error=it.message }
                                    adding=false
                                } }
                            }
                            MediaRow(existing ?: work.media,work.roles.joinToString(" · "),onClick={choose()},onPrimaryAction=if(existing==null&&!adding) {{choose()}} else null)
                        }
                    }
                }
            }
        }
    }
}
