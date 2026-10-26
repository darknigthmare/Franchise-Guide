/* FranchiseGuide 0.12 - pure domain rules, shared by the Web and PC builds. */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.FGCore = api;
})(typeof globalThis === 'object' ? globalThis : this, function () {
  'use strict';
  const KINDS = ['show', 'movie', 'game', 'book'];
  const n = (x, fallback = 0) => Number.isFinite(Number(x)) ? Number(x) : fallback;
  const integer = (x, min = 0, max = Number.MAX_SAFE_INTEGER) => Math.max(min, Math.min(max, Math.trunc(n(x))));
  function localDate(value = new Date()) {
    const d = new Date(value);
    if (Number.isNaN(+d)) return null;
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
  }
  function shiftDate(value, offset) {
    const d = new Date(`${value}T12:00:00`);
    d.setDate(d.getDate() + offset);
    return localDate(d);
  }
  function normalizeStatus(value) {
    const s = String(value || 'unknown').toUpperCase().replace(/[ -]/g, '_');
    return ({ RETURNING_SERIES:'CONTINUING', CANCELLED:'CANCELED', IN_PRODUCTION:'IN_PRODUCTION' })[s] ||
      (['CONTINUING','UPCOMING','IN_PRODUCTION','PILOT','ENDED','CANCELED'].includes(s) ? s : 'UNKNOWN');
  }
  function safeUrl(value) {
    if (!value || typeof value !== 'string') return null;
    try {
      const u = new URL(value);
      if (!u.username && !u.password && (u.protocol === 'https:' || (u.protocol === 'http:' && ['127.0.0.1','localhost','[::1]'].includes(u.hostname)))) return u.href;
    } catch { /* Invalid URL: show the built-in placeholder. */ }
    return null;
  }
  function aliases(m) {
    const kind = String(m.kind || '').toLowerCase();
    return [m.tmdbId ? `tmdb:${m.tmdbId}` : null, m.imdbId ? `imdb:${m.imdbId}` : null,
      m.tvdbId ? `tvdb:${m.tvdbId}` : null, m.sourceId ? `${m.source || 'source'}:${m.source==='openlibrary'&&/^(?:\/works\/)?OL[1-9]\d{0,11}W$/.test(m.sourceId)?'/works/'+String(m.sourceId).split('/').pop():m.sourceId}` : null,
      m.id ? `local:${m.id}` : null].filter(Boolean).map(id => `${kind}:${id}`);
  }
  function sameMedia(a,b) {
    if (!a || !b || String(a.kind).toLowerCase() !== String(b.kind).toLowerCase()) return false;
    return aliases(a).some(id => aliases(b).includes(id));
  }
  // Only known dates are kept. Legacy play counts never manufacture earlier dates.
  function knownWatchHistory(item) {
    if (!item?.watched || item.skipped) return [];
    const dates = (Array.isArray(item.watchHistory) ? item.watchHistory : [])
      .filter(t => Number.isSafeInteger(t) && t > 0 && t <= 8640000000000000);
    if (Number.isSafeInteger(item.lastWatchedAt) && item.lastWatchedAt > 0 && item.lastWatchedAt <= 8640000000000000) dates.push(item.lastWatchedAt);
    return [...new Set(dates)].sort((a,b)=>a-b).slice(-Math.max(1, integer(item.plays)));
  }
  function syncAcknowledged(response,plural) {
    if(!['movies','shows','episodes','seasons'].includes(plural)||!response||typeof response!=='object'||Array.isArray(response))return false;
    return ['added','updated','deleted','existing'].some(k=>response[k]&&typeof response[k]==='object'&&!Array.isArray(response[k])&&Number.isSafeInteger(response[k][plural])&&response[k][plural]>=0);
  }
  function historyAdditions(before,after) {
    if(!after?.watched||after.skipped||(before?.watched&&integer(after.plays)<=integer(before.plays)))return [];
    const previous=new Set(knownWatchHistory(before));return knownWatchHistory(after).filter(t=>!previous.has(t));
  }
  function episodeState(e, action, now = Date.now()) {
    const out = {...e, watchHistory:knownWatchHistory(e)};
    if (action === 'watch') action = e.watched ? 'unwatch' : 'watched';
    if (action === 'watched') {
      out.watched=true; out.skipped=false; out.plays=Math.max(1,integer(e.plays));
      if (!e.watched || e.skipped) { out.lastWatchedAt=now; out.watchHistory=[now]; }
    }
    if (action === 'replay') {
      out.watched=true; out.skipped=false; out.plays=integer(e.plays)+1;
      out.lastWatchedAt=now; out.watchHistory=[...out.watchHistory,now];
    }
    if (action === 'unwatch') { out.watched=false; out.plays=0; out.lastWatchedAt=null; out.watchHistory=[]; }
    if (action === 'skip' || action === 'skipped') {
      // Skipping is a local planning state, never an implicit history deletion.
      if ((action==='skipped'||!e.skipped) && (e.watched || (e.plays||0)>0 || (e.lastWatchedAt||0)>0 || (e.watchHistory||[]).length>0)) return {...e};
      out.skipped=action === 'skipped' || !e.skipped;
      if (out.skipped) { out.watched=false; out.plays=0; out.lastWatchedAt=null; out.watchHistory=[]; }
    }
    if (action === 'collect') out.collected=!e.collected;
    if (action === 'collected') out.collected=true;
    if (action === 'uncollect') out.collected=false;
    if (action === 'unskip') out.skipped=false;
    return out;
  }
  function episodeIndex(episodes) {
    const byShow=new Map();
    for (const e of episodes||[]) { if (!byShow.has(e.showId)) byShow.set(e.showId,[]); byShow.get(e.showId).push(e); }
    return byShow;
  }
  function timeline(media,episodes) {
    const entries=[], parents=new Map(media.filter(m=>m.added!==false).map(m=>[m.id,m]));
    let totalPlays=0;
    for (const item of [...media.filter(m=>m.kind!=='show'&&m.added!==false),...episodes.filter(e=>parents.has(e.showId))]) {
      if(!item.watched || item.skipped) continue;
      const dates=knownWatchHistory(item),parent=item.showId?parents.get(item.showId):item;
      totalPlays+=Math.max(1,integer(item.plays));
      dates.forEach((at,i)=>entries.push({id:item.id+':'+at+':'+i,itemId:item.id,mediaId:parent.id,
        kind:item.showId?'episode':item.kind,title:item.title,parentTitle:parent.title,at,
        minutes:item.showId||item.kind==='movie'?Math.max(0,n(item.runtimeMinutes)):0,
        season:item.season,number:item.num??item.number}));
    }
    entries.sort((a,b)=>b.at-a.at||String(a.id).localeCompare(String(b.id)));
    return {entries,totalPlays,knownPlays:entries.length,undatedPlays:Math.max(0,totalPlays-entries.length)};
  }
  function seasonProgress(episodes) {
    const a = episodes || [];
    return { total:a.length, watched:a.filter(e=>e.watched&&!e.skipped).length,
      skipped:a.filter(e=>e.skipped).length, collected:a.filter(e=>e.collected).length,
      remaining:a.filter(e=>!e.watched&&!e.skipped).length };
  }
  function statusFilter(items, rules = {}) {
    const included = Object.keys(rules).filter(k => rules[k] === 1);
    return items.filter(m => {
      const s = normalizeStatus(m.statusRaw || m.status);
      return rules[s] !== -1 && (!included.length || included.includes(s));
    });
  }
  function sortMedia(items, mode = 'title', ascending = true, episodes = []) {
    const key = String(mode).toLowerCase();
    const collator = new Intl.Collator('fr', {numeric:true, sensitivity:'base'});
    const epMap = episodeIndex(episodes);
    const value = m => {
      const es = epMap.get(m.id) || [], progress = seasonProgress(es);
      switch(key) {
        case 'year': return m.year ?? null;
        case 'recently_watched': case 'activity': return es.reduce((last,e)=>Math.max(last,n(e.lastWatchedAt)),n(m.lastWatchedAt));
        case 'added': return m.addedAt ?? null;
        case 'status': return normalizeStatus(m.statusRaw || m.status);
        case 'remaining': return m.kind === 'show' ? progress.remaining : Math.max(0,n(m.progressTotal)-n(m.progressCurrent));
        case 'rating': return m.rating ?? m.catalogRating ?? null;
        case 'next_episode': return es.filter(e=>!e.watched&&!e.skipped&&(e.date||e.airDate))
          .map(e=>e.date||e.airDate).sort()[0] || m.nextAirDate || null;
        case 'progress': return m.kind === 'show' ? (progress.total?(progress.watched+progress.skipped)/progress.total:0)
          : ['game','book'].includes(m.kind)?(m.progressTotal?Math.min(1,n(m.progressCurrent)/m.progressTotal):0):m.watched?1:0;
        default:return m.title || '';
      }
    };
    const values = new Map(items.map(m=>[m,value(m)]));
    return [...items].sort((a,b) => {
      const av = values.get(a), bv = values.get(b);
      if (av === null && bv !== null) return 1;
      if (bv === null && av !== null) return -1;
      let c = typeof av === 'number' && typeof bv === 'number' ? av-bv : collator.compare(String(av??''),String(bv??''));
      return (ascending?c:-c) || collator.compare(a.title||'', b.title||'') || collator.compare(a.id||'',b.id||'');
    });
  }
  function calendarEpisodes(episodes, media, settings, mode, now = localDate()) {
    const shows = new Set(media.filter(m => m.added !== false && (settings.includeHiddenInCalendar || !m.hidden)).map(m=>m.id));
    const days = integer(mode==='recent'?settings.recentDays:settings.upcomingDays,0,36500);
    const begin = mode==='recent'&&days ? shiftDate(now,-days) : null;
    const end = mode!=='recent'&&days ? shiftDate(now,days) : null;
    return episodes.filter(e => {
      const d = e.date || e.airDate;
      if (!d || !shows.has(e.showId) || (!settings.includeSpecials && Number(e.season)===0)) return false;
      return mode==='recent' ? d<=now && (!begin || d>=begin) : d>=now && (!end || d<=end);
    }).sort((a,b) => (mode==='recent'?-1:1)*(a.date||a.airDate).localeCompare(b.date||b.airDate) || a.season-b.season || (a.num??a.number)-(b.num??b.number));
  }
  function validateSeason(summary, details) {
    if (!details || !Array.isArray(details.episodes)) throw Error(`Saison ${summary.season_number} : réponse invalide`);
    const numbers = new Set(), ids = new Set();
    for (const e of details.episodes) {
      if (!e || !Number.isInteger(e.id) || e.id<=0 || !Number.isInteger(e.episode_number) || e.episode_number<0 ||
          ids.has(e.id) || numbers.has(e.episode_number)) throw Error(`Saison ${summary.season_number} : épisodes invalides ou en double`);
      ids.add(e.id); numbers.add(e.episode_number);
    }
    if (details.episodes.length < integer(summary.episode_count)) throw Error(`Saison ${summary.season_number} incomplète : ${details.episodes.length}/${summary.episode_count} épisodes récupérés`);
    return details.episodes;
  }
  function isRecord(x) { return !!x && typeof x === 'object' && !Array.isArray(x); }
  function validatePreferences(p) {
    if(p == null) return;
    if(!isRecord(p))throw Error('Préférences invalides.');
    for(const k of ['statusFilters','librarySort'])if(p[k]!=null&&!isRecord(p[k]))throw Error(`Préférences ${k} invalides.`);
    for(const rule of Object.values(p.statusFilters||{}))if(![-1,0,1].includes(rule))throw Error('Filtre de statut invalide.');
    for(const sort of Object.values(p.librarySort||{}))if(!isRecord(sort))throw Error('Tri de bibliothèque invalide.');
  }
  function contentLanguage(value) {
    const code=String(value??'').trim();
    if(!code)return null;
    if(!/^[a-z]{2}(?:-[A-Z]{2})?$/.test(code))throw Error('Langue invalide : utilise fr-FR, en-US ou laisse vide.');
    return code;
  }
  function reviewText(text) {
    const value=String(text??'').trim();
    const words=value?value.split(/\s+/u).filter(Boolean).length:0;
    if(words<5)throw Error('Un avis Trakt doit contenir au moins 5 mots.');
    if(value.length>10000)throw Error('Un avis est limité à 10 000 caractères dans FranchiseGuide.');
    return {text:value,words,review:words>=200};
  }
  function validateBackup(x) {
    if (!x || !Array.isArray(x.media) || (x.schema!=null && (!Number.isInteger(x.schema) || x.schema<1 || x.schema>11))) throw Error('Format de sauvegarde non pris en charge (V1 à V11 attendues).');
    if(x.gaming!=null){const gaming=typeof FGGaming!=='undefined'?FGGaming:(typeof require==='function'?require('./gaming-core.js'):null);if(!gaming)throw Error('Module trophées manquant.');gaming.validateState(x.gaming,x.media);}
    for(const l of x.lists||[]){if(l.manualOrder!=null&&typeof l.manualOrder!=='boolean')throw Error('Ordre de liste invalide.');if(l.sortOrder!=null&&(!Array.isArray(l.sortOrder)||l.sortOrder.length>50000||l.sortOrder.some(id=>typeof id!=='string'||!id||id.length>500||/[\u0000-\u001f\u007f]/.test(id))||new Set(l.sortOrder).size!==l.sortOrder.length))throw Error('Ordre de liste mal formé ou en double.');}
    validatePreferences(x.settings); validatePreferences(x.preferences);
    if(x._syncState!=null){
      if(!isRecord(x._syncState))throw Error('Journal de synchronisation invalide.');
      for(const k of ['queue','remote','errors'])if(x._syncState[k]!=null&&!Array.isArray(x._syncState[k]))throw Error('Journal de synchronisation invalide.');
    }
    for (const key of ['media','episodes','suggestions','lists','customFranchises']) {
      if (x[key] != null && !Array.isArray(x[key])) throw Error(`${key} doit être une liste.`);
      if ((x[key]||[]).length > (key==='episodes'?200000:50000)) throw Error('Sauvegarde trop volumineuse.');
      const ids = new Set();
      for (const v of x[key]||[]) {
        if (!v || typeof v.id !== 'string' || !v.id || ids.has(v.id)) throw Error(`${key} : identifiant absent ou en double.`);
        ids.add(v.id);
        if(key==='customFranchises'){const engine=typeof FGFranchises!=='undefined'?FGFranchises:require('./franchise-core.js');engine.validateDefinition(v);}
        if (['media','suggestions'].includes(key)) {
          for (const k of ['genres','platforms','ratings','seasons','credits','listIds','productionCompanies','providers','people'])
            if(v[k]!=null && !Array.isArray(v[k])) throw Error(`${key}.${k} doit être une liste.`);
          for(const k of ['credits','ratings','seasons']) if((v[k]||[]).some(c=>c==null || (typeof c!=='object' && !(k==='credits'&&typeof c==='string')))) throw Error(`${key}.${k} invalide.`);
        }
        if(v.selectedEdition!=null){const B=typeof FGBookSelection!=='undefined'?FGBookSelection:require('./book-selection-core.js');B.normalize(v.selectedEdition,v);}
        if(v.steam!=null){if(String(v.kind).toLowerCase()!=='game')throw Error('Métadonnées Steam sur un autre type de média.');const steam=typeof FGSteam!=='undefined'?FGSteam:require('./steam-library.js');steam.metadata(v.steam);}
        if(v.contentLanguage!=null)contentLanguage(v.contentLanguage);
        if (v.lastWatchedAt!=null && (!Number.isSafeInteger(v.lastWatchedAt)||v.lastWatchedAt<0||v.lastWatchedAt>8640000000000000))throw Error('Date de suivi invalide.');
        for(const k of ['plays','progressCurrent','progressTotal','runtimeMinutes'])if(v[k]!=null&&(!Number.isFinite(v[k])||v[k]<0))throw Error(`${k} invalide.`);
        for(const k of ['genres','platforms','providers','people','productionCompanies','listIds'])if(Array.isArray(v[k])&&v[k].some(x=>typeof x!=='string'))throw Error(`${k} contient une valeur invalide.`);
        for(const k of ['date','airDate','releaseDate'])if(v[k]!=null&&(typeof v[k]!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(v[k])||(Number.isNaN(Date.parse(v[k]+'T12:00:00Z'))||new Date(v[k]+'T12:00:00Z').toISOString().slice(0,10)!==v[k])))throw Error('Date catalogue invalide.');
        if (v.trackingStatus!=null&&!['planned','active','paused','abandoned','completed'].includes(v.trackingStatus))throw Error('État de parcours invalide.');
        for(const key of ['personalPlatform','personalFormat'])if(v[key]!=null&&(typeof v[key]!=='string'||v[key].length>120||/[\x00-\x1f]/.test(v[key])))throw Error('Champ de suivi personnel invalide.');
        if(v.playtimeMinutes!=null&&(!Number.isSafeInteger(v.playtimeMinutes)||v.playtimeMinutes<0||v.playtimeMinutes>10000000))throw Error('Temps personnel invalide.');
        if (v.watchHistory!=null && (!Array.isArray(v.watchHistory)||v.watchHistory.length>100000||v.watchHistory.some(t=>!Number.isSafeInteger(t)||t<=0||t>8640000000000000))) throw Error('Dates de suivi invalides.');
        if (['media','suggestions'].includes(key) && (!KINDS.includes(String(v.kind).toLowerCase()) || typeof v.title !== 'string')) throw Error('Média invalide.');
      }
    }
    if (x.preferences!=null && (typeof x.preferences!=='object'||Array.isArray(x.preferences))) throw Error('Préférences invalides.');
    const shows = new Set(x.media.filter(m=>String(m.kind).toLowerCase()==='show').map(m=>m.id));
    for (const e of x.episodes||[]) if (!shows.has(e.showId) || !Number.isInteger(e.season) || (e.season<0&&!(e.season===-1&&e.seasonUnknown)) || !Number.isInteger(e.num??e.number) || (e.num??e.number)<0) throw Error('Épisode orphelin ou numérotation invalide.');
    return true;
  }
  function redact(text) {
    return String(text || '').replace(/\bBearer\s+[A-Za-z0-9._~+\/-]+=*/gi,'Bearer [MASQUÉ]').replace(/(api_key|key|access_token|refresh_token|client_secret|authorization)(["'\s:=]+)[^\s&,"'}]+/gi,'$1$2[MASQUÉ]');
  }
  function streaks(days, now=localDate()) {
    const set = new Set(days.filter(Boolean)); let current=0,longest=0,run=0,last=null;
    for (const day of [...set].sort()) { run = last && shiftDate(last,1)===day ? run+1 : 1; longest=Math.max(longest,run); last=day; }
    let cursor=set.has(now)?now:shiftDate(now,-1);
    while (set.has(cursor)) { current++; cursor=shiftDate(cursor,-1); }
    return {current,longest};
  }
  return {KINDS, n, integer, localDate, shiftDate, normalizeStatus, safeUrl, aliases, sameMedia, knownWatchHistory, historyAdditions,syncAcknowledged, episodeIndex, timeline, episodeState,
    seasonProgress, statusFilter, sortMedia, calendarEpisodes, validateSeason, validateBackup, validatePreferences, contentLanguage, reviewText, redact, streaks};
});
