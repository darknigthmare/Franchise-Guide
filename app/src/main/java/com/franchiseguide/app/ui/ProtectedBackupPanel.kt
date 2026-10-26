package com.franchiseguide.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.franchiseguide.app.data.AppRepository
import com.franchiseguide.app.model.ProtectedBackup
import com.franchiseguide.app.model.PreparedLibraryImport
import com.franchiseguide.app.model.ImportSnapshot
import kotlinx.coroutines.*

/** UI source only until an Android SDK/device build is performed. Secrets are never rememberSaveable. */
@Composable
fun ProtectedBackupPanel(repo:AppRepository,onPrepared:(PreparedLibraryImport)->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var pass by remember{mutableStateOf("")};var confirmation by remember{mutableStateOf("")};var understood by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)};var hasFile by remember{mutableStateOf(false)}
    val chosen=remember{arrayOfNulls<ByteArray>(1)};val pending=remember{arrayOfNulls<ByteArray>(1)}
    var token by remember{mutableStateOf<Pair<Int,ImportSnapshot>?>(null)};var opened by remember{mutableLongStateOf(0L)}
    var task by remember{mutableStateOf<Job?>(null)};var ticket by remember{mutableIntStateOf(0)}
    fun checkSnapshot(expected:Pair<Int,ImportSnapshot>,at:Long){check(System.currentTimeMillis()-at in 0..600000) {"Formulaire expiré. Recommence."};repo.checkProtectedState(expected)}
    DisposableEffect(Unit){onDispose{ticket++;task?.cancel();pass="";confirmation="";chosen[0]?.fill(0);chosen[0]=null;pending[0]?.fill(0);pending[0]=null}}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->
        val bytes=pending[0];pending[0]=null
        if(bytes!=null){if(uri==null){bytes.fill(0);busy=false;message="Enregistrement annulé."}else task=scope.launch {
            try{withContext(Dispatchers.IO){val out=context.contentResolver.openOutputStream(uri,"wt")?:error("Destination inaccessible.");out.use{it.write(bytes);it.flush()}};repo.noteExternalBackupProposed("protected","franchiseguide-protege.fgbackup");message="Fichier protégé proposé. Vérifie sa présence dans Aujourd’hui et conserve sa phrase séparément."}
            catch(e:CancellationException){throw e}catch(e:Exception){message="Écriture impossible ; le fichier de destination peut être incomplet. La bibliothèque n’a pas changé."}
            finally{bytes.fill(0);busy=false}
        }}
    }
    val choose=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null){val mine=++ticket;busy=true;val before=repo.protectedState();val at=System.currentTimeMillis();task=scope.launch{
            var data:ByteArray?=null
            try{withContext(Dispatchers.IO){data=ProtectedBackup.readBounded(context.contentResolver.openInputStream(uri)?:error("Fichier inaccessible."));ProtectedBackup.inspect(data!!)}
                if(mine==ticket){checkSnapshot(before,at);chosen[0]?.fill(0);chosen[0]=data;data=null;token=before;opened=at;hasFile=true;pass="";confirmation="";message="Fichier reconnu. Entre sa phrase secrète pour préparer l’aperçu."}
            }catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"Fichier refusé, bibliothèque inchangée."}
            finally{data?.fill(0);if(mine==ticket)busy=false}
        }}
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Sauvegarde protégée par une phrase secrète",style=MaterialTheme.typography.headlineSmall)
        Text("Chiffre uniquement le fichier exporté. La bibliothèque ouverte, ses anciennes copies et ses identifiants locaux ne sont pas chiffrés par cette action. Si tu oublies la phrase, aucune récupération de ce fichier n’est possible.")
        OutlinedButton(enabled=!busy,onClick={message=null;choose.launch(arrayOf("application/octet-stream","*/*"))}){Text("Choisir un fichier .fgbackup à déchiffrer")}
        if(hasFile)TextButton(enabled=!busy,onClick={chosen[0]?.fill(0);chosen[0]=null;token=null;hasFile=false;pass="";confirmation="";message=null}){Text("Retirer le fichier sélectionné et revenir à l’export")}
        OutlinedTextField(value=pass,onValueChange={pass=it},modifier=Modifier.fillMaxWidth(),enabled=!busy,label={Text("Phrase secrète · 12 à 256 caractères")},singleLine=true,visualTransformation=PasswordVisualTransformation())
        if(!hasFile){OutlinedTextField(value=confirmation,onValueChange={confirmation=it},modifier=Modifier.fillMaxWidth(),enabled=!busy,label={Text("Confirmer la phrase")},singleLine=true,visualTransformation=PasswordVisualTransformation())
            Row{Checkbox(checked=understood,onCheckedChange={understood=it},enabled=!busy);Text("Je comprends qu’un mot de passe perdu ne peut pas être réinitialisé pour ce fichier.")}}
        Text("Les espaces, accents et emoji sont significatifs. Aucun mot de passe n’est enregistré ou envoyé. L’import présente toujours un aperçu avant toute application. Limite : 32 Mio de JSON, plus 64 octets de protection.",style=MaterialTheme.typography.bodySmall)
        Button(enabled=!busy,onClick={
            val password=pass.toCharArray()
            try{ProtectedBackup.validatePassword(password);if(!hasFile){check(understood){"Confirme l’avertissement."};check(pass==confirmation){"Les phrases ne correspondent pas."}}}
            catch(e:Exception){password.fill('\u0000');pass="";confirmation="";message=e.message;return@Button}
            val importing=hasFile;val before=if(importing)token?:repo.protectedState() else repo.protectedState();val at=if(importing)opened else System.currentTimeMillis()
            pass="";confirmation="";busy=true;message=if(importing)"Déchiffrement et validation…" else "Chiffrement local…";val mine=++ticket
            task=scope.launch {
                var plain:ByteArray?=null;var result:ByteArray?=null
                try{checkSnapshot(before,at)
                    if(importing){val encrypted=chosen[0]?.copyOf()?:error("Fichier retiré.")
                        try{withContext(Dispatchers.Default){plain=ProtectedBackup.unprotect(encrypted,password)}}finally{encrypted.fill(0)}
                        if(mine!=ticket)return@launch;checkSnapshot(before,at)
                        val review=withContext(Dispatchers.Default){repo.preparePortableImport(ProtectedBackup.decode(plain!!),listOf("Sauvegarde protégée"))}.getOrThrow()
                        checkSnapshot(before,at);chosen[0]?.fill(0);chosen[0]=null;hasFile=false;onPrepared(review)
                    }else{
                        plain=repo.exportJson().toByteArray(Charsets.UTF_8);checkSnapshot(before,at)
                        withContext(Dispatchers.Default){result=ProtectedBackup.protect(plain!!,password)}
                        if(mine!=ticket)return@launch;checkSnapshot(before,at);pending[0]=result;result=null;save.launch("franchiseguide-protege.fgbackup")
                    }
                }catch(e:CancellationException){throw e}catch(e:Exception){if(mine==ticket)message=e.message?:"Protection impossible, aucun import appliqué."}
                finally{password.fill('\u0000');plain?.fill(0);result?.fill(0);if(mine==ticket&&pending[0]==null)busy=false}
            }
        }){Text(if(hasFile)"Déchiffrer et préparer l’aperçu" else "Chiffrer et enregistrer")}
        if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick={ticket++;task?.cancel();pass="";confirmation="";pending[0]?.fill(0);pending[0]=null;message="Annulation demandée. Le calcul cryptographique natif peut encore se terminer sans appliquer son résultat.";task?.invokeOnCompletion{busy=false}}){Text("Annuler")}}
        message?.let{Text(it,style=MaterialTheme.typography.bodyMedium)}
    }
}
