/* One persisted Trakt check-in per local origin. Remote writes are never retried blindly. */
'use strict';
const CHECKIN_KEY='franchiseguide-trakt-checkin-v1',CHECKIN_RECOVERY_KEY='franchiseguide-trakt-checkin-recovery-v1';
let checkInLoadError=null,checkInBusy=false;
function checkInAccount(){return traktAuth().username||traktAuth().displayName||syncAccount();}
function checkInAppRef(){return state._traktAppRef||null;}
function checkInRead(){
 let raw;try{raw=localStorage.getItem(CHECKIN_KEY);}catch(e){checkInLoadError='Stockage de la session inaccessible : '+e.message;return null;}
 if(!raw){checkInLoadError=null;return null;}
 try{const session=FGCheckIn.decode(raw);checkInLoadError=null;return session;}
 catch(e){checkInLoadError=e.message;try{if(!localStorage.getItem(CHECKIN_RECOVERY_KEY))localStorage.setItem(CHECKIN_RECOVERY_KEY,raw);}catch{}return null;}
}
function checkInWrite(session){try{if(session)localStorage.setItem(CHECKIN_KEY,FGCheckIn.encode(session));else localStorage.removeItem(CHECKIN_KEY);checkInLoadError=null;return true;}catch(e){toast('Session de check-in non enregistrée : '+e.message);return false;}}
function checkInSameTarget(session,kind,id){return !!session&&session.kind===kind&&session.localId===id;}
function checkInCurrent(){return checkInRead();}
function checkInControlsLabel(kind,id){const s=checkInRead();if(!s)return '▶ Check-in Trakt';if(checkInSameTarget(s,kind,id))return '◉ Check-in en cours';return '◉ Voir le check-in actif';}
function definitiveCheckInError(e){return Number.isInteger(e?.status)&&[400,401,403,404,410,412,413,415,422,423,429].includes(e.status);}
function checkInRefresh(){if(overlay?.type==='today')renderToday();else render();}
function checkInFailure(message,code){return Object.assign(Error(message),{code});}
function checkInRequireContext(session){
 assertWritable();
 if(!session)throw Error(checkInLoadError||'Aucun check-in actif.');
 if(!traktConnected())throw Error('Connecte le compte Trakt de cette session dans Réglages.');
 if(!FGCheckIn.context(session,checkInAccount(),checkInAppRef()))throw checkInFailure('Cette session appartient à un autre compte ou une autre application Trakt.','CHECKIN_CONTEXT');
 if(traktSyncing)throw Error('Synchronisation Trakt en cours. Réessaie après sa fin.');
}
function checkInRequireHistoryClear(session){
 const key=session.kind+':'+session.tmdbId;
 if(syncState?.queue?.some(op=>op.key===key&&op.field==='history'))throw Error('Ce titre possède un visionnage Trakt en attente. Résous-le dans le centre de synchronisation avant le check-in.');
}
function checkInOperation(session){
 checkInRequireContext(session);
 return {session,raw:localStorage.getItem(CHECKIN_KEY),generation:authGeneration,epoch:libraryEpoch};
}
function checkInAssertOperation(op){
 checkInRequireContext(op.session);
 if(op.generation!==authGeneration||op.epoch!==libraryEpoch||localStorage.getItem(CHECKIN_KEY)!==op.raw)throw checkInFailure('La session, le compte ou la bibliothèque a changé. Rouvre le check-in pour vérifier son résultat.','CHECKIN_STALE');
}
function checkInCommit(session,op=null){
 if(op)checkInAssertOperation(op);
 if(!checkInWrite(session))throw checkInFailure('Le résultat doit rester récupérable. Rétablis le stockage puis vérifie la session.','CHECKIN_STORAGE');
 if(op)op.raw=localStorage.getItem(CHECKIN_KEY);
}
function checkInRemember(session,op){
 // A late response must not restore an old session over another account/tab.
 try{checkInCommit(session,op);return true;}catch{return false;}
}
async function startTraktCheckIn(kind,id){
 if(checkInBusy)return;let session=null,op=null;
 try{
  assertWritable();if(!traktConnected())throw Error('Connecte ton compte Trakt dans Réglages.');
  const existing=checkInRead();if(checkInLoadError)throw Error('Récupère ou oublie explicitement la session illisible avant d’en démarrer une autre.');
  if(existing){overlay={type:'today',title:'Aujourd’hui'};return render();}
  const target=FGCheckIn.target(state,kind,id);checkInRequireHistoryClear(target);
  if(traktSyncing)throw Error('Synchronisation Trakt en cours. Réessaie après sa fin.');
  if(!confirm(`Démarrer un check-in Trakt pour « ${target.title}${target.code?' · '+target.code:''} » ? Aucun visionnage local ne sera ajouté avant confirmation.`))return;
  session=FGCheckIn.create(target,checkInAccount(),checkInAppRef(),Date.now());checkInCommit(session);op=checkInOperation(session);
  checkInBusy=true;overlay={type:'today',title:'Aujourd’hui'};render();
  try{
   checkInAssertOperation(op);
   const response=await traktApi('/checkin',{method:'POST',body:JSON.stringify(FGCheckIn.body(session))});
   checkInAssertOperation(op);session=FGCheckIn.startConfirmed(session,response,Date.now());checkInCommit(session,op);toast('Check-in Trakt démarré.');
  }catch(e){
   if(definitiveCheckInError(e)){if(checkInRemember(null,op))toast('Check-in refusé : '+e.message);else toast(e.message);}
   else{checkInRemember(FGCheckIn.uncertain(session,'start',e.message),op);toast('Résultat du démarrage incertain : vérifie avant de relancer.');}
  }
 }catch(e){toast(e.message);}finally{if(op){checkInBusy=false;checkInRefresh();}}
}
function checkInApplyConfirmed(session,at){const next=FGCheckIn.apply(state,session,at);state=next;save(false);savedState=clone(state);}
async function checkInCleanup(session,op){
 try{
  checkInAssertOperation(op);await traktResponse('/checkin',{method:'DELETE'});checkInAssertOperation(op);checkInCommit(null,op);return true;
 }catch(e){
  if(e.status===404){checkInCommit(null,op);return true;}
  checkInRemember({...session,lastError:String(e.message).slice(0,800)},op);
  toast('Visionnage confirmé ; nettoyage distant à réessayer. '+e.message);return false;
 }
}
async function completeTraktCheckIn(forceVerify=false){
 if(checkInBusy)return;let session=checkInRead(),op=null,mode=null,historyConfirmed=false;
 try{
  checkInRequireContext(session);
  const selected=FGCheckIn.completionMode(session,Date.now());
  if(selected==='cleanup'){await retryCheckInCleanup();return;}
  checkInRequireHistoryClear(session);mode=forceVerify?'verify':selected;op=checkInOperation(session);checkInBusy=true;
  if(mode==='write'){session=FGCheckIn.beginFinish(session,Date.now());checkInCommit(session,op);}
  renderToday();
  if(mode==='verify'){
   checkInAssertOperation(op);
   const plural=session.kind==='episode'?'episodes':'movies',rows=await traktApi(`/users/me/history/${plural}?limit=100`);
   checkInAssertOperation(op);const at=FGCheckIn.recentMatch(session,rows,Date.now());
   if(at==null){
    const error='Aucun historique correspondant visible dans les 100 entrées récentes. Aucun ajout envoyé pour éviter un doublon.';
    session=selected==='write'?{...session,lastError:error}:FGCheckIn.uncertain(session,'finish',error);
    checkInCommit(session,op);toast('Historique Trakt non confirmé. Aucun visionnage local ou distant ajouté.');return;
   }
   session=FGCheckIn.remoteHistoryFinish(session,at);checkInCommit(session,op);historyConfirmed=true;
  }else{
   checkInAssertOperation(op);
   const response=await traktApi('/sync/history',{method:'POST',body:JSON.stringify(FGCheckIn.historyBody(session))});
   checkInAssertOperation(op);FGCheckIn.historyAcknowledged(session,response);historyConfirmed=true;
  }
  checkInAssertOperation(op);checkInRequireHistoryClear(session);checkInApplyConfirmed(session,session.finishAt);
  session=FGCheckIn.confirmed(session);checkInCommit(session,op);
  if(await checkInCleanup(session,op))toast(mode==='verify'?'Historique Trakt confirmé ; suivi local mis à jour sans renvoi.':'Visionnage confirmé et check-in terminé.');
 }catch(e){
  if(op){
   const rejected=mode==='write'&&!historyConfirmed&&(definitiveCheckInError(e)||e.code==='CHECKIN_NOT_FOUND');
   session=rejected?{...session,status:'active',finishAt:null,lastError:String(e.message).slice(0,800)}:FGCheckIn.uncertain(session,'finish',e.message);
   checkInRemember(session,op);
   toast(rejected?'Fin refusée : '+e.message:'Confirmation incertaine : vérifie l’historique. '+e.message);
  }else toast(e.message);
 }finally{if(op){checkInBusy=false;checkInRefresh();}}
}
async function finishTraktCheckIn(){return completeTraktCheckIn(false);}
async function verifyTraktCheckIn(){return completeTraktCheckIn(true);}
async function cancelTraktCheckIn(){
 if(checkInBusy)return;let session=checkInRead(),op=null;
 try{
  checkInRequireContext(session);if(!confirm('Arrêter ce check-in Trakt sans ajouter de visionnage local ?'))return;
  op=checkInOperation(session);checkInBusy=true;renderToday();checkInAssertOperation(op);
  try{await traktResponse('/checkin',{method:'DELETE'});checkInAssertOperation(op);checkInCommit(null,op);toast('Check-in Trakt annulé.');}
  catch(e){if(e.status===404){checkInCommit(null,op);toast('Aucun check-in distant restant.');}else{checkInRemember(FGCheckIn.uncertain(session,'cancel',e.message),op);toast('Annulation distante incertaine. Réessaie ou vérifie Trakt.');}}
 }catch(e){toast(e.message);}finally{if(op){checkInBusy=false;checkInRefresh();}}
}
async function retryCheckInCleanup(){
 if(checkInBusy)return;const session=checkInRead();let op=null;
 try{
  checkInRequireContext(session);if(FGCheckIn.completionMode(session)!=='cleanup')throw Error('Vérifie d’abord le visionnage avant le nettoyage.');
  op=checkInOperation(session);checkInBusy=true;renderToday();if(await checkInCleanup(session,op))toast('Nettoyage du check-in confirmé.');
 }catch(e){toast(e.message);}finally{if(op){checkInBusy=false;checkInRefresh();}}
}
function forgetLocalCheckIn(){
 if(checkInBusy)return toast('Une opération de check-in est en cours. Attends son résultat.');
 try{assertWritable();const raw=localStorage.getItem(CHECKIN_KEY);if(!raw)return;
  if(confirm('Oublier uniquement la référence locale ? Cette action ne garantit pas l’arrêt du check-in sur Trakt.')){checkInCommit(null);checkInRefresh();}
 }catch(e){toast(e.message);}
}
function exportCheckInRecovery(){try{const raw=localStorage.getItem(CHECKIN_KEY)||localStorage.getItem(CHECKIN_RECOVERY_KEY);if(!raw)return toast('Aucune session brute à exporter.');downloadJson('franchiseguide-checkin-recuperation.json',{raw,exportedAt:new Date().toISOString()});}catch(e){toast('Lecture de la session impossible : '+e.message);}}
document.addEventListener('click',e=>{const b=e.target.closest('[data-trakt-checkin]');if(!b)return;e.preventDefault();startTraktCheckIn(b.dataset.traktCheckin,b.dataset.checkinId);});
