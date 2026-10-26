const $ = (s) => document.querySelector(s);
const $$ = (s) => [...document.querySelectorAll(s)];
const KEY = 'franchiseguide-web-v8';
const SERVICES_KEY = 'franchiseguide-services-v1';
const SYNC_KEY = 'franchiseguide-sync-v2';
const LOG_KEY = 'franchiseguide-diagnostics-v1';
const TRAKT_AUTH_KEY = 'franchiseguide-trakt-auth-v1';
const LEGACY_KEYS = ['franchiseguide-web-v7','franchiseguide-web-v6','franchiseguide-web-v5','franchiseguide-web-v4', 'franchiseguide-preview-v3', 'franchiseguide-preview-v2'];
const BACKUP_KEY = 'franchiseguide-web-backups-v2';
const DAY = 86400000;
const clone = (v) => JSON.parse(JSON.stringify(v));
const today = () => FGCore.localDate();
const dateOffset = (n) => FGCore.shiftDate(today(), n);
const esc = (v = '') => String(v).replace(/[&<>"']/g, (c) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const kindName = (k) => ({show:'Série',movie:'Film',game:'Jeu vidéo',book:'Livre'}[k] || k);
const icon = (k) => ({show:'▣',movie:'▤',game:'🎮',book:'▥'}[k] || '•');
const tmdbImage = (path, size = 'w500') => !path ? null : /^https:\/\//.test(path) ? FGCore.safeUrl(path) : /^\/[A-Za-z0-9._-]+$/.test(path) ? `https://image.tmdb.org/t/p/${size}${path}` : null;
const epCode = (e) => e.seasonUnknown||e.numberUnknown?'Numérotation à compléter':`S${String(e.season || 0).padStart(2,'0')}E${String(e.num ?? e.number ?? 0).padStart(2,'0')}`;
const identity = (m) => FGCore.aliases(m)[0];
const pct = (a,b) => b > 0 ? Math.min(100, Math.round(a/b*100)) : 0;

const seed = {
  schema: 11,
  settings: {
    dark:false, tmdbKey:'', rawgKey:'', traktId:'', traktSecret:'', language:'fr-FR', region:'FR',
    traktAutoSync:true, traktSyncIntervalHours:6,
    detailJingleEnabled:true, hideEpisodeSpoilers:false, showCastImages:true,
    statisticsIncludeRewatches:true, includeSpecials:false, recentDays:30, upcomingDays:60,
    includeHiddenInCalendar:false, statusFilters:{}, librarySort:{}, discoverKind:'show',
    discoverYearMin:0, discoverYearMax:0, discoverLanguage:'',
    autoBackupEnabled:true, autoBackupMinutes:30, catalogAutoRefresh:true, detailJingleVolume:42
  },
  media: [], episodes: [], suggestions: [],
  lists: [{id:'later',name:'À voir / lire / jouer',description:'',sortMode:'title',ascending:true}],
  gaming:{version:1,accounts:[],sets:[]},
  customFranchises: []
};

let loadIssue = null, recoveryOnly = null;
function decodeStored(raw,key) {
  try {
    const value=JSON.parse(raw);
    FGCore.validateBackup(value);
    if(value._gamingControl!=null)FGSyncPolicy.normalize(value._gamingControl);
    if(value.settings!=null && (typeof value.settings!=='object'||Array.isArray(value.settings)))throw Error('Réglages enregistrés invalides.');
    return value;
  } catch(error) {
    // Do not migrate or overwrite a newer/malformed library. Keep the EXACT original bytes.
    recoveryOnly={raw,key};loadIssue='Bibliothèque non prise en charge : '+error.message+' Les données originales restent intactes. Exporte la copie brute avant toute intervention.';
    return clone(seed);
  }
}
function load() {
  try {
    for(const key of [KEY,...LEGACY_KEYS]) {
      const raw=localStorage.getItem(key);
      if(raw)return decodeStored(raw,key);
    }
  } catch(error) {
    recoveryOnly={raw:null,key:KEY};loadIssue='Accès au stockage refusé. Aucune écriture autorisée sur cette origine.';
  }
  return clone(seed);
}

function normalizeRating(r) {
  if (!r) return null;
  return {
    source:r.source || r.label || 'Catalogue', label:r.label || r.source || 'Catalogue',
    score:Number(r.score ?? r.rating ?? 0), maxScore:Number(r.maxScore || 10),
    votes:r.votes == null ? null : Number(r.votes), externalUrl:r.externalUrl || null
  };
}
function migrate(s) {
  s = s || clone(seed);
  s.schema = 11;
  s.settings = {...clone(seed.settings), ...(s.settings || {})};
  s.media = (s.media || []).map((m) => ({
    ...m,
    kind:String(m.kind || 'movie').toLowerCase(), added:m.added!==false, suggestion:!!(m.suggestion??m.isSuggestion), franchise:m.franchise||m.franchiseId||null, seasons:m.seasons||[], listIds:m.listIds || [], plays:m.plays || +(!!m.watched),
    hidden:!!m.hidden, notify:m.notify !== false, genres:m.genres || [], platforms:m.platforms || [],
    providers:m.providers || [], people:m.people || [], credits:(m.credits || []).map(c => typeof c === 'string' ? {name:c,role:'',department:'Cast',profileUrl:null} : c),
    ratings:(m.ratings || []).map(normalizeRating).filter(Boolean), productionCompanies:m.productionCompanies || []
  }));
  s.episodes = (s.episodes || []).map((e) => ({
    ...e, num:e.num ?? e.number ?? 0, date:e.date ?? e.airDate ?? null,
    plays:e.plays || +(!!e.watched), skipped:!!e.skipped, collected:!!e.collected,
    runtimeMinutes:e.runtimeMinutes ?? 45
  }));
  s.suggestions = (s.suggestions || []).map(m => ({...m,kind:String(m.kind || 'movie').toLowerCase(),suggestion:true,added:false,listIds:m.listIds || [],genres:m.genres || [],credits:m.credits || [],ratings:m.ratings || []}));
  s.lists = (s.lists || []).map((l,i) => ({id:l.id || `list-${i}`,name:l.name || 'Liste',description:l.description || '',sortMode:String(l.sortMode || 'title').toLowerCase(),ascending:l.ascending !== false,manualOrder:l.manualOrder===true,sortOrder:l.sortOrder||[]}));
  s.customFranchises = s.customFranchises || [];
  s.gaming=FGGaming.validateState(s.gaming,s.media);
  for (const m of [...s.media,...s.suggestions]) {
    if(m.steam!=null)m.steam=FGSteam.metadata(m.steam);
    ['posterUrl','backdropUrl','externalUrl','homepage','trailerUrl'].forEach(k=>m[k]=FGCore.safeUrl(m[k]));
    (m.credits||[]).forEach(c=>{ c.profileUrl=FGCore.safeUrl(c.profileUrl); c.externalUrl=FGCore.safeUrl(c.externalUrl); });
  }
  for (const e of s.episodes) { e.watched=!!e.watched&&!e.skipped; if (!e.watched) {e.plays=0;e.lastWatchedAt=null;} }
  for(const x of [...s.media,...s.episodes])x.watchHistory=FGCore.knownWatchHistory(x);
  s.settings.statusFilters = s.settings.statusFilters || {};
  s.settings.librarySort = s.settings.librarySort || {};
  return s;
}

let state = migrate(load());
let serviceRaw=null;
try{serviceRaw=localStorage.getItem(SERVICES_KEY);FGSettings.reference(state._traktAppRef);Object.assign(state.settings,FGSettings.read(serviceRaw,state._servicesRef,state.settings));}
catch(e){recoveryOnly={raw:serviceRaw,key:SERVICES_KEY};loadIssue='Accès non récupérables : '+e.message+' Bibliothèque consultable, écritures bloquées.';}
let savedState = clone(state);
let libraryEpoch=0;
let searchGeneration=0, discoverGeneration=0, catalogBusy=new Set(), authGeneration=0, refreshPromise=null;
let activeAudio=null, searchPage=1, searchHasMore=false, searchErrors=[];
let syncState;
let catalogRefreshRunning=false;

let screen = 'shows', tab = 0, overlay = null, kindFilter = 'all', remoteResults = [], lastSnapshot = 0;
let detailJingleFor = null;
let traktSyncing = false, traktSaveTimer = null;
function traktAuth(){try{return JSON.parse(localStorage.getItem(TRAKT_AUTH_KEY)||'{}')}catch{return{}}}
function saveTraktAuth(a){const next={...(a||{})};if(next.accessToken)next._appRef=state._traktAppRef||null;localStorage.setItem(TRAKT_AUTH_KEY,JSON.stringify(next));updateTraktUi();}

function traktConnected(){const a=traktAuth();return !!a.accessToken&&(a._appRef||null)===(state._traktAppRef||null);}
function traktAccountLabel(){const a=traktAuth();return a.displayName||a.username||'';}
function traktHeaders(token){return {'Content-Type':'application/json','trakt-api-version':'2','trakt-api-key':serviceCredential('trakt')||'',...(token?{Authorization:`Bearer ${token}`}:{})};}
const FGRequests=FGNetwork.create(automaticServiceFetch);
async function fetchResult(url,opts={}) { return FGRequests.result(url,opts); }

function save(markTraktDirty=true) {
  try {
    assertWritable();
    const raw=localStorage.getItem(KEY),disk=raw?JSON.parse(raw):null;
    const prepared=FGSettings.stage(localStorage.getItem(SERVICES_KEY),disk?._servicesRef,disk?.settings||{},serviceSettings(),'slot-'+(crypto.randomUUID?crypto.randomUUID():Date.now().toString(36)+'-'+Math.random().toString(36).slice(2)));
    if(prepared.traktChanged&&traktSyncing)throw Error('Synchronisation Trakt active : attends sa fin avant de modifier son application.');
    if(prepared.raw!==null)localStorage.setItem(SERVICES_KEY,prepared.raw);
    if(prepared.traktChanged)state._traktAppRef=prepared.ref;
    if(markTraktDirty&&traktConnected())queueLocalChanges(savedState,state,false);
    persistEnvelope(prepared.ref);savedState=clone(state);
    if(prepared.traktChanged){authGeneration++;cancelTraktRequests();clearTimeout(traktSaveTimer);refreshPromise=null;updateTraktUi();}
  }catch(e){storageFailure(e);throw e;}
  maybeBackup();
  if(markTraktDirty&&traktConnected()&&state.settings.traktAutoSync!==false&&syncState.queue.length){
    clearTimeout(traktSaveTimer);traktSaveTimer=setTimeout(()=>traktSync().catch(e=>toast('Trakt : '+e.message)),2500);
  }
  return true;
}
function backups(){ try{return JSON.parse(localStorage.getItem(BACKUP_KEY)||'[]')}catch{return[]} }
function restoreBackup(i) {
  const b=backups()[i]; if(!b)return;
  if(!confirm('Restaurer cette sauvegarde locale ? Une copie de l’état actuel sera conservée. Aucun effacement ne sera envoyé à Trakt.'))return;
  const services=serviceSettings(), restored=fromPortableBackup(b.state);
  requireSafetySnapshot('Avant restauration');
  prepareLibraryReplacement('Restauration d’une sauvegarde');
  state=restored;Object.assign(state.settings,services);save(false);
  theme();overlay=null;render();toast('Sauvegarde restaurée');diagnostic('info','backup','Restauration locale effectuée');
}
function serviceSettings(){ return {tmdbKey:state.settings.tmdbKey||'',rawgKey:state.settings.rawgKey||'',traktId:state.settings.traktId||'',traktSecret:state.settings.traktSecret||''}; }
function humanDate(s){ try{return new Intl.DateTimeFormat('fr-FR',{dateStyle:'short',timeStyle:'short'}).format(new Date(s))}catch{return s} }

let groupedEpisodesRef=null,groupedEpisodes=null;
function episodesFor(id){if(groupedEpisodesRef!==state.episodes){groupedEpisodesRef=state.episodes;groupedEpisodes=FGCore.episodeIndex(state.episodes);}return groupedEpisodes.get(id)||[];}
function mediaStatus(m) {
  if(FGProgress.applicable(m))return FGProgress.label(m)+(m.watched?` · ${Math.max(1,m.plays||0)} fin(s) historique(s)`:'');
  if (m.hidden) return 'Masqué';
  if (m.kind === 'show') {
    const unwatched = episodesFor(m.id).filter(e => !e.watched && !e.skipped && (state.settings.includeSpecials||e.season!==0)).sort((a,b)=>a.season-b.season||a.num-b.num)[0];
    return unwatched ? `${epCode(unwatched)} ${state.settings.hideEpisodeSpoilers?'Épisode non vu':unwatched.title}` : episodesFor(m.id).length?'À jour':m.catalogComplete?'Aucun épisode annoncé':'Fiche à compléter';
  }
  if (m.kind === 'movie') return m.watched ? `Vu${m.plays>1?` ${m.plays}×`:''}` : m.watchlist ? 'À voir' : 'Non vu';
  if (m.kind === 'game') return m.watched ? `Terminé${m.plays>1?` ${m.plays}×`:''}` : (m.started || m.progressCurrent) ? 'En cours' : 'À jouer';
  return m.watched ? `Lu${m.plays>1?` ${m.plays}×`:''}` : (m.started || m.progressCurrent) ? 'Lecture en cours' : 'À lire';
}
function posterHtml(m, cls='poster') {
  return `<div class="${cls} image-box">${FGCore.safeUrl(m.posterUrl)?`<img src="${esc(FGCore.safeUrl(m.posterUrl))}" alt="" loading="lazy" decoding="async">`:''}<span class="image-placeholder">${icon(m.kind)}</span></div>`;
}
function ratingText(m) {
  const parts=[];
  if(m.rating!=null) parts.push(`Moi ${m.rating}/10`);
  (m.ratings||[]).slice(0,2).forEach(r=>parts.push(`${r.label||r.source} ${Number(r.score).toFixed(1)}/${r.maxScore||10}`));
  if(!m.ratings?.length && m.catalogRating) parts.push(`Catalogue ${Number(m.catalogRating).toFixed(1)}/10`);
  return parts.join(' · ');
}
function row(m, src='media') {
  const progress=m.progressTotal?`<div class="progress"><i style="width:${pct(m.progressCurrent||0,m.progressTotal)}%"></i></div><div class="meta">${m.progressCurrent||0}/${m.progressTotal} ${esc(m.progressUnit||'')}</div>`:'';
  return `<article class="row ${m.suggestion?'missing':''}" data-open="${esc(m.id)}" data-src="${src}">${posterHtml(m)}<div class="info"><h3>${esc(m.title)}${m.favorite?' ★':''}</h3><div class="meta">${[kindName(m.kind),m.year,m.creator,m.franchiseName].filter(Boolean).map(esc).join(' · ')}</div><div>${m.suggestion?'Suggestion manquante':esc(mediaStatus(m))}</div>${ratingText(m)?`<div class="meta ratings-inline">${esc(ratingText(m))}</div>`:''}${progress}</div><button class="action" data-act="${m.suggestion?'add':'done'}" data-id="${esc(m.id)}">${m.suggestion?'+':m.watched?'↻':'✓'}</button></article>`;
}
function tabs(labels){ return `<div class="tabs">${labels.map((x,i)=>`<button data-tab="${i}" class="${i===tab?'selected':''}">${esc(x)}</button>`).join('')}</div>`; }
function empty(t,b){ return `<div class="empty"><h2>${esc(t)}</h2><p>${esc(b)}</p></div>`; }
function visible(k){ return state.media.filter(m=>m.kind===k&&m.added&&!m.hidden); }
function libraryAdd(kind,label){return `<div class="library-tools"><button class="primary" data-add-kind="${kind}">+ Ajouter / rechercher ${esc(label)}</button><button class="tonal" data-next-kind="${kind}">À reprendre</button><button class="tonal" data-organize-kind="${kind}">Organiser</button></div>`;}
function bindLibraryAdd(){$$('[data-add-kind]').forEach(b=>b.onclick=()=>openScopedSearch(b.dataset.addKind));$$('[data-next-kind]').forEach(b=>b.onclick=()=>openNextUp(b.dataset.nextKind));$$('[data-organize-kind]').forEach(b=>b.onclick=()=>openOrganizer(b.dataset.organizeKind));}
function openScopedSearch(kind){kindFilter=kind||'all';$$('#kinds button').forEach(x=>x.classList.toggle('selected',x.dataset.kind===kindFilter));$('#q').value='';$('#searchDialog').showModal();$('#q').focus();searchResults();}

function render() {
  const titleMap={shows:'Séries',movies:'Films',games:'Jeux vidéo',books:'Livres',franchises:'Franchises',calendar:'Agenda',more:'Plus'};
  $('#title').textContent=overlay?overlay.title:titleMap[screen];
  $('#back').classList.toggle('hidden',!overlay);
  $('#nav').classList.toggle('hidden',!!overlay);
  $$('#nav button').forEach(b=>{b.classList.toggle('active',b.dataset.screen===screen);b.setAttribute('aria-current',b.dataset.screen===screen?'page':'false');});
  $$('[data-rail]').forEach(b=>b.classList.toggle('active',b.dataset.rail===screen));
  if(overlay) renderOverlay(overlay);
  else ({shows:renderShows,movies:()=>renderLibrary('movie',['Films','Vus','À voir','À venir','Collection']),games:()=>renderLibrary('game',['Jeux','Parcours terminés','En cours','À jouer','En pause','Abandonnés']),books:()=>renderLibrary('book',['Livres','Parcours lus','En cours','À lire','En pause','Abandonnés']),franchises:renderFranchises,calendar:renderCalendar,more:renderMore}[screen])();
  bindRows();bindLibraryTools();bindPagedLibrary();enhanceAccessibility();
  if(overlay?.type==='item'){ enhanceDetail(); const item=state.media.find(m=>m.id===overlay.id);if(item&&typeof addDatedViewingActions==='function')addDatedViewingActions(item); }
  enhanceGamingNavigation();
  if(typeof enhanceSteamConnect==='function')enhanceSteamConnect();
  if(typeof enhanceRetroConnect==='function')enhanceRetroConnect();
  if(typeof enhanceSyncCenter==='function')enhanceSyncCenter();
  if(typeof enhanceFranchiseControls==='function')enhanceFranchiseControls();
  if(typeof enhancePersonalLists==='function')enhancePersonalLists();
}
function bindTabs(){ $$('[data-tab]').forEach(b=>b.onclick=()=>{tab=+b.dataset.tab;render()}); }
function renderShows() {
  let html=tabs(['Ajoutées','Historique','À venir','Récentes'])+libraryAdd('show','une série')+libraryTools('show',true);
  const a=FGCore.statusFilter(visible('show'),state.settings.statusFilters);
  if(tab===0)html+=pagedLibrary(sorted(a,'show'),'shows');
  else if(tab===1) html+=a.filter(m=>lastActivity(m)>0).sort((a,b)=>lastActivity(b)-lastActivity(a)).map(m=>row(m)).join('')||empty('Pas encore de visionnage','Marque un épisode comme vu depuis sa fiche.');
  else {
    const es=FGCore.calendarEpisodes(state.episodes,a,state.settings,tab===2?'upcoming':'recent');
    html+=calendarToolbar(tab===2?'upcoming':'recent')+(es.map(episodeFeedRow).join('')||empty('Aucun épisode dans cette plage','Change la période ou ajoute des séries.'));
  }
  $('#content').innerHTML=html;bindTabs();bindLibraryAdd();
}
function episodeFeedRow(e) {
  const m=state.media.find(x=>x.id===e.showId&&(state.settings.includeHiddenInCalendar||!x.hidden));
  if(!m)return '';
  const hidden=state.settings.hideEpisodeSpoilers&&!e.watched;
  return `<article class="row" data-open="${esc(m.id)}" data-src="media">${posterHtml(m)}<div class="info"><small>${esc(e.date||'Date inconnue')} · ${epCode(e)}</small><h3>${esc(m.title)}</h3><div>${hidden?'Épisode non vu':esc(e.title)}</div><span class="meta">${e.skipped?'Ignoré':e.watched?'Vu':'À voir'}${e.season===0?' · Spécial':''}</span></div></article>`;
}
function renderLibrary(k,labels) {
  let a=visible(k);
  if(tab===1)a=a.filter(m=>k==='movie'?m.watched:FGProgress.status(m)==='completed');
  if(tab===2)a=a.filter(m=>k==='movie'?m.watchlist:FGProgress.status(m)==='active');
  if(tab===3)a=a.filter(m=>k==='movie'?(m.releaseDate&&m.releaseDate>today()):FGProgress.status(m)==='planned');
  if(tab===4)a=a.filter(m=>k==='movie'?m.collected:FGProgress.status(m)==='paused');
  if(tab===5&&k!=='movie')a=a.filter(m=>FGProgress.status(m)==='abandoned');
  const label=k==='movie'?'un film':k==='game'?'un jeu':'un livre';
  $('#content').innerHTML=tabs(labels)+libraryAdd(k,label)+libraryTools(k)+pagedLibrary(sorted(a,k),k);
  bindTabs();bindLibraryAdd();
}

const baseDefs=[['alien','Alien'],['predator','Predator'],['stargate','Stargate'],['star-wars','Star Wars'],['star-trek','Star Trek'],['marvel','Marvel'],['dc','DC'],['witcher','The Witcher'],['resident-evil','Resident Evil'],['mass-effect','Mass Effect'],['dune','Dune'],['terminator','Terminator'],['matrix','Matrix'],['jurassic','Jurassic'],['silent-hill','Silent Hill'],['metal-gear','Metal Gear']];
function defs(){return [...baseDefs,...state.customFranchises.map(f=>[f.id,f.name])];}
function groups(){return defs().map(([id,name])=>{const items=[...state.media,...state.suggestions].filter(m=>(m.franchise||m.franchiseId)===id),added=items.filter(m=>m.added&&!m.suggestion).length;return{id,name,items,added};}).filter(g=>g.items.length);}
function renderFranchises(){
  $('#content').innerHTML=`<section class="hero"><h2>Tes univers</h2><p>Séries, films, jeux et livres réunis. Les œuvres manquantes sont grisées.</p></section><section class="grid">${groups().map(g=>`<div class="card" data-fr="${esc(g.id)}"><h3>${esc(g.name)}</h3><div class="meta">${g.added}/${g.items.length} ajoutés · ${g.items.length-g.added} manquants</div><div class="progress"><i style="width:${pct(g.added,g.items.length)}%"></i></div><div class="minis">${g.items.slice(0,4).map(m=>`<div class="mini ${m.suggestion?'missing':''}">${icon(m.kind)} ${esc(m.title)}</div>`).join('')}</div></div>`).join('')}</section>`;
  $$('[data-fr]').forEach(x=>x.onclick=()=>{const g=groups().find(g=>g.id===x.dataset.fr);overlay={type:'franchise',title:g.name,id:g.id};render();});
}
function renderMore(){
  const listCounts=new Map();for(const m of state.media)if(m.added&&!m.suggestion)for(const id of new Set(m.listIds||[]))listCounts.set(id,(listCounts.get(id)||0)+1);
  const quick=[['today','☀','Aujourd’hui'],['next-up','▶','À regarder / reprendre'],['organize','☑','Organiser ma bibliothèque'],['setup','⚙','Bien démarrer'],['privacy','◇','Confidentialité'],['stats','▥','Statistiques'],['gaming','🏆','Trophées & comptes jeux'],['sync-center','↻','Centre de synchronisation'],['discover','✦','Découvrir'],['history','↶','Historique'],['collection','▣','Collection'],['watchlist','◇','À faire'],['favorites','★','Favoris'],['hidden','◌','Masqués'],['maintenance','◉','Données & hors ligne'],['catalog-health','↻','Entretien des catalogues'],['integrity','🛠','Intégrité des données'],['diagnostics','⚙','Diagnostic'],['trakt-library','↔','Bibliothèque Trakt'],['about','ⓘ','À propos']];
  $('#content').innerHTML=`<section class="hero"><h2>Centre de contrôle</h2><p>Accès aux vues globales, listes, imports, sauvegardes et options.</p></section>${typeof backupSafetyBanner==='function'?backupSafetyBanner():''}<section class="quick-grid">${quick.map(([id,ic,label])=>`<button class="quick" data-quick="${id}"><b>${ic}</b><span>${label}</span></button>`).join('')}</section><h2 class="hero">Listes (${state.lists.length}) <button id="newList" class="tonal">+ Créer</button></h2><section class="menu">${state.lists.slice(0,30).map(l=>`<div data-list="${esc(l.id)}">${esc(l.name)} · ${listCounts.get(l.id)||0}</div>`).join('')||'<div>Aucune liste</div>'}</section><h2 class="hero">Sauvegardes locales <button id="backupNow" class="tonal">Créer maintenant</button></h2><section class="menu">${backups().map((b,i)=>`<div data-backup="${i}">↶ ${humanDate(b.at)}</div>`).join('')||'<div>Aucune sauvegarde automatique</div>'}</section><h2 class="hero">Données & application</h2><section class="menu"><div id="export">⇧ Exporter FranchiseGuide V11 (JSON en clair)</div><button id="exportProtected" class="tonal" type="button">🔒 Exporter avec une phrase secrète</button><div id="import">⇩ Importer FranchiseGuide</div><div id="seriesguide">⇥ Importer depuis SeriesGuide</div><div id="installApp">⊕ Installer la PWA</div><div id="newFr">⌘ Créer une franchise personnalisée</div><div id="settingsMenu">⚙ Réglages</div><div id="reset">↻ Réinitialiser la bibliothèque locale</div></section>`;
  if(typeof bindBackupSafety==='function')bindBackupSafety($('#content'));
  $$('[data-quick]').forEach(b=>b.onclick=()=>{const id=b.dataset.quick;overlay={type:['today','catalog-health','next-up','organize','setup','privacy','gaming','sync-center','stats','discover','history','maintenance','integrity','diagnostics','trakt-library','about'].includes(id)?id:'subset',mode:id,title:{today:'Aujourd’hui','catalog-health':'Entretien des catalogues','next-up':'À regarder / reprendre','organize':'Organiser ma bibliothèque','sync-center':'Centre de synchronisation',gaming:'Trophées & comptes jeux',setup:'Bien démarrer',privacy:'Confidentialité',stats:'Statistiques',discover:'Découvrir',history:'Historique',collection:'Collection',watchlist:'À faire',favorites:'Favoris',hidden:'Masqués',maintenance:'Données & hors ligne',integrity:'Intégrité des données',diagnostics:'Diagnostic', 'trakt-library':'Bibliothèque Trakt',about:'À propos'}[id]};render();});
  $('#backupNow').onclick=()=>{const ok=createBackup('Manuelle');render();toast(ok?'Sauvegarde créée':'Échec de sauvegarde : exporte un fichier JSON.');}; $('#export').onclick=exportFg;$('#exportProtected').onclick=openProtectedExport; $('#import').onclick=()=>{overlay={type:'import-center',title:'Importer et vérifier'};render();}; $('#seriesguide').onclick=()=>$('#importSg').click(); $('#settingsMenu').onclick=()=>{fillExtraSettings();$('#settingsDialog').showModal();};
  $('#installApp').onclick=installPwa; $$('[data-backup]').forEach(x=>x.onclick=()=>reviewLocalRestore(+x.dataset.backup));
  $('#newList').onclick=()=>{const name=prompt('Nom de la liste ?');if(name?.trim()){state.lists.push({id:crypto.randomUUID?.()||`l-${Date.now()}`,name:name.trim(),description:'',sortMode:'title',ascending:true});save();render();}};
  $('#newFr').onclick=()=>{const name=prompt('Nom de la franchise ?')?.trim();if(!name)return;if(state.customFranchises.some(f=>f.name.localeCompare(name,'fr',{sensitivity:'base'})===0))return toast('Cette franchise existe déjà.');const id='custom-'+(crypto.randomUUID?.()||Date.now()+'-'+Math.random().toString(36).slice(2));state.customFranchises.push({id,name});save();render();};
  $$('[data-list]').forEach(x=>x.onclick=()=>{const l=state.lists.find(y=>y.id===x.dataset.list);overlay={type:'list',title:l.name,id:l.id};render();});
  $('#reset').onclick=()=>{if(confirm('Effacer toute la bibliothèque locale ? Cette action ne supprimera rien sur Trakt.')){requireSafetySnapshot('Avant réinitialisation');prepareLibraryReplacement('Réinitialisation de la bibliothèque');const services=serviceSettings();state=clone(seed);Object.assign(state.settings,services);save(false);render();toast('Bibliothèque locale réinitialisée');}};
}

function renderOverlay(o){
  if(o.type==='today')return renderToday();
  if(o.type==='next-up')return renderNextUp();
  if(o.type==='organize')return renderOrganizer();
  if(o.type==='import-center')return renderImportCenter();
  if(o.type==='sync-center')return renderSyncCenter();
  if(o.type==='gaming')return renderGamingHub();
  if(o.type==='achievements')return renderGameAchievements(o);
  if(o.type==='setup')return renderSetup();
  if(o.type==='privacy')return renderPrivacy();
  if(o.type==='person')return renderPerson(o);
  if(o.type==='maintenance')return renderMaintenance();
  if(o.type==='integrity')return renderIntegrity();
  if(o.type==='diagnostics')return renderDiagnostics();
  if(o.type==='trakt-library')return renderTraktLibrary();
  if(o.type==='about')return renderAbout();
  if(o.type==='franchise') return renderFranchiseDetail(o);
  if(o.type==='list') return renderListDetail(o);
  if(o.type==='lists') return renderPersonalLists();
  if(o.type==='catalog-health')return renderCatalogueHealth();
  if(o.type==='stats') return renderStatistics();
  if(o.type==='discover') return renderDiscover();
  if(o.type==='history') return renderHistory();
  if(o.type==='subset') return renderSubset(o.mode);
  return renderMediaDetail(o);
}
function renderFranchiseDetail(o){ const g=groups().find(x=>x.id===o.id);if(!g){overlay=null;return render();}$('#content').innerHTML=`<section class="hero"><h2>${esc(g.name)}</h2><p>${g.added}/${g.items.length} dans la bibliothèque.</p></section>${g.items.map(m=>row(m,m.suggestion?'suggestions':'media')).join('')}`;bindRows(); }


function showStatusLabel(s) { return ({CONTINUING:'Continuing · En cours',UPCOMING:'Upcoming · À venir',IN_PRODUCTION:'In production · En production',PILOT:'Pilot · Pilote',ENDED:'Ended · Terminée',CANCELED:'Canceled · Annulée',UNKNOWN:'Statut inconnu'})[FGCore.normalizeStatus(s)]; }
function seasonStats(show,season){ const es=episodesFor(show.id).filter(e=>e.season===season);return {episodes:es,total:es.length,watched:es.filter(e=>e.watched).length,skipped:es.filter(e=>e.skipped).length,collected:es.filter(e=>e.collected).length,remaining:es.filter(e=>!e.watched&&!e.skipped).length}; }
function ratingCards(m){
  const all=[];
  if(m.rating!=null) all.push({label:'Ma note',score:m.rating,maxScore:10,votes:null});
  for(const r of m.ratings||[]) if(r&&r.score!=null) all.push(r);
  if(!all.some(r=>String(r.source||r.label).toLowerCase().includes('tmdb'))&&m.catalogRating!=null) all.push({label:'TMDb / catalogue',score:m.catalogRating,maxScore:10,votes:m.catalogRatingVotes});
  return all.length?`<div class="rating-cards">${all.map(r=>`<div class="rating-card"><b>${esc(r.label||r.source||'Note')}</b><strong>${Number(r.score).toFixed(1)}<small>/${r.maxScore||10}</small></strong>${r.votes?`<span>${Number(r.votes).toLocaleString('fr-FR')} votes</span>`:''}</div>`).join('')}</div>`:'';
}

function episodeHtml(m,e){ const hidden=state.settings.hideEpisodeSpoilers&&!e.watched;return `<article class="episode"><div class="episode-still">${!hidden&&e.stillUrl?`<img src="${esc(e.stillUrl)}" alt="" loading="lazy">`:`<span>${e.watched?'✓':'▶'}</span>`}</div><div class="episode-info"><h4>${epCode(e)} · ${hidden?'Épisode non vu':esc(e.title)}</h4><div class="meta">${esc(e.date||'Date inconnue')} · ${e.watched?'Vu':e.skipped?'Ignoré':'À voir'}${e.plays>1?` · ${e.plays}×`:''}${e.collected?' · Collection':''}${e.catalogRating!=null?` · ★ ${Number(e.catalogRating).toFixed(1)}`:''}</div>${!hidden&&e.overview?`<p>${esc(e.overview)}</p>`:''}</div><div class="episode-actions"><button class="tonal" aria-label="Marquer vu ou non vu" data-ep="watch" data-eid="${esc(e.id)}">${e.watched?'○':'✓'}</button><button class="tonal" aria-label="Ignorer ou rétablir cet épisode" data-ep="skip" data-eid="${esc(e.id)}">${e.skipped?'↩':'→'}</button><button class="tonal" aria-label="Ajouter ou retirer de la collection" data-ep="collect" data-eid="${esc(e.id)}">${e.collected?'▣':'□'}</button><button class="tonal" aria-label="Ajouter un revisionnage" data-ep="replay" data-eid="${esc(e.id)}">↻</button>${e.tmdbId?`<button class="tonal" data-trakt-checkin="episode" data-checkin-id="${esc(e.id)}">${esc(checkInControlsLabel("episode",e.id))}</button>`:""}<button class="tonal" data-viewing-date="${esc(e.id)}">Date du visionnage</button></div></article>`; }

function renderMediaDetail(o){
  const m=(o.src==='suggestions'?state.suggestions:state.media).find(x=>x.id===o.id);if(!m){overlay=null;return render();}
  playDetailJingle(m.id);
  const listHtml=state.lists.map(l=>`<label class="toggle">${esc(l.name)} <input data-list-toggle="${esc(l.id)}" type="checkbox" ${(m.listIds||[]).includes(l.id)?'checked':''}></label>`).join('');
  const meta=[kindName(m.kind),m.year,m.contentRating,m.kind==='show'?showStatusLabel(m.statusRaw||m.status):null,m.runtimeMinutes?`${m.runtimeMinutes} min`:null].filter(Boolean).map(esc).join(' · ');
  const seasons=m.kind==='show'?[...new Set([...(m.seasons||[]).map(s=>s.number),...state.episodes.filter(e=>e.showId===m.id).map(e=>e.season)])].filter(n=>state.settings.includeSpecials||n!==0).sort((a,b)=>a-b):[];
  const showProgress=m.kind==='show'?seasonStatsAll(m):null;
  const showComplete=m.kind!=='show'||(!!m.catalogComplete&&(!m.catalogExpectedEpisodes||state.episodes.filter(e=>e.showId===m.id&&!e.catalogOrphaned).length>=m.catalogExpectedEpisodes));
  const facts=[
    ['Statut',m.kind==='show'?showStatusLabel(m.statusRaw||m.status):null],['Genres',(m.genres||[]).join(' · ')],['Chaîne / réseau',m.network],['Créateur / auteur',m.creator],['Éditeur',m.publisher],['Pays',m.country],['Langue originale',m.originalLanguage],['Diffusion',m.releaseSchedule],['Saisons',m.seasonCount||seasons.filter(x=>x>0).length||null],['Épisodes',m.episodeCount||showProgress?.total||null],['Production',(m.productionCompanies||[]).join(' · ')],['Plateformes',(m.platforms||[]).join(' · ')]
  ].filter(x=>x[1]);
  $('#content').innerHTML=`<article class="media-detail">${m.backdropUrl?`<div class="backdrop"><img src="${esc(m.backdropUrl)}" alt=""><i></i></div>`:''}<section class="detail-hero">${posterHtml(m,'detail-poster')}<div class="detail-title"><span class="eyebrow">${esc(kindName(m.kind))}</span><h2>${esc(m.title)}</h2>${m.tagline?`<p class="tagline">${esc(m.tagline)}</p>`:''}<div class="meta">${meta}</div>${m.kind==='show'&&showProgress?`<div class="watch-progress"><b>${showProgress.watched}/${showProgress.total}</b> épisodes vus · <b>${showProgress.remaining}</b> restants<div class="progress"><i style="width:${pct(showProgress.watched+showProgress.skipped,showProgress.total)}%"></i></div><small class="catalog-state ${showComplete?'ok':'warn'}">${showComplete?'Données série complètes':'Données série incomplètes'}</small></div>`:''}</div></section><section class="actions detail-actions"><button class="tonal" data-detail="fav">${m.favorite?'★':'☆'} Favori</button>${m.kind!=='show'?`<button class="tonal" data-detail="done">${m.watched?'↻ Revoir':'✓ Terminer'}</button>`:''}${m.kind==='movie'&&m.tmdbId?`<button class="tonal" data-trakt-checkin="movie" data-checkin-id="${esc(m.id)}">${esc(checkInControlsLabel('movie',m.id))}</button>`:''}<button class="tonal" data-detail="collect">${m.collected?'▣':'□'} Collection</button><button class="tonal" data-detail="watchlist">${m.watchlist?'🔖':'◇'} À faire</button><button class="tonal" data-detail="hide">${m.hidden?'👁 Réafficher':'◌ Masquer'}</button>${m.suggestion?'<button class="primary" data-detail="add">+ Ajouter</button>':'<button class="tonal" data-detail="refresh">↻ Actualiser toutes les données</button>'}${m.trailerUrl?`<a class="tonal linkbtn" href="${esc(m.trailerUrl)}" target="_blank" rel="noopener">▶ Bande-annonce</a>`:''}${m.externalUrl?`<a class="tonal linkbtn" href="${esc(m.externalUrl)}" target="_blank" rel="noopener">↗ Source</a>`:''}</section><section class="detail-body">${m.overview?`<section class="section"><h3>Synopsis</h3><p>${esc(m.overview)}</p></section>`:''}<section class="section"><div class="section-title"><h3>Notes</h3><span>Personnel + catalogues</span></div>${ratingCards(m)||'<p class="meta">Aucune note catalogue disponible.</p>'}</section>${(m.providers||[]).length?`<section class="section"><h3>Ancien relevé de services</h3><p class="meta">Noms enregistrés avec la fiche ; pays et date de cette lecture non conservés. Ce n’est pas une vérification actuelle. Données JustWatch via TMDb.</p><div class="chips">${m.providers.map(p=>`<span>${esc(p)}</span>`).join('')}</div></section>`:''}${creditCards(m)}${['show','movie'].includes(m.kind)?`<section class="section"><div class="section-title"><h3>Avis & commentaires Trakt</h3><span>Spoilers masqués</span></div><div id="traktComments"><p class="meta">Chargement à la demande…</p></div></section>`:''}${m.kind==='show'&&seasons.length?`<section class="section"><div class="section-title"><h3>Saisons & épisodes</h3><span>${showProgress.watched}/${showProgress.total} vus</span></div>${seasons.map(s=>seasonHtml(m,s)).join('')}</section>`:''}<section class="section"><h3>Détails</h3><div class="facts">${facts.map(([k,v])=>`<div><b>${esc(k)}</b><span>${esc(v)}</span></div>`).join('')}</div></section>${['game','book'].includes(m.kind)?`<section class="section"><h3>Progression</h3><label>Avancement <input id="cur" type="number" min="0" value="${m.progressCurrent||0}"> / <input id="tot" type="number" min="0" value="${m.progressTotal||0}"> ${esc(m.progressUnit||'')} <button id="saveProg" class="tonal">Enregistrer</button></label></section>`:''}${['show','movie'].includes(m.kind)?`<section class="section"><h3>Langue de cette fiche</h3><p class="meta">Indépendante de la langue générale. Une actualisation récupère les titres, résumés, saisons et épisodes disponibles, sans modifier ton suivi.</p><label>Code langue (vide = réglage général)<input id="titleLanguage" value="${esc(m.contentLanguage||'')}" placeholder="fr-FR ou en-US" maxlength="5"></label><button class="tonal" id="saveTitleLanguage">Appliquer et actualiser</button></section>`:''}<section class="section"><h3>Ma fiche</h3><label>Ma note /10 <input id="rating" type="number" min="1" max="10" value="${esc(m.rating??'')}"></label><label>Note personnelle<textarea id="note" rows="3">${esc(m.userNote||'')}</textarea></label><button id="saveNote" class="tonal">Enregistrer</button>${listHtml?`<h4>Listes</h4>${listHtml}`:''}</section></section></article>`;
  bindDetail(m);
  if(['show','movie'].includes(m.kind)) loadTraktComments(m);
}
function seasonStatsAll(m){ const es=episodesFor(m.id).filter(e=>state.settings.includeSpecials||e.season!==0);return{total:es.length,watched:es.filter(e=>e.watched).length,skipped:es.filter(e=>e.skipped).length,collected:es.filter(e=>e.collected).length,remaining:es.filter(e=>!e.watched&&!e.skipped).length}; }
function bindDetail(m) {
  $('#saveTitleLanguage')?.addEventListener('click',async()=>{
    let language;try{language=FGCore.contentLanguage($('#titleLanguage').value);}catch(e){return toast(e.message);}
    await refreshCatalogItem({...m,contentLanguage:language});
  });
  $$('[data-detail]').forEach(b=>b.onclick=async()=>{
    const a=b.dataset.detail;
    if(a==='add'){await add(m.id);return;}
    if(a==='refresh'){await refreshCatalogItem(m);return;}
    if(m.suggestion)return toast('Ajoute ce titre à ta bibliothèque avant de modifier son suivi.');
    if(a==='fav')m.favorite=!m.favorite;
    if(a==='done'){Object.assign(m,FGProgress.finish(m,Date.now(),m.watched&&(!FGProgress.applicable(m)||FGProgress.status(m)==='completed')));}
    if(a==='collect')m.collected=!m.collected;
    if(a==='watchlist')m.watchlist=!m.watchlist;
    if(a==='hide')m.hidden=!m.hidden;
    save();render();
  });
  $$('[data-list-toggle]').forEach(c=>c.onchange=()=>{m.listIds=c.checked?[...new Set([...(m.listIds||[]),c.dataset.listToggle])]:(m.listIds||[]).filter(x=>x!==c.dataset.listToggle);save();});
  $$('[data-ep]').forEach(b=>{
    b.setAttribute('aria-label',({watch:'Basculer vu / non vu',skip:'Ignorer / réactiver',collect:'Ajouter / retirer de la collection',replay:'Enregistrer un visionnage'})[b.dataset.ep]||b.dataset.ep);
    b.onclick=()=>{
      const e=state.episodes.find(x=>x.id===b.dataset.eid);if(!e)return;
      if(b.dataset.ep==='skip'&&!e.skipped&&FGEpisodes.hasHistory(e)){toast('Historique protégé : utilise explicitement l’effacement des visionnages avant d’ignorer un épisode vu.');return;}
      if(b.dataset.ep==='watch'&&e.watched){try{previewEpisodeAction(m.id,[e.id],'unwatch');}catch(error){toast(error.message);}return;}
      Object.assign(e,FGCore.episodeState(e,b.dataset.ep));m.lastWatchedAt=episodesFor(m.id).reduce((last,e)=>Math.max(last,e.lastWatchedAt||0),0)||null;
      const y=window.scrollY;save();render();window.scrollTo(0,y);
    };
  });
  $$('[data-season]').forEach(b=>b.onclick=()=>{try{const season=Number(b.dataset.s),ids=state.episodes.filter(e=>e.showId===m.id&&e.season===season).map(e=>e.id);previewEpisodeAction(m.id,ids,b.dataset.season,false);}catch(e){toast(e.message);}});
  if($('#saveProg'))$('#saveProg').onclick=()=>{try{Object.assign(m,FGProgress.update(m,{current:$('#cur').value,total:$('#tot').value}));save();render();}catch(e){toast(e.message);}};
  $('#saveNote')?.addEventListener('click',()=>{
    if(m.suggestion)return toast('Ajoute ce titre avant de noter.');
    m.rating=$('#rating').value.trim()===''?null:FGCore.integer($('#rating').value,1,10);m.userNote=$('#note').value.trim();save();render();toast('Note enregistrée');
  });
}
function lastActivity(m){return episodesFor(m.id).reduce((last,e)=>Math.max(last,e.lastWatchedAt||0),m.lastWatchedAt||0);}

function computeStats(){
  const media=state.media.filter(m=>m.added),ids=new Set(media.map(m=>m.id)),eps=state.episodes.filter(e=>ids.has(e.showId)&&(state.settings.includeSpecials||e.season!==0));
  const watchedE=eps.filter(e=>e.watched&&!e.skipped), movies=media.filter(m=>m.kind==='movie'), games=media.filter(m=>m.kind==='game'), books=media.filter(m=>m.kind==='book'), shows=media.filter(m=>m.kind==='show');
  const uniqueMinutes=watchedE.reduce((n,e)=>n+(e.runtimeMinutes||45),0)+movies.filter(m=>m.watched).reduce((n,m)=>n+(m.runtimeMinutes||0),0);
  const allMinutes=watchedE.reduce((n,e)=>n+(e.runtimeMinutes||45)*Math.max(1,e.plays||1),0)+movies.filter(m=>m.watched).reduce((n,m)=>n+(m.runtimeMinutes||0)*Math.max(1,m.plays||1),0);
  const genres=Object.create(null);media.forEach(m=>(m.genres||[]).forEach(g=>genres[g]=(genres[g]||0)+1));
  const fr=Object.create(null);media.forEach(m=>{if(m.franchiseName)fr[m.franchiseName]=(fr[m.franchiseName]||0)+1;});
  const activity=FGCore.timeline(media,eps).entries.map(e=>FGCore.localDate(e.at));
  const byDay=Object.create(null);activity.forEach(d=>byDay[d]=(byDay[d]||0)+1);
  return {media,eps,watchedE,movies,games,books,shows,uniqueMinutes,allMinutes,rewatches:watchedE.reduce((n,e)=>n+Math.max(0,(e.plays||1)-1),0)+movies.reduce((n,m)=>n+Math.max(0,(m.plays||0)-1),0),genres:Object.entries(genres).sort((a,b)=>b[1]-a[1]).slice(0,8),fr:Object.entries(fr).sort((a,b)=>b[1]-a[1]).slice(0,8),byDay};
}
function formatMinutes(m){const d=Math.floor(m/1440),h=Math.floor((m%1440)/60);return d?`${d}j ${h}h`:`${Math.floor(m/60)}h ${m%60}m`;}
function renderStatistics(){
  const s=computeStats(),minutes=state.settings.statisticsIncludeRewatches?s.allMinutes:s.uniqueMinutes;
  const activeShows=s.shows.filter(m=>['CONTINUING','RETURNING_SERIES','IN_PRODUCTION','UPCOMING','PILOT'].includes(String(m.status||m.statusRaw||'').toUpperCase().replace(/ /g,'_'))).length;
  const collectedEpisodes=s.eps.filter(e=>e.collected).length;
  const stats=[['Médias suivis',s.media.length],['Épisodes vus',`${s.watchedE.length}/${s.eps.length}`],['Temps vidéo',formatMinutes(minutes)],['Films vus',`${s.movies.filter(m=>m.watched).length}/${s.movies.length}`],['Séries à jour',`${s.shows.filter(m=>seasonStatsAll(m).remaining===0&&seasonStatsAll(m).total>0).length}/${s.shows.length}`],['Séries actives',activeShows],['Revisionnages',s.rewatches],['Jeux terminés',`${s.games.filter(m=>m.watched).length}/${s.games.length}`],['Livres lus',`${s.books.filter(m=>m.watched).length}/${s.books.length}`],['Favoris',s.media.filter(m=>m.favorite).length],['Collection',s.media.filter(m=>m.collected).length+collectedEpisodes],['À faire',s.media.filter(m=>m.watchlist).length],['Pages suivies',s.books.reduce((n,m)=>n+(m.progressCurrent||0),0)],['Activité consécutive',FGCore.streaks(Object.keys(s.byDay)).current+' jour(s)'],['Record d’activité',FGCore.streaks(Object.keys(s.byDay)).longest+' jour(s)']];
  const days=[...Array(30)].map((_,i)=>dateOffset(i-29));
  $('#content').innerHTML=`<section class="hero"><h2>Statistiques détaillées</h2><p>Progression locale, temps, revisionnages, genres et activité.</p></section><section class="stats stats-large">${stats.map(([l,v])=>`<div class="stat"><b>${esc(v)}</b>${esc(l)}</div>`).join('')}</section><section class="section panel"><h3>Activité · 30 jours</h3><div class="activity-chart">${days.map(d=>`<i title="${d}: ${s.byDay[d]||0}" style="height:${8+Math.min(8,s.byDay[d]||0)*7}px"></i>`).join('')}</div></section>${ranking('Genres les plus suivis',s.genres)}${ranking('Franchises les plus remplies',s.fr)}<section class="section panel"><p class="meta">${FGCore.timeline(s.media,s.eps).undatedPlays} lecture(s) sans date précise ne figurent pas dans le graphique.</p><p class="meta">${state.settings.statisticsIncludeRewatches?'Le temps inclut les revisionnages.':'Le temps compte chaque film/épisode une fois.'}</p></section>`;
}
function ranking(title,rows){ return rows.length?`<section class="section panel"><h3>${esc(title)}</h3>${rows.map(([n,v],i)=>`<div class="rank"><b>${i+1}</b><span>${esc(n)}</span><strong>${v}</strong></div>`).join('')}</section>`:''; }

function renderSubset(mode){const a=state.media.filter(m=>m.added&&({hidden:m.hidden,collection:m.collected,watchlist:m.watchlist,favorites:m.favorite}[mode]));$('#content').innerHTML=a.map(m=>row(m)).join('')||empty('Aucun élément','Cette vue se remplira au fil de tes actions.');bindRows();}


function bindRows() {
  $$('[data-open]').forEach(r=>{
    r.tabIndex=-1;r.setAttribute('role','group');
    const open=()=>{
      const src=r.dataset.src||'media',m=(src==='suggestions'?state.suggestions:src==='remote'?remoteResults:state.media).find(x=>x.id===r.dataset.open);
      if(!m)return;
      if(src==='remote') { const neo={...clone(m),suggestion:true,added:false};assignFranchise(neo); if(!state.suggestions.some(x=>FGCore.sameMedia(x,neo)))state.suggestions.push(neo); }
      $('#searchDialog').close();
      const returnTo=['person','franchise'].includes(overlay?.type)?overlay:null;
      overlay={type:'item',title:m.title,id:m.id,src:src==='remote'?'suggestions':src,returnTo};render();window.scrollTo(0,0);
    };
    let titleButton=r.querySelector('.open-item');
    if(!titleButton){const heading=r.querySelector('h3,b');if(heading){titleButton=document.createElement('button');titleButton.className='open-item';titleButton.type='button';titleButton.textContent=heading.textContent;heading.replaceChildren(titleButton);}}
    if(titleButton)titleButton.onclick=e=>{e.stopPropagation();open();};
    r.onclick=e=>{if(!e.target.closest('button,a,input,select,textarea'))open();};
    r.onkeydown=e=>{if(e.target===r&&['Enter',' '].includes(e.key)){e.preventDefault();open();}};
  });
  $$('[data-act]').forEach(b=>b.onclick=async e=>{
    e.stopPropagation();if(b.disabled)return;
    if(b.dataset.act==='add'){b.disabled=true;await add(b.dataset.id);b.disabled=false;return;}
    const m=state.media.find(x=>x.id===b.dataset.id);if(!m)return;
    if(m.kind==='show') {
      const next=FGLibrary.nextUp([m],state.episodes,{today:today(),includeSpecials:!!state.settings.includeSpecials}).records[0];
      if(!next||next.status!=='ready'||!next.episodeId)return toast('Le prochain épisode est futur ou à compléter. Aucun épisode antérieur sauté : ouvre la fiche.');
      const e=state.episodes.find(e=>e.id===next.episodeId&&e.showId===m.id);if(!e||e.watched||e.skipped)return;
      Object.assign(e,FGCore.episodeState(e,'watched'));m.lastWatchedAt=Math.max(m.lastWatchedAt||0,e.lastWatchedAt||0);
    } else { Object.assign(m,FGProgress.finish(m,Date.now(),m.watched&&(!FGProgress.applicable(m)||FGProgress.status(m)==='completed'))); }
    save();render();
  });
}

async function fetchJson(url,opts={}) { return FGRequests.json(url,opts); }
function tmdbSearchItem(x){const k=x.media_type==='tv'?'show':'movie';return{id:`tmdb-${k}-${x.id}`,kind:k,title:k==='show'?x.name:x.title,year:Number(String(k==='show'?x.first_air_date:x.release_date||'').slice(0,4))||null,releaseDate:k==='show'?x.first_air_date:x.release_date||null,overview:x.overview||'',posterUrl:tmdbImage(x.poster_path),backdropUrl:tmdbImage(x.backdrop_path,'w1280'),catalogRating:x.vote_average??null,catalogRatingVotes:x.vote_count??null,ratings:x.vote_average!=null?[{source:'TMDb',label:'TMDb',score:x.vote_average,maxScore:10,votes:x.vote_count||null,externalUrl:`https://www.themoviedb.org/${k==='show'?'tv':'movie'}/${x.id}`}]:[],tmdbId:x.id,source:'tmdb',sourceId:String(x.id),added:false,listIds:[]};}
function rawgSearchItem(x){return{id:`rawg-game-${x.id}`,kind:'game',title:x.name,year:Number(String(x.released||'').slice(0,4))||null,releaseDate:x.released||null,posterUrl:x.background_image||null,catalogRating:x.rating?x.rating*2:null,ratings:x.rating?[{source:'RAWG',label:'RAWG',score:x.rating,maxScore:5,votes:x.ratings_count||null,externalUrl:`https://rawg.io/games/${x.slug}`}]:[],source:'rawg',sourceId:String(x.id),added:false,listIds:[]};}
function openLibrarySearchItem(x){return FGBookEditions.searchItem(x);}
function searchProviderIds(kind=kindFilter){
 const ids=[];if(['all','show','movie'].includes(kind)){if(serviceCredential('tmdb'))ids.push('tmdb');else ids.push(...(kind==='all'?['tvmaze','itunes']:[kind==='show'?'tvmaze':'itunes']));}
 if(['all','game'].includes(kind))ids.push(serviceCredential('rawg')?'rawg':'freetogame');if(['all','book'].includes(kind))ids.push('openlibrary');return ids;
}
async function remoteSearch(q,kind=kindFilter,page=1,withMeta=false,options={}) {
 await ensureAutomaticServices();if(options.signal?.aborted)throw Object.assign(Error('Recherche annulée.'),{name:'AbortError'});
 const allowed=searchProviderIds(kind),requested=options.requests||allowed.map(id=>({id,page})),lang=state.settings.language||'fr-FR';
 const opts={signal:options.signal,maxBytes:4*1024*1024,retries:0,...(options.force?{force:true,cacheMs:0}:{})};
 const outcomes=await Promise.all(requested.map(async request=>{
  const id=request.id,p=request.page;try{
   if(!allowed.includes(id))throw Error('Configuration catalogue modifiée. Relance la recherche.');
   let payload,rows,map=x=>x;
   if(id==='tmdb'){const type=kind==='all'?'multi':kind==='show'?'tv':'movie';payload=await fetchJson(`https://api.themoviedb.org/3/search/${type}?api_key=${encodeURIComponent(serviceCredential('tmdb'))}&language=${encodeURIComponent(lang)}&query=${encodeURIComponent(q)}&page=${p}&include_adult=false`,opts);map=x=>tmdbSearchItem(type==='multi'?x:{...x,media_type:type});rows=FGReliability.response(id,payload,p);rows.rows=rows.rows.filter(x=>type!=='multi'||['tv','movie'].includes(x?.media_type));}
   else if(id==='rawg'){payload=await fetchJson(`https://api.rawg.io/api/games?key=${encodeURIComponent(serviceCredential('rawg'))}&search=${encodeURIComponent(q)}&page_size=20&page=${p}`,opts);rows=FGReliability.response(id,payload,p);map=rawgSearchItem;}
   else if(id==='openlibrary'){payload=await readOpenLibrary(`https://openlibrary.org/search.json?q=${encodeURIComponent(q)}&limit=20&page=${p}&fields=key,title,author_name,first_publish_year,cover_i,number_of_pages_median`,opts);rows=FGReliability.response(id,payload,p);map=openLibrarySearchItem;}
   else {payload=await publicCatalogSearch(q,{tvmaze:'show',itunes:'movie',freetogame:'game'}[id],p,opts);rows=FGReliability.response(id,payload,p);}
   return {id,page:p,items:rows.rows.map(map).map(FGReliability.validateItem),more:rows.more,error:null};
  }catch(e){if(options.signal?.aborted)throw Object.assign(Error('Recherche annulée.'),{name:'AbortError'});return {id,page:p,items:[],more:false,error:id+' : '+e.message,status:e.status,retryAfter:e.retryAfter};}
 }));
 const result={items:outcomes.flatMap(x=>x.items),more:outcomes.some(x=>x.more),errors:outcomes.filter(x=>x.error).map(x=>x.error),successes:outcomes.filter(x=>!x.error).length,complete:outcomes.every(x=>!x.error),sources:outcomes};
 return withMeta?result:result.items;
}

async function enrichTrakt(m){
  if(!serviceCredential('trakt')||!m.tmdbId||!['show','movie'].includes(m.kind))return;
  try{const type=m.kind==='show'?'show':'movie';const arr=await fetchJson(`https://api.trakt.tv/search/tmdb/${m.tmdbId}?type=${type}&extended=full`,{headers:{'trakt-api-version':'2','trakt-api-key':serviceCredential('trakt')}});const x=arr?.[0]?.[type];if(!x)return;m.traktId=x.ids?.trakt||m.traktId;m.statusRaw=x.status||m.statusRaw;m.network=x.network||m.network;m.contentRating=x.certification||m.contentRating;m.runtimeMinutes=x.runtime||m.runtimeMinutes;m.genres=[...new Set([...(m.genres||[]),...(x.genres||[])])];if(x.rating!=null){m.ratings=(m.ratings||[]).filter(r=>String(r.source).toLowerCase()!=='trakt');m.ratings.push({source:'Trakt',label:'Trakt',score:x.rating,maxScore:10,votes:x.votes||null,externalUrl:`https://trakt.tv/search/tmdb/${m.tmdbId}?id_type=${type}`});}if(x.airs)m.releaseSchedule=[x.airs.day,x.airs.time,x.airs.timezone].filter(Boolean).join(' · ');}catch{}
}

async function enrichCatalog(m,{signal}={}){
  await ensureAutomaticServices();if(FGPublicCatalog.SOURCES.has(m.source))return publicCatalogBundle(m,{signal});
  const catalogFetch=(url,opts={})=>fetchJson(url,{...opts,...(signal?{signal}:{})});
  if(signal?.aborted)throw Error("Lecture annulée.");
  const lang=FGCore.contentLanguage(m.contentLanguage)||state.settings.language||'fr-FR',region=state.settings.region||'FR';
  if(!m.tmdbId&&['show','movie'].includes(m.kind)&&(m.imdbId||m.tvdbId)&&serviceCredential('tmdb')){
    const external=m.imdbId||m.tvdbId,source=m.imdbId?'imdb_id':'tvdb_id';
    const found=await catalogFetch(`https://api.themoviedb.org/3/find/${encodeURIComponent(external)}?api_key=${encodeURIComponent(serviceCredential('tmdb'))}&external_source=${source}`);
    const matches=found[m.kind==='show'?'tv_results':'movie_results']||[];
    if(matches.length!==1)throw Error('Correspondance TMDb absente ou ambiguë. Aucune fiche remplacée.');
    m.tmdbId=matches[0]?.id;
    if(!m.tmdbId)throw Error('Aucune correspondance TMDb pour cet ancien identifiant. Les données importées restent conservées.');
  }
  if(m.tmdbId&&['show','movie'].includes(m.kind))m.source='tmdb';
  if(m.source==='tmdb'&&!serviceCredential('tmdb'))throw Error('La clé TMDb est requise pour charger toutes les saisons et les affiches. Ouvre Réglages.');
  if(m.source==='rawg'&&!serviceCredential('rawg'))throw Error('La clé RAWG est requise pour charger la fiche du jeu.');
  if(m.source==='tmdb'&&m.tmdbId&&serviceCredential('tmdb')){
    const key=encodeURIComponent(serviceCredential('tmdb')),type=m.kind==='show'?'tv':'movie',id=m.tmdbId;
    const append=m.kind==='show'?'external_ids,content_ratings,aggregate_credits,videos':'external_ids,release_dates,credits,videos';
    const d=await catalogFetch(`https://api.themoviedb.org/3/${type}/${id}?api_key=${key}&language=${encodeURIComponent(lang)}&append_to_response=${append}`);
    m.title=(m.kind==='show'?d.name:d.title)||m.title;m.originalTitle=(m.kind==='show'?d.original_name:d.original_title)||m.originalTitle;m.overview=d.overview||m.overview;m.tagline=d.tagline||m.tagline;m.homepage=d.homepage||m.homepage;m.releaseDate=(m.kind==='show'?d.first_air_date:d.release_date)||m.releaseDate;m.year=Number(String(m.releaseDate||'').slice(0,4))||m.year;m.lastAirDate=m.kind==='show'?d.last_air_date||null:null;m.posterUrl=tmdbImage(d.poster_path)||m.posterUrl;m.backdropUrl=tmdbImage(d.backdrop_path,'w1280')||m.backdropUrl;m.genres=(d.genres||[]).map(x=>x.name).filter(Boolean);m.catalogRating=d.vote_average??null;m.catalogRatingVotes=d.vote_count||null;m.ratings=(m.ratings||[]).filter(r=>String(r.source).toLowerCase()!=='tmdb');if(d.vote_average!=null)m.ratings.unshift({source:'TMDb',label:'TMDb',score:d.vote_average,maxScore:10,votes:d.vote_count||null,externalUrl:`https://www.themoviedb.org/${type}/${id}`});m.imdbId=d.external_ids?.imdb_id||d.imdb_id||m.imdbId;m.runtimeMinutes=m.kind==='show'?((d.episode_run_time||[])[0]||null):(d.runtime||null);m.externalUrl=`https://www.themoviedb.org/${type}/${id}`;m.originalLanguage=d.original_language||m.originalLanguage;m.country=(d.origin_country||d.production_countries||[]).map(x=>typeof x==='string'?x:x.iso_3166_1).filter(Boolean).join(', ');m.productionCompanies=(d.production_companies||[]).map(x=>x.name).filter(Boolean);m.seasonCount=d.number_of_seasons||m.seasonCount;m.episodeCount=d.number_of_episodes||m.episodeCount;
    const cr=m.kind==='show'?(d.content_ratings?.results||[]).find(x=>x.iso_3166_1===region)?.rating:(d.release_dates?.results||[]).find(x=>x.iso_3166_1===region)?.release_dates?.find(x=>x.certification)?.certification;m.contentRating=cr||m.contentRating;
    if(m.kind==='show'){m.network=(d.networks||[]).map(x=>x.name).join(' / ');m.creator=(d.created_by||[]).map(x=>x.name).join(', ')||m.creator;const ac=d.aggregate_credits||{};m.credits=[...(ac.cast||[]).map(x=>({id:String(x.id),name:x.name,role:(x.roles||[]).map(r=>r.character).filter(Boolean).slice(0,2).join(' / '),department:'Cast',profileUrl:tmdbImage(x.profile_path,'w185')})),...(ac.crew||[]).filter(x=>['Directing','Production','Writing'].includes(x.department)).slice(0,12).map(x=>({id:String(x.id),name:x.name,role:(x.jobs||[]).map(j=>j.job).filter(Boolean).slice(0,2).join(' / '),department:x.department,profileUrl:tmdbImage(x.profile_path,'w185')}))];m.statusRaw=d.status||m.statusRaw;m.status=String(d.status||'UNKNOWN').toUpperCase().replace(/ /g,'_');}
    else{const c=d.credits||{};m.credits=[...(c.cast||[]).map(x=>({id:String(x.id),name:x.name,role:x.character||'',department:'Cast',profileUrl:tmdbImage(x.profile_path,'w185')})),...(c.crew||[]).filter(x=>['Director','Writer','Producer','Executive Producer'].includes(x.job)).slice(0,12).map(x=>({id:String(x.id),name:x.name,role:x.job||'',department:x.department||'Crew',profileUrl:tmdbImage(x.profile_path,'w185')}))];}
    m.people=[...new Set((m.credits||[]).map(x=>x.name))];const vids=d.videos?.results||[],v=vids.find(x=>x.site==='YouTube'&&x.type==='Trailer'&&x.official)||vids.find(x=>x.site==='YouTube'&&x.type==='Trailer');if(v)m.trailerUrl=`https://www.youtube.com/watch?v=${v.key}`;
    try{const wp=await catalogFetch(`https://api.themoviedb.org/3/${type}/${id}/watch/providers?api_key=${key}`),node=wp.results?.[region.toUpperCase()]||{};m.providers=[...new Set(['flatrate','free','ads','rent','buy'].flatMap(k=>node[k]||[]).map(x=>x.provider_name).filter(Boolean))];}catch{}
    if(m.kind==='show'){
      const pending=[], expected=[], seasonMetadata=[];
      if(!Array.isArray(d.seasons))throw Error('Liste des saisons absente : ajout annulé.');
      const summaries=d.seasons.filter(se=>Number.isInteger(se.season_number)&&se.season_number>=0);
      const details=await FGNetwork.mapLimit(summaries,3,se=>catalogFetch(`https://api.themoviedb.org/3/tv/${id}/season/${se.season_number}?api_key=${key}&language=${encodeURIComponent(lang)}&append_to_response=watch/providers`));
      for(const [index,se] of summaries.entries()){
        const n=se.season_number;
        const sd=details[index],sp=tmdbImage(sd.poster_path||se.poster_path),eps=FGCore.validateSeason(se,sd);
        const want=Number(se.episode_count||eps.length||0);if(want>0&&eps.length<want)throw Error(`Saison ${n} incomplète : ${eps.length}/${want} épisodes récupérés`);expected.push(want);seasonMetadata.push({number:n,tmdbId:sd.id||se.id,title:sd.name||se.name,overview:sd.overview||se.overview||'',posterUrl:sp,episodeCount:want,airDate:sd.air_date||se.air_date||null,providers:providerNames(sd['watch/providers']?.results?.[region.toUpperCase()])});
        for(const e of eps)pending.push({id:`tmdb-episode-${e.id}`,showId:m.id,season:n,num:e.episode_number,title:e.name||`Épisode ${e.episode_number}`,date:e.air_date||null,watched:false,plays:0,skipped:false,collected:false,overview:e.overview||'',stillUrl:tmdbImage(e.still_path),seasonPosterUrl:sp,seasonTmdbId:sd.id||se.id,seasonProviders:seasonMetadata[seasonMetadata.length-1].providers,tmdbId:e.id,runtimeMinutes:e.runtime||m.runtimeMinutes||45,catalogRating:e.vote_average??null,catalogRatingVotes:e.vote_count||null});
      }
      const regular=pending.filter(e=>e.season>0).length;
      if(Number(d.number_of_episodes||0)>regular)throw Error(`Série incomplète : ${regular}/${d.number_of_episodes} épisodes réguliers récupérés.`);
      m._pendingEpisodes=pending;m.seasons=seasonMetadata;m.catalogExpectedEpisodes=pending.length;m.catalogComplete=true;m.catalogUpdatedAt=Date.now();
    }
    if(m.kind==='movie'&&!m.franchiseManual&&d.belongs_to_collection?.id)try{
      const c=await catalogFetch(`https://api.themoviedb.org/3/collection/${d.belongs_to_collection.id}?api_key=${key}&language=${encodeURIComponent(lang)}`);
      if(!Array.isArray(c.parts))throw Error('Collection partielle.');
      const definitions=FGFranchises.definitions(state),fid=m.franchise||m.franchiseId||FGFranchises.effective(m,definitions)||`tmdb-collection-${d.belongs_to_collection.id}`;
      const existingDefinition=definitions.find(x=>x.id===fid),fname=existingDefinition?.name||c.name||d.belongs_to_collection.name;
      m.franchise=fid;m.franchiseId=fid;m.franchiseName=fname;
      if(!existingDefinition)m._pendingFranchise={id:fid,name:fname};m._pendingSuggestions=[];
      for(const x of c.parts){if(x.id===id)continue;if(!Number.isInteger(x.id)||x.id<=0||typeof x.title!=='string')continue;
       m._pendingSuggestions.push({id:`tmdb-movie-${x.id}`,kind:'movie',title:x.title,year:Number(String(x.release_date||'').slice(0,4))||null,releaseDate:x.release_date||null,overview:x.overview||'',posterUrl:tmdbImage(x.poster_path),backdropUrl:tmdbImage(x.backdrop_path,'w1280'),added:false,suggestion:true,franchise:fid,franchiseId:fid,franchiseName:fname,tmdbId:x.id,source:'tmdb',sourceId:String(x.id),ratings:x.vote_average?[{source:'TMDb',label:'TMDb',score:x.vote_average,maxScore:10,votes:x.vote_count||null}]:[]});
      }
    }catch{}

    await enrichTrakt(m);
  }
  if(m.source==='rawg'&&m.sourceId&&serviceCredential('rawg')){const x=await catalogFetch(`https://api.rawg.io/api/games/${encodeURIComponent(m.sourceId)}?key=${encodeURIComponent(serviceCredential('rawg'))}`);m.title=x.name||m.title;m.overview=x.description_raw||m.overview;m.releaseDate=x.released||m.releaseDate;m.year=Number(String(x.released||'').slice(0,4))||m.year;m.posterUrl=x.background_image||m.posterUrl;m.backdropUrl=x.background_image_additional||m.backdropUrl;m.catalogRating=x.rating?x.rating*2:null;m.ratings=x.rating?[{source:'RAWG',label:'RAWG',score:x.rating,maxScore:5,votes:x.ratings_count||null,externalUrl:x.website||`https://rawg.io/games/${x.slug}`}]:[];m.genres=(x.genres||[]).map(v=>v.name);m.platforms=(x.platforms||[]).map(v=>v.platform?.name).filter(Boolean);m.creator=(x.developers||[]).map(v=>v.name).join(', ')||m.creator;m.publisher=(x.publishers||[]).map(v=>v.name).join(', ')||m.publisher;m.externalUrl=x.website||`https://rawg.io/games/${x.slug}`;m.catalogPlaytimeHours=x.playtime||null;if(!m.progressUnit&&!m.progressTotal&&!m.progressCurrent){m.progressTotal=100;m.progressUnit='%';}}
  if(m.source==='openlibrary'&&m.sourceId){const key=FGBookEditions.key(m.sourceId),x=await readOpenLibrary(`https://openlibrary.org${key}.json`,{signal,cacheMs:0}),desc=typeof x.description==='string'?x.description:x.description?.value;if(FGBookEditions.key(x.key)!==key)throw Error('Réponse Open Library pour une autre œuvre.');if(x.title!=null){const t=FGBookEditions.text(x.title,'Titre d’œuvre',1000);if(!t)throw Error('Titre d’œuvre vide.');m.title=t;}m.overview=FGBookEditions.text(desc,'Description',20000)||m.overview;m.sourceId=key;m.externalUrl=`https://openlibrary.org${key}`;if(x.subjects!=null){if(!Array.isArray(x.subjects))throw Error('Sujets Open Library invalides.');m.genres=x.subjects.slice(0,20).map(x=>FGBookEditions.text(x,'Sujet',1000));}const cover=FGBookEditions.cover(x.covers);if(cover)m.posterUrl=cover;}
  if(['tmdb','rawg','openlibrary'].includes(m.source)){m.catalogComplete=true;m.catalogUpdatedAt=Date.now();}
  if(signal?.aborted)throw Error("Lecture annulée.");
  return m;
}
async function add(id) {
  const source=state.suggestions.find(x=>x.id===id)||remoteResults.find(x=>x.id===id);
  if(!source||catalogBusy.has(identity(source)))return;
  const episodeOrPersonReturn=overlay?.type==='person'?overlay:overlay?.returnTo?.type==='person'?overlay.returnTo:null;
  const epoch=libraryEpoch,key=identity(source);catalogBusy.add(key);toast(`Chargement de la fiche complète : ${source.title}…`);
  try {
    const existing=state.media.find(x=>FGCore.sameMedia(x,source));
    const wasAddedAtStart=!!existing?.added;
    if(existing&&existing.source!==source.source){overlay={type:'item',title:existing.title,id:existing.id,src:'media'};$('#searchDialog').close();render();toast('Titre déjà enregistré auprès d’une autre source ; aucune migration automatique.');return;}
    const m=await enrichCatalog({...clone(source),id:existing?.id||source.id});
    if(epoch!==libraryEpoch)throw Error('La bibliothèque a été remplacée pendant le chargement. Aucun ajout tardif appliqué.');
    if(existing&&wasAddedAtStart&&!state.media.some(x=>x.id===existing.id&&x.added))throw Error('L’élément a été retiré pendant le chargement.');
    commitCatalog(m,existing);m.added=true;m.suggestion=false;m.addedAt=existing?.addedAt||Date.now();
    if(!existing)state.media.push(m);else Object.assign(existing,{...m,...trackingFields(existing),added:true,suggestion:false});
    assignFranchise(existing||m);
    state.suggestions=state.suggestions.filter(x=>!FGCore.sameMedia(x,m));remoteResults=remoteResults.filter(x=>!FGCore.sameMedia(x,m));
    save();$('#searchDialog').close();searchGeneration++;
    overlay={type:'item',title:(existing||m).title,id:(existing||m).id,src:'media',returnTo:episodeOrPersonReturn};render();window.scrollTo(0,0);
    toast(`${m.title} ajouté : ${m.kind==='show'?m.catalogExpectedEpisodes+' épisodes chargés':'fiche disponible'}`);
    diagnostic('info','catalog','Ajout complet : '+m.kind);
  } catch(e) {toast('Ajout annulé : '+e.message);diagnostic('error','catalog',e.message);}
  finally {catalogBusy.delete(key);}
}
async function refreshCatalogItem(m) {
  if(m.source==='retro'){overlay={type:'gaming',title:'Trophées & comptes jeux'};screen='games';render();toast('Utilise la récupération RetroAchievements du profil associé.');return;}
  if(m.source==='steam'){
    overlay={type:'gaming',title:'Trophées & comptes jeux'};screen='games';render();
    if(steamConnection.authenticated)steamImportDialog();else toast('Connecte ton compte Steam pour actualiser ses jeux et succès.');return;
  }
  FGRequests.clear();
  if(catalogBusy.has(identity(m)))return;
  catalogBusy.add(identity(m));toast(`Actualisation complète : ${m.title}…`);
  try {
    const epoch=libraryEpoch,fresh=await enrichCatalog(clone(m));
    const target=state.media.find(x=>x.id===m.id&&x.added);if(!target||epoch!==libraryEpoch)return;
    commitCatalog(fresh,target);const tracking=trackingFields(target);Object.assign(target,fresh,tracking);assignFranchise(target);
    save(false);render();toast('Fiche actualisée sans modifier ton suivi.');
  }catch(e){toast('Actualisation annulée : '+e.message);diagnostic('error','catalog',e.message);}
  finally{catalogBusy.delete(identity(m));}
}
function scheduleCatalogRepair(){setTimeout(()=>repairIncompleteCatalog().catch(()=>{}),1300);}








let installPrompt=null;
window.addEventListener('beforeinstallprompt',e=>{e.preventDefault();installPrompt=e;$('#installPwa')?.classList.remove('hidden');});
async function installPwa(){if(installPrompt){installPrompt.prompt();await installPrompt.userChoice;installPrompt=null;$('#installPwa')?.classList.add('hidden');}else toast('Utilise le menu du navigateur pour installer FranchiseGuide');}
function toast(t){const x=$('#toast');x.textContent=t;x.classList.add('show');clearTimeout(toast.t);toast.t=setTimeout(()=>x.classList.remove('show'),3000);}

const railItems=[['shows','▣','Séries'],['movies','▤','Films'],['games','🎮','Jeux'],['books','▥','Livres'],['franchises','⌘','Franchises'],['calendar','▦','Agenda'],['more','•••','Plus']];
$('#railNav').innerHTML=railItems.map(([s,i,l])=>`<button data-rail="${s}"><span>${i}</span>${l}</button>`).join('');
$$('[data-rail]').forEach(b=>b.onclick=()=>{screen=b.dataset.rail;tab=0;overlay=null;detailJingleFor=null;render();});
$$('#nav button').forEach(b=>b.onclick=()=>{screen=b.dataset.screen;tab=0;overlay=null;detailJingleFor=null;render();});
$('#back').onclick=()=>{overlay=overlay?.returnTo||null;detailJingleFor=null;render();};
$('#search').onclick=()=>openScopedSearch(({shows:'show',movies:'movie',games:'game',books:'book'})[screen]||'all');
$('#settings').onclick=()=>{fillExtraSettings();$('#settingsDialog').showModal();};
$('#installPwa').onclick=installPwa;
$$('.close').forEach(b=>b.onclick=()=>b.closest('dialog').close());

let searchSession=null,searchController=null,searchLocalLimit=60;
function searchSettingsKey(){return JSON.stringify([state.settings.language,state.settings.region,serviceCredential('tmdb'),serviceCredential('rawg')]);}
function invalidateSearch(){searchGeneration++;searchController?.abort();searchController=null;clearTimeout(searchTimer);}
function cancelSearch(){invalidateSearch();if(searchSession){searchSession.loading=false;searchSession.cancelled=true;drawSearch();}}
function drawSearch(){
 const session=searchSession;if(!session||!$('#searchDialog').open)return;
 const q=FGReliability.fold(session.query),local=[...state.media.filter(m=>m.added),...state.suggestions].filter(m=>(session.kind==='all'||m.kind===session.kind)&&(!q||FGReliability.fold(m.title).includes(q)));
 const existing=[...state.media.filter(m=>m.added),...state.suggestions];const aliases=new Set(existing.flatMap(FGCore.aliases));
 const remote=remoteResults.filter(m=>!FGCore.aliases(m).some(a=>aliases.has(a)));
 const errors=Object.entries(session.pages||{}).filter(([,x])=>x.error);searchErrors=errors.map(([,x])=>x.error);searchHasMore=FGReliability.requests(session.pages||{}).length>0;
 $('#results').innerHTML=(local.length?`<h3>Ta bibliothèque · ${local.length}</h3>`+local.slice(0,searchLocalLimit).map(m=>row(m,m.suggestion?'suggestions':'media')).join('')+(local.length>searchLocalLimit?'<button id="searchLocalMore" class="tonal">Afficher plus de titres locaux</button>':''):'')+
  (remote.length?`<h3>Catalogue · ${remote.length} résultats reçus</h3>`+remote.map(m=>row({...m,suggestion:true},'remote')).join(''):'')+
  (session.loading?'<p role="status" class="notice">Recherche dans les catalogues… <button id="searchCancel" class="tonal">Annuler la recherche</button></p>':'')+
  (session.cancelled?'<p role="status" class="notice">Recherche annulée. Les résultats déjà reçus sont conservés. <button id="searchResume" class="tonal">Reprendre</button></p>':'')+
  (errors.length?`<div role="status" class="notice warn">${errors.map(([id,x])=>esc(x.error)+(x.retryAt>Date.now()?' · reprise après '+esc(new Date(x.retryAt).toLocaleTimeString('fr-FR')):'')).join('<br>')}<button id="searchSettings" class="tonal">État des services</button></div>`:'')+
  (!session.loading&&!session.cancelled&&!local.length&&!remote.length?empty(session.query.length<2?'Trouve ton prochain titre':errors.length?'Recherche partiellement indisponible':'Aucun résultat',session.query.length<2?'Saisis au moins deux caractères. Les recherches publiques restent utilisables sans clé.':errors.length?'Réessaie les sources en erreur. Ce message ne signifie pas que le titre est absent.':'Aucun résultat reçu pour cette requête.'):'')+
  (!session.loading&&errors.length?'<button id="searchRetry" class="primary">Réessayer cette page · sources en erreur</button>':'')+
  (!session.loading&&!session.cancelled&&searchHasMore&&session.query.length>=2?'<button id="searchMore" class="tonal">Charger plus de résultats</button>':'');
 bindRows();$('#searchLocalMore')?.addEventListener('click',()=>{searchLocalLimit+=60;drawSearch();});$('#searchCancel')?.addEventListener('click',cancelSearch);
 $('#searchMore')?.addEventListener('click',()=>searchResults(true));$('#searchRetry')?.addEventListener('click',()=>searchResults('retry'));
 $('#searchResume')?.addEventListener('click',()=>searchResults(Object.values(session.pages||{}).some(x=>x.error)?'retry':true));
 $('#searchSettings')?.addEventListener('click',()=>{$('#searchDialog').close();theme();fillExtraSettings();$('#settingsDialog').showModal();});
}
async function searchResults(mode=false) {
 invalidateSearch();const generation=searchGeneration,query=$('#q').value.trim(),kind=kindFilter,epoch=libraryEpoch;
 if(!mode||!searchSession||searchSession.query!==query||searchSession.kind!==kind){searchSession={query,kind,pages:null,settings:null,loading:false,cancelled:false};searchPage=0;searchLocalLimit=60;remoteResults=[];}
 const session=searchSession;session.loading=query.length>=2;session.cancelled=false;drawSearch();if(query.length<2)return;
 if(query.length>300){session.loading=false;session.pages={query:{page:0,more:false,error:'Recherche limitée à 300 caractères.',retryAt:0}};drawSearch();return;}
 const controller=new AbortController();searchController=controller;
 const valid=()=>!controller.signal.aborted&&generation===searchGeneration&&session===searchSession&&epoch===libraryEpoch&&$('#searchDialog').open&&query===$('#q').value.trim()&&kind===kindFilter;
 try{
  await ensureAutomaticServices();if(!valid())return;
  const settings=searchSettingsKey();if(!session.pages||session.settings!==settings){session.pages=FGReliability.pager(searchProviderIds(kind));session.settings=settings;remoteResults=[];searchPage=0;}
  const requests=FGReliability.requests(session.pages,mode==='retry'?'retry':'more');
  if(!requests.length){session.loading=false;drawSearch();return;}
  const result=await remoteSearch(query,kind,1,true,{requests,signal:controller.signal,force:mode==='retry'});
  if(!valid())return;
  if(settings!==searchSettingsKey()){session.cancelled=true;session.loading=false;drawSearch();return;}
  for(const source of result.sources){let retryAt=0;if(source.status===429)retryAt=Date.now()+Math.max(60000,Math.min(86400000,FGNetwork.retryDelay(source.retryAfter)));session.pages=FGReliability.settle(session.pages,source.id,source.page,source,source.error,retryAt);}
  const received=new Set(remoteResults.flatMap(FGCore.aliases));for(const m of result.items){const keys=FGCore.aliases(m);if(!keys.some(k=>received.has(k))){remoteResults.push(m);keys.forEach(k=>received.add(k));}}
  searchPage=Math.min(...Object.values(session.pages).map(x=>x.page));session.loading=false;drawSearch();
 }catch(e){if(valid()){session.loading=false;session.cancelled=true;toast(e.message);drawSearch();}}
 finally{if(searchController===controller)searchController=null;}
}
$('#searchDialog').addEventListener('close',()=>{if(!$('#searchDialog').open)invalidateSearch();});
let searchTimer;$('#q').oninput=()=>{invalidateSearch();searchTimer=setTimeout(()=>searchResults(),350);};$$('#kinds button').forEach(b=>b.onclick=()=>{kindFilter=b.dataset.kind;$$('#kinds button').forEach(x=>x.classList.toggle('selected',x===b));searchResults();});

function theme(){document.body.classList.toggle('dark',!!state.settings.dark);$('#dark').checked=!!state.settings.dark;$('#tmdb').value=state.settings.tmdbKey||'';$('#rawg').value=state.settings.rawgKey||'';$('#trakt').value=state.settings.traktId||'';$('#traktSecret').value=state.settings.traktSecret||'';$('#traktAuto').checked=state.settings.traktAutoSync!==false;$('#traktHours').value=state.settings.traktSyncIntervalHours||6;updateTraktUi();$('#lang').value=state.settings.language||'fr-FR';$('#region').value=state.settings.region||'FR';$('#jingle').checked=state.settings.detailJingleEnabled!==false;$('#spoilers').checked=!!state.settings.hideEpisodeSpoilers;$('#castImages').checked=state.settings.showCastImages!==false;$('#rewatchStats').checked=state.settings.statisticsIncludeRewatches!==false;}
$('#saveSettings').onclick=()=>{state.settings={...state.settings,dark:$('#dark').checked,tmdbKey:$('#tmdb').value.trim(),rawgKey:$('#rawg').value.trim(),traktId:$('#trakt').value.trim(),traktSecret:$('#traktSecret').value.trim(),traktAutoSync:$('#traktAuto').checked,traktSyncIntervalHours:Math.max(1,+$('#traktHours').value||6),language:$('#lang').value.trim()||'fr-FR',region:($('#region').value.trim()||'FR').toUpperCase().slice(0,2),detailJingleEnabled:$('#jingle').checked,hideEpisodeSpoilers:$('#spoilers').checked,showCastImages:$('#castImages').checked,statisticsIncludeRewatches:$('#rewatchStats').checked};save();theme();$('#settingsDialog').close();toast('Réglages enregistrés');};
$('#traktConnect').onclick=async()=>{try{state.settings.traktId=$('#trakt').value.trim();state.settings.traktSecret=$('#traktSecret').value.trim();state.settings.traktAutoSync=$('#traktAuto').checked;state.settings.traktSyncIntervalHours=Math.max(1,+$('#traktHours').value||6);save(false);await startTraktLogin()}catch(e){toast('Trakt : '+e.message)}};
$('#traktSyncNow').onclick=async()=>{try{await traktSync()}catch(e){toast('Trakt : '+e.message)}};
$('#traktDisconnect').onclick=()=>{if(confirm('Déconnecter le compte Trakt de cet appareil ?')){clearTraktAuth();toast('Trakt déconnecté')}};
// File import handlers are installed by import-ui.js after the preview modules.
document.addEventListener('keydown',e=>{if(e.target.matches('input,textarea'))return;if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='k'){e.preventDefault();$('#searchDialog').showModal();$('#q').focus();searchResults();return;}if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='e'){e.preventDefault();exportFg();return;}if(e.key==='/'){e.preventDefault();$('#searchDialog').showModal();$('#q').focus();return;}if(e.key==='Escape'&&!document.querySelector('dialog[open]')&&overlay){overlay=overlay.returnTo||null;detailJingleFor=null;render();return;}const n=Number(e.key);if(n>=1&&n<=7){screen=railItems[n-1][0];tab=0;overlay=null;detailJingleFor=null;render();}});

document.addEventListener('error',e=>{const img=e.target;if(!(img instanceof HTMLImageElement))return;const credit=img.closest('.credit');if(credit){const name=credit.querySelector('b')?.textContent||'?';const av=document.createElement('div');av.className='avatar';av.textContent=name.trim().slice(0,1)||'?';img.replaceWith(av);return;}const box=img.closest('.image-box');if(box){img.remove();box.classList.add('image-failed');return;}if(img.closest('.episode-still')){const host=img.closest('.episode-still');img.remove();host.textContent='▶';return;}img.remove();},true);

