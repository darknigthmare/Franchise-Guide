package com.franchiseguide.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.franchiseguide.app.data.AppRepository
import com.franchiseguide.app.model.MediaItem
import com.franchiseguide.app.model.ReleaseRules
import kotlinx.coroutines.launch

@Composable
fun ContentLanguagePanel(repo:AppRepository,item:MediaItem) {
    var language by remember(item.id,item.contentLanguage){mutableStateOf(item.contentLanguage.orEmpty())}
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Langue de cette fiche",style=MaterialTheme.typography.titleMedium)
        Text("Vide = langue générale. L’actualisation conserve ton suivi ; elle ne change pas la langue de l’interface.")
        OutlinedTextField(language,{language=it.take(5)},Modifier.fillMaxWidth(),label={Text("fr-FR, en-US…")},enabled=!busy,singleLine=true)
        Button(enabled=!busy&&runCatching{ReleaseRules.contentLanguage(language)}.isSuccess,onClick={
            busy=true;scope.launch {
                repo.setContentLanguage(item.id,language).onSuccess{message="Fiche actualisée dans la langue demandée, selon les données disponibles."}.onFailure{message=it.message}
                busy=false
            }
        }){Text(if(busy)"Actualisation…" else "Appliquer et actualiser")}
        message?.let{Text(it)}
    }
}
