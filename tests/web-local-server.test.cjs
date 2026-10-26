'use strict';
const test=require('node:test');const assert=require('node:assert/strict');const http=require('node:http');const path=require('node:path');
const {start}=require('../server/start.cjs');const {createSteamBridge}=require('../server/steam-bridge.cjs');
const {createSteamAuth}=require('../server/steam-auth.cjs');
let server,port;
test.before(async()=>{server=start({port:0,root:path.resolve(__dirname,'../web'),bridge:createSteamBridge({key:''}),auth:createSteamAuth({key:'',vaultFile:null})});await new Promise(resolve=>server.once('listening',resolve));port=server.address().port;});
test.after(async()=>{server.closeAllConnections();await new Promise(resolve=>server.close(resolve));});
function req(p,headers={},method='GET'){return new Promise((resolve,reject)=>{const q=http.request({hostname:'127.0.0.1',port,path:p,method,headers},r=>{let chunks=[];r.on('data',d=>chunks.push(d));r.on('end',()=>resolve({status:r.statusCode,headers:r.headers,body:Buffer.concat(chunks).toString()}));});q.on('error',reject);q.end();});}
test('local Web server serves real index with trophy modules',async()=>{const r=await req('/');assert.equal(r.status,200);assert.match(r.body,/gaming-core.js/);assert.match(r.body,/gaming.js/);});
test('local Web server serves gaming script',async()=>{const r=await req('/gaming.js');assert.equal(r.status,200);assert.match(r.headers['content-type'],/javascript/);assert.match(r.body,/runGamingSync/);});
test('local Web server sends security headers',async()=>{const r=await req('/');assert.equal(r.headers['x-frame-options'],'DENY');assert.equal(r.headers['x-content-type-options'],'nosniff');assert.equal(r.headers['referrer-policy'],'no-referrer');});
test('local Web server HEAD sends no body',async()=>{const r=await req('/',{},'HEAD');assert.equal(r.status,200);assert.equal(r.body,'');assert.ok(Number(r.headers['content-length'])>0);});
test('local Web server refuses foreign Host',async()=>assert.equal((await req('/',{Host:'evil.test'})).status,403));
test('local Web server does not serve environment file',async()=>assert.equal((await req('/.env')).status,404));
test('local Web server does not serve parent sources',async()=>assert.equal((await req('/../server/start.cjs')).status,404));
test('local Web server refuses static writes',async()=>assert.equal((await req('/',{},'POST')).status,405));
test('local Web bridge reports missing key without contacting Steam',async()=>{const r=await req('/api/gaming/status',{'X-FranchiseGuide-Client':'0.11'});assert.equal(r.status,200);const v=JSON.parse(r.body);assert.equal(v.steam,false);assert.equal(v.writes,false);});
test('local Web bridge refuses cross-origin access',async()=>assert.equal((await req('/api/gaming/status',{'X-FranchiseGuide-Client':'0.11',Origin:'https://evil.test'})).status,403));

test('Web status exposes Steam sign-in capability',async()=>assert.equal(JSON.parse((await req('/api/gaming/status',{'X-FranchiseGuide-Client':'0.12'})).body).steamSignIn,true));
test('Web server dispatches session lookup',async()=>{const r=await req('/api/gaming/auth/steam/session',{'X-FranchiseGuide-Client':'0.12'});assert.equal(r.status,200);assert.equal(JSON.parse(r.body).authenticated,false);assert.equal(r.headers['cache-control'],'no-store');});
test('Web login without server key explains configuration',async()=>{const r=await req('/api/gaming/auth/steam/begin',{'X-FranchiseGuide-Client':'0.12'},'POST');assert.equal(r.status,503);assert.match(r.body,/FG_STEAM_API_KEY/);});
test('Web owned library requires a session',async()=>assert.equal((await req('/api/gaming/steam/library',{'X-FranchiseGuide-Client':'0.12'})).status,401));
test('Web auth route rejects foreign origin',async()=>assert.equal((await req('/api/gaming/auth/steam/session',{'X-FranchiseGuide-Client':'0.12',Origin:'https://evil.test'})).status,403));
test('Web invalid callback produces local recovery page',async()=>{const r=await req('/api/gaming/auth/steam/callback?state=missing',{Accept:'text/html'});assert.equal(r.status,401);assert.match(r.headers['content-type'],/text\/html/);assert.match(r.body,/Revenir à FranchiseGuide/);});
test('Web server refuses duplicate Host and security headers on all routes',async()=>{
 for(const route of ['/','/api/services/config','/api/gaming/status','/api/gaming/auth/steam/session']){
  const response=await req(route,['Host','127.0.0.1:'+port,'Host','evil.test','X-FranchiseGuide-Client','0.12','X-FranchiseGuide-Services','1']);assert.equal(response.status,403,route);
 }
});
