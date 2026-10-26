/* Pure scheduling rules. No credentials, no timers, no requests and no remote writes. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.FGSyncPolicy=api;})(globalThis,function(){
'use strict';
const HOUR=3600000, MINUTE=60000, PROVIDERS=['steam','retro'], MAX_LISTS=2000;
const record=x=>!!x&&typeof x==='object'&&!Array.isArray(x);
function text(x,max=160){if(typeof x!=='string'||!x.trim()||x.length>max||/[\u0000-\u001f]/.test(x))throw Error('Identifiant de synchronisation invalide.');return x;}
function stamp(x){if(x==null)return null;if(!Number.isSafeInteger(x)||x<0||x>8640000000000000)throw Error('Date de synchronisation invalide.');return x;}
function counter(x){if(!Number.isSafeInteger(x)||x<0||x>MAX_LISTS)throw Error('Compteur de synchronisation invalide.');return x;}
function ids(x){if(!Array.isArray(x)||x.length>MAX_LISTS||new Set(x).size!==x.length)throw Error('File gaming invalide.');return x.map(v=>text(v));}
function empty(){return {version:1,pauseAll:false,lists:[],providers:[],checkpoint:null};}
function normalize(raw){
 if(raw==null)return empty();if(!record(raw)||raw.version!==1||typeof raw.pauseAll!=='boolean'||!Array.isArray(raw.lists)||raw.lists.length>MAX_LISTS||!Array.isArray(raw.providers)||raw.providers.length>2)throw Error('État local du centre de synchronisation non reconnu.');
 const lists=raw.lists.map(x=>{if(!record(x)||typeof x.paused!=='boolean')throw Error('Contrôle de liste invalide.');const status=x.status??null;if(status!==null&&(!Number.isInteger(status)||status<0||status>599))throw Error('Statut réseau invalide.');return {id:text(x.id),key:text(x.key,2000),paused:x.paused,lastAttemptAt:stamp(x.lastAttemptAt),retryAt:stamp(x.retryAt),failures:counter(x.failures??0),status};});
 const providers=raw.providers.map(x=>{if(!record(x)||!PROVIDERS.includes(x.id))throw Error('Fournisseur gaming invalide.');return {id:x.id,retryAt:stamp(x.retryAt)};});
 if(new Set(lists.map(x=>x.id)).size!==lists.length||new Set(providers.map(x=>x.id)).size!==providers.length)throw Error('Contrôles gaming en double.');
 let checkpoint=null;
 if(raw.checkpoint!=null){const c=raw.checkpoint;if(!record(c)||!['due','all'].includes(c.mode))throw Error('Lot interrompu invalide.');const pending=ids(c.pending),total=counter(c.total),ok=counter(c.ok),failed=counter(c.failed),skipped=counter(c.skipped);if(pending.length+ok+failed+skipped!==total)throw Error('Comptage du lot incohérent.');checkpoint={mode:c.mode,createdAt:stamp(c.createdAt),pending,total,ok,failed,skipped};}
 return {version:1,pauseAll:raw.pauseAll,lists,providers,checkpoint};
}
function key(set,account){return JSON.stringify([set.mediaId,set.provider,set.platform,set.accountId??null,set.externalGameId,set.edition||'',account?.provider??null,account?.externalId??null]);}
function controlFor(c,s,a){return c.lists.find(x=>x.id===s.id&&x.key===key(s,a))||{id:s.id,key:key(s,a),paused:false,lastAttemptAt:null,retryAt:null,failures:0,status:null};}
function replace(c,row){c.lists=c.lists.filter(x=>x.id!==row.id);c.lists.push(row);if(c.lists.length>MAX_LISTS)c.lists=c.lists.slice(-MAX_LISTS);return c;}
function retryAfter(value,now=Date.now(),fallback=5*MINUTE){
 let delay=fallback;
 if(typeof value==='string'&&value.trim()){
  const v=value.trim();if(/^\d+(?:\.\d+)?$/.test(v))delay=Number(v)*1000;
  else if(/^[A-Za-z]{3}, \d{2} [A-Za-z]{3} \d{4} \d{2}:\d{2}:\d{2} GMT$/.test(v)){const parsed=Date.parse(v);if(Number.isFinite(parsed))delay=parsed-now;}
 }
 if(!Number.isFinite(delay))delay=fallback;
 return now+Math.max(MINUTE,Math.min(24*HOUR,Math.ceil(delay)));
}
function outcome(raw,set,account,{ok=false,status=null,retryAfter:header=null,cancelled=false},now=Date.now()){
 const c=normalize(raw);if(cancelled)return c;const row=controlFor(c,set,account);row.lastAttemptAt=now;row.status=ok?200:(Number.isInteger(status)&&status>=0&&status<=599?status:0);
 if(ok){row.failures=0;row.retryAt=null;}else{
  row.failures=Math.min(MAX_LISTS,row.failures+1);
  const delay=Math.min(HOUR,5*MINUTE*2**Math.min(row.failures-1,4));row.retryAt=retryAfter(header,now,delay);
  if(status===429){const old=c.providers.find(p=>p.id===set.provider);if(PROVIDERS.includes(set.provider)){const until=Math.max(old?.retryAt||0,row.retryAt);c.providers=c.providers.filter(p=>p.id!==set.provider);c.providers.push({id:set.provider,retryAt:until});}}
 }
 replace(c,row);
 if(c.checkpoint?.pending.includes(set.id)){c.checkpoint.pending=c.checkpoint.pending.filter(id=>id!==set.id);c.checkpoint[ok?'ok':'failed']++;}
 return c;
}
function pauseList(raw,set,account,value){const c=normalize(raw),r=controlFor(c,set,account);r.paused=!!value;return replace(c,r);}
function resetFailure(raw,set,account){const c=normalize(raw),r=controlFor(c,set,account);r.failures=0;r.status=null;return replace(c,r);} // never shorten a provider delay
function start(raw,selected,mode='due',now=Date.now()){const c=normalize(raw),pending=ids(selected);if(!['due','all'].includes(mode))throw Error('Mode de lot invalide.');c.checkpoint={mode,createdAt:now,pending,total:pending.length,ok:0,failed:0,skipped:0};return c;}
function settleMissing(raw,existing){const c=normalize(raw),have=new Set(existing);if(c.checkpoint){const lost=c.checkpoint.pending.filter(id=>!have.has(id));c.checkpoint.pending=c.checkpoint.pending.filter(id=>have.has(id));c.checkpoint.skipped+=lost.length;}c.lists=c.lists.filter(x=>have.has(x.id));return c;}
function rows(gaming,raw,{now=Date.now(),automatic=false,dueOnly=true,accountId=null,onlyIds=null,inFlight=[],steamIdentity=null,steamExpiresAt=0,mediaIds=null}={}){
 const c=normalize(raw),accounts=new Map((gaming?.accounts||[]).map(a=>[a.id,a])),active=new Set(inFlight),wanted=onlyIds==null?null:new Set(onlyIds),media=mediaIds==null?null:new Set(mediaIds);
 return (gaming?.sets||[]).filter(s=>(!accountId||s.accountId===accountId)&&(!wanted||wanted.has(s.id))).map(s=>{
  const a=accounts.get(s.accountId),r=controlFor(c,s,a),provider=c.providers.find(p=>p.id===s.provider),until=Math.max(r.retryAt||0,provider?.retryAt||0);
  let code='ready',reason='Prête à lire';const fresh=s.lastSyncAt!=null&&s.lastSyncAt<=now+5*MINUTE&&now-s.lastSyncAt<6*HOUR;
  if(!PROVIDERS.includes(s.provider)||s.source==='manual'){code='local';reason='Liste locale / fournisseur sans lecture automatique';}
  else if(media&&!media.has(s.mediaId)){code='missing';reason='Jeu retiré de la bibliothèque';}
  else if(!a||a.provider!==s.provider){code='account';reason='Profil de jeu manquant';}
  else if(s.provider==='retro'&&!/^[0-7][0-9A-HJKMNP-TV-Z]{25}$/.test(a.externalId)){code='identity';reason='Profil RetroAchievements à associer';}
  else if(active.has(s.id)){code='running';reason='Lecture en cours';}
  else if(c.pauseAll||r.paused){code='paused';reason=c.pauseAll?'Lectures gaming suspendues':'Cette liste est en pause';}
  else if(s.requiresSteamSignIn&&(steamIdentity!==a.externalId||steamExpiresAt<=now)){code='login';reason='Connexion Steam de ce compte requise';}
  else if(until>now){code='waiting';reason=provider?.retryAt>now?'Délai de reprise du fournisseur':'Délai de reprise de cette liste';}
  else if(automatic&&!a.autoSync){code='consent';reason='Actualisation automatique non autorisée';}
  else if(dueOnly&&fresh&&!s.syncError){code='fresh';reason='Relevé actualisé depuis moins de 6 h';}
  return {id:s.id,key:r.key,mediaId:s.mediaId,accountId:s.accountId,provider:s.provider,title:s.title,edition:s.edition,platform:s.platform,accountLabel:a?.label||'Sans profil',code,reason,until:until>now?until:null,lastSyncAt:s.lastSyncAt??null,lastAttemptAt:r.lastAttemptAt,failures:r.failures,status:r.status,paused:r.paused,hasError:!!s.syncError};
 });
}
function ready(list,limit=8){return list.filter(x=>x.code==='ready').sort((a,b)=>(a.lastAttemptAt||0)-(b.lastAttemptAt||0)||(a.lastSyncAt||0)-(b.lastSyncAt||0)||a.id.localeCompare(b.id)).slice(0,limit);}
return {HOUR,MINUTE,MAX_LISTS,normalize,empty,key,retryAfter,outcome,pauseList,resetFailure,start,settleMissing,rows,ready};
});
