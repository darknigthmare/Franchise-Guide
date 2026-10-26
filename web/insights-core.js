/* V0.20 statistics are three separate facts: current library, dated local readings, provider snapshots. */
(function(root,factory){const req=typeof module==='object'&&module.exports;const api=factory(req?require('./core.js'):root.FGCore,req?require('./activity-core.js'):root.FGActivity,req?require('./progress-core.js'):root.FGProgress);if(req)module.exports=api;else root.FGInsights=api;})(globalThis,function(C,A,P){
'use strict';
const KINDS=['show','movie','game','book'],EVENTS=['episode','movie','game','book'];
const norm=x=>String(x??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('fr');
const own=m=>m&&m.added!==false&&!m.suggestion&&!m.isSuggestion;
const count=x=>Number.isSafeInteger(x)&&x>=0?x:0;
const minute=x=>Number.isSafeInteger(x)&&x>0?x:null;
function options(raw={}){
 const o={kind:'all',from:null,to:null,includeHidden:false,includeSpecials:false,favoritesOnly:false,query:'',now:Date.now(),...raw};
 if(!['all',...KINDS].includes(o.kind))throw Error('Catégorie statistique invalide.');
 if(!Number.isSafeInteger(o.now)||o.now<1||!C.localDate(o.now))throw Error('Date de référence invalide.');
 for(const k of ['from','to']){if(o[k]==='')o[k]=null;if(o[k]!=null&&!A.validDate(o[k]))throw Error('Date de filtre invalide.');}
 if(o.from&&o.to&&o.from>o.to)throw Error('La date de début est après la date de fin.');
 o.query=norm(o.query).slice(0,250);return o;
}
function analyse(media=[],episodes=[],gaming={},raw={}){
 const o=options(raw),selected=media.filter(m=>own(m)&&(o.includeHidden||!m.hidden)&&(!o.favoritesOnly||m.favorite)&&(o.kind==='all'||m.kind===o.kind));
 const parents=new Map(selected.map(m=>[m.id,m])),games=new Map(selected.filter(m=>m.kind==='game').map(m=>[m.id,m]));
 const eps=episodes.filter(e=>parents.get(e.showId)?.kind==='show'&&(o.includeSpecials||e.season!==0));
 const records=[...selected.filter(m=>m.kind!=='show'),...eps],byKind=Object.fromEntries(EVENTS.map(k=>[k,{titles:0,plays:0,knownDates:0,undated:0,windowPlays:0}]));
 const allEvents=[],undated=[];let futureDates=0,unknownDurationPlays=0,uniqueVideoMinutes=0,totalVideoMinutes=0;
 for(const item of records){
  if(!item.watched||item.skipped)continue;
  const parent=item.showId?parents.get(item.showId):item,kind=item.showId?'episode':item.kind;if(!EVENTS.includes(kind)||!parent)continue;
  const played=Math.max(1,count(item.plays)),known=C.knownWatchHistory(item),valid=known.filter(t=>t<=o.now);futureDates+=known.length-valid.length;
  const total=byKind[kind];total.titles++;total.plays+=played;total.knownDates+=valid.length;total.undated+=Math.max(0,played-valid.length);
  const mins=kind==='episode'||kind==='movie'?minute(item.runtimeMinutes):null;
  if(kind==='episode'||kind==='movie'){if(mins!=null){uniqueVideoMinutes+=mins;totalVideoMinutes+=mins*played;}else unknownDurationPlays+=played;}
  const itemTitle=item.showId?`${A.episodeCode(item)} · ${item.title||'Épisode'}`:item.title||'Sans titre',search=norm(parent.title+' '+itemTitle);
  if(played>valid.length)undated.push({itemId:item.id,mediaId:parent.id,kind,title:itemTitle,parentTitle:parent.title,plays:played-valid.length});
  for(const at of valid){const date=C.localDate(at);allEvents.push({id:JSON.stringify([kind,item.id,at]),itemId:item.id,mediaId:parent.id,kind,title:itemTitle,parentTitle:parent.title,at,date,minutes:mins,search});}
 }
 const window=allEvents.filter(e=>(!o.from||e.date>=o.from)&&(!o.to||e.date<=o.to)&&(!o.query||e.search.includes(o.query))).sort((a,b)=>b.at-a.at||a.id.localeCompare(b.id));
 const days=new Map();let windowVideoMinutes=0,windowUnknownDuration=0;
 for(const e of window){byKind[e.kind].windowPlays++;const d=days.get(e.date)||{date:e.date,events:0,videoMinutes:0,videoUnknown:0};d.events++;
  if(e.kind==='episode'||e.kind==='movie'){if(e.minutes==null){windowUnknownDuration++;d.videoUnknown++;}else{windowVideoMinutes+=e.minutes;d.videoMinutes+=e.minutes;}}
  days.set(e.date,d);
 }
 const statuses={game:Object.fromEntries(P.STATES.map(k=>[k,0])),book:Object.fromEntries(P.STATES.map(k=>[k,0]))};
 let currentPages=0,booksWithPageUnits=0,booksOtherUnits=0,declaredGameMinutes=0,gamesWithDeclaredMinutes=0;
 for(const m of selected){if(P.applicable(m))statuses[m.kind][P.status(m)]++;
  if(m.kind==='book'){if(['page','pages'].includes(norm(m.progressUnit).trim())){currentPages+=count(m.progressCurrent);booksWithPageUnits++;}else if(count(m.progressCurrent)>0||m.progressUnit)booksOtherUnits++;}
  if(m.kind==='game'&&m.playtimeMinutes!=null&&Number.isSafeInteger(m.playtimeMinutes)&&m.playtimeMinutes>=0){declaredGameMinutes+=m.playtimeMinutes;gamesWithDeclaredMinutes++;}
 }
 const accountMap=new Map((gaming.accounts||[]).map(a=>[a.id,a])),steamIndex=new Map();
 for(const m of games.values())if(m.steam?.appId)for(const [id,value] of Object.entries(m.steam.accounts||{})){
  let bucket=steamIndex.get(id);if(!bucket){const profile=(gaming.accounts||[]).find(a=>a.provider==='steam'&&a.externalId===id);bucket={accountId:profile?.id||null,label:profile?.label||'Profil Steam non associé',apps:new Map(),duplicates:0};steamIndex.set(id,bucket);}
  if(bucket.apps.has(m.steam.appId)){bucket.duplicates++;const prev=bucket.apps.get(m.steam.appId);bucket.apps.set(m.steam.appId,prev==null?value.playtimeMinutes:value.playtimeMinutes==null?prev:Math.max(prev,value.playtimeMinutes));}
  else bucket.apps.set(m.steam.appId,Number.isSafeInteger(value?.playtimeMinutes)&&value.playtimeMinutes>=0?value.playtimeMinutes:null);
 }
 const steam=[...steamIndex.values()].map(x=>({accountId:x.accountId,label:x.label,games:x.apps.size,knownGames:[...x.apps.values()].filter(v=>v!=null).length,unknownGames:[...x.apps.values()].filter(v=>v==null).length,minutes:[...x.apps.values()].reduce((n,v)=>n+(v??0),0),duplicateReferences:x.duplicates}));
 const achievements=[];for(const set of gaming.sets||[]){if(!games.has(set.mediaId))continue;const list=set.achievements||[],account=accountMap.get(set.accountId);
  achievements.push({id:set.id,mediaId:set.mediaId,title:set.title,accountId:set.accountId||null,accountLabel:account?.label||'Sans compte associé',provider:set.provider,platform:set.platform,edition:set.edition||'',total:list.length,earned:list.filter(a=>a.earned===true).length,unknown:list.filter(a=>a.earned==null).length,hardcore:list.filter(a=>a.hardcoreEarned===true).length,localDone:list.filter(a=>a.localDone).length,platinum:list.some(a=>a.grade==='platinum'&&a.earned===true),complete:!!set.completeCatalog&&list.length>0&&list.every(a=>a.earned===true)});
 }
 const byShow=C.episodeIndex(eps),shows=selected.filter(m=>m.kind==='show');
 const caughtUp=shows.filter(m=>{const es=byShow.get(m.id)||[];return es.length>0&&es.every(e=>e.watched||e.skipped)&&m.catalogComplete===true&&(!Number.isSafeInteger(m.episodeCount)||es.filter(e=>e.season>0).length>=m.episodeCount);}).length;
 const distribution=values=>{const counts=new Map();for(const label of values)if(typeof label==='string'&&label.trim())counts.set(label,(counts.get(label)||0)+1);return [...counts].map(([label,count])=>({label,count})).sort((a,b)=>b.count-a.count||a.label.localeCompare(b.label,'fr'));};
 const distributions={genres:distribution(selected.flatMap(m=>[...new Set(m.genres||[])])),franchises:distribution(selected.map(m=>m.franchiseName||m.franchise||m.franchiseId||'Sans franchise attribuée'))};
 const streaks=C.streaks([...new Set(allEvents.map(e=>e.date))],C.localDate(o.now));
 const rows=selected.map(m=>m.id),totalPlays=Object.values(byKind).reduce((n,k)=>n+k.plays,0),knownPlays=allEvents.length;
 return {filters:o,distributions,library:{total:selected.length,byKind:Object.fromEntries(KINDS.map(k=>[k,selected.filter(m=>m.kind===k).length])),episodes:eps.length,watchedEpisodes:eps.filter(e=>e.watched&&!e.skipped).length,collectedEpisodes:eps.filter(e=>e.collected).length,skippedEpisodes:eps.filter(e=>e.skipped&&!e.watched).length,caughtUp,everFinishedGames:selected.filter(m=>m.kind==='game'&&m.watched).length,everReadBooks:selected.filter(m=>m.kind==='book'&&m.watched).length,moviesWatched:selected.filter(m=>m.kind==='movie'&&m.watched).length,statuses,currentPages,booksWithPageUnits,booksOtherUnits,declaredGameMinutes,gamesWithDeclaredMinutes,favorites:selected.filter(m=>m.favorite).length,collected:selected.filter(m=>m.collected).length,watchlist:selected.filter(m=>m.watchlist).length},
  history:{byKind,totalPlays,knownPlays,streaks,extraPlays:Object.values(byKind).reduce((sum,k)=>sum+Math.max(0,k.plays-k.titles),0),undatedPlays:Math.max(0,totalPlays-knownPlays),futureDates,windowPlays:window.length,windowVideoMinutes,windowUnknownDuration,uniqueVideoMinutes,totalVideoMinutes,unknownDurationPlays,days:[...days.values()].sort((a,b)=>a.date.localeCompare(b.date)),events:window,undated},gaming:{steam,sets:achievements},mediaIds:rows};
}
function report(a){return {app:'FranchiseGuide',report:'Statistics (not a backup)',version:1,filters:{kind:a.filters.kind,from:a.filters.from,to:a.filters.to,includeHidden:a.filters.includeHidden,includeSpecials:a.filters.includeSpecials,favoritesOnly:a.filters.favoritesOnly,searchApplied:!!a.filters.query},library:a.library,history:{...a.history,events:undefined,undated:undefined},gaming:{steam:a.gaming.steam.map(({label,accountId,...x})=>x),sets:a.gaming.sets.map(({id,mediaId,title,accountLabel,accountId,platform,edition,...x})=>x)}};}
function csv(a){if(a.history.events.length>10000)throw Error('Plus de 10 000 événements. Réduis les filtres avant l’export.');
 const cell=x=>{let s=String(x??'');if(/^[\s]*[=+@-]/u.test(s))s="'"+s;return '"'+s.replace(/"/g,'""')+'"';};
 const lines=[['type','titre','oeuvre','date_locale','instant_utc','minutes_video_connues'],...a.history.events.map(e=>[e.kind,e.title,e.parentTitle,e.date,new Date(e.at).toISOString(),e.minutes??''])];return '\ufeff'+lines.map(row=>row.map(cell).join(';')).join('\r\n')+'\r\n';
}
return {options,analyse,report,csv};
});
