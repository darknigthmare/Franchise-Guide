package com.franchiseguide.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.franchiseguide.app.data.AppRepository
import com.franchiseguide.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

/** Read-only daily dashboard. Remote/local mutations require explicit buttons. */
@Composable
fun TodayPanel(repo:AppRepository,onOpen:(MediaItem)->Unit) {
    val scope=rememberCoroutineScope();var now by remember{mutableLongStateOf(System.currentTimeMillis())};var message by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)};var forget by remember{mutableStateOf(false)}
    val session=repo.traktCheckIn.value;val backupRecord=repo.externalBackup.value;val checkTiming=session?.let{repo.traktCheckInTiming(now)}
    LaunchedEffect(session?.id){while(session!=null){delay(1000);now=System.currentTimeMillis()}}
    val snapshot=remember(repo.media.toList(),repo.episodes.toList(),repo.settings.value,session,backupRecord,now){repo.todaySnapshot()}
    fun remote(block:suspend()->Result<String>){if(busy)return;busy=true;scope.launch{message=withContext(Dispatchers.IO){block()}.fold({it},{it.message?:"Opération impossible."});busy=false}}
    if(forget)AlertDialog(onDismissRequest={forget=false},title={Text("Oublier uniquement la référence locale ?")},text={Text("Cette action ne modifie ni l’historique Trakt, ni les visionnages locaux, ni un éventuel check-in distant encore actif. Utilise-la seulement après vérification manuelle.")},confirmButton={TextButton(onClick={message=repo.forgetLocalTraktCheckIn().fold({it},{it.message});forget=false}){Text("Oublier localement")}},dismissButton={TextButton(onClick={forget=false}){Text("Annuler")}})
    LazyColumn(contentPadding=PaddingValues(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item { Column(Modifier.padding(16.dp)) { Text("Aujourd’hui",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(snapshot.day.format(DateTimeFormatter.ofPattern("EEEE d MMMM")),color=MaterialTheme.colorScheme.onSurfaceVariant);message?.let{Spacer(Modifier.height(8.dp));Text(it,color=if(it.contains("impossible",true)||it.contains("erreur",true))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)} } }
        session?.let { s->item { ElevatedCard(Modifier.fillMaxWidth().padding(horizontal=12.dp)) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Check-in Trakt",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(s.title+(s.code?.let{" · $it"}?:""));Text("État : "+when(s.status){"starting"->"envoi initial";"active"->"actif";"uncertain-start"->"début distant incertain";"finishing"->"confirmation de l’historique";"uncertain-finish"->"historique distant incertain";"cleanup"->"visionnage confirmé, nettoyage restant";else->"annulation distante incertaine"})
            checkTiming?.let { t->Text(if(t.due)"Durée estimée atteinte : vérification de l’historique Trakt requise." else t.remaining?.let{"Temps estimé restant : ${it/60000} min"}?:"Durée inconnue : termine manuellement.");LinearProgressIndicator(progress={((t.percent?:0)/100f).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth()) }
            s.lastError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
                when(s.status){
                    "active","finishing","uncertain-finish"->Button(enabled=!busy,onClick={remote(repo::finishTraktCheckIn)}){Text(if(s.status=="active"&&checkTiming?.due==true)"Vérifier la fin Trakt" else if(s.status=="active")"Terminer" else "Réessayer la confirmation")}
                    "cleanup"->Button(enabled=!busy,onClick={remote(repo::retryTraktCheckInCleanup)}){Text("Nettoyer Trakt")}
                    else->Button(enabled=!busy,onClick={remote(repo::verifyTraktCheckIn)}){Text("Vérifier l’historique")}
                }
                OutlinedButton(enabled=!busy,onClick={remote(repo::cancelTraktCheckIn)}){Text("Annuler")}
            }
            TextButton(enabled=!busy,onClick={forget=true}){Text("Oublier seulement la référence locale")}
            Text("Le visionnage local n’est ajouté qu’après accusé ou vérification de l’historique distant.",style=MaterialTheme.typography.bodySmall)
        } } } }
        item { ElevatedCard(Modifier.fillMaxWidth().padding(horizontal=12.dp)) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            val b=snapshot.backup
            Text("Sauvegarde externe",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text(when {b==null||b.ageDays==null->"Aucune sauvegarde externe vérifiée sur cet appareil.";b.pending->"Un fichier a été proposé, mais sa présence n’a pas encore été confirmée.";b.due->"Dernière sauvegarde vérifiée il y a ${b.ageDays} jour(s).";else->"Dernière sauvegarde vérifiée il y a ${b.ageDays} jour(s)."})
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(enabled=b?.pending==true,onClick={message=repo.verifyExternalBackup().fold({it},{it.message})}){Text("J’ai vérifié le fichier")};TextButton(enabled=b?.due==true,onClick={message=repo.snoozeExternalBackup(7).fold({it},{it.message})}){Text("Rappeler dans 7 jours")}}
            Text("Cette confirmation est déclarative : Android ne prouve pas qu’un fichier existe encore hors de l’application.",style=MaterialTheme.typography.bodySmall)
        } } }
        if(snapshot.uncertain>0||snapshot.held>0)item{ListItem(headlineContent={Text("Synchronisation à vérifier")},supportingContent={Text("${snapshot.uncertain} opération(s) incertaine(s) · ${snapshot.held} en attente")},leadingContent={Icon(Icons.Default.SyncProblem,null)})}
        item{Text("Agenda du jour · ${snapshot.agendaTotal}",Modifier.padding(16.dp,12.dp,16.dp,4.dp),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
        if(snapshot.agenda.isEmpty())item{Text("Aucune sortie ou diffusion datée aujourd’hui.",Modifier.padding(horizontal=16.dp))}
        items(snapshot.agenda,key={it.key}){e->ListItem(headlineContent={Text(e.title)},supportingContent={Text(e.description)},leadingContent={Icon(if(e.kind=="episode")Icons.Default.Tv else Icons.Default.Event,null)},modifier=Modifier.padding(horizontal=4.dp))}
        item{Text("Prêts à reprendre · ${snapshot.nextTotal}",Modifier.padding(16.dp,12.dp,16.dp,4.dp),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
        if(snapshot.next.isEmpty())item{Text("Aucun titre prêt selon les données locales.",Modifier.padding(horizontal=16.dp))}
        items(snapshot.next,key={it.mediaId+":"+(it.episodeId?:"")}){row->ListItem(headlineContent={Text(row.title)},supportingContent={Text(listOfNotNull(row.code,row.minutes?.let{"$it min"}).joinToString(" · "))},leadingContent={Icon(if(row.kind==MediaKind.SHOW)Icons.Default.PlayCircle else Icons.Default.Schedule,null)},modifier=Modifier.padding(horizontal=4.dp),trailingContent={IconButton(onClick={repo.media.firstOrNull{it.id==row.mediaId}?.let(onOpen)}){Icon(Icons.Default.ChevronRight,"Ouvrir")}})}
    }
}
