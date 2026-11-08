package com.franchiseguide.app.ui

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.franchiseguide.app.data.AppRepository

/** The final action uses Android's own full-data clearing API, not a partial home-grown deletion. */
@Composable
fun NativePrivacyPanel(repo: AppRepository) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf("") }
    var acknowledged by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Confidentialité sur cet appareil", style = MaterialTheme.typography.titleLarge)
        Text(if (repo.credentialStorageProtected()) "Accès et jetons protégés par le coffre Android. La bibliothèque et les sauvegardes locales ne sont pas chiffrées par ce coffre."
            else repo.credentialStorageProblem().orEmpty())
        Text("L'effacement retire les données internes de l'application et la ferme. Les fichiers déjà exportés et les comptes Trakt, Steam ou autres ne sont pas supprimés.")
        OutlinedButton(modifier=Modifier.testTag("privacy-open"), onClick = { dialog = true; confirmation = ""; acknowledged = false }, enabled = !busy) {
            Text("Effacer les données de cet appareil", color = MaterialTheme.colorScheme.error)
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (dialog) AlertDialog(
        onDismissRequest = { if (!busy) dialog = false },
        title = { Text("Effacement local définitif") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Exportez d'abord votre bibliothèque depuis Plus. Les snapshots, réglages, accès, brouillons et changements non envoyés seront perdus. Aucune suppression n'est envoyée à Trakt.")
            OutlinedTextField(confirmation, { confirmation = it }, modifier=Modifier.testTag("privacy-confirmation"), label = { Text("Saisir EFFACER") }, singleLine = true, enabled = !busy)
            Row { Checkbox(acknowledged, { acknowledged = it }, modifier=Modifier.testTag("privacy-acknowledge"), enabled = !busy)
                Text("Je comprends que les modifications non synchronisées seront perdues.", Modifier.weight(1f)) }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(modifier=Modifier.testTag("privacy-erase"), enabled = !busy && confirmation == "EFFACER" && acknowledged, onClick = {
            runCatching {
                repo.prepareLocalErase(confirmation, acknowledged)
                busy = true
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                check(manager.clearApplicationUserData()) { "Android a refusé l'effacement. Aucune réussite n'est confirmée." }
                message = "Effacement demandé à Android ; l'application va se fermer."
            }.onFailure { repo.cancelLocalErase(); busy = false; message = it.message ?: "Effacement non confirmé." }
        }) { Text("Effacer définitivement") } },
        dismissButton = { TextButton(modifier=Modifier.testTag("privacy-cancel"), enabled = !busy, onClick = { dialog = false }) { Text("Annuler") } }
    )
}
