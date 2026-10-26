/* Steam-only OpenID 2.0 RP for the personal loopback server.
 * No generic discovery, credentials, URLs or verified identities supplied by the client.
 * Verification: Steam HTTPS check_authentication + signed fields + cookie-bound return_to + nonce.
 */
'use strict';
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const {steamRead} = require('./steam-bridge.cjs');
const {ambiguousHeaders}=require('./local-request.cjs');
const ENDPOINT = 'https://steamcommunity.com/openid/login';
const NS = 'http://specs.openid.net/auth/2.0';
const PENDING_MS = 10 * 60 * 1000, SESSION_MS = 7 * 86400000;
const random = () => crypto.randomBytes(32).toString('hex');
const digest = s => crypto.createHash('sha256').update(s).digest('hex');
const same = (a,b) => typeof a === 'string' && typeof b === 'string' && a.length === b.length && crypto.timingSafeEqual(Buffer.from(a),Buffer.from(b));
function error(status,message){return Object.assign(new Error(message),{status});}
function strictQuery(raw,max=16384){
 if(raw.length>max)throw error(400,'Réponse trop longue.');
 const q=new URLSearchParams(raw),seen=new Set();
 for(const [k,v] of q){if(seen.has(k)||k.length>100||v.length>8192)throw error(400,'Paramètres en double ou invalides.');seen.add(k);}
 return q;
}
function cookies(header){const out={};for(const part of String(header||'').split(';')){const i=part.indexOf('=');if(i<0)continue;const k=part.slice(0,i).trim(),v=part.slice(i+1).trim();if(Object.hasOwn(out,k))out[k]='';else out[k]=v;}return out;}
function validateAssertion(q,expectedReturn,now){
 const get=k=>q.get('openid.'+k);
 if(get('ns')!==NS||get('mode')!=='id_res'||![ENDPOINT,'https://steamcommunity.com/openid/'].includes(get('op_endpoint')))throw error(400,'Réponse OpenID Steam invalide.');
 if(get('return_to')!==expectedReturn)throw error(400,'Adresse de retour différente.');
 const identity=get('claimed_id');
 if(identity!==get('identity')||!/^https:\/\/steamcommunity\.com\/openid\/id\/\d{17}$/.test(identity||''))throw error(400,'Identité Steam invalide.');
 const signed=(get('signed')||'').split(',');
 if(new Set(signed).size!==signed.length||!['op_endpoint','claimed_id','identity','return_to','response_nonce','assoc_handle'].every(k=>signed.includes(k))||!get('sig')||!get('assoc_handle'))throw error(400,'Signature OpenID incomplète.');
 const nonce=get('response_nonce')||'',match=nonce.match(/^(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ).+$/);
 const stamp=match&&Date.parse(match[1]);
 if(!match||!Number.isFinite(stamp)||new Date(stamp).toISOString().replace('.000Z','Z')!==match[1]||now-stamp>PENDING_MS||stamp-now>60000)throw error(400,'Réponse Steam expirée.');
 for(const [k] of q)if(k!=='state'&&!k.startsWith('openid.'))throw error(400,'Paramètre inattendu.');
 return {steamid:identity.split('/').pop(),nonce};
}
async function verifySteam(q,fetcher=fetch){
 const body=new URLSearchParams();for(const [k,v]of q)if(k.startsWith('openid.'))body.append(k,v);body.set('openid.mode','check_authentication');
 const r=await fetcher(ENDPOINT,{method:'POST',body,redirect:'error',headers:{'Content-Type':'application/x-www-form-urlencoded','Accept':'text/plain','User-Agent':'FranchiseGuide/0.16 (Steam OpenID)'},signal:AbortSignal.timeout(14000)});
 if(r.status!==200)throw error(502,'Validation Steam indisponible.');
 const reader=r.body.getReader(),parts=[];let size=0;
 try{for(;;){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>16384)throw error(502,'Réponse Steam trop longue.');parts.push(value);}}finally{await reader.cancel().catch(()=>{});}
 const lines=Buffer.concat(parts).toString('utf8').trim().split(/\r?\n/),values=new Map();
 for(const line of lines){const i=line.indexOf(':');if(i<=0||values.has(line.slice(0,i)))throw error(502,'Validation Steam malformée.');values.set(line.slice(0,i),line.slice(i+1));}
 if(values.get('is_valid')!=='true'||values.has('ns')&&values.get('ns')!==NS)throw error(401,'Steam a refusé la signature.');
 return true;
}
/* Opaque cookie tokens are never written to disk; only SHA-256 indexes are retained.
 * This is local session persistence, not encryption against an OS account owner.
 */
class SessionVault {
 constructor(file,now=Date.now){this.file=file;this.now=now;this.sessions=new Map();if(file&&fs.existsSync(file)){
  const st=fs.lstatSync(file);if(!st.isFile()||st.isSymbolicLink()||st.size>1024*1024)throw Error('Fichier de sessions non sûr.');
  const x=JSON.parse(fs.readFileSync(file,'utf8'));if(x.version!==1||!Array.isArray(x.sessions)||x.sessions.length>128)throw Error('Fichier de sessions invalide.');
  for(const [k,v]of x.sessions){if(!/^[a-f0-9]{64}$/.test(k)||!v||!/^\d{17}$/.test(v.steamid)||!Number.isSafeInteger(v.expiresAt))throw Error('Session stockée invalide.');if(v.expiresAt>now())this.sessions.set(k,v);}
 }}
 persist(next){if(this.file){fs.mkdirSync(path.dirname(this.file),{recursive:true,mode:0o700});const tmp=this.file+'.'+random()+'.tmp';let fd;try{fd=fs.openSync(tmp,'wx',0o600);fs.writeFileSync(fd,JSON.stringify({version:1,sessions:[...next]}));fs.fsyncSync(fd);fs.closeSync(fd);fd=null;fs.renameSync(tmp,this.file);}finally{if(fd!=null)fs.closeSync(fd);try{fs.unlinkSync(tmp);}catch{}}}this.sessions=next;}
 get(token){if(!/^[a-f0-9]{64}$/.test(token||''))return null;const value=this.sessions.get(digest(token));return value&&value.expiresAt>this.now()?value:null;}
 put(token,value,oldToken){const next=new Map([...this.sessions].filter(([,v])=>v.expiresAt>this.now()));if(oldToken)next.delete(digest(oldToken));if(next.size>=128)throw error(503,'Trop de sessions locales.');next.set(digest(token),value);this.persist(next);}
 remove(token){const next=new Map(this.sessions);if(token)next.delete(digest(token));this.persist(next);}
}
function createSteamAuth({key=process.env.FG_STEAM_API_KEY||'',read=steamRead,verify=verifySteam,now=Date.now,vaultFile=path.join(os.homedir(),'.franchiseguide','steam-sessions-web.json'),lookup}={}){
 key=key.trim();const configured=/^[a-fA-F0-9]{32}$/.test(key),pending=new Map(),cancelled=new Map(),nonces=new Map(),cache=new Map(),inflight=new Map();let vault,unavailable=false,starts=[],reads=[];
 try{vault=new SessionVault(vaultFile,now);}catch{unavailable=true;}
 function clean(){for(const [k,at]of cancelled)if(now()-at>PENDING_MS)cancelled.delete(k);for(const [k,p]of pending)if(p.expiresAt<=now())pending.delete(k);for(const [k,at]of nonces)if(now()-at>PENDING_MS)nonces.delete(k);starts=starts.filter(t=>now()-t<PENDING_MS);reads=reads.filter(t=>now()-t<60000);}
 return async function handle(req,res,url){
  const route=url.pathname;
  if(!route.startsWith('/api/gaming/auth/steam/')&&!['/api/gaming/steam/library','/api/gaming/steam/achievements'].includes(route))return false;
  const host=req.headers.host,port=req.socket.localPort,origin='http://'+host;
  const sessionName='FGSteamSession'+port,pendingName='FGSteamPending'+port,jar=cookies(req.headers.cookie);
  const cookie=(name,value,seconds)=>`${name}=${value}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${seconds}`;
  function send(status,body,headers={}){
   if(status>=400&&route.endsWith('/callback')&&String(req.headers.accept||'').includes('text/html')){
    const html='<!doctype html><html lang="fr"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Connexion Steam interrompue</title><style>body{font:18px system-ui;max-width:42rem;margin:10vh auto;padding:24px;line-height:1.6}a{display:inline-block;padding:14px;background:#6750a4;color:white;border-radius:12px}</style><h1>Connexion Steam non confirmée</h1><p>La réponse a été refusée, a expiré ou le service est indisponible. Aucun compte n’a été connecté par cette tentative et ta bibliothèque reste intacte.</p><p>Code HTTP : '+status+'</p><a href="/#games">Revenir à FranchiseGuide et réessayer</a></html>';
    res.writeHead(status,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff','X-Frame-Options':'DENY','Referrer-Policy':'no-referrer','Content-Security-Policy':"default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'"});res.end(html);return true;
   }
   res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','Referrer-Policy':'no-referrer','X-Content-Type-Options':'nosniff','X-Frame-Options':'DENY',...headers});res.end(JSON.stringify(body).split(key||'__no_key__').join('[redacted]'));return true;}
  function redirect(location,cookieHeaders){res.writeHead(303,{'Location':location,'Set-Cookie':cookieHeaders,'Cache-Control':'no-store','Referrer-Policy':'no-referrer'});res.end();return true;}
  try{
   clean();if(ambiguousHeaders(req)||!['127.0.0.1:'+port,'localhost:'+port].includes(host))throw error(403,'Origine locale non autorisée.');
   if(!['/api/gaming/auth/steam/begin','/api/gaming/auth/steam/callback','/api/gaming/auth/steam/session','/api/gaming/auth/steam/disconnect','/api/gaming/steam/library','/api/gaming/steam/achievements'].includes(route))throw error(404,'Route inconnue.');
   const callback=route.endsWith('/callback');
   if(!callback&&(req.headers['x-franchiseguide-client']!=='0.12'||req.headers['sec-fetch-site']==='cross-site'||req.headers.origin&&req.headers.origin!==origin))throw error(403,'Requête de l’application locale requise.');
   if(unavailable)throw error(503,'Le fichier local des sessions ne peut pas être ouvert. Aucun remplacement automatique.');
   if(route.endsWith('/begin')){
    if(req.method!=='POST'||url.search)throw error(405,'POST requis.');if(!configured)throw error(503,'La clé Steam doit être configurée une fois sur le serveur (FG_STEAM_API_KEY).');
    if(starts.length>=8||pending.size>=64)throw error(429,'Trop de tentatives de connexion.');starts.push(now());
    const state=random(),binding=random(),returnTo=origin+'/api/gaming/auth/steam/callback?state='+state;
    pending.set(state,{binding,returnTo,expiresAt:now()+PENDING_MS});
    const q=new URLSearchParams({'openid.ns':NS,'openid.mode':'checkid_setup','openid.return_to':returnTo,'openid.realm':origin+'/','openid.identity':NS+'/identifier_select','openid.claimed_id':NS+'/identifier_select'});
    return send(200,{authorizeUrl:ENDPOINT+'?'+q,expiresAt:now()+PENDING_MS},{'Set-Cookie':cookie(pendingName,binding,600)});
   }
   if(callback){
    if(req.method!=='GET')throw error(405,'Retour GET requis.');const q=strictQuery(url.search),state=q.get('state'),p=pending.get(state);
    if(!p||p.expiresAt<=now()||!same(jar[pendingName],p.binding))throw error(401,'Connexion absente, expirée ou provenant d’un autre navigateur.');
    pending.delete(state); // Every cookie-bound attempt is single-use, including failures and cancellation.
    if(q.get('openid.mode')==='cancel')return redirect('/?steam_cancelled=1',[cookie(pendingName,'',0)]);
    const a=validateAssertion(q,p.returnTo,now());if(nonces.has(a.nonce))throw error(401,'Réponse Steam déjà utilisée.');
    nonces.set(a.nonce,now()); // Reserve before the asynchronous verification, preventing concurrent replay.
    if(await verify(q)!==true)throw error(401,'Validation Steam refusée.');
    if(cancelled.has(p.binding)||p.expiresAt<=now())throw error(401,'Connexion annulée pendant la vérification.');
    const token=random();vault.put(token,{steamid:a.steamid,expiresAt:now()+SESSION_MS},jar[sessionName]);
    return redirect('/?steam_connected=1',[cookie(sessionName,token,SESSION_MS/1000),cookie(pendingName,'',0)]);
   }
   if(route.endsWith('/disconnect')){
    if(req.method!=='POST'||url.search)throw error(405,'POST requis.');vault.remove(jar[sessionName]);const binder=jar[pendingName];if(binder)cancelled.set(binder,now());for(const [s,p]of pending)if(same(p.binding,binder))pending.delete(s);
    cache.clear();return send(200,{authenticated:false},{'Set-Cookie':[cookie(sessionName,'',0),cookie(pendingName,'',0)]});
   }
   if(req.method!=='GET')throw error(405,'GET requis.');const session=vault.get(jar[sessionName]);
   if(route.endsWith('/session')){if(url.search)throw error(400,'Aucun paramètre attendu.');return send(200,session?{authenticated:true,...session}:{authenticated:false});}
   if(!session)throw error(401,'Reconnecte ton compte Steam.');if(!configured)throw error(503,'Clé Steam non configurée côté serveur.');
   const q=strictQuery(url.search,300);if([...q].some(([k])=>!['appid','language'].includes(k)))throw error(400,'Paramètre interdit.');
   if(route.endsWith('/achievements')){
    if(!lookup)throw error(503,'Passerelle de succès indisponible.');
    const params={steamid:session.steamid,appid:q.get('appid'),language:q.get('language')||'english'};
    const value=await lookup(params);if(!vault.get(jar[sessionName]))throw error(401,'Session terminée pendant la lecture.');return send(200,value);
   }
   if(q.has('appid'))throw error(400,'La bibliothèque ne prend pas d’App ID.');
   const cacheKey=session.steamid,hit=cache.get(cacheKey);if(hit&&now()-hit.at<60000)return send(200,hit.data);
   if(!inflight.has(cacheKey)){
    if(reads.length>=6||inflight.size>=2)throw error(429,'Limite locale de lecture.');reads.push(now());
    inflight.set(cacheKey,(async()=>{const qs=new URLSearchParams({key,steamid:session.steamid,include_appinfo:'true',include_played_free_games:'true'});
     const library=await read('https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/?'+qs);let profile=null;
     try{profile=await read('https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v2/?'+new URLSearchParams({key,steamids:session.steamid}));}catch{}
     return {provider:'steam',steamid:session.steamid,fetchedAt:now(),library,profile};
    })());
   }
   try{const data=await inflight.get(cacheKey);if(!vault.get(jar[sessionName]))throw error(401,'Session terminée pendant la lecture.');cache.set(cacheKey,{at:now(),data});while(cache.size>8)cache.delete(cache.keys().next().value);return send(200,data);}finally{inflight.delete(cacheKey);}
  }catch(e){const status=Number(e.status)||502;return send(status,{error:e.status?e.message:'Steam indisponible ou sauvegarde locale refusée. Les données existantes sont conservées.'},status===429?{'Retry-After':'60'}:{});}
 };
}
module.exports={createSteamAuth,validateAssertion,strictQuery,verifySteam,SessionVault,ENDPOINT,NS};
