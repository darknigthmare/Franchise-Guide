package com.franchiseguide.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.franchiseguide.app.data.AppRepository
import com.franchiseguide.app.model.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun DatedViewingButton(repo:AppRepository,mediaId:String,episodeId:String?=null) {
    var open by rememberSaveable(mediaId,episodeId){mutableStateOf(false)}
    TextButton(onClick={open=true}){Text("Ajouter un visionnage à une date")}
    if(open)DatedViewingDialog(repo,mediaId,episodeId){open=false}
}

@Composable
private fun DatedViewingDialog(repo:AppRepository,mediaId:String,episodeId:String?,close:()->Unit) {
    val zone=remember{ZoneId.systemDefault()};val generation=remember{repo.gamingLibraryEpoch()}
    var day by rememberSaveable{mutableStateOf(LocalDate.now(zone).toString())};var time by rememberSaveable{mutableStateOf("")}
    var selected by rememberSaveable(day,time){mutableStateOf<Long?>(null)};var error by remember{mutableStateOf<String?>(null)}
    val candidates=remember(day,time){runCatching{ActivityRules.localChoices(day,time,zone)}.getOrNull()}
    AlertDialog(onDismissRequest=close,title={Text("Ajouter un visionnage daté")},text={
        Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Ajoute une lecture supplémentaire, sans déplacer ni effacer les précédentes. Si l’heure est inconnue, n’en invente pas.")
            Text("Fuseau : ${zone.id}. La nouvelle date sera envoyée à Trakt selon tes réglages si le titre est identifié.")
            OutlinedTextField(day,{day=it},label={Text("Date AAAA-MM-JJ")},singleLine=true)
            OutlinedTextField(time,{time=it},label={Text("Heure connue HH:mm")},singleLine=true)
            TextButton(onClick={val n=ZonedDateTime.now(zone);day=n.toLocalDate().toString();time=n.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}){Text("Utiliser la date et l’heure actuelles")}
            if(candidates?.isEmpty()==true)Text("Cette heure locale n’existe pas au changement d’heure.",color=MaterialTheme.colorScheme.error)
            if(candidates!=null&&candidates.size>1){Text("Heure répétée : choisis explicitement son décalage.");candidates.forEach { at->
                Row { RadioButton(selected=selected==at,onClick={selected=at});Text(Instant.ofEpochMilli(at).atZone(zone).toString()) }
            }}
            if(candidates?.size==1)Text("Instant UTC : ${Instant.ofEpochMilli(candidates.first())}",style=MaterialTheme.typography.bodySmall)
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={TextButton(onClick={runCatching{
        val choices=ActivityRules.localChoices(day,time,zone);require(choices.isNotEmpty()){ "Heure locale inexistante." }
        val at=if(choices.size==1)choices.first() else selected?.takeIf{it in choices}?:error("Choisis le décalage horaire.")
        repo.addDatedViewing(mediaId,episodeId,at,generation).getOrThrow()
    }.onSuccess{close()}.onFailure{error=it.message?:"Date invalide."}}){Text("Ajouter")}},dismissButton={TextButton(onClick=close){Text("Annuler")}})
}

@Composable
fun CalendarScreen(repo:AppRepository,onExport:(String)->Unit,onOpen:(MediaItem)->Unit) {
    var kind by rememberSaveable{mutableStateOf("all")};var recent by rememberSaveable{mutableStateOf(false)};var custom by rememberSaveable{mutableStateOf(false)}
    var from by rememberSaveable{mutableStateOf(LocalDate.now().toString())};var to by rememberSaveable{mutableStateOf(LocalDate.now().plusDays(30).toString())}
    var favorite by rememberSaveable{mutableStateOf(false)};var unwatched by rememberSaveable{mutableStateOf(false)};var query by rememberSaveable{mutableStateOf("")}
    var hidden by rememberSaveable{mutableStateOf(repo.settings.value.includeHiddenInCalendar)};var specials by rememberSaveable{mutableStateOf(repo.settings.value.includeSpecials)}
    var limit by rememberSaveable{mutableIntStateOf(60)};var export by remember{mutableStateOf<List<ActivityRules.Event>?>(null)};var exportGeneration by remember{mutableIntStateOf(0)};var message by remember{mutableStateOf<String?>(null)}
    val today=LocalDate.now();val days=if(recent)repo.settings.value.recentDays else repo.settings.value.upcomingDays
    val data=runCatching { ActivityRules.agenda(repo.media.toList(),repo.episodes.toList(),ActivityRules.Query(kind,
        from=if(custom)LocalDate.parse(from) else if(recent)if(days>0)today.minusDays(days.toLong())else null else today,
        to=if(custom)LocalDate.parse(to) else if(recent)today else if(days>0)today.plusDays(days.toLong())else null,
        favoritesOnly=favorite,unwatchedOnly=unwatched,includeHidden=hidden,includeSpecials=specials,hideSpoilers=repo.settings.value.hideEpisodeSpoilers,query=query)) }
    val rows=data.getOrNull()?.events.orEmpty();val grouped=rows.take(limit).groupBy{it.date}
    export?.let { snapshot->AlertDialog(onDismissRequest={export=null},title={Text("Exporter ${snapshot.size} événements ?")},text={Text("Fichier .ics sur des journées entières, pas une synchronisation calendrier. Aucune heure, alarme, invitation ou disponibilité française n’est supposée. Les titres affichés peuvent devenir visibles aux personnes partageant le calendrier cible. Les notes privées et les comptes ne sont pas exportés. Limite : 10 000 événements, sans troncature silencieuse.")},confirmButton={TextButton(onClick={runCatching{check(exportGeneration==repo.gamingLibraryEpoch()&&rows==snapshot){"Les données ont changé. Prépare un nouvel export."};onExport(ActivityRules.calendar(snapshot))}.onSuccess{export=null}.onFailure{message=it.message;export=null}}){Text("Créer le fichier")}},dismissButton={TextButton(onClick={export=null}){Text("Annuler")}}) }
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Text("Agenda de ma bibliothèque",style=MaterialTheme.typography.headlineSmall);Text("Dates issues des fiches. Horaires et disponibilité locale non confirmés.") }
        item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(!recent&&!custom,{recent=false;custom=false;limit=60},label={Text("À venir")});FilterChip(recent&&!custom,{recent=true;custom=false;limit=60},label={Text("Récentes")});FilterChip(custom,{custom=true;limit=60},label={Text("Plage choisie")})
        } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("all" to "Tout","episode" to "Épisodes","movie" to "Films","game" to "Jeux","book" to "Livres").forEach{(k,label)->FilterChip(kind==k,{kind=k;limit=60},label={Text(label)})} } }
        if(custom)item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(from,{from=it;limit=60},Modifier.weight(1f),label={Text("Du AAAA-MM-JJ")});OutlinedTextField(to,{to=it;limit=60},Modifier.weight(1f),label={Text("Au AAAA-MM-JJ")})}}
        item{OutlinedTextField(query,{query=it.take(250);limit=60},Modifier.fillMaxWidth(),label={Text("Titre / série")})}
        item{Row(Modifier.horizontalScroll(rememberScrollState())){FilterChip(favorite,{favorite=!favorite;limit=60},label={Text("Favoris")});FilterChip(unwatched,{unwatched=!unwatched;limit=60},label={Text("Non vus")});FilterChip(hidden,{hidden=!hidden;limit=60},label={Text("Inclure masqués")});FilterChip(specials,{specials=!specials;limit=60},label={Text("Inclure spéciaux")})}}
        item{Text("${rows.size} événements · ${data.getOrNull()?.undated?:0} sans date connue");Button(enabled=rows.isNotEmpty()&&data.isSuccess,onClick={export=rows.toList();exportGeneration=repo.gamingLibraryEpoch()}){Text("Exporter le calendrier .ics")};data.exceptionOrNull()?.message?.let{Text(it,color=MaterialTheme.colorScheme.error)};message?.let{Text(it,color=MaterialTheme.colorScheme.error)}}
        grouped.forEach{(day,events)->
            item(key="day:$day"){Text(day.toString(),style=MaterialTheme.typography.titleMedium)}
            items(events,key={it.key}){e->ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { TextButton(onClick={repo.media.firstOrNull{it.id==e.mediaId}?.let(onOpen)}){Text(e.title)};Text(e.description,style=MaterialTheme.typography.bodySmall);if(e.kind in setOf("episode","movie"))DatedViewingButton(repo,e.mediaId,if(e.kind=="episode")e.itemId else null) } } }
        }
        if(rows.size>limit)item{TextButton(onClick={limit+=60}){Text("Afficher 60 événements supplémentaires")}}
        if(rows.isEmpty()&&data.isSuccess)item{Text("Aucun événement daté pour ces filtres. Les entrées sans date restent dans la bibliothèque.")}
    }
}
