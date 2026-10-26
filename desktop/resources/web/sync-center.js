/* Foreground-only coordinator. A checkpoint records the remaining READS, never provider writes.
 * Interrupted manual batches never restart without a new user action after page reload. */
'use strict';
let gamingContinuous=false,gamingRunMessage='',gamingCurrentSet=null,gamingNextBatchAt=0,gamingWakeTimer=null;
const SYNC_LABELS={ready:'À lire',fresh:'À jour',running:'En cours',waiting:'Délai de reprise',paused:'En pause',login:'Connexion requise',consent:'Automatique désactivé',local:'Local / import seulement',account:'Profil manquant',identity:'Profil à associer',missing:'Jeu retiré'};
let syncCenterLimit=50;
function gamingControl(){return FGSyncPolicy.normalize(state._gamingControl);}
function gamingControlCommit(update){assertWritable();const c=FGSyncPolicy.normalize(update(gamingControl()));state._gamingControl=c;save(false);refreshSyncCenter();return c;}
function gamingContext(extra={}){return {steamIdentity:typeof steamConnection!=='undefined'&&steamConnection.authenticated&&!steamDisconnecting?steamConnection.steamid:null,steamExpiresAt:typeof steamConnection!=='undefined'?steamConnection.expiresAt:0,inFlight:[...gamingRequests.keys()],mediaIds:state.media.filter(m=>m.added!==false&&m.kind==='game').map(m=>m.id),...extra};}
function gamingPlan(extra={}){return FGSyncPolicy.rows(gamingState(),gamingControl(),gamingContext(extra));}
function gamingReadGate(s,account,automatic){
 if(!storageWritable||storageDraft||recoveryOnly||navigator.onLine===false||document.hidden)return false;
 const row=gamingPlan({automatic,dueOnly:false,onlyIds:[s.id]}).find(r=>r.id===s.id);
 if(!row||row.code!=='ready'){if(!automatic&&row)toast(row.reason+(row.until?' · '+humanDate(row.until):''));return false;}
 if(gamingRequests.size>0){if(!automatic)toast('Une lecture de succès est déjà en cours. Le lot traite une liste à la fois.');return false;}
 return true;
}
function gamingReadResult(setId,account,result,fresh=null){
 const current=gamingState().sets.find(s=>s.id===setId);if(!current)return;
 const c=FGSyncPolicy.outcome(gamingControl(),current,account,result);
 gamingCommit(g=>{const i=g.sets.findIndex(s=>s.id===setId);if(i<0)return;if(fresh)g.sets[i]=FGGaming.mergeSet(g.sets[i],fresh);else g.sets[i].syncError=FGCore.redact(result.message||'Lecture interrompue ou indisponible.').slice(0,300);},()=>c);
 refreshSyncCenter();
}
function gamingProviderBlocked(provider){return (gamingControl().providers.find(p=>p.id===provider)?.retryAt||0)>Date.now();}
function holdGamingProvider(provider,retryAt){if(!['steam','retro'].includes(provider)||!storageWritable||storageDraft||recoveryOnly)return;gamingControlCommit(c=>{const old=c.providers.find(x=>x.id===provider);c.providers=c.providers.filter(x=>x.id!==provider);c.providers.push({id:provider,retryAt:Math.max(old?.retryAt||0,retryAt)});return c;});}
function gamingRetryError(provider){const until=gamingControl().providers.find(p=>p.id===provider)?.retryAt||Date.now();const e=Error('Délai '+gamingLabel(provider)+' en cours, reprise à partir du '+humanDate(until));e.status=429;e.retryAt=until;e.retryAfter=String(Math.ceil((until-Date.now())/1000));e.localCooldown=true;return e;}
function stopGamingQueue(message='Lot mis en pause. Les lectures réussies restent enregistrées.'){
 gamingContinuous=false;clearTimeout(gamingWakeTimer);gamingWakeTimer=null;gamingRunMessage=message;refreshSyncCenter();
}
function scheduleGamingQueue(){
 clearTimeout(gamingWakeTimer);if(!gamingContinuous)return;
 const delay=Math.max(1000,gamingNextBatchAt-Date.now());gamingWakeTimer=setTimeout(()=>{gamingWakeTimer=null;void pumpGamingQueue();},delay);
}
async function pumpGamingQueue(){
 if(!gamingContinuous)return;
 if(document.hidden||navigator.onLine===false||storageDraft||!storageWritable||recoveryOnly){gamingRunMessage='En attente : fenêtre visible, réseau et stockage autorisé requis.';refreshSyncCenter();return;}
 if(gamingControl().pauseAll){stopGamingQueue('Lectures gaming suspendues.');return;}
 if(Date.now()<gamingNextBatchAt){scheduleGamingQueue();return;}
 await gamingBatch(false,null,true);
 if(!gamingContinuous)return;
 const cp=gamingControl().checkpoint;
 if(!cp?.pending.length){stopGamingQueue(cp?`Lot terminé : ${cp.ok} réussi(s), ${cp.failed} échec(s), ${cp.skipped} référence(s) retirée(s).`:'Aucun lot à poursuivre.');return;}
 gamingNextBatchAt=Date.now()+30000;scheduleGamingQueue();
}
async function gamingBatch(automatic=true,onlyAccount=null,continuous=false){
 // Imports request their initial batch before clearing their busy flag. Let their finally run.
 await Promise.resolve();
 if(gamingAutoRunning||gamingRequests.size||!storageWritable||storageDraft||recoveryOnly||navigator.onLine===false||document.hidden)return;
 if((typeof steamLibraryBusy!=='undefined'&&steamLibraryBusy)||(typeof retroLibraryBusy!=='undefined'&&retroLibraryBusy))return;
 const c=gamingControl();if(c.pauseAll){if(!automatic)toast('Lectures gaming en pause : réactive-les dans le centre de synchronisation.');return;}
 if(!continuous&&c.checkpoint?.pending.length){if(!automatic)toast('Un lot est en attente : utilise Reprendre ou Fermer le lot dans le centre de synchronisation.');return;}
 if(continuous&&!gamingContinuous)return;
 if(continuous){const normalized=FGSyncPolicy.settleMissing(c,gamingState().sets.map(s=>s.id));if(JSON.stringify(normalized)!==JSON.stringify(c))gamingControlCommit(()=>normalized);}
 const cp=gamingControl().checkpoint;
 const plan=gamingPlan({automatic,accountId:onlyAccount,dueOnly:continuous?false:automatic,onlyIds:continuous?(cp?.pending||[]):null});
 const due=FGSyncPolicy.ready(plan,8);
 if(!due.length){if(!automatic&&!continuous)toast('Aucune liste disponible : vérifie les connexions, pauses et délais dans le centre.');if(continuous)gamingRunMessage='Listes restantes en attente de connexion, de reprise ou de délai fournisseur.';refreshSyncCenter();return;}
 gamingAutoRunning=true;const gen=gamingGeneration,epoch=libraryEpoch;let ok=0,attempted=0;
 try{
  for(const row of due){
   if(gen!==gamingGeneration||epoch!==libraryEpoch||document.hidden||!storageWritable||storageDraft||recoveryOnly||continuous&&!gamingContinuous)break;
   // Re-evaluate after every response: a 429 blocks ALL following lists of that provider.
   const current=gamingPlan({automatic,dueOnly:false,onlyIds:[row.id]}).find(x=>x.id===row.id);if(current?.code!=='ready')continue;
   gamingCurrentSet=row.id;refreshSyncCenter();attempted++;
   if(await syncGamingSet(row.id,automatic))ok++;
  }
 }finally{
  gamingCurrentSet=null;gamingAutoRunning=false;
  if(gen===gamingGeneration&&epoch===libraryEpoch)gamingRunMessage=`Dernier lot : ${ok}/${attempted} relevé(s) actualisé(s). Les relevés précédents sont conservés en cas d’échec.`;
  if(typeof updateSteamCard==='function')updateSteamCard();if(typeof updateRetroCard==='function')updateRetroCard();refreshSyncCenter();
  if(!automatic&&!continuous)toast(gamingRunMessage);
 }
}
function startGamingQueue(mode='due',onlyAccount=null){
 assertWritable();if(storageDraft)throw Error('Résous la copie de travail avant de lancer un lot.');if(gamingAutoRunning||gamingRequests.size)throw Error('Une lecture est en cours.');
 if(gamingControl().pauseAll)throw Error('Réactive les lectures gaming avant de lancer un lot.');
 const candidates=gamingPlan({automatic:false,dueOnly:mode!=='all',accountId:onlyAccount}).filter(r=>!['local','missing','account','identity','paused','fresh'].includes(r.code)).map(r=>r.id);
 if(!candidates.length)return toast('Aucune liste à traiter pour cette sélection. Les listes en pause ne sont pas réactivées automatiquement.');
 if(gamingControl().checkpoint?.pending.length&&!confirm('Remplacer le lot en attente ? Les données récupérées et les délais de reprise seront conservés.'))return;
 gamingControlCommit(c=>FGSyncPolicy.start(c,candidates,mode));gamingContinuous=true;gamingNextBatchAt=0;
 gamingRunMessage='Lot autorisé : une lecture à la fois, huit maximum par passage ; poursuite toutes les 30 secondes tant que la fenêtre reste visible.';refreshSyncCenter();void pumpGamingQueue();
}
function resumeGamingQueue(){assertWritable();if(!gamingControl().checkpoint?.pending.length)return toast('Aucun lot interrompu à reprendre.');if(gamingControl().pauseAll)return toast('Réactive d’abord les lectures gaming.');gamingContinuous=true;gamingNextBatchAt=0;void pumpGamingQueue();}
function pauseAllGaming(value){gamingControlCommit(c=>({...c,pauseAll:!!value}));if(value){invalidateGamingReads();gamingRunMessage='Toutes les lectures gaming sont suspendues. Trakt reste indépendant.';}refreshSyncCenter();}
function pauseGamingList(id,value){const s=gamingState().sets.find(x=>x.id===id);if(!s)return;const a=gamingState().accounts.find(x=>x.id===s.accountId);gamingControlCommit(c=>FGSyncPolicy.pauseList(c,s,a,value));if(value)gamingRequests.get(id)?.abort();refreshSyncCenter();}
function openSyncCenter(){overlay={type:'sync-center',title:'Centre de synchronisation'};syncCenterLimit=50;render();}
function syncCenterAction(fn){return async()=>{try{await fn();}catch(e){gamingRunMessage=e.message;toast(e.message);refreshSyncCenter();}};}
function renderSyncCenter(){
 $('#content').innerHTML=`<section class="hero"><span class="eyebrow">LECTURES ET SUIVI</span><h2>Centre de synchronisation</h2><p>Des lots explicites, sans confondre données locales et déblocages distants.</p></section><section class="panel sync-console"><h3>Steam & RetroAchievements</h3><p class="meta">Une liste à la fois. Les lots complets sont poursuivis pendant l’utilisation ; après fermeture ou rechargement, une reprise manuelle est nécessaire. Aucun succès n’est débloqué à distance.</p><div class="facts" id="gamingSyncFacts"></div><p id="gamingSyncStatus" role="status" aria-live="polite"></p><div class="actions"><button id="startGamingAll" class="primary">Récupérer toutes les listes à actualiser</button><button id="resumeGamingBatch" class="tonal">Reprendre le lot interrompu</button><button id="pauseGamingBatch" class="tonal">Mettre le lot en pause</button><button id="closeGamingBatch" class="tonal">Fermer le lot</button><button id="pauseAllGaming" class="tonal"></button></div><p class="meta">« À actualiser » : jamais lues, relues il y a au moins six heures ou en erreur. Les pauses et délais restent prioritaires, même en cliquant manuellement. Un lot terminé avec des erreurs n’est pas un succès intégral.</p></section><section class="panel"><h3>Examiner les listes</h3><div class="sync-filters"><label>Rechercher un jeu, compte ou édition<input id="syncListQuery" type="search" autocomplete="off"></label><label>Profil<select id="syncAccountFilter"><option value="all">Tous les profils</option>${gamingState().accounts.map(a=>`<option value="${esc(a.id)}">${esc(a.label+' · '+gamingLabel(a.provider))}</option>`).join('')}</select></label><label>État<select id="syncListFilter"><option value="all">Tous</option><option value="ready">À lire</option><option value="errors">En erreur</option><option value="blocked">Bloqués / en attente</option><option value="paused">En pause</option><option value="fresh">À jour</option><option value="local">Import / local seulement</option></select></label></div><p class="meta" id="syncListCount" aria-live="polite"></p><div id="gamingSyncRows"></div><button id="moreGamingSync" class="tonal">Afficher les 50 suivantes</button></section><section class="panel"><h3>Trakt reste une synchronisation distincte</h3><p>${traktConnected()?'Compte connecté localement.':'Pas de compte Trakt connecté.'} ${syncState.queue.length} opération(s) en attente, dont ${syncState.queue.filter(x=>x.uncertain).length} incertaine(s) et ${syncState.queue.filter(x=>x.held).length} suspendue(s).</p><p class="meta">Les lectures de trophées n’envoient ni visionnages ni notes à Trakt. Une opération d’historique incertaine demande toujours une vérification avant réémission.</p><div class="actions"><button id="syncCenterTrakt" class="tonal">Examiner la file Trakt</button><button id="syncCenterAccounts" class="tonal">Gérer les profils gaming</button></div></section>`;
 $('#startGamingAll').onclick=syncCenterAction(()=>startGamingQueue('due',$('#syncAccountFilter').value==='all'?null:$('#syncAccountFilter').value));
 $('#resumeGamingBatch').onclick=syncCenterAction(resumeGamingQueue);
 $('#pauseGamingBatch').onclick=()=>{invalidateGamingReads();stopGamingQueue();};
 $('#pauseAllGaming').onclick=syncCenterAction(()=>pauseAllGaming(!gamingControl().pauseAll));
 $('#closeGamingBatch').onclick=syncCenterAction(()=>{if(!confirm('Fermer ce lot de lectures ? Les relevés reçus, les pauses et les délais restent enregistrés.'))return;invalidateGamingReads();gamingControlCommit(c=>({...c,checkpoint:null}));gamingRunMessage='Lot fermé ; aucune donnée distante modifiée.';refreshSyncCenter();});
 $('#syncCenterTrakt').onclick=()=>{overlay={type:'trakt-library',title:'Bibliothèque Trakt'};render();};$('#syncCenterAccounts').onclick=()=>{overlay={type:'gaming',title:'Trophées & comptes jeux'};render();};
 for(const id of ['syncListQuery','syncAccountFilter','syncListFilter'])$('#'+id).addEventListener(id==='syncListQuery'?'input':'change',()=>{syncCenterLimit=50;refreshSyncCenter();});
 $('#moreGamingSync').onclick=()=>{syncCenterLimit+=50;refreshSyncCenter();};refreshSyncCenter();
}
function refreshSyncCenter(){
 const host=$('#gamingSyncRows');if(!host)return;
 const c=gamingControl(),plan=gamingPlan({automatic:false,dueOnly:true}),cp=c.checkpoint,count=code=>plan.filter(r=>r.code===code).length;
 $('#gamingSyncFacts').innerHTML=`<div><b>${count('ready')}</b><span>Listes prêtes</span></div><div><b>${count('fresh')}</b><span>À jour</span></div><div><b>${count('waiting')}</b><span>Délai de reprise</span></div><div><b>${count('paused')}</b><span>En pause</span></div>`;
 const active=gamingState().sets.find(s=>s.id===gamingCurrentSet);
 $('#gamingSyncStatus').textContent=(active?'Lecture : '+active.title+'. ':'')+(gamingRunMessage||'Les états ci-dessous sont locaux ; une configuration présente n’est pas un compte vérifié.')+(cp?` Lot : ${cp.ok} réussi(s), ${cp.failed} échec(s), ${cp.skipped} retiré(s), ${cp.pending.length} restant(s) / ${cp.total}.`:'');
 $('#pauseAllGaming').textContent=c.pauseAll?'Réactiver les lectures gaming':'Suspendre toutes les lectures gaming';$('#resumeGamingBatch').disabled=!cp?.pending.length||gamingContinuous||c.pauseAll;$('#pauseGamingBatch').disabled=!gamingContinuous&&!gamingAutoRunning;$('#closeGamingBatch').disabled=!cp;$('#startGamingAll').disabled=c.pauseAll||gamingAutoRunning||!!gamingRequests.size;
 const q=$('#syncListQuery').value.trim().normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase(),a=$('#syncAccountFilter').value,filter=$('#syncListFilter').value;
 const list=plan.filter(r=>(a==='all'||r.accountId===a)&&(!q||(r.title+' '+r.accountLabel+' '+r.edition+' '+r.platform).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().includes(q))&&(filter==='all'||filter==='errors'&&(r.hasError||r.failures>0)||filter==='blocked'&&['login','account','identity','waiting','missing'].includes(r.code)||filter===r.code));
 $('#syncListCount').textContent=`${list.length} liste(s) correspondant aux filtres · ${Math.min(list.length,syncCenterLimit)} affichée(s).`;
 host.innerHTML=list.slice(0,syncCenterLimit).map(r=>`<article class="sync-list-row"><div><h4>${esc(r.title)}</h4><p>${esc(r.accountLabel+' · '+r.platform+(r.edition?' · '+r.edition:''))}</p><span class="sync-state sync-${r.code}">${esc(SYNC_LABELS[r.code]||r.reason)}</span><p class="meta">${esc(r.reason)}${r.until?' · à partir du '+esc(humanDate(r.until)):''}${r.lastSyncAt?' · Dernière réussite '+esc(humanDate(r.lastSyncAt)):''}${r.failures?' · '+r.failures+' échec(s) consécutif(s)':''}</p></div><div class="actions"><button class="tonal" data-sync-open="${esc(r.mediaId)}">Ouvrir les succès</button>${!['local','account','identity','missing'].includes(r.code)?`<button class="tonal" data-sync-pause="${esc(r.id)}" aria-pressed="${r.paused}">${r.paused?'Réactiver cette liste':'Mettre cette liste en pause'}</button><button class="tonal" data-sync-once="${esc(r.id)}" ${['paused','waiting','login','running'].includes(r.code)?'disabled':''}>Relire cette liste</button>`:''}</div></article>`).join('')||'<p>Aucune liste dans cette vue. Relie Steam ou RetroAchievements, ou importe un relevé depuis la fiche d’un jeu.</p>';
 $('#moreGamingSync').hidden=list.length<=syncCenterLimit;
 host.querySelectorAll('[data-sync-open]').forEach(b=>b.onclick=()=>openGameAchievements(b.dataset.syncOpen));
 host.querySelectorAll('[data-sync-pause]').forEach(b=>b.onclick=syncCenterAction(()=>{const s=gamingState().sets.find(x=>x.id===b.dataset.syncPause),a=gamingState().accounts.find(a=>a.id===s?.accountId);if(s){const row=gamingPlan({onlyIds:[s.id]}).find(x=>x.id===s.id);pauseGamingList(s.id,!row?.paused);}}));
 host.querySelectorAll('[data-sync-once]').forEach(b=>b.onclick=syncCenterAction(()=>syncGamingSet(b.dataset.syncOnce,false)));
}
function enhanceSyncCenter(){
 if(overlay?.type==='gaming'){const b=document.createElement('button');b.id='openGamingSyncCenter';b.className='primary';b.textContent='Centre de synchronisation · reprendre les lectures';b.onclick=openSyncCenter;const panel=document.createElement('section');panel.className='panel';panel.append(b);$('#content').prepend(panel);}
 if(overlay?.type==='achievements'){const id=gameView(overlay.id)?.setId,s=gamingState().sets.find(s=>s.id===id);if(!s)return;const r=gamingPlan({onlyIds:[id]}).find(r=>r.id===id);if(!r)return;const b=$('#syncAchievementSet');if(b&&['paused','waiting','login','running','missing'].includes(r.code)){b.disabled=true;b.textContent=r.reason+(r.until?' · '+humanDate(r.until):'');}const link=document.createElement('button');link.className='tonal';link.textContent='Examiner la reprise et les pauses';link.onclick=openSyncCenter;$('#achievementSetBody .actions')?.append(link);}
}
function gamingForegroundTick(){if(gamingContinuous)void pumpGamingQueue();else refreshSyncCenter();}
document.addEventListener('visibilitychange',gamingForegroundTick);window.addEventListener('online',gamingForegroundTick);
window.addEventListener('pagehide',()=>stopGamingQueue());setInterval(gamingForegroundTick,30000);
