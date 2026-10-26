/* V0.22 - explicit episode selection and staged field-scoped edits. No storage/network. */
(function(root,factory){const c=typeof module==='object'&&module.exports?require('./core.js'):root.FGCore;const a=typeof module==='object'&&module.exports?require('./activity-core.js'):root.FGActivity;const api=factory(c,a);if(typeof module==='object'&&module.exports)module.exports=api;else root.FGEpisodes=api;})(globalThis,function(C,A){
'use strict';
const MAX=10000,ACTIONS=['watch','unwatch','skip','unskip','collect','uncollect'],EXPIRY=10*60*1000;
const normalize=x=>String(x??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('fr');
const owns=m=>m&&m.kind==='show'&&m.added!==false&&!m.suggestion&&!m.isSuggestion;
function parent(s,id){const items=s.media.filter(m=>m.id===id);if(items.length!==1||!owns(items[0]))throw Error('La série n’est plus ajoutée ou son identifiant est ambigu.');return items[0];}
function hasHistory(e){return !!e.watched||(e.plays||0)>0||(e.lastWatchedAt||0)>0||(e.watchHistory||[]).length>0;}
function publicLabel(e,hide){return A.episodeCode(e)+(hide&&!e.watched?' · Épisode non vu':e.title?' · '+e.title:'');}
function stateView(s,id,options={}){
 const show=parent(s,id),o={season:'all',status:'all',airing:'all',query:'',includeSpecials:false,hideSpoilers:false,today:C.localDate(),...options};
 if(!A.validDate(o.today)||!['all','unwatched','watched','skipped','collected','uncollected'].includes(o.status)||!['all','aired','future','unknown'].includes(o.airing)||typeof o.includeSpecials!=='boolean'||typeof o.hideSpoilers!=='boolean'||typeof o.query!=='string'||o.query.length>250)throw Error('Filtres d’épisodes invalides.');
 if(o.season!=='all'&&!Number.isInteger(o.season))throw Error('Numéro de saison invalide.');
 const all=s.episodes.filter(e=>e.showId===id),q=normalize(o.query),seasons=[...new Set(all.map(e=>e.season))].sort((a,b)=>a-b);
 const rows=all.filter(e=>{
  if(!o.includeSpecials&&e.season===0||o.season!=='all'&&e.season!==o.season)return false;
  if(o.status==='unwatched'&&(e.watched||e.skipped)||o.status==='watched'&&!e.watched||o.status==='skipped'&&!e.skipped||o.status==='collected'&&!e.collected||o.status==='uncollected'&&e.collected)return false;
  const date=e.date??e.airDate;const known=A.validDate(date);
  if(o.airing==='aired'&&(!known||date>o.today)||o.airing==='future'&&(!known||date<=o.today)||o.airing==='unknown'&&known)return false;
  return !q||normalize(publicLabel(e,o.hideSpoilers)).includes(q);
 }).sort((a,b)=>a.season-b.season||(a.num??a.number)-(b.num??b.number)||String(a.id).localeCompare(String(b.id)));
 return {show,rows,seasons,total:all.length,counts:{watched:all.filter(e=>e.watched).length,skipped:all.filter(e=>e.skipped).length,collected:all.filter(e=>e.collected).length,remaining:all.filter(e=>!e.watched&&!e.skipped).length}};
}
function identity(e){return {id:e.id,showId:e.showId,tmdbId:e.tmdbId??null,tvdbId:e.tvdbId??null,season:e.season,num:e.num??e.number,seasonUnknown:!!e.seasonUnknown,numberUnknown:!!e.numberUnknown};}
function history(e){return {watched:!!e.watched,plays:e.plays||0,lastWatchedAt:e.lastWatchedAt??null,watchHistory:[...(e.watchHistory||[])]};}
function guard(e,action){const v=identity(e);if(['collect','uncollect'].includes(action))return {...v,collected:!!e.collected};if(action==='unskip')return {...v,skipped:!!e.skipped};return {...v,...history(e),skipped:!!e.skipped,...(action==='watch'?{date:e.date??e.airDate??null}:{})};}
function outcome(e,action,at,today,includeUndatedFuture){
 if(action==='collect'||action==='uncollect'){const val=action==='collect';return {change:!!e.collected!==val?{collected:val}:null,reason:'unchanged'};}
 if(action==='unskip')return {change:e.skipped?{skipped:false}:null,reason:'unchanged'};
 if(action==='skip')return hasHistory(e)?{change:null,reason:'history-protected'}:{change:!e.skipped?{skipped:true}:null,reason:'unchanged'};
 if(action==='unwatch')return hasHistory(e)?{change:{watched:false,plays:0,lastWatchedAt:null,watchHistory:[]},reason:'unchanged'}:{change:null,reason:'unchanged'};
 if(e.watched)return {change:null,reason:'already-watched'};
 if(hasHistory(e))return {change:null,reason:'history-protected'};
 if(e.skipped)return {change:null,reason:'skipped-protected'};
 const date=e.date??e.airDate;if(!includeUndatedFuture&&(!A.validDate(date)||date>today))return {change:null,reason:'date-protected'};
 return {change:{watched:true,plays:1,skipped:false,lastWatchedAt:at,watchHistory:[at]},reason:'unchanged'};
}
function makePlan(s,showId,ids,action,options={}){
 if(!ACTIONS.includes(action)||!Array.isArray(ids)||!ids.length||ids.length>MAX||ids.some(x=>typeof x!=='string'||!x))throw Error('Sélection ou action invalide (1 à 10 000 épisodes).');
 const at=options.at??Date.now(),today=options.today??C.localDate(at),includeUndatedFuture=options.includeUndatedFuture??false;
 if(!Number.isSafeInteger(at)||at<=0||at>8640000000000000||!A.validDate(today)||typeof includeUndatedFuture!=='boolean')throw Error('Date ou option d’action invalide.');
 const show=parent(s,showId),index=new Map();for(const e of s.episodes){if(index.has(e.id))index.set(e.id,null);else index.set(e.id,e);}
 const chosen=[...new Set(ids)],entries=[],counts={selected:chosen.length,changed:0,unchanged:0,historyProtected:0,skippedProtected:0,dateProtected:0,knownDatesRemoved:0,playsRemoved:0,providerChanges:0};
 for(const id of chosen){const e=index.get(id);if(!e||e.showId!==showId)throw Error('Un épisode sélectionné a disparu, est ambigu ou appartient à une autre série.');
  const result=outcome(e,action,at,today,includeUndatedFuture),g=guard(e,action);entries.push({id,guard:g,change:result.change,reason:result.reason});
  if(result.change){counts.changed++;if(Number.isInteger(e.tmdbId)&&e.tmdbId>0&&!['skip','unskip'].includes(action))counts.providerChanges++;
   if(action==='unwatch'){counts.knownDatesRemoved+=[...new Set([...(e.watchHistory||[]),e.lastWatchedAt].filter(t=>Number.isSafeInteger(t)&&t>0))].length;counts.playsRemoved+=Math.max(e.plays||0,e.watched?1:0);}
  }else{counts.unchanged++;if(result.reason==='history-protected')counts.historyProtected++;if(result.reason==='skipped-protected')counts.skippedProtected++;if(result.reason==='date-protected')counts.dateProtected++;}
 }
 return {version:1,showId,showIdentity:{tmdbId:show.tmdbId??null,tvdbId:show.tvdbId??null},action,at,today,includeUndatedFuture,ids:chosen,entries,counts};
}
function applyPlan(s,plan,now=Date.now()){
 if(!plan||plan.version!==1||!Array.isArray(plan.ids)||!Array.isArray(plan.entries)||now<plan.at-120000||now-plan.at>EXPIRY)throw Error('Aperçu expiré ou invalide. Prépare à nouveau la sélection.');
 const fresh=makePlan(s,plan.showId,plan.ids,plan.action,{at:plan.at,today:plan.today,includeUndatedFuture:plan.includeUndatedFuture});
 // Rebuild guards/outcomes, never execute arbitrary fields from a serialized/imported patch.
 if(JSON.stringify(fresh)!==JSON.stringify(plan))throw Error('Un champ concerné a changé depuis l’aperçu. Aucune partie du lot n’a été appliquée.');
 const changes=new Map(fresh.entries.filter(e=>e.change).map(e=>[e.id,e.change]));if(!changes.size)return s;
 const episodes=s.episodes.map(e=>changes.has(e.id)?{...e,...changes.get(e.id)}:e);
 const last=episodes.filter(e=>e.showId===plan.showId).reduce((at,e)=>Math.max(at,e.lastWatchedAt||0),0)||null;
 const media=['watch','unwatch'].includes(plan.action)?s.media.map(m=>m.id===plan.showId?{...m,lastWatchedAt:last}:m):s.media;
 return {...s,media,episodes};
}
return {MAX,ACTIONS,EXPIRY,hasHistory,publicLabel,parent,stateView,makePlan,applyPlan};
});
