'use strict';
const {test}=require('node:test');
const A=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const {spawnSync}=require('node:child_process');
const Core=require('../web/core.js');
const Episodes=require('../web/episodes-core.js');
const now=new Date(2026,10,7,12).getTime();
for(const [date,allowed] of [['2026-11-06',true],['2026-11-07',true],['2026-11-08',false],[null,true],['',true],['not-a-date',true],['2026-02-30',true]]) {
 test('Future guard: '+date,()=>{
  const episode={id:'e',date,watched:false};const before=structuredClone(episode);
  if(allowed)A.strictEqual(Core.requireEpisodeNotFuture(episode,now),episode);
  else A.throws(()=>Core.requireEpisodeNotFuture(episode,now),{code:'EPISODE_FUTURE'});
  A.deepEqual(episode,before);
 });
}
test('Either conflicting date alias can prevent a future write',()=>{
 A.throws(()=>Core.requireEpisodeNotFuture({date:'2026-11-06',airDate:'2026-11-08'},now),{code:'EPISODE_FUTURE'});
});
test('Invalid clock is not silently accepted',()=>A.throws(()=>Core.requireEpisodeNotFuture({},NaN)));
test('Legacy batch consent allows unknown dates but never future ones',()=>{
 const state={media:[{id:'s',kind:'show',added:true}],episodes:[{id:'e',showId:'s',season:1,num:1,date:'2099-01-01'},{id:'u',showId:'s',season:1,num:2,date:null}]};
 const plan=Episodes.makePlan(state,'s',['e','u'],'watch',{at:now,today:'2026-11-07',includeUndatedFuture:true});
 A.equal(plan.counts.changed,1);A.equal(plan.counts.dateProtected,1);
 const applied=Episodes.applyPlan(state,plan,now);A.notEqual(applied.episodes[0].watched,true);A.equal(applied.episodes[1].watched,true);A.equal(applied.episodes[1].date,null);
});
for(const [zone,allowed] of [['Europe/Paris',true],['UTC',true],['America/Los_Angeles',false],['Pacific/Auckland',true]]) {
 test('Guard respects local calendar day in '+zone,()=>{
  const script=`const C=require('./web/core.js');try{C.requireEpisodeNotFuture({date:'2026-11-07'},Date.parse('2026-11-07T00:30:00Z'));console.log('allowed')}catch(e){console.log(e.code)}`;
  const run=spawnSync(process.execPath,['-e',script],{cwd:require('node:path').resolve(__dirname,'..'),env:{...process.env,TZ:zone},encoding:'utf8',timeout:5000});
  A.equal(run.status,0);A.equal(run.stdout.trim(),allowed?'allowed':'EPISODE_FUTURE');
 });
}
for(const action of ['watch','replay'])test('Real series event binding refuses future '+action,()=>{
 const episode={id:'e',showId:'s',date:'2099-11-07',watched:false};
 const button={dataset:{eid:'e',ep:action}},messages=[];
 const context={FGCore:Core,FGEpisodes:Episodes,state:{episodes:[episode]},toast:x=>messages.push(x),document:{},
  save(){throw Error('No write permitted');},render(){throw Error('No render required');},window:{},
  episodesFor:()=>[episode]};
 vm.createContext(context);vm.runInContext(fs.readFileSync(require.resolve('../web/views.js'),'utf8'),context);
 context.bindEpisodeActions({id:'s'},{querySelectorAll:()=>[button]});button.onclick();
 A.equal(episode.watched,false);A.equal(messages.length,1);A.match(messages[0],/future/);
});
test('An episode check-in is rejected before any storage or network write',async()=>{
 const episode={id:'e',showId:'s',tmdbId:1,date:'2099-11-07'};let writes=0;const messages=[];
 const context={FGCore:Core,FGCheckIn:require('../web/trakt-checkin-core.js'),state:{media:[{id:'s',kind:'show',title:'Fixture',added:true}],episodes:[episode]},
  localStorage:{getItem:()=>null,setItem(){writes++;}},document:{addEventListener(){}},
  assertWritable(){},traktConnected:()=>true,traktSyncing:false,syncState:{queue:[]},
  toast:x=>messages.push(x),confirm:()=>true,Date,JSON,TextEncoder};
 vm.createContext(context);vm.runInContext(fs.readFileSync(require.resolve('../web/trakt-checkin-ui.js'),'utf8'),context);
 await context.startTraktCheckIn('episode','e');A.equal(writes,0);A.equal(messages.length,1);A.match(messages[0],/future/);
});
