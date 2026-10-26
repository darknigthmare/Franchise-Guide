/* Read-only Steam adapter. The secret exists only in the server process. No public proxy. */
'use strict';
const {ambiguousHeaders}=require('./local-request.cjs');
const ALLOWED_LANGUAGES=new Set(['english','french','german','spanish','italian','japanese','portuguese']);
function parameters(url){
 const q=url.searchParams,entries=[...q];if(url.search.length>300||entries.some(([k])=>!['steamid','appid','language'].includes(k))||new Set(entries.map(([k])=>k)).size!==entries.length)throw Error('Invalid parameters');
 const steamid=q.get('steamid'),appid=q.get('appid'),language=q.get('language')||'english';
 if(!/^\d{17}$/.test(steamid||'')||!(/^[1-9]\d{0,9}$/.test(appid||''))||Number(appid)>4294967295||!ALLOWED_LANGUAGES.has(language))throw Error('Invalid identifiers');return {steamid,appid,language};
}
async function steamRead(url,fetcher=fetch){
 const u=new URL(url);if(u.protocol!=='https:'||u.hostname!=='api.steampowered.com'||u.port||u.username||u.password||u.hash)throw Error('Disallowed upstream');
 const r=await fetcher(u,{redirect:'error',headers:{Accept:'application/json','User-Agent':'FranchiseGuide/0.16 (personal read-only tracker)'},signal:AbortSignal.timeout(14000)});if(!r.ok)throw Error('Upstream unavailable');
 if(!r.body)throw Error('Upstream unavailable');
 if(Number(r.headers.get('content-length'))>4*1024*1024){await r.body.cancel().catch(()=>{});throw Error('Response too large');}
 const reader=r.body.getReader();let size=0;const parts=[];try{for(;;){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>4*1024*1024)throw Error('Response too large');parts.push(value);}}finally{await reader.cancel().catch(()=>{});}
 return JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(Buffer.concat(parts)));
}
function createSteamBridge({key=process.env.FG_STEAM_API_KEY||'',read=steamRead,now=Date.now}={}){
 key=key.trim();const configured=/^[a-fA-F0-9]{32}$/.test(key),cache=new Map(),inflight=new Map();let recent=[],busy=0;
 async function data(p){const base='https://api.steampowered.com/ISteamUserStats/',common=new URLSearchParams({appid:p.appid,l:p.language,key});
  const schema=await read(base+'GetSchemaForGame/v2/?'+common);
  const definitions=schema?.game?.availableGameStats?.achievements;
  if(schema?.game?.gameName&&(!definitions||Array.isArray(definitions)&&!definitions.length))return {provider:'steam',appid:p.appid,steamid:p.steamid,fetchedAt:now(),noAchievements:true,schema,player:null,rarity:null};
  const player=await read(base+'GetPlayerAchievements/v1/?'+common+'&steamid='+p.steamid);let rarity=null;try{rarity=await read(base+'GetGlobalAchievementPercentagesForApp/v2/?gameid='+p.appid);}catch{}
  return {provider:'steam',appid:p.appid,steamid:p.steamid,fetchedAt:now(),schema,player,rarity};
 }
 async function lookup(params){
  const p=parameters(new URL('http://localhost/?'+new URLSearchParams(params)));
  if(!configured)throw Object.assign(Error('Steam key missing'),{status:503});
  const cacheKey=JSON.stringify(p),hit=cache.get(cacheKey);if(hit&&now()-hit.at<60000)return hit.value;
  if(inflight.has(cacheKey))return inflight.get(cacheKey);
  recent=recent.filter(t=>now()-t<60000);if(busy>=2||recent.length>=12)throw Object.assign(Error('Local rate limit'),{status:429});recent.push(now());busy++;
  const pending=data(p);inflight.set(cacheKey,pending);
  try{const value=await pending;cache.set(cacheKey,{at:now(),value});while(cache.size>32)cache.delete(cache.keys().next().value);return value;}
  finally{busy--;inflight.delete(cacheKey);}
 }
 const handle=async function handle(req,res,url){
  if(!url.pathname.startsWith('/api/gaming/'))return false;
  function send(code,body){res.writeHead(code,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff',...(code===429?{'Retry-After':'60'}:{})});res.end(req.method==='HEAD'?'':JSON.stringify(body).split(key||'__no_key__').join('[redacted]'));return true;}
  const host=req.headers.host,port=req.socket.localPort;
  if(ambiguousHeaders(req)||!['127.0.0.1:'+port,'localhost:'+port].includes(host))return send(403,{error:'Invalid host or ambiguous headers'});
  if(!['GET','HEAD'].includes(req.method))return send(405,{error:'Read only'});
  if(!['0.11','0.12'].includes(req.headers['x-franchiseguide-client'])||req.headers['sec-fetch-site']==='cross-site'||req.headers.origin&&req.headers.origin!=='http://'+host)return send(403,{error:'Same-origin application required'});
  if(url.pathname==='/api/gaming/status')return send(200,{app:'FranchiseGuide Gaming Bridge',version:'0.16.0',steam:configured,steamSignIn:true,writes:false});
  if(url.pathname!=='/api/gaming/steam')return send(404,{error:'Unknown endpoint'});if(req.method==='HEAD')return send(405,{error:'GET required'});
  let p;try{p=parameters(url);}catch{return send(400,{error:'Invalid parameters'});}if(!configured)return send(503,{error:'FG_STEAM_API_KEY is not configured on this local server'});
  try{return send(200,await lookup(p));}
  catch(e){return send(e.status===429?429:502,{error:e.status===429?'Local rate limit':'Steam unavailable, private, unauthorized or timed out. Existing local data is unchanged.'});}
 };
 handle.lookup=lookup;return handle;
}
module.exports={parameters,steamRead,createSteamBridge};
