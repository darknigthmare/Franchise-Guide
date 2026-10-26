/* FranchiseGuide data-integrity audit and controlled repairs. Pure domain helpers + UI adapter. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.FGIntegrity=api;})(typeof globalThis==='object'?globalThis:this,function(){
'use strict';
const copy=v=>JSON.parse(JSON.stringify(v));
const text=v=>String(v??'').trim();
const norm=v=>text(v).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('fr').replace(/[^\p{L}\p{N}]+/gu,' ').trim();
const trackedEpisode=e=>!!(e.watched||e.skipped||e.collected||e.plays||e.rating!=null||text(e.userNote)||e.lastWatchedAt||(e.watchHistory||[]).length);
const providerFields={tmdb:'tmdbId',tvdb:'tvdbId',imdb:'imdbId',trakt:'traktId',rawg:'rawgId',openlibrary:'openLibraryId',steam:'steamAppId',retro:'retroGameId',tvmaze:'tvmazeId',itunes:'itunesId',freetogame:'freeToGameId'};
const externalFields=Object.values(providerFields);
function identities(m){
 const ids=new Map();const add=(provider,value)=>{if(value==null||text(value)==='')return;if(!ids.has(provider))ids.set(provider,new Set());let v=text(value);if(provider==='openlibrary'&&/^(?:\/works\/)?OL[1-9]\d*W$/.test(v))v='/works/'+v.split('/').pop();ids.get(provider).add(v);};
 for(const [provider,field] of Object.entries(providerFields))add(provider,m?.[field]);
 if(/^tvmaze-episode-\d+$/.test(m?.id||''))add('tvmaze',m.id.slice(15));
 add('steam',m?.steam?.appId??m?.steam?.appid);
 const source=text(m?.source).toLowerCase();if(Object.hasOwn(providerFields,source))add(source,m?.sourceId);
 return ids;
}
function mediaKey(m){const year=Number(m.year||String(m.releaseDate||m.firstAired||'').slice(0,4))||0;return `${String(m.kind||'').toLowerCase()}|${norm(m.title)}|${year||'?'}`;}
function conflicts(a,b){const left=identities(a),right=identities(b),rows=[];
 for(const provider of new Set([...left.keys(),...right.keys()])){const values=[...new Set([...(left.get(provider)||[]),...(right.get(provider)||[])])];if(values.length>1)rows.push({field:providerFields[provider],a:values[0],b:values.slice(1).join(', ')});}
 return rows;
}
function audit(state){
 const media=Array.isArray(state?.media)?state.media:[],episodes=Array.isArray(state?.episodes)?state.episodes:[],lists=Array.isArray(state?.lists)?state.lists:[],gaming=state?.gaming||{accounts:[],sets:[]};
 const issues=[];const mediaIds=new Map(),episodeIds=new Map(),listIds=new Map(),showIds=new Set(media.filter(m=>String(m.kind).toLowerCase()==='show').map(m=>text(m.id)));
 for(const m of media){const id=text(m.id);if(!id)issues.push({severity:'critical',code:'MEDIA_ID_MISSING',message:`Média sans identifiant : ${text(m.title)||'sans titre'}`});else{const n=(mediaIds.get(id)||0)+1;mediaIds.set(id,n);if(n===2)issues.push({severity:'critical',code:'MEDIA_ID_DUPLICATE',id,message:`Identifiant média en double : ${id}`});}}
 for(const e of episodes){const id=text(e.id);if(!id)issues.push({severity:'critical',code:'EPISODE_ID_MISSING',message:'Épisode sans identifiant.'});else{const n=(episodeIds.get(id)||0)+1;episodeIds.set(id,n);if(n===2)issues.push({severity:'critical',code:'EPISODE_ID_DUPLICATE',id,message:`Identifiant épisode en double : ${id}`});}
   if(!showIds.has(text(e.showId)))issues.push({severity:trackedEpisode(e)?'warning':'repairable',code:'ORPHAN_EPISODE',id:e.id,showId:e.showId,tracked:trackedEpisode(e),message:`Épisode orphelin ${id||'sans id'} (${trackedEpisode(e)?'suivi à conserver':'sans suivi'})`});}
 for(const l of lists){const id=text(l.id);if(!id)issues.push({severity:'critical',code:'LIST_ID_MISSING',message:`Liste sans identifiant : ${text(l.name)||'sans nom'}`});else{const n=(listIds.get(id)||0)+1;listIds.set(id,n);if(n===2)issues.push({severity:'critical',code:'LIST_ID_DUPLICATE',id,message:`Identifiant de liste en double : ${id}`});}}
 const validLists=new Set([...listIds.keys()]);for(const m of media){const ids=Array.isArray(m.listIds)?m.listIds:[];const seen=new Set();for(const id of ids){if(seen.has(id))issues.push({severity:'repairable',code:'LIST_REF_DUPLICATE',mediaId:m.id,listId:id,message:`${m.title} référence deux fois la liste ${id}`});seen.add(id);if(!validLists.has(id))issues.push({severity:'repairable',code:'LIST_REF_ORPHAN',mediaId:m.id,listId:id,message:`${m.title} référence une liste inexistante : ${id}`});}}
 const games=new Set(media.filter(m=>m.kind==='game').map(m=>m.id)),accounts=new Map((gaming.accounts||[]).map(a=>[a.id,a]));for(const s of gaming.sets||[]){if(!games.has(s.mediaId))issues.push({severity:'warning',code:'GAMING_SET_ORPHAN',setId:s.id,mediaId:s.mediaId,message:`Relevé trophées sans jeu local : ${s.title||s.id}`});if(s.accountId&&!accounts.has(s.accountId))issues.push({severity:'warning',code:'GAMING_ACCOUNT_ORPHAN',setId:s.id,accountId:s.accountId,message:`Relevé trophées sans compte : ${s.title||s.id}`});}
 const byCandidate=new Map();for(const m of media){if(!m.added||!norm(m.title))continue;const year=Number(m.year||String(m.releaseDate||m.firstAired||'').slice(0,4))||0;const base=`${m.kind}|${norm(m.title)}`;if(!byCandidate.has(base))byCandidate.set(base,[]);byCandidate.get(base).push({...m,_auditYear:year});}
 const duplicateCandidates=[];let scannedPairs=0,truncated=false;
 outer:for(const group of byCandidate.values()){if(group.length<2)continue;for(let i=0;i<group.length;i++)for(let j=i+1;j<group.length;j++){
   if(scannedPairs>=20000||duplicateCandidates.length>=1000){truncated=true;break outer;}scannedPairs++;
   const a=group[i],b=group[j];if(a._auditYear&&b._auditYear&&a._auditYear!==b._auditYear)continue;const allowed=mergeAllowed(a,b,state,mediaIds);duplicateCandidates.push({aId:a.id,bId:b.id,title:a.title,kind:a.kind,year:a._auditYear||b._auditYear||null,conflicts:allowed.conflicts||[],safe:allowed.ok,reason:allowed.reason||''});
 }}
 const counts={critical:issues.filter(x=>x.severity==='critical').length,warning:issues.filter(x=>x.severity==='warning').length,repairable:issues.filter(x=>x.severity==='repairable').length,candidates:duplicateCandidates.length};
 return {issues,duplicateCandidates,counts,scannedPairs,truncated,healthy:!truncated&&!counts.critical&&!counts.warning&&!counts.repairable&&!counts.candidates};
}
function safeRepair(state){const next=copy(state),report=audit(next),removedEpisodeIds=[];const validLists=new Set((next.lists||[]).map(l=>l.id));
 for(const m of next.media||[]){const out=[];const seen=new Set();for(const id of Array.isArray(m.listIds)?m.listIds:[]){if(validLists.has(id)&&!seen.has(id)){out.push(id);seen.add(id);}}m.listIds=out;}
 const showIds=new Set((next.media||[]).filter(m=>String(m.kind).toLowerCase()==='show').map(m=>m.id));next.episodes=(next.episodes||[]).filter(e=>{if(showIds.has(e.showId)||trackedEpisode(e))return true;removedEpisodeIds.push(e.id);return false;});return {state:next,removedEpisodeIds,before:report,after:audit(next)};}
function mergeAllowed(a,b,state,mediaCounts){
 if(!a||!b||a.id===b.id||String(a.kind).toLowerCase()!==String(b.kind).toLowerCase())return {ok:false,reason:'Deux médias différents sont requis.'};
 if(state&&[a.id,b.id].some(id=>(mediaCounts?mediaCounts.get(text(id)):state.media.filter(m=>m.id===id).length)!==1))return {ok:false,reason:'Identifiant média en double : répare les identifiants avant toute fusion.'};
 const c=conflicts(a,b);if(c.length)return {ok:false,reason:'Identifiants fournisseurs contradictoires.',conflicts:c};
 if(a.selectedEdition&&b.selectedEdition&&JSON.stringify(a.selectedEdition)!==JSON.stringify(b.selectedEdition))return {ok:false,reason:'Éditions personnelles différentes : choisis la référence avant de fusionner.',conflicts:[{field:'selectedEdition'}]};
 if((a.selectedEdition||b.selectedEdition)&&![a,b].every(m=>m.kind==='book'&&m.source==='openlibrary'))return {ok:false,reason:'La référence d’édition ne peut pas être déplacée vers une autre source.',conflicts:[{field:'selectedEdition'}]};
 if(text(a.progressUnit)&&text(b.progressUnit)&&text(a.progressUnit)!==text(b.progressUnit))return {ok:false,reason:'Unités de progression différentes : harmonise-les avant la fusion.',conflicts:[{field:'progressUnit',a:text(a.progressUnit),b:text(b.progressUnit)}]};
 for(const k of ['trackingStatus','personalPlatform','personalFormat','playtimeMinutes'])if(a[k]!=null&&b[k]!=null&&a[k]!==b[k])return {ok:false,reason:'Parcours ou données personnelles contradictoires : choisis les valeurs avant la fusion.',conflicts:[{field:k,a:a[k],b:b[k]}]};
 if(a.trackingStatus&&b.trackingStatus&&['progressCurrent','progressTotal'].some(k=>(a[k]||0)!==(b[k]||0)))return {ok:false,reason:'Progression actuelle différente : harmonise le parcours avant la fusion.',conflicts:[{field:'currentRun'}]};
 // Relinking two snapshots to the same media may violate gaming.setIdentity. There is no
 // trustworthy newer snapshot to choose here; keep both cards instead of discarding one.
 if(state?.gaming?.sets){const keys=new Map();for(const s of state.gaming.sets){if(s.mediaId!==a.id&&s.mediaId!==b.id)continue;const key=JSON.stringify([s.provider,s.platform,s.accountId??null,s.externalGameId,s.edition??'']);if(keys.has(key))return {ok:false,reason:'Deux relevés de trophées désignent le même compte et la même édition. Exporte et consolide ces relevés avant la fusion.',conflicts:[{field:'gaming.sets',a:keys.get(key),b:s.id}]};keys.set(key,s.id);}}
 return {ok:true,conflicts:[]};
}
function mergedNote(a,b){const first=text(a.userNote),second=text(b.userNote);let note=first&&second&&first!==second?first+'\n\n[Note de la fiche fusionnée]\n'+second:first||second;
 if(a.rating!=null&&b.rating!=null&&a.rating!==b.rating)note+=(note?'\n\n':'')+'[Note personnelle de la fiche fusionnée : '+b.rating+'/10]';return note;
}
function mergeSteam(a,b){if(!a)return b||null;if(!b)return a;const accounts={...b.accounts,...a.accounts};
 for(const id of Object.keys(accounts)){const left=a.accounts?.[id],right=b.accounts?.[id];if(!left||!right)continue;const maxKnown=k=>left[k]==null?right[k]??null:right[k]==null?left[k]:Math.max(left[k],right[k]);accounts[id]={...right,...left,playtimeMinutes:maxKnown('playtimeMinutes'),lastPlayedAt:maxKnown('lastPlayedAt')};}
 return {...b,...a,accounts,hasStats:a.hasStats??b.hasStats??null};
}
function mergeTracking(a,b){
 const out={...b,...a};for(const k of Object.keys(b))if((out[k]==null||out[k]==='')&&b[k]!=null)out[k]=b[k];
 for(const k of ['favorite','hidden','collected','watchlist','started'])out[k]=!!(a[k]||b[k]);
 out.watched=!!(a.watched||b.watched);out.skipped=!out.watched&&!!(a.skipped||b.skipped);
 out.watchHistory=[...new Set([...(a.watchHistory||[]),...(b.watchHistory||[]),...(a.kind==='show'&&b.kind==='show'?[]:[a.lastWatchedAt,b.lastWatchedAt])].filter(t=>Number.isSafeInteger(t)&&t>0&&t<=8640000000000000))].sort((x,y)=>x-y);
 // Distinct dated events are a lower bound. Undated play counts may overlap, so never sum them.
 out.plays=Math.max(Number(a.plays||0),Number(b.plays||0),out.watchHistory.length,out.watched?1:0);out.lastWatchedAt=a.kind==='show'&&b.kind==='show'?Math.max(a.lastWatchedAt||0,b.lastWatchedAt||0)||null:out.watchHistory.at(-1)||null;
 out.progressCurrent=Math.max(Number(a.progressCurrent||0),Number(b.progressCurrent||0));out.progressTotal=Math.max(Number(a.progressTotal||0),Number(b.progressTotal||0));
 const current=a.trackingStatus?a:b.trackingStatus?b:null;if(current)for(const k of ['trackingStatus','progressCurrent','progressTotal','progressUnit'])out[k]=current[k];
 for(const k of ['listIds','genres','platforms','providers','people','productionCompanies'])out[k]=[...new Set([...(a[k]||[]),...(b[k]||[])])];
 const ratings=new Map();for(const r of [...(a.ratings||[]),...(b.ratings||[])]){const key=text(r.source||r.label).toLowerCase();if(!ratings.has(key))ratings.set(key,r);}if(a.ratings||b.ratings)out.ratings=[...ratings.values()];
 out.rating=a.rating??b.rating??null;out.userNote=mergedNote(a,b);
 // source/sourceId only has one slot. Preserve other known provider identities in their own fields.
 for(const m of [a,b])for(const [provider,values] of identities(m)){const key=providerFields[provider];if(out[key]==null||out[key]==='')out[key]=values.values().next().value;}
 if(a.steam||b.steam)out.steam=mergeSteam(a.steam,b.steam);return out;
}
function knownNumber(e){return !e.seasonUnknown&&!e.numberUnknown&&Number.isInteger(e.season)&&e.season>=0&&Number.isInteger(e.num??e.number)&&(e.num??e.number)>=0;}
function episodeKeys(e){const keys=[];for(const [provider,values] of identities(e))for(const value of values)keys.push(provider+':'+value);if(knownNumber(e))keys.push(`num:${e.season}:${e.num??e.number}`);return keys;}
function mergeEpisodes(episodes,primaryId,secondaryId){
 const unaffected=episodes.filter(e=>e.showId!==primaryId&&e.showId!==secondaryId),primary=episodes.filter(e=>e.showId===primaryId),secondary=episodes.filter(e=>e.showId===secondaryId),ids=new Set();
 for(const e of [...primary,...secondary]){if(ids.has(e.id))throw Error('Identifiant épisode en double : fusion refusée.');ids.add(e.id);}
 const index=new Map(),used=new Set(),rows=primary.map(e=>({...e}));
 const add=(e,i)=>{for(const key of episodeKeys(e)){if(!index.has(key))index.set(key,new Set());index.get(key).add(i);}};
 rows.forEach(add);
 for(const e of secondary){const candidate={...e,showId:primaryId},matches=new Set();for(const key of episodeKeys(candidate))for(const i of index.get(key)||[])if(!used.has(i)&&!conflicts(rows[i],candidate).length)matches.add(i);
  if(matches.size!==1){rows.push(candidate);continue;}
  const i=matches.values().next().value,old=rows[i],joined=mergeTracking(old,candidate);joined.id=old.id;joined.showId=primaryId;
  if(!knownNumber(old)&&knownNumber(candidate)){joined.season=candidate.season;joined.num=candidate.num??candidate.number;joined.number=joined.num;joined.seasonUnknown=false;joined.numberUnknown=false;}
  rows[i]=joined;used.add(i);
 }
 return unaffected.concat(rows);
}
function mergeMedia(state,primaryId,secondaryId){const next=copy(state),a=next.media.find(m=>m.id===primaryId),b=next.media.find(m=>m.id===secondaryId),allowed=mergeAllowed(a,b,next);if(!allowed.ok)throw Error(allowed.reason);const merged=mergeTracking(a,b);merged.id=a.id;next.media=next.media.filter(m=>m.id!==b.id).map(m=>m.id===a.id?merged:m);
 if(String(a.kind).toLowerCase()==='show')next.episodes=mergeEpisodes(next.episodes||[],a.id,b.id);
 if(next.gaming?.sets)for(const s of next.gaming.sets)if(s.mediaId===b.id)s.mediaId=a.id;next.suggestions=(next.suggestions||[]).filter(m=>m.id!==b.id);return {state:next,mergedId:a.id,removedId:b.id,report:audit(next)};}
 return {audit,safeRepair,mergeAllowed,mergeMedia,mediaKey,norm,trackedEpisode};
});
