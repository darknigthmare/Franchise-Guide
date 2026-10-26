/* FranchiseGuide 0.9 - integrated views, safe storage, catalogue commits and Trakt sync.
   No API key is shipped. No remote write is made until the user connects their own account. */
'use strict';

function readJsonLocal(key, fallback) {
  try { return JSON.parse(localStorage.getItem(key)) || clone(fallback); } catch { return clone(fallback); }
}
function diagnostic(level, area, message) {
  try {
    const rows=readJsonLocal(LOG_KEY,[]),now=Date.now();
    rows.push({at:new Date(now).toISOString(),level,area,message:FGCore.redact(message).slice(0,400)});
    localStorage.setItem(LOG_KEY,JSON.stringify(rows.filter(x=>now-Date.parse(x.at)<3*DAY).slice(-200)));
  } catch { /* Diagnostics must not prevent the user from saving a library. */ }
}
function createBackup(reason='Automatique') {
  if(!storageWritable||recoveryOnly)return false;
  try{assertWritable();}catch{return false;}
  const at=new Date().toISOString(),snapshot={at,reason,state:toPortableBackup(state)};
  const old=backups().filter(b=>b.state);
  let next=[snapshot,...old].slice(0,5);
  while(next.length) {
    try {localStorage.setItem(BACKUP_KEY,JSON.stringify(next));lastSnapshot=Date.now();return true;}
    catch(e) {next.pop();}
  }
  diagnostic('error','backup','Espace insuffisant pour une sauvegarde locale.');
  return false;
}
function maybeBackup() {
  if(!storageWritable||recoveryOnly||state.settings.autoBackupEnabled===false)return;
  const last=backups()[0]?.at,age=last?Date.now()-Date.parse(last):Infinity;
  if(age>=FGCore.integer(state.settings.autoBackupMinutes||30,1,1440)*60000)createBackup();
}
function downloadJson(name,value) {
  const u=URL.createObjectURL(new Blob([JSON.stringify(value,null,2)],{type:'application/json'}));
  const a=document.createElement('a');a.href=u;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(u),5000);
}
function trackingFields(m) {
  return {...(m.franchiseManual?{franchiseManual:true,franchise:m.franchise,franchiseId:m.franchiseId,franchiseName:m.franchiseName}:{}),...Object.fromEntries(['watched','favorite','hidden','collected','watchlist','rating','userNote','listIds','plays','lastWatchedAt','watchHistory','progressCurrent','progressTotal','progressUnit','started','trackingStatus','playtimeMinutes','personalPlatform','personalFormat','selectedEdition','notify','addedAt'].map(k=>[k,m[k]]))};
}
function providerNames(node) {
  const labels={flatrate:'Abonnement',free:'Gratuit',ads:'Avec publicité',rent:'Location',buy:'Achat'};
  return [...new Set(Object.entries(labels).flatMap(([k,label])=>(node?.[k]||[]).map(p=>`${p.provider_name} · ${label}`)))];
}
function commitCatalog(fresh,old) {
  if(fresh._pendingEpisodes) {
    const previous=state.episodes.filter(e=>e.showId===fresh.id);
    const byLocalId=new Map(previous.map(e=>[e.id,e]));
    const byId=new Map(previous.filter(e=>e.tmdbId).map(e=>[e.tmdbId,e])),byNumber=new Map(previous.filter(e=>!e.seasonUnknown&&!e.numberUnknown).map(e=>[e.season+':'+(e.num??e.number),e]));
    const merged=fresh._pendingEpisodes.map(e=>{
      const candidate=(!e.seasonUnknown&&!e.numberUnknown)?byNumber.get(e.season+':'+e.num):null;
      const local=byLocalId.get(e.id)||(fresh.source==='tvmaze'?null:byId.get(e.tmdbId)||(candidate&&!(candidate.tmdbId&&e.tmdbId&&candidate.tmdbId!==e.tmdbId)?candidate:null));
      return local?{...e,id:local.id,watched:local.watched,plays:local.plays||0,skipped:local.skipped,collected:local.collected,lastWatchedAt:local.lastWatchedAt,watchHistory:FGCore.knownWatchHistory(local),rating:local.rating,userNote:local.userNote}:e;
    });
    // Keep orphaned tracked episodes, clearly marked, if the catalogue removes or renumbers them.
    const retainedIds=new Set(merged.map(x=>x.id));
    for(const e of previous)if(!retainedIds.has(e.id)&&(e.watched||e.skipped||e.collected||e.plays||e.rating!=null||e.userNote))merged.push({...e,catalogOrphaned:true});
    state.episodes=state.episodes.filter(e=>e.showId!==fresh.id).concat(merged);
    delete fresh._pendingEpisodes;
  }
  if(fresh._pendingFranchise&&!state.customFranchises.some(f=>f.id===fresh._pendingFranchise.id))state.customFranchises.push(fresh._pendingFranchise);
  for(const m of fresh._pendingSuggestions||[])if(![...state.media,...state.suggestions].some(x=>FGCore.sameMedia(x,m)))state.suggestions.push(m);
  delete fresh._pendingFranchise;delete fresh._pendingSuggestions;
}
function assignFranchise(m) {
 if(m.franchiseManual||m.franchise||m.franchiseId)return;
 const defs=FGFranchises.definitions(state),id=FGFranchises.effective(m,defs),def=defs.find(d=>d.id===id);
 if(def){m.franchise=def.id;m.franchiseId=def.id;m.franchiseName=def.name;}
}

function catalogEligible(m) {
  return (!!(m.tmdbId||m.tvdbId||m.imdbId)&&!!state.settings.tmdbKey&&['show','movie'].includes(m.kind)) || (m.source==='rawg'&&!!state.settings.rawgKey) || m.source==='openlibrary';
}
async function repairIncompleteCatalog() {
  if(!storageWritable||catalogRefreshRunning||state.settings.catalogAutoRefresh===false||!navigator.onLine)return;
  catalogRefreshRunning=true;let updated=0;
  try {
    const now=Date.now(),candidates=state.media.filter(m=>m.added&&catalogEligible(m)&&(!m.catalogComplete||now-(m.catalogUpdatedAt||0)>(['ENDED','CANCELED'].includes(FGCore.normalizeStatus(m.statusRaw||m.status))?90:1)*DAY)).slice(0,3);
    for(const m of candidates) {
      if(catalogBusy.has(identity(m)))continue;
      catalogBusy.add(identity(m));
      try {
        const epoch=libraryEpoch,fresh=await enrichCatalog(clone(m)),target=state.media.find(x=>x.id===m.id&&x.added);
        if(target&&epoch===libraryEpoch){commitCatalog(fresh,target);Object.assign(target,fresh,trackingFields(target));assignFranchise(target);updated++;}
      }catch(e){diagnostic('warn','catalog',e.message);}finally{catalogBusy.delete(identity(m));}
    }
    if(updated){save(false);if(!document.querySelector('dialog[open]')&&!overlay)render();diagnostic('info','catalog',`${updated} fiche(s) actualisée(s)`);}
  }finally{catalogRefreshRunning=false;}
}

const SORT_OPTIONS=[['title','Titre'],['year','Année'],['next_episode','Prochain épisode'],['status','Statut'],['remaining','Restants'],['rating','Note'],['recently_watched','Dernière activité'],['added','Date d’ajout'],['progress','Progression']];
function sorted(a,kind) {
  const v=state.settings.librarySort[kind]||{mode:'title',ascending:true};
  return FGCore.sortMedia(a,v.mode,v.ascending,state.episodes);
}
function libraryTools(kind,show=false) {
  const v=state.settings.librarySort[kind]||{mode:'title',ascending:true};
  return `<section class="filter-panel"><div class="filter-row"><label>Trier <select data-sort-kind="${kind}">${SORT_OPTIONS.filter(([k])=>show||!['next_episode','status'].includes(k)).map(([k,label])=>`<option value="${k}" ${v.mode===k?'selected':''}>${label}</option>`).join('')}</select></label><button class="tonal" data-sort-direction="${kind}" aria-label="Inverser le tri">${v.ascending?'↑ Croissant':'↓ Décroissant'}</button>${show?'<button id="refreshAll" class="tonal">↻ Actualiser la bibliothèque</button>':''}</div>${show?`<details class="status-filters"><summary>Filtrer par statut · inclure / exclure</summary><div class="filter-row">${['CONTINUING','UPCOMING','IN_PRODUCTION','PILOT','ENDED','CANCELED','UNKNOWN'].map(k=>{const v=state.settings.statusFilters[k]||0;return `<button data-status="${k}" class="chip ${v===1?'included':v===-1?'excluded':''}" aria-label="${esc(showStatusLabel(k))} : ${v===1?'inclus':v===-1?'exclu':'tous'}">${v===1?'+':v===-1?'−':'○'} ${esc(showStatusLabel(k))}</button>`;}).join('')}<button id="clearStatus" class="tonal">Réinitialiser</button></div><p class="meta">Un clic inclut le statut, un deuxième l’exclut, un troisième annule le filtre.</p></details>`:''}</section>`;
}
function calendarToolbar(mode) {
  const value=mode==='recent'?state.settings.recentDays:state.settings.upcomingDays;
  return `<div class="filter-row calendar-tools"><label>Période <select data-calendar-mode="${mode}">${[7,14,30,60,90,180,365,0].map(n=>`<option value="${n}" ${value===n?'selected':''}>${n?`${n} jours`:'Jours illimités'}</option>`).join('')}</select></label><span class="meta">Dates catalogue · ${state.settings.includeSpecials?'spéciaux inclus':'hors spéciaux'}</span></div>`;
}
function bindLibraryTools() {
  $$('[data-sort-kind]').forEach(s=>s.onchange=()=>{const k=s.dataset.sortKind;state.settings.librarySort[k]={...(state.settings.librarySort[k]||{ascending:true}),mode:s.value};save(false);render();});
  $$('[data-sort-direction]').forEach(b=>b.onclick=()=>{const k=b.dataset.sortDirection,v=state.settings.librarySort[k]||{mode:'title',ascending:true};state.settings.librarySort[k]={...v,ascending:!v.ascending};save(false);render();});
  $$('[data-status]').forEach(b=>b.onclick=()=>{const k=b.dataset.status,v=state.settings.statusFilters[k]||0;state.settings.statusFilters[k]=v===0?1:v===1?-1:0;save(false);render();document.querySelector('.status-filters')?.setAttribute('open','');});
  $('#clearStatus')?.addEventListener('click',()=>{state.settings.statusFilters={};save(false);render();});
  $$('[data-calendar-mode]').forEach(s=>s.onchange=()=>{state.settings[s.dataset.calendarMode==='recent'?'recentDays':'upcomingDays']=+s.value;save(false);render();});
  $('#refreshAll')?.addEventListener('click',()=>{overlay={type:'catalog-health',title:'Entretien des catalogues'};render();});
}
function enhanceDetail() {
  const m=(overlay.src==='suggestions'?state.suggestions:state.media).find(x=>x.id===overlay.id);if(!m)return;
  const host=document.querySelector('.detail-actions');
  if(!m.suggestion&&m.kind!=='show')host.insertAdjacentHTML('beforeend','<button id="notDone" class="tonal">○ Effacer les fins / visionnages enregistrés</button>');
  if(!m.suggestion)host.insertAdjacentHTML('beforeend','<button id="deleteMedia" class="tonal danger">Supprimer de ma bibliothèque</button>');
  const body=document.querySelector('.detail-body');
  if(m.kind==='show') {
    const s=seasonStatsAll(m),seasons=(m.seasons||[]).filter(s=>state.settings.includeSpecials||s.number!==0);
    const section=document.createElement('section');section.className='section panel';
    section.innerHTML=`<h3>Navigation dans les saisons</h3><p>${s.total} épisodes chargés · ${s.watched} vus · ${s.skipped} ignorés · ${s.remaining} à voir. Les spéciaux sont conservés même lorsqu’ils sont masqués.</p><div class="filter-row">${seasons.map(s=>`<button class="tonal" data-jump-season="${esc(s.number)}">${s.number===0?'Spéciaux':'Saison '+s.number}</button>`).join('')}<button id="toggleSpecials" class="tonal">${state.settings.includeSpecials?'Masquer':'Afficher'} les spéciaux</button></div>`;
    body.prepend(section);
    const elements=[...document.querySelectorAll('.season')];
    for(const el of elements) {
      const n=Number(el.querySelector('[data-s]')?.dataset.s),meta=(m.seasons||[]).find(s=>s.number===n);
      el.dataset.seasonNumber=n;
      if(meta?.providers?.length)el.querySelector('.season-tools').insertAdjacentHTML('afterend',`<p class="season-providers">${meta.providers.map(esc).join(' · ')}<small>Ancien relevé de saison · pays et date non conservés · JustWatch via TMDb. Vérifie les offres actuelles.</small></p>`);
      if(meta?.overview)el.querySelector('.season-tools').insertAdjacentHTML('afterend',`<p class="season-overview">${esc(meta.overview)}</p>`);
    }
    $$('[data-jump-season]').forEach(b=>b.onclick=()=>{const el=elements.find(e=>e.dataset.seasonNumber===b.dataset.jumpSeason);if(el){el.open=true;el.scrollIntoView({behavior:'smooth',block:'start'});}});
    $('#toggleSpecials').onclick=()=>{state.settings.includeSpecials=!state.settings.includeSpecials;save(false);render();};
  }
  if(!m.suggestion) {
    const section=document.createElement('section');section.className='section panel';
    section.innerHTML=`<h3>Franchise / univers</h3><label>Classement <select id="mediaFranchise"><option value="">Aucune</option>${defs().map(([id,name])=>`<option value="${esc(id)}" ${(m.franchise||m.franchiseId)===id?'selected':''}>${esc(name)}</option>`).join('')}</select></label><p class="meta">Un classement manuel évite de confondre œuvres liées, remakes et adaptations. Les suggestions ne sont jamais ajoutées sans ton action.</p>`;
    body.append(section);
    $('#mediaFranchise').onchange=()=>{m.franchiseManual=true;m.franchise=$('#mediaFranchise').value||null;m.franchiseId=m.franchise;m.franchiseName=defs().find(([id])=>id===m.franchise)?.[1]||null;save();toast('Classement enregistré');};
  }
  $('#deleteMedia')?.addEventListener('click',()=>{
    if(!confirm('Supprimer ce média et son suivi LOCAL ? Une copie de sécurité sera créée. Trakt ne sera pas effacé.'))return;
    requireSafetySnapshot('Avant suppression');state.gaming.sets=state.gaming.sets.filter(s=>s.mediaId!==m.id);gamingGeneration++;state.media=state.media.filter(x=>x.id!==m.id);state.episodes=state.episodes.filter(e=>e.showId!==m.id);save(false);overlay=null;render();
  });
  $('#notDone')?.addEventListener('click',()=>{
    if(!confirm('Effacer l’historique de ce titre ?'+(traktConnected()&&m.kind==='movie'?' Les visionnages seront aussi retirés de Trakt.':'')))return;
    Object.assign(m,FGProgress.clear(m));save();render();
  });
  $$('[data-detail="hide"],[data-detail="collect"],[data-detail="watchlist"]').forEach(b=>b.setAttribute('aria-pressed',String(!!m[{hide:'hidden',collect:'collected',watchlist:'watchlist'}[b.dataset.detail]])));
  bindPeople();if(m.kind==='show')hydrateSeasons(m);else enhancePersonalProgress(m);
  if(m.catalogUpdatedAt)body.insertAdjacentHTML('beforeend',`<p class="meta catalog-updated">Métadonnées actualisées le ${esc(humanDate(m.catalogUpdatedAt))}. ${m.kind==='show'?'Les comptes incluent les épisodes annoncés par le catalogue.':''}</p>`);
}
function renderListDetail(o) {
  const l=state.lists.find(x=>x.id===o.id);if(!l){overlay=null;return render();}
  const a=FGCore.sortMedia(state.media.filter(m=>(m.listIds||[]).includes(l.id)&&m.added),l.sortMode,l.ascending,state.episodes);
  $('#content').innerHTML=`<section class="hero"><h2>${esc(l.name)}</h2><p>${esc(l.description||'Liste personnalisée')}</p><div class="filter-row"><button id="renameList" class="tonal">Modifier</button><button id="delList" class="tonal danger">Supprimer</button><label>Tri <select id="listSort">${SORT_OPTIONS.map(([k,v])=>`<option value="${k}" ${String(l.sortMode).toLowerCase()===k?'selected':''}>${v}</option>`).join('')}</select></label><button id="listOrder" class="tonal">${l.ascending?'↑ Croissant':'↓ Décroissant'}</button></div></section>${a.map(m=>row(m)).join('')||empty('Liste vide','Ajoute des médias depuis leur fiche.')}`;
  $('#listSort').onchange=()=>{l.sortMode=$('#listSort').value;save();render();};$('#listOrder').onclick=()=>{l.ascending=!l.ascending;save();render();};
  $('#renameList').onclick=()=>{const n=prompt('Nom',l.name);if(n?.trim()){l.name=n.trim();l.description=prompt('Description',l.description||'')||'';save();overlay.title=l.name;render();}};
  $('#delList').onclick=()=>{if(confirm('Supprimer cette liste sans supprimer ses médias ?')){state.lists=state.lists.filter(x=>x.id!==l.id);state.media.forEach(m=>m.listIds=(m.listIds||[]).filter(id=>id!==l.id));save();overlay=null;render();}};
  bindRows();
}
async function renderDiscover(page=1,append=false) {
  return renderDiscoverV33(page,append);
}

function renderDiagnostics() {
  const rows=readJsonLocal(LOG_KEY,[]),report={version:'0.33.0',media:state.media.length,episodes:state.episodes.length,incomplete:state.media.filter(m=>m.added&&!m.catalogComplete).length,backups:backups().length,queued:syncState.queue.length,errors:syncState.errors||[],browserOnline:navigator.onLine};
  $('#content').innerHTML=`<section class="hero"><h2>Diagnostic local</h2><p>Journal limité à 200 lignes et 3 jours. Identifiants et clés masqués. Aucun envoi automatique.</p><div class="actions"><button id="exportLog" class="tonal">Exporter le diagnostic</button><button id="clearLog" class="tonal">Effacer le journal</button><button id="refreshCatalog" class="tonal">Réparer les fiches incomplètes</button></div></section><div class="facts panel">${Object.entries(report).filter(([k])=>k!=='errors').map(([k,v])=>`<div><b>${esc(k)}</b><span>${esc(v)}</span></div>`).join('')}</div><section class="panel">${rows.reverse().map(x=>`<p class="log-${esc(x.level)}"><small>${esc(humanDate(x.at))} · ${esc(x.area)}</small><br>${esc(x.message)}</p>`).join('')||'<p>Aucun incident enregistré.</p>'}</section>`;
  $('#exportLog').onclick=()=>downloadJson('FranchiseGuide-diagnostic.json',{...report,errors:(report.errors||[]).map(FGCore.redact),logs:rows});
  $('#clearLog').onclick=()=>{localStorage.removeItem(LOG_KEY);render();};
  $('#refreshCatalog').onclick=async()=>{await repairIncompleteCatalog();render();};
}
function renderAbout() {
  $('#content').innerHTML=`<section class="hero"><h2>FranchiseGuide 0.33.0</h2><p>Séries, films, jeux et livres dans une bibliothèque personnelle. Application indépendante de SeriesGuide.</p></section><section class="section panel"><h3>Sources et attributions</h3><p>This product uses the TMDB API but is not endorsed or certified by TMDB.</p><p>Disponibilité streaming / location / achat : JustWatch, via TMDb. Notes, avis et synchronisation cinéma / télévision : Trakt. Jeux : RAWG. Livres : Open Library. Succès : lectures de la Steam Web API lorsque la passerelle est configurée. RetroAchievements permet la lecture de profils publics lorsque sa passerelle personnelle est configurée ; cette association ne prouve pas la propriété du compte. PSNProfiles, PlayStation, Xbox, Epic et GOG restent des références de services et imports neutres, sans connecteur distant livré. Aucune affiliation ni certification n’est revendiquée.</p><p>Le son d’ouverture est une composition originale incluse dans le projet. Aucun thème de série ou jingle Plex n’est distribué.</p><h3>Ce qui reste local</h3><p>La bibliothèque et les identifiants Trakt/catalogues restent dans ce profil de navigateur. La clé API Steam et les empreintes des sessions Steam restent sur le serveur local ; le navigateur utilise un cookie HttpOnly. Les exports excluent les clés et jetons. Trakt synchronise uniquement les données séries / films prises en charge, pas les jeux, livres, franchises, listes locales ou paramètres.</p><p>La PWA synchronise pendant qu’elle est ouverte, au retour au premier plan et au rétablissement du réseau. Un navigateur fermé ne lance pas de synchronisation périodique.</p><h3>Licence</h3><p>Sources du projet fournies sous GPL-3.0-or-later. Les marques et données des fournisseurs restent soumises à leurs conditions. Voir LICENSE et les notices dans l’archive.</p></section>`;
}
function playDetailJingle(id) {
  if(!state.settings.detailJingleEnabled||detailJingleFor===id)return;
  detailJingleFor=id;if(activeAudio){activeAudio.pause();activeAudio.currentTime=0;}
  try {activeAudio=new Audio('detail_signature.wav');activeAudio.volume=FGCore.integer(state.settings.detailJingleVolume??42,0,100)/100;activeAudio.play().catch(()=>{});}catch{}
}

function requireSafetySnapshot(reason){assertWritable();if(!createBackup(reason))throw Error('Copie de sécurité impossible. Exporte un JSON avant cette opération puis libère du stockage.');}
