'use strict';const fs=require('node:fs'),assert=require('node:assert/strict'),E=require('../web/episodes-core.js');
const at=Date.parse('2026-10-06T12:00:00Z'),e=(i,other={})=>({id:'e'+i,showId:'s',season:1,num:i,title:'Épisode e'+i,date:'2026-01-01',tmdbId:100+i,watched:false,plays:0,watchHistory:[],...other});
const s={media:[{id:'s',kind:'show',title:'Série test',added:true,tmdbId:123,lastWatchedAt:at-1000}],episodes:[e(1),e(2,{watched:true,plays:3,lastWatchedAt:at-1000,watchHistory:[at-2000,at-1000],userNote:'Privé',rating:8}),e(3,{skipped:true}),e(4,{date:'2030-01-01'}),e(5,{date:null}),e(6,{season:0})]};
const result=[['watch',['e1','e2','e3','e4','e5']],['skip',['e1','e2']],['unwatch',['e1','e2']],['collect',['e2','e3','e4']]].map(([action,ids])=>E.makePlan(s,'s',ids,action,{at,today:'2026-10-06'}).counts);
assert.deepEqual(result,JSON.parse(fs.readFileSync(process.argv[2],'utf8')));console.log('Episode batch counts JS/Kotlin parity: passed (four actions).');
