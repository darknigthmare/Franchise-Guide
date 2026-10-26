package com.franchiseguide.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.franchiseguide.app.data.AppRepository
import com.franchiseguide.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** Explicit low-volume public consultation. Pages affect only total after confirmation. */
@Composable
fun BookEditionsButton(repo:AppRepository,m:MediaItem) {
    if(m.kind!=MediaKind.BOOK||m.source!="openlibrary"||!m.added||m.isSuggestion)return
    var open by remember(m.id){mutableStateOf(false)}
    if(runCatching{BookEditionRules.key(m.sourceId.orEmpty())}.isFailure)return
    TextButton(onClick={open=true}){Text("Éditions et nombre de pages · sans clé")}
    if(open)BookEditionsDialog(repo,m){open=false}
}
@Composable
private fun BookEditionsDialog(repo:AppRepository,initial:MediaItem,onClose:()->Unit) {
    val scope=rememberCoroutineScope();val uri=LocalUriHandler.current;val work=remember(initial.id){BookEditionRules.key(initial.sourceId.orEmpty())}
    var result by remember{mutableStateOf<BookEditionPage?>(null)}
    var loading by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    var generation by remember{mutableIntStateOf(0)};var observed by remember{mutableStateOf<Long?>(null)};var failedOffset by remember{mutableIntStateOf(0)}
    var query by remember{mutableStateOf("")};var known by remember{mutableStateOf(false)}
    var pending by remember{mutableStateOf<BookPagePlan?>(null)}
    var choice by remember{mutableStateOf<BookSelectionPlan?>(null)}
    var isbn by remember{mutableStateOf("")};var matches by remember{mutableStateOf<List<IsbnCandidate>>(emptyList())}
    var verified by remember{mutableStateOf<BookEdition?>(null)}
    val observations=remember{mutableMapOf<String,Long>()};val epoch=remember{repo.gamingLibraryEpoch()}
    fun findIsbn() {if(loading)return;val code=runCatching{BookSelectionRules.input(isbn)}.getOrElse{error=it.message;return};val token=++generation;loading=true;error=null;matches=emptyList();verified=null
        scope.launch{val r=withContext(Dispatchers.IO){runCatching{repo.bookIsbnSearch(code)}};if(token!=generation)return@launch;loading=false
            if(epoch!=repo.gamingLibraryEpoch()){error="Bibliothèque modifiée.";return@launch}
            r.onSuccess{matches=it.rows.filter{c->c.work==work};if(matches.isEmpty())error="Aucune référence de cette œuvre parmi les résultats limités."}.onFailure{error=it.message}}
    }
    fun verifyIsbn(c:IsbnCandidate) {if(loading)return;val token=++generation;loading=true;error=null;verified=null
        scope.launch{val r=withContext(Dispatchers.IO){runCatching{repo.bookIsbnEdition(c)}};if(token!=generation)return@launch;loading=false
            if(epoch!=repo.gamingLibraryEpoch()){error="Bibliothèque modifiée.";return@launch}
            r.onSuccess{verified=it;observations[it.id]=System.currentTimeMillis()}.onFailure{error=it.message}}
    }
    fun retain(e:BookEdition) {runCatching{val m=repo.media.first{it.id==initial.id};BookSelectionRules.prepare(m,e,observations[e.id]?:error("Relevé non daté."))}.onSuccess{choice=it}.onFailure{error=it.message}}

    fun load(offset:Int) {
        if(loading)return;val token=++generation;loading=true;error=null;failedOffset=offset
        scope.launch {
            val r=withContext(Dispatchers.IO){runCatching{repo.bookEditions(work,offset)}}
            if(token!=generation)return@launch
            if(epoch!=repo.gamingLibraryEpoch()){loading=false;error="Bibliothèque modifiée.";return@launch}
            val current=repo.media.firstOrNull{it.id==initial.id&&it.added&&it.source=="openlibrary"}
            if(current==null||runCatching{BookEditionRules.key(current.sourceId.orEmpty())}.getOrNull()!=work){loading=false;error="La fiche a changé. Rouvre ce panneau.";return@launch}
            r.onSuccess{page->runCatching{BookEditionRules.merge(if(offset==0)null else result,page)}.onSuccess{result=it;val now=System.currentTimeMillis();observed=now;if(offset==0)observations.clear();page.entries.forEach{e->observations[e.id]=now}}.onFailure{error=it.message}}
                .onFailure{error=it.message?:"Lecture indisponible."};loading=false
        }
    }
    DisposableEffect(Unit){onDispose{generation++}}
    if(choice!=null)AlertDialog(onDismissRequest={choice=null},title={Text(if(choice!!.selection==null)"Oublier cette référence ?" else "Retenir cette édition ?")},text={Text("Seule la référence change. Le livre, le total et les lectures précédentes restent conservés. Pour appliquer les pages, utilise l’action séparée.")},confirmButton={TextButton(onClick={runCatching{repo.applyBookSelection(choice!!,epoch)}.onSuccess{choice=null;error=null}.onFailure{error=it.message;choice=null}}){Text("Confirmer")}},dismissButton={TextButton(onClick={choice=null}){Text("Annuler")}})
    if(pending!=null)AlertDialog(onDismissRequest={pending=null},title={Text("Utiliser ${pending!!.pages} pages ?")},text={Text("Seul le total change. La progression, le statut, les anciennes fins, les notes et ton format personnel restent conservés. Ce changement ne termine pas une lecture.")},confirmButton={TextButton(onClick={runCatching{repo.applyBookPageTotal(pending!!)}.onSuccess{pending=null;error=null}.onFailure{error=it.message;pending=null}}){Text("Confirmer")}},dismissButton={TextButton(onClick={pending=null}){Text("Annuler")}})
    AlertDialog(onDismissRequest={generation++;onClose()},title={Text("Éditions · ${initial.title}")},text={
        Column(Modifier.heightIn(max=540.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Open Library · sans clé. Lecture de 30 éditions par page, 300 maximum. Aucun emprunt, prix ou droit de lecture n’est supposé. Recherche dans les pages chargées seulement.",style=MaterialTheme.typography.bodySmall)
            repo.media.firstOrNull{it.id==initial.id}?.let{m->m.selectedEdition?.let{selected->Text("Mon édition : ${selected.title} · ${selected.editionId}");TextButton(onClick={runCatching{BookSelectionRules.prepareClear(m)}.onSuccess{choice=it}.onFailure{error=it.message}}){Text("Oublier seulement la référence")}}}
            OutlinedTextField(isbn,{isbn=it.take(100)},label={Text("ISBN de cette œuvre · sans clé")},singleLine=true)
            TextButton(enabled=!loading,onClick={findIsbn()}){Text("Rechercher cet ISBN")}
            matches.forEach{c->TextButton(enabled=!loading,onClick={verifyIsbn(c)}){Text("Vérifier ${c.editionId}")}}
            verified?.let{e->Text(e.title);TextButton(onClick={retain(e)}){Text("Retenir l’édition vérifiée")}}
            Row { TextButton(enabled=!loading,onClick={load(0)}){Text(if(result==null)"Lire les éditions" else "Actualiser")};if(loading)TextButton(onClick={generation++;loading=false;error="Lecture annulée ; relevé précédent conservé."}){Text("Annuler la lecture")}}
            if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let{Text(it,color=MaterialTheme.colorScheme.error);TextButton(enabled=!loading,onClick={load(failedOffset)}){Text("Réessayer cette page")}}
            result?.let { data->
                Text("${data.entries.size} chargées / ${data.total} annoncées · dernière lecture ${observed?.let{java.time.Instant.ofEpochMilli(it).toString()}.orEmpty()}",style=MaterialTheme.typography.labelSmall)
                OutlinedTextField(query,{query=it.take(250)},label={Text("Titre, éditeur, année, ISBN")},singleLine=true)
                FilterChip(selected=known,onClick={known=!known},label={Text("Pages connues")})
                val entries=BookEditionRules.filter(data.entries,query,known=known)
                LazyColumn(Modifier.weight(1f,false),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(entries,key={it.id}){e->ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
                        Text(e.title,style=MaterialTheme.typography.titleMedium);Text(e.publishers.joinToString(" · ").ifBlank{"Éditeur inconnu"})
                        Text("${e.publishDate.ifBlank{"Date inconnue"}} · ${e.languages.joinToString(", ").ifBlank{"Langue inconnue"}}")
                        Text(e.pages?.let{"$it pages"}?:"Pages non renseignées");Text(e.format.ifBlank{"Format inconnu"})
                        Text(e.isbns.joinToString(" / ").ifBlank{"ISBN valide non renseigné"},style=MaterialTheme.typography.labelSmall)
                        TextButton(onClick={uri.openUri(e.sourceUrl)}){Text("Source de l’édition")}
                        if(e.workVerified)TextButton(onClick={retain(e)}){Text("Retenir cette édition")}
                        if(!e.workVerified)Text("Lien à l’œuvre non confirmé : total non applicable.",style=MaterialTheme.typography.labelSmall)
                        TextButton(enabled=e.workVerified&&(e.pages?:0)>0,onClick={runCatching{val m=repo.media.first{it.id==initial.id};BookEditionRules.prepare(m,e)}.onSuccess{pending=it}.onFailure{error=it.message}}){Text("Utiliser ce total de pages")}
                    }}}
                    if(data.nextOffset!=null&&data.entries.size<BookEditionRules.MAX_LOADED)item{TextButton(enabled=!loading,onClick={load(data.nextOffset)}){Text("Charger 30 éditions supplémentaires")}}
                    if(data.nextOffset!=null&&data.entries.size>=BookEditionRules.MAX_LOADED)item{Text("300 éditions consultées ; ouvre la source pour poursuivre.")}
                }
            }
        }
    },confirmButton={TextButton(onClick={generation++;onClose()}){Text("Fermer")}},dismissButton={TextButton(onClick={uri.openUri("https://openlibrary.org$work")}){Text("Source Open Library")}})
}
