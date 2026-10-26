package com.franchiseguide.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.franchiseguide.app.data.AppRepository
import com.franchiseguide.app.model.*
import com.franchiseguide.app.network.EpisodeDetailsClient
import com.franchiseguide.app.network.PublicTvDetailsClient
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Episode UI source. Real Compose/SDK and phone execution remain separate release gates. */
@Composable
fun EpisodeSheetScreen(repo:AppRepository,showId:String,id:String,onClose:()->Unit,onNavigate:(String)->Unit,
    onManage:()->Unit,onAvailability:(Int)->Unit,onOpenMedia:(MediaItem)->Unit,onBackBinding:((()->Unit)?)->Unit) {
    val scope=rememberCoroutineScope();val client=remember{EpisodeDetailsClient()};val publicClient=remember{PublicTvDetailsClient()};val uri=LocalUriHandler.current
    val epoch=remember(showId,id){repo.gamingLibraryEpoch()};val initial=remember(showId,id){runCatching{EpisodeDetailRules.locate(EpisodeDetailRules.State(repo.media.toList(),repo.episodes.toList()),showId,id)}.getOrNull()}
    if(initial==null){Text("Épisode absent ou ambigu. Rouvre sa fiche.",Modifier.padding(20.dp));return}
    val (initialShow,initialEpisode)=initial
    val signature=rememberSaveable(showId,id){EpisodeDetailRules.identity(initialShow,initialEpisode)}
    var baseRating by rememberSaveable(showId,id){mutableStateOf(initialEpisode.rating?.toString().orEmpty())}
    var baseNote by rememberSaveable(showId,id){mutableStateOf(initialEpisode.userNote.orEmpty())}
    var draftRating by rememberSaveable(showId,id){mutableStateOf(baseRating)};var draftNote by rememberSaveable(showId,id){mutableStateOf(baseNote)}
    var openedAt by rememberSaveable(showId,id){mutableLongStateOf(System.currentTimeMillis())};var account by remember(showId,id){mutableStateOf(repo.episodeBatchAccount())}
    var revealed by rememberSaveable(showId,id){mutableStateOf(false)};var group by remember(showId,id){mutableStateOf("all")};var query by remember(showId,id){mutableStateOf("")};var limit by remember(showId,id){mutableIntStateOf(60)}
    var error by remember(showId,id){mutableStateOf<String?>(null)};var personalError by remember(showId,id){mutableStateOf<String?>(null)};var message by remember(showId,id){mutableStateOf("Fiche locale. Lecture du fournisseur uniquement à ta demande.")}
    var loading by remember{mutableStateOf(false)};var job by remember{mutableStateOf<Job?>(null)};var serial by remember{mutableIntStateOf(0)}
    var pendingExit by remember{mutableStateOf<(() -> Unit)?>(null)};var person by remember{mutableStateOf<Int?>(null)}
    val dirty=draftRating!=baseRating||draftNote!=baseNote
    fun stop(){serial++;job?.cancel();job=null;loading=false}
    fun leave(action:()->Unit){if(dirty)pendingExit=action else {stop();action()}}
    val backAction by rememberUpdatedState<()->Unit>({leave(onClose)})
    DisposableEffect(showId,id){onBackBinding{backAction()};onDispose{job?.cancel();onBackBinding(null)}}
    BackHandler{backAction()}
    pendingExit?.let { action->AlertDialog(onDismissRequest={pendingExit=null},title={Text("Abandonner le brouillon ?")},text={Text("La note ou le texte privé n’est pas enregistré. Aucun changement ne sera appliqué si tu quittes.")},confirmButton={TextButton(onClick={pendingExit=null;stop();action()}){Text("Quitter sans enregistrer")}},dismissButton={TextButton(onClick={pendingExit=null}){Text("Rester")}}) }
    person?.let { personId->PersonDialog(repo,personId,{person=null},onOpen={ media->person=null;leave{onOpenMedia(media)} },provider=if(initialShow.source=="tvmaze")"tvmaze" else "tmdb") }
    val state=EpisodeDetailRules.State(repo.media.toList(),repo.episodes.toList());val current=runCatching{EpisodeDetailRules.locate(state,showId,id)}.getOrNull()
    if(current==null||repo.gamingLibraryEpoch()!=epoch||EpisodeDetailRules.identity(current.first,current.second)!=signature){Text("La bibliothèque ou l’identité de cet épisode a changé. Rouvre sa fiche.",Modifier.padding(20.dp));return}
    val(m,e)=current;val isPublic=m.source=="tvmaze";val key=if(isPublic)"public-tvmaze" else repo.settings.value.tmdbApiKey;val q=runCatching{if(isPublic)PublicTvDetailRules.query(m,e) else EpisodeDetailRules.query(m,e,m.contentLanguage?:repo.settings.value.language)}.getOrNull()
    val cache=remember(showId,id,key){mutableStateMapOf<String,EpisodeDetailSnapshot>()};val snapshot=q?.let{cache[it.key]};val masked=repo.settings.value.hideEpisodeSpoilers&&!e.watched&&!revealed
    val neighbors=EpisodeDetailRules.neighbors(state,showId,id,repo.settings.value.includeSpecials)
    fun valid(token:Int,expected:EpisodeDetailQuery,expectedKey:String):Boolean {
        val live=runCatching{EpisodeDetailRules.locate(EpisodeDetailRules.State(repo.media.toList(),repo.episodes.toList()),showId,id)}.getOrNull()?:return false
        val queryNow=runCatching{if(live.first.source=="tvmaze")PublicTvDetailRules.query(live.first,live.second) else EpisodeDetailRules.query(live.first,live.second,live.first.contentLanguage?:repo.settings.value.language)}.getOrNull()
        return token==serial&&epoch==repo.gamingLibraryEpoch()&&(expected.provider=="tvmaze"||expectedKey==repo.settings.value.tmdbApiKey)&&expected==queryNow
    }
    fun read(force:Boolean=false){
        if(loading)return
        val expected=q;if(expected==null){error="Identifiant TMDb ou numérotation inconnue. Actualise la série pour identifier cet épisode.";return}
        if(key.isBlank()){error="Configure la clé TMDb. Les champs personnels restent éditables sans réseau.";return}
        val cached=cache[expected.key];if(!force&&cached!=null&&System.currentTimeMillis()-cached.observedAt in 0L until 300000L){error=null;message="Dernière consultation réutilisée sans changer sa date.";return}
        serial++;val token=serial;error=null;loading=true;message="Lecture des détails et de la distribution de cet épisode…"
        job=scope.launch {try{val result=withContext(Dispatchers.IO){if(expected.provider=="tvmaze")publicClient.episode(expected) else client.details(expected,key)};if(valid(token,expected,key)){cache[expected.key]=result;while(cache.size>20)cache.remove(cache.keys.first());message="Détails en mémoire seulement : aucune modification de l’historique ou des métadonnées enregistrées."}}
            catch(ex:CancellationException){throw ex}catch(ex:Exception){if(valid(token,expected,key)){error=(ex.message?:"Lecture impossible.")+(if(ex is EpisodeDetailsClient.Failure&&ex.retryAt!=null)" Reprise après "+SimpleDateFormat("dd/MM HH:mm:ss",Locale.FRANCE).format(Date(ex.retryAt)) else "");message="Dernière consultation et brouillon conservés."}}
            finally{if(token==serial){loading=false;job=null}}
        }
    }
    fun reload(){val saved=repo.episodes.firstOrNull{it.id==id&&it.showId==showId}?:return;baseRating=saved.rating?.toString().orEmpty();baseNote=saved.userNote.orEmpty();draftRating=baseRating;draftNote=baseNote;openedAt=System.currentTimeMillis();account=repo.episodeBatchAccount();personalError=null}
    val credits=if(masked)emptyList() else snapshot?.groups?.filter{group=="all"||it.kind==group}?.flatMap{g->g.people.map{g.label to it}}.orEmpty()
    val folded=query.trim().lowercase(Locale.ROOT);val filtered=credits.filter{(_,p)->folded.isEmpty()||(listOf(p.name)+p.roles+p.departments).joinToString(" ").lowercase(Locale.ROOT).contains(folded)}
    val dates=(e.watchHistory+listOfNotNull(e.lastWatchedAt)).filter{it>0}.distinct().sortedDescending()
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("${m.title} · ${e.code}",style=MaterialTheme.typography.labelLarge);Text(if(masked)"Épisode non vu — détails masqués" else snapshot?.title?:e.title,style=MaterialTheme.typography.headlineSmall)
            Row(Modifier.horizontalScroll(rememberScrollState())) {TextButton(enabled=neighbors.first!=null,onClick={leave{neighbors.first?.let(onNavigate)}}){Text("Précédent")};TextButton(enabled=neighbors.second!=null,onClick={leave{neighbors.second?.let(onNavigate)}}){Text("Suivant")};TextButton(onClick={leave(onClose)}){Text("Retour série")}}
        }
        item { ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("${(if(snapshot!=null)snapshot.airDate else e.airDate)?.toString()?:"Date inconnue"} · "+(if(snapshot!=null){snapshot.runtime?.let{"$it min"}?:"Durée inconnue"}else "${e.runtimeMinutes} min enregistrées"))
            if(masked){Text("Titre, image, synopsis, invités et rôles masqués.");TextButton(onClick={revealed=true}){Text("Révéler cet épisode")}}
            else { val url=if(snapshot!=null)snapshot.stillUrl else e.stillUrl;url?.let { AsyncImage(model=it,contentDescription="Image de l’épisode",modifier=Modifier.fillMaxWidth().heightIn(max=230.dp)) }
                Text((snapshot?.overview?:e.overview).ifBlank{"Synopsis non renseigné."});if(repo.settings.value.hideEpisodeSpoilers&&!e.watched)TextButton(onClick={revealed=false;query=""}){Text("Masquer de nouveau")}}
            Text((if(isPublic)"Note TVmaze : " else "Note TMDb : ")+(if(snapshot!=null)snapshot.rating else e.catalogRating)?.let{"$it/10"}.orEmpty().ifBlank{"inconnue"})
        }} }
        item { Text("Mon suivi",style=MaterialTheme.typography.titleLarge);Text("${maxOf(e.plays,dates.size,if(e.watched)1 else 0)} lecture(s) · ${dates.size} date(s) connue(s).")
            DatedViewingButton(repo,showId,id)
            Row(Modifier.horizontalScroll(rememberScrollState())) {TextButton(onClick={leave(onManage)}){Text("Gérer les épisodes")};if(q!=null&&!isPublic)TextButton(onClick={leave{onAvailability(e.season)}}){Text("Où regarder la saison")}}
        }
        item { Text("Ma note et mon texte privé",style=MaterialTheme.typography.titleLarge)
            OutlinedTextField(draftRating,{draftRating=it},label={Text("Ma note /10 · vide = aucune")},singleLine=true)
            OutlinedTextField(draftNote,{draftNote=it},Modifier.fillMaxWidth(),label={Text("Texte privé · 2 000 caractères maximum")},minLines=3)
            Text("Le texte reste local et dans tes sauvegardes. La note chiffrée d’un épisode identifié peut rejoindre Trakt.",style=MaterialTheme.typography.bodySmall)
            if(dirty)Text("Brouillon non enregistré.")
            personalError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            Button(onClick={runCatching{
                val before=EpisodePersonalFields(baseRating.takeIf{it.isNotEmpty()}?.toInt(),baseNote.ifEmpty{null})
                val plan=EpisodePersonalPlan(showId,id,signature,before,EpisodeDetailRules.personal(draftRating,draftNote),openedAt)
                repo.saveEpisodePersonal(plan,epoch,account)
            }.onSuccess{val updated=repo.episodes.first{it.id==id};baseRating=updated.rating?.toString().orEmpty();baseNote=updated.userNote.orEmpty();draftRating=baseRating;draftNote=baseNote;openedAt=System.currentTimeMillis();personalError=null;message="Champs personnels enregistrés."}.onFailure{personalError=it.message}}){Text("Enregistrer mes champs")}
            TextButton(onClick={if(dirty)pendingExit={reload()} else reload()}){Text("Recharger les champs enregistrés")}
        }
        item { Text(if(isPublic)"Invités et équipe de cet épisode · TVmaze" else "Détails propres à cet épisode · TMDb",style=MaterialTheme.typography.titleLarge)
            Row(Modifier.horizontalScroll(rememberScrollState())) {Button(enabled=!loading&&q!=null,onClick={read()}){Text(if(isPublic)"Lire sans clé" else "Lire les détails TMDb")};TextButton(enabled=!loading&&q!=null,onClick={read(true)}){Text("Actualiser")};TextButton(enabled=loading,onClick={stop();message="Lecture annulée ; ancien relevé conservé."}){Text("Annuler")}}
            if(loading)LinearProgressIndicator(Modifier.fillMaxWidth());Text(message);error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            snapshot?.let { Text("Consulté le "+SimpleDateFormat("dd/MM/yyyy HH:mm:ss",Locale.FRANCE).format(Date(it.observedAt))+" · "+it.query.language)
                TextButton(onClick={runCatching{uri.openUri(it.sourceUrl)}.onFailure{error="Ouverture externe impossible."}}){Text(if(isPublic)"TVmaze · CC BY-SA — site externe" else "Source TMDb — site externe")}}
        }
        if(snapshot!=null&&!masked){
            item { Row(Modifier.horizontalScroll(rememberScrollState())) {(listOf("all" to "Tous")+snapshot.groups.map{it.kind to it.label}).forEach{(k,l)->FilterChip(group==k,{group=k;limit=60},label={Text(l)})}}
                OutlinedTextField(query,{query=it.take(250);limit=60},Modifier.fillMaxWidth(),label={Text("Rechercher nom ou rôle")});Text("${filtered.size} référence(s)") }
            items(filtered.take(limit),key={it.first+":"+it.second.id}){(label,p)->OutlinedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                if(repo.settings.value.showCastImages)p.profileUrl?.let{AsyncImage(model=it,contentDescription=null,modifier=Modifier.size(54.dp,80.dp))}
                Column(Modifier.weight(1f)){Text(p.name);Text(label,style=MaterialTheme.typography.labelSmall);Text(p.roles.joinToString(" · ").ifBlank{p.departments.joinToString(" · ")});TextButton(onClick={person=p.id}){Text("Fiche personne")}}
            }}}
            if(filtered.size>limit)item{TextButton(onClick={limit+=60}){Text("Afficher 60 crédits de plus")}}
        }
    }
}
