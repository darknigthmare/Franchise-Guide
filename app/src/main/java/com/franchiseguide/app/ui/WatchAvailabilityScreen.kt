package com.franchiseguide.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.franchiseguide.app.data.AppRepository
import com.franchiseguide.app.model.*
import com.franchiseguide.app.network.WatchProvidersClient
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Read-only, on demand. Neither preferences nor library/history are written by this screen. */
@Composable
fun WatchAvailabilityScreen(repo:AppRepository,id:String,initialSeason:Int?=null) {
    val scope=rememberCoroutineScope();val uri=LocalUriHandler.current;val client=remember { WatchProvidersClient() }
    val epoch=remember(id){repo.gamingLibraryEpoch()}
    val m=repo.media.firstOrNull { it.id==id&&it.added }?:repo.suggestions.firstOrNull { it.id==id }
    var region by remember(id){mutableStateOf(repo.settings.value.region.ifBlank { "FR" }.uppercase(Locale.ROOT))}
    var season by remember(id){mutableStateOf(initialSeason)}
    var countries by remember(id){mutableStateOf<List<WatchCountry>>(emptyList())}
    var countryMenu by remember { mutableStateOf(false) };var seasonMenu by remember { mutableStateOf(false) }
    var kindFilter by remember(id){mutableStateOf("all")};var textFilter by remember(id){mutableStateOf("")};var limit by remember(id){mutableIntStateOf(60)}
    var loading by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};var message by remember{mutableStateOf("Choisis le pays et la portée, puis vérifie les disponibilités.")}
    var job by remember{mutableStateOf<Job?>(null)};var serial by remember{mutableIntStateOf(0)}
    val cache=remember(id,repo.settings.value.tmdbApiKey){mutableStateMapOf<String,WatchAvailability>()}
    DisposableEffect(id){onDispose { job?.cancel() }}
    if(m==null||epoch!=repo.gamingLibraryEpoch()) { Text("La bibliothèque ou la fiche a changé. Rouvre « Où regarder » depuis une fiche actuelle.",Modifier.padding(20.dp));return }
    val q=runCatching{WatchAvailabilityRules.query(m.kind,m.tmdbId,region,season)}.getOrNull()
    val snapshot=q?.let{cache[it.key]}
    val knownSeasons=WatchAvailabilityRules.seasons(m,repo.episodes.toList())
    fun cancel(msg:String="Lecture annulée ; dernier relevé conservé."){serial++;job?.cancel();job=null;loading=false;error=null;message=msg}
    fun currentQuery()=runCatching{WatchAvailabilityRules.query(m.kind,m.tmdbId,region,season)}.getOrNull()
    fun valid(expected:WatchQuery,key:String,token:Int):Boolean {
        val live=repo.media.firstOrNull{it.id==id&&it.added}?:repo.suggestions.firstOrNull{it.id==id}
        return token==serial&&repo.gamingLibraryEpoch()==epoch&&live!=null&&live.tmdbId==expected.id&&live.kind==expected.kind&&key==repo.settings.value.tmdbApiKey&&currentQuery()==expected
    }
    fun read(force:Boolean=false,regions:Boolean=false){
        if(loading)return
        val key=repo.settings.value.tmdbApiKey;val expected=currentQuery()
        if(key.isBlank()){error="Configure la clé TMDb dans les réglages. Aucune disponibilité n’est inventée.";return}
        if(expected==null){error="Pays ou référence TMDb invalide.";return}
        val cached=cache[expected.key]
        if(!regions&&!force&&cached!=null&&System.currentTimeMillis()-cached.observedAt in 0L until 5*60000L){error=null;message="Dernier relevé de cette session réutilisé, à sa date de consultation initiale.";return}
        serial++;val token=serial;loading=true;error=null;message=if(regions)"Lecture des pays disponibles…" else "Vérification auprès de TMDb…"
        job=scope.launch {
            try{
                if(regions){val result=withContext(Dispatchers.IO){client.regions(key,repo.settings.value.language)};if(valid(expected,key,token)){countries=result;message="Pays chargés. Choisis la région puis vérifie le titre."}}
                else{val result=withContext(Dispatchers.IO){client.offers(expected,key)};if(valid(expected,key,token)){cache.remove(expected.key);cache[expected.key]=result;while(cache.size>20)cache.remove(cache.keys.first());message="Relevé consulté. Confirme le prix et les conditions auprès du service."}}
            }catch(e:CancellationException){throw e}catch(e:Exception){if(valid(expected,key,token)){error=(e.message?:"Lecture impossible.")+(if(e is WatchProvidersClient.Failure&&e.retryAt!=null)" Reprise après "+SimpleDateFormat("dd/MM HH:mm:ss",Locale.FRANCE).format(Date(e.retryAt)) else "");message="Dernier relevé complet conservé pour cette sélection."}}
            finally{if(token==serial){loading=false;job=null}}
        }
    }
    val filtered=snapshot?.let{WatchAvailabilityRules.filter(it,kindFilter,textFilter)}.orEmpty();val resultCount=filtered.sumOf{it.offers.size};var remaining=limit
    val visible=filtered.map{g->val rows=g.offers.take(remaining);remaining-=rows.size;g.copy(offers=rows)}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("Où regarder · ${m.title}",style=MaterialTheme.typography.headlineSmall);Text("Disponibilités JustWatch via TMDb. Pas de lecture vidéo, de prix ni d’abonnement personnel supposé.") }
        item { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(region,{v->cancel("Pays modifié. Vérifie ses offres.");region=v.take(2).uppercase(Locale.ROOT);kindFilter="all";textFilter="";limit=60},Modifier.fillMaxWidth(),label={Text("Pays · code à deux lettres")},singleLine=true)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(enabled=!loading,onClick={read(regions=true)}){Text("Charger les pays disponibles")}
                Box { TextButton(enabled=countries.isNotEmpty(),onClick={countryMenu=true}){Text("Choisir un pays")};DropdownMenu(countryMenu,{countryMenu=false}) { countries.forEach { c->DropdownMenuItem(text={Text("${c.label} (${c.code})")},onClick={cancel("Pays modifié. Vérifie ses offres.");region=c.code;countryMenu=false;kindFilter="all";textFilter="";limit=60}) } } }
            }
            if(m.kind==MediaKind.SHOW)Box { OutlinedButton(onClick={seasonMenu=true}){Text(season?.let{if(it==0)"Spéciaux" else "Saison $it"}?:"Série entière")};DropdownMenu(seasonMenu,{seasonMenu=false}) {
                DropdownMenuItem(text={Text("Série entière")},onClick={cancel("Portée modifiée.");season=null;seasonMenu=false})
                knownSeasons.forEach{n->DropdownMenuItem(text={Text(if(n==0)"Spéciaux" else "Saison $n")},onClick={cancel("Portée modifiée.");season=n;seasonMenu=false})}
            } }
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Button(enabled=!loading&&q!=null,onClick={read()}){Text("Vérifier les disponibilités")}
                OutlinedButton(enabled=!loading&&q!=null,onClick={read(force=true)}){Text("Actualiser")}
                TextButton(enabled=loading,onClick={cancel()}){Text("Annuler")}
            }
            if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(message,style=MaterialTheme.typography.bodySmall)
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            Text("Le pays ne change pas les réglages généraux. Une offre de série ne garantit pas les mêmes offres pour chaque saison.",style=MaterialTheme.typography.bodySmall)
        } }
        item { ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("${season?.let { if(it==0)"Spéciaux" else "Saison $it" }?:if(m.kind==MediaKind.SHOW)"Série entière" else "Film"} · $region",style=MaterialTheme.typography.titleMedium)
            if(snapshot==null)Text("Aucun relevé pour cette sélection. Lance une vérification.") else {
                Text("Consulté le "+SimpleDateFormat("dd/MM/yyyy HH:mm:ss",Locale.FRANCE).format(Date(snapshot.observedAt)))
                Text("${snapshot.totalProviders} services · ${snapshot.totalOffers} offres signalées par type d’accès.")
                if(System.currentTimeMillis()-snapshot.observedAt>=5*60000)Text("Relevé de plus de cinq minutes : vérifie-le avant de décider.")
                if(!snapshot.countryReported)Text("Pays non renseigné dans cette réponse. Cela ne prouve pas une indisponibilité.")
                else if(snapshot.totalOffers==0)Text("Aucune offre renseignée. Cela ne prouve pas une indisponibilité générale.")
                if(snapshot.ignoredLinks>0)Text("Des liens ou logos non fiables ont été écartés.")
                snapshot.link?.let { link->TextButton(onClick={runCatching{uri.openUri(link)}.onFailure{error="Ouverture externe impossible."}}){Text("Voir les offres chez TMDb — site externe")} }?:Text("Aucun lien fiable fourni. Pas de lien de lecture reconstruit.")
            }
        } } }
        if(snapshot!=null&&snapshot.totalOffers>0){
            item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { (listOf("all" to "Tous")+WatchAvailabilityRules.modes).forEach{(mode,label)->FilterChip(kindFilter==mode,{kindFilter=mode;limit=60},label={Text(label)})} } }
            item { OutlinedTextField(textFilter,{textFilter=it.take(250);limit=60},Modifier.fillMaxWidth(),label={Text("Rechercher un service")});Text("$resultCount offres correspondant au filtre") }
            visible.filter{it.offers.isNotEmpty()}.forEach{group->
                item("header-${group.mode}"){Text(group.label,style=MaterialTheme.typography.titleMedium)}
                items(group.offers,key={"${group.mode}:${it.id}"}){offer->OutlinedCard(Modifier.fillMaxWidth()){Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    offer.logoUrl?.let{AsyncImage(model=it,contentDescription=null,modifier=Modifier.size(48.dp))};Column{Text(offer.name,style=MaterialTheme.typography.titleSmall);Text("${group.label} signalé · conditions à confirmer",style=MaterialTheme.typography.bodySmall)}
                }}}
            }
            if(resultCount==0)item{Text("Aucune offre parmi celles reçues ne correspond au filtre.")}
            if(resultCount>limit)item{TextButton(onClick={limit+=60}){Text("Afficher 60 offres de plus")}}
        }
        item { Text("Données JustWatch via TMDb · aucun partenariat ou prix supposé. Consultation en mémoire uniquement, sans modification de l’historique ou des comptes.",style=MaterialTheme.typography.bodySmall) }
    }
}
