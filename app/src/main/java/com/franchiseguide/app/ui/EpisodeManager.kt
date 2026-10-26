package com.franchiseguide.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.franchiseguide.app.data.AppRepository
import com.franchiseguide.app.model.*
import java.time.Instant
import java.time.ZoneId

private val episodeActions=listOf("watch" to "Marquer vus maintenant","unwatch" to "Effacer les visionnages","skip" to "Ignorer les non vus","unskip" to "Réactiver","collect" to "Collectionner","uncollect" to "Retirer de la collection")
@Composable
fun EpisodeManagerScreen(repo:AppRepository,showId:String,initialSeason:Int?=null,initialAction:String="watch",onBack:()->Unit) {
    var season by remember(showId){mutableStateOf(initialSeason)}
    var status by remember(showId){mutableStateOf("all")};var airing by remember(showId){mutableStateOf("all")};var query by remember(showId){mutableStateOf("")}
    var specials by remember(showId){mutableStateOf(repo.settings.value.includeSpecials||initialSeason==0)}
    var selected by remember(showId){mutableStateOf(if(initialSeason==null)emptySet<String>() else repo.episodes.filter{it.showId==showId&&it.season==initialSeason}.map{it.id}.toSet())}
    var action by remember(showId){mutableStateOf(initialAction)};var includeDates by remember(showId){mutableStateOf(false)}
    var plan by remember{mutableStateOf<EpisodeBatchRules.Plan?>(null)};var ack by remember{mutableStateOf(false)};var previewLimit by remember{mutableIntStateOf(40)}
    var generation by remember{mutableIntStateOf(repo.gamingLibraryEpoch())};var account by remember{mutableStateOf(repo.episodeBatchAccount())};var error by remember{mutableStateOf<String?>(null)}
    val current=EpisodeBatchRules.State(repo.media.toList(),repo.episodes.toList())
    val data=runCatching{EpisodeBatchRules.view(current,showId,EpisodeBatchRules.Query(season,status,airing,query,specials,repo.settings.value.hideEpisodeSpoilers))}.getOrNull()
    if(data==null){Column(Modifier.padding(16.dp)){Text("Série retirée ou ambiguë. Rouvre le gestionnaire depuis sa fiche.");TextButton(onClick=onBack){Text("Retour")}};return}
    fun toggle(id:String){if(id in selected)selected=selected-id else if(selected.size<EpisodeBatchRules.MAX)selected=selected+id else error="10 000 épisodes maximum."}
    plan?.let { p->
        val names=current.episodes.associateBy{it.id};val c=p.counts
        AlertDialog(onDismissRequest={plan=null},title={Text("Vérifier le lot d’épisodes")},text={Column(Modifier.heightIn(max=500.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(episodeActions.first{it.first==p.action}.second)
            Text("${c.selected} sélectionnés · ${c.changed} modifiés · ${c.unchanged} inchangés")
            Text("${c.historyProtected} historiques protégés · ${c.skippedProtected} ignorés protégés · ${c.dateProtected} dates exclues")
            if(p.action=="watch")Text("Date proposée : ${Instant.ofEpochMilli(p.at).atZone(ZoneId.systemDefault())}. Les épisodes déjà vus conservent leur date.")
            if(p.action=="unwatch")Text("Effacement de ${c.playsRemoved} lecture(s) comptée(s) et ${c.knownDatesRemoved} date(s) connue(s). Notes et collection conservées.",color=MaterialTheme.colorScheme.error)
            Text(if(repo.isTraktConnected())"${c.providerChanges} modification(s) identifiée(s) peuvent rejoindre Trakt. Le statut ignoré reste local." else "Aucun compte Trakt connecté ; aucun envoi distant ajouté.")
            Text("Une copie locale de sécurité doit réussir. Pas d’annulation automatique d’un envoi déjà parti. Aperçu valable dix minutes.")
            LazyColumn(Modifier.weight(1f,false)){items(p.entries.take(previewLimit),key={it.id}) { entry->
                Text((names[entry.id]?.let{EpisodeBatchRules.label(it,repo.settings.value.hideEpisodeSpoilers)}?:"Épisode retiré")+" · "+if(entry.changed)"Sera modifié" else "Inchangé / protégé",Modifier.padding(vertical=4.dp))
            };if(p.entries.size>previewLimit)item{TextButton(onClick={previewLimit+=40}){Text("Afficher 40 lignes de plus")}}}
            Row(Modifier.fillMaxWidth().clickable{ack=!ack},verticalAlignment=Alignment.CenterVertically){Checkbox(checked=ack,onCheckedChange=null);Text(if(p.action=="unwatch")"Je confirme le lot et l’effacement des visionnages." else "Je confirme exactement ce lot.")}
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }},confirmButton={TextButton(onClick={if(!ack){error="Confirme le lot avant application."}else runCatching{repo.applyEpisodeBatch(p,generation,account)}.onSuccess{error=it;plan=null;selected=emptySet()}.onFailure{error=it.message}}){Text("Appliquer le lot")}},dismissButton={TextButton(onClick={plan=null;error=null}){Text("Annuler")}})
    }
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {
            Text("Épisodes · ${data.show.title}",style=MaterialTheme.typography.headlineSmall)
            Text("${data.total} épisodes chargés. Les suggestions et épisodes non chargés ne sont pas créés par cette sélection.")
            TextButton(onClick=onBack){Text("Retour à la fiche")}
            OutlinedTextField(query,{query=it.take(250)},Modifier.fillMaxWidth(),label={Text("Titre ou numéro · anti-spoiler respecté")})
            Row(Modifier.horizontalScroll(rememberScrollState())){FilterChip(selected=season==null,onClick={season=null},label={Text("Toutes les saisons")});data.seasons.forEach{n->FilterChip(selected=season==n,onClick={season=n},label={Text(if(n==0)"Spéciaux" else if(n<0)"À identifier" else "Saison $n")})}}
            Row(Modifier.horizontalScroll(rememberScrollState())){listOf("all" to "Tous","unwatched" to "À voir","watched" to "Vus","skipped" to "Ignorés","collected" to "Collectionnés","uncollected" to "Hors collection").forEach{(k,n)->FilterChip(selected=status==k,onClick={status=k},label={Text(n)})}}
            Row(Modifier.horizontalScroll(rememberScrollState())){listOf("all" to "Toutes les dates","aired" to "Diffusés","future" to "À venir","unknown" to "Date inconnue").forEach{(k,n)->FilterChip(selected=airing==k,onClick={airing=k},label={Text(n)})};FilterChip(selected=specials,onClick={specials=!specials},label={Text("Inclure les spéciaux")})}
            val visible=data.rows.map{it.id}.toSet();Text("${selected.size} sélectionnés, dont ${selected.count{it !in visible}} hors filtre · ${data.rows.size} résultats.")
            Row(Modifier.horizontalScroll(rememberScrollState())){
                TextButton(onClick={val all=selected+visible;if(all.size>EpisodeBatchRules.MAX)error="10 000 épisodes maximum." else selected=all}){Text("Sélectionner tous les résultats")}
                TextButton(onClick={selected=selected-visible}){Text("Désélectionner les résultats")};TextButton(onClick={selected=emptySet()}){Text("Vider")}
            }
            Row(Modifier.horizontalScroll(rememberScrollState())){episodeActions.forEach{(k,n)->FilterChip(selected=action==k,onClick={action=k},label={Text(n)})}}
            if(action=="watch")Row(Modifier.fillMaxWidth().clickable{includeDates=!includeDates},verticalAlignment=Alignment.CenterVertically){Checkbox(checked=includeDates,onCheckedChange=null);Text("Autoriser explicitement les dates inconnues ou futures pour marquer vu.")}
            Text("« Vu » protège les ignorés et ne crée pas de revisionnage. « Ignorer » ne supprime pas un historique. Utilise la fiche pour ajouter une lecture à une date précise.")
            Button(enabled=selected.isNotEmpty(),onClick={runCatching{repo.prepareEpisodeBatch(showId,selected.toList(),action,includeDates)}.onSuccess{plan=it;ack=false;previewLimit=40;generation=repo.gamingLibraryEpoch();account=repo.episodeBatchAccount();error=null}.onFailure{error=it.message}}){Text("Prévisualiser l’action")}
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
        items(data.rows,key={it.id}){e->Row(Modifier.fillMaxWidth().clickable{toggle(e.id)}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(checked=e.id in selected,onCheckedChange=null);Column(Modifier.weight(1f)){Text(EpisodeBatchRules.label(e,repo.settings.value.hideEpisodeSpoilers));Text("${e.airDate?:"Date inconnue"} · "+(if(e.watched)"Vu" else if(e.skipped)"Ignoré" else "À voir")+(if(e.collected)" · Collection" else ""),style=MaterialTheme.typography.bodySmall)}}}
    }
}
