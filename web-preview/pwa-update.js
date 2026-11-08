/* Explicit updates. No old tab may keep writing after its controller has changed. */
'use strict';
const FGPwaUpdate=(()=>{
 let changed=false,requested=false,installed=false;
 function blockReason(){
  if(storageDraft||recoveryOnly)return 'Exporte ou récupère d’abord la bibliothèque avant la mise à jour.';
  if(traktSyncing||checkInBusy||[...reviewSessions.values()].some(s=>s.sending))return 'Une opération distante est en cours. Termine-la avant la mise à jour.';
  if(document.querySelector('dialog[open]'))return 'Ferme ou enregistre le formulaire ouvert avant la mise à jour.';
  if(typeof directoryBackupBusy==='function'&&directoryBackupBusy())return 'Une sauvegarde externe est en cours. Réessaie après sa vérification.';
  return null;
 }
 function suspend(){
  changed=true;storageWritable=false;
  authGeneration++;libraryEpoch++;searchGeneration++;discoverGeneration++;
  clearTimeout(traktSaveTimer);
  if(typeof todayTimer!=='undefined')clearInterval(todayTimer);
  FGRequests.clear();
  if(typeof cancelTraktRequests==='function')cancelTraktRequests();
  if(typeof invalidateGamingReads==='function')invalidateGamingReads();
  if(typeof directoryBackupStop==='function')directoryBackupStop();
 }
 function staleBanner(){
  let panel=document.querySelector('#appVersionChanged');if(panel)return;
  panel=document.createElement('section');panel.id='appVersionChanged';panel.className='update-banner';panel.setAttribute('role','alert');
  panel.innerHTML='<span>Une autre version a pris le contrôle. Cet ancien onglet ne peut plus modifier ni synchroniser les données. Exporte ta copie si nécessaire, puis recharge.</span><button class="tonal" data-version-export>Exporter ma copie</button><button class="primary" data-version-reload>Recharger</button>';
  panel.querySelector('[data-version-export]').onclick=()=>exportFg();
  panel.querySelector('[data-version-reload]').onclick=()=>{if(!document.querySelector('dialog[open]')||confirm('Les modifications non enregistrées du formulaire seront perdues. Recharger ?'))location.reload();};
  document.body.prepend(panel);
 }
 async function activate(worker){
  if(!worker)throw Error('La mise à jour n’est plus disponible. Recharge la page.');
  return new Promise((resolve,reject)=>{
   const channel=new MessageChannel();
   const timer=setTimeout(()=>{channel.port1.close();reject(Error('Activation non confirmée. Tu peux réessayer sans effacer les données.'));},10000);
   channel.port1.onmessage=e=>{clearTimeout(timer);channel.port1.close();resolve(e.data);};
   try{worker.postMessage({type:'ACTIVATE_UPDATE'},[channel.port2]);}catch(error){clearTimeout(timer);channel.port1.close();reject(error);}
  });
 }
 function install(){
  if(installed||!('serviceWorker'in navigator))return;installed=true;
  let previous=navigator.serviceWorker.controller;
  navigator.serviceWorker.addEventListener('controllerchange',()=>{
   const current=navigator.serviceWorker.controller;
   if(previous&&current!==previous){suspend();if(requested)location.reload();else staleBanner();}
   previous=current;
  });
  navigator.serviceWorker.register('sw.js',{updateViaCache:'none'}).then(reg=>{
   function offer(){
    if(!reg.waiting||!navigator.serviceWorker.controller||document.querySelector('#appUpdate')||changed)return;
    const panel=document.createElement('section');panel.id='appUpdate';panel.className='update-banner';panel.setAttribute('role','status');
    panel.innerHTML='<span>Une mise à jour est prête. Ferme les autres onglets FranchiseGuide avant de l’activer.</span><button class="tonal" data-pwa-update>Enregistrer et recharger</button>';
    const button=panel.querySelector('button');
    button.onclick=async()=>{
     const reason=blockReason();if(reason){toast(reason);return;}
     button.disabled=true;
     try{
      if(storageWritable)save(false);
      requested=true;
      const result=await activate(reg.waiting);
      if(!result?.ok){requested=false;toast(result?.reason==='other-tabs'?'Ferme les autres onglets ou fenêtres FranchiseGuide, puis réessaie. Aucune mise à jour forcée.':'La mise à jour n’a pas été activée. Recharge puis réessaie.');}
     }catch(error){requested=false;toast(error.message);}
     finally{button.disabled=false;}
    };
    document.body.append(panel);
   }
   offer();reg.addEventListener('updatefound',()=>reg.installing?.addEventListener('statechange',offer));
  }).catch(()=>diagnostic('warn','pwa','Cache hors-ligne indisponible sur cette origine.'));
 }
 return {install,changed:()=>changed,blockReason};
})();
