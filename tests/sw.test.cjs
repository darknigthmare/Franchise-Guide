'use strict';
const {test}=require('node:test'), assert=require('node:assert/strict'), vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const web=path.join(__dirname,'../web'),source=fs.readFileSync(path.join(web,'sw.js'),'utf8');
function harness(){
 const handlers={},bags=new Map();let installs=[],skip=0,claims=0,fetches=[];
 const key=r=>new URL(typeof r==='string'?r:r.url,'https://local.test/app/').href;
 const caches={async open(name){if(!bags.has(name))bags.set(name,new Map());const bag=bags.get(name);return {
  async addAll(items){installs=items;for(const a of items){const file=a==='./'?'index.html':a;assert.ok(fs.existsSync(path.join(web,file)));bag.set(key(a),new Response(file));}},
  async match(r){return bag.get(key(r))?.clone();},async put(r,response){bag.set(key(r),response.clone());},async keys(){return [...bag.keys()];},async delete(r){return bag.delete(key(r));}
 };},async keys(){return [...bags.keys()]},async delete(k){return bags.delete(k)}};
 const self={registration:{scope:'https://local.test/app/'},location:{origin:'https://local.test'},clients:{claim:()=>{claims++;return Promise.resolve()}},skipWaiting:()=>{skip++;return Promise.resolve()},addEventListener:(name,fn)=>handlers[name]=fn};
 const fetch=async r=>{fetches.push(typeof r==='string'?r:r.url);return new Response('network')};
 vm.runInNewContext(source,{self,caches,URL,Set,Response,fetch});
 return {handlers,bags,caches,fetches,get installs(){return installs},get skip(){return skip},get claims(){return claims},
 async event(name,payload={}){let p;handlers[name]({...payload,waitUntil:x=>p=x,respondWith:x=>p=x});return p?await p:undefined;}};
}
test('SW installs every runtime module, including new V0.8 modules',async()=>{const h=harness();await h.event('install');for(const f of ['network.js','persistence.js','people.js','views.js'])assert.ok(h.installs.includes(f));for(const f of fs.readdirSync(web).filter(x=>x.endsWith('.js')&&x!=='sw.js'))assert.ok(h.installs.includes(f));});
test('SW does not force activate while user is editing',async()=>{const h=harness();await h.event('install');assert.equal(h.skip,0);await h.event('message',{data:{type:'ACTIVATE_UPDATE'}});assert.equal(h.skip,1);});
test('activation removes only old FranchiseGuide shell caches',async()=>{const h=harness();h.bags.set('unrelated-app',new Map());h.bags.set('franchiseguide-web-0.7.0',new Map());h.bags.set('franchiseguide-images-v1',new Map());await h.event('install');await h.event('activate');assert.ok(h.bags.has('unrelated-app'));assert.ok(!h.bags.has('franchiseguide-web-0.7.0'));assert.ok(h.bags.has('franchiseguide-images-v1'));assert.equal(h.claims,1);});
test('shell is served from same installed version even when online',async()=>{const h=harness();await h.event('install');const r=await h.event('fetch',{request:{method:'GET',url:'https://local.test/app/people.js',destination:'script'}});assert.equal(await r.text(),'people.js');assert.equal(h.fetches.length,0);});
test('non-GET requests are never cached or intercepted',async()=>{const h=harness();assert.equal(await h.event('fetch',{request:{method:'POST',url:'https://local.test/app/index.html'}}),undefined);});
test('external JSON APIs are not cached by SW',async()=>{const h=harness();assert.equal(await h.event('fetch',{request:{method:'GET',url:'https://api.trakt.tv/sync/watched/movies',destination:''}}),undefined);assert.equal(h.bags.size,0);});
test('unknown same-origin API URLs are not cached by SW',async()=>{const h=harness();assert.equal(await h.event('fetch',{request:{method:'GET',url:'https://local.test/api/private',destination:''}}),undefined);});
test('provider poster cache has a 100-image cap',async()=>{const h=harness();for(let i=0;i<105;i++)await h.event('fetch',{request:{method:'GET',url:'https://image.tmdb.org/t/p/w500/'+i+'.jpg',destination:'image'}});assert.equal(h.bags.get('franchiseguide-images-v1').size,100);});
