/* V0.8: library + pending Trakt changes in ONE localStorage record.
   Web Locks provides one editor on supported secure origins; revision checks reject stale writers. */
'use strict';
let storageRevision=Number(state._revision||0),storageDraft=null,storageWritable=true,writerRelease=null;
let storedSyncSnapshot=null;
function assertWritable(){
  if(recoveryOnly){const e=Error(loadIssue||'Bibliothèque à récupérer : écriture bloquée.');e.code='RECOVERY_ONLY';throw e;}
  if(!storageWritable){const e=Error('Cette fenêtre est en lecture seule. Ferme l’autre fenêtre puis recharge celle-ci.');e.code='READ_ONLY';throw e;}
  const raw=localStorage.getItem(KEY);
  if(!raw&&storageRevision>0){const e=Error('Les données enregistrées ont été supprimées ailleurs. Recharge ou exporte ta copie avant de poursuivre.');e.code='STALE_WRITE';throw e;}
  if(raw){let disk;try{disk=JSON.parse(raw);}catch{throw Error('La bibliothèque enregistrée est illisible. Aucun remplacement automatique autorisé.');}
    if(disk.schema!=null&&(!Number.isInteger(disk.schema)||disk.schema<1||disk.schema>11)){const e=Error('Le stockage utilise une autre version. Aucun écrasement autorisé.');e.code='STALE_WRITE';throw e;}
    if(Number(disk._revision||0)!==storageRevision){const e=Error('La bibliothèque a changé dans une autre fenêtre. Recharge avant de modifier.');e.code='STALE_WRITE';throw e;}
  }
}
function persistEnvelope(serviceRef){
  assertWritable();
  const persisted=clone(state),raw=localStorage.getItem(KEY),previous=raw?JSON.parse(raw):{};
  if(serviceRef===undefined)serviceRef=previous._servicesRef;
  if(!persisted._traktAppRef&&previous._traktAppRef)persisted._traktAppRef=previous._traktAppRef;
  FGSettings.reference(serviceRef);FGSettings.reference(persisted._traktAppRef);if(serviceRef)FGSettings.read(localStorage.getItem(SERVICES_KEY),serviceRef);
  if(serviceRef)persisted._servicesRef=serviceRef;else delete persisted._servicesRef;
  for(const k of ['tmdbKey','rawgKey','traktId','traktSecret','accessToken','refreshToken'])delete persisted.settings[k];
  persisted._revision=storageRevision+1;persisted._syncState=clone(syncState||{queue:[],baseline:{},remote:[],errors:[]});
  localStorage.setItem(KEY,JSON.stringify(persisted)); // Atomic for this key; failure leaves the old value intact.
  storageRevision=persisted._revision;state._revision=storageRevision;if(persisted._traktAppRef)state._traktAppRef=persisted._traktAppRef;if(serviceRef)state._servicesRef=serviceRef;else delete state._servicesRef;
  storedSyncSnapshot=clone(persisted._syncState);delete state._syncState;
}
function storageFailure(error){
  storageDraft=clone(state);
  state=clone(savedState);state._revision=storageRevision;
  if(storedSyncSnapshot)syncState=clone(storedSyncSnapshot);
  if(['READ_ONLY','STALE_WRITE','RECOVERY_ONLY','SERVICES_RECOVERY'].includes(error.code)){storageWritable=false;showStorageBanner(error.message);}
  else {showStorageBanner('Enregistrement refusé : stockage indisponible ou plein. La dernière version enregistrée est conservée.');}
  diagnostic('error','storage',error.message);toast(error.message);
  try{if(typeof FGDurable!=='undefined'&&FGDurable.available())void FGDurable.put(toPortableBackup(storageDraft||state),'Échec du stockage principal').then(()=>diagnostic('info','storage','Copie de récupération IndexedDB créée.')).catch(e=>diagnostic('warn','storage','Copie IndexedDB impossible : '+e.message));}catch{}
}
function showStorageBanner(text){
  let host=$('#storageBanner');if(!host){host=document.createElement('section');host.id='storageBanner';host.className='storage-banner';host.setAttribute('role','alert');($('#appShell')||document.body).prepend(host);}
  const shell=$('#appShell');if(shell&&host.parentElement!==shell)shell.prepend(host);
  host.innerHTML=`<strong>${esc(text)}</strong><div><button id="reloadStorage" class="tonal">Recharger les données enregistrées</button><button id="exportDraft" class="tonal">Exporter ma copie de travail</button></div>`;
  if(recoveryOnly?.raw!=null){
    const note=document.createElement('p');note.textContent='Copie brute non nettoyée : elle peut contenir des données privées. Ne la partage pas publiquement.';host.append(note);
    const rawButton=document.createElement('button');rawButton.id='exportOriginal';rawButton.className='tonal';rawButton.textContent='Exporter le fichier original intact';host.append(rawButton);
    rawButton.onclick=()=>{const u=URL.createObjectURL(new Blob([recoveryOnly.raw],{type:'application/json'}));const a=document.createElement('a');a.href=u;a.download='franchiseguide-original-recuperation.json';a.click();setTimeout(()=>URL.revokeObjectURL(u),5000);};
  }
  $('#reloadStorage').onclick=()=>location.reload();$('#exportDraft').onclick=()=>downloadJson('franchiseguide-copie-recuperation.json',toPortableBackup(storageDraft||state));
}
async function protectWriter(){
  if(recoveryOnly){storageWritable=false;showStorageBanner(loadIssue);return;}
  if(!navigator.locks?.request)return; // Legacy browsers: stale-revision checks still apply; keep only one editing window.
  storageWritable=false;
  return new Promise(resolve=>{
    navigator.locks.request('franchiseguide-library-editor',{ifAvailable:true},async lock=>{
      if(!lock){showStorageBanner('Une autre fenêtre modifie cette bibliothèque. Celle-ci reste consultable en lecture seule.');resolve();return;}
      storageWritable=true;resolve();await new Promise(r=>{writerRelease=r;});storageWritable=false;
    }).catch(()=>{storageWritable=false;showStorageBanner('Le verrou de stockage est indisponible. Recharge pour réessayer.');resolve();});
  });
}
window.addEventListener('pagehide',()=>{writerRelease?.();writerRelease=null;});
window.addEventListener('pageshow',e=>{if(e.persisted)location.reload();});
function suspendStorageEditing(message){
 storageWritable=false;libraryEpoch++;authGeneration++;clearTimeout(traktSaveTimer);cancelTraktRequests();refreshPromise=null;
 if(typeof invalidateSearch==='function')invalidateSearch();
 if(typeof invalidateCatalogueReads==='function')invalidateCatalogueReads();
 if(typeof invalidateGamingReads==='function')invalidateGamingReads();
 if(typeof episodeSheetForget==='function')episodeSheetForget();
 if(typeof availabilityForget==='function')availabilityForget();
 showStorageBanner(message);
}
window.addEventListener('storage',e=>{
 if(e.storageArea!=null&&e.storageArea!==localStorage)return;
 if(e.key===null||e.key===KEY){suspendStorageEditing('Les données ont changé ou ont été effacées ailleurs. Exporte ta copie ou recharge avant de modifier.');return;}
 if(e.key===SERVICES_KEY){try{const access=FGSettings.read(localStorage.getItem(SERVICES_KEY),state._servicesRef,state.settings);if(!FGSettings.same(access,serviceSettings()))suspendStorageEditing('Les accès sélectionnés ont changé ailleurs. Recharge avant de modifier.');}catch{suspendStorageEditing('Les accès locaux ont disparu ou sont illisibles. Écriture suspendue.');}return;}
 if(e.key===TRAKT_AUTH_KEY){authGeneration++;cancelTraktRequests();clearTimeout(traktSaveTimer);refreshPromise=null;updateTraktUi();}
});
