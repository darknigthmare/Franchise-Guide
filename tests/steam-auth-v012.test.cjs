'use strict';
/* Real loopback HTTP, with an injected Steam verifier/reader. No real account authentication. */
const test=require('node:test'),assert=require('node:assert/strict'),http=require('node:http'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {createSteamAuth,validateAssertion,verifySteam,SessionVault,NS,ENDPOINT}=require('../server/steam-auth.cjs');
const {createSteamBridge}=require('../server/steam-bridge.cjs');
const ID='76561198000000001',KEY='1'.repeat(32),NOW=Date.UTC(2026,9,5,12,0,0);
function assertion(returnTo,nonce='n1'){
 return new URLSearchParams({state:new URL(returnTo).searchParams.get('state'),'openid.ns':NS,'openid.mode':'id_res','openid.op_endpoint':ENDPOINT,'openid.claimed_id':'https://steamcommunity.com/openid/id/'+ID,'openid.identity':'https://steamcommunity.com/openid/id/'+ID,'openid.return_to':returnTo,'openid.response_nonce':new Date(NOW).toISOString().replace('.000Z','Z')+nonce,'openid.assoc_handle':'fixture-association','openid.signed':'op_endpoint,claimed_id,identity,return_to,response_nonce,assoc_handle','openid.sig':'fixture-signature-not-real'});
}
async function fixture(options={}){
 const calls=[],read=async raw=>{const u=new URL(raw);calls.push(u);assert.equal(u.hostname,'api.steampowered.com');if(u.pathname.includes('GetOwnedGames'))return {response:{game_count:1,games:[{appid:10,name:'Jeu de test',playtime_forever:100,has_community_visible_stats:true}]}};if(u.pathname.includes('GetPlayerSummaries'))return {response:{players:[{steamid:ID,personaname:'Compte de test'}]}};if(u.pathname.includes('GetSchemaForGame'))return {game:{gameName:'Jeu de test',availableGameStats:{achievements:[{name:'A',displayName:'A'}]}}};if(u.pathname.includes('GetPlayerAchievements'))return {playerstats:{steamID:ID,success:true,achievements:[{apiname:'A',achieved:1,unlocktime:100}]}};return {achievementpercentages:{achievements:[]}};};
 let verifies=0;const bridge=createSteamBridge({key:KEY,read,now:()=>NOW});
 const auth=createSteamAuth({key:KEY,read,now:()=>NOW,vaultFile:null,lookup:bridge.lookup,verify:async q=>{verifies++;return true;},...options});
 const server=http.createServer(async(req,res)=>{const u=new URL(req.url,'http://'+req.headers.host);if(!await auth(req,res,u))if(!await bridge(req,res,u)){res.writeHead(404);res.end();}});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));const port=server.address().port,base='http://127.0.0.1:'+port;
 const request=(route,opt={})=>fetch(base+route,{redirect:'manual',...opt,headers:{'X-FranchiseGuide-Client':'0.12',...opt.headers}});
 const begin=async()=>{const r=await request('/api/gaming/auth/steam/begin',{method:'POST'});assert.equal(r.status,200);const body=await r.json(),u=new URL(body.authorizeUrl);return {cookie:r.headers.getSetCookie()[0].split(';')[0],returnTo:u.searchParams.get('openid.return_to'),url:u};};
 const login=async(nonce='n1',alter=()=>{})=>{const b=await begin(),q=assertion(b.returnTo,nonce);alter(q);const response=await request('/api/gaming/auth/steam/callback?'+q,{headers:{Cookie:b.cookie}});return {response,cookie:response.headers.getSetCookie().find(s=>s.startsWith('FGSteamSession'))?.split(';')[0],b,q};};
 return {base,port,request,begin,login,calls,verifies:()=>verifies,close:()=>new Promise(r=>server.close(r))};
}
async function withFixture(fn,options){const f=await fixture(options);try{await fn(f);}finally{await f.close();}}
const rt='http://127.0.0.1:47632/api/gaming/auth/steam/callback?state=fixture';
test('correct signed assertion identifies SteamID, not display name',()=>assert.equal(validateAssertion(assertion(rt),rt,NOW).steamid,ID));
for(const [name,mutate]of [
 ['wrong provider',q=>q.set('openid.op_endpoint','https://attacker.example/openid')],
 ['different identity',q=>q.set('openid.identity','https://steamcommunity.com/openid/id/76561198000000002')],
 ['HTTP identity',q=>q.set('openid.claimed_id','http://steamcommunity.com/openid/id/'+ID)],
 ['identity query suffix',q=>{q.set('openid.claimed_id',q.get('openid.identity')+'?x');q.set('openid.identity',q.get('openid.claimed_id'));}],
 ['unsigned return URL',q=>q.set('openid.signed','op_endpoint,claimed_id,identity,response_nonce,assoc_handle')],
 ['duplicate signed field',q=>q.set('openid.signed',q.get('openid.signed')+',identity')],
 ['wrong namespace',q=>q.set('openid.ns','evil')],
 ['missing signature',q=>q.delete('openid.sig')],
 ['expired nonce',q=>q.set('openid.response_nonce','2026-10-05T11:40:00Zn')],
 ['future nonce',q=>q.set('openid.response_nonce','2026-10-05T12:10:00Zn')],
 ['invalid calendar nonce',q=>q.set('openid.response_nonce','2026-02-30T12:00:00Zn')],
 ['open redirect parameter',q=>q.set('next','https://attacker.example')],
 ['wrong return address',q=>q.set('openid.return_to','http://attacker.example')]
])test('reject '+name,()=>{const q=assertion(rt);mutate(q);assert.throws(()=>validateAssertion(q,rt,NOW));});
test('verification always POSTs to fixed Steam endpoint with check_authentication',async()=>{
 const q=assertion(rt);assert.equal(await verifySteam(q,async(url,opt)=>{assert.equal(url,ENDPOINT);assert.equal(opt.method,'POST');assert.equal(opt.redirect,'error');assert.equal(opt.body.get('openid.mode'),'check_authentication');assert.equal(opt.body.has('state'),false);return new Response('ns:'+NS+'\nis_valid:true\n');}),true);
});
for(const body of ['is_valid:false\n','is_valid:true\nis_valid:false\n','ns:evil\nis_valid:true\n','invalid'])test('reject bad verifier response '+JSON.stringify(body),async()=>assert.rejects(()=>verifySteam(assertion(rt),async()=>new Response(body))));
test('verifier response size bounded',async()=>assert.rejects(()=>verifySteam(assertion(rt),async()=>new Response('x'.repeat(17000)))));
test('complete loopback login, library read and logout',()=>withFixture(async f=>{
 const {response,cookie}=await f.login();assert.equal(response.status,303);assert.equal(response.headers.get('location'),'/?steam_connected=1');assert.ok(cookie);const headers={Cookie:cookie};
 assert.match(response.headers.getSetCookie()[0],/HttpOnly; SameSite=Lax/);assert.match(response.headers.getSetCookie()[0],/Max-Age=604800/);
 const session=await (await f.request('/api/gaming/auth/steam/session',{headers})).json();assert.equal(session.authenticated,true);assert.equal(session.steamid,ID);assert.equal(session.expiresAt,NOW+7*86400000);
 const library=await (await f.request('/api/gaming/steam/library',{headers})).json();assert.equal(library.steamid,ID);assert.equal(library.library.response.game_count,1);assert.equal(library.profile.response.players[0].steamid,ID);assert.equal(JSON.stringify(library).includes(KEY),false);
 const achievements=await (await f.request('/api/gaming/steam/achievements?appid=10',{headers})).json();assert.equal(achievements.steamid,ID);assert.equal(achievements.player.playerstats.achievements[0].achieved,1);
 const logout=await f.request('/api/gaming/auth/steam/disconnect',{method:'POST',headers});assert.equal(logout.status,200);assert.equal((await (await f.request('/api/gaming/auth/steam/session',{headers})).json()).authenticated,false);
 assert.equal((await f.request('/api/gaming/steam/library',{headers})).status,401);
}));
test('no cookie cannot consume a pending login',()=>withFixture(async f=>{const b=await f.begin();const q=assertion(b.returnTo);assert.equal((await f.request('/api/gaming/auth/steam/callback?'+q)).status,401);assert.equal(f.verifies(),0);assert.equal((await f.request('/api/gaming/auth/steam/callback?'+q,{headers:{Cookie:b.cookie}})).status,303);}));
test('wrong binding cookie is refused',()=>withFixture(async f=>{const b=await f.begin();const q=assertion(b.returnTo);assert.equal((await f.request('/api/gaming/auth/steam/callback?'+q,{headers:{Cookie:'FGSteamPending'+f.port+'='+'a'.repeat(64)}})).status,401);assert.equal(f.verifies(),0);}));
test('callback can only be consumed once',()=>withFixture(async f=>{const x=await f.login();assert.equal(x.response.status,303);assert.equal((await f.request('/api/gaming/auth/steam/callback?'+x.q,{headers:{Cookie:x.b.cookie}})).status,401);assert.equal(f.verifies(),1);}));
test('nonce cannot be replayed on a second login',()=>withFixture(async f=>{assert.equal((await f.login('same')).response.status,303);assert.equal((await f.login('same')).response.status,401);assert.equal(f.verifies(),1);}));
test('duplicate callback parameters rejected before Steam verification',()=>withFixture(async f=>{const b=await f.begin(),q=assertion(b.returnTo);q.append('openid.identity',q.get('openid.identity'));assert.equal((await f.request('/api/gaming/auth/steam/callback?'+q,{headers:{Cookie:b.cookie}})).status,400);assert.equal(f.verifies(),0);}));
test('Steam cancellation never creates a session',()=>withFixture(async f=>{const b=await f.begin(),q=new URL(b.returnTo).searchParams;q.set('openid.mode','cancel');const r=await f.request('/api/gaming/auth/steam/callback?'+q,{headers:{Cookie:b.cookie}});assert.equal(r.status,303);assert.equal(r.headers.get('location'),'/?steam_cancelled=1');assert.equal(f.verifies(),0);}));
test('failed upstream verification never creates a session',()=>withFixture(async f=>{const x=await f.login();assert.equal(x.response.status,401);assert.equal(x.cookie,undefined);},{verify:async()=>false}));
test('upstream network failure never creates a session',()=>withFixture(async f=>{const x=await f.login();assert.equal(x.response.status,502);assert.equal(x.cookie,undefined);},{verify:async()=>{throw Error('network');}}));
test('logout cancels an in-flight OpenID verification',async()=>{let release,entered;const entry=new Promise(r=>entered=r),hold=new Promise(r=>release=r);await withFixture(async f=>{const b=await f.begin(),q=assertion(b.returnTo);const p=f.request('/api/gaming/auth/steam/callback?'+q,{headers:{Cookie:b.cookie}});await entry;await f.request('/api/gaming/auth/steam/disconnect',{method:'POST',headers:{Cookie:b.cookie}});release(true);assert.equal((await p).status,401);},{verify:async()=>{entered();return hold;}});});
for(const endpoint of ['/api/gaming/auth/steam/begin','/api/gaming/auth/steam/disconnect'])test('cross-origin POST refused '+endpoint,()=>withFixture(async f=>assert.equal((await f.request(endpoint,{method:'POST',headers:{Origin:'https://attacker.example'}})).status,403)));
test('missing application header cannot start login',()=>withFixture(async f=>assert.equal((await fetch(f.base+'/api/gaming/auth/steam/begin',{method:'POST'})).status,403)));
test('GET cannot start login',()=>withFixture(async f=>assert.equal((await f.request('/api/gaming/auth/steam/begin')).status,405)));
test('connected library does not accept another SteamID parameter',()=>withFixture(async f=>{const x=await f.login();assert.equal((await f.request('/api/gaming/steam/library?steamid=76561198000000002',{headers:{Cookie:x.cookie}})).status,400);}));
test('forged session cookie cannot read library',()=>withFixture(async f=>assert.equal((await f.request('/api/gaming/steam/library',{headers:{Cookie:'FGSteamSession'+f.port+'='+'f'.repeat(64)}})).status,401)));
test('unknown endpoint is rejected',()=>withFixture(async f=>assert.equal((await f.request('/api/gaming/auth/steam/evil/begin',{method:'POST'})).status,404)));
test('missing API key gives explicit setup failure, not fake connected mode',()=>withFixture(async f=>assert.equal((await f.request('/api/gaming/auth/steam/begin',{method:'POST'})).status,503),{key:''}));
test('session vault persists hashed tokens and logout across restarts',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'fg-auth-')),file=path.join(dir,'sessions.json'),token='e'.repeat(64);try{
 const v=new SessionVault(file,()=>NOW);v.put(token,{steamid:ID,expiresAt:NOW+10000});assert.equal(fs.readFileSync(file,'utf8').includes(token),false);assert.equal(new SessionVault(file,()=>NOW).get(token).steamid,ID);if(process.platform!=='win32')assert.equal(fs.statSync(file).mode&0o777,0o600);
 const resumed=new SessionVault(file,()=>NOW);resumed.remove(token);assert.equal(new SessionVault(file,()=>NOW).get(token),null);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
test('expired sessions not resurrected',()=>{const v=new SessionVault(null,()=>NOW);v.put('e'.repeat(64),{steamid:ID,expiresAt:NOW-1});assert.equal(v.get('e'.repeat(64)),null);});
test('corrupt session file is not overwritten',()=>{const dir=fs.mkdtempSync(path.join(os.tmpdir(),'fg-auth-')),file=path.join(dir,'sessions.json');try{fs.writeFileSync(file,'broken');assert.throws(()=>new SessionVault(file));assert.equal(fs.readFileSync(file,'utf8'),'broken');}finally{fs.rmSync(dir,{recursive:true,force:true});}});

test('Browser callback failure has a safe local recovery page',()=>withFixture(async f=>{const r=await f.request('/api/gaming/auth/steam/callback?state=missing&extra=PRIVATE',{headers:{Accept:'text/html'}});assert.equal(r.status,401);assert.match(r.headers.get('content-type'),/text\/html/);const text=await r.text();assert.match(text,/Revenir à FranchiseGuide/);assert.equal(text.includes('PRIVATE'),false);assert.match(r.headers.get('content-security-policy'),/default-src 'none'/);}));
