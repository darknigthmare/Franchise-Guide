package com.franchiseguide.app.ui

import androidx.compose.foundation.clickable
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

@Composable
fun PersonalListsScreen(repo:AppRepository,onOpen:(UserList)->Unit) {
    var query by rememberSaveable{mutableStateOf("")};var create by remember{mutableStateOf(false)}
    val counts=repo.media.filter(ListRules::own).flatMap{it.listIds}.groupingBy{it}.eachCount()
    val rows=repo.userLists.filter{query.isBlank()||ListRules.search(it.name+" "+it.description).contains(ListRules.search(query))}.sortedBy{it.name.lowercase()}
    if(create)PersonalListEditor(repo,null,{create=false}){create=false;onOpen(it)}
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Mes listes personnelles",style=MaterialTheme.typography.headlineSmall)
        Text("Listes locales, indépendantes des watchlists Trakt. Un titre peut appartenir à plusieurs listes sans être dupliqué.")
        OutlinedTextField(query,{query=it.take(250)},Modifier.fillMaxWidth(),label={Text("Nom ou description")})
        Button(onClick={create=true}){Text("Créer une liste")}
        Text("${rows.size} résultat(s)")
        LazyColumn { items(rows,key={it.id}){l->ListItem(headlineContent={Text(l.name)},supportingContent={Text("${counts[l.id]?:0} titre(s) ajouté(s)${if(l.manualOrder)" · Mon ordre" else ""}")},modifier=Modifier.clickable{onOpen(l)})} }
    }
}

@Composable
fun PersonalListEditor(repo:AppRepository,existing:UserList?,onClose:()->Unit,onSaved:(UserList)->Unit) {
    var name by rememberSaveable(existing?.id){mutableStateOf(existing?.name.orEmpty())}
    var description by rememberSaveable(existing?.id){mutableStateOf(existing?.description.orEmpty())}
    var error by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onClose,title={Text(if(existing==null)"Créer une liste personnelle" else "Modifier ma liste")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Aucun historique ni compte distant n’est modifié.")
        OutlinedTextField(name,{name=it.take(120)},label={Text("Nom · 120 caractères maximum")})
        OutlinedTextField(description,{description=it.take(3000)},label={Text("Description")},minLines=3)
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={runCatching {
        val id=if(existing==null)repo.addList(name,description)!! else {repo.savePersonalList(existing.copy(name=name,description=description),existing);existing.id}
        repo.userLists.first{it.id==id}
    }.onSuccess(onSaved).onFailure{error=it.message?:"Enregistrement impossible."}}){Text("Enregistrer")}},dismissButton={TextButton(onClick=onClose){Text("Annuler")}})
}

@Composable
fun PersonalListScreen(repo:AppRepository,list:UserList,onOpen:(MediaItem)->Unit,onDeleted:()->Unit) {
    val current=repo.userLists.firstOrNull{it.id==list.id};if(current==null){LaunchedEffect(list.id){onDeleted()};return}
    var query by rememberSaveable(current.id){mutableStateOf("")};var kind by rememberSaveable(current.id){mutableStateOf("all")};var includeHidden by rememberSaveable(current.id){mutableStateOf(true)}
    var error by remember{mutableStateOf<String?>(null)};var edit by remember{mutableStateOf(false)};var members by remember{mutableStateOf(false)}
    var duplicate by remember{mutableStateOf<UserList?>(null)};var delete by remember{mutableStateOf<UserList?>(null)};var nameCopy by remember{mutableStateOf("")}
    var moveId by remember{mutableStateOf<String?>(null)};var moveOrder by remember{mutableStateOf<List<String>>(emptyList())};var position by remember{mutableStateOf("")}
    if(edit)PersonalListEditor(repo,current,{edit=false}){edit=false}
    if(members)PersonalMembersDialog(repo,current.id){members=false}
    duplicate?.let{old->AlertDialog(onDismissRequest={duplicate=null},title={Text("Dupliquer la liste")},text={Column{Text("Les mêmes titres ajoutés seront référencés, sans dupliquer leurs fiches ni leurs lectures.");OutlinedTextField(nameCopy,{nameCopy=it.take(120)},label={Text("Nouveau nom")});error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={TextButton(onClick={runCatching{repo.duplicatePersonalList(old,nameCopy)}.onSuccess{duplicate=null;error="Nouvelle liste créée ; œuvres non dupliquées."}.onFailure{error=it.message}}){Text("Dupliquer")}},dismissButton={TextButton(onClick={duplicate=null}){Text("Annuler")}})}
    delete?.let{old->AlertDialog(onDismissRequest={delete=null},title={Text("Supprimer uniquement la liste ?")},text={Column{Text(old.name);Text("Les œuvres, historiques, notes, épisodes et succès restent conservés. Les appartenances à cette liste seront retirées, masqués compris. Une copie de sécurité doit réussir avant la suppression.");error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={TextButton(onClick={runCatching{repo.deletePersonalList(old)}.onSuccess{delete=null;onDeleted()}.onFailure{error=it.message}}){Text("Supprimer la liste")}},dismissButton={TextButton(onClick={delete=null}){Text("Annuler")}})}
    moveId?.let{id->AlertDialog(onDismissRequest={moveId=null},title={Text("Position dans mon ordre")},text={Column{Text("Position parmi les ${moveOrder.size} titres de la liste entière, masqués et résultats filtrés compris.");OutlinedTextField(position,{position=it},label={Text("Position à partir de 1")});error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={TextButton(onClick={runCatching {val n=position.toIntOrNull()?:error("Nombre entier requis.");repo.movePersonalMember(current.id,id,n,moveOrder)}.onSuccess{moveId=null;error=null}.onFailure{error=it.message}}){Text("Déplacer")}},dismissButton={TextButton(onClick={moveId=null}){Text("Annuler")}})}
    val rows=repo.listItems(current).filter{(kind=="all"||it.kind.name==kind)&&(includeHidden||!it.hidden)&&(query.isBlank()||ListRules.search(it.title).contains(ListRules.search(query)))}
    val order=repo.personalListOrder(current.id).withIndex().associate{it.value to it.index+1}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item { Text(current.name,style=MaterialTheme.typography.headlineSmall);if(current.description.isNotBlank())Text(current.description)
            Text("${order.size} titre(s) ajouté(s) connus, ${rows.size} résultat(s). Une liste locale n’est pas une watchlist distante.")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick={edit=true}){Text("Modifier")};TextButton(onClick={members=true}){Text("Choisir les titres")}
                TextButton(onClick={duplicate=current;nameCopy=(current.name+" — copie").take(120);error=null}){Text("Dupliquer")}
                TextButton(onClick={delete=current;error=null}){Text("Supprimer la liste")}
            }
            OutlinedTextField(query,{query=it.take(250)},Modifier.fillMaxWidth(),label={Text("Rechercher un titre")})
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(selected=kind=="all",onClick={kind="all"},label={Text("Tout")});MediaKind.entries.forEach{k->FilterChip(selected=kind==k.name,onClick={kind=k.name},label={Text(mediaKindLabel(k))})}
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(selected=current.manualOrder,onClick={runCatching{repo.savePersonalList(current.copy(manualOrder=true),current)}.onFailure{error=it.message}},label={Text("Mon ordre")})
                SortMode.entries.forEach{mode->FilterChip(selected=!current.manualOrder&&current.sortMode==mode,onClick={runCatching{repo.savePersonalList(current.copy(manualOrder=false,sortMode=mode),current)}.onFailure{error=it.message}},label={Text(mode.name.lowercase().replace('_',' '))})}
            }
            Row { FilterChip(selected=includeHidden,onClick={includeHidden=!includeHidden},label={Text("Inclure les masqués")});TextButton(enabled=!current.manualOrder,onClick={runCatching{repo.savePersonalList(current.copy(ascending=!current.ascending),current)}.onFailure{error=it.message}}){Text(if(current.ascending)"↑ Croissant" else "↓ Décroissant")} }
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
        if(rows.isEmpty())item{Text("Aucun titre correspondant. Les membres se choisissent dans la bibliothèque déjà ajoutée.")}
        items(rows,key={it.id}){m->MediaRow(m,if(ProgressRules.applicable(m))ProgressRules.label(m) else if(m.hidden)"Masqué" else "Ouvrir la fiche",onClick={onOpen(m)})
            TextButton(onClick={moveOrder=repo.personalListOrder(current.id);position=(order[m.id]?:1).toString();moveId=m.id;error=null}){Text("Position ${order[m.id]} dans mon ordre")}
        }
    }
}

@Composable
fun PersonalMembersDialog(repo:AppRepository,id:String,onClose:()->Unit) {
    val base=remember(id){ListRules.State(repo.media.toList(),repo.suggestions.toList(),repo.userLists.toList())}
    var chosen by remember(id){mutableStateOf(ListRules.members(base,id).map{it.id}.toSet())};var query by remember{mutableStateOf("")};var kind by remember{mutableStateOf<MediaKind?>(null)}
    var plan by remember{mutableStateOf<ListRules.Plan?>(null)};var error by remember{mutableStateOf<String?>(null)}
    val rows=base.media.filter{ListRules.own(it)&&(kind==null||it.kind==kind)&&(query.isBlank()||ListRules.search(it.title).contains(ListRules.search(query)))}
    AlertDialog(onDismissRequest=onClose,title={Text("Choisir les titres")},text={Column(Modifier.heightIn(max=500.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("${chosen.size} sélectionnés · ${rows.size} résultats. Bibliothèque ajoutée, masqués compris. Aucun visionnage n’est changé.")
        OutlinedTextField(query,{query=it.take(250)},label={Text("Titre")},singleLine=true)
        Row(Modifier.horizontalScroll(rememberScrollState())){FilterChip(selected=kind==null,onClick={kind=null},label={Text("Tout")});MediaKind.entries.forEach{k->FilterChip(selected=kind==k,onClick={kind=k},label={Text(mediaKindLabel(k))})}}
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick={val ids=chosen+rows.map{it.id};if(ids.size>ListRules.MAX)error="10 000 titres maximum." else{chosen=ids;plan=null}}){Text("Sélectionner tous les résultats")}
            TextButton(onClick={chosen=chosen-rows.map{it.id}.toSet();plan=null}){Text("Désélectionner les résultats")}
        }
        LazyColumn(Modifier.weight(1f,false)){items(rows,key={it.id}){m->Row(Modifier.fillMaxWidth().clickable{if(m.id in chosen)chosen=chosen-m.id else if(chosen.size<ListRules.MAX)chosen=chosen+m.id else error="10 000 titres maximum.";plan=null}) {Checkbox(checked=m.id in chosen,onCheckedChange=null);Text(m.title+(if(m.hidden)" · Masqué" else ""),Modifier.padding(8.dp))}}}
        plan?.let{Text("Après application : ${it.desired.size} membres ; ${it.added} ajouts, ${it.removed} retraits. Copie locale requise avant confirmation.")}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={runCatching{val p=plan;if(p==null)plan=ListRules.plan(base,id,chosen.toList()) else {repo.applyPersonalMembers(p);onClose()}}.onFailure{error=it.message}}){Text(if(plan==null)"Prévisualiser" else "Confirmer")}},dismissButton={TextButton(onClick=onClose){Text("Annuler")}})
}
