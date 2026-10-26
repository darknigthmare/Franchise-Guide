/* RetroAchievements public profile reads. The Web API key stays on the local server. */
'use strict';
let retroBridge={checked:0,configured:false,message:'Vérification du serveur RetroAchievements en attente.'};
let retroLibraryBusy=false,retroLibraryController=null,retroNotice='',retroLastReport=null;
const retroLibraryRetry=new Map();
const retroStableAccount=a=>!!a&&a.provider==='retro'&&/^[0-7][0-9A-HJKMNP-TV-Z]{25}$/.test(a.externalId);
async function retroRequest(path,{signal}={}){
 if(typeof gamingProviderBlocked==='function'&&gamingProviderBlocked('retro'))throw gamingRetryError('retro');
 const r=await fetch('/api/gaming/retro/'+path,{credentials:'same-origin',cache:'no-store',headers:{'X-FranchiseGuide-Client':'0.14'},signal:signal||AbortSignal.timeout(45000)});
 let body;try{body=await r.json();}catch{throw Error('Le serveur RetroAchievements est indisponible. Utilise le lanceur PC ou Web fourni.');}
 if(!r.ok){const e=Error(body.error||'Lecture RetroAchievements impossible (HTTP '+r.status+').');e.status=r.status;e.retryAfter=r.headers.get('Retry-After');e.retryAt=FGSyncPolicy.retryAfter(e.retryAfter);if(e.status===429)holdGamingProvider('retro',e.retryAt);throw e;}
 if(body?.provider!=='retro')throw Error('Réponse RetroAchievements inconnue.');return body;
}
async function retroImportPage(path,signal){
 // Retain already validated pages while respecting provider/local rate limits.
 for(let attempt=0;;attempt++){
  try{return await retroRequest(path,{signal});}
  catch(e){
   if(e.status!==429||attempt>=2||signal.aborted)throw e;
   const delay=Math.max(1000,e.retryAt-Date.now());retroNotice='Limite de lecture atteinte. La récupération reprendra automatiquement ; tu peux l’annuler.';updateRetroCard();
   await new Promise((resolve,reject)=>{
    if(signal.aborted){reject(Error('Récupération annulée.'));return;}
    const cancelled=()=>{clearTimeout(timer);reject(Error('Récupération annulée.'));};
    const timer=setTimeout(()=>{signal.removeEventListener('abort',cancelled);resolve();},delay);
    signal.addEventListener('abort',cancelled,{once:true});
   });
  }
 }
}
async function checkRetroBridge(force=false){
 if(!force&&retroBridge.checked&&Date.now()-retroBridge.checked<60000)return retroBridge;
 try{const r=await retroRequest('status',{signal:AbortSignal.timeout(5000)});retroBridge={checked:Date.now(),configured:r.configured===true,message:r.configured===true?'Serveur RetroAchievements disponible. Les profils sont lus sur demande.':'Configure FG_RETRO_API_KEY dans le lanceur, puis vérifie à nouveau.'};}
 catch{retroBridge={checked:Date.now(),configured:false,message:'La lecture RetroAchievements nécessite le lanceur PC ou le serveur Web personnel fourni.'};}
 updateRetroCard();return retroBridge;
}
function retroCardHtml(){
 const accounts=gamingState().accounts.filter(retroStableAccount);
 return `<div class="steam-connect-head"><div><span class="eyebrow">RETROACHIEVEMENTS · PROFIL PUBLIC</span><h3>Mes jeux rétro et leurs succès</h3></div><span class="retro-mark" aria-hidden="true">RA</span></div><p>Associe un pseudo pour retrouver les jeux ayant une progression et lire leurs succès en modes normal et Hardcore. L’association conserve l’identifiant stable du profil si son pseudo change.</p><p class="meta">Cette association lit un profil public ; elle n’authentifie pas son propriétaire. Aucun mot de passe n’est demandé. La clé Web API reste dans le serveur personnel.</p><div class="actions"><button id="retroAssociate" class="primary" ${retroLibraryBusy?'disabled':''}>Associer un profil RetroAchievements</button><button id="retroCheckBridge" class="tonal">Vérifier le serveur</button>${retroLibraryBusy?'<button id="retroCancelLibrary" class="tonal">Annuler la récupération</button>':''}</div><p class="meta">${esc(retroBridge.message)}</p><div class="retro-accounts">${accounts.map(a=>{const sets=gamingState().sets.filter(s=>s.provider==='retro'&&s.accountId===a.id),pending=sets.filter(s=>!s.lastSyncAt||Date.now()-s.lastSyncAt>=6*3600000).length;return `<article class="gaming-account"><div><b>${esc(a.label)}</b><p>${esc(a.username||'Profil associé')} · ${sets.length} liste(s), ${pending} à lire ou actualiser</p><small>${a.autoImport||a.autoSync?'Actualisation activée pendant l’utilisation, au plus tôt après 6 heures.':'Actualisation automatique suspendue.'}</small></div><div class="actions"><button class="primary" data-retro-import="${esc(a.id)}" ${retroLibraryBusy?'disabled':''}>Récupérer les jeux et succès</button><button class="tonal" data-retro-auto="${esc(a.id)}">${a.autoImport||a.autoSync?'Suspendre':'Activer'} l’actualisation</button></div></article>`;}).join('')}</div><p id="retroSyncNotice" role="status" aria-live="polite">${esc(retroNotice||'Les relevés déjà enregistrés restent consultables hors connexion.')}</p>${retroLastReport?.conflicts?.length?`<details><summary>${retroLastReport.conflicts.length} liaison(s) à examiner</summary>${retroLastReport.conflicts.slice(0,30).map(c=>`<p>${esc(c.title||c.gameId)} · ${esc(c.reason)}</p>`).join('')}</details>`:''}`;
}
function updateRetroCard(){
 const card=$('#retroConnectCard');if(!card)return;card.innerHTML=retroCardHtml();
 $('#retroAssociate').onclick=retroAssociateDialog;$('#retroCheckBridge').onclick=()=>checkRetroBridge(true);
 $('#retroCancelLibrary')?.addEventListener('click',()=>{retroLibraryController?.abort();retroNotice='Récupération annulée. Les données enregistrées sont conservées.';updateRetroCard();});
 $$('[data-retro-import]').forEach(b=>b.onclick=()=>retroImportDialog(b.dataset.retroImport));
 $$('[data-retro-auto]').forEach(b=>b.onclick=()=>{try{const account=gamingState().accounts.find(a=>a.id===b.dataset.retroAuto);if(!account)return;const enabled=!(account.autoImport||account.autoSync);gamingCommit(g=>{const a=g.accounts.find(a=>a.id===account.id);a.autoImport=enabled;a.autoSync=enabled;});invalidateGamingReads();updateRetroCard();if(enabled)void retroForeground();}catch(e){toast(e.message);}});
}
function enhanceRetroConnect(){
 if(overlay?.type==='gaming'){const s=document.createElement('section');s.className='panel retro-connect';s.id='retroConnectCard';const steam=$('#steamSignInCard');if(steam)steam.after(s);else $('#content').prepend(s);updateRetroCard();}
 if(!overlay&&screen==='games'){const b=document.createElement('button');b.className='tonal';b.id='retroQuickConnect';b.textContent='Associer RetroAchievements';b.onclick=()=>{overlay={type:'gaming',title:'Trophées & comptes jeux'};render();retroAssociateDialog();};$('.gaming-shortcuts')?.append(b);}
 if(overlay?.type==='achievements'){const game=gameById(overlay.id),anchor=$('#linkSteamGame');if(game&&anchor){const b=document.createElement('button');b.id='linkRetroGame';b.className='tonal';b.textContent='Relier RetroAchievements';b.onclick=()=>retroLinkDialog(game);anchor.after(b);}}
}
function retroAssociateDialog(){
 const d=gamingDialog('Associer un profil RetroAchievements',`<p>Indique le pseudo public du profil à suivre. Les jeux ayant une progression seront ajoutés sans modifier tes notes, favoris ou états de campagne.</p><label>Pseudo RetroAchievements<input name="user" required maxlength="64" pattern="[A-Za-z0-9_-]{2,64}" autocomplete="off"></label><label class="toggle">Récupérer ses jeux et leurs succès maintenant<input name="import" type="checkbox" checked></label><label class="toggle">Actualiser automatiquement pendant l’utilisation<input name="auto" type="checkbox"></label><p class="meta">Le serveur doit disposer de FG_RETRO_API_KEY. Cette association ne constitue pas une connexion au compte et ne donne pas accès à des données privées.</p>`,async form=>{
  await importRetroLibrary({user:String(form.get('user')||''),includeGames:form.has('import'),autoImport:form.has('auto'),autoSync:form.has('auto'),initial:true});
 });d.addEventListener('close',()=>retroLibraryController?.abort());$('#gamingSubmit').textContent='Associer le profil';
}
function retroImportDialog(accountId){
 const account=gamingState().accounts.find(a=>a.id===accountId);if(!retroStableAccount(account))return retroAssociateDialog();
 const d=gamingDialog('Récupérer les jeux RetroAchievements',`<p>Profil : <b>${esc(account.label)}</b>. Les jeux ayant une progression sont récupérés. Les versions console et ensembles supplémentaires restent séparés par leur identifiant RetroAchievements.</p><label class="toggle">Actualiser les jeux et les succès automatiquement pendant l’utilisation<input name="auto" type="checkbox" ${account.autoImport||account.autoSync?'checked':''}></label>`,async form=>{await importRetroLibrary({accountId,autoImport:form.has('auto'),autoSync:form.has('auto'),initial:true});});d.addEventListener('close',()=>retroLibraryController?.abort());$('#gamingSubmit').textContent='Récupérer les jeux et succès';
}
async function importRetroLibrary({accountId,user,includeGames=true,autoImport,autoSync,initial=false}={}){
 if(retroLibraryBusy)throw Error('Une récupération RetroAchievements est déjà en cours.');
 if(gamingControl().pauseAll)throw Error('Lectures gaming en pause : réactive-les dans le centre de synchronisation.');
 assertWritable();if(storageDraft||recoveryOnly)throw Error('Résous la récupération de données avant cet import.');
 const previous=accountId?gamingState().accounts.find(a=>a.id===accountId):null;
 if(accountId&&!retroStableAccount(previous))throw Error('Associe de nouveau ce profil pour vérifier son identifiant stable.');
 const requested=previous?.externalId||user,epoch=libraryEpoch,generation=gamingGeneration,c=new AbortController();
 retroLibraryBusy=true;retroLibraryController=c;updateRetroCard();
 const valid=()=>!c.signal.aborted&&epoch===libraryEpoch&&generation===gamingGeneration&&storageWritable&&!storageDraft&&!recoveryOnly&&(!accountId||gamingState().accounts.some(a=>a.id===accountId&&a.externalId===previous.externalId));
 const timeout=setTimeout(()=>c.abort(),600000);
 try{
  if(!(await checkRetroBridge()).configured)throw Error(retroBridge.message);if(!valid())throw Error('Récupération interrompue.');
  const profile=FGRetro.profile(await retroRequest('profile?'+new URLSearchParams({user:requested}),{signal:c.signal}),requested);if(!valid())throw Error('La bibliothèque a changé ; relance la récupération.');
  let id;
  if(includeGames){
   const pages=[];let offset=0,total=0;
   do{
    const raw=await retroImportPage('library?'+new URLSearchParams({user:profile.ulid,offset:String(offset),count:'500'}),c.signal);if(!valid())throw Error('Récupération interrompue ; aucun import partiel appliqué.');
    const page=FGRetro.page(raw,profile.ulid,offset,500);pages.push(page);total=page.total;offset+=page.returnedCount;
    retroNotice=`Lecture des jeux de ${profile.username} : ${offset} / ${total}.`;updateRetroCard();
    if(offset<total&&page.returnedCount===0)throw Error('Page RetroAchievements incomplète. Les données précédentes sont conservées.');
   }while(offset<total);
   const library=FGRetro.library(pages,profile),planned=FGRetro.plan(state,library,{autoImport,autoSync});
   requireSafetySnapshot('Avant récupération des jeux RetroAchievements');if(!valid())throw Error('La bibliothèque a changé ; relance la récupération.');
   state=planned.state;save(false);id=planned.accountId;retroLastReport=planned.report;
   retroNotice=`${planned.report.added} jeu(x) ajouté(s), ${planned.report.updated} actualisé(s), ${planned.report.listsCreated} liste(s) de succès à lire.`;
   if(planned.report.deferred)retroNotice+=` ${planned.report.deferred} liste(s) reportée(s), capacité locale atteinte.`;
   if(library.empty)retroNotice='Aucun jeu avec progression retourné pour ce profil. Les jeux et relevés déjà enregistrés sont conservés.';
  }else{
   id=gamingState().accounts.find(a=>a.provider==='retro'&&a.externalId===profile.ulid)?.id||'retro-account-'+profile.ulid;
   gamingCommit(g=>{const old=g.accounts.find(a=>a.id===id);const a=FGGaming.normalizeAccount({...old,id,provider:'retro',externalId:profile.ulid,username:profile.username,label:old?.label||profile.label,avatarUrl:profile.avatarUrl,autoImport:autoImport===true,autoSync:autoSync===true});if(old)g.accounts[g.accounts.indexOf(old)]=a;else g.accounts.push(a);});
   retroNotice=`Profil ${profile.username} associé. Tu peux récupérer ses jeux depuis cet écran.`;
  }
  retroLibraryRetry.delete(id);if(includeGames)void runGamingSync(!initial,id);return true;
 }catch(e){
  if(!c.signal.aborted&&valid()){retroNotice=e.message;if(accountId)retroLibraryRetry.set(accountId,e.retryAt||Date.now()+5*60000);}
  else retroNotice='Récupération interrompue. Les données enregistrées sont conservées.';
  if(initial)throw Error(retroNotice);return false;
 }finally{clearTimeout(timeout);retroLibraryBusy=false;if(retroLibraryController===c)retroLibraryController=null;updateRetroCard();}
}
async function syncRetroSet(setId,automatic=false){
 if(gamingRequests.has(setId)||!storageWritable||storageDraft||recoveryOnly)return false;
 const original=gamingState().sets.find(s=>s.id===setId),account=gamingState().accounts.find(a=>a.id===original?.accountId);
 if(!original||!retroStableAccount(account)||!gamingReadGate(original,account,automatic))return false;
 const epoch=libraryEpoch,generation=gamingGeneration,identity=FGSyncPolicy.key(original,account),c=new AbortController();gamingRequests.set(setId,c);
 const alive=()=>epoch===libraryEpoch&&generation===gamingGeneration&&storageWritable&&!storageDraft&&!recoveryOnly&&gamingState().sets.some(s=>s.id===setId&&FGSyncPolicy.key(s,gamingState().accounts.find(a=>a.id===s.accountId))===identity)&&!gamingControl().pauseAll&&!gamingPlan({onlyIds:[setId]}).some(r=>r.paused)&&(!automatic||gamingState().accounts.find(a=>a.id===account.id)?.autoSync);
 const valid=()=>!c.signal.aborted&&alive();let timedOut=false;const timeout=setTimeout(()=>{timedOut=true;c.abort();},50000);
 try{
  if(!(await checkRetroBridge()).configured)throw Error(retroBridge.message);if(!valid())return false;
  const raw=await retroRequest('achievements?'+new URLSearchParams({user:account.externalId,gameId:original.externalGameId}),{signal:c.signal});if(!valid())return false;
  const fresh=FGRetro.achievements(raw,{...original,accountExternalId:account.externalId},Date.now());gamingReadResult(setId,account,{ok:true},fresh);gamingRetry.delete(setId);if(!automatic)toast('Relevé RetroAchievements actualisé.');return true;
 }catch(e){
  if(alive()&&(!c.signal.aborted||timedOut)){const message=timedOut?'Délai RetroAchievements dépassé. Ancien relevé conservé ; reprise différée.':e.message;try{gamingReadResult(setId,account,{status:e.status,retryAfter:e.retryAfter,message});}catch{}if(!automatic)toast(message);}return false;
 }finally{clearTimeout(timeout);if(gamingRequests.get(setId)===c)gamingRequests.delete(setId);updateRetroCard();refreshSyncCenter();if(overlay?.type==='achievements'&&overlay.id===original.mediaId&&!document.querySelector('dialog[open]'))render();}
}
function retroLinkDialog(game){
 const accounts=gamingState().accounts.filter(retroStableAccount);if(!accounts.length){overlay={type:'gaming',title:'Trophées & comptes jeux'};render();retroAssociateDialog();return;}
 const d=gamingDialog('Relier ce jeu à RetroAchievements',`<p>Choisis le profil et l’identifiant de la bonne version console, visible dans l’adresse de sa fiche RetroAchievements.</p><label>Profil<select name="account">${accounts.map(a=>`<option value="${esc(a.id)}">${esc(a.label)}</option>`).join('')}</select></label><label>ID du jeu RetroAchievements<input name="gameId" required inputmode="numeric" pattern="[1-9][0-9]*"></label>`,async form=>{
  assertWritable();const account=gamingState().accounts.find(a=>a.id===form.get('account'));if(!retroStableAccount(account))throw Error('Profil supprimé ou remplacé.');
  const externalGameId=FGGaming.gameId(form.get('gameId')),epoch=libraryEpoch,generation=gamingGeneration,c=new AbortController();const timer=setTimeout(()=>c.abort(),50000);d.addEventListener('close',()=>c.abort(),{once:true});
  try{const raw=await retroRequest('achievements?'+new URLSearchParams({user:account.externalId,gameId:externalGameId}),{signal:c.signal});
   if(c.signal.aborted||epoch!==libraryEpoch||generation!==gamingGeneration||!gameById(game.id))throw Error('La bibliothèque a changé ; relance la liaison.');
   const old=gamingState().sets.find(s=>s.provider==='retro'&&s.accountId===account.id&&s.externalGameId===externalGameId&&s.mediaId===game.id);
   let fresh=FGRetro.achievements(raw,{...(old||{}),id:old?.id||gamingUuid(),mediaId:game.id,accountId:account.id,accountExternalId:account.externalId,externalGameId,title:game.title,edition:old?.edition||'RetroAchievements'},Date.now());
   if(old)fresh=FGGaming.mergeSet(old,fresh);requireSafetySnapshot('Avant liaison RetroAchievements');gamingCommit(g=>{const i=g.sets.findIndex(s=>s.id===fresh.id);if(i<0)g.sets.push(fresh);else g.sets[i]=fresh;});selectGamingSet(game.id,fresh.id);
  }finally{clearTimeout(timer);}
 });$('#gamingSubmit').textContent='Lire et relier les succès';
}
async function retroForeground(){
 if(document.hidden||navigator.onLine===false||!storageWritable||storageDraft||recoveryOnly||retroLibraryBusy||gamingControl().pauseAll||gamingProviderBlocked('retro'))return;
 const account=gamingState().accounts.filter(a=>retroStableAccount(a)&&a.autoImport&&Date.now()-(a.lastLibrarySyncAt||0)>=6*3600000&&Date.now()>=(retroLibraryRetry.get(a.id)||0)).sort((a,b)=>(a.lastLibrarySyncAt||0)-(b.lastLibrarySyncAt||0))[0];
 if(account)await importRetroLibrary({accountId:account.id});
}
setInterval(()=>void retroForeground(),60000);window.addEventListener('online',()=>void retroForeground());document.addEventListener('visibilitychange',()=>{if(!document.hidden)void retroForeground();});
