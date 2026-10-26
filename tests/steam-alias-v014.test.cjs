'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),S=require('../web/steam-library.js');
const steamid='76561198000000001';
const library=S.normalize({provider:'steam',steamid,fetchedAt:1700000000000,library:{response:{game_count:1,games:[{appid:20,name:'Fixture Steam Game',has_community_visible_stats:true}]}}},steamid);
function state(media){return {media,gaming:{version:1,accounts:[],sets:[]}};}
test('Steam reimport reuses an identity retained by a cross-catalogue merge',()=>{
 const before=state([{id:'merged',kind:'game',title:'My title',source:'rawg',sourceId:'42',steamAppId:'20',userNote:'Keep this note',favorite:true}]);
 const next=S.plan(before,library);assert.equal(next.state.media.length,1);assert.equal(next.state.media[0].userNote,'Keep this note');assert.equal(next.state.media[0].favorite,true);assert.equal(next.state.gaming.sets[0].mediaId,'merged');assert.equal(next.report.added,0);assert.equal(before.media[0].steam,undefined);
});
test('A conflicting Steam alias cannot overwrite the source identity',()=>{
 const before=state([{id:'conflicting',kind:'game',title:'Different edition',source:'steam',sourceId:'21',steamAppId:'20'}]);
 const next=S.plan(before,library);assert.equal(next.report.conflicts.length,1);assert.equal(next.state.gaming.sets.length,0);assert.equal(next.state.media[0].sourceId,'21');assert.equal(next.state.media[0].steam,undefined);
});
