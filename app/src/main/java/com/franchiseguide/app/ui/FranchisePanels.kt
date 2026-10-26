package com.franchiseguide.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.franchiseguide.app.data.AppRepository
import com.franchiseguide.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UniverseEditor(repo:AppRepository,definition:FranchiseDefinition?,onClose:()->Unit) {
    var name by rememberSaveable{mutableStateOf(definition?.name.orEmpty())};var aliases by rememberSaveable{mutableStateOf(definition?.aliases?.joinToString(", ").orEmpty())}
    var error by remember{mutableStateOf<String?>(null)}
    val generation=remember{repo.gamingLibraryEpoch()}
    AlertDialog(onDismissRequest=onClose,title={Text(if(definition==null)"Créer mon univers" else "Modifier mon univers")},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Classement principal personnel, pas catalogue exhaustif. Un choix manuel — y compris Aucune — reste prioritaire sur la détection par titre.")
        OutlinedTextField(name,{name=it.take(200)},label={Text("Nom")},singleLine=true)
        OutlinedTextField(aliases,{aliases=it.take(8000)},label={Text("Alias séparés par des virgules")})
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={runCatching {
        check(repo.gamingLibraryEpoch()==generation){"Bibliothèque remplacée : ferme et recommence."}
        if(definition!=null){check(repo.allFranchiseDefinitions().firstOrNull{it.id==definition.id}==definition){"Définition modifiée : ferme et recommence."};repo.updateCustomFranchise(definition.id,name,aliases)} else repo.addCustomFranchise(name,aliases)
    }.onSuccess{onClose()}.onFailure{error=it.message}}){Text("Enregistrer")}},dismissButton={TextButton(onClick=onClose){Text("Annuler")}})
}

@Composable
fun UniverseListScreen(repo:AppRepository,onOpen:(FranchiseGroup)->Unit) {
    var query by rememberSaveable{mutableStateOf("")};var personal by rememberSaveable{mutableStateOf(false)};var create by remember{mutableStateOf(false)};var limit by rememberSaveable{mutableIntStateOf(60)}
    if(create)UniverseEditor(repo,null){create=false}
    val groups=repo.franchiseGroups().filter{(!personal||it.definition.custom)&&FranchiseRules.normal(it.definition.name).contains(FranchiseRules.normal(query))}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Text("Mes univers",style=MaterialTheme.typography.headlineSmall);Text("Références déjà associées et suggestions connues. Ce n’est pas le catalogue complet des franchises.");Button(onClick={create=true}){Text("Créer mon univers")}}
        item{OutlinedTextField(query,{query=it.take(250);limit=60},Modifier.fillMaxWidth(),label={Text("Chercher un univers")});FilterChip(personal,{personal=!personal;limit=60},label={Text("Personnels / importés")})}
        items(groups.take(limit),key={it.definition.id}){g->ElevatedCard(onClick={onOpen(g)},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text(g.definition.name,style=MaterialTheme.typography.titleLarge);Text("${g.addedCount} ajouté(s) · ${g.missingCount} suggestion(s) connue(s)");Text(g.kinds.joinToString(" / "){mediaKindLabel(it)}.ifBlank{"Aucun titre associé"})}}}
        if(groups.isEmpty())item{Text("Aucun univers pour ces filtres. Les univers personnels vides restent accessibles ; tu peux en créer un.")}
        if(groups.size>limit)item{TextButton(onClick={limit+=60}){Text("Afficher 60 univers supplémentaires")}}
    }
}

@Composable
fun UniverseDetailScreen(repo:AppRepository,initial:FranchiseGroup,onOpen:(MediaItem)->Unit) {
    val current=repo.franchiseGroups().firstOrNull{it.definition.id==initial.definition.id}
    if(current==null){Text("Cet univers n’existe plus. Les titres restent dans leurs bibliothèques.",Modifier.padding(16.dp));return}
    val d=current.definition
    var query by rememberSaveable(d.id){mutableStateOf("")};var kind by rememberSaveable(d.id){mutableStateOf("all")};var selection by rememberSaveable(d.id){mutableStateOf("all")};var sort by rememberSaveable(d.id){mutableStateOf("personal")};var limit by rememberSaveable(d.id){mutableIntStateOf(50)}
    var edit by remember{mutableStateOf(false)};var delete by remember{mutableStateOf(false)};var search by remember{mutableStateOf(false)};var positionTarget by remember{mutableStateOf<MediaItem?>(null)};var position by remember{mutableStateOf("")};var message by remember{mutableStateOf<String?>(null)}
    var busy by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope()
    if(edit)UniverseEditor(repo,d){edit=false}
    if(search)UniverseSuggestionDialog(repo,d){search=false}
    if(delete)AlertDialog(onDismissRequest={delete=false},title={Text("Supprimer ce classement ?")},text={Column{Text("Les titres, épisodes, notes, historiques, succès et suggestions sont conservés. Les références détachées ne seront pas automatiquement reclassées. Une copie locale de sécurité doit réussir avant application.");message?.let{Text(it)}}},confirmButton={TextButton(onClick={runCatching{repo.deleteCustomFranchise(d.id)}.onSuccess{delete=false}.onFailure{message=it.message}}){Text("Supprimer le classement")}},dismissButton={TextButton(onClick={delete=false}){Text("Annuler")}})
    positionTarget?.let{target->AlertDialog(onDismissRequest={positionTarget=null},title={Text("Ordre personnel")},text={Column{Text("${target.title} · position dans les ${current.items.size} références, pas seulement le filtre. Aucune chronologie officielle n’est supposée.");OutlinedTextField(position,{position=it},label={Text("Position de 1 à ${current.items.size}")});message?.let{Text(it)}}},confirmButton={TextButton(onClick={runCatching{repo.moveInFranchise(d.id,target.id,position.toIntOrNull()?:0)}.onSuccess{sort="personal";positionTarget=null}.onFailure{message=it.message}}){Text("Déplacer")}},dismissButton={TextButton(onClick={positionTarget=null}){Text("Annuler")}})}
    val rows=FranchiseRules.sort(current.items,d,sort).filter{(kind=="all"||it.kind.name.lowercase()==kind)&&(selection=="all"||(selection=="added")==it.added)&&FranchiseRules.normal(it.title).contains(FranchiseRules.normal(query))}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Text(d.name,style=MaterialTheme.typography.headlineSmall);Text("${current.addedCount} titres ajoutés · ${current.missingCount} suggestions connues. Périmètre non exhaustif.");Text(d.description)
            Row(Modifier.horizontalScroll(rememberScrollState())){Button(onClick={search=true}){Text("Scanner séries, films, jeux et livres")};if(d.custom){TextButton(onClick={edit=true}){Text("Modifier")};TextButton(onClick={delete=true;message=null}){Text("Supprimer le classement")}}}
        }
        item{OutlinedTextField(query,{query=it.take(250);limit=50},Modifier.fillMaxWidth(),label={Text("Filtrer tous les titres")})}
        item{Row(Modifier.horizontalScroll(rememberScrollState())){listOf("all" to "Tout","show" to "Séries","movie" to "Films","game" to "Jeux","book" to "Livres").forEach{(k,l)->FilterChip(kind==k,{kind=k;limit=50},label={Text(l)})}}}
        item{Row(Modifier.horizontalScroll(rememberScrollState())){listOf("all" to "Toutes références","added" to "Ajoutées","suggestions" to "Suggestions").forEach{(k,l)->FilterChip(selection==k,{selection=k;limit=50},label={Text(l)})}}}
        item{Row(Modifier.horizontalScroll(rememberScrollState())){listOf("personal" to "Ordre personnel","year" to "Année","title" to "Titre","kind" to "Catégorie").forEach{(k,l)->FilterChip(sort==k,{sort=k;limit=50},label={Text(l)})}}}
        message?.let{item{Text(it)}}
        items(rows.take(limit),key={it.id}){m->ElevatedCard{Column(Modifier.padding(8.dp)){
            MediaRow(m,if(m.isSuggestion)"Suggestion · pas encore dans la bibliothèque" else "Dans la bibliothèque",onClick={onOpen(m)})
            Row(Modifier.horizontalScroll(rememberScrollState())){
                if(m.isSuggestion){TextButton(enabled=busy==null,onClick={busy=m.id;scope.launch{val result=repo.addCatalogItem(m);busy=null;result.onSuccess{message="Fiche ajoutée avec ses données complètes.";onOpen(it)}.onFailure{message=it.message}}}){Text(if(busy==m.id)"Chargement…" else "Ajouter à la bibliothèque")};TextButton(enabled=busy==null,onClick={runCatching{repo.dismissFranchiseSuggestion(m.id)}.onFailure{message=it.message}}){Text("Écarter la suggestion")}}
                if(d.custom&&repo.customFranchises.any{it.id==d.id})TextButton(onClick={positionTarget=m;position=(current.items.indexOfFirst{it.id==m.id}+1).toString();message=null}){Text("Position dans mon ordre")}
            }
        }}}
        if(rows.size>limit)item{TextButton(onClick={limit+=50}){Text("Afficher 50 titres supplémentaires")}}
        if(rows.isEmpty())item{Text("Aucune référence pour ces filtres. Une recherche ne prouve pas une relation canonique ; ne retiens que les suggestions pertinentes.")}
    }
}

@Composable
fun UniverseSuggestionDialog(repo:AppRepository,definition:FranchiseDefinition,onClose:()->Unit) {
    val scope=rememberCoroutineScope();val epoch=remember{repo.gamingLibraryEpoch()}
    var selectedKinds by remember { mutableStateOf(MediaKind.entries.toSet()) }
    var session by remember { mutableStateOf<FranchiseScanRules.Session?>(null) }
    var busyKinds by remember { mutableStateOf(emptySet<MediaKind>()) }
    var message by remember { mutableStateOf<String?>(null) }
    val jobs=remember { mutableMapOf<MediaKind,Job>() }
    fun stateSnapshot()=FranchiseRules.State(repo.media.toList(),repo.suggestions.toList(),repo.customFranchises.toList())
    fun valid()=epoch==repo.gamingLibraryEpoch()&&repo.allFranchiseDefinitions().firstOrNull{it.id==definition.id}==definition
    fun cancelAll(){jobs.values.forEach{it.cancel()};jobs.clear();busyKinds=emptySet()}
    fun load(kind:MediaKind,requested:FranchiseScanRules.Session?=session){
        val base=requested?:return
        if(kind in busyKinds||base.categories[kind]?.complete!=false)return
        val cursor=FranchiseScanRules.current(base,kind);if(cursor.term.isBlank())return
        jobs[kind]?.cancel();busyKinds=busyKinds+kind;message=null
        jobs[kind]=scope.launch {
            val result=withContext(Dispatchers.IO){repo.scanFranchisePage(base,kind)}
            if(!valid()){message="Bibliothèque ou définition modifiée : ferme puis relance le scanner.";busyKinds=busyKinds-kind;return@launch}
            val latest=session?:base
            if(FranchiseScanRules.current(latest,kind)!=cursor){busyKinds=busyKinds-kind;return@launch}
            result.onSuccess { page->val changed=FranchiseScanRules.accept(latest,kind,cursor,page);session=latest.copy(categories=latest.categories+(kind to changed.categories.getValue(kind))) }.onFailure { error->val changed=FranchiseScanRules.failed(latest,kind,cursor,error.message?:"Catalogue indisponible.");session=latest.copy(categories=latest.categories+(kind to changed.categories.getValue(kind))) }
            busyKinds=busyKinds-kind;jobs.remove(kind)
        }
    }
    fun start(){runCatching { check(valid()){"Bibliothèque ou définition modifiée."};FranchiseScanRules.create(definition,selectedKinds) }.onSuccess { created->session=created;message=null;created.categories.keys.forEach{load(it,created)} }.onFailure { message=it.message }}
    DisposableEffect(Unit){onDispose{cancelAll()}}
    AlertDialog(onDismissRequest={cancelAll();onClose()},title={Text("Scanner ${definition.name}")},text={Column(Modifier.heightIn(max=620.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Analyse les catégories séparément. Les résultats ne sont qu’une recherche par termes, pas un inventaire canonique. Le lot final crée uniquement des suggestions grisées.")
        if(session==null){
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){MediaKind.entries.forEach{k->FilterChip(k in selectedKinds,{selectedKinds=if(k in selectedKinds)selectedKinds-k else selectedKinds+k},label={Text(mediaKindLabel(k))})}}
            Button(onClick={start()},enabled=selectedKinds.isNotEmpty()){Text("Analyser les catégories sélectionnées")}
        } else {
            val current=session!!;val snapshot=stateSnapshot();val selected=current.categories.values.sumOf{it.selected.size}
            Text("${current.categories.values.sumOf{it.items.size}} références temporaires · $selected sélectionnée(s). Aucun épisode, suivi ou historique créé.")
            if(busyKinds.isNotEmpty())Row{CircularProgressIndicator(Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("Lectures en cours : ${busyKinds.joinToString{mediaKindLabel(it)}}")}
            LazyColumn(Modifier.weight(1f,false)) {
                current.categories.forEach { (kind,category)->
                    item(key="header-${kind.name}") { ElevatedCard(Modifier.fillMaxWidth().padding(vertical=4.dp)){Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text(mediaKindLabel(kind),style=MaterialTheme.typography.titleLarge);Text("${category.items.size} résultats · ${category.pagesAccepted} pages · ${category.duplicates} doublons ignorés · ${category.rejected} hors termes");category.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                        Row(Modifier.horizontalScroll(rememberScrollState())){TextButton(enabled=kind !in busyKinds&&!category.complete,onClick={load(kind)}){Text(if(category.error!=null)"Réessayer cette catégorie" else if(category.pagesAccepted>0)"Continuer cette catégorie" else "Analyser")};TextButton(onClick={runCatching{session=FranchiseScanRules.selectAvailable(current,kind,snapshot,true)}.onFailure{message=it.message}}){Text("Sélectionner les nouvelles")};TextButton(onClick={session=FranchiseScanRules.selectAvailable(current,kind,snapshot,false)}){Text("Vider")}}
                    }}}
                    items(category.items,key={FranchiseScanRules.key(it)}) { media->val status=FranchiseScanRules.classify(media,snapshot);val id=FranchiseScanRules.key(media);val checked=id in (session?.categories?.get(kind)?.selected?:emptySet());ListItem(headlineContent={Text(media.title)},supportingContent={Text("${media.year?:"année inconnue"} · ${media.source} · ${when(status.status){"new"->"nouvelle référence";"added"->"déjà ajoutée";"withdrawn"->"retirée mais connue";"suggested"->"suggestion existante";else->"correspondance ambiguë"}}")},leadingContent={Checkbox(checked,onCheckedChange=if(status.selectable){{value->runCatching{session=FranchiseScanRules.toggle(session!!,kind,id,stateSnapshot(),value)}.onFailure{message=it.message}}}else null)}) }
                }
            }
            if(selected>0)Button(enabled=busyKinds.isEmpty(),onClick={runCatching{repo.commitFranchiseScan(session!!,definition,epoch)}.onSuccess{count->message="$count suggestion(s) grisée(s) enregistrée(s), sans suivi ni épisodes.";cancelAll();onClose()}.onFailure{message=it.message}},modifier=Modifier.fillMaxWidth()){Text("Enregistrer $selected suggestion(s) grisée(s)")}
            TextButton(onClick={cancelAll();message="Lectures annulées ; pages validées conservées."}){Text("Annuler les lectures en cours")}
        }
        message?.let{Text(it,color=if(it.contains("enregistr"))MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={cancelAll();onClose()}){Text("Fermer")}})
}

