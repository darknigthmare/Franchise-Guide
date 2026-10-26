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
import com.franchiseguide.app.model.*
import java.time.LocalDate

private fun mins(n:Long)="${n/60} h ${n%60} min"
private val kinds=listOf("all" to "Tout","show" to "Séries","movie" to "Films","game" to "Jeux","book" to "Livres")

@Composable
fun PersonalProgressPanel(repo:AppRepository,m:MediaItem) {
    var current by rememberSaveable(m.id,m.progressCurrent){mutableStateOf(m.progressCurrent.toString())}
    var total by rememberSaveable(m.id,m.progressTotal){mutableStateOf(m.progressTotal.toString())}
    var unit by rememberSaveable(m.id,m.progressUnit){mutableStateOf(m.progressUnit)}
    var status by rememberSaveable(m.id,m.trackingStatus){mutableStateOf(m.trackingStatus?.takeIf{it in setOf("paused","abandoned")}?:"auto")}
    var platform by rememberSaveable(m.id,m.personalPlatform){mutableStateOf(m.personalPlatform.orEmpty())}
    var format by rememberSaveable(m.id,m.personalFormat){mutableStateOf(m.personalFormat.orEmpty())}
    var minutes by rememberSaveable(m.id,m.playtimeMinutes){mutableStateOf(m.playtimeMinutes?.toString().orEmpty())}
    var error by remember{mutableStateOf<String?>(null)}
    var restart by remember{mutableStateOf(false)}
    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Mon parcours · ${ProgressRules.label(m)}",style=MaterialTheme.typography.titleLarge)
        Text("${if(m.watched)maxOf(1,m.plays)else 0} fin(s) historique(s). Modifier l’avancement ne les supprime pas.")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(current,{current=it},Modifier.weight(1f),label={Text("Avancement")},singleLine=true)
            OutlinedTextField(total,{total=it},Modifier.weight(1f),label={Text("Total · 0 inconnu")},singleLine=true)
        }
        OutlinedTextField(unit,{unit=it.take(30)},Modifier.fillMaxWidth(),label={Text("Unité : pages, %, chapitres…")})
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("auto" to "Selon l’avancement","planned" to "À commencer","active" to "En cours","paused" to "En pause","abandoned" to "Abandonné","completed" to "Terminé").forEach { (k,label) -> FilterChip(status==k,{status=k},label={Text(label)}) }
        }
        if(m.kind==MediaKind.GAME) {
            OutlinedTextField(platform,{platform=it.take(120)},Modifier.fillMaxWidth(),label={Text("Ma plateforme")})
            OutlinedTextField(minutes,{minutes=it},Modifier.fillMaxWidth(),label={Text("Minutes réellement jouées · vide inconnu")})
        } else OutlinedTextField(format,{format=it.take(120)},Modifier.fillMaxWidth(),label={Text("Mon édition / format")})
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        Button(onClick={runCatching{repo.setPersonalProgress(m.id,ProgressRules.parse(current,"Avancement"),ProgressRules.parse(total,"Total"),unit,status,platform,format,minutes.takeIf{it.isNotBlank()}?.let{ProgressRules.parse(it,"Minutes")})}.onSuccess{error=null}.onFailure{error=it.message}}) { Text("Enregistrer la progression") }
        TextButton(onClick={restart=true}){Text(if(m.kind==MediaKind.BOOK)"Commencer une relecture" else "Commencer une nouvelle partie")}
        Text("Les minutes Steam et les listes de succès restent des mesures indépendantes. Les pages du parcours actuel ne sont pas un cumul historique.",style=MaterialTheme.typography.bodySmall)
    }
    if(restart)AlertDialog(onDismissRequest={restart=false},title={Text("Recommencer à zéro ?")},text={Text("Les dates et compteurs de fins précédentes sont conservés.")},confirmButton={TextButton(onClick={runCatching{repo.restartProgress(m.id)}.onSuccess{restart=false}.onFailure{error=it.message;restart=false}}){Text("Recommencer")}},dismissButton={TextButton(onClick={restart=false}){Text("Annuler")}})
}

@Composable
private fun InsightMetric(value:String,label:String){ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(value,style=MaterialTheme.typography.titleLarge);Text(label,style=MaterialTheme.typography.bodySmall)}}}

@Composable
fun InsightsPanel(repo:AppRepository) {
    var kind by rememberSaveable{mutableStateOf("all")};var days by rememberSaveable{mutableIntStateOf(30)}
    var hidden by rememberSaveable{mutableStateOf(false)};var specials by rememberSaveable{mutableStateOf(repo.settings.value.includeSpecials)};var favorites by rememberSaveable{mutableStateOf(false)}
    val media=repo.media.toList();val episodes=repo.episodes.toList();val gaming=repo.gaming.value
    val data=remember(media,episodes,gaming,kind,days,hidden,specials,favorites){repo.insights(InsightsRules.Query(kind=kind,from=if(days>0)LocalDate.now().minusDays((days-1).toLong())else null,to=if(days>0)LocalDate.now()else null,includeHidden=hidden,includeSpecials=specials,favoritesOnly=favorites))}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text("Statistiques & progression",style=MaterialTheme.typography.headlineSmall);Text("Bibliothèque actuelle, lectures datées et relevés de plateformes sont trois mesures séparées.")}
        item {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){kinds.forEach{(k,label)->FilterChip(kind==k,{kind=k},label={Text(label)})}}}
        item {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(7,30,90,365,0).forEach{n->FilterChip(days==n,{days=n},label={Text(if(n==0)"Toutes dates" else "$n jours")})}}}
        item {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(hidden,{hidden=!hidden},label={Text("Inclure masqués")});FilterChip(specials,{specials=!specials},label={Text("Inclure spéciaux")});FilterChip(favorites,{favorites=!favorites},label={Text("Favoris seulement")})}}
        item {Text("Bibliothèque actuelle · hors filtre de dates",style=MaterialTheme.typography.titleLarge)}
        item {Text("${data.media.count{it.favorite}} favori(s), ${data.media.count{it.collected}} titre(s) en collection, ${data.episodes.count{it.collected}} épisode(s) collectionné(s), ${data.media.count{it.watchlist}} à faire/voir.")}

        item {InsightMetric(data.media.size.toString(),"Titres ajoutés");Spacer(Modifier.height(6.dp));InsightMetric("${data.episodes.count{it.watched&&!it.skipped}} / ${data.episodes.size}","Épisodes vus / chargés");Spacer(Modifier.height(6.dp));InsightMetric(data.caughtUp.toString(),"Séries à jour sur leurs épisodes chargés")}
        item {InsightMetric(data.currentPages.toString(),"Pages du parcours actuel · ${data.pageBooks} livres en pages");Text("${data.otherUnitBooks} livre(s) en autres unités ne sont pas additionnés. Aucun total historique de pages lu n’est inventé.",style=MaterialTheme.typography.bodySmall)}
        item {InsightMetric(mins(data.declaredGameMinutes),"Temps de jeu saisi manuellement · ${data.declaredGameCount} jeu(x)")}
        listOf("game","book").forEach { type -> if(data.media.any{it.kind.name.lowercase()==type})item {
            Text(if(type=="game")"Parcours de jeux" else "Parcours de livres",style=MaterialTheme.typography.titleMedium)
            Text("${data.media.count{it.kind.name.lowercase()==type&&it.watched}} titre(s) déjà terminé(s) au moins une fois.")
            data.statuses.getValue(type).forEach{(state,count)->Text("${when(state){"active"->"En cours";"paused"->"En pause";"abandoned"->"Abandonné";"completed"->"Terminé";else->"À commencer"}} : $count")}
        } }
        item {HorizontalDivider();Text("Lectures datées dans la période",style=MaterialTheme.typography.titleLarge)}
        item {InsightMetric(data.events.size.toString(),"Dates connues dans la sélection");Spacer(Modifier.height(6.dp));InsightMetric(mins(data.windowMinutes),"Somme des durées vidéo connues dans la période");Text("${data.windowUnknown} lecture(s) vidéo sans durée. La durée catalogue actuelle n’est pas un chronomètre réel.",style=MaterialTheme.typography.bodySmall)}
        item {Text("${data.totalPlays} lectures comptées dans ce périmètre, dont ${data.knownDates} datées et ${data.undatedPlays} sans date exploitable. ${data.futureDates} date(s) future(s) écartée(s). Ces compteurs globaux ne dépendent pas du filtre de période.")}
        item {Text("${mins(if(repo.settings.value.statisticsIncludeRewatches)data.allMinutes else data.uniqueMinutes)} de durées vidéo connues, toutes dates. ${data.unknownVideoPlays} lecture(s) sans durée non additionnées.")}
        items(data.days.takeLast(30),key={"day-${it.date}"}){d->Text("${d.date} · ${d.events} lecture(s) · ${mins(d.minutes)} vidéo connues")}
        item {HorizontalDivider();Text("Répartition des titres sélectionnés · hors période",style=MaterialTheme.typography.titleLarge)
            Text("Genres connus",style=MaterialTheme.typography.titleMedium)
            data.media.flatMap{it.genres}.groupingBy{it}.eachCount().entries.sortedByDescending{it.value}.take(12).forEach{Text("${it.value} · ${it.key}")}
            Text("Franchises attribuées",style=MaterialTheme.typography.titleMedium)
            data.media.map{it.franchiseName?:it.franchiseId?:"Sans franchise attribuée"}.groupingBy{it}.eachCount().entries.sortedByDescending{it.value}.take(12).forEach{Text("${it.value} · ${it.key}")}
            Text("12 groupes les plus représentés. Plusieurs genres peuvent décrire le même titre.",style=MaterialTheme.typography.bodySmall)
        }
        if(data.steam.isNotEmpty())item{HorizontalDivider();Text("Temps Steam actuels · hors période",style=MaterialTheme.typography.titleLarge);Text("Les profils ne sont pas additionnés entre eux. Un même App ID n’est compté qu’une fois par profil.")}
        items(data.steam){s->InsightMetric(mins(s.minutes),"${s.label} · ${s.knownGames}/${s.games} jeux avec temps connu ; ${s.duplicates} doublon(s) d’App ID non additionnés")}
        if(data.successSets.isNotEmpty())item{HorizontalDivider();Text("Relevés de succès actuels · hors période",style=MaterialTheme.typography.titleLarge);Text("Normal/Hardcore et cases personnelles ne sont pas additionnés. 50 listes détaillées maximum ici ; les autres restent accessibles dans le module Jeux.")}
        items(data.successSets.take(50),key={"success-${it.id}"}){s->ElevatedCard{Column(Modifier.padding(12.dp)){Text(s.title,style=MaterialTheme.typography.titleMedium);Text("${s.accountLabel} · ${s.provider} · ${s.platform} · ${s.edition}");Text("${s.earned}/${s.total} obtenus · ${s.unknown} inconnus · ${s.hardcore} Hardcore · ${s.localDone} cases personnelles");Text(if(s.complete)"Liste complète selon le relevé" else "Complétude non établie")}}}
    }
}

@Composable
fun DatedInsightsPanel(repo:AppRepository,onOpen:(MediaItem)->Unit) {
    var kind by rememberSaveable{mutableStateOf("all")};var query by rememberSaveable{mutableStateOf("")};var fromText by rememberSaveable{mutableStateOf("")};var toText by rememberSaveable{mutableStateOf("")}
    var appliedFrom by rememberSaveable{mutableStateOf("")};var appliedTo by rememberSaveable{mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)}
    var hidden by rememberSaveable{mutableStateOf(false)};var specials by rememberSaveable{mutableStateOf(repo.settings.value.includeSpecials)};var limit by rememberSaveable{mutableIntStateOf(100)}
    val media=repo.media.toList();val episodes=repo.episodes.toList();val data=remember(media,episodes,kind,query,appliedFrom,appliedTo,hidden,specials){repo.insights(InsightsRules.Query(kind,appliedFrom.takeIf{it.isNotEmpty()}?.let(LocalDate::parse),appliedTo.takeIf{it.isNotEmpty()}?.let(LocalDate::parse),includeHidden=hidden,includeSpecials=specials,search=query))}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item{Text("Historique daté",style=MaterialTheme.typography.headlineSmall);Text("${data.events.size} ligne(s) filtrée(s). ${data.undatedPlays} lecture(s) sans date exploitable dans cette catégorie, non réparties dans le temps.")}
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){kinds.forEach{(k,l)->FilterChip(kind==k,{kind=k;limit=100},label={Text(l)})}}}
        item{OutlinedTextField(query,{query=it.take(250);limit=100},Modifier.fillMaxWidth(),label={Text("Titre ou numéro")})}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(fromText,{fromText=it},Modifier.weight(1f),label={Text("Du AAAA-MM-JJ")});OutlinedTextField(toText,{toText=it},Modifier.weight(1f),label={Text("Au AAAA-MM-JJ")})};Button(onClick={runCatching{val from=fromText.trim().takeIf{it.isNotBlank()}?.let(LocalDate::parse);val to=toText.trim().takeIf{it.isNotBlank()}?.let(LocalDate::parse);require(from==null||to==null||from<=to){"Plage inversée."};appliedFrom=from?.toString().orEmpty();appliedTo=to?.toString().orEmpty();limit=100;error=null}.onFailure{error=it.message?:"Date invalide."}}){Text("Appliquer les dates")};error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(hidden,{hidden=!hidden;limit=100},label={Text("Inclure masqués")});FilterChip(specials,{specials=!specials;limit=100},label={Text("Inclure spéciaux")})}}
        items(data.events.take(limit),key={it.id}){e->TextButton(onClick={media.firstOrNull{it.id==e.mediaId}?.let(onOpen)},modifier=Modifier.fillMaxWidth()){Column(Modifier.fillMaxWidth()){Text(e.parentTitle);Text("${e.date} · ${e.title}",style=MaterialTheme.typography.bodySmall)}}}
        if(data.events.size>limit)item{TextButton(onClick={limit+=100}){Text("Afficher 100 lectures supplémentaires")}}
        if(data.events.isEmpty())item{Text("Aucune date connue dans cette sélection. Les compteurs sans date ne sont pas supprimés.")}
    }
}
