package com.franchiseguide.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.franchiseguide.app.BuildConfig
import com.franchiseguide.app.data.AppRepository

@Composable
fun NativeSupportPanel(repo:AppRepository) {
    val context=LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<String?>(null) }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val snapshot=report;report=null
        if(uri!=null && snapshot!=null) message=runCatching {
            val stream=context.contentResolver.openOutputStream(uri,"wt")?:error("Destination indisponible.")
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(snapshot) }
            "Rapport technique exporté. Aucun envoi automatique."
        }.getOrElse { "Export du rapport non confirmé." }
    }
    Column {
        Text("Assistance · ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.titleMedium)
        Text(repo.diagnosticsText())
        OutlinedButton(onClick={report=repo.supportReport(BuildConfig.VERSION_NAME);launcher.launch("FranchiseGuide-diagnostic.json")}) { Text("Exporter le rapport technique") }
        message?.let { Text(it) }
    }
}
