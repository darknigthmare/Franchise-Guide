package com.franchiseguide.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.franchiseguide.app.data.AppRepository
import com.franchiseguide.app.model.*
import kotlinx.coroutines.launch

/** Native paged reviews. Network writes always require a separate explicit confirmation. */
@Composable
fun TraktReviewsPanel(repo:AppRepository,item:MediaItem) {
    val account=repo.traktUsername().orEmpty()
    val scope=rememberCoroutineScope()
    var comments by remember(item.id,account) { mutableStateOf(emptyList<CommunityComment>()) }
    var page by remember(item.id,account) { mutableStateOf(0) }
    var more by remember(item.id,account) { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var draft by remember(item.id,account) { mutableStateOf(repo.readReviewDraft(item.id,account)) }
    var confirm by remember { mutableStateOf<Pair<String,()->Unit>?>(null) }
    val load:(Boolean)->Unit={reset ->
        if(!loading && !sending) {
            loading=true;error=null
            scope.launch {
                val result=repo.readTraktReviews(item.id,if(reset)1 else page+1)
                result.onSuccess { next ->
                    comments=(if(reset)next.items else comments+next.items).distinctBy { it.id }
                    page=next.page;more=next.hasMore
                }.onFailure { error=it.message?:"Avis indisponibles." }
                loading=false
            }
        }
    }
    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Avis Trakt",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
            TextButton(enabled=!loading&&!sending,onClick={load(true)}) { Text(if(page==0)"Charger" else "Actualiser") }
        }
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        message?.let { Text(it) }
        comments.forEach { comment ->
            TraktCommentCard(comment)
            if(ReleaseRules.ownsReview(account,comment.username)) {
                Row {
                    TextButton(enabled=!sending&&!draft.uncertain,onClick={
                        confirm="Remplacer le brouillon par cet avis pour le modifier ?" to {
                            draft=ReviewDraft(comment.text,comment.spoiler,comment.id)
                        }
                    }) { Text("Modifier mon avis") }
                    TextButton(enabled=!sending,onClick={
                        confirm="Supprimer définitivement cet avis sur Trakt ?" to {
                            sending=true;error=null
                            scope.launch {
                                repo.deleteTraktReview(comment.id).onSuccess {
                                    comments=comments.filterNot { it.id==comment.id };message="Avis supprimé sur Trakt."
                                }.onFailure { error="Suppression non confirmée : vérifie Trakt avant de réessayer. ${it.message}" }
                                sending=false
                            }
                        }
                    }) { Text("Supprimer") }
                }
            }
        }
        if(page>0&&comments.isEmpty()&&error==null)Text("Aucun avis public sur les pages chargées.")
        if(page>0&&more)OutlinedButton(enabled=!loading&&!sending,onClick={load(false)}) { Text(if(error!=null)"Réessayer la page suivante" else "Charger plus d’avis") }
        if(account.isBlank())Text("Connecte ton compte Trakt dans les réglages pour publier un avis.")
        else {
            HorizontalDivider()
            Text(if(draft.editingId==null)"Écrire un avis" else "Modifier mon avis")
            OutlinedTextField(value=draft.text,onValueChange={draft=draft.copy(text=it.take(10_000))},
                modifier=Modifier.fillMaxWidth(),label={Text("Au moins cinq mots")},minLines=3,enabled=!sending&&!draft.uncertain)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(checked=draft.spoiler,onCheckedChange={draft=draft.copy(spoiler=it)},enabled=!sending&&!draft.uncertain)
                Text("Cet avis contient des spoilers")
            }
            if(draft.uncertain) {
                Text("Publication incertaine. Vérifie ton avis sur Trakt avant toute nouvelle tentative.",color=MaterialTheme.colorScheme.error)
                OutlinedButton(enabled=!sending,onClick={
                    confirm="As-tu vérifié sur Trakt que cette publication n’a pas abouti ?" to {
                        runCatching { repo.saveReviewDraft(item.id,draft.copy(uncertain=false),account) }
                            .onSuccess { draft=draft.copy(uncertain=false) }.onFailure { error=it.message }
                    }
                }) { Text("J’ai vérifié : non publié") }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled=!sending,onClick={
                    runCatching { repo.saveReviewDraft(item.id,draft,account) }.onSuccess { message="Brouillon enregistré sur cet appareil." }.onFailure { error=it.message }
                }) { Text("Brouillon") }
                Button(enabled=!sending&&!draft.uncertain&&runCatching { ReleaseRules.reviewText(draft.text) }.isSuccess,onClick={
                    confirm="Publier cet avis avec ton compte Trakt ? Il sera visible publiquement." to {
                        sending=true;error=null
                        scope.launch {
                            repo.publishTraktReview(item.id,draft).onSuccess { posted ->
                                comments=(listOf(posted)+comments.filterNot { it.id==posted.id });draft=ReviewDraft();message="Publication confirmée par Trakt."
                            }.onFailure { error=it.message;draft=repo.readReviewDraft(item.id,account) }
                            sending=false
                        }
                    }
                }) { Text(if(sending)"Envoi…" else "Publier") }
            }
            Text("Enregistre le brouillon avant de quitter cette fiche. Aucun avis n’est envoyé automatiquement.",style=MaterialTheme.typography.bodySmall)
        }
    }
    confirm?.let { choice -> AlertDialog(onDismissRequest={confirm=null},title={Text("Confirmation")},text={Text(choice.first)},
        confirmButton={TextButton(onClick={confirm=null;choice.second()}){Text("Confirmer")}},
        dismissButton={TextButton(onClick={confirm=null}){Text("Annuler")}}) }
}
