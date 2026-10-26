/* Extract unchanged snippets from the supplied V0.28 ZIP for before/after evidence. */
'use strict';
const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const base=process.argv[2]||path.join(__dirname,'fixtures/v029-baseline'),current=path.join(__dirname,'../web'),C=require('../web/core.js'),R=require('../web/reliability-core.js');
const src=(root,name,start,end)=>{const s=fs.readFileSync(path.join(root,name),'utf8');return s.slice(s.indexOf(start),s.indexOf(end,s.indexOf(start)));};
function dates(root){const c=vm.createContext({FGCore:C,FGReliability:R});vm.runInContext(src(root,'backup.js','function upsertInto(','function stageSeriesGuide(')+';globalThis.go=upsertInto;',c);const state={media:[{id:'a',kind:'movie',tmdbId:1,watched:true,plays:1,lastWatchedAt:100}]};c.go(state,{id:'b',kind:'movie',tmdbId:1,watched:true,plays:1,lastWatchedAt:200});return state.media[0];}
async function empty(root){const c=vm.createContext({FGReliability:R,Headers,URL,URLSearchParams,state:{settings:{}},kindFilter:'movie',ensureAutomaticServices:async()=>{},serviceCredential:()=> 'fixture-not-a-real-key',fetchJson:async()=>({}),tmdbSearchItem:x=>x});let s=src(root,'app.js','async function remoteSearch(','async function enrichTrakt(');if(root===current)s=src(root,'app.js','function searchProviderIds(','async function enrichTrakt(');vm.runInContext(s+';globalThis.go=remoteSearch;',c);return c.go('test','movie',1,true);}
(async()=>{const before=dates(base),after=dates(current),old=await empty(base),fresh=await empty(current);const B=require(path.join(base,'integrity.js')),I=require('../web/integrity.js');const a={id:'a',title:'Test',kind:'show',source:'tvmaze',sourceId:'100'},b={...a,id:'b',sourceId:'200'};
const report={base:'Supplied V0.28 sources, unmodified snippets',cases:[
 {name:'Imported distinct dates lost before counter grows',before:{plays:before.plays,dates:before.watchHistory},after:{plays:after.plays,dates:after.watchHistory},reproduced:before.watchHistory.length===1&&after.watchHistory.length===2},
 {name:'Missing result array incorrectly reported as success-empty',before:old,after:fresh,reproduced:old.complete&&!fresh.complete},
 {name:'Conflicting public TV supplier identities permitted for merge',before:B.mergeAllowed(a,b),after:I.mergeAllowed(a,b),reproduced:B.mergeAllowed(a,b).ok&&!I.mergeAllowed(a,b).ok}
]};console.log(JSON.stringify(report,null,2));if(report.cases.some(c=>!c.reproduced))process.exitCode=1;})();
