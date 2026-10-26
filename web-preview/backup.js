/* Portable V11 backups and real SeriesGuide JSON migration.
   Import is staged in memory and committed only after complete validation. */
'use strict';
const PORTABLE_PREFS=['language','region','includeSpecials','recentDays','upcomingDays','includeHiddenInCalendar','detailJingleEnabled','hideEpisodeSpoilers','showCastImages','statisticsIncludeRewatches','traktAutoSync','traktSyncIntervalHours','statusFilters','librarySort','discoverKind','discoverYearMin','discoverYearMax','discoverLanguage','autoBackupEnabled','autoBackupMinutes','catalogAutoRefresh','detailJingleVolume'];
function portableMedia(m) {
  const out=clone(m);if(out.selectedEdition!=null)out.selectedEdition=FGBookSelection.normalize(out.selectedEdition,m);
  if(out.steam!=null)out.steam=FGSteam.metadata(out.steam);
  out.kind=String(m.kind||'movie').toUpperCase();out.franchiseId=m.franchise||m.franchiseId||null;out.isSuggestion=!!(m.suggestion||m.isSuggestion);out.status=FGCore.normalizeStatus(m.statusRaw||m.status);
  delete out.franchise;delete out.suggestion;
  for(const k of Object.keys(out))if(k.startsWith('_'))delete out[k];
  return out;
}
function toPortableBackup(s) {
  const preferences={darkTheme:!!s.settings?.dark};
  for(const k of PORTABLE_PREFS)if(s.settings?.[k]!==undefined)preferences[k]=clone(s.settings[k]);
  return {schema:11,app:'FranchiseGuide',exportedBy:'web-0.36.0',createdAt:new Date().toISOString(),
    media:(s.media||[]).map(portableMedia),suggestions:(s.suggestions||[]).map(portableMedia),
    episodes:(s.episodes||[]).map(e=>{const out={...clone(e),number:e.num??e.number,airDate:e.date??e.airDate??null};delete out.num;delete out.date;return out;}),
    gaming:FGGaming.validateState(s.gaming,s.media),lists:clone(s.lists||[]).map(l=>({...l,sortMode:String(l.sortMode||'title').toUpperCase()})),customFranchises:clone(s.customFranchises||[]),preferences};
}
function fromPortableBackup(x) {
  FGCore.validateBackup(x);
  const clean=clone(x);for(const key of Object.keys(clean))if(key.startsWith("_"))delete clean[key];
  const p=x.preferences||x.settings||{},settings=clone(seed.settings);
  for(const k of PORTABLE_PREFS)if(p[k]!==undefined)settings[k]=clone(p[k]);
  settings.dark=p.darkTheme??p.dark??false;
  settings.recentDays=FGCore.integer(settings.recentDays,0,36500);settings.upcomingDays=FGCore.integer(settings.upcomingDays,0,36500);
  const gaming=FGGaming.validateState(x.gaming,x.media);gaming.accounts.forEach(a=>{a.autoSync=false;a.autoImport=false;});
  return migrate({...clean,gaming,settings,media:x.media.map(m=>({...m,...(m.selectedEdition?{selectedEdition:FGBookSelection.normalize(m.selectedEdition,m)}:{}),kind:String(m.kind).toLowerCase(),franchise:m.franchise??m.franchiseId??null,suggestion:!!(m.suggestion??m.isSuggestion)}))});
}
function exportFg(){downloadJson('franchiseguide_backup_v11.json',toPortableBackup(state));}
function upsertInto(target,m) {
  const candidates=target.media.filter(x=>FGCore.sameMedia(x,m));
  if(candidates.length>1)throw Error('Plusieurs fiches correspondent au média importé : import annulé, aucune fusion automatique.');
  const old=candidates[0];
  if(old&&FGReliability.conflicts(old,m).length)throw Error('Identifiants fournisseurs contradictoires dans le média importé.');
  if(!old){target.media.push(m);return m;}
  // Metadata can improve; existing tracking is merged without losing positive imported states.
  const joined={...m,...old,added:true};
  for(const k of ['posterUrl','backdropUrl','overview','tmdbId','tvdbId','imdbId','network','creator','runtimeMinutes','userNote','rating','contentLanguage'])if(joined[k]==null||joined[k]==='')joined[k]=m[k];
  for(const k of ['watched','favorite','collected','watchlist'])joined[k]=!!old[k]||!!m[k];
  Object.assign(joined,FGReliability.mergeHistory(old,m,{series:m.kind==='show'}));joined.listIds=[...new Set([...(old.listIds||[]),...(m.listIds||[])])];
  Object.assign(old,joined);return old;
}
function stageSeriesGuide(texts,names=[],base=state) {
  const staged=clone(base),pendingLists=[],report={shows:0,movies:0,episodes:0,lists:0,linked:0,skipped:0};
  const epIds=new Map(),epNumbers=new Map();
  function indexEpisode(e){
    if(e.tmdbId)epIds.set(e.showId+':tmdb:'+e.tmdbId,e);
    if(e.tvdbId)epIds.set(e.showId+':tvdb:'+e.tvdbId,e);
    if(!e.seasonUnknown&&!e.numberUnknown)epNumbers.set(e.showId+':'+e.season+':'+(e.num??e.number),e);
  }
  staged.episodes.forEach(indexEpisode);
  const validId=x=>Number.isInteger(Number(x))&&Number(x)>0?Number(x):null;
  const date=x=>{
    if(x==null||x===''||x===0||x===-1)return null;
    if(typeof x==='number'){if(!Number.isSafeInteger(x)||x<0)throw Error('Date SeriesGuide invalide.');const d=new Date(x);if(!Number.isFinite(+d))throw Error('Date SeriesGuide hors plage.');return d.toISOString().slice(0,10);}
    if(typeof x!=='string'||!/^\d{4}-\d{2}-\d{2}(?:T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?(?:Z|[+-]\d{2}:\d{2}))?$/.test(x))throw Error('Date SeriesGuide invalide.');
    const day=x.slice(0,10),plain=new Date(day+'T12:00:00Z');if(Number(day.slice(0,4))<1||!Number.isFinite(+plain)||plain.toISOString().slice(0,10)!==day)throw Error('Jour SeriesGuide impossible.');
    if(x.length===10)return day;
    if(Number(x.slice(11,13))>23||Number(x.slice(14,16))>59||Number(x.slice(17,19))>59)throw Error('Heure SeriesGuide invalide.');
    const instant=new Date(x);if(!Number.isFinite(+instant))throw Error('Date SeriesGuide invalide.');return instant.toISOString().slice(0,10);
  };
  const score=x=>x==null||x===''?null:Number.isFinite(Number(x))?Math.min(10,Math.max(0,Number(x))):null;
  const rates=x=>[x.rating_tmdb!=null?{source:'TMDb',label:'TMDb',score:score(x.rating_tmdb),maxScore:10,votes:x.rating_tmdb_votes??null}:null,x.rating!=null?{source:'Trakt',label:'Trakt',score:score(x.rating),maxScore:10,votes:x.rating_votes??null}:null].filter(x=>x&&x.score!==null);
  if(!Array.isArray(texts)||!texts.length||texts.length>10)throw Error('Sélectionne de 1 à 10 fichiers SeriesGuide.');
  let inputBytes=0;
  for(const raw of texts){if(typeof raw!=='string')throw Error('Fichier texte attendu.');const size=new TextEncoder().encode(raw).length;inputBytes+=size;if(size>32*1024*1024||inputBytes>64*1024*1024)throw Error('Import limité à 32 Mio par fichier et 64 Mio au total.');}
  function flag(obj,key,fallback=false){if(obj[key]==null)return fallback;const v=obj[key];if(v!==true&&v!==false&&v!==0&&v!==1)throw Error('Champ SeriesGuide '+key+' invalide : booléen attendu.');return v===true||v===1;}
  function integerField(obj,key){if(obj[key]==null)return;const n=obj[key];if(typeof n!=='number'||!Number.isSafeInteger(n)||n<0)throw Error('Champ numérique SeriesGuide '+key+' invalide.');}
  texts.forEach((raw,fileIndex)=>{
    let rows=JSON.parse(raw.replace(/^\uFEFF/,''));if(!Array.isArray(rows))throw Error('Un export SeriesGuide doit être une liste JSON.');
    if(!rows.length)return;
    const first=rows[0];if(!first||typeof first!=='object')throw Error('Export SeriesGuide invalide.');
    const name=names[fileIndex]||'';
    const type=Array.isArray(first.items)||/lists/i.test(name)?'list':Array.isArray(first.seasons)||'language'in first||/shows/i.test(name)?'show':('in_collection'in first)||('in_watchlist'in first)||('released_utc_ms'in first)||('watched'in first)||/movies/i.test(name)?'movie':null;
    if(!type)throw Error('Type d’export ambigu. Sélectionne les fichiers seriesguide-shows/movies/lists-backup.json originaux.');
    if(type==='list'){pendingLists.push(...rows);return;}
    for(const x of rows) {
      if(!x||typeof x!=='object'||Array.isArray(x))throw Error('Entrée SeriesGuide invalide.');
      const tmdbId=validId(x.tmdb_id),tvdbId=validId(x.tvdb_id),imdbId=/^tt\d+$/.test(x.imdb_id||'')?x.imdb_id:null;
      if(!tmdbId&&!(type==='show'?tvdbId:imdbId)){report.skipped++;continue;}
      const id=`sg-${type}-${tmdbId?'tmdb-'+tmdbId:tvdbId?'tvdb-'+tvdbId:'imdb-'+imdbId}`;
      for(const k of ['plays','runtime','runtime_min','last_watched_ms'])integerField(x,k);
      const watched=flag(x,'watched');
      const m={id,kind:type,title:x.title||`${type==='show'?'Série':'Film'} ${tmdbId||tvdbId||imdbId}`,tmdbId,tvdbId,imdbId,source:tmdbId?'tmdb':'seriesguide',sourceId:String(tmdbId||tvdbId||imdbId),added:true,suggestion:false,listIds:[],
        contentLanguage:FGCore.contentLanguage(x.language),overview:x.overview||'',posterUrl:tmdbImage(x.poster),watched,plays:watched?Math.max(1,FGCore.integer(x.plays)):0,
        favorite:flag(x,'favorite'),hidden:flag(x,'hidden'),notify:flag(x,'notify',true),collected:flag(x,'in_collection'),watchlist:flag(x,'in_watchlist'),
        rating:Number(x.rating_user)>0?FGCore.integer(x.rating_user,1,10):null,userNote:x.user_note||null,
        catalogRating:score(x.rating_tmdb),catalogRatingVotes:x.rating_tmdb_votes??null,ratings:rates(x),
        status:FGCore.normalizeStatus(x.status),statusRaw:x.status||null,network:x.network||null,runtimeMinutes:x.runtime??x.runtime_min??null,
        genres:String(x.genres||'').split('|').filter(Boolean),releaseDate:date(x.first_aired||x.released_utc_ms),lastWatchedAt:x.last_watched_ms>0?x.last_watched_ms:null,
        catalogComplete:false,seasons:[],credits:[],providers:[],addedAt:Date.now()};
      m.year=m.releaseDate?Number(m.releaseDate.slice(0,4)):null;assignFranchise(m);
      const local=upsertInto(staged,m);report[type==='show'?'shows':'movies']++;
      if(type!=='show')continue;
      if(x.seasons!=null&&!Array.isArray(x.seasons))throw Error('Les saisons SeriesGuide doivent être un tableau.');
      for(const se of x.seasons||[]) {
        if(!se||typeof se!=='object'||!Array.isArray(se.episodes||[]))throw Error('Saison importée invalide.');
        integerField(se,'season');
        const seasonUnknown=se.season==null,season=seasonUnknown?-1:FGCore.integer(se.season);

        for(const e of se.episodes||[]) {
          if(!e||typeof e!=='object'||Array.isArray(e))throw Error('Épisode importé invalide.');
          for(const k of ['episode','plays','runtime','runtime_min','last_watched_ms'])integerField(e,k);
          const et=validId(e.tmdb_id),ev=validId(e.tvdb_id),numberUnknown=e.episode==null,number=FGCore.integer(e.episode??0);
          if((seasonUnknown||numberUnknown)&&!et&&!ev){report.skipped++;continue;}
          const incoming={showId:local.id,tmdbId:et,tvdbId:ev,season,num:number,seasonUnknown,numberUnknown};
          const byTmdb=et?epIds.get(local.id+':tmdb:'+et):null,byTvdb=ev?epIds.get(local.id+':tvdb:'+ev):null;
          const explicit=[...new Set([byTmdb,byTvdb].filter(Boolean))];
          if(explicit.length>1||explicit.some(e=>FGReliability.conflicts(e,incoming).length))throw Error('Identifiants d’épisode contradictoires : import annulé.');
          const candidate=!seasonUnknown&&!numberUnknown?epNumbers.get(local.id+':'+season+':'+number):null;
          const old=explicit[0]||(candidate&&FGReliability.episodeCompatible(candidate,incoming)?candidate:null);
          const importedWatched=flag(e,'watched'),importedSkipped=flag(e,'skipped');
          const seen=!!old?.watched||(!importedSkipped&&importedWatched),skipped=!seen&&(importedSkipped||!!old?.skipped);

          const neo={id:old?.id||`sg-ep-${local.id}-${et?'tmdb-'+et:ev?'tvdb-'+ev:season+'-'+number}`,showId:local.id,season:seasonUnknown&&old?old.season:season,num:numberUnknown&&old?(old.num??old.number):number,seasonUnknown:seasonUnknown&&(!old||!!old.seasonUnknown),numberUnknown:numberUnknown&&(!old||!!old.numberUnknown),title:e.title||old?.title||`Épisode ${number}`,date:date(e.first_aired)||old?.date||null,
            watched:seen,plays:seen?Math.max(1,FGCore.integer(e.plays),old?.plays||0):0,skipped,collected:flag(e,'collected')||!!old?.collected,
            overview:e.overview||old?.overview||'',stillUrl:tmdbImage(e.image)||old?.stillUrl||null,tmdbId:et||old?.tmdbId||null,tvdbId:ev||old?.tvdbId||null,seasonTmdbId:validId(se.tmdb_id)||old?.seasonTmdbId||null,seasonTvdbId:validId(se.tvdb_id)||old?.seasonTvdbId||null,
            catalogRating:score(e.rating_tmdb)??old?.catalogRating??null,catalogRatingVotes:e.rating_tmdb_votes??old?.catalogRatingVotes??null,rating:old?.rating??(Number(e.rating_user)>0?FGCore.integer(e.rating_user,1,10):null),userNote:old?.userNote??e.user_note??null,
            runtimeMinutes:e.runtime??e.runtime_min??old?.runtimeMinutes??m.runtimeMinutes??45,lastWatchedAt:seen?Math.max(old?.lastWatchedAt||0,e.last_watched_ms||0)||null:null,watchHistory:old?FGCore.knownWatchHistory(old):[]};
          Object.assign(neo,FGReliability.mergeHistory(old||{}, {watched:!importedSkipped&&importedWatched,skipped:importedSkipped,plays:!importedSkipped&&importedWatched?Math.max(1,FGCore.integer(e.plays)):0,lastWatchedAt:e.last_watched_ms||null}));
          neo.skipped=!neo.watched&&(importedSkipped||!!old?.skipped);
          if(old){Object.assign(old,neo);indexEpisode(old);}else{staged.episodes.push(neo);indexEpisode(neo);}report.episodes++;
        }
      }
    }
  });
  for(const x of pendingLists) {
    if(!Array.isArray(x.items))throw Error('Liste SeriesGuide invalide.');
    const id='sg-list-'+encodeURIComponent(String(x.list_id||x.name||'sans-nom')),name=x.name||'Liste SeriesGuide';
    let list=staged.lists.find(l=>l.id===id||l.name===name);if(!list){list={id,name,description:'Importée depuis SeriesGuide',sortMode:'title',ascending:true};staged.lists.push(list);}report.lists++;
    for(const it of x.items) {
      let m=staged.media.find(m=>(it.type==='tmdb-show'&&m.kind==='show'&&String(m.tmdbId)===String(it.externalId))||(it.type==='show'&&m.kind==='show'&&String(m.tvdbId)===String(it.externalId))||(['movie','tmdb-movie'].includes(it.type)&&m.kind==='movie'&&String(m.tmdbId)===String(it.externalId))||(it.type==='imdb-movie'&&m.kind==='movie'&&m.imdbId===it.externalId));
      if(!m&&['episode','season'].includes(it.type)){
        const e=staged.episodes.find(e=>String(it.type==='episode'?e.tvdbId:e.seasonTvdbId)===String(it.externalId));m=e&&staged.media.find(m=>m.id===e.showId);
      }
      if(m){m.listIds=[...new Set([...(m.listIds||[]),list.id])];report.linked++;}else report.skipped++;
    }
  }
  FGCore.validateBackup(toPortableBackup(staged));
  return {staged:migrate(staged),report};
}
function importSg(texts,names=[]) {
  const {staged,report}=stageSeriesGuide(texts,names,state);
  commitImportCandidate(staged,'Fusion de sauvegardes SeriesGuide');
  render();diagnostic('info','import',`${report.shows} séries, ${report.movies} films, ${report.episodes} épisodes importés.`);
  return `${report.shows} série(s), ${report.movies} film(s), ${report.episodes} épisode(s), ${report.lists} liste(s), ${report.linked} lien(s), ${report.skipped} référence(s) ignorée(s)`;
}
function commitImportCandidate(candidate,reason) {
  assertWritable();
  if(traktSyncing)throw Error('Une synchronisation est en cours. Attends sa fin avant de valider l’import.');
  FGCore.validateBackup(toPortableBackup(candidate));
  requireSafetySnapshot('Avant import vérifié');
  prepareLibraryReplacement(reason);
  state=clone(candidate);save(false);
}
