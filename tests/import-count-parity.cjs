'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),I=require('../web/import-core.js');
const s={media:[{id:'s',kind:'show',title:'Série QA',added:true,lastWatchedAt:1000,userNote:'Note série privée'},{id:'m',kind:'movie',title:'Film QA',added:true,watched:true,plays:2,watchHistory:[1000,2000],lastWatchedAt:2000,userNote:'Note film privée',rating:8},{id:'g',kind:'game',added:true},{id:'b',kind:'book',added:true}],episodes:[{id:'e',showId:'s',watched:true,watchHistory:[1000,1001],lastWatchedAt:1000,userNote:'Note épisode privée',rating:9}],lists:[{}],customFranchises:[{}],gaming:{accounts:[{}],sets:[{achievements:[{}]}]}};
assert.deepEqual(I.count(s),JSON.parse(fs.readFileSync(process.argv[2],'utf8')));
console.log('Import review counter parity: JavaScript and Kotlin match on explicit four-media fixture, including private series note and dates.');
