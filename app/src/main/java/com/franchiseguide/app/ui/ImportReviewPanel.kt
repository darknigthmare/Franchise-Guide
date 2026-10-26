package com.franchiseguide.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.franchiseguide.app.data.AppRepository
import com.franchiseguide.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Native source implementation: validate in memory, show impact, explicitly commit. */
@Composable
fun ImportReviewPanel(repo:AppRepository,review:PreparedLibraryImport,onClose:()->Unit,onApplied:(String)->Unit) {
    val scope=rememberCoroutineScope();val comparison=remember(review){repo.importComparison(review)}
    var prefs by remember(review){mutableStateOf(false)};var acknowledged by remember(review){mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    var query by remember{mutableStateOf("")};var filter by remember{mutableStateOf("all")};var limit by remember{mutableIntStateOf(50)}
    val rows=remember(comparison,query,filter){ImportReviewRules.rows(comparison,query,filter)}
    val before=comparison.before;val after=comparison.after
    val counters=listOf("Titres suivis" to (before.titles to after.titles),"Séries" to (before.shows to after.shows),"Films" to (before.movies to after.movies),"Jeux" to (before.games to after.games),"Livres" to (before.books to after.books),"Épisodes" to (before.episodes to after.episodes),"Épisodes vus" to (before.watchedEpisodes to after.watchedEpisodes),"Visionnages datés connus" to (before.dates to after.dates),"Notes privées" to (before.notes to after.notes),"Listes" to (before.lists to after.lists),"Franchises personnalisées" to (before.franchises to after.franchises),"Profils gaming" to (before.accounts to after.accounts),"Listes de succès" to (before.sets to after.sets),"Succès" to (before.achievements to after.achievements))
    AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text(if(review.mode=="portable")"Vérifier le remplacement" else "Vérifier la fusion SeriesGuide")},text={
        Column(Modifier.heightIn(max=530.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Aucune écriture avant confirmation. Une copie locale de sécurité réussie est obligatoire ; les anciennes opérations Trakt seront suspendues, sans suppression distante.")
            review.sourceNames.forEach{Text(it,style=MaterialTheme.typography.bodySmall)}
            Text("Actuel → Après import",style=MaterialTheme.typography.titleSmall)
            counters.forEach{(label,values)->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,Modifier.weight(1f));Text("${values.first} → ${values.second}")}}
            Text("${comparison.absentDates} date(s) connue(s) absente(s) ; ${comparison.changedNotes} note(s) privée(s) et ${comparison.changedRatings} note(s) chiffrée(s) modifiées ou absentes.",color=MaterialTheme.colorScheme.error)
            Text("Comparaison par identifiant local, pas par titre. Un identifiant différent compte comme une entrée absente, sans inférence de correspondance.",style=MaterialTheme.typography.bodySmall)
            review.seriesGuideSummary?.let { Text("Fichiers lus : ${it.shows} séries, ${it.movies} films, ${it.episodes} épisodes, ${it.lists} listes. ${it.linkedListItems} liens résolus, ${it.skippedListItems} références de liste ignorées. Aucun titre n’est inventé pour les références absentes.") }
            if(review.mode=="portable")Row { Checkbox(prefs,{prefs=it;acknowledged=false},enabled=!busy);Text("Importer aussi les réglages publics du fichier. Par défaut, garder ceux de cet appareil.",Modifier.weight(1f)) }
            Text("Les clés et jetons de cet appareil ne proviennent jamais du fichier importé. Les profils gaming restaurés ne réactivent pas leurs automatismes.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(query,{query=it.take(250);limit=50},label={Text("Rechercher un titre dans toutes les différences")},enabled=!busy,modifier=Modifier.fillMaxWidth())
            Row { listOf("all" to "Tout","removed" to "Absents","added" to "Ajoutés").forEach{(key,label)->TextButton(onClick={filter=key;limit=50},enabled=!busy){Text(if(filter==key)"[$label]" else label)}} }
            Text("${minOf(limit,rows.size)} / ${rows.size} fiches affichées")
            rows.take(limit).forEach { row->Text(row.title+" · "+when(row.change){"added"->"Ajouté";"removed"->"Absent après import";"modified"->"Différent";else->"Identique"},style=MaterialTheme.typography.bodySmall) }
            if(limit<rows.size)TextButton(onClick={limit+=50},enabled=!busy){Text("Afficher 50 fiches de plus")}
            Row { Checkbox(acknowledged,{acknowledged=it},enabled=!busy);Text("J’ai vérifié le résultat et compris ${if(review.mode=="portable")"le remplacement complet" else "la fusion"}.",Modifier.weight(1f)) }
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    },confirmButton={TextButton(enabled=acknowledged&&!busy,onClick={busy=true;scope.launch {
        val result=withContext(Dispatchers.IO){repo.commitReviewedImport(review,prefs)};busy=false
        result.onSuccess(onApplied).onFailure{error=it.message?:"Import non enregistré."}
    }}){Text(if(review.mode=="portable")"Remplacer" else "Fusionner")}},dismissButton={TextButton(enabled=!busy,onClick=onClose){Text("Annuler")}})
}
