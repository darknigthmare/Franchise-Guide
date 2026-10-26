/* Catalogue maintenance plan: local observations only, no fetch and no destructive repair. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.FGCatalog=api;})(globalThis,function(){
'use strict';const DAY=86400000;
function provider(m){if(['tvmaze','itunes','freetogame'].includes(m.source)&&m.sourceId)return m.source;if(['show','movie'].includes(m.kind)&&(m.tmdbId||m.tvdbId||m.imdbId))return 'tmdb';if(m.kind==='game'&&(m.rawgId||m.source==='rawg'&&m.sourceId))return 'rawg';if(m.kind==='book'&&(m.openLibraryId||m.source==='openlibrary'&&m.sourceId))return 'openlibrary';return null;}
function fingerprint(m){return JSON.stringify([m.id,m.kind,m.tmdbId,m.tvdbId,m.imdbId,m.source,m.sourceId,m.rawgId,m.openLibraryId,m.contentLanguage]);}
function runtime(raw){if(!raw||raw.version!==1||!Array.isArray(raw.items))return {version:1,items:[],providers:[]};return {version:1,items:raw.items.filter(x=>x&&typeof x.id==='string'&&x.id.length<=400&&Number.isSafeInteger(x.lastAttempt)&&x.lastAttempt>=0&&Number.isSafeInteger(x.failures)&&x.failures>=0&&Number.isSafeInteger(x.retryAt)&&x.retryAt>=0).slice(-10000),providers:(Array.isArray(raw.providers)?raw.providers:[]).filter(x=>x&&['tmdb','rawg','openlibrary','tvmaze','itunes','freetogame'].includes(x.provider)&&Number.isSafeInteger(x.retryAt)&&x.retryAt>=0)};}
function audit(media,episodes,settings,raw,now=Date.now()){
 const r=runtime(raw),attempts=new Map(r.items.map(x=>[x.id,x])),cooldowns=new Map(r.providers.map(x=>[x.provider,x.retryAt])),byShow=new Map();
 for(const e of episodes){let data=byShow.get(e.showId);if(!data){data={normal:0,total:0};byShow.set(e.showId,data);}data.total++;if(e.season>0)data.normal++;}
 return media.filter(m=>m.added!==false&&!m.suggestion&&!m.isSuggestion).map(m=>{
  const p=provider(m),a=attempts.get(m.id),e=byShow.get(m.id)||{normal:0,total:0},updated=Number.isSafeInteger(m.catalogUpdatedAt)?m.catalogUpdatedAt:0;
  const incomplete=!m.catalogComplete||(m.kind==='show'&&Number.isSafeInteger(m.episodeCount)&&m.episodeCount>e.normal);
  const ttl=m.kind==='show'?['ENDED','CANCELED'].includes(String(m.status||m.statusRaw).toUpperCase())?90*DAY:DAY:30*DAY;
  const stale=!updated||now-updated>=ttl;
  const keyOK=p==='tmdb'?!!String(settings.tmdbKey||'').trim():p==='rawg'?!!String(settings.rawgKey||'').trim():['openlibrary','tvmaze','itunes','freetogame'].includes(p);
  const retryAt=Math.max(a?.retryAt||0,cooldowns.get(p)||0);let status=!p?'unsupported':!keyOK?'key-missing':retryAt>now?'waiting':incomplete?'incomplete':stale?'outdated':'current';
  return {id:m.id,title:m.title,kind:m.kind,provider:p,status,incomplete,stale,missingPoster:!m.posterUrl,episodesLoaded:e.normal,episodesExpected:Number.isSafeInteger(m.episodeCount)?m.episodeCount:null,lastAttempt:a?.lastAttempt||null,retryAt:retryAt||null,failures:a?.failures||0,message:a?.message||'',updatedAt:updated||null,fingerprint:fingerprint(m)};
 });
}
function plan(rows,{incompleteOnly=true,limit=8}={}){return rows.filter(x=>['incomplete','outdated'].includes(x.status)&&(!incompleteOnly||x.incomplete)).sort((a,b)=>Number(b.incomplete)-Number(a.incomplete)||(a.lastAttempt||0)-(b.lastAttempt||0)||a.id.localeCompare(b.id)).slice(0,limit);}
function success(raw,id,now){const r=runtime(raw),items=r.items.filter(x=>x.id!==id);items.push({id,lastAttempt:now,failures:0,retryAt:0,message:''});return {...r,items:items.slice(-10000)};}
function failure(raw,row,{status=null,retryAfter=null,message='Catalogue indisponible'},now){
 const r=runtime(raw),old=r.items.find(x=>x.id===row.id),failures=Math.min(20,(old?.failures||0)+1),base=Math.min(3600000,300000*2**Math.min(4,failures-1));
 let wait=base;if(status===429){const seconds=typeof retryAfter==='number'?retryAfter:typeof retryAfter==='string'&&/^\d+(\.\d+)?$/.test(retryAfter)?Number(retryAfter):null;const date=seconds==null&&typeof retryAfter==='string'?Date.parse(retryAfter):NaN;wait=Math.max(60000,Math.min(86400000,seconds!=null?seconds*1000:Number.isFinite(date)?date-now:base));}
 const retryAt=now+wait,items=r.items.filter(x=>x.id!==row.id);items.push({id:row.id,lastAttempt:now,failures,retryAt,message:String(message).slice(0,300)});
 const providers=status===429?[...r.providers.filter(x=>x.provider!==row.provider),{provider:row.provider,retryAt:Math.max(retryAt,r.providers.find(x=>x.provider===row.provider)?.retryAt||0)}]:r.providers;
 return {...r,items:items.slice(-10000),providers};
}
return {provider,fingerprint,runtime,audit,plan,success,failure};
});
