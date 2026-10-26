'use strict';
const {test}=require('node:test'),A=require('node:assert/strict'),http=require('node:http'),{once}=require('node:events');
const {createAutoServices,configuration,status,plan,readUpstream}=require('../server/auto-services.cjs');
const ENV={FG_TMDB_API_KEY:'1'.repeat(32),FG_RAWG_API_KEY:'2'.repeat(32),FG_TRAKT_CLIENT_ID:'3'.repeat(32),FG_TRAKT_CLIENT_SECRET:'4'.repeat(32),FG_STEAM_API_KEY:'5'.repeat(32),FG_RETRO_API_KEY:'6'.repeat(32)};
const cfg=configuration(ENV),url=p=>new URL('http://127.0.0.1/api/services/'+p);
async function rig(opts,fn){const calls=[];const handler=createAutoServices({env:ENV,read:async p=>{calls.push(p);return {status:200,body:'{"ok":true}'};},...opts});const s=http.createServer(async(req,res)=>{if(!await handler(req,res,new URL(req.url,'http://'+req.headers.host))){res.writeHead(404);res.end();}});s.listen(0,'127.0.0.1');await once(s,'listening');const base='http://127.0.0.1:'+s.address().port;const req=(path,opt={})=>fetch(base+'/api/services/'+path,{redirect:'manual',...opt,headers:{'X-FranchiseGuide-Services':'1',...opt.headers}});try{await fn({base,req,calls});}finally{s.closeAllConnections();await new Promise(r=>s.close(r));}}
test('Discovery exposes capabilities not keys or client identifiers',()=>rig({},async r=>{const x=await r.req('config');A.equal(x.status,200);const body=await x.text();for(const secret of Object.values(ENV))A.ok(!body.includes(secret));A.equal(JSON.parse(body).tmdb,true);A.equal(JSON.parse(body).trakt,true);A.equal(x.headers.get('cache-control'),'no-store');}));
test('No configuration still exposes usable public catalogues',()=>rig({env:{}},async r=>{const x=await (await r.req('config')).json();A.equal(x.tmdb,false);A.deepEqual(x.keyless,['tvmaze','itunes','freetogame','openlibrary']);A.equal((await r.req('tvmaze/search/shows?q=demo')).status,200);}));
test('Managed TMDb overrides caller key without exposing secret',()=>{const p=plan(url('tmdb/3/search/tv?query=Alien&api_key=bogus'),'GET',{},'',cfg);A.equal(new URL(p.url).searchParams.get('api_key'),ENV.FG_TMDB_API_KEY);A.equal(p.headers.Cookie,undefined);});
test('Managed TMDb read token uses Authorization instead of query',()=>{const c=configuration({...ENV,FG_TMDB_READ_TOKEN:'read.token.'+'x'.repeat(32)}),p=plan(url('tmdb/3/movie/3?api_key=wrong'),'GET',{},'',c);A.equal(new URL(p.url).searchParams.has('api_key'),false);A.equal(p.headers.Authorization,'Bearer '+c.tmdbToken);});
test('Managed RAWG injects publisher key only upstream',()=>A.equal(new URL(plan(url('rawg/api/games?search=demo'),'GET',{},'',cfg).url).searchParams.get('key'),cfg.rawg));
test('Device code requests inject real app ID without returning secret',()=>{const p=plan(url('trakt-auth/oauth/device/code'),'POST',{},'{"client_id":"__FG_PUBLISHER__","client_secret":"wrong"}',cfg);A.deepEqual(JSON.parse(p.body),{client_id:cfg.traktId});});
test('OAuth token request is bound to publisher app and user code',()=>{const p=plan(url('trakt-auth/oauth/device/token'),'POST',{},'{"client_id":"wrong","code":"device-fixture"}',cfg);A.deepEqual(JSON.parse(p.body),{client_id:cfg.traktId,client_secret:cfg.traktSecret,code:'device-fixture'});});
test('Trakt private calls preserve user token, replace only app key',()=>{const p=plan(url('trakt/users/settings'),'GET',{authorization:'Bearer personal-token'},'',cfg);A.equal(p.headers.Authorization,'Bearer personal-token');A.equal(p.headers['trakt-api-key'],cfg.traktId);});
test('OAuth refresh requires refresh token grant only',()=>{A.throws(()=>plan(url('trakt-auth/oauth/token'),'POST',{},'{"grant_type":"password","refresh_token":"a"}',cfg));const p=plan(url('trakt-auth/oauth/token'),'POST',{},'{"grant_type":"refresh_token","refresh_token":"test"}',cfg);A.equal(JSON.parse(p.body).refresh_token,'test');});
for(const bad of ['unknown/https://evil.test','tmdb/3/authentication/token/new','tvmaze/shows/1?url=https://evil.test','freetogame/api/games?redirect=https://evil.test','trakt/users/me/history','trakt/comments/2?api_key=a','itunes/search?term=a&term=b','tvmaze/shows/%31','tvmaze/shows/1?specials=0','itunes/search?limit=201','itunes/search?limit=0'])test('Reject unapproved route '+bad,()=>A.throws(()=>plan(url(bad),'GET',{},'',cfg)));
test('Credentials absent is explicit publisher activation error',()=>{for(const p of ['tmdb/3/movie/1','rawg/api/games','trakt/users/settings'])A.throws(()=>plan(url(p),'GET',{authorization:'Bearer user'},'',configuration({})),e=>e.status===503);});
test('No private Trakt endpoint without user bearer',()=>A.throws(()=>plan(url('trakt/sync/watched/shows'),'GET',{},'',cfg),e=>e.status===401));
test('No public catalogue writes',()=>{for(const p of ['tvmaze/shows/1','itunes/search','rawg/api/games','tmdb/3/movie/1'])A.throws(()=>plan(url(p),'POST',{},'{}',cfg),e=>e.status===405);});
test('Allow necessary Trakt writes but no arbitrary private methods',()=>{A.equal(plan(url('trakt/sync/history/remove'),'POST',{authorization:'Bearer user'},'{"movies":[]}',cfg).method,'POST');A.throws(()=>plan(url('trakt/users/settings'),'DELETE',{authorization:'Bearer user'},'{}',cfg));});
test('Cross-site requests blocked before reading provider',()=>rig({},async r=>{A.equal((await r.req('config',{headers:{Origin:'https://evil.test'}})).status,403);A.equal((await r.req('config',{headers:{'Sec-Fetch-Site':'cross-site'}})).status,403);A.equal((await fetch(r.base+'/api/services/config')).status,403);A.equal(r.calls.length,0);}));
test('Response redacts app secrets but retains user OAuth tokens',()=>rig({read:async()=>({status:200,body:JSON.stringify({access_token:'personal-access-token',echo:cfg.traktSecret})})},async r=>{const x=await r.req('trakt-auth/oauth/device/token',{method:'POST',body:'{"code":"fixture"}'});const body=await x.text();A.ok(!body.includes(cfg.traktSecret));A.equal(JSON.parse(body).access_token,'personal-access-token');}));
test('Cookie/header relay is not permitted',()=>rig({},async r=>{await r.req('tvmaze/shows/1',{headers:{Cookie:'private=not-forwarded',Authorization:'Bearer should-not-forward','X-Other':'x'}});A.equal(r.calls[0].headers.Authorization,undefined);A.equal(r.calls[0].headers.Cookie,undefined);}));
test('Gateway adds no hidden cache or POST retry to the client policy',()=>rig({},async r=>{await r.req('tvmaze/shows/1');await r.req('tvmaze/shows/1');A.equal(r.calls.length,2);for(let i=0;i<2;i++)await r.req('trakt-auth/oauth/device/code',{method:'POST',body:'{}'});A.equal(r.calls.length,4);}));
test('429 absent Retry-After holds provider for default minute',async()=>{let now=10000,calls=0;await rig({clock:()=>now,read:async()=>{calls++;return {status:429,body:'private details'};}},async r=>{A.equal((await r.req('tvmaze/shows/1')).status,429);const second=await r.req('tvmaze/shows/2');A.equal(second.status,429);A.equal(second.headers.get('retry-after'),'60');A.equal(calls,1);now+=61000;await r.req('tvmaze/shows/2');A.equal(calls,2);});});
test('Provider failures do not relay error secrets or HTML',()=>rig({read:async()=>({status:503,body:'<html>secret '+cfg.rawg+'</html>'})},async r=>{const x=await r.req('rawg/api/games');A.equal(x.status,503);const body=await x.text();A.ok(!body.includes(cfg.rawg));A.ok(!body.includes('<html>'));}));
test('Trakt device pending 400 and pagination headers are preserved',()=>rig({read:async p=>p.provider==='trakt-auth'?{status:400,body:'pending'}:{status:200,body:'[]',pages:'4'}},async r=>{A.equal((await r.req('trakt-auth/oauth/device/token',{method:'POST',body:'{"code":"x"}'})).status,400);const x=await r.req('trakt/sync/watchlist/movies',{headers:{Authorization:'Bearer user'}});A.equal(x.headers.get('x-pagination-page-count'),'4');}));
test('Invalid UTF8 JSON and oversize upstream bodies rejected',async()=>{const p=plan(url('tvmaze/shows/1'),'GET',{},'',cfg);await A.rejects(()=>readUpstream(p,async()=>new Response(new Uint8Array([0xff,0xfe]))));await A.rejects(()=>readUpstream(p,async()=>new Response('invalid json')));await A.rejects(()=>readUpstream(p,async()=>new Response('x',{headers:{'Content-Length':String(4*1024*1024+1)}})));});
test('Upstream requests pin origin disallow redirects and bound time',async()=>{const p=plan(url('tvmaze/shows/1'),'GET',{},'',cfg);await readUpstream(p,async (u,o)=>{A.equal(new URL(u).hostname,'api.tvmaze.com');A.equal(o.redirect,'error');A.ok(o.signal);return new Response('{}');});});

test('JSON escapes cannot expose publisher credentials in keys or nested values',()=>rig({read:async()=>({status:200,body:JSON.stringify({access_token:'personal-access-token',nested:[{echo:cfg.traktSecret,note:'line\b\f\u0001\nend'}],[cfg.traktSecret]:'secret-key'}).replaceAll('4','\\u0034')})},async r=>{
 const response=await r.req('trakt-auth/oauth/device/token',{method:'POST',body:'{"code":"fixture"}'});A.equal(response.status,200);const data=await response.json();
 A.ok(!JSON.stringify(data).includes(cfg.traktSecret));A.equal(data.access_token,'personal-access-token');A.equal(data.nested[0].echo,'[redacted]');A.equal(data.nested[0].note,'line\b\f\u0001\nend');
}));

test('429 with invalid Retry-After still enforces the default provider cooldown',async()=>{
 let now=10000,calls=0;await rig({clock:()=>now,read:async()=>{calls++;return {status:429,body:'error',retryAfter:'not-a-date'};}},async r=>{
  const first=await r.req('tvmaze/shows/1');A.equal(first.status,429);A.equal(first.headers.get('retry-after'),'60');
  now+=1000;const second=await r.req('tvmaze/shows/2');A.equal(second.status,429);A.equal(second.headers.get('retry-after'),'59');A.equal(calls,1);
  now+=60000;await r.req('tvmaze/shows/2');A.equal(calls,2);
 });
});

test('Retry-After delay starts when the provider response arrives',async()=>{
 let now=10000,calls=0;await rig({clock:()=>now,read:async()=>{calls++;now+=25000;return {status:429,body:'error',retryAfter:'60'};}},async r=>{
  await r.req('tvmaze/shows/1');const second=await r.req('tvmaze/shows/2');A.equal(second.headers.get('retry-after'),'60');A.equal(calls,1);
 });
});

test('Duplicate security headers are refused before provider contact',()=>rig({},async r=>{
 const port=new URL(r.base).port;const response=await new Promise((resolve,reject)=>{
  const q=http.request({hostname:'127.0.0.1',port,path:'/api/services/tvmaze/shows/1',headers:['Host','127.0.0.1:'+port,'X-FranchiseGuide-Services','1','Sec-Fetch-Site','same-origin','Sec-Fetch-Site','cross-site']},res=>{res.resume();res.on('end',()=>resolve(res.statusCode));});q.on('error',reject);q.end();
 });A.equal(response,403);A.equal(r.calls.length,0);
}));
