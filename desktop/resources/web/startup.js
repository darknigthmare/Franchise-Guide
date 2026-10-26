/* Application startup and user preferences. Loaded last, after all feature modules. */
'use strict';
syncState=state._syncState||readJsonLocal(SYNC_KEY,{queue:[],baseline:{},remote:[],errors:[]});
storedSyncSnapshot=clone(syncState);delete state._syncState;
syncState.queue=Array.isArray(syncState.queue)?syncState.queue:[];
syncState.baseline=syncState.baseline||{};syncState.remote=syncState.remote||[];syncState.errors=syncState.errors||[];
const previousTheme=theme;
function fillExtraSettings() {
  $('#specials').checked=!!state.settings.includeSpecials;
  $('#hiddenCalendar').checked=!!state.settings.includeHiddenInCalendar;
  $('#recentDays').value=state.settings.recentDays??30;
  $('#upcomingDays').value=state.settings.upcomingDays??60;
  $('#autoBackup').checked=state.settings.autoBackupEnabled!==false;
  $('#backupMinutes').value=state.settings.autoBackupMinutes||30;
  $('#catalogAuto').checked=state.settings.catalogAutoRefresh!==false;
  $('#jingleVolume').value=state.settings.detailJingleVolume??42;
}
const settingsButtonHandler=()=>{previousTheme();fillExtraSettings();beginSettingsEditor();$('#settingsDialog').showModal();};
$('#settings').onclick=settingsButtonHandler;
$('#settingsDialog').addEventListener('close',()=>{if($('#settingsDialog').open)return;endSettingsEditor();if(!traktConnected()){authGeneration++;cancelTraktRequests();$('#traktDevice').classList.add('hidden');$('#traktConnect').disabled=false;}});
$('#settingsDialog').addEventListener('cancel',e=>{if(!canCloseSettings())e.preventDefault();else endSettingsEditor();});
$('#settingsDialog .close').onclick=()=>{if(canCloseSettings()){endSettingsEditor();$('#settingsDialog').close();}};
$('#saveSettings').onclick=commitSettingsEditor;
$('#backupNowSettings').onclick=()=>{toast(createBackup('Manuelle')?'Sauvegarde locale créée.':'Échec de sauvegarde : exporte le fichier JSON.');};
$('#traktDisconnect').onclick=()=>{
  if(!confirm('Déconnecter Trakt de cet appareil ? Les données locales restent intactes et aucune action ne sera envoyée tant que le compte est déconnecté.'))return;
  clearTraktAuth();toast('Trakt déconnecté');
};
$('#back').setAttribute('aria-label','Retour');$('#search').setAttribute('aria-label','Rechercher / ajouter un titre');$('#settings').setAttribute('aria-label','Ouvrir les réglages');
$('#q').setAttribute('aria-label','Titre à rechercher');$('#toast').setAttribute('role','status');$('#toast').setAttribute('aria-live','polite');
$$('dialog .close').forEach(b=>b.setAttribute('aria-label','Fermer'));
$$('#tmdb,#rawg').forEach(x=>{x.type='password';x.autocomplete='off';});
window.addEventListener('error',e=>{if(e.message)diagnostic('error','javascript',e.message);});
window.addEventListener('unhandledrejection',e=>{diagnostic('error','promise',e.reason?.message||'Action interrompue');toast(e.reason?.message||'Action interrompue : voir Diagnostic.');});
window.addEventListener('online',()=>{document.body.classList.remove('offline');scheduleInitialTraktSync();});
window.addEventListener('offline',()=>{document.body.classList.add('offline');toast('Hors ligne : les données déjà chargées restent consultables.');});
document.addEventListener('visibilitychange',()=>{if(!document.hidden){scheduleInitialTraktSync();maybeBackup();}});
setInterval(()=>{if(!document.hidden){scheduleInitialTraktSync();maybeBackup();}},60000);
// Remove secrets from historical automatic backups, without deleting the user's library.
try {
  if(recoveryOnly)throw Error('Mode récupération : aucun nettoyage automatique.');
  const old=backups();
  if(old.some(b=>b.state?.settings))localStorage.setItem(BACKUP_KEY,JSON.stringify(old.map(b=>({...b,state:toPortableBackup(migrate(b.state))})).slice(0,5)));
  for(const key of LEGACY_KEYS) {
    const raw=readJsonLocal(key,null);if(!raw?.settings)continue;
    for(const k of ['tmdbKey','rawgKey','traktId','traktSecret','accessToken','refreshToken'])delete raw.settings[k];
    localStorage.setItem(key,JSON.stringify(raw));
  }
}catch{diagnostic('warn','storage','Nettoyage des anciens exports non terminé.');}
const requestedSection=location.hash.slice(1);if(railItems.some(x=>x[0]===requestedSection))screen=requestedSection;
window.addEventListener('hashchange',()=>{const route=location.hash.slice(1);if(railItems.some(x=>x[0]===route)){screen=route;overlay=null;tab=0;render();}});
document.body.classList.toggle('offline',navigator.onLine===false);
previousTheme();fillExtraSettings();render();
protectWriter().then(()=>{if(storageWritable){try{save(false);}catch{}scheduleCatalogRepair();scheduleInitialTraktSync();if(typeof bootstrapSteamConnection==='function')void bootstrapSteamConnection();if(typeof checkRetroBridge==='function')void checkRetroBridge().then(()=>retroForeground());}});
if('serviceWorker'in navigator) {
  let updateRequested=false;
  navigator.serviceWorker.addEventListener('controllerchange',()=>{if(updateRequested)location.reload();});
  navigator.serviceWorker.register('sw.js').then(reg=>{
    function offer(){
      if(!reg.waiting||!navigator.serviceWorker.controller||$('#appUpdate'))return;
      const panel=document.createElement('section');panel.id='appUpdate';panel.className='update-banner';panel.setAttribute('role','status');
      panel.innerHTML='<span>Une mise à jour de FranchiseGuide est prête.</span><button class="tonal">Enregistrer et recharger</button>';
      panel.querySelector('button').onclick=()=>{
        if(storageDraft){toast('Exporte ou recharge d’abord ta copie de récupération avant la mise à jour.');return;}
        try{if(storageWritable)save(false);updateRequested=true;reg.waiting?.postMessage({type:'ACTIVATE_UPDATE'});}catch{}
      };
      document.body.append(panel);
    }
    offer();reg.addEventListener('updatefound',()=>reg.installing?.addEventListener('statechange',offer));
  }).catch(()=>diagnostic('warn','pwa','Cache hors-ligne indisponible sur cette origine.'));
}

if(loadIssue){diagnostic('error','recovery',loadIssue);toast(loadIssue);}


if(typeof FGDurable!=='undefined'&&FGDurable.available()){
  FGDurable.latest().then(r=>{
    if(!r||recoveryOnly||state.media.length||state.episodes.length)return;
    const count=(r.state?.media||[]).filter(m=>m.added!==false).length;if(!count)return;
    let host=document.querySelector('#durableRecoveryBanner');if(host)return;
    host=document.createElement('section');host.id='durableRecoveryBanner';host.className='storage-banner';host.setAttribute('role','status');
    host.innerHTML=`<strong>Une copie de récupération IndexedDB contient ${count} titre(s).</strong><div><button class="tonal">Ouvrir Données & hors ligne</button></div>`;
    host.querySelector('button').onclick=()=>{overlay={type:'maintenance',title:'Données & hors ligne'};render();};document.body.prepend(host);
  }).catch(()=>{});
}


try{
  const integrity=FGIntegrity.audit(state);
  if(integrity.counts.critical||integrity.counts.warning){
    diagnostic(integrity.counts.critical?'error':'warn','integrity',`${integrity.counts.critical} critique(s), ${integrity.counts.warning} avertissement(s)`);
    let host=document.querySelector('#integrityBanner');
    if(!host){host=document.createElement('section');host.id='integrityBanner';host.className='storage-banner';host.setAttribute('role','alert');host.innerHTML=`<strong>Contrôle d’intégrité : ${integrity.counts.critical} critique(s), ${integrity.counts.warning} avertissement(s).</strong><div><button class="tonal">Examiner</button></div>`;host.querySelector('button').onclick=()=>{overlay={type:'integrity',title:'Intégrité des données'};render();};document.body.prepend(host);}
  }
}catch(e){diagnostic('warn','integrity','Audit initial indisponible : '+e.message);}
