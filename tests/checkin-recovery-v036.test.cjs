'use strict';
const {test}=require('node:test'),A=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const C=require('../web/trakt-checkin-core.js');
const KEY='franchiseguide-trakt-checkin-v1';
function library(){return {media:[{id:'film',kind:'movie',title:'Film QA',added:true,tmdbId:101,runtimeMinutes:120,plays:0,watchHistory:[]}],episodes:[]};}
function harness(options={}){
 const state=library(),now=Date.now(),messages=[],calls=[],data=new Map(),h={data,messages,calls,failSet:false,failRemove:false,failRead:false};
 const session=C.startConfirmed(C.create(C.target(state,'movie','film'),'qa-user','app-a',now-60000),{});
 state._traktAppRef='app-a';
 if(options.session!==false)data.set(KEY,C.encode(options.session||session));
 const context={FGCheckIn:C,Date,TextEncoder,JSON,console,state,savedState:structuredClone(state),libraryEpoch:0,authGeneration:0,traktSyncing:false,syncState:{queue:[]},writable:true,connected:true,auth:{username:'qa-user'},overlay:null,
  clone:structuredClone,document:{addEventListener(){}},render(){},renderToday(){},toast:m=>messages.push(m),confirm:()=>true,
  localStorage:{getItem(k){if(h.failRead)throw Error('Storage blocked');return data.get(k)??null;},setItem(k,v){if(h.failSet)throw Error('Quota full');data.set(k,v);},removeItem(k){if(h.failRemove)throw Error('Removal blocked');data.delete(k);}},
  assertWritable(){if(!context.writable)throw Object.assign(Error('Read only'),{code:'READ_ONLY'});},traktConnected:()=>context.connected,traktAuth:()=>context.auth,syncAccount:()=>context.auth.username,
  save(){if(h.failSave){context.state=structuredClone(context.savedState);throw Error('Save failed');}context.savedState=structuredClone(context.state);return true;},
  downloadJson(){},
  async traktApi(path,opts={}){calls.push({path,method:opts.method||'GET',body:opts.body?JSON.parse(opts.body):null});if(h.handler)return h.handler(path,opts);return path.includes('/history/')?[]:{added:{movies:1},not_found:{movies:[]}};},
  async traktResponse(path,opts={}){calls.push({path,method:opts.method||'GET'});if(h.cleanup)return h.cleanup(path,opts);return {};}
 };
 vm.createContext(context);vm.runInContext(fs.readFileSync(require.resolve('../web/trakt-checkin-ui.js'),'utf8'),context);
 h.ctx=context;h.session=session;h.read=()=>context.checkInRead();h.eval=code=>vm.runInContext(code,context);return h;
}
const posts=h=>h.calls.filter(x=>x.method==='POST'&&x.path==='/sync/history');
function historyRow(s){return {movie:{ids:{tmdb:s.tmdbId}},watched_at:new Date(s.finishAt??s.startedAt+30000).toISOString()};}
test('An acknowledged check-in finish creates one local viewing and then cleans up',async()=>{
 const h=harness();await h.ctx.finishTraktCheckIn();A.equal(posts(h).length,1);A.equal(h.ctx.state.media[0].plays,1);A.equal(h.read(),null);A.equal(h.calls.at(-1).method,'DELETE');
});
test('Zero or inconsistent acknowledgement counts cannot mark a movie watched',async t=>{
 for(const response of [{added:{movies:0}},{added:{movies:0},existing:{movies:0}},{added:{movies:2}},{added:{movies:1},existing:{movies:1}}])await t.test(JSON.stringify(response),async()=>{
  const h=harness();h.handler=()=>response;await h.ctx.finishTraktCheckIn();A.equal(h.ctx.state.media[0].plays,0);A.equal(h.read().status,'uncertain-finish');A.equal(h.calls.some(x=>x.method==='DELETE'),false);
 });
});
test('A lost finish reply is verified on repeated clicks, never posted twice',async()=>{
 const h=harness();h.handler=path=>{if(path==='/sync/history')throw Error('Lost reply');return [];};
 await h.ctx.finishTraktCheckIn();A.equal(h.read().status,'uncertain-finish');
 await h.ctx.finishTraktCheckIn();await h.ctx.verifyTraktCheckIn();await h.ctx.finishTraktCheckIn();
 A.equal(posts(h).length,1);A.equal(h.read().status,'uncertain-finish');A.equal(h.ctx.state.media[0].plays,0);
});
test('Persisted finishing intent after a crash cannot create another history write',async()=>{
 const h=harness();h.data.set(KEY,C.encode(C.beginFinish(h.session,Date.now()-1000)));await h.ctx.finishTraktCheckIn();A.equal(posts(h).length,0);A.equal(h.read().status,'uncertain-finish');
});
test('An uncertain finish is recovered from remote history before runtime elapses',async()=>{
 const h=harness();const pending=C.uncertain(C.beginFinish(h.session,Date.now()-1000),'finish','Lost reply');h.data.set(KEY,C.encode(pending));h.handler=()=>[historyRow(pending)];
 await h.ctx.finishTraktCheckIn();A.equal(posts(h).length,0);A.equal(h.ctx.state.media[0].plays,1);A.equal(h.read(),null);
});
test('Known remote rejection permits a fresh explicit finish, without a guessed local watch',async()=>{
 const h=harness();h.handler=()=>{throw Object.assign(Error('Rejected'),{status:422});};await h.ctx.finishTraktCheckIn();A.equal(h.read().status,'active');A.equal(h.read().finishAt,null);A.equal(h.ctx.state.media[0].plays,0);
 h.handler=null;await h.ctx.finishTraktCheckIn();A.equal(posts(h).length,2);A.equal(h.ctx.state.media[0].plays,1);
});
test('History not_found remains unviewed and retryable after metadata is corrected',async()=>{
 const h=harness();h.handler=()=>({added:{movies:0},not_found:{movies:[{ids:{tmdb:101}}]}});await h.ctx.finishTraktCheckIn();A.equal(h.read().status,'active');A.equal(h.read().finishAt,null);A.equal(h.ctx.state.media[0].plays,0);
});
test('Start and finish require durable session storage before any remote POST',async t=>{
 await t.test('start',async()=>{const h=harness({session:false});h.failSet=true;await h.ctx.startTraktCheckIn('movie','film');A.equal(h.calls.length,0);});
 await t.test('finish',async()=>{const h=harness(),raw=h.data.get(KEY);h.failSet=true;await h.ctx.finishTraktCheckIn();A.equal(h.calls.length,0);A.equal(h.data.get(KEY),raw);});
});
test('Local library persistence failure retains a recoverable finish and sends no duplicate',async()=>{
 const h=harness();h.failSave=true;await h.ctx.finishTraktCheckIn();const pending=h.read();A.equal(pending.status,'uncertain-finish');A.equal(h.ctx.state.media[0].plays,0);A.equal(h.calls.some(x=>x.method==='DELETE'),false);
 h.failSave=false;h.handler=()=>[historyRow(pending)];await h.ctx.finishTraktCheckIn();A.equal(posts(h).length,1);A.equal(h.ctx.state.media[0].plays,1);A.equal(h.read(),null);
});
test('Cleanup failure retries only DELETE and keeps one local viewing',async()=>{
 const h=harness();h.cleanup=()=>{throw Error('Cleanup lost');};await h.ctx.finishTraktCheckIn();A.equal(h.read().status,'cleanup');A.equal(h.read().historyApplied,true);A.equal(h.ctx.state.media[0].plays,1);
 h.cleanup=null;await h.ctx.finishTraktCheckIn();A.equal(posts(h).length,1);A.equal(h.ctx.state.media[0].plays,1);A.equal(h.read(),null);
});
test('Cleanup enforces account, application, connection and write permission',async t=>{
 for(const condition of ['account','application','disconnected','readonly'])await t.test(condition,async()=>{
  const h=harness();h.data.set(KEY,C.encode(C.confirmed(C.beginFinish(h.session,Date.now()-1000))));
  if(condition==='account')h.ctx.auth.username='someone-else';if(condition==='application')h.ctx.state._traktAppRef='app-b';if(condition==='disconnected')h.ctx.connected=false;if(condition==='readonly')h.ctx.writable=false;
  await h.ctx.retryCheckInCleanup();A.equal(h.calls.length,0);A.equal(h.read().status,'cleanup');
 });
});
test('Unconfirmed active sessions cannot bypass completion through cleanup',async()=>{
 const h=harness();await h.ctx.retryCheckInCleanup();A.equal(h.calls.length,0);A.equal(h.read().status,'active');
});
test('A late finish reply cannot modify a switched account or replaced library',async t=>{
 for(const change of ['account','library','session'])await t.test(change,async()=>{
  const h=harness();let answer;h.handler=()=>new Promise(resolve=>{answer=resolve;});const pending=h.ctx.finishTraktCheckIn();A.equal(posts(h).length,1);
  if(change==='account'){h.ctx.auth.username='new-user';h.ctx.authGeneration++;}
  if(change==='library')h.ctx.libraryEpoch++;
  if(change==='session')h.data.set(KEY,C.encode({...h.session,id:'different-session'}));
  const preserved=h.data.get(KEY);answer({added:{movies:1}});await pending;
  A.equal(h.ctx.state.media[0].plays,0);A.equal(h.data.get(KEY),preserved);A.equal(h.calls.some(x=>x.method==='DELETE'),false);
 });
});
test('Concurrent clicks and local forget cannot orphan an in-flight request',async()=>{
 const h=harness();let answer;h.handler=()=>new Promise(resolve=>{answer=resolve;});const pending=h.ctx.finishTraktCheckIn();
 await h.ctx.finishTraktCheckIn();await h.ctx.cancelTraktCheckIn();await h.ctx.retryCheckInCleanup();h.ctx.forgetLocalCheckIn();A.equal(h.calls.length,1);A.ok(h.read());
 answer({added:{movies:1}});await pending;A.equal(posts(h).length,1);A.equal(h.read(),null);
});
test('Pending local history changes prevent conflicting check-in writes',async()=>{
 const h=harness({session:false});h.ctx.syncState.queue=[{key:'movie:101',field:'history',uncertain:true}];await h.ctx.startTraktCheckIn('movie','film');A.equal(h.calls.length,0);A.equal(h.read(),null);
 h.data.set(KEY,C.encode(h.session));await h.ctx.finishTraktCheckIn();A.equal(h.calls.length,0);A.equal(h.read().status,'active');
});
test('An active synchronization blocks check-in writes',async()=>{
 const h=harness();h.ctx.traktSyncing=true;await h.ctx.finishTraktCheckIn();await h.ctx.cancelTraktCheckIn();A.equal(h.calls.length,0);A.equal(h.read().status,'active');
});
test('Corrupt sessions cannot be silently replaced and can be explicitly forgotten',async()=>{
 const h=harness();h.data.set(KEY,'{bad');await h.ctx.startTraktCheckIn('movie','film');A.equal(h.calls.length,0);A.equal(h.data.get(KEY),'{bad');A.equal(h.data.get('franchiseguide-trakt-checkin-recovery-v1'),'{bad');
 h.ctx.forgetLocalCheckIn();A.equal(h.data.has(KEY),false);A.equal(h.data.get('franchiseguide-trakt-checkin-recovery-v1'),'{bad');
});
test('A read-only tab cannot discard a valid or corrupt local session',()=>{
 const h=harness();h.ctx.writable=false;const raw=h.data.get(KEY);h.ctx.forgetLocalCheckIn();A.equal(h.data.get(KEY),raw);h.data.set(KEY,'{bad');h.ctx.forgetLocalCheckIn();A.equal(h.data.get(KEY),'{bad');
});
test('Storage access denial produces a recoverable read error',()=>{const h=harness();h.failRead=true;A.equal(h.read(),null);A.match(h.eval('checkInLoadError'),/inaccessible/);});
test('Unknown-duration finish attempts still switch to verify-only after persistence',()=>{
 const h=harness(),s={...h.session,runtimeMinutes:null,expectedEndAt:null};A.equal(C.completionMode(s),'write');A.equal(C.completionMode(C.beginFinish(s)),'verify');A.equal(C.completionMode(C.uncertain(C.beginFinish(s),'finish','timeout')),'verify');A.equal(C.completionMode(C.confirmed(C.beginFinish(s))),'cleanup');
});
