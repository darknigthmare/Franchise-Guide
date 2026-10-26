/* Trakt integration: explicit OAuth, one refresh at a time, paginated reads,
   persisted change queue, per-operation acknowledgement, no full-history resends. */
'use strict';
function syncStore(){try{persistEnvelope();savedState=clone(state);}catch(e){storageFailure(e);throw e;}}
function syncAccount() { return traktAuth().username||traktAuth().displayName||'connected'; }
function itemKey(kind,id) { return `${kind}:${id}`; }
function trackedRecords(s) {
  return [...(s.media||[]).filter(m=>['movie','show'].includes(m.kind)).map(m=>({...m,_kind:m.kind})),
    ...(s.episodes||[]).map(e=>({...e,_kind:'episode'}))].filter(x=>x.tmdbId);
}
function queueLocalChanges(before,after,persist=true) {
  if(!syncState||!traktConnected())return;
  const previous=new Map(trackedRecords(before).map(x=>[itemKey(x._kind,x.tmdbId),x]));
  for(const x of trackedRecords(after)) {
    const key=itemKey(x._kind,x.tmdbId),old=previous.get(key)||{},fields=[];
    if(x._kind!=='show') {
      for(const date of FGCore.historyAdditions(old,x))fields.push(['history',true,date]);
      if(old.watched&&!x.watched&&!x.skipped)fields.push(['history',false]);
      if(!!old.collected!==!!x.collected)fields.push(['collection',!!x.collected]);
    }
    if(x._kind!=='episode'&&!!old.watchlist!==!!x.watchlist)fields.push(['watchlist',!!x.watchlist]);
    if((old.rating??null)!==(x.rating??null))fields.push(['ratings',x.rating??null]);
    for(const [field,value,watchedAt] of fields) {
      if(field!=='history')syncState.queue=syncState.queue.filter(op=>!(op.key===key&&op.field===field&&!op.uncertain&&!op.held));
      const at=new Date().toISOString(),entry={ids:{tmdb:x.tmdbId}};
      if(field==='history'&&value)entry.watched_at=new Date(watchedAt).toISOString();
      if(field==='ratings'&&value!==null)entry.rating=FGCore.integer(value,1,10);
      const plural=x._kind==='episode'?'episodes':x._kind==='show'?'shows':'movies';
      const remove=value===false||value===null;
      syncState.queue.push({id:crypto.randomUUID?crypto.randomUUID():`op-${Date.now()}-${Math.random().toString(16).slice(2)}`,
        account:syncAccount(),key,field,value,at,title:x.title||'',path:`/sync/${field}${remove?'/remove':''}`,body:{[plural]:[entry]},attempts:0});
    }
  }
  if(persist&&syncState.queue.length)syncStore();
}
const activeTraktRequests=new Set();
function checkTraktGeneration(generation){if(generation!==authGeneration)throw Object.assign(Error('Compte déconnecté ou changé pendant la requête.'),{code:'STALE_TRAKT',name:'AbortError'});}
function cancelTraktRequests(){for(const c of activeTraktRequests)c.abort();activeTraktRequests.clear();}
async function guardedTraktFetch(url,options={},generation=authGeneration){
  checkTraktGeneration(generation);const c=new AbortController(),cancel=()=>c.abort();if(options.signal?.aborted)c.abort();options.signal?.addEventListener('abort',cancel,{once:true});activeTraktRequests.add(c);
  try{const result=await fetchResult(url,{...options,signal:c.signal});checkTraktGeneration(generation);return result;}finally{activeTraktRequests.delete(c);options.signal?.removeEventListener('abort',cancel);}
}
function clearTraktAuth() {
  authGeneration++;cancelTraktRequests();clearTimeout(traktSaveTimer);localStorage.removeItem(TRAKT_AUTH_KEY);refreshPromise=null;
  $('#traktDevice')?.classList.add('hidden');updateTraktUi();
}
async function validTraktToken(force=false,generation=authGeneration) {
  checkTraktGeneration(generation);const a=traktAuth(),now=Math.floor(Date.now()/1000);
  if(!a.accessToken||!traktConnected())return null;
  if(!force&&(a.expiresAt||0)>now+120)return a.accessToken;
  if(refreshPromise){const token=await refreshPromise;checkTraktGeneration(generation);return token;}
  const task=(async()=>{
    if(!a.refreshToken||!serviceCredential('trakt')||!serviceCredential('traktSecret'))throw Error('Renouvellement impossible : vérifie les identifiants Trakt.');
    const r=await guardedTraktFetch('https://auth.trakt.tv/oauth/token',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({refresh_token:a.refreshToken,client_id:serviceCredential('trakt'),client_secret:serviceCredential('traktSecret'),redirect_uri:'urn:ietf:wg:oauth:2.0:oob',grant_type:'refresh_token'})},generation);
    checkTraktGeneration(generation);
    if(!r.ok||!r.json?.access_token) {
      if([400,401].includes(r.status)){clearTraktAuth();throw Error('Autorisation Trakt expirée : reconnecte le compte.');}
      throw Error(`Renouvellement Trakt temporairement indisponible (${r.status}). Le compte reste enregistré.`);
    }
    const expires=Number(r.json.created_at||now)+Number(r.json.expires_in||604800);
    saveTraktAuth({...traktAuth(),accessToken:r.json.access_token,refreshToken:r.json.refresh_token||a.refreshToken,expiresAt:expires});
    return r.json.access_token;
  })();refreshPromise=task;
  try{return await task;}finally{if(refreshPromise===task)refreshPromise=null;}
}

async function traktResponse(path,opts={},generation=authGeneration) {
  checkTraktGeneration(generation);
  let token=await validTraktToken(false,generation);checkTraktGeneration(generation);if(!token)throw Error('Connecte ton compte Trakt dans Réglages.');
  const make=()=>guardedTraktFetch(`https://api.trakt.tv${path}`,{...opts,headers:{...traktHeaders(token),...(opts.headers||{})}},generation);
  let r=await make();checkTraktGeneration(generation);
  if(r.status===401){token=await validTraktToken(true,generation);checkTraktGeneration(generation);if(!token)throw Error('Session Trakt indisponible.');r=await make();}
  checkTraktGeneration(generation);
  if(!r.ok) {
    const messages={401:'Autorisation Trakt refusée.',403:'Action non autorisée par Trakt.',420:'Limite du compte Trakt atteinte.',423:'Compte Trakt verrouillé.',429:'Trop de requêtes Trakt. La file sera conservée.',500:'Service Trakt temporairement indisponible.',502:'Service Trakt temporairement indisponible.',503:'Service Trakt temporairement indisponible.'};
    const e=Error(messages[r.status]||`Trakt HTTP ${r.status}`);e.status=r.status;e.retryAfter=r.headers?.get('Retry-After');throw e;
  }
  return r;
}

async function traktApi(path,opts={}) { return (await traktResponse(path,opts)).json; }
async function traktAll(path) {
  const out=[],limit=100,generation=authGeneration;
  for(let page=1;page<=500;page++) {
    checkTraktGeneration(generation);const r=await traktResponse(`${path}${path.includes('?')?'&':'?'}page=${page}&limit=${limit}`,{},generation);checkTraktGeneration(generation);
    if(!Array.isArray(r.json))throw Error('Trakt a renvoyé une liste invalide.');
    out.push(...r.json);
    const rawCount=r.headers?.get('X-Pagination-Page-Count'),rawPage=r.headers?.get('X-Pagination-Page');
    if(rawCount!=null&&(!/^\d+$/.test(rawCount)||!Number.isSafeInteger(Number(rawCount))))throw Error('Pagination Trakt invalide.');
    if(rawPage!=null&&(!/^\d+$/.test(rawPage)||Number(rawPage)!==page))throw Error('Trakt a renvoyé une autre page.');
    const count=Number(rawCount||0);if(count>500)throw Error('Pagination Trakt supérieure à la limite de sécurité.');
    if(count&&page>=count)return out;
    if(!count&&r.json.length<limit)return out;
  }
  throw Error('La pagination Trakt dépasse la limite de sécurité. Aucune liste partielle n’a été appliquée.');
}
async function startTraktLogin() {
  await ensureAutomaticServices();
  const clientId=serviceCredential('trakt')?.trim(),secret=serviceCredential('traktSecret')?.trim();
  if(!clientId||!secret)throw Error('Le service Trakt doit être activé une fois par l’éditeur ; aucun mot de passe ne doit être saisi ici.');
  const generation=++authGeneration;cancelTraktRequests();
  $('#traktConnect').disabled=true;
  try {
    const r=await guardedTraktFetch('https://auth.trakt.tv/oauth/device/code',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({client_id:clientId})});
    if(generation!==authGeneration)return;
    if(!r.ok||!r.json?.device_code)throw Error(`Trakt OAuth HTTP ${r.status}`);
    const d=r.json,verify=FGCore.safeUrl(d.verification_url)||'https://trakt.tv/activate';
    if(!['trakt.tv','auth.trakt.tv','app.trakt.tv'].includes(new URL(verify).hostname))throw Error('Domaine d’activation Trakt inattendu.');
    const expiresAt=Date.now()+Math.max(30,Number(d.expires_in||600))*1000;
    $('#traktDevice').innerHTML=`<b>Code : ${esc(d.user_code)}</b><p>Ouvre Trakt, connecte-toi et autorise FranchiseGuide.</p><a class="tonal linkbtn" href="${esc(verify)}" target="_blank" rel="noopener noreferrer">Ouvrir la page Trakt</a><button id="cancelTraktLogin" class="tonal">Annuler la connexion</button><small>Ce code expire après ${Math.ceil((expiresAt-Date.now())/60000)} minutes.</small>`;
    $('#traktDevice').classList.remove('hidden');
    $('#cancelTraktLogin').onclick=()=>{authGeneration++;cancelTraktRequests();$('#traktDevice').classList.add('hidden');$('#traktConnect').disabled=false;};
    let interval=Math.max(1,Number(d.interval||5));
    while(Date.now()<expiresAt&&generation===authGeneration) {
      await new Promise(resolve=>setTimeout(resolve,interval*1000));
      if(generation!==authGeneration)return;
      const p=await guardedTraktFetch('https://auth.trakt.tv/oauth/device/token',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({code:d.device_code,client_id:clientId,client_secret:secret})});
      if(generation!==authGeneration)return;
      if(p.status===400)continue;
      if(p.status===429){interval+=5;continue;}
      if(p.status===418)throw Error('Autorisation refusée sur Trakt.');
      if([404,409,410].includes(p.status))throw Error('Code expiré, invalide ou déjà utilisé. Relance la connexion.');
      if(!p.ok||!p.json?.access_token||!p.json?.refresh_token)throw Error(`Connexion Trakt impossible (${p.status}).`);
      const now=Math.floor(Date.now()/1000),oldAccount=syncState.account;
      saveTraktAuth({accessToken:p.json.access_token,refreshToken:p.json.refresh_token,expiresAt:Number(p.json.created_at||now)+Number(p.json.expires_in||604800)});
      const profile=await traktApi('/users/settings');
      checkTraktGeneration(generation);const user=profile?.user;if(!user?.username){clearTraktAuth();throw Error('Profil Trakt introuvable.');}
      saveTraktAuth({...traktAuth(),username:user.username,displayName:user.name||user.username});
      if(oldAccount&&oldAccount!==user.username) {
        // Never upload a previous account's pending actions to a different user.
        const accounts=syncState.accounts||{};accounts[oldAccount]={queue:syncState.queue,baseline:syncState.baseline,remote:syncState.remote};
        syncState={queue:[],baseline:{},remote:[],errors:[],...(accounts[user.username]||{}),account:user.username,accounts};delete syncState.accounts[user.username];
      } else syncState.account=user.username;
      syncStore();savedState=clone(state);$('#traktDevice').classList.add('hidden');updateTraktUi();
      diagnostic('info','trakt','Compte connecté.');
      await traktSync();return;
    }
    if(generation===authGeneration)throw Error('Le code Trakt a expiré.');
  }finally{if(generation===authGeneration)$('#traktConnect').disabled=false;}
}
function byTmdb(kind,id) { if(!id)return null;return state.media.find(m=>m.kind===kind&&String(m.tmdbId)===String(id)); }
function remoteState(data) {
  const map=new Map(),remote=[],remoteIds=new Set();
  function ensure(kind,id) {const key=itemKey(kind,id);if(!map.has(key))map.set(key,{});return map.get(key);}
  function addRemote(kind,x) {
    if(!x?.ids?.tmdb)return;
    const key=itemKey(kind,x.ids.tmdb);if(remoteIds.has(key))return;remoteIds.add(key);
    remote.push({id:`tmdb-${kind}-${x.ids.tmdb}`,kind,title:x.title||key,year:x.year,tmdbId:x.ids.tmdb,imdbId:x.ids.imdb,source:'tmdb',sourceId:String(x.ids.tmdb),added:false,suggestion:true,traktId:x.ids.trakt});
  }
  for(const m of state.media)if(m.tmdbId) {
    const v=ensure(m.kind,m.tmdbId);
    if(data.watchedMovies&&m.kind==='movie')Object.assign(v,{watched:false,plays:0,lastWatchedAt:null});
    if(data.collectionMovies&&m.kind==='movie')v.collected=false;
    if(data[m.kind==='show'?'watchShows':'watchMovies'])v.watchlist=false;
    if(data[m.kind==='show'?'ratingShows':'ratingMovies'])v.rating=null;
  }
  for(const e of state.episodes)if(e.tmdbId) {
    const v=ensure('episode',e.tmdbId);
    if(data.watchedShows)Object.assign(v,{watched:false,plays:0,lastWatchedAt:null});
    if(data.collectionShows)v.collected=false;
    if(data.ratingEpisodes)v.rating=null;
  }
  for(const x of data.watchedMovies||[]) {
    addRemote('movie',x.movie);if(x.movie?.ids?.tmdb)Object.assign(ensure('movie',x.movie.ids.tmdb),{watched:true,plays:x.plays||1,lastWatchedAt:Date.parse(x.last_watched_at)||null});
  }
  for(const x of data.watchedShows||[]) {
    addRemote('show',x.show);const m=byTmdb('show',x.show?.ids?.tmdb);if(!m)continue;
    for(const se of x.seasons||[])for(const ep of se.episodes||[]) {
      const e=state.episodes.find(e=>e.showId===m.id&&e.season===se.number&&e.num===ep.number);
      if(e?.tmdbId)Object.assign(ensure('episode',e.tmdbId),{watched:true,plays:ep.plays||1,lastWatchedAt:Date.parse(ep.last_watched_at)||null});
    }
  }
  for(const x of data.collectionMovies||[]) {addRemote('movie',x.movie);if(x.movie?.ids?.tmdb)ensure('movie',x.movie.ids.tmdb).collected=true;}
  for(const x of data.collectionShows||[]) {
    addRemote('show',x.show);const m=byTmdb('show',x.show?.ids?.tmdb);if(!m)continue;
    for(const se of x.seasons||[])for(const ep of se.episodes||[]) {
      const e=state.episodes.find(e=>e.showId===m.id&&e.season===se.number&&e.num===ep.number);
      if(e?.tmdbId)ensure('episode',e.tmdbId).collected=true;
    }
  }
  for(const [list,kind,field] of [['watchMovies','movie','watchlist'],['watchShows','show','watchlist'],['ratingMovies','movie','rating'],['ratingShows','show','rating']])for(const x of data[list]||[]) {
    const m=x[kind];addRemote(kind,m);if(m?.ids?.tmdb)ensure(kind,m.ids.tmdb)[field]=field==='rating'?(x.rating??null):true;
  }
  for(const x of data.ratingEpisodes||[])if(x.episode?.ids?.tmdb)ensure('episode',x.episode.ids.tmdb).rating=x.rating??null;
  return {map,remote};
}
function applyTraktDownloads(data) {
  const {map,remote}=remoteState(data),fieldMap={watched:'history',plays:'history',lastWatchedAt:'history',collected:'collection',watchlist:'watchlist',rating:'ratings'};
  for(const x of [...state.media.map(m=>({target:m,kind:m.kind})),...state.episodes.map(e=>({target:e,kind:'episode'}))]) {
    const item=x.target,key=itemKey(x.kind,item.tmdbId),fresh=map.get(key);if(!fresh)continue;
    const baseline=syncState.baseline[key]||{};
    for(const [field,value] of Object.entries(fresh)) {
      const pending=syncState.queue.some(op=>op.key===key&&op.field===fieldMap[field]);
      if(pending || (item.skipped&&fieldMap[field]==='history'))continue;
      // First sync is a merge. Later explicit remote removals are applied only to fields previously observed remotely.
      if((value!==false&&value!==null&&value!==0)||(Object.hasOwn(baseline,field)&&baseline[field]!==value))item[field]=value;
    }
    item.watchHistory=FGCore.knownWatchHistory(item);
    syncState.baseline[key]={...baseline,...fresh};
  }
  const allLists=['watchedMovies','watchedShows','collectionMovies','collectionShows','watchMovies','watchShows','ratingMovies','ratingShows'];
  if(allLists.every(k=>Array.isArray(data[k])))syncState.remote=remote;
  else if(remote.length)syncState.remote=[...new Map([...(syncState.remote||[]),...remote].map(m=>[itemKey(m.kind,m.tmdbId),m])).values()];
  syncStore();return map;
}
async function traktSync() {
  assertWritable();
  if(traktSyncing||!traktConnected()||(typeof checkInBusy!=='undefined'&&checkInBusy))return;
  traktSyncing=true;const generation=authGeneration;syncState.errors=[];updateTraktUi();
  try {
    toast('Synchronisation Trakt en cours…');
    const specs=[['watchedMovies','/sync/watched/movies'],['watchedShows','/sync/watched/shows?specials=true'],['collectionMovies','/sync/collection/movies'],['collectionShows','/sync/collection/shows'],['watchMovies','/sync/watchlist/movies'],['watchShows','/sync/watchlist/shows'],['ratingMovies','/sync/ratings/movies'],['ratingShows','/sync/ratings/shows'],['ratingEpisodes','/sync/ratings/episodes']];
    const data={};
    // Small bounded concurrency to avoid bursts and to retain independently successful sections.
    for(let i=0;i<specs.length;i+=3) {
      const chunk=specs.slice(i,i+3),results=await Promise.allSettled(chunk.map(([,p])=>traktAll(p)));
      if(generation!==authGeneration)return;
      results.forEach((r,j)=>{if(r.status==='fulfilled')data[chunk[j][0]]=r.value;else syncState.errors.push(`${chunk[j][0]} : ${r.reason.message}`);});
      if(generation!==authGeneration)return;
    }
    const observed=applyTraktDownloads(data);save(false);
    for(const op of [...syncState.queue]) {
      if(generation!==authGeneration)return;
      if(!syncState.queue.some(x=>x.id===op.id))continue;
      if(op.held){syncState.errors.push('Action suspendue après import/restauration : '+op.title);continue;}
      if(op.account!==syncAccount()){syncState.errors.push('Des actions appartiennent à un autre compte et ne seront pas envoyées.');continue;}
      if(op.uncertain) {
        const remote=observed.get(op.key),stamp=Object.values(op.body)[0][0]?.watched_at;
        if(op.field==='history'&&op.value&&remote?.lastWatchedAt&&Date.parse(stamp)===remote.lastWatchedAt) {
          syncState.queue=syncState.queue.filter(x=>x.id!==op.id);syncStore();continue;
        }
        syncState.errors.push(`Confirmation distante incertaine pour « ${op.title} ». Vérifie dans Bibliothèque Trakt avant de réessayer.`);continue;
      }
      try {
        op.attempts++;if(op.field==='history'&&op.value)op.uncertain=true;syncStore();
        const response=await traktApi(op.path,{method:'POST',body:JSON.stringify(op.body)});
        if(generation!==authGeneration)return;
        if(response?.not_found&&Object.values(response.not_found).some(a=>Array.isArray(a)&&a.length))throw Object.assign(Error('Identifiant non reconnu par Trakt.'),{status:422});
        if(!FGCore.syncAcknowledged(response,Object.keys(op.body)[0]))throw Error('Accusé Trakt incomplet : vérifie la lecture distante avant de réessayer.');
        syncState.queue=syncState.queue.filter(x=>x.id!==op.id);syncStore();
      }catch(e) {
        if(generation!==authGeneration)return;
        op.error=e.message;
        // A timeout after a non-idempotent history POST must not be blindly retried.
        if(op.field==='history'&&op.value)op.uncertain=![400,401,403,404,409,410,412,413,415,422,423,426,429].includes(e.status);
        syncState.errors.push(e.message);syncStore();
        if([401,423,429].includes(e.status)||op.uncertain)break;
      }
    }
    if(generation!==authGeneration)return;
    const a=traktAuth();a.lastAttempt=new Date().toISOString();a.lastError=syncState.errors.length?syncState.errors[0]:null;
    if(!syncState.errors.length&&!syncState.queue.length)a.lastSync=a.lastAttempt;
    saveTraktAuth(a);syncStore();savedState=clone(state);save(false);
    diagnostic(syncState.errors.length?'warn':'info','trakt',syncState.errors.length?`${syncState.errors.length} erreur(s), ${syncState.queue.length} action(s) conservée(s)`:'Synchronisation réussie.');
    toast(syncState.errors.length?`Trakt : synchronisation partielle, ${syncState.queue.length} action(s) en attente.`:'Trakt synchronisé.');
    if(overlay?.type==='trakt-library')render();
  }finally{traktSyncing=false;updateTraktUi();}
}
function updateTraktUi() {
  const host=$('#traktStatus');if(!host)return;const a=traktAuth(),connected=!!a.accessToken;
  host.innerHTML=connected?`<b>${traktSyncing?'Synchronisation…':'Connecté'} · ${esc(a.displayName||a.username||'Trakt')}</b><small>${a.lastSync?'Dernière réussite : '+esc(humanDate(a.lastSync)):'Première synchronisation non terminée'}</small><small>${syncState?.queue?.length||0} action(s) en attente</small>${a.lastError?`<p role="alert" class="notice warn">${esc(a.lastError)}</p>`:''}`:'<b>Compte Trakt non connecté</b><small>OAuth : ton mot de passe est saisi uniquement sur le site Trakt.</small>';
  $('#traktConnect')?.classList.toggle('hidden',connected);$('#traktDisconnect')?.classList.toggle('hidden',!connected);$('#traktSyncNow')?.classList.toggle('hidden',!connected);
  if($('#traktSyncNow'))$('#traktSyncNow').disabled=traktSyncing;
}
function renderTraktLibrary() {
  const remote=syncState.remote||[];
  $('#content').innerHTML=`<section class="hero"><h2>Bibliothèque Trakt</h2><p>Les séries et films trouvés dans ton compte peuvent être ajoutés ci-dessous. Chaque ajout charge la fiche complète TMDb ; les états de visionnage seront repris à la synchronisation suivante.</p><div class="actions"><button id="openTraktSettings" class="tonal">Compte & connexion</button><button id="syncTraktView" class="primary" ${!traktConnected()?'disabled':''}>Synchroniser</button></div></section><section class="panel"><h3>File d’envoi · ${syncState.queue.length}</h3>${(syncState.errors||[]).map(e=>`<p role="alert" class="notice warn">${esc(e)}</p>`).join('')}${syncState.queue.slice(0,100).map(op=>`<div class="queue-item"><b>${esc(op.title)}</b> · ${esc(op.field)}${op.error?`<p>${esc(op.error)}</p>`:''}${op.held?`<p class="notice warn">Suspendue : ${esc(op.held)}</p><button class="tonal" data-resume-op="${esc(op.id)}">Examiner avant envoi</button><button class="tonal" data-discard-op="${esc(op.id)}">Annuler cet envoi local</button>`:''}${op.uncertain&&!op.held?`<button class="tonal" data-retry-op="${esc(op.id)}">Réessayer après vérification</button><button class="tonal" data-ack-op="${esc(op.id)}">Déjà appliqué sur Trakt</button>`:''}</div>`).join('')||'<p>Aucune modification en attente.</p>'}</section><h3 class="hero">Titres du compte · ${remote.length}</h3><section id="remoteTraktTitles"></section>`;
  remoteResults=remote.filter(m=>!state.media.some(x=>x.added&&FGCore.sameMedia(x,m)));
  $('#remoteTraktTitles').innerHTML=remote.map(m=>{const local=state.media.find(x=>x.added&&FGCore.sameMedia(x,m));return local?row(local):row({...m,suggestion:true},'remote');}).join('')||empty('Aucun titre récupéré','Connecte ton compte, puis lance une synchronisation.');
  $('#openTraktSettings').onclick=()=>{theme();$('#settingsDialog').showModal();};$('#syncTraktView').onclick=()=>traktSync().catch(e=>toast(e.message));
  $$('[data-retry-op]').forEach(b=>b.onclick=()=>{if(!confirm('As-tu vérifié que ce visionnage n’existe pas déjà sur Trakt ? Réessayer pourrait créer un doublon.'))return;const op=syncState.queue.find(o=>o.id===b.dataset.retryOp);op.uncertain=false;op.error=null;syncStore();render();});
  $$('[data-ack-op]').forEach(b=>b.onclick=()=>{if(!confirm('Confirmer que cette action est déjà appliquée sur Trakt et la retirer de la file locale ?'))return;syncState.queue=syncState.queue.filter(o=>o.id!==b.dataset.ackOp);syncStore();render();});bindHeldOperations();bindRows();
}
function scheduleInitialTraktSync() {
  if(!storageWritable||!traktConnected()||state.settings.traktAutoSync===false)return;
  const a=traktAuth(),hours=FGCore.integer(state.settings.traktSyncIntervalHours||6,1,168),last=Date.parse(a.lastSync||'')||0;
  if(syncState.queue.some(op=>!op.held&&!op.uncertain)||Date.now()-last>hours*3600000)setTimeout(()=>traktSync().catch(e=>{diagnostic('warn','trakt',e.message);toast(e.message);}),900);
}
