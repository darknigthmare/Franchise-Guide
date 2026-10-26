'use strict';
const {test}=require('node:test');
const A=require('node:assert/strict');
const http=require('node:http');
const path=require('node:path');
const {once}=require('node:events');
const {createRetroBridge,retroRead,parameters,parseJson}=require('../server/retro-bridge.cjs');
const {start}=require('../server/start.cjs');
const KEY='RetroTestKeyNotReal12345678901234',USER='01ARZ3NDEKTSV4RRFFQ69G5FAV',USER2='01ARZ3NDEKTSV4RRFFQ69G5FAW';
const uid=n=>'000000000000000000'+n.toString(16).toUpperCase().padStart(8,'0');
const profile=(user=USER)=>({User:'FixtureUser',ULID:user,ID:45,UserPic:'/UserPic/FixtureUser.png',TotalPoints:5,TotalSoftcorePoints:5});
const libraryRow=(id=1)=>({GameID:id,Title:'Fixture game '+id,ConsoleID:1,ConsoleName:'Mega Drive',ImageIcon:'/Images/000001.png',MaxPossible:1,NumAwarded:1,NumAwardedHardcore:1});
const game=(id=1)=>({ID:id,Title:'Fixture game '+id,ConsoleID:1,ConsoleName:'Mega Drive',NumAchievements:1,NumAwardedToUser:1,NumAwardedToUserHardcore:1,Achievements:{'9':{ID:9,Title:'Fixture achievement',Description:'An explicitly simulated unlock.',Points:5,DateEarned:'2025-01-01 12:00:00',DateEarnedHardcore:'2025-01-01 12:00:00'}}});
async function fixtureRead(input){
 const u=new URL(input);
 if(u.pathname.endsWith('API_GetUserProfile.php'))return profile(u.searchParams.get('u').length===26?u.searchParams.get('u').toUpperCase():USER);
 if(u.pathname.endsWith('API_GetUserCompletionProgress.php'))return {Count:1,Total:1,Results:[libraryRow()]};
 return game(Number(u.searchParams.get('g')));
}
async function withServer(options,fn){
 const handler=createRetroBridge({key:KEY,read:fixtureRead,...options});
 const server=http.createServer(async(req,res)=>{try{if(!await handler(req,res,new URL(req.url,'http://'+req.headers.host))){res.writeHead(404);res.end();}}catch{res.writeHead(500);res.end('fixture failure');}});
 server.listen(0,'127.0.0.1');await once(server,'listening');
 const base='http://127.0.0.1:'+server.address().port;
 const req=(route='/status',init={})=>fetch(base+'/api/gaming/retro'+route,{...init,headers:{'X-FranchiseGuide-Client':'0.14',...init.headers}});
 try{return await fn(req,base,handler);}finally{server.closeAllConnections();await new Promise(resolve=>server.close(resolve));}
}
test('RA status reports configuration without contacting the API or claiming authentication',async()=>withServer({key:'',read:()=>{throw Error('No network expected');}},async req=>{
 const r=await req();A.equal(r.status,200);A.deepEqual(await r.json(),{provider:'retro',configured:false});A.equal((await req('/profile?user=FixtureUser')).status,503);
}));
test('RA status accepts HEAD without upstream requests or a response body',async()=>withServer({read:()=>{throw Error('No network expected');}},async req=>{
 const r=await req('/status',{method:'HEAD'});A.equal(r.status,200);A.equal(await r.text(),'');A.equal(r.headers.get('cache-control'),'no-store');
}));
test('RA profile resolves a public username to the stable ULID without a session cookie',async()=>withServer({},async req=>{
 const r=await req('/profile?user=FixtureUser'),v=await r.json();A.equal(r.status,200);A.equal(v.user,USER);A.equal(v.profile.User,'FixtureUser');A.ok(Number.isSafeInteger(v.fetchedAt));A.equal(v.authenticated,undefined);A.equal(r.headers.get('set-cookie'),null);
 A.equal(r.headers.get('cache-control'),'no-store');A.equal(r.headers.get('access-control-allow-origin'),null);A.equal(r.headers.get('referrer-policy'),'no-referrer');
}));
test('RA library validates the profile before a fixed read-only progress endpoint',async()=>{
 const calls=[];await withServer({read:async url=>{calls.push(new URL(url));return fixtureRead(url);}},async req=>{
  const r=await req('/library?user='+USER+'&offset=0&count=100'),v=await r.json();A.equal(r.status,200);A.equal(v.user,USER);A.equal(v.library.Results[0].GameID,1);A.equal(calls.length,2);
  A.deepEqual(calls.map(u=>u.pathname),['/API/API_GetUserProfile.php','/API/API_GetUserCompletionProgress.php']);A.ok(calls.every(u=>u.origin==='https://retroachievements.org'&&u.searchParams.get('y')===KEY));
  A.equal(calls[1].searchParams.get('u'),USER);A.equal(calls[1].searchParams.get('c'),'100');A.equal(calls[1].searchParams.get('o'),'0');A.ok(!JSON.stringify(v).includes(KEY));
 });
});
test('RA game reads use the stable profile ID and request award metadata',async()=>{
 const calls=[];await withServer({read:async url=>{calls.push(new URL(url));return fixtureRead(url);}},async req=>{
  const r=await req('/achievements?user='+USER.toLowerCase()+'&gameId=123'),v=await r.json();A.equal(r.status,200);A.equal(v.user,USER);A.equal(v.gameId,'123');A.equal(v.game.ID,123);
  const u=calls[1];A.equal(u.pathname,'/API/API_GetGameInfoAndUserProgress.php');A.equal(u.searchParams.get('u'),USER);A.equal(u.searchParams.get('g'),'123');A.equal(u.searchParams.get('a'),'1');
 });
});
test('RA true empty progress is accepted only after verifying an existing profile',async()=>{
 let profiles=0;await withServer({read:async url=>{if(url.includes('API_GetUserProfile')){profiles++;return profile();}return {Count:0,Total:0,Results:[]};}},async req=>{
  const r=await req('/library?user='+USER);A.equal(r.status,200);A.equal((await r.json()).library.Total,0);A.equal(profiles,1);
 });
});
test('RA missing profile stops the ambiguous empty-library request',async()=>{
 let calls=0;await withServer({read:async()=>{calls++;return [];}},async req=>{
  const r=await req('/library?user='+USER);A.equal(r.status,502);A.equal(calls,1);A.equal((await r.json()).library,undefined);
 });
});
test('RA profile returned for another username or ULID is rejected',async()=>withServer({read:async()=>profile(USER2)},async req=>{
 A.equal((await req('/profile?user='+USER)).status,502);A.equal((await req('/profile?user=AnotherUser')).status,502);
}));
for(const raw of [null,[],{}, {User:'FixtureUser'}, {User:'FixtureUser',ULID:'invalid'}])test('RA invalid profile is never relayed '+JSON.stringify(raw),async()=>withServer({read:async()=>raw},async req=>A.equal((await req('/profile?user=FixtureUser')).status,502)));
for(const raw of [[],{}, {Count:0,Total:1,Results:[]},{Count:2,Total:2,Results:[libraryRow()]},{Count:2,Total:2,Results:[libraryRow(),libraryRow()]},{Count:1,Total:1,Results:[{...libraryRow(),GameID:0}]}])test('RA incomplete or inaccessible library is rejected '+JSON.stringify(raw).slice(0,90),async()=>withServer({read:async url=>url.includes('API_GetUserProfile')?profile():raw},async req=>A.equal((await req('/library?user='+USER)).status,502)));
for(const raw of [[],{}, {...game(2)}, {...game(),NumAchievements:2},{...game(),Achievements:[]},{...game(),NumAwardedToUserHardcore:2},{...game(),Achievements:{'9':{ID:10,Title:'Wrong ID'}}}])test('RA invalid game snapshot cannot replace progress '+JSON.stringify(raw).slice(0,90),async()=>withServer({read:async url=>url.includes('API_GetUserProfile')?profile():raw},async req=>A.equal((await req('/achievements?user='+USER+'&gameId=1')).status,502)));
test('RA zero-achievement game stays a valid empty definition set',async()=>withServer({read:async url=>url.includes('API_GetUserProfile')?profile():{...game(),NumAchievements:0,NumAwardedToUser:0,NumAwardedToUserHardcore:0,Achievements:{}}},async req=>{
 const r=await req('/achievements?user='+USER+'&gameId=1');A.equal(r.status,200);A.equal((await r.json()).game.NumAchievements,0);
}));
test('RA pagination sends the requested offset and accepts a final shorter page',async()=>withServer({read:async url=>{
 const u=new URL(url);if(u.pathname.includes('UserProfile'))return profile();A.equal(u.searchParams.get('o'),'100');return {Count:1,Total:101,Results:[libraryRow(101)]};
}},async req=>{const r=await req('/library?user='+USER+'&offset=100&count=100');A.equal(r.status,200);A.equal((await r.json()).library.Results[0].GameID,101);}));
test('RA large libraries can use the official maximum of 500 records per page',async()=>withServer({read:async url=>{
 const u=new URL(url);if(u.pathname.includes('UserProfile'))return profile();A.equal(u.searchParams.get('c'),'500');return {Count:500,Total:1500,Results:Array.from({length:500},(_,i)=>libraryRow(i+1))};
}},async req=>{const r=await req('/library?user='+USER+'&count=500');A.equal(r.status,200);A.equal((await r.json()).library.Results.length,500);}));
test('RA same reads coalesce while in flight and reuse their cached snapshot time',async()=>{
 let count=0,time=1700000000000;await withServer({now:()=>time,read:async url=>{count++;await new Promise(r=>setTimeout(r,10));return fixtureRead(url);}},async req=>{
  const values=await Promise.all([req('/profile?user=FixtureUser'),req('/profile?user=FixtureUser'),req('/profile?user=FixtureUser')]);A.ok(values.every(v=>v.status===200));A.equal(count,1);
  time+=30000;const hit=await(await req('/profile?user='+USER)).json();A.equal(count,1);A.equal(hit.fetchedAt,1700000000000);
  time+=31000;A.equal((await req('/profile?user='+USER)).status,200);A.equal(count,2);
 });
});
test('RA failed response is not cached and a retry can recover',async()=>{
 let failed=true,calls=0;await withServer({read:async url=>{calls++;if(url.includes('API_GetUserProfile'))return profile();if(failed){failed=false;return [];}return {Count:1,Total:1,Results:[libraryRow()]};}},async req=>{
  A.equal((await req('/library?user='+USER)).status,502);A.equal((await req('/library?user='+USER)).status,200);A.equal(calls,3);
 });
});
test('RA cache remains partitioned by stable user and game identity',async()=>{
 let calls=0;await withServer({read:async url=>{calls++;return fixtureRead(url);}},async req=>{
  for(const route of ['/achievements?user='+USER+'&gameId=1','/achievements?user='+USER+'&gameId=1','/achievements?user='+USER+'&gameId=2','/achievements?user='+USER2+'&gameId=1'])A.equal((await req(route)).status,200);A.equal(calls,5);
 });
});
test('RA echoed secrets are removed after decoding escaped JSON strings',async()=>withServer({read:async()=>JSON.stringify({...profile(),Motto:KEY}).replace(KEY,Array.from(KEY).map(c=>'\\u'+c.charCodeAt(0).toString(16).padStart(4,'0')).join(''))},async req=>{
 const r=await req('/profile?user='+USER),body=await r.text();A.equal(r.status,200);A.ok(!body.includes(KEY));A.equal(JSON.parse(body).profile.Motto,'[redacted]');
}));
test('RA upstream errors never disclose the secret or a remote request URL',async()=>withServer({read:async()=>{throw Error('https://retroachievements.org/API/?y='+KEY);}},async req=>{
 const r=await req('/profile?user='+USER),body=await r.text();A.equal(r.status,502);A.ok(!body.includes(KEY));A.ok(!body.includes('?y='));
}));
test('RA an API error flag is never treated as a complete profile',async()=>withServer({read:async()=>({...profile(),Success:false,Error:KEY})},async req=>{
 const r=await req('/profile?user='+USER);A.equal(r.status,502);A.ok(!(await r.text()).includes(KEY));
}));
test('RA impossible hardcore library counters are rejected',async()=>withServer({read:async url=>url.includes('API_GetUserProfile')?profile():{Count:1,Total:1,Results:[{...libraryRow(),NumAwarded:0,NumAwardedHardcore:1}]}},async req=>A.equal((await req('/library?user='+USER)).status,502)));
for(const method of ['POST','PUT','PATCH','DELETE','OPTIONS'])test('RA refuses '+method+' without upstream activity',async()=>withServer({read:()=>{throw Error('No upstream request');}},async req=>A.equal((await req('/profile?user='+USER,{method})).status,405)));
test('RA HEAD cannot fetch profile, library or achievements',async()=>withServer({read:()=>{throw Error('No upstream request');}},async req=>{
 for(const route of ['/profile?user='+USER,'/library?user='+USER,'/achievements?user='+USER+'&gameId=1'])A.equal((await req(route,{method:'HEAD'})).status,405);
}));
test('RA refuses cross-origin, cross-site and absent application headers',async()=>withServer({},async(req,base)=>{
 A.equal((await req('/status',{headers:{Origin:'https://evil.test'}})).status,403);A.equal((await req('/status',{headers:{'Sec-Fetch-Site':'cross-site'}})).status,403);
 A.equal((await fetch(base+'/api/gaming/retro/status')).status,403);A.equal((await req('/status',{headers:{Origin:'null'}})).status,403);
 A.equal((await req('/status',{headers:{Origin:base,'Sec-Fetch-Site':'same-origin'}})).status,200);
}));
test('RA rejects a forged Host header before it could act as a DNS rebinding proxy',async()=>withServer({},async(req,base)=>{
 const code=await new Promise((resolve,reject)=>http.get(base+'/api/gaming/retro/status',{headers:{Host:'evil.test','X-FranchiseGuide-Client':'0.14'}},r=>{r.resume();resolve(r.statusCode);}).on('error',reject));A.equal(code,403);
}));
test('RA duplicated security headers cannot exploit first-value header handling',async()=>withServer({},async(req,base)=>{
 for(const name of ['Host','Origin','Sec-Fetch-Site','X-FranchiseGuide-Client']){
  const headers=['Host',new URL(base).host,'X-FranchiseGuide-Client','0.14'];
  if(name==='Host')headers.push('Host',new URL(base).host);
  else if(name==='X-FranchiseGuide-Client')headers.push(name,'0.14');
  else headers.push(name,name==='Origin'?base:'same-origin',name,name==='Origin'?base:'cross-site');
  const code=await new Promise((resolve,reject)=>http.get(base+'/api/gaming/retro/status',{headers},r=>{r.resume();resolve(r.statusCode);}).on('error',reject));A.equal(code,403);
 }
}));
for(const route of ['/status?key=x','/profile?user=FixtureUser&user=Other','/profile?user=FixtureUser&y=secret','/profile?user=https%3A%2F%2Fevil.test','/library?user=FixtureUser','/library?user='+USER+'&offset=-1','/library?user='+USER+'&offset=100001','/library?user='+USER+'&count=501','/library?user='+USER+'&count=0','/achievements?user='+USER+'&gameId=2147483648','/achievements?user='+USER+'&gameId=01','/achievements?user='+USER+'&gameId=1&url=https://evil.test'])test('RA rejects invalid query '+route,async()=>withServer({},async req=>A.equal((await req(route)).status,400)));
test('RA unknown endpoints cannot forward arbitrary requests',async()=>withServer({},async req=>A.equal((await req('/proxy?url=https://evil.test')).status,404)));
test('RA rejects a query longer than 300 characters',()=>A.throws(()=>parameters(new URL('http://localhost/api/gaming/retro/profile?user='+'X'.repeat(301)))));
test('RA bounds upstream concurrency to two requests',async()=>{
 const releases=[],started=[];await withServer({read:url=>new Promise(resolve=>{started.push(url);releases.push(()=>resolve(profile(new URL(url).searchParams.get('u'))));})},async req=>{
  const one=req('/profile?user='+USER),two=req('/profile?user='+USER2);
  for(let i=0;started.length<2&&i<100;i++)await new Promise(r=>setTimeout(r,2));A.equal(started.length,2);
  const blocked=await req('/profile?user='+uid(3));A.equal(blocked.status,429);A.equal(blocked.headers.get('retry-after'),'60');releases.forEach(r=>r());A.equal((await one).status,200);A.equal((await two).status,200);
 });
});
test('RA coalescing also bounds waiting callers instead of accumulating them indefinitely',async()=>{
 let release,reads=0;await withServer({read:async()=>{reads++;return new Promise(resolve=>{release=()=>resolve(profile());});}},async(req,base,handler)=>{
  const pending=[];for(let i=0;i<17;i++)pending.push(handler.lookup({route:'profile',user:USER}));
  await A.rejects(()=>handler.lookup({route:'profile',user:USER}),e=>e.status===429);
  release();A.equal((await Promise.all(pending)).length,17);A.equal(reads,1);
 });
});
test('RA upstream 429 starts a one-minute cooldown rather than repeatedly calling the provider',async()=>{
 let time=1700000000000,reads=0;await withServer({now:()=>time,read:async url=>{reads++;if(reads===1)throw Object.assign(Error('limited'),{status:429});return fixtureRead(url);}},async req=>{
  A.equal((await req('/profile?user='+USER)).status,429);A.equal((await req('/profile?user='+USER)).status,429);A.equal(reads,1);
  time+=60001;A.equal((await req('/profile?user='+USER)).status,200);A.equal(reads,2);
 });
});
test('RA rate limit is counted per upstream read and recovers after its fixed minute',async()=>{
 let time=1700000000000;await withServer({now:()=>time},async req=>{
  for(let i=1;i<=36;i++)A.equal((await req('/profile?user='+uid(i))).status,200);
  A.equal((await req('/profile?user='+uid(37))).status,429);A.equal((await req('/profile?user='+uid(36))).status,200);
  time+=60001;A.equal((await req('/profile?user='+uid(37))).status,200);
 });
});
test('RA cache keeps at most 32 entries even inside a single rate window',async()=>{
 let reads=0;await withServer({read:async url=>{reads++;return fixtureRead(url);}},async req=>{
  for(let i=1;i<=33;i++)A.equal((await req('/profile?user='+uid(i))).status,200);
  await req('/profile?user='+uid(1));A.equal(reads,34);
 });
});
test('RA cache also evicts entries at its total eight MiB memory budget',async()=>{
 let reads=0;await withServer({read:async url=>{reads++;return {...await fixtureRead(url),Motto:'x'.repeat(2900000)};}},async req=>{
  for(let i=1;i<=3;i++)A.equal((await req('/profile?user='+uid(i))).status,200);
  await req('/profile?user='+uid(1));A.equal(reads,4);
 });
});
test('RA production reader permits only the three official HTTPS endpoints',async()=>{
 for(const url of ['http://retroachievements.org/API/API_GetUserProfile.php','https://evil.test/API/API_GetUserProfile.php','https://retroachievements.org/API/API_DeleteUser.php','https://user@retroachievements.org/API/API_GetUserProfile.php','https://retroachievements.org:444/API/API_GetUserProfile.php'])await A.rejects(()=>retroRead(url,()=>{throw Error('No fetch expected');}),/Disallowed/);
});
test('RA production reader uses GET, no redirects, and an abort deadline',async()=>{
 let opts;const value=await retroRead('https://retroachievements.org/API/API_GetUserProfile.php',async(url,o)=>{opts=o;return new Response(JSON.stringify(profile()));});A.equal(value.ULID,USER);A.equal(opts.method,'GET');A.equal(opts.redirect,'error');A.ok(opts.signal instanceof AbortSignal);
});
test('RA reader rejects malformed UTF-8, malformed JSON, duplicate and escaped duplicate keys',async()=>{
 for(const raw of ['not json','{"a":1,"a":2}','{"a":1,"\\u0061":2}',new Uint8Array([0x7b,0x22,0xff,0x22,0x3a,0x31,0x7d])])await A.rejects(()=>retroRead('https://retroachievements.org/API/API_GetUserProfile.php',async()=>new Response(raw)));
});
test('RA reader rejects oversized advertised and streamed bodies',async()=>{
 await A.rejects(()=>retroRead('https://retroachievements.org/API/API_GetUserProfile.php',async()=>new Response('{}',{headers:{'content-length':String(4*1024*1024+1)}})),/too large/);
 await A.rejects(()=>retroRead('https://retroachievements.org/API/API_GetUserProfile.php',async()=>new Response(' '.repeat(4*1024*1024+1))),/too large/);
});
test('RA reader propagates rate limiting without leaking remote errors',async()=>{
 await A.rejects(()=>retroRead('https://retroachievements.org/API/API_GetUserProfile.php',async()=>new Response(KEY,{status:429})),e=>e.status===429&&!e.message.includes(KEY));
 await A.rejects(()=>retroRead('https://retroachievements.org/API/API_GetUserProfile.php',async()=>new Response(KEY,{status:500})),/unavailable/);
});
test('RA JSON depth, size and non-finite numbers are bounded',()=>{
 A.throws(()=>parseJson('['.repeat(33)+'0'+']'.repeat(33)),/deep/);A.throws(()=>parseJson('{"value":1e999}'),/number/);A.throws(()=>parseJson(' '.repeat(4*1024*1024+1)),/large/);
 A.deepEqual(parseJson('{"title":"braces { are [ ordinary ] characters }", "values":[null,1,true]}'),{title:'braces { are [ ordinary ] characters }',values:[null,1,true]});
});
test('Actual Web server dispatches Retro ahead of the Steam gaming catch-all',async()=>{
 let steamFallback=0;const server=start({port:0,root:path.resolve(__dirname,'../web'),auth:async()=>false,bridge:async(req,res)=>{steamFallback++;res.writeHead(418);res.end();return true;},retro:createRetroBridge({key:KEY,read:fixtureRead})});
 await once(server,'listening');const base='http://127.0.0.1:'+server.address().port;
 try{const r=await fetch(base+'/api/gaming/retro/library?user='+USER,{headers:{'X-FranchiseGuide-Client':'0.14'}});A.equal(r.status,200);A.equal((await r.json()).provider,'retro');A.equal(steamFallback,0);}finally{server.closeAllConnections();await new Promise(r=>server.close(r));}
});
test('Real HTTP bridge envelopes feed the actual Retro domain import and achievement normalizer',async()=>withServer({},async req=>{
 const R=require('../web/retro-core.js'),G=require('../web/gaming-core.js');
 const p=R.profile(await(await req('/profile?user=FixtureUser')).json(),'FixtureUser');
 const page=R.page(await(await req('/library?user='+p.ulid+'&count=500')).json(),p.ulid,0,500);
 const snapshot=R.library([page],p),state={schema:8,media:[],episodes:[],lists:[],settings:{},gaming:{version:1,accounts:[],sets:[]}};
 const result=R.plan(state,snapshot,{autoImport:false,autoSync:false});
 A.equal(result.state.media.length,1);A.equal(result.state.gaming.accounts[0].externalId,USER);
 const binding={...result.state.gaming.sets[0],accountExternalId:USER};
 const set=R.achievements(await(await req('/achievements?user='+USER+'&gameId=1')).json(),binding,Date.now());
 A.equal(G.progress(set).earned,1);A.equal(G.progress(set).hardcore,1);A.equal(G.progress(set).platinum,false);A.equal(state.media.length,0);
}));
