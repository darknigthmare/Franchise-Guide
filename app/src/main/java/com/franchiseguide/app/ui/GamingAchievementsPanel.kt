package com.franchiseguide.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.franchiseguide.app.data.AppRepository
import com.franchiseguide.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Native local/imported tracking. Live Steam and RetroAchievements reads use the Web/PC server. */
@Composable
fun GamingAchievementsPanel(repo:AppRepository,game:MediaItem) {
    val context=LocalContext.current
    val uriHandler=LocalUriHandler.current
    val scope=rememberCoroutineScope()
    val sets=repo.gaming.value.sets.filter { it.mediaId==game.id }
    var selectedId by rememberSaveable(game.id) { mutableStateOf("") }
    val selected=sets.find { it.id==selectedId }?:sets.firstOrNull()
    var query by rememberSaveable(game.id,selected?.id) { mutableStateOf("") }
    var filter by rememberSaveable(game.id,selected?.id) { mutableStateOf("all") }
    var group by rememberSaveable(game.id,selected?.id) { mutableStateOf("all") }
    var hardcoreMode by rememberSaveable(game.id,selected?.id) { mutableStateOf(false) }
    var limit by rememberSaveable(game.id,selected?.id) { mutableIntStateOf(50) }
    var revealed by remember(game.id,selected?.id) { mutableStateOf(setOf<String>()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var edit by remember(game.id,selected?.id) { mutableStateOf<Pair<String,GameAchievement>?>(null) }
    var createList by remember { mutableStateOf(false) }
    var createEntry by remember(game.id,selected?.id) { mutableStateOf(false) }
    var deleteList by remember(game.id,selected?.id) { mutableStateOf(false) }
    var importEpoch by remember { mutableIntStateOf(0) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri->
        if(uri!=null&&!busy) {
            busy=true
            scope.launch {
                try {
                    val raw=withContext(Dispatchers.IO) {
                        val bytes=context.contentResolver.openInputStream(uri)?.use { input->
                            val out=java.io.ByteArrayOutputStream();val buf=ByteArray(8192)
                            while(true) { val n=input.read(buf);if(n<0)break;require(out.size()+n<=8*1024*1024) { "Import limité à 8 Mio." };out.write(buf,0,n) };out.toByteArray()
                        }?:error("Fichier inaccessible.")
                        require(bytes.size<=8*1024*1024) { "Import limité à 8 Mio." }
                        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                    }
                    check(importEpoch==repo.gamingLibraryEpoch()) { "La bibliothèque a changé. Relance l’import." }
                    repo.importAchievements(game.id,raw).getOrThrow()
                    status="Liste importée. Il s’agit d’un relevé de fichier, pas d’une connexion authentifiée."
                } catch(e:Exception) { status=e.message?:"Import impossible. Anciennes données conservées." }
                finally { busy=false }
            }
        }
    }
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Trophées & succès",style=MaterialTheme.typography.titleLarge)
            Text("Les cases personnelles ne modifient pas les trophées du compte. Les éditions, plateformes et comptes restent séparés.",style=MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={createList=true},enabled=!busy) { Text("Nouvelle liste") }
                OutlinedButton(onClick={importEpoch=repo.gamingLibraryEpoch();picker.launch(arrayOf("application/json","text/plain","application/octet-stream"))},enabled=!busy) { Text("Importer un JSON") }
            }
            Text("Format : franchiseguide-achievements v1. Aucun export natif PSNProfiles n’est supposé. Les connexions Steam et RetroAchievements sont disponibles sur Web/PC avec le serveur configuré. Sur Android, importe leurs relevés ou une sauvegarde portable pour les consulter.",style=MaterialTheme.typography.bodySmall)
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(status.isNotBlank()) Text(status,color=MaterialTheme.colorScheme.primary)
            if(sets.isEmpty()) Text("Aucune liste pour ce jeu. Crée une liste personnelle ou importe un relevé compatible.")
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                sets.forEach { s->FilterChip(selected?.id==s.id,onClick={selectedId=s.id},label={Text("${s.title} · ${s.platform} ${s.edition}")}) }
            }
            if(selected!=null) {
                val s=selected
                val p=GamingRules.progress(s)
                val hp=GamingRules.progress(s,hardcore=true)
                val isRetro=s.provider=="retro"
                val viewedHardcore=isRetro&&hardcoreMode
                val account=repo.gaming.value.accounts.find { it.id==s.accountId }
                Text("${if(isRetro)"Normal : " else ""}${p.earned}/${p.total} obtenus · ${p.unknown} inconnus · ${p.localDone} cochés localement")
                if(isRetro)Text("Hardcore : ${hp.earned}/${hp.total} obtenus · ${hp.unknown} inconnus${if(hp.complete)" · 100 %" else ""}")
                val catalogueLabel=when {
                    p.complete&&isRetro->"Catalogue complet · Normal à 100 %"
                    p.complete->"Catalogue complet à 100 %"
                    s.completeCatalog->"Catalogue complet"
                    else->"Catalogue non certifié complet"
                }
                Text(catalogueLabel+if(p.platinum)" · Platine enregistré" else "")
                val sourceLabel=when(s.source){"retro-api"->"Relevé RetroAchievements";"steam-api"->"Relevé Steam";"manual"->"Liste personnelle";else->"Relevé importé"}
                Text("${account?.username?:account?.label?:"Sans compte"} · $sourceLabel · ${s.edition.ifBlank { "Édition non précisée" }}",style=MaterialTheme.typography.bodySmall)
                account?.let { a->GamingRules.accountUrl(a)?.let { link->
                    val providerLabel=when(a.provider){"psn"->"PSNProfiles";"retro"->"RetroAchievements";else->"Steam"}
                    OutlinedButton(onClick={runCatching { uriHandler.openUri(link) }.onFailure { status="Aucune application disponible pour ouvrir ce profil." }}) { Text("Ouvrir le profil $providerLabel") }
                } }
                if(s.syncError.isNotBlank()) Text(s.syncError,color=MaterialTheme.colorScheme.error)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(s.source=="manual") OutlinedButton(onClick={createEntry=true}) { Text("Ajouter un objectif") }
                    TextButton(onClick={deleteList=true}) { Text("Supprimer la liste") }
                }
                OutlinedTextField(query,{query=it;limit=50},Modifier.fillMaxWidth(),label={Text("Rechercher sans révéler les secrets")},singleLine=true)
                if(isRetro)Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    FilterChip(!viewedHardcore,{hardcoreMode=false;limit=50},label={Text("Mode normal")})
                    FilterChip(viewedHardcore,{hardcoreMode=true;limit=50},label={Text("Mode Hardcore")})
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("all" to "Tous","earned" to "Obtenus","remaining" to "Restants","unknown" to "Inconnus","pinned" to "Épinglés").forEach { (id,label)->FilterChip(filter==id,{filter=id;limit=50},label={Text(label)}) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    FilterChip(group=="all",{group="all";limit=50},label={Text("Tous les groupes")})
                    s.groups.forEach { g->val gp=GamingRules.progress(s,g.id,viewedHardcore);FilterChip(group==g.id,{group=g.id;limit=50},label={Text("${g.name} · ${gp.earned}/${gp.total}")}) }
                }
                val rows=s.achievements.filter { a->
                    val secret=a.hidden&&a.earned!=true&&(!isRetro||a.hardcoreEarned!=true)&&a.id !in revealed
                    val earned=GamingRules.earnedState(a,viewedHardcore)
                    (group=="all"||a.groupId==group)&&(when(filter){"earned"->earned==true;"remaining"->earned!=true;"unknown"->earned==null;"pinned"->a.pinned;else->true})&&(query.isBlank()||!secret&&("${a.title} ${a.description}").contains(query,true))
                }
                rows.take(limit).forEach { a->
                    val secret=a.hidden&&a.earned!=true&&(!isRetro||a.hardcoreEarned!=true)&&a.id !in revealed
                    val earned=GamingRules.earnedState(a,viewedHardcore)
                    HorizontalDivider()
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        if(!secret) AsyncImage(model=if(earned==true)a.iconUrl else a.lockedIconUrl?:a.iconUrl,contentDescription=null,modifier=Modifier.size(48.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if(secret)"Succès secret" else a.title,style=MaterialTheme.typography.titleMedium)
                            if(isRetro) {
                                Text("Normal : ${unlockLabel(a.earned)} · Hardcore : ${unlockLabel(a.hardcoreEarned)}",style=MaterialTheme.typography.bodySmall)
                            } else Text("${a.grade} · ${unlockLabel(a.earned)}",style=MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick={repo.setAchievementNote(s.id,a.id,a.note,a.localDone,!a.pinned).fold({status="Épingle enregistrée."},{status=it.message?:"Échec"})}) { Text(if(a.pinned)"Désépingler" else "Épingler") }
                    }
                    if(secret) TextButton(onClick={revealed=revealed+a.id}) { Text("Révéler ce secret") }
                    else { if(a.description.isNotBlank())Text(a.description);if(a.hidden&&a.earned!=true&&(!isRetro||a.hardcoreEarned!=true))TextButton(onClick={revealed=revealed-a.id}){Text("Masquer de nouveau")} }
                    a.rarity?.let { Text("Rareté : ${it.percent} % · ${it.source}",style=MaterialTheme.typography.bodySmall) }
                    a.earnedAt?.let { Text((if(isRetro)"Normal obtenu le " else "Obtenu le ")+formatUnlockDate(it),style=MaterialTheme.typography.bodySmall) }
                    if(isRetro)a.hardcoreEarnedAt?.let { Text("Hardcore obtenu le "+formatUnlockDate(it),style=MaterialTheme.typography.bodySmall) }
                    a.points?.let { Text("$it points · barème de cette plateforme uniquement",style=MaterialTheme.typography.bodySmall) }
                    if(a.note.isNotBlank())Text(a.note)
                    TextButton(onClick={edit=s.id to a}) { Text(if(a.localDone)"Case personnelle cochée · modifier" else "Notes et case personnelle") }
                    GamingRules.safeUrl(a.guideUrl)?.let { link->TextButton(onClick={uriHandler.openUri(link)}){Text("Ouvrir le guide associé")} }
                }
                if(rows.size>limit) OutlinedButton(onClick={limit+=50}) { Text("Afficher 50 de plus (${rows.size-limit} restants)") }
            }
        }
    }
    if(createList) GamingEditor("Nouvelle liste personnelle",onClose={createList=false},fields=listOf("Nom de la liste","Plateforme / édition")) { v->repo.createAchievementList(game.id,v[0],v[1]).getOrThrow();status="Liste créée." }
    if(createEntry&&selected!=null) {
        val s=selected
        GamingEditor("Nouvel objectif personnel",onClose={createEntry=false},fields=listOf("Titre","Description","Grade : bronze, silver, gold, platinum, achievement","DLC (vide = jeu de base)")) { v->repo.addPersonalAchievement(s.id,v[0],v[1],v[2].ifBlank { "achievement" },v[3]).getOrThrow();status="Objectif ajouté, sans déblocage officiel." }
    }
    edit?.let { (setId,a)->
        var note by remember(setId,a.id) { mutableStateOf(a.note) };var done by remember(setId,a.id) { mutableStateOf(a.localDone) };var error by remember(setId,a.id) { mutableStateOf("") }
        AlertDialog(onDismissRequest={edit=null},title={Text("Note personnelle")},text={Column { OutlinedTextField(note,{note=it.take(3000)},label={Text("Note")});Row(verticalAlignment=Alignment.CenterVertically){Checkbox(done,{done=it});Text("Coché personnellement, sans modifier le compte")};if(error.isNotBlank())Text(error) }},confirmButton={TextButton(onClick={repo.setAchievementNote(setId,a.id,note,done,a.pinned).fold({edit=null},{error=it.message?:"Échec"})}){Text("Enregistrer")}},dismissButton={TextButton(onClick={edit=null}){Text("Annuler")}})
    }
    if(deleteList&&selected!=null) {
        val s=selected
        AlertDialog(onDismissRequest={deleteList=false},title={Text("Supprimer cette liste locale ?")},text={Text("Une copie de sécurité est créée avant la suppression. Aucun trophée distant ne sera modifié.")},confirmButton={TextButton(onClick={repo.removeAchievementSet(s.id).fold({status="Liste supprimée."},{status=it.message?:"Échec"});deleteList=false}){Text("Supprimer")}},dismissButton={TextButton(onClick={deleteList=false}){Text("Annuler")}})
    }
}

private fun unlockLabel(earned:Boolean?):String=when(earned){true->"Obtenu";false->"Verrouillé";null->"État inconnu"}
private fun formatUnlockDate(at:Long):String=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at))

@Composable
private fun GamingEditor(title:String,onClose:()->Unit,fields:List<String>,submit:(List<String>)->Unit) {
    val values=remember { mutableStateListOf(*Array(fields.size){""}) }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onClose,title={Text(title)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){fields.forEachIndexed { i,label->OutlinedTextField(values[i],{values[i]=it.take(if(i==1)5000 else 300)},label={Text(label)}) };if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)}},confirmButton={TextButton(onClick={try { submit(values.toList());onClose() }catch(e:Exception){error=e.message?:"Échec de sauvegarde."}}){Text("Enregistrer")}},dismissButton={TextButton(onClick=onClose){Text("Annuler")}})
}
