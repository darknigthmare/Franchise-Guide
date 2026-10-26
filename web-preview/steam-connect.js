/* Steam sign-in + library orchestration. The browser never receives an API key or session token. */
'use strict';
let steamConnection={authenticated:false,checked:false,checkedAt:0,steamid:null,expiresAt:0};
let steamLibraryBusy=false,steamLibraryController=null,steamLibraryRetryAt=0,steamNotice='',steamLastReport=null;
let steamSessionRevision=0,steamDisconnecting=false;
const STEAM_INTENT='franchiseguide-steam-login-intent-v1';
async function steamRequest(path,options={}){
 if(path.startsWith('/api/gaming/steam/')&&typeof gamingProviderBlocked==='function'&&gamingProviderBlocked('steam'))throw gamingRetryError('steam');
 const r=await fetch(path,{...options,credentials:'same-origin',cache:'no-store',headers:{'X-FranchiseGuide-Client':'0.12',...(options.headers||{})},signal:options.signal||AbortSignal.timeout(45000)});
 let x;try{x=await r.json();}catch{throw Error('Le serveur de connexion Steam n’est pas disponible sur cette origine.');}
 if(!r.ok){const e=Error(x.error||'Steam HTTP '+r.status);e.status=r.status;e.retryAfter=r.headers.get('Retry-After');e.retryAt=FGSyncPolicy.retryAfter(e.retryAfter,Date.now(),60000);if(e.status===429&&path.startsWith('/api/gaming/steam/'))holdGamingProvider('steam',e.retryAt);throw e;}return x;
}
function activeSteamAccount(){return steamConnection.authenticated?gamingState().accounts.find(a=>a.provider==='steam'&&a.externalId===steamConnection.steamid):null;}
function steamReadAuthorized(account){return !steamDisconnecting&&!!account&&steamConnection.authenticated&&steamConnection.steamid===account.externalId&&steamConnection.expiresAt>Date.now();}
async function refreshSteamSession(){
 if(steamDisconnecting)return steamConnection;const revision=++steamSessionRevision;
 try{
  const next=await steamRequest('/api/gaming/auth/steam/session',{signal:AbortSignal.timeout(6000)});
  if(revision!==steamSessionRevision||steamDisconnecting)return steamConnection;
  if(next.authenticated){FGGaming.steamId(next.steamid);if(!Number.isSafeInteger(next.expiresAt)||next.expiresAt<=Date.now())throw Error('Session Steam expirée.');}
  if(steamConnection.steamid&&steamConnection.steamid!==next.steamid){invalidateGamingReads();steamLibraryController?.abort();}
  steamConnection={authenticated:next.authenticated===true,steamid:next.authenticated?next.steamid:null,expiresAt:next.expiresAt||0,checked:true,checkedAt:Date.now()};
 }catch(e){
  if(revision!==steamSessionRevision||steamDisconnecting)return steamConnection;
  // A network outage is not represented as a successful connected session.
  steamConnection={...steamConnection,authenticated:false,checked:true,checkedAt:Date.now()};
  steamNotice=e.status===401?'Session Steam terminée. Reconnecte ton compte.':'Connexion Steam momentanément indisponible ; aucune donnée locale supprimée.';
 }
 updateSteamCard();return steamConnection;
}
function steamCounters(){const account=activeSteamAccount();const sets=account?gamingState().sets.filter(s=>s.provider==='steam'&&s.accountId===account.id):[];return {
 games:steamConnection.steamid?state.media.filter(m=>m.kind==='game'&&m.steam?.accounts?.[steamConnection.steamid]).length:0,
 lists:sets.length,read:sets.filter(s=>s.lastSyncAt&&Date.now()-s.lastSyncAt<6*3600000).length,failed:sets.filter(s=>s.syncError).length,
 pending:sets.filter(s=>!s.lastSyncAt||Date.now()-s.lastSyncAt>=6*3600000).length};}
function steamCardHtml(){
 const a=activeSteamAccount(),p=steamCounters(),connected=steamConnection.authenticated;
 return `<div class="steam-connect-head"><div><span class="eyebrow">CONNEXION OFFICIELLE · LECTURE SEULE</span><h3>${connected?'Steam connecté':'Se connecter avec Steam'}</h3></div>${a?.avatarUrl?`<img class="steam-avatar" src="${esc(a.avatarUrl)}" alt="Avatar Steam" loading="lazy">`:''}</div>
 <p>${connected?`<b>${esc(a?.label||steamConnection.steamid)}</b> · ${esc(steamConnection.steamid)}<br><small>Identité validée côté serveur. Session locale jusqu’au ${esc(humanDate(steamConnection.expiresAt))}.</small>`:'Connecte-toi sur le site Steam ; l’application retrouve ton compte, tes jeux accessibles et leurs succès. Aucun SteamID ni App ID à recopier.'}</p>
 <p class="meta">Steam ne transmet aucun mot de passe à FranchiseGuide. La lecture respecte la confidentialité Steam et nécessite une clé configurée une fois côté serveur.</p>
 <div class="actions">${!connected?'<button id="steamSignIn" class="primary">Se connecter avec Steam</button>':`<button id="steamImportLibrary" class="primary" ${steamLibraryBusy?'disabled':''}>${steamLibraryBusy?'Lecture de la bibliothèque…':'Récupérer mes jeux et succès'}</button><button id="steamAutoToggle" class="tonal">${a?.autoImport||a?.autoSync?'Suspendre':'Activer'} l’actualisation automatique</button><button id="steamSignOut" class="tonal">Déconnecter Steam</button>`}<button id="steamCheckSession" class="tonal">Vérifier la connexion</button></div>
 ${connected?`<div class="facts steam-facts"><div><b>${p.games}</b><span>Jeux liés à ce compte</span></div><div><b>${p.read} / ${p.lists}</b><span>Listes relues depuis moins de 6 h</span></div><div><b>${p.pending}</b><span>Listes à lire ou reprendre</span></div></div><p class="meta">${a?.autoImport||a?.autoSync?'Automatique activé pendant l’utilisation, pas quand l’application est fermée.':'Automatique suspendu.'} ${p.failed} liste(s) en erreur, données précédentes conservées.</p>`:''}
 <p id="steamSyncNotice" role="status">${esc(steamNotice||'Les jeux et succès déjà importés restent consultables sans connexion.')}</p>
 ${steamLastReport?.conflicts?.length?`<details><summary>${steamLastReport.conflicts.length} liaison(s) à résoudre</summary>${steamLastReport.conflicts.slice(0,50).map(c=>`<p>${esc(c.title)} · App ID ${esc(c.appId)} — ${esc(c.reason)}</p>`).join('')}</details>`:''}`;
}
function updateSteamCard(){const card=$('#steamSignInCard');if(!card)return;card.innerHTML=steamCardHtml();
 $('#steamSignIn')?.addEventListener('click',steamSignInDialog);
 $('#steamCheckSession')?.addEventListener('click',async()=>{await checkGamingBridge(true);await refreshSteamSession();if(!gamingBridge.steamSignIn)steamNotice='Utilise le programme PC ou le serveur Web Node V0.16 fourni. Le site statique seul ne peut pas valider une connexion Steam.';updateSteamCard();});
 $('#steamImportLibrary')?.addEventListener('click',()=>steamImportDialog());
 $('#steamSignOut')?.addEventListener('click',disconnectSteam);
 $('#steamAutoToggle')?.addEventListener('click',()=>{const a=activeSteamAccount();if(!a)return steamImportDialog();try{const enable=!(a.autoImport||a.autoSync);gamingCommit(g=>{const x=g.accounts.find(x=>x.id===a.id);x.autoImport=enable;x.autoSync=enable;});if(!enable){steamLibraryController?.abort();invalidateGamingReads();}else steamForeground();updateSteamCard();}catch(e){toast(e.message);}});
 bindImages(card);
}
function enhanceSteamConnect(){
 if(overlay?.type==='gaming'){
  const section=document.createElement('section');section.className='panel steam-signin';section.id='steamSignInCard';$('#content').prepend(section);updateSteamCard();
  $$('.gaming-account').forEach(card=>{if(steamConnection.authenticated&&card.innerText.includes(steamConnection.steamid)){const small=card.querySelector('small');if(small)small.textContent='Compte authentifié sur ce serveur Steam — pas de mot de passe dans l’application.';}});
 }
 if(!overlay&&screen==='games'){
  const b=document.createElement('button');b.id='steamQuickConnect';b.className='primary';b.textContent=steamConnection.authenticated?'Steam · compte connecté':'Se connecter avec Steam';b.onclick=()=>{overlay={type:'gaming',title:'Trophées & comptes jeux'};render();};$('.gaming-shortcuts')?.append(b);
 }
 if(overlay?.type==='item'){
  const m=gameById(overlay.id);if(m?.steam){const data=FGSteam.metadata(m.steam),s=document.createElement('section');s.className='panel';s.innerHTML=`<h3>Bibliothèque Steam</h3><p>App ID ${esc(data.appId)} · Les heures jouées ne signifient pas que la campagne est terminée.</p>${Object.entries(data.accounts).map(([id,a])=>`<p>${esc(gamingState().accounts.find(x=>x.provider==='steam'&&x.externalId===id)?.label||id)} : <b>${a.playtimeMinutes==null?'Temps masqué / inconnu':(a.playtimeMinutes/60).toLocaleString('fr-FR',{maximumFractionDigits:1})+' h'}</b>${a.lastPlayedAt?' · Dernière partie : '+esc(humanDate(a.lastPlayedAt)):''}</p>`).join('')}`;$('#content').append(s);}
 }
}
function steamSignInDialog(){
 gamingDialog('Connexion Steam et récupération automatique',`<p>Tu vas être redirigé vers <b>steamcommunity.com</b>, puis revenir ici. La session est propre à ce navigateur et à ce serveur local.</p><label class="toggle">Importer automatiquement mes jeux accessibles après la connexion<input type="checkbox" name="import" checked></label><label class="toggle">Lire les succès progressivement et actualiser pendant l’utilisation<input type="checkbox" name="auto" checked></label><p class="notice">Détails de jeux Steam privés : l’application ne pourra pas les lire. Aucun jeu existant n’est supprimé et aucun succès n’est débloqué à distance.</p><p class="meta">La clé API est configurée une fois par l’exploitant du serveur dans FG_STEAM_API_KEY. Pas de clé partagée embarquée dans cette archive.</p>`,async form=>{
  assertWritable();const config={at:Date.now(),import:form.has('import'),auto:form.has('auto')};sessionStorage.setItem(STEAM_INTENT,JSON.stringify(config));
  try{const x=await steamRequest('/api/gaming/auth/steam/begin',{method:'POST'});const url=new URL(x.authorizeUrl);if(url.origin!=='https://steamcommunity.com'||url.pathname!=='/openid/login')throw Error('Adresse Steam non autorisée.');location.assign(url.href);}catch(e){sessionStorage.removeItem(STEAM_INTENT);throw e;}
 });$('#gamingSubmit').textContent='Continuer sur Steam';
}
function steamImportDialog(){const a=activeSteamAccount();gamingDialog('Récupérer la bibliothèque Steam',`<p>Ajouter les jeux retournés par Steam et lier leurs listes de succès. Les notes, favoris et états de campagne existants sont conservés. Les rapprochements utilisent les App ID, jamais seulement les titres.</p><label class="toggle">Actualiser automatiquement bibliothèque et succès pendant l’utilisation<input type="checkbox" name="auto" ${a?.autoImport||a?.autoSync?'checked':''}></label>`,async form=>{await importSteamLibrary({autoImport:form.has('auto'),autoSync:form.has('auto'),initial:true});});$('#gamingSubmit').textContent='Importer et lire les succès';}
async function importSteamLibrary({autoImport,autoSync,initial=false}={}){
 if(steamLibraryBusy||storageDraft||!storageWritable||recoveryOnly||!steamConnection.authenticated)return false;
 if(gamingControl().pauseAll)throw Error('Lectures gaming en pause : réactive-les dans le centre de synchronisation.');
 const steamid=steamConnection.steamid,epoch=libraryEpoch,generation=gamingGeneration,c=new AbortController();steamLibraryController=c;steamLibraryBusy=true;updateSteamCard();
 const valid=()=>!c.signal.aborted&&epoch===libraryEpoch&&generation===gamingGeneration&&steamConnection.authenticated&&steamConnection.steamid===steamid&&storageWritable&&!recoveryOnly&&!storageDraft;
 const timeout=setTimeout(()=>c.abort(),50000);
 try{
  assertWritable();const raw=await steamRequest('/api/gaming/steam/library',{signal:c.signal});if(!valid())return false;
  const library=FGSteam.normalize(raw,steamid),planned=FGSteam.plan(state,library,{autoImport,autoSync});
  requireSafetySnapshot('Avant récupération de la bibliothèque Steam');if(!valid())return false;
  state=planned.state;save(false);steamLastReport=planned.report;steamLibraryRetryAt=0;
  const r=planned.report;steamNotice=`${r.added} jeu(x) ajouté(s), ${r.updated} actualisé(s), ${r.listsCreated} liste(s) de succès à lire. ${r.noStats} jeu(x) sans statistiques exposées dans la liste Steam.`;
  if(r.empty)steamNotice='Steam a retourné zéro jeu. Cela ne permet pas de conclure que la bibliothèque est vide : vérifie les réglages de confidentialité. Aucun titre local supprimé.';
  if(r.deferred||r.conflicts.length)steamNotice+=` ${r.deferred} liste(s) non créées (limite de 2 000 listes), ${r.conflicts.length} liaison(s) ambiguë(s).`;
  if(initial)void runGamingSync(false,planned.accountId);else void runGamingSync(true,planned.accountId);return true;
 }catch(e){
  if(!c.signal.aborted){steamLibraryRetryAt=e.retryAt||Date.now()+5*60000;steamNotice=e.message;if(e.status===401){steamConnection.authenticated=false;invalidateGamingReads();}if(initial)throw e;}
  else steamNotice='Lecture interrompue. Bibliothèque et anciens relevés conservés.';return false;
 }finally{clearTimeout(timeout);steamLibraryBusy=false;if(steamLibraryController===c)steamLibraryController=null;updateSteamCard();}
}
async function disconnectSteam(){
 if(!confirm('Déconnecter Steam ici et suspendre ses lectures automatiques ? Les jeux et succès importés restent dans la bibliothèque.'))return;
 const account=activeSteamAccount();steamDisconnecting=true;steamSessionRevision++;steamLibraryController?.abort();invalidateGamingReads();
 try{await steamRequest('/api/gaming/auth/steam/disconnect',{method:'POST'});try{sessionStorage.removeItem(STEAM_INTENT);}catch(e){diagnostic('warn','steam','Marqueur de connexion temporaire indisponible après révocation.');}steamConnection={authenticated:false,checked:true,checkedAt:Date.now(),steamid:null,expiresAt:0};if(account)gamingCommit(g=>{const a=g.accounts.find(a=>a.id===account.id);if(a){a.autoImport=false;a.autoSync=false;}});steamNotice='Session Steam révoquée sur ce serveur. Aucun effacement distant.';}catch(e){steamNotice=e.message;steamConnection.authenticated=false;}finally{steamSessionRevision++;steamDisconnecting=false;}updateSteamCard();refreshSyncCenter();
}
async function bootstrapSteamConnection(){
 await checkGamingBridge();if(!gamingBridge.steamSignIn)return;
 await refreshSteamSession();const url=new URL(location.href),returned=url.searchParams.has('steam_connected'),cancelled=url.searchParams.has('steam_cancelled');let intent;
 try{intent=JSON.parse(sessionStorage.getItem(STEAM_INTENT)||'null');}catch{}
 if(returned||cancelled){url.searchParams.delete('steam_connected');url.searchParams.delete('steam_cancelled');history.replaceState(null,'',url);sessionStorage.removeItem(STEAM_INTENT);overlay={type:'gaming',title:'Trophées & comptes jeux'};screen='games';render();}
 if(cancelled){steamNotice='Connexion annulée sur Steam. Aucune bibliothèque modifiée.';updateSteamCard();return;}
 if(returned&&steamConnection.authenticated&&intent&&Date.now()-intent.at<10*60000&&intent.at<=Date.now()){
  if(intent.import){try{await importSteamLibrary({autoImport:intent.auto,autoSync:intent.auto,initial:true});}catch(e){steamNotice=e.message;updateSteamCard();}}
  else{steamNotice='Steam connecté. Lance « Récupérer mes jeux et succès » pour importer la bibliothèque.';updateSteamCard();}
 }else await steamForeground();
}
async function steamForeground(){
 if(document.hidden||navigator.onLine===false||!storageWritable||recoveryOnly||storageDraft||steamLibraryBusy||Date.now()<steamLibraryRetryAt||gamingControl().pauseAll||gamingProviderBlocked('steam'))return;
 if(!steamConnection.checked)return;
 if(Date.now()-steamConnection.checkedAt>5*60000)await refreshSteamSession();
 const account=activeSteamAccount();if(!account)return;
 if(account.autoImport&&Date.now()-(account.lastLibrarySyncAt||0)>=6*3600000)await importSteamLibrary();
 else if(account.autoSync)void runGamingSync(true,account.id);
 updateSteamCard();
}
setInterval(steamForeground,60000);window.addEventListener('online',steamForeground);document.addEventListener('visibilitychange',()=>{if(!document.hidden)void steamForeground();});
