/* File System Access target: local-only, opt-in and foreground-only. */
'use strict';
let directoryBackupConfig=FGDirectoryBackup.empty(),directoryBackupProblem='',directoryBackupJob=null,directoryBackupAbort=null,directoryBackupGeneration=0,directoryBackupRetryAt=0;
const directoryBackupStore=FGDirectoryBackup.createStore(globalThis.indexedDB);
function directoryBackupSupported(){return typeof window.showDirectoryPicker==='function'&&!!globalThis.crypto?.subtle&&!!globalThis.indexedDB;}
function directoryBackupBusy(){return directoryBackupJob!==null;}
function directoryBackupStop(){directoryBackupGeneration++;directoryBackupAbort?.abort();}
function directoryBackupRefresh(){
 const d=document.querySelector('#directoryBackupDialog');
 if(d?.open){d.querySelector('[data-directory-status]').textContent=directoryBackupDescription();return;}
 if(document.querySelector('dialog[open]'))return;
 if(overlay?.type==='today')renderToday();else if(!overlay&&screen==='more')renderMore();
}
function directoryBackupDescription(){
 if(!directoryBackupSupported())return 'Ce navigateur ne propose pas de dossier externe autorisable. Les exports manuels JSON et protégés restent disponibles.';
 if(directoryBackupProblem)return directoryBackupProblem;
 if(directoryBackupBusy())return 'Écriture puis relecture de la sauvegarde en cours…';
 const c=directoryBackupConfig;
 if(!c.handle)return 'Aucun dossier choisi. Aucun fichier externe n’est écrit automatiquement.';
 const last=c.verifiedAt===null?'aucune sauvegarde vérifiée':new Date(c.verifiedAt).toLocaleString('fr-FR');
 return `${c.enabled?'Activée':'Suspendue'} · tous les ${c.intervalDays} jour(s), pendant l’utilisation · dernière relecture : ${last}.`;
}
function directoryBackupBanner(){return `<section class="panel"><h3>Sauvegarde vers un dossier choisi</h3><p>${esc(directoryBackupDescription())}</p><button type="button" class="tonal" data-directory-backup>Configurer le dossier externe</button><p class="meta">Fichiers JSON non chiffrés, sans clés ni jetons. Aucune écriture lorsque l’application est fermée. Les fichiers créés sont conservés ; aucun ancien fichier n’est supprimé automatiquement.</p></section>`;}
function bindDirectoryBackup(host=document){host.querySelectorAll('[data-directory-backup]').forEach(b=>b.onclick=openDirectoryBackup);}
async function directoryBackupLoad(){
 if(!directoryBackupSupported())return;
 try{directoryBackupConfig=await directoryBackupStore.load();directoryBackupProblem='';}
 catch{directoryBackupProblem='Configuration du dossier illisible. Aucun dossier ni fichier n’a été remplacé. Oublie explicitement la destination pour la reconfigurer.';}
 directoryBackupRefresh();
}
async function directoryBackupRun(force=false){
 if(!directoryBackupSupported()||directoryBackupJob||document.hidden||!storageWritable||recoveryOnly)return;
 if(!force&&Date.now()<directoryBackupRetryAt)return;
 const generation=directoryBackupGeneration,abort=new AbortController();directoryBackupAbort=abort;
 async function execute(){
  assertWritable();const config=await directoryBackupStore.load();directoryBackupConfig=config;
  if(generation!==directoryBackupGeneration||!config.enabled||(!force&&!FGDirectoryBackup.due(config)))return;
  assertWritable();
  const snapshot=toPortableBackup(state);
  const next=await FGDirectoryBackup.writeVerified(config,snapshot,{crypto:globalThis.crypto,signal:abort.signal,validateSnapshot:FGCore.validateBackup});
  if(generation!==directoryBackupGeneration||abort.signal.aborted)return;
  assertWritable();
  // Success is published only after close + read-back + SHA-256, then an acknowledged IDB transaction.
  directoryBackupConfig=await directoryBackupStore.save(next,config.revision);
  const reminder=FGBackupSafety.proposed(backupSafetyRead(),'json',next.lastFile,next.verifiedAt);
  backupSafetyWrite(FGBackupSafety.verified(reminder,next.verifiedAt));
  directoryBackupProblem='';directoryBackupRetryAt=0;
  if(force)toast('Fichier externe écrit et relu à l’identique.');
 }
 directoryBackupJob=(async()=>{
  try{
   if(navigator.locks?.request)await navigator.locks.request('franchiseguide-external-backup-writer',{ifAvailable:true},lock=>lock?execute():undefined);
   else await execute();
  }catch(e){
   if(generation===directoryBackupGeneration&&!abort.signal.aborted){
    directoryBackupProblem=e.code==='PERMISSION'?e.message:'Sauvegarde externe non confirmée. Vérifie le dossier et ses permissions, puis réessaie. Un fichier partiel peut subsister.';
    directoryBackupRetryAt=Date.now()+5*60000;
    if(force)toast(directoryBackupProblem);
   }
  }finally{directoryBackupJob=null;directoryBackupAbort=null;directoryBackupRefresh();}
 })();
 return directoryBackupJob;
}
async function directoryBackupForget(){
 directoryBackupStop();if(directoryBackupJob)await directoryBackupJob;
 if(globalThis.indexedDB)await directoryBackupStore.forget();directoryBackupConfig=FGDirectoryBackup.empty();directoryBackupProblem='';directoryBackupRefresh();
}
function openDirectoryBackup(){
 const d=activityDialog('directoryBackupDialog','Sauvegardes externes',`<p>Choisis toi-même un dossier. Seule une bibliothèque portable, sans les clés, jetons ni files de synchronisation, y sera écrite. Les notes et profils de cette bibliothèque restent des données privées.</p><p class="notice warn">Les fichiers sont en clair. Pour un fichier chiffré par mot de passe, utilise « Exporter protégé ». Ce module ne se connecte pas à Google Drive et ne certifie pas qu’un dossier est synchronisé par un autre logiciel.</p><p data-directory-status role="status" aria-live="polite"></p><label>Fréquence<select id="directoryBackupPeriod"><option value="1">Chaque jour</option><option value="7">Chaque semaine</option><option value="14">Tous les 14 jours</option><option value="30">Tous les 30 jours</option></select></label><label class="import-ack"><input type="checkbox" id="directoryBackupConsent">J’autorise les sauvegardes automatiques non chiffrées dans le dossier que je choisis.</label><div class="actions"><button class="primary" id="directoryBackupChoose" type="button">Choisir le dossier et activer</button><button class="tonal" id="directoryBackupAllow" type="button">Autoriser à nouveau</button><button class="tonal" id="directoryBackupNow" type="button">Sauvegarder maintenant</button><button class="tonal" id="directoryBackupPause" type="button">Suspendre</button><button class="tonal" id="directoryBackupForget" type="button">Oublier ce dossier</button></div><p class="meta">La prochaine sauvegarde a lieu lorsque l’application est ouverte, visible et autorisée à écrire. Aucun fichier antérieur n’est supprimé ; surveille l’espace disponible dans le dossier. Oublier le dossier ne supprime pas les fichiers ni l’autorisation conservée par ton navigateur.</p>`);
 d.querySelector('#directoryBackupPeriod').value=String(directoryBackupConfig.intervalDays);
 d.querySelector('[data-directory-status]').textContent=directoryBackupDescription();
 for(const id of ['directoryBackupChoose','directoryBackupAllow','directoryBackupNow','directoryBackupPause'])d.querySelector('#'+id).disabled=!directoryBackupSupported();
 const report=e=>{directoryBackupProblem=e?.name==='AbortError'?'Choix annulé ; destination inchangée.':e?.message||'Action non confirmée.';directoryBackupRefresh();};
 d.querySelector('#directoryBackupChoose').onclick=async()=>{
  try{
   assertWritable();if(directoryBackupBusy())throw Error('Attends la fin de la sauvegarde en cours.');
   if(!d.querySelector('#directoryBackupConsent').checked)throw Error('Confirme explicitement les sauvegardes non chiffrées.');
   const intervalDays=Number(d.querySelector('#directoryBackupPeriod').value),generation=directoryBackupGeneration;
   const handle=await window.showDirectoryPicker({id:'franchiseguide-external-backups',mode:'readwrite'});
   if(!d.open||generation!==directoryBackupGeneration)return;
   assertWritable();const previous=await directoryBackupStore.load();
   if(!d.open||generation!==directoryBackupGeneration)return;assertWritable();
   directoryBackupConfig=await directoryBackupStore.save({...FGDirectoryBackup.empty(),handle,intervalDays,enabled:true},previous.revision);
   directoryBackupProblem='';directoryBackupRefresh();await directoryBackupRun(true);
  }catch(e){report(e);}
 };
 d.querySelector('#directoryBackupAllow').onclick=async()=>{
  try{
   assertWritable();const handle=directoryBackupConfig.handle;if(!handle)throw Error('Choisis d’abord un dossier.');
   const permission=await handle.requestPermission({mode:'readwrite'});
   if(permission!=='granted')throw Error('Permission non accordée. Aucun fichier écrit.');
   directoryBackupProblem='';directoryBackupRefresh();await directoryBackupRun(true);
  }catch(e){report(e);}
 };
 d.querySelector('#directoryBackupNow').onclick=()=>directoryBackupRun(true);
 d.querySelector('#directoryBackupPause').onclick=async()=>{
  try{assertWritable();directoryBackupStop();if(directoryBackupJob)await directoryBackupJob;
   const prior=await directoryBackupStore.load();directoryBackupConfig=await directoryBackupStore.save({...prior,enabled:false},prior.revision);directoryBackupProblem='';directoryBackupRefresh();
  }catch(e){report(e);}
 };
 d.querySelector('#directoryBackupForget').onclick=async()=>{try{assertWritable();await directoryBackupForget();}catch(e){report(e);}};
 d.showModal();
}
function startDirectoryBackup(){
 void directoryBackupLoad().then(()=>directoryBackupRun());
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)void directoryBackupRun();});
 setInterval(()=>{void directoryBackupRun();},60000);
}
