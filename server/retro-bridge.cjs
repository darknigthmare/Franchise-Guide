/* Public RetroAchievements profile reads. This is not account authentication or a public proxy. */
'use strict';
const MAX_BODY=4*1024*1024,MAX_CACHE_BYTES=8*1024*1024,CACHE_MS=60000;
const PREFIX='/api/gaming/retro/';
const ULID=/^[0-7][0-9A-HJKMNP-TV-Z]{25}$/i,USERNAME=/^[A-Za-z0-9_]{2,32}$/;
const UPSTREAM_PATHS=new Set(['/API/API_GetUserProfile.php','/API/API_GetUserCompletionProgress.php','/API/API_GetGameInfoAndUserProgress.php']);
class Failure extends Error { constructor(status,message){super(message);this.status=status;} }
const invalid=()=>{throw new Error('Invalid or incomplete RetroAchievements response');};
const object=v=>v!==null&&typeof v==='object'&&!Array.isArray(v);
function integer(v,min=0,max=2147483647){
 if(typeof v==='string'&&/^(0|[1-9]\d*)$/.test(v))v=Number(v);
 return typeof v==='number'&&Number.isSafeInteger(v)&&v>=min&&v<=max?v:null;
}
function parameters(url){
 const route=url.pathname.slice(PREFIX.length),q=url.searchParams,entries=[...q];
 if(!url.pathname.startsWith(PREFIX)||!['status','profile','library','achievements'].includes(route))throw new Failure(404,'Unknown endpoint');
 const allowed={status:[],profile:['user'],library:['user','offset','count'],achievements:['user','gameId']}[route];
 if(url.search.length>300||entries.some(([k])=>!allowed.includes(k))||new Set(entries.map(([k])=>k)).size!==entries.length||url.search&&url.search.slice(1).split('&').some(p=>!p.includes('=')))throw new Failure(400,'Invalid parameters');
 if(route==='status')return {route};
 let user=q.get('user');if(typeof user!=='string'||!(ULID.test(user)||route==='profile'&&USERNAME.test(user)))throw new Failure(400,'Invalid user identifier');
 if(ULID.test(user))user=user.toUpperCase();
 const p={route,user};
 if(route==='library'){
  const offset=integer(q.get('offset')??'0',0,100000),count=integer(q.get('count')??'100',1,500);
  if(offset===null||count===null)throw new Failure(400,'Invalid pagination');Object.assign(p,{offset,count});
 }
 if(route==='achievements'){
  const gameId=integer(q.get('gameId'),1);if(gameId===null)throw new Failure(400,'Invalid game identifier');p.gameId=String(gameId);
 }
 return p;
}
/* JSON.parse validates grammar; this second scan also rejects duplicate object keys
   and bounds nesting before any recursive processing. Escaped keys are decoded. */
function parseJson(raw){
 if(typeof raw!=='string'||Buffer.byteLength(raw)>MAX_BODY)throw Error('Response too large');
 const stack=[];
 for(let i=0;i<raw.length;i++){
  const c=raw[i];
  if(c==='"'){
   const start=i;for(i++;i<raw.length;i++){if(raw[i]==='\\'){i++;continue;}if(raw[i]==='"')break;}
   if(i>=raw.length)throw Error('Invalid JSON');
   let next=i+1;while(next<raw.length&&/\s/.test(raw[next]))next++;
   if(raw[next]===':'){
    const keys=stack[stack.length-1];if(!(keys instanceof Set))throw Error('Invalid JSON');
    const key=JSON.parse(raw.slice(start,i+1));if(keys.has(key))throw Error('Duplicate JSON key');keys.add(key);
   }
  }else if(c==='{'||c==='['){stack.push(c==='{'?new Set():null);if(stack.length>32)throw Error('JSON too deep');}
  else if(c==='}'||c===']')stack.pop();
 }
 const value=JSON.parse(raw);let nodes=0;
 const visit=(v,depth)=>{if(++nodes>100000||depth>32)throw Error('JSON too complex');if(typeof v==='number'&&!Number.isFinite(v))throw Error('Invalid JSON number');if(v&&typeof v==='object')for(const child of Object.values(v))visit(child,depth+1);};
 visit(value,0);return value;
}
async function retroRead(url,fetcher=fetch){
 const u=new URL(url);
 if(u.protocol!=='https:'||u.hostname!=='retroachievements.org'||u.port||u.username||u.password||u.hash||!UPSTREAM_PATHS.has(u.pathname))throw Error('Disallowed upstream');
 const r=await fetcher(u,{method:'GET',redirect:'error',headers:{Accept:'application/json','User-Agent':'FranchiseGuide/0.16 (personal read-only tracker)'},signal:AbortSignal.timeout(14000)});
 if(r.status===429)throw new Failure(429,'RetroAchievements rate limit');
 if(!r.ok||!r.body)throw Error('Upstream unavailable');
 if(Number(r.headers.get('content-length'))>MAX_BODY){await r.body.cancel().catch(()=>{});throw Error('Response too large');}
 const reader=r.body.getReader(),parts=[];let size=0;
 try{for(;;){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>MAX_BODY)throw Error('Response too large');parts.push(value);}}
 finally{await reader.cancel().catch(()=>{});}
 return parseJson(new TextDecoder('utf-8',{fatal:true}).decode(Buffer.concat(parts)));
}
function validateProfile(profile,user){
 if(!object(profile)||typeof profile.User!=='string'||!USERNAME.test(profile.User)||typeof profile.ULID!=='string'||!ULID.test(profile.ULID))invalid();
 const expected=ULID.test(user)?profile.ULID:profile.User;if(expected.toUpperCase()!==user.toUpperCase())invalid();
 return profile;
}
function validateLibrary(library,p){
 if(!object(library)||!Array.isArray(library.Results))invalid();
 const count=integer(library.Count,0,500),total=integer(library.Total,0,1000000);
 if(count===null||total===null||count!==library.Results.length||count!==Math.min(p.count,Math.max(0,total-p.offset)))invalid();
 const ids=new Set();
 for(const game of library.Results){
  const id=object(game)?integer(game.GameID,1):null;
  if(id===null||ids.has(id)||typeof game.Title!=='string'||!game.Title.trim()||game.Title.length>1000)invalid();ids.add(id);
  const max=game.MaxPossible===undefined?2147483647:integer(game.MaxPossible);
  if(max===null)invalid();
  for(const field of ['NumAwarded','NumAwardedHardcore'])if(game[field]!==undefined&&integer(game[field],0,max)===null)invalid();
  if(game.NumAwarded!==undefined&&game.NumAwardedHardcore!==undefined&&Number(game.NumAwardedHardcore)>Number(game.NumAwarded))invalid();
 }
 return library;
}
function validateGame(game,p){
 if(!object(game)||integer(game.ID,1)!==Number(p.gameId)||typeof game.Title!=='string'||!game.Title.trim()||game.Title.length>1000)invalid();
 const count=integer(game.NumAchievements,0,50000),defs=game.Achievements;
 if(count===null||!(object(defs)||Array.isArray(defs)&&defs.length===0&&count===0))invalid();
 const entries=Object.entries(defs);if(entries.length!==count)invalid();
 for(const [id,entry] of entries){
  if(integer(id,1)===null||!object(entry)||integer(entry.ID,1)!==Number(id)||typeof entry.Title!=='string'||!entry.Title.trim()||entry.Title.length>1000)invalid();
  if(entry.Points!==undefined&&integer(entry.Points)===null||entry.Description!==undefined&&typeof entry.Description!=='string')invalid();
  for(const field of ['NumAwarded','NumAwardedHardcore'])if(entry[field]!==undefined&&integer(entry[field])===null)invalid();
  if(entry.NumAwarded!==undefined&&entry.NumAwardedHardcore!==undefined&&Number(entry.NumAwardedHardcore)>Number(entry.NumAwarded))invalid();
 }
 for(const field of ['NumAwardedToUser','NumAwardedToUserHardcore'])if(game[field]!==undefined&&integer(game[field],0,count)===null)invalid();
 if(game.NumAwardedToUser!==undefined&&game.NumAwardedToUserHardcore!==undefined&&Number(game.NumAwardedToUserHardcore)>Number(game.NumAwardedToUser))invalid();
 return game;
}
function createRetroBridge({key=process.env.FG_RETRO_API_KEY||'',read=retroRead,now=Date.now}={}){
 key=typeof key==='string'?key.trim():'';const configured=/^[A-Za-z0-9_-]{16,128}$/.test(key);
 const cache=new Map(),inflight=new Map();let cacheBytes=0,busy=0,waiters=0,blockedUntil=0,recent=[];
 function get(cKey){const hit=cache.get(cKey);if(!hit)return null;if(now()-hit.at>=CACHE_MS||now()<hit.at){cache.delete(cKey);cacheBytes-=hit.bytes;return null;}return JSON.parse(hit.json);}
 function remember(cKey,value,at=now()){
  // Canonical JSON removes alternate encodings of an echoed key before redaction.
  const json=JSON.stringify(value).split(key||'__no_key__').join('[redacted]'),bytes=Buffer.byteLength(json);
  if(bytes>MAX_BODY)throw Error('Response too large');
  const old=cache.get(cKey);if(old){cacheBytes-=old.bytes;cache.delete(cKey);}
  cache.set(cKey,{at,json,bytes});cacheBytes+=bytes;
  while(cache.size>32||cacheBytes>MAX_CACHE_BYTES){const first=cache.keys().next().value;cacheBytes-=cache.get(first).bytes;cache.delete(first);}
  return JSON.parse(json);
 }
 async function cachedRead(cKey,path,query,validate){
  const hit=get(cKey);if(hit)return hit;
  if(inflight.has(cKey)){
   if(waiters>=16)throw new Failure(429,'Local read limit');waiters++;
   try{return await inflight.get(cKey);}finally{waiters--;}
  }
  recent=recent.filter(t=>now()-t<CACHE_MS&&now()>=t);
  if(busy>=2||recent.length>=36||now()<blockedUntil)throw new Failure(429,'Local read limit');
  recent.push(now());busy++;
  const pending=(async()=>{
   const upstream=new URL('https://retroachievements.org'+path);upstream.search=new URLSearchParams({...query,y:key}).toString();
   let raw;try{raw=await read(upstream.toString());}catch(e){if(e.status===429)blockedUntil=now()+60000;throw e;}
   const value=typeof raw==='string'?parseJson(raw):parseJson(JSON.stringify(raw));
   if(object(value)&&(value.Success===false||Object.hasOwn(value,'Error')||Object.hasOwn(value,'error')))invalid();
   validate(value);return remember(cKey,value);
  })();
  inflight.set(cKey,pending);
  try{return await pending;}finally{busy--;inflight.delete(cKey);}
 }
 async function profile(user){
  const pKey='profile:'+user.toUpperCase();
  const value=await cachedRead(pKey,'/API/API_GetUserProfile.php',{u:user},v=>validateProfile(v,user));
  // Usernames can change. All further reads are addressed by the returned ULID.
  const canonical='profile:'+value.ULID.toUpperCase(),source=cache.get(pKey);
  if(canonical!==pKey&&source)remember(canonical,value,source.at);return value;
 }
 async function lookup(p){
  // Revalidate direct internal calls as well as browser query strings.
  const route=p.route,query={...p};delete query.route;
  p=parameters(new URL('http://localhost'+PREFIX+route+'?'+new URLSearchParams(query)));
  if(p.route==='status')return {provider:'retro',configured};
  if(!configured)throw new Failure(503,'RetroAchievements key missing');
  const verified=await profile(p.user),user=verified.ULID.toUpperCase();
  if(p.route==='profile')return {provider:'retro',user,profile:verified,fetchedAt:cache.get('profile:'+user)?.at??now()};
  if(user!==p.user)invalid();
  if(p.route==='library'){
   const cKey='library:'+user+':'+p.offset+':'+p.count;
   const library=await cachedRead(cKey,'/API/API_GetUserCompletionProgress.php',{u:user,c:String(p.count),o:String(p.offset)},v=>validateLibrary(v,p));
   return {provider:'retro',user,library,fetchedAt:cache.get(cKey)?.at??now()};
  }
  const cKey='game:'+user+':'+p.gameId;
  const game=await cachedRead(cKey,'/API/API_GetGameInfoAndUserProgress.php',{u:user,g:p.gameId,a:'1'},v=>validateGame(v,p));
  return {provider:'retro',user,gameId:p.gameId,game,fetchedAt:cache.get(cKey)?.at??now()};
 }
 const handle=async function handle(req,res,url){
  if(!url.pathname.startsWith(PREFIX))return false;
  function send(code,body){
   const headers={'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff','X-Frame-Options':'DENY','Referrer-Policy':'no-referrer',...(code===429?{'Retry-After':'60'}:{})};
   res.writeHead(code,headers);res.end(req.method==='HEAD'?'':JSON.stringify(body).split(key||'__no_key__').join('[redacted]'));return true;
  }
  const host=req.headers.host,port=req.socket.localPort;
  const headerCounts=new Map();for(let i=0;i<req.rawHeaders.length;i+=2){const name=req.rawHeaders[i].toLowerCase();headerCounts.set(name,(headerCounts.get(name)||0)+1);}
  if(['host','origin','sec-fetch-site','x-franchiseguide-client'].some(name=>(headerCounts.get(name)||0)>1))return send(403,{error:'Ambiguous request headers'});
  if(!['127.0.0.1:'+port,'localhost:'+port].includes(host))return send(403,{error:'Invalid host'});
  if(!['GET','HEAD'].includes(req.method)){res.setHeader('Allow','GET, HEAD');return send(405,{error:'Read only'});}
  if(!['0.12','0.13','0.14'].includes(req.headers['x-franchiseguide-client'])||req.headers['sec-fetch-site']==='cross-site'||req.headers.origin&&req.headers.origin!=='http://'+host)return send(403,{error:'Same-origin application required'});
  let p;try{p=parameters(url);}catch(e){return send(e.status===404?404:400,{error:e.status===404?'Unknown endpoint':'Invalid parameters'});}
  if(p.route==='status')return send(200,{provider:'retro',configured});
  if(req.method==='HEAD'){res.setHeader('Allow','GET');return send(405,{error:'GET required'});}
  if(!configured)return send(503,{error:'FG_RETRO_API_KEY is not configured on this local server'});
  try{return send(200,await lookup(p));}
  catch(e){return send(e.status===429?429:502,{error:e.status===429?'Local or RetroAchievements read limit. Try again later.':'RetroAchievements unavailable, profile inaccessible, response incomplete or timed out. Existing local data is unchanged.'});}
 };
 handle.lookup=lookup;return handle;
}
module.exports={createRetroBridge,retroRead,parameters,parseJson,validateProfile,validateLibrary,validateGame};
