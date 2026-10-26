package com.franchiseguide.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.franchiseguide.app.data.AppRepository
import com.franchiseguide.app.ui.theme.FranchiseGuideTheme

/** This replaces the entire normal UI before its sync/notification side effects can run. */
@Composable
fun RecoveryScreen(repo: AppRepository) {
    val context=LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) message=runCatching {
            val raw=repo.rawRecoveryExport()?:error("Aucun original à exporter.")
            val output=context.contentResolver.openOutputStream(uri,"wt")?:error("Fichier de destination indisponible.")
            output.bufferedWriter(Charsets.UTF_8).use { it.write(raw) }
            "Copie brute exportée. L’original de l’application n’a pas été modifié."
        }.getOrElse { "Export impossible : ${it.message}" }
    }
    FranchiseGuideTheme(darkTheme=repo.settings.value.darkTheme) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("Récupération de la bibliothèque",style=MaterialTheme.typography.headlineSmall)
                Text(repo.recoveryMessage.value.orEmpty())
                Text("La synchronisation, les imports et les modifications sont bloqués. Ne désinstalle pas l’application avant d’avoir sauvegardé l’original.")
                Text("La copie brute peut contenir des informations privées. Ne la publie pas et ne la partage pas sans la vérifier.",color=MaterialTheme.colorScheme.error)
                Button(onClick={exporter.launch("FranchiseGuide-original-recovery.json")}) { Text("Exporter l’original intact") }
                message?.let { Text(it) }
            }
        }
    }
}
