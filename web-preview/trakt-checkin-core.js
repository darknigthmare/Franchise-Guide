/* Trakt check-in rules. No network or storage here.
 * A local watch is applied only after an acknowledged history write or a matching remote history read.
 */
(function(root,factory){
  const activity=typeof module==='object'&&module.exports?require('./activity-core.js'):root.FGActivity;
  const api=factory(activity);
  if(typeof module==='object'&&module.exports)module.exports=api;else root.FGCheckIn=api;
})(globalThis,function(A){
'use strict';
const VERSION=1,MAX_BYTES=65536,STATUSES=['starting','active','uncertain-start','finishing','uncertain-finish','cleanup','uncertain-cancel'];
const object=x=>!!x&&typeof x==='object'&&!Array.isArray(x),own=(o,k)=>Object.prototype.hasOwnProperty.call(o,k);
function fail(message,code='CHECKIN_INVALID'){throw Object.assign(Error(message),{code});}
function integer(v,label,min=0,max=8640000000000000){if(!Number.isSafeInteger(v)||v<min||v>max)fail(label+' invalide.');return v;}
function optionalInteger(v,label,min=0,max=8640000000000000){return v==null?null:integer(v,label,min,max);}
function text(v,label,max=500,nullable=false){if(v==null&&nullable)return null;if(typeof v!=='string'||!v.trim()||v.length>max||/[\u0000-\u001f\u007f]/.test(v))fail(label+' invalide.');return v.trim();}
function target(state,kind,id){
 if(!object(state)||!Array.isArray(state.media)||!Array.isArray(state.episodes)||!['movie','episode'].includes(kind))fail('Cible de check-in invalide.');
 if(kind==='movie'){
  const rows=state.media.filter(m=>m.id===id&&m.kind==='movie'&&m.added&&!m.suggestion&&!m.isSuggestion);
  if(rows.length!==1)fail('Film retiré ou ambigu. Rouvre sa fiche.','CHECKIN_STALE');const m=rows[0];integer(m.tmdbId,'Identifiant TMDb',1,Number.MAX_SAFE_INTEGER);
  return {kind:'movie',localId:m.id,parentId:null,tmdbId:m.tmdbId,title:text(m.title,'Titre'),code:null,runtimeMinutes:Number.isSafeInteger(m.runtimeMinutes)&&m.runtimeMinutes>0&&m.runtimeMinutes<=10000?m.runtimeMinutes:null,identity:JSON.stringify(['movie',m.id,m.tmdbId])};
 }
 const eps=state.episodes.filter(e=>e.id===id),e=eps[0];if(eps.length!==1)fail('Épisode retiré ou ambigu. Rouvre la série.','CHECKIN_STALE');
 const shows=state.media.filter(m=>m.id===e.showId&&m.kind==='show'&&m.added&&!m.suggestion&&!m.isSuggestion);if(shows.length!==1)fail('Série retirée ou ambiguë.','CHECKIN_STALE');
 integer(e.tmdbId,'Identifiant TMDb épisode',1,Number.MAX_SAFE_INTEGER);const show=shows[0],code=e.seasonUnknown||e.numberUnknown?'Épisode':`S${String(e.season).padStart(2,'0')}E${String(e.num??e.number).padStart(2,'0')}`;
 return {kind:'episode',localId:e.id,parentId:show.id,tmdbId:e.tmdbId,title:text(show.title,'Titre série'),code,runtimeMinutes:Number.isSafeInteger(e.runtimeMinutes)&&e.runtimeMinutes>0&&e.runtimeMinutes<=10000?e.runtimeMinutes:null,identity:JSON.stringify(['episode',show.id,e.id,e.tmdbId])};
}
function account(value){return text(value,'Compte Trakt',250);}
function appRef(value){return value==null?null:text(value,'Application Trakt',150);}
function create(t,accountName,applicationRef,now=Date.now()){
 integer(now,'Début',1);if(!object(t)||!['movie','episode'].includes(t.kind))fail('Cible de check-in invalide.');integer(t.tmdbId,'Identifiant TMDb',1,Number.MAX_SAFE_INTEGER);
 const runtime=t.runtimeMinutes==null?null:integer(t.runtimeMinutes,'Durée',1,10000),expected=runtime==null?null:now+runtime*60000;
 return {version:VERSION,id:'checkin-'+now+'-'+String(t.tmdbId),account:account(accountName),appRef:appRef(applicationRef),kind:t.kind,localId:text(t.localId,'Identifiant local',500),parentId:t.parentId==null?null:text(t.parentId,'Identifiant parent',500),tmdbId:t.tmdbId,title:text(t.title,'Titre'),code:t.code==null?null:text(t.code,'Code',100),runtimeMinutes:runtime,identity:text(t.identity,'Identité',1000),startedAt:now,remoteStartedAt:null,expectedEndAt:expected,status:'starting',finishAt:null,lastError:null,historyApplied:false};
}
function validate(s){
 if(!object(s)||s.version!==VERSION||!STATUSES.includes(s.status))fail('Session de check-in inconnue.');
 const out={...s};text(out.id,'Session',600);account(out.account);appRef(out.appRef);if(!['movie','episode'].includes(out.kind))fail('Type de check-in invalide.');text(out.localId,'Identifiant local',500);if(out.kind==='episode')text(out.parentId,'Identifiant parent',500);else if(out.parentId!=null)fail('Parent inattendu.');integer(out.tmdbId,'Identifiant TMDb',1,Number.MAX_SAFE_INTEGER);text(out.title,'Titre');if(out.code!=null)text(out.code,'Code',100);out.runtimeMinutes=optionalInteger(out.runtimeMinutes,'Durée',1,10000);text(out.identity,'Identité',1000);integer(out.startedAt,'Début',1);out.remoteStartedAt=optionalInteger(out.remoteStartedAt,'Début distant',1);out.expectedEndAt=optionalInteger(out.expectedEndAt,'Fin estimée',1);out.finishAt=optionalInteger(out.finishAt,'Fin confirmée',1);if(out.finishAt!=null&&out.finishAt<out.startedAt)fail('Fin antérieure au début.');if(out.expectedEndAt!=null&&out.expectedEndAt<out.startedAt)fail('Fin estimée antérieure au début.');if(out.lastError!=null){if(typeof out.lastError!=='string'||out.lastError.length>800)fail('Erreur de session invalide.');}if(typeof out.historyApplied!=='boolean')fail('État local invalide.');return out;
}
function encode(s){const raw=JSON.stringify(validate(s));if(new TextEncoder().encode(raw).length>MAX_BYTES)fail('Session trop volumineuse.');return raw;}
function decode(raw){if(raw==null||raw==='')return null;if(typeof raw!=='string'||new TextEncoder().encode(raw).length>MAX_BYTES)fail('Session locale illisible.','CHECKIN_RECOVERY');let parsed;try{parsed=JSON.parse(raw);}catch{fail('Session locale illisible.','CHECKIN_RECOVERY');}return validate(parsed);}
function context(s,accountName,applicationRef){s=validate(s);return s.account===account(accountName)&&(s.appRef??null)===(appRef(applicationRef)??null);}
function body(s){s=validate(s);const node={ids:{tmdb:s.tmdbId}},key=s.kind==='episode'?'episode':'movie';return {[key]:node,app_version:'0.36.0',app_date:'2026-10-26'};}
function startConfirmed(s,response,receivedAt=Date.now()){
 s=validate(s);integer(receivedAt,'Réception',1);if(!object(response))fail('Accusé de check-in Trakt invalide.');let remote=null;if(response.started_at!=null){const t=Date.parse(response.started_at);if(!Number.isFinite(t))fail('Date de début Trakt invalide.');remote=t;}
 return {...s,status:'active',remoteStartedAt:remote,lastError:null};
}
function uncertain(s,phase,error){s=validate(s);if(!['start','finish','cancel'].includes(phase))fail('Phase incertaine invalide.');const status=phase==='start'?'uncertain-start':phase==='finish'?'uncertain-finish':'uncertain-cancel';return {...s,status,lastError:String(error||'Réponse distante inconnue').slice(0,800)};}
function beginFinish(s,at=Date.now()){s=validate(s);integer(at,'Fin',s.startedAt);return {...s,status:'finishing',finishAt:s.finishAt??at,lastError:null};}
function remoteHistoryFinish(s,at){s=validate(s);integer(at,'Date historique',s.startedAt);return {...s,status:'finishing',finishAt:at,lastError:null};}
function completionMode(s,now=Date.now()){
 s=validate(s);
 if(s.status==='cleanup'||s.historyApplied)return 'cleanup';
 // A persisted finish can have reached Trakt even when its response was lost or
 // the application closed. Never resend that history write on a later click.
 if(s.finishAt!=null||s.status==='finishing'||s.status==='uncertain-finish')return 'verify';
 return timing(s,now).due?'verify':'write';
}
function historyBody(s){s=validate(s);if(s.finishAt==null)fail('Fin non préparée.');const plural=s.kind==='episode'?'episodes':'movies';return {[plural]:[{ids:{tmdb:s.tmdbId},watched_at:new Date(s.finishAt).toISOString()}]};}
function nonNegative(v,label){if(!Number.isSafeInteger(v)||v<0)fail(label+' invalide.');return v;}
function historyAcknowledged(s,response){s=validate(s);if(!object(response))fail('Accusé historique Trakt invalide.');const plural=s.kind==='episode'?'episodes':'movies',notFound=response.not_found?.[plural];if(notFound!=null&&!Array.isArray(notFound))fail('Réponse not_found invalide.');if(Array.isArray(notFound)&&notFound.length)fail('Identifiant non reconnu par Trakt.','CHECKIN_NOT_FOUND');
 const added=response.added?.[plural],existing=response.existing?.[plural];if(added==null&&existing==null)fail('Accusé Trakt incomplet. Vérifie l’historique distant avant de réessayer.','CHECKIN_UNCERTAIN');if(added!=null)nonNegative(added,'Ajouts');if(existing!=null)nonNegative(existing,'Existants');
 if((added??0)+(existing??0)!==1)fail('Trakt n’a pas confirmé exactement ce visionnage. Vérifie l’historique distant.','CHECKIN_UNCERTAIN');return true;
}
function confirmed(s){s=validate(s);if(s.finishAt==null)fail('Fin absente.');return {...s,status:'cleanup',historyApplied:true,lastError:null};}
function recentMatch(s,rows,now=Date.now()){
 s=validate(s);integer(now,'Date de vérification',1);if(!Array.isArray(rows)||rows.length>1000)fail('Historique Trakt invalide.');const key=s.kind==='episode'?'episode':'movie',lower=s.startedAt,upper=now+300000;
 for(const row of rows){if(!object(row))fail('Ligne historique invalide.');const node=row[key],id=node?.ids?.tmdb,watched=Date.parse(row.watched_at||'');if(id===s.tmdbId&&Number.isFinite(watched)&&watched>=lower&&watched<=upper)return watched;}
 return null;
}
function apply(state,s,at=null){s=validate(s);const watchedAt=at??s.finishAt;if(watchedAt==null)fail('Date de visionnage absente.');integer(watchedAt,'Date de visionnage',1,Date.now()+300000);const next=JSON.parse(JSON.stringify(state));
 if(s.kind==='movie'){
  const rows=next.media.filter(m=>m.id===s.localId&&m.kind==='movie'&&m.added&&!m.suggestion&&!m.isSuggestion);if(rows.length!==1||JSON.stringify(['movie',rows[0].id,rows[0].tmdbId])!==s.identity)fail('Le film a changé. Aucun suivi local appliqué.','CHECKIN_STALE');const known=[...(rows[0].watchHistory||[]),rows[0].lastWatchedAt].filter(Number.isSafeInteger);if(!known.some(t=>Math.floor(t/1000)===Math.floor(watchedAt/1000)))Object.assign(rows[0],A.addViewing(rows[0],watchedAt,Date.now()+300000));
 }else{
  const eps=next.episodes.filter(e=>e.id===s.localId&&e.showId===s.parentId),shows=next.media.filter(m=>m.id===s.parentId&&m.kind==='show'&&m.added&&!m.suggestion&&!m.isSuggestion);if(eps.length!==1||shows.length!==1||JSON.stringify(['episode',shows[0].id,eps[0].id,eps[0].tmdbId])!==s.identity)fail('L’épisode a changé. Aucun suivi local appliqué.','CHECKIN_STALE');const known=[...(eps[0].watchHistory||[]),eps[0].lastWatchedAt].filter(Number.isSafeInteger);if(!known.some(t=>Math.floor(t/1000)===Math.floor(watchedAt/1000)))Object.assign(eps[0],A.addViewing(eps[0],watchedAt,Date.now()+300000));shows[0].lastWatchedAt=Math.max(shows[0].lastWatchedAt||0,eps[0].lastWatchedAt||0);
 }
 return next;
}
function timing(s,now=Date.now()){s=validate(s);integer(now,'Horloge',1);const remaining=s.expectedEndAt==null?null:Math.max(0,s.expectedEndAt-now);return {elapsed:Math.max(0,now-s.startedAt),remaining,due:s.expectedEndAt!=null&&now>=s.expectedEndAt,percent:s.expectedEndAt==null?null:Math.max(0,Math.min(100,Math.round((now-s.startedAt)/(s.expectedEndAt-s.startedAt)*100)))};}
return {VERSION,STATUSES,target,create,validate,encode,decode,context,body,startConfirmed,uncertain,beginFinish,remoteHistoryFinish,completionMode,historyBody,historyAcknowledged,confirmed,recentMatch,apply,timing};
});
