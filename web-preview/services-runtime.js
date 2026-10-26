/* No publisher secrets in browser state or portable backups. Capabilities are booleans only. */
'use strict';
const PUBLISHER_MARKER='__FG_PUBLISHER__';
let automaticServices={loaded:false,gateway:false,tmdb:false,rawg:false,trakt:false,steam:false,retro:false};
let automaticServiceRequest=null;
function serviceCredential(provider){
 const fields={tmdb:'tmdbKey',rawg:'rawgKey',trakt:'traktId',traktSecret:'traktSecret'};
 let settings={};try{settings=state.settings;}catch{}
 if(provider==='trakt'||provider==='traktSecret'){
  const ownId=String(settings.traktId||'').trim(),ownSecret=String(settings.traktSecret||'').trim();
  if(ownId||ownSecret)return provider==='trakt'?ownId:ownSecret;
  return automaticServices.trakt?PUBLISHER_MARKER:'';
 }
 const own=String(settings[fields[provider]]||'').trim();if(own&&own!==PUBLISHER_MARKER)return own;
 return automaticServices[provider]?PUBLISHER_MARKER:'';
}
function effectiveCatalogSettings(){return {...state.settings,tmdbKey:serviceCredential('tmdb'),rawgKey:serviceCredential('rawg')};}
async function ensureAutomaticServices(force=false){
 if(automaticServiceRequest)return automaticServiceRequest;if(automaticServices.loaded&&!force)return automaticServices;
 automaticServiceRequest=(async()=>{try{
  const r=await fetch('/api/services/config',{headers:{'X-FranchiseGuide-Services':'1'},cache:'no-store',credentials:'same-origin',redirect:'error',signal:AbortSignal.timeout(3000)});
  if(!r.ok)throw Error('no gateway');const x=await r.json();if(x.contract!==1||x.app!=='FranchiseGuide Auto Services'||['tmdb','rawg','trakt','steam','retro'].some(k=>typeof x[k]!=='boolean'))throw Error('invalid capabilities');
  automaticServices={loaded:true,gateway:true,...Object.fromEntries(['tmdb','rawg','trakt','steam','retro'].map(k=>[k,x[k]]))};
 }catch{automaticServices={loaded:true,gateway:false,tmdb:false,rawg:false,trakt:false,steam:false,retro:false};}
 return automaticServices;
 })();try{return await automaticServiceRequest;}finally{automaticServiceRequest=null;}
}
async function automaticServiceFetch(url,options={}){
 let u;try{u=new URL(String(url));}catch{return fetch(url,options);}
 const providers={'api.tvmaze.com':'tvmaze','itunes.apple.com':'itunes','www.freetogame.com':'freetogame','api.themoviedb.org':'tmdb','api.rawg.io':'rawg','api.trakt.tv':'trakt','auth.trakt.tv':'trakt-auth'};
 const p=providers[u.hostname];if(!p)return fetch(url,options);if(u.protocol!=='https:'||u.username||u.password||u.port)throw Error('Origine catalogue non autorisée.');
 await ensureAutomaticServices();if(options.signal?.aborted)throw Object.assign(Error('Lecture annulée.'),{name:'AbortError'});
 const h=new Headers(options.headers||{});const key=p==='tmdb'?u.searchParams.get('api_key'):p==='rawg'?u.searchParams.get('key'):h.get('trakt-api-key');
 let managed=['tvmaze','itunes','freetogame'].includes(p)&&automaticServices.gateway;
 if(['tmdb','rawg','trakt'].includes(p))managed=automaticServices.gateway&&key===PUBLISHER_MARKER;
 if(p==='trakt-auth'){try{managed=automaticServices.gateway&&JSON.parse(options.body||'{}').client_id===PUBLISHER_MARKER;}catch{managed=false;}}
 if(managed){h.set('X-FranchiseGuide-Services','1');h.delete('trakt-api-key');u.searchParams.delete('api_key');if(p==='rawg')u.searchParams.delete('key');return fetch('/api/services/'+p+u.pathname+u.search,{...options,headers:h,credentials:'same-origin',redirect:'error'});}
 if(key===PUBLISHER_MARKER||p==='trakt-auth'&&String(options.body||'').includes(PUBLISHER_MARKER))throw Error('L’accès de l’éditeur est indisponible. Réessaie depuis le lanceur fourni.');
 return fetch(url,options);
}
async function publicCatalogSearch(query,kind,page=1,options={}){
 const q=String(query||'').trim();if(q.length<2||q.length>300)throw Error('Recherche : de 2 à 300 caractères.');
 if(kind==='show'){const data=await readTVmaze('/search/shows?q='+encodeURIComponent(q),options);return FGPublicCatalog.tvSearch(data,page);}
 if(kind==='movie'){const country=/^[A-Z]{2}$/.test(state.settings.region||'')?state.settings.region:'FR';const p=new URLSearchParams({term:q,country,entity:'movie',media:'movie',limit:'200',explicit:'No'});return FGPublicCatalog.movieSearch(await fetchJson('https://itunes.apple.com/search?'+p,{maxBytes:4*1024*1024,...options}),country,page);}
 if(kind==='game'){const all=await fetchJson('https://www.freetogame.com/api/games?sort-by=alphabetical',{maxBytes:4*1024*1024,cacheMs:300000,...options});return FGPublicCatalog.games(all,q,page);}
 throw Error('Catalogue public non pris en charge.');
}
async function publicCatalogBundle(m,{signal}={}){
 const sid=FGPublicCatalog.id(m.sourceId),get=url=>fetchJson(url,{signal,maxBytes:4*1024*1024,retries:0});let fresh;
 if(m.source==='tvmaze'){
  const base='https://api.tvmaze.com/shows/'+sid;
  // Entire set is prepared before commit. A failed page cannot create a partially added show.
  const [main,seasons,episodes,cast]=await FGNetwork.mapLimit([base,base+'/seasons',base+'/episodes?specials=1',base+'/cast'],2,url=>readTVmaze(url.slice('https://api.tvmaze.com'.length),{signal,maxBytes:4*1024*1024}));
  if(String(main?.id)!==sid)throw Error('La fiche TVmaze correspond à un autre titre.');fresh=FGPublicCatalog.tvBundle(main,seasons,episodes,cast,m.id);
 }else if(m.source==='itunes'){
  const country=/^[A-Z]{2}$/.test(m.country||'')?m.country:state.settings.region||'FR',raw=await get('https://itunes.apple.com/lookup?'+new URLSearchParams({id:sid,country,entity:'movie'}));
  const result=FGPublicCatalog.movieSearch(raw,country,1);if(result.items.length!==1||result.items[0].sourceId!==sid)throw Error('Référence Apple absente ou ambiguë.');fresh=result.items[0];fresh.catalogComplete=true;fresh.catalogUpdatedAt=Date.now();
 }else if(m.source==='freetogame'){
  const x=await get('https://www.freetogame.com/api/game?id='+sid);if(String(x?.id)!==sid)throw Error('Référence FreeToGame incohérente.');fresh=FGPublicCatalog.game(x);fresh.catalogComplete=true;fresh.catalogUpdatedAt=Date.now();
 }else throw Error('Source publique inconnue.');
 return {...m,...fresh,id:m.id,added:m.added,suggestion:m.suggestion};
}
function catalogueSourceLabel(m){return FGPublicCatalog.info(m.source)?.name||'';}

async function publicDiscovery(kind,page=1){
 const cfg=state.settings,filter=items=>items.filter(m=>(!cfg.discoverYearMin||m.year!=null&&m.year>=cfg.discoverYearMin)&&(!cfg.discoverYearMax||m.year!=null&&m.year<=cfg.discoverYearMax));
 if(kind==='show'){
  // TVmaze's published show index is paged by ID range. Never reuse page zero.
  if(!Number.isInteger(page)||page<1||page>100)throw Error('Page TVmaze hors limite.');
  let rows;try{rows=await readTVmaze('/shows?page='+(page-1));}
  catch(e){if(page>1&&e.status===404)return {items:[],more:false};throw e;}
  if(!Array.isArray(rows)||rows.length>250)throw Error('Page TVmaze invalide.');
  let items=filter(rows.map(FGPublicCatalog.tvShow));
  if(cfg.discoverLanguage){const names={en:'english',fr:'french',ja:'japanese',de:'german',es:'spanish',it:'italian',ko:'korean',zh:'chinese',ru:'russian',pt:'portuguese'};const wanted=names[cfg.discoverLanguage]||cfg.discoverLanguage;items=items.filter(m=>(m.originalLanguage||'').toLowerCase()===wanted.toLowerCase());}
  return {items,more:rows.length>0&&page<100};
 }
 if(kind==='game'){const rows=await fetchJson('https://www.freetogame.com/api/games?sort-by=popularity',{maxBytes:4*1024*1024,cacheMs:300000});return FGPublicCatalog.slice(filter(rows.map(FGPublicCatalog.game)),page);}
 return {items:[],more:false};
}

// Shared public-TV request gate: HTTP 429 applies to search, series, people and episodes.
let tvmazeRetryAt=0;
async function readTVmaze(path,options={}){
 if(!/^\/(?:search\/shows|shows(?:\/[1-9]\d*(?:\/(?:seasons|episodes|cast))?)?|episodes\/[1-9]\d*(?:\/(?:guestcast|guestcrew))?|people\/[1-9]\d*(?:\/(?:castcredits|crewcredits))?)(?:\?[^#]*)?$/.test(path))throw Error('Chemin TVmaze non autorisé.');
 if(Date.now()<tvmazeRetryAt)throw Object.assign(Error('Délai TVmaze en cours. Réessaie après '+new Date(tvmazeRetryAt).toLocaleTimeString('fr-FR')),{status:429,retryAfter:String(Math.ceil((tvmazeRetryAt-Date.now())/1000))});
 try{return await fetchJson('https://api.tvmaze.com'+path,{maxBytes:4*1024*1024,retries:0,...options});}
 catch(e){if(e.status===429)tvmazeRetryAt=Date.now()+Math.max(60000,Math.min(86400000,FGNetwork.retryDelay(e.retryAfter)));throw e;}
}
async function readPublicEpisodeDetails(q,signal){
 const [detail,guests,crew]=await FGNetwork.mapLimit([q.path,`/episodes/${q.episodeId}/guestcast`,`/episodes/${q.episodeId}/guestcrew`],2,path=>readTVmaze(path,{signal,cacheMs:0,force:true,maxBytes:2*1024*1024,timeoutMs:20000}));
 return FGTVmazeDetail.episodeSnapshot(detail,guests,crew,q);
}
async function readPublicPersonDetails(id,signal){
 const personId=FGTVmazeDetail.identifier(id);
 const [person,cast,crew]=await FGNetwork.mapLimit([`/people/${personId}`,`/people/${personId}/castcredits?embed=show`,`/people/${personId}/crewcredits?embed=show`],2,path=>readTVmaze(path,{signal,cacheMs:0,force:true,maxBytes:2*1024*1024,timeoutMs:20000}));
 return FGTVmazeDetail.personSnapshot(person,cast,crew,personId);
}
