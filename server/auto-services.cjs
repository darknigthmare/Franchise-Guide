/* Publisher credentials stay in this personal loopback process. Public catalogs need no keys.
 * NOT an unauthenticated public multi-tenant proxy. No redirects, arbitrary URL, or cookie relay.
 */
'use strict';
const {ambiguousHeaders}=require('./local-request.cjs');
const {parseJson}=require('./retro-bridge.cjs');
const LIMIT=4*1024*1024,PREFIX='/api/services/',HOSTS={tvmaze:'https://api.tvmaze.com',itunes:'https://itunes.apple.com',freetogame:'https://www.freetogame.com',tmdb:'https://api.themoviedb.org',rawg:'https://api.rawg.io',trakt:'https://api.trakt.tv','trakt-auth':'https://auth.trakt.tv'};
const rules={
 tvmaze:[/^\/(search\/shows|shows|shows\/[1-9]\d*(?:\/(episodes|seasons|cast))?|episodes\/[1-9]\d*(?:\/(guestcast|guestcrew))?|people\/[1-9]\d*(?:\/(castcredits|crewcredits))?)$/,new Set(['q','page','specials','embed'])],
 itunes:[/^\/(search|lookup)$/,new Set(['term','country','entity','media','limit','lang','explicit','id'])],
 freetogame:[/^\/api\/(games|game)$/,new Set(['id','sort-by'])],
 tmdb:[/^\/3\/(search\/(multi|tv|movie|person)|discover\/(tv|movie)|trending\/(tv|movie|all)\/(day|week)|(?:tv|movie)\/[1-9]\d*(?:\/(external_ids|credits|aggregate_credits|videos|watch\/providers|content_ratings|release_dates|season\/\d+(?:\/(watch\/providers|episode\/\d+))?))?|person\/[1-9]\d*(?:\/(combined_credits|external_ids))?|collection\/[1-9]\d*|find\/(tt\d+|\d+)|watch\/providers\/regions|configuration(?:\/(countries|languages))?)$/,new Set(['api_key','language','query','page','include_adult','sort_by','region','watch_region','with_original_language','first_air_date.gte','first_air_date.lte','primary_release_date.gte','primary_release_date.lte','append_to_response','external_source'])],
 rawg:[/^\/api\/games(?:\/[a-zA-Z0-9_-]+)?$/,new Set(['key','search','page','page_size','ordering','dates'])],
 trakt:[/^\/(users\/settings|sync\/(last_activities|(?:watched|collection|watchlist|ratings|history)(?:\/(movies|shows|episodes|remove))?)|search\/tmdb\/\d+|(?:shows|movies)\/[a-zA-Z0-9_-]+\/comments(?:\/(likes|newest|oldest|highest|lowest|replies))?|comments(?:\/\d+)?|checkin)$/,new Set(['type','extended','page','limit','start_at','end_at'])],
 'trakt-auth':[/^\/oauth\/(device\/code|device\/token|token)$/,new Set()]
};
function configuration(env=process.env){const valid=(x,p)=>typeof x==='string'&&p.test(x.trim())?x.trim():'';return {tmdb:valid(env.FG_TMDB_API_KEY,/^[\da-f]{32}$/i),tmdbToken:valid(env.FG_TMDB_READ_TOKEN,/^[a-zA-Z0-9._~-]{30,2048}$/),rawg:valid(env.FG_RAWG_API_KEY,/^[a-zA-Z0-9_-]{16,128}$/),traktId:valid(env.FG_TRAKT_CLIENT_ID,/^[a-zA-Z0-9_-]{16,256}$/),traktSecret:valid(env.FG_TRAKT_CLIENT_SECRET,/^[a-zA-Z0-9_-]{16,256}$/),steam:valid(env.FG_STEAM_API_KEY,/^[\da-f]{32}$/i),retro:valid(env.FG_RETRO_API_KEY,/^[a-zA-Z0-9_-]{16,256}$/)};}
function status(c){return {contract:1,app:'FranchiseGuide Auto Services',keyless:['tvmaze','itunes','freetogame','openlibrary'],tmdb:!!(c.tmdb||c.tmdbToken),rawg:!!c.rawg,trakt:!!(c.traktId&&c.traktSecret),steam:!!c.steam,retro:!!c.retro};}
function fail(code,message){throw Object.assign(Error(message),{status:code});}
function redactResponse(body,c){
 // Decode escaped JSON before comparing secrets, and keep replacement values
 // inside valid JSON strings. User OAuth tokens remain available to the app.
 const secrets=Object.values(c).filter(Boolean),contains=s=>secrets.some(secret=>s.includes(secret));
 const clean=value=>{
  if(typeof value==='string'){for(const secret of secrets)value=value.split(secret).join('[redacted]');return value;}
  if(Array.isArray(value))return value.map(clean);
  if(value&&typeof value==='object'){const result=Object.create(null);for(const [name,child]of Object.entries(value))if(!contains(name))result[name]=clean(child);return result;}
  return typeof value==='number'&&contains(String(value))?'[redacted]':value;
 };
 return JSON.stringify(clean(parseJson(body)));
}
function retrySeconds(value,now){
 if(typeof value!=='string'||!value.trim())return null;
 const seconds=/^\d+$/.test(value)?Number(value):Math.ceil((Date.parse(value)-now)/1000);
 return Number.isFinite(seconds)?Math.max(1,Math.min(86400,seconds)):null;
}
function plan(url,method,headers,body,c){
 if(url.href.length>12000)fail(400,"Requête trop longue.");
 const suffix=url.pathname.slice(PREFIX.length),at=suffix.indexOf('/'),provider=suffix.slice(0,at),path=suffix.slice(at);if(at<1||!rules[provider])fail(404,'Service inconnu.');
 if(url.pathname.includes('%')||path.includes('..')||!rules[provider][0].test(path))fail(400,'Chemin non autorisé.');
 const params=new URLSearchParams();for(const [key,v]of url.searchParams){if(!rules[provider][1].has(key)||params.has(key)||v.length>2048||/[\u0000-\u001f\u007f]/.test(v))fail(400,'Paramètre non autorisé.');params.set(key,v);}
 const oauth=provider==='trakt-auth';if(oauth?method!=='POST':provider!=='trakt'&&method!=='GET')fail(405,'Méthode non autorisée.');
 if(provider==='trakt'&&method!=='GET'&&!((method==='POST'&&/^\/(sync\/(history|collection|watchlist|ratings)(\/remove)?|comments|checkin)$/.test(path))||(['PUT','DELETE'].includes(method)&&/^\/comments\/\d+$/.test(path))||(method==='DELETE'&&path==='/checkin')))fail(405,'Écriture Trakt non autorisée.');
 const outHeaders={'Accept':'application/json','User-Agent':'FranchiseGuide/0.27 (personal catalogue app)'};let outBody=method==='GET'?null:body;
 if(provider==='itunes'){params.set('entity','movie');if(path==='/search')params.set('media','movie');if(params.has('limit')&&(!/^\d+$/.test(params.get('limit'))||+params.get('limit')>200||+params.get('limit')<1))fail(400,'Limite Apple invalide.');}
 if(provider==='tvmaze'&&params.has('specials')&&params.get('specials')!=='1')fail(400,'Paramètre specials invalide.');
 if(provider==='tvmaze'&&params.has('embed')&&(params.get('embed')!=='show'||!/^\/(episodes\/[1-9]\d*|people\/[1-9]\d*\/(castcredits|crewcredits))$/.test(path)))fail(400,'Inclusion TVmaze non autorisée.');
 if(provider==='tmdb'){if(!status(c).tmdb)fail(503,'Accès TMDb non activé par l’éditeur.');params.delete('api_key');if(c.tmdbToken)outHeaders.Authorization='Bearer '+c.tmdbToken;else params.set('api_key',c.tmdb);}
 if(provider==='rawg'){if(!c.rawg)fail(503,'Accès RAWG non activé par l’éditeur.');params.set('key',c.rawg);}
 if(provider==='trakt'||oauth){if(!status(c).trakt)fail(503,'Connexion Trakt non activée par l’éditeur.');outHeaders['trakt-api-key']=c.traktId;outHeaders['trakt-api-version']='2';outHeaders['Content-Type']='application/json';
  const token=headers.authorization;if(!oauth&&token){if(!/^Bearer [a-zA-Z0-9._~-]{1,4096}$/.test(token))fail(400,'Jeton utilisateur invalide.');outHeaders.Authorization=token;}
  if(!oauth&&((method!=='GET')||path.startsWith('/sync/')||path==='/users/settings')&&!token)fail(401,'Connecte ton compte Trakt.');
  if(oauth){let x;try{x=JSON.parse(body);}catch{fail(400,'Corps OAuth invalide.');}const allowed=new Set(['client_id','client_secret','code','refresh_token','redirect_uri','grant_type']);if(!x||Array.isArray(x)||typeof x!=='object'||Object.entries(x).some(([k,v])=>!allowed.has(k)||typeof v!=='string'||v.length>4096||/[\u0000-\u001f\u007f]/.test(v)))fail(400,'Corps OAuth invalide.');
   if(path==='/oauth/token'&&(x.grant_type!=='refresh_token'||!x.refresh_token))fail(400,'Renouvellement attendu.');if(path==='/oauth/device/token'&&!x.code)fail(400,'Code appareil manquant.');
   x.client_id=c.traktId;if(path!=='/oauth/device/code')x.client_secret=c.traktSecret;else delete x.client_secret;outBody=JSON.stringify(x);
  }
 }
 return {provider,url:HOSTS[provider]+path+(params.size?'?'+params:''),method,headers:outHeaders,body:outBody};
}
async function readUpstream(p,fetcher=fetch){const r=await fetcher(p.url,{method:p.method,headers:p.headers,body:p.body||undefined,redirect:'error',signal:AbortSignal.timeout(25000)});const declared=r.headers.get('Content-Length');if(declared&&Number(declared)>LIMIT){await r.body?.cancel();fail(502,'Réponse trop volumineuse.');}
 const reader=r.body?.getReader(),parts=[];let n=0;if(reader)try{for(;;){const x=await reader.read();if(x.done)break;n+=x.value.byteLength;if(n>LIMIT)fail(502,'Réponse trop volumineuse.');parts.push(x.value);}}finally{await reader.cancel().catch(()=>{});}
 const body=new TextDecoder('utf-8',{fatal:true}).decode(Buffer.concat(parts));if(r.status!==204&&r.ok){try{JSON.parse(body);}catch{fail(502,'Réponse JSON invalide.');}}
 return {status:r.status,body,retryAfter:r.headers.get('Retry-After'),pages:r.headers.get('X-Pagination-Page-Count')};}
function createAutoServices({env=process.env,read=readUpstream,clock=Date.now}={}){const c=configuration(env),rates=new Map(),cooldown=new Map();let active=0;
 return async(req,res,url)=>{if(!url.pathname.startsWith(PREFIX))return false;const port=req.socket.localPort,host=req.headers.host;const json=(code,x,extra={})=>{res.writeHead(code,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer',...extra});res.end(typeof x==='string'?x:JSON.stringify(x));return true;};
 if(ambiguousHeaders(req)||!['127.0.0.1:'+port,'localhost:'+port].includes(host)||req.headers['x-franchiseguide-services']!=='1'||req.headers['sec-fetch-site']==='cross-site'||req.headers.origin&&req.headers.origin!=='http://'+host)return json(403,{error:'Application locale requise.'});
 if(url.pathname===PREFIX+'config')return req.method==='GET'&&!url.search?json(200,status(c)):json(405,{error:'GET sans paramètres attendu.'});
 try{let body='';if(!['GET','HEAD'].includes(req.method)){if(Number(req.headers['content-length'])>2*1024*1024)fail(413,'Requête trop grande.');let n=0;const parts=[];for await(const b of req){n+=b.length;if(n>2*1024*1024)fail(413,'Requête trop grande.');parts.push(b);}body=new TextDecoder('utf-8',{fatal:true}).decode(Buffer.concat(parts));}
  const p=plan(url,req.method,req.headers,body,c);const now=clock();
  const hold=cooldown.get(p.provider)||0;if(hold>now) return json(429,{error:'Délai fournisseur en cours.'},{'Retry-After':String(Math.ceil((hold-now)/1000))});
  const times=(rates.get(p.provider)||[]).filter(t=>now-t<60000);if(active>=4||times.length>=(p.provider==='itunes'?18:90))return json(429,{error:'Lecture limitée, réessaie après le délai.'},{'Retry-After':'60'});times.push(now);rates.set(p.provider,times);active++;
  try{const r=await read(p),receivedAt=clock(),wait=retrySeconds(r.retryAfter,receivedAt)??(r.status===429?60:null);let extra={};
   if(wait!==null){extra['Retry-After']=String(wait);if(r.status===429)cooldown.set(p.provider,Math.max(cooldown.get(p.provider)||0,receivedAt+wait*1000));}
   if(r.pages&&/^\d+$/.test(r.pages))extra['X-Pagination-Page-Count']=r.pages;
   if(r.status<200||r.status>=300)return json(r.status>=400?r.status:502,{error:'Service fournisseur temporairement indisponible.'},extra);
   if(typeof r.body!=='string'||Buffer.byteLength(r.body,'utf8')>LIMIT)fail(502,'Réponse invalide ou trop volumineuse.');let safe='';
   if(r.status!==204){try{safe=redactResponse(r.body,c);}catch{fail(502,'Réponse JSON invalide.');}}

   return json(r.status,safe,extra);
  }finally{active--;}
 }catch(e){return json(e.status||502,{error:e.status?e.message:'Lecture indisponible. Aucune donnée locale modifiée.'});}
 };
}
module.exports={createAutoServices,configuration,status,plan,readUpstream};
