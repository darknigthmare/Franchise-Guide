/* V0.17: next-up discovery and field-level local organisation. No network or storage here. */
(function(root,factory){const c=typeof module==='object'&&module.exports?require('./core.js'):root.FGCore;const a=typeof module==='object'&&module.exports?require('./activity-core.js'):root.FGActivity;const api=factory(c,a);if(typeof module==='object'&&module.exports)module.exports=api;else root.FGLibrary=api;})(globalThis,function(C,A){
'use strict';
const ACTIONS=['favorite-on','favorite-off','hide','unhide','list-add','list-remove'];
const norm=x=>String(x??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('fr');
const own=m=>m&&m.added!==false&&!m.suggestion&&!m.isSuggestion;
const knownOrder=e=>!e.seasonUnknown&&!e.numberUnknown&&Number.isInteger(e.season)&&e.season>=0&&Number.isInteger(e.num??e.number)&&(e.num??e.number)>0;
function nextUp(media,episodes,options={}){
 const o={kind:'all',section:'all',query:'',favoritesOnly:false,includeHidden:false,includeSpecials:false,hideSpoilers:false,maxMinutes:0,today:C.localDate(),...options};
 if(!A.validDate(o.today)||!['all','show','movie','game','book'].includes(o.kind)||!['all','ready','future','unknown','caught-up'].includes(o.section))throw Error('Filtre de reprise invalide.');
 if(!Number.isInteger(o.maxMinutes)||o.maxMinutes<0||o.maxMinutes>10000)throw Error('Durée maximale invalide.');
 const buckets=new Map();for(const e of episodes){if(!buckets.has(e.showId))buckets.set(e.showId,[]);buckets.get(e.showId).push(e);}
 let records=[];const q=norm(o.query).slice(0,250),stats={ready:0,future:0,unknown:0,'caught-up':0};
 for(const m of media){
  if(!own(m)||(!o.includeHidden&&m.hidden)||o.favoritesOnly&&!m.favorite||o.kind!=='all'&&m.kind!==o.kind)continue;
  let status,title=m.title,episodeId=null,code=null,date=null,minutes=null,remaining=0,incomplete=false;
  if(m.kind==='show'){
   const all=(buckets.get(m.id)||[]).filter(e=>o.includeSpecials||e.season!==0),pending=all.filter(e=>!e.watched&&!e.skipped);
   remaining=pending.length;incomplete=!m.catalogComplete;
   const positions=new Set();const unnumbered=pending.some(e=>{if(!knownOrder(e))return true;const key=e.season+':'+(e.num??e.number);if(positions.has(key))return true;positions.add(key);return false;});
   if(Number.isSafeInteger(m.episodeCount)&&m.episodeCount>all.filter(e=>e.season>0).length)incomplete=true;
   if(unnumbered){status='unknown';code='Numérotation à compléter';}
   else if(!pending.length){status=all.length&&!incomplete?'caught-up':'unknown';code=all.length?'Aucun épisode non vu chargé':'Épisodes à charger';}
   else{
    pending.sort((a,b)=>a.season-b.season||(a.num??a.number)-(b.num??b.number)||String(a.id).localeCompare(String(b.id)));
    const e=pending[0];episodeId=e.id;code=A.episodeCode(e);date=e.date??e.airDate??null;
    // An unknown/future earlier episode is never jumped over to offer a later one.
    status=A.validDate(date)?date>o.today?'future':'ready':'unknown';
    minutes=Number.isSafeInteger(e.runtimeMinutes)&&e.runtimeMinutes>0?e.runtimeMinutes:null;
    title=m.title+' · '+code+(o.hideSpoilers&&!e.watched?'':e.title?' · '+e.title:'');
   }
  }else if(m.kind==='movie'){
   if(m.watched)continue;date=m.releaseDate??null;status=A.validDate(date)?date>o.today?'future':'ready':'unknown';
   minutes=Number.isSafeInteger(m.runtimeMinutes)&&m.runtimeMinutes>0?m.runtimeMinutes:null;
  }else if(['game','book'].includes(m.kind)){
   if(['completed','paused','abandoned'].includes(m.trackingStatus)||(m.trackingStatus==null&&m.watched))continue;date=m.releaseDate??null;status=A.validDate(date)&&date>o.today?'future':'ready';
  }else continue;
  const searchable=title+' '+(episodeId?'':code||'');if(q&&!norm(searchable).includes(q))continue;
  if(o.maxMinutes&&(minutes==null||minutes>o.maxMinutes))continue;
  stats[status]++;
  if(o.section!=='all'&&status!==o.section)continue;
  records.push({mediaId:m.id,kind:m.kind,title,parentTitle:m.title,episodeId,code,date:A.validDate(date)?date:null,minutes,status,remaining,incomplete,favorite:!!m.favorite,hidden:!!m.hidden,started:!!m.started||m.progressCurrent>0,progressCurrent:m.progressCurrent||0,progressTotal:m.progressTotal||0,progressUnit:m.progressUnit||''});
 }
 const rank={ready:0,future:1,unknown:2,'caught-up':3};records.sort((a,b)=>rank[a.status]-rank[b.status]||Number(b.favorite)-Number(a.favorite)||norm(a.parentTitle).localeCompare(norm(b.parentTitle),'fr')||String(a.mediaId).localeCompare(String(b.mediaId)));
 return {records,stats};
}
function property(action){if(action.startsWith('favorite-'))return 'favorite';if(action==='hide'||action==='unhide')return 'hidden';return 'listIds';}
function read(m,field,listId){return field==='listIds'?(m.listIds||[]).includes(listId):!!m[field];}
function makePlan(media,lists,ids,action,listId=null){
 if(!ACTIONS.includes(action)||!Array.isArray(ids)||ids.some(id=>typeof id!=='string'||!id)||ids.length>10000)throw Error('Sélection ou action invalide (10 000 titres maximum).');
 const selected=[...new Set(ids)];if(!selected.length)throw Error('Sélectionne au moins un titre.');
 const field=property(action),index=new Map(media.map(m=>[m.id,m]));
 if(field==='listIds'&&!lists.some(l=>l.id===listId))throw Error('La liste choisie n’existe plus.');
 const after=['favorite-on','hide','list-add'].includes(action),patches=[];
 for(const id of selected){const m=index.get(id);if(!own(m))throw Error('Un titre sélectionné a été retiré. Actualise la sélection.');
  const before=read(m,field,listId);if(before!==after)patches.push({id,title:m.title,field,listId:field==='listIds'?listId:null,before,after});
 }
 return {action,listId:field==='listIds'?listId:null,selectedCount:selected.length,unchanged:selected.length-patches.length,patches};
}
function applyPlan(media,lists,plan,undo=false){
 if(!plan||!ACTIONS.includes(plan.action)||!Array.isArray(plan.patches)||plan.patches.length>10000)throw Error('Aperçu d’action invalide.');
 const index=new Map(media.map(m=>[m.id,m])),patchIndex=new Map();
 // Validate the whole preview first; conflict on one title means no partial mutation.
 for(const p of plan.patches){
  if(patchIndex.has(p.id)||p.field!==property(plan.action)||typeof p.before!=='boolean'||typeof p.after!=='boolean'||p.before===p.after||p.after!==['favorite-on','hide','list-add'].includes(plan.action))throw Error('Action mal formée.');
  if(p.field==='listIds'&&(!lists.some(l=>l.id===p.listId)||p.listId!==plan.listId))throw Error('La liste a été supprimée. Aucune référence orpheline créée.');
  const m=index.get(p.id);if(!own(m)||read(m,p.field,p.listId)!==(undo?p.after:p.before))throw Error('Un champ concerné a changé depuis l’aperçu. Action refusée pour préserver la modification récente.');
  patchIndex.set(p.id,p);
 }
 return media.map(m=>{const p=patchIndex.get(m.id);if(!p)return m;const value=undo?p.before:p.after;
  if(p.field==='listIds')return {...m,listIds:value?[...(m.listIds||[]),p.listId]:(m.listIds||[]).filter(id=>id!==p.listId)};
  return {...m,[p.field]:value};
 });
}
return {ACTIONS,nextUp,makePlan,applyPlan};
});
