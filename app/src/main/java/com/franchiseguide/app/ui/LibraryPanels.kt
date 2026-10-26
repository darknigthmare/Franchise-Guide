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
import java.text.Normalizer

@Composable
fun NextUpScreen(repo:AppRepository,onOpen:(MediaItem)->Unit) {
    var kind by rememberSaveable{mutableStateOf("all")};var section by rememberSaveable{mutableStateOf("ready")};var query by rememberSaveable{mutableStateOf("")}
    var favorites by rememberSaveable{mutableStateOf(false)};var hidden by rememberSaveable{mutableStateOf(false)};var specials by rememberSaveable{mutableStateOf(repo.settings.value.includeSpecials)}
    var maxMinutes by rememberSaveable{mutableIntStateOf(0)};var limit by rememberSaveable{mutableIntStateOf(60)};var message by remember{mutableStateOf<String?>(null)}
    val options=LibraryRules.Query(kind,section,query,favorites,hidden,specials,repo.settings.value.hideEpisodeSpoilers,maxMinutes)
    val result=LibraryRules.nextUp(repo.media.toList(),repo.episodes.toList(),options);val generation=repo.gamingLibraryEpoch()
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Text("À regarder / reprendre",style=MaterialTheme.typography.headlineSmall);Text("Un prochain épisode non vu/non ignoré par série. Une date de diffusion connue ne garantit pas une disponibilité en streaming.") }
        item { Row(Modifier.horizontalScroll(rememberScrollState())){listOf("all" to "Tout","show" to "Séries","movie" to "Films","game" to "Jeux","book" to "Livres").forEach{(k,label)->FilterChip(kind==k,{kind=k;limit=60},label={Text(label)})}} }
        item { Row(Modifier.horizontalScroll(rememberScrollState())){listOf("ready" to "À reprendre","future" to "À venir","unknown" to "À compléter","caught-up" to "À jour localement","all" to "Toutes").forEach{(k,label)->FilterChip(section==k,{section=k;limit=60},label={Text(label)})}} }
        item { OutlinedTextField(query,{query=it.take(250);limit=60},Modifier.fillMaxWidth(),label={Text("Titre ou numéro")}) }
        item { Row(Modifier.horizontalScroll(rememberScrollState())){FilterChip(favorites,{favorites=!favorites;limit=60},label={Text("Favoris")});FilterChip(hidden,{hidden=!hidden;limit=60},label={Text("Inclure masqués")});FilterChip(specials,{specials=!specials;limit=60},label={Text("Inclure spéciaux")})} }
        item { Row(Modifier.horizontalScroll(rememberScrollState())){listOf(0,20,30,45,60,90,120).forEach{n->FilterChip(maxMinutes==n,{maxMinutes=n;limit=60},label={Text(if(n==0)"Sans durée limite" else "≤ $n min")})}};if(maxMinutes>0)Text("Films/épisodes avec durée connue seulement ; aucun temps de lecture/partie supposé.") }
        item { Text("${result.stats["ready"]} à reprendre · ${result.stats["future"]} à venir · ${result.stats["unknown"]} à compléter · ${result.stats["caught-up"]} à jour sur les épisodes chargés");message?.let{Text(it,color=MaterialTheme.colorScheme.primary)} }
        items(result.records.take(limit),key={it.mediaId}){row->ElevatedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            TextButton(onClick={repo.media.firstOrNull{it.id==row.mediaId}?.let(onOpen)}){Text(row.title)}
            Text((row.date?.toString()?:"Date inconnue")+(row.minutes?.let{" · $it min"}?:""))
            if(row.kind==MediaKind.SHOW)Text("${row.remaining} épisode(s) restant(s) chargé(s)"+if(row.incomplete)" · Fiche à compléter" else "")
            if(row.episodeId==null)row.code?.let{Text(it)}
            if(row.status=="ready"&&(row.kind==MediaKind.MOVIE||row.episodeId!=null)) {
                Button(onClick={message=repo.markNextViewed(row.mediaId,row.episodeId,options,generation).fold({it},{it.message})}){Text("Marquer vu maintenant")}
                DatedViewingButton(repo,row.mediaId,row.episodeId)
            }
        }}}
        if(result.records.size>limit)item{TextButton(onClick={limit+=60}){Text("Afficher 60 titres supplémentaires")}}
        if(result.records.isEmpty())item{Text("Aucun titre pour ces filtres. Les épisodes sans date ou numérotation fiable sont dans À compléter.")}
    }
}

private fun localActionLabel(a:LibraryRules.Action)=when(a){LibraryRules.Action.FAVORITE_ON->"Ajouter aux favoris";LibraryRules.Action.FAVORITE_OFF->"Retirer des favoris";LibraryRules.Action.HIDE->"Masquer";LibraryRules.Action.UNHIDE->"Rendre visibles";LibraryRules.Action.LIST_ADD->"Ajouter à une liste";LibraryRules.Action.LIST_REMOVE->"Retirer d’une liste"}
@Composable
fun OrganizeScreen(repo:AppRepository,onOpen:(MediaItem)->Unit) {
    var kind by rememberSaveable{mutableStateOf("all")};var query by rememberSaveable{mutableStateOf("")};var visibility by rememberSaveable{mutableStateOf("visible")};var limit by rememberSaveable{mutableIntStateOf(80)}
    // Selection and undo are deliberately runtime-only. They are never silently replayed after navigation/reload.
    var selected by remember{mutableStateOf<Set<String>>(emptySet())};var action by remember{mutableStateOf(LibraryRules.Action.FAVORITE_ON)};var listId by remember{mutableStateOf<String?>(repo.userLists.firstOrNull()?.id)}
    var preview by remember{mutableStateOf<LibraryRules.Plan?>(null)};var previewGeneration by remember{mutableIntStateOf(0)};var undo by remember{mutableStateOf<LibraryRules.Plan?>(null)};var undoGeneration by remember{mutableIntStateOf(0)};var message by remember{mutableStateOf<String?>(null)}
    fun normal(x:String)=Normalizer.normalize(x,Normalizer.Form.NFD).replace(Regex("[\\u0300-\\u036f]"),"").lowercase()
    val q=normal(query)
    val rows=repo.media.filter{it.added&&!it.isSuggestion&&(kind=="all"||it.kind.name.lowercase()==kind)&&(visibility=="all"||if(visibility=="hidden")it.hidden else !it.hidden)&&(q.isBlank()||normal(it.title+" "+it.creator.orEmpty()).contains(q))}.sortedBy{normal(it.title)}
    preview?.let { plan->AlertDialog(onDismissRequest={preview=null},title={Text("Vérifier l’action groupée")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(localActionLabel(plan.action));Text("${plan.selectedCount} sélectionnés · ${plan.patches.size} modifiés · ${plan.unchanged} déjà dans cet état.")
        Text("Copie de sécurité locale requise. Aucun visionnage, note, succès ni état distant n’est supprimé.")
        plan.patches.take(10).forEach{Text(it.title)};if(plan.patches.size>10)Text("Et ${plan.patches.size-10} autres.");message?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(enabled=plan.patches.isNotEmpty(),onClick={repo.applyLocalOrganization(plan,previewGeneration).onSuccess{message=it;undo=plan;undoGeneration=previewGeneration;preview=null}.onFailure{message=it.message}}){Text("Appliquer")}},dismissButton={TextButton(onClick={preview=null}){Text("Annuler")}}) }
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item { Text("Organiser ma bibliothèque",style=MaterialTheme.typography.headlineSmall);Text("Actions locales réversibles dans cet écran. Aucun envoi Trakt pour favoris, visibilité et listes locales.") }
        item { Row(Modifier.horizontalScroll(rememberScrollState())){listOf("all" to "Tout","show" to "Séries","movie" to "Films","game" to "Jeux","book" to "Livres").forEach{(k,label)->FilterChip(kind==k,{kind=k;limit=80},label={Text(label)})}} }
        item { Row(Modifier.horizontalScroll(rememberScrollState())){listOf("visible" to "Visibles","hidden" to "Masqués","all" to "Tous").forEach{(k,label)->FilterChip(visibility==k,{visibility=k;limit=80},label={Text(label)})}} }
        item { OutlinedTextField(query,{query=it.take(250);limit=80},Modifier.fillMaxWidth(),label={Text("Titre ou auteur")}) }
        item { Text("${selected.size} sélectionnés · ${rows.size} résultats. Sélection conservée entre filtres.")
            Row(Modifier.horizontalScroll(rememberScrollState())){TextButton(onClick={val next=selected+rows.map{it.id};if(next.size>10000)message="Maximum 10 000 titres par action." else selected=next}){Text("Sélectionner tous les résultats")};TextButton(onClick={selected=emptySet()}){Text("Désélectionner")}}
        }
        item { Row(Modifier.horizontalScroll(rememberScrollState())){LibraryRules.Action.entries.forEach{a->FilterChip(action==a,{action=a},label={Text(localActionLabel(a))})}} }
        if(action in setOf(LibraryRules.Action.LIST_ADD,LibraryRules.Action.LIST_REMOVE))item{Row(Modifier.horizontalScroll(rememberScrollState())){repo.userLists.forEach{l->FilterChip(listId==l.id,{listId=l.id},label={Text(l.name)})}};if(repo.userLists.isEmpty())Text("Crée d’abord une liste depuis Plus.")}
        item { Button(enabled=selected.isNotEmpty(),onClick={runCatching{LibraryRules.plan(repo.media.toList(),repo.userLists.toList(),selected.toList(),action,listId)}.onSuccess{message=null;preview=it;previewGeneration=repo.gamingLibraryEpoch()}.onFailure{message=it.message}}){Text("Prévisualiser")}
            TextButton(enabled=undo!=null,onClick={undo?.let{p->repo.applyLocalOrganization(p,undoGeneration,true).onSuccess{message=it;undo=null}.onFailure{message=it.message}}}){Text("Annuler la dernière action groupée")};message?.let{Text(it)}
        }
        items(rows.take(limit),key={it.id}){m->ElevatedCard{Row(Modifier.fillMaxWidth().padding(8.dp)){
            Checkbox(checked=m.id in selected,onCheckedChange={checked->selected=if(checked)selected+m.id else selected-m.id})
            Column(Modifier.weight(1f)){Text(m.title);Text(m.kind.name+if(m.hidden)" · Masqué" else "");TextButton(onClick={onOpen(m)}){Text("Fiche")}}
        }}}
        if(rows.size>limit)item{TextButton(onClick={limit+=80}){Text("Afficher 80 titres supplémentaires")}}
    }
}
