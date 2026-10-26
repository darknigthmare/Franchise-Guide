'use strict';
const {test}=require('node:test');
const A=require('node:assert/strict');
const R=require('../web/retro-core.js');
const G=require('../web/gaming-core.js');
const C=require('../web/core.js');
const USER='00003EMFWR7XB8SDPEHB3K56ZQ',OTHER='00003EMFWR7XB8SDPEHB3K56ZR',AT=Date.UTC(2026,9,5,12);
const copy=x=>JSON.parse(JSON.stringify(x));
const state=()=>({schema:8,media:[],episodes:[],lists:[],settings:{},gaming:{version:1,accounts:[],sets:[]}});
function profileBundle(){return {provider:'retro',user:USER,profile:{User:'Fixture_Player',ULID:USER,UserPic:'/UserPic/Fixture_Player.png'},fetchedAt:AT};}
function row(id=1,x={}){return {GameID:id,Title:'Jeu fictif '+id,ConsoleID:1,ConsoleName:'Mega Drive / Genesis',ImageIcon:'/Images/000001.png',MaxPossible:2,NumAwarded:1,NumAwardedHardcore:0,MostRecentAwardedDate:'2025-02-28T15:30:00+00:00',HighestAwardKind:null,HighestAwardDate:null,...x};}
function pageBundle(rows=[row()],total=rows.length){return {provider:'retro',user:USER,library:{Count:rows.length,Total:total,Results:rows},fetchedAt:AT};}
function library(rows=[row()]){return R.library([R.page(pageBundle(rows),USER,0,Math.max(1,rows.length))],R.profile(profileBundle(),USER));}
const binding=(x={})=>({id:'retro-list',mediaId:'game',accountId:'ra',provider:'retro',accountExternalId:USER,externalGameId:'1',title:'Jeu fictif',platform:'Mega Drive / Genesis',edition:'RetroAchievements',...x});
function achievementRaw(id=1,x={}){return {ID:id,Title:'Succès fictif '+id,Description:'Objectif de test',Points:5,BadgeName:'000001',DisplayOrder:id,NumAwarded:10,NumAwardedHardcore:4,Type:null,...x};}
function achievementsBundle(){return {provider:'retro',user:USER,gameId:'1',fetchedAt:AT,game:{ID:1,Title:'Jeu fictif RA',ConsoleID:1,ConsoleName:'Mega Drive / Genesis',ParentGameID:null,NumDistinctPlayers:100,NumAchievements:3,NumAwardedToUser:2,NumAwardedToUserHardcore:1,
 Achievements:{'1':achievementRaw(1,{DateEarned:'2025-02-28 15:30:00',DateEarnedHardcore:'2025-03-01 16:00:00',Type:'missable'}),'2':achievementRaw(2,{DateEarned:'2025-02-28T16:30:00+01:00'}),'3':achievementRaw(3)}}};}

test('RA profile has a stable ULID and a separate display username; no SSO proof is created',()=>{
 const b=profileBundle();b.profile.apiKey='SECRET';b.accessToken='SECRET';const p=R.profile(b,'fixture_player');
 A.equal(p.ulid,USER);A.equal(p.username,'Fixture_Player');A.equal(p.label,'Fixture_Player');A.equal(p.avatarUrl,'https://retroachievements.org/UserPic/Fixture_Player.png');A.ok(!JSON.stringify(p).includes('SECRET'));A.equal(p.authenticated,undefined);
});
for(const [name,change,expected] of [
 ['wrong provider',b=>b.provider='steam',USER],['wrong envelope user',b=>b.user=OTHER,USER],['missing stable identifier',b=>delete b.profile.ULID,USER],
 ['invalid ULID alphabet',b=>{b.user=b.profile.ULID='O'.repeat(26);},undefined],['future ambiguous username mismatch',()=>{},'Other_Player'],['invalid username',b=>b.profile.User='bad/<script>',USER],
 ['empty response',b=>b.profile=[],USER],['missing observation time',b=>delete b.fetchedAt,USER]
])test('RA rejects profile '+name,()=>{const b=profileBundle();change(b);A.throws(()=>R.profile(b,expected));});
test('RA resolves a renamed profile using its original ULID',()=>{const b=profileBundle();b.profile.User='New_Player';A.equal(R.profile(b,USER).username,'New_Player');});
test('RA unsafe external avatar is ignored',()=>{const b=profileBundle();b.profile.UserPic='https://attacker.example/key.png';A.equal(R.profile(b).avatarUrl,null);});

test('RA pages combine in offset order and preserve console identity',()=>{
 const p=R.profile(profileBundle()),a=R.page(pageBundle([row(1),row(2)],3),USER,0,2),b=R.page(pageBundle([row(3)],3),USER,2,2);
 const list=R.library([b,a],p);A.deepEqual(list.games.map(g=>g.gameId),['1','2','3']);A.equal(list.games[0].consoleId,'1');A.equal(list.games[0].numAwarded,1);A.equal(list.complete,true);A.equal(list.total,3);
});
for(const [name,change,offset=0,count=100] of [
 ['different account',b=>b.user=OTHER],['missing results',b=>delete b.library.Results],['wrong count',b=>b.library.Count=2],['negative total',b=>b.library.Total=-1],
 ['string count',b=>b.library.Count='1'],['missing result within page',b=>b.library.Total=2],['duplicate game IDs',b=>{b.library.Results=[row(1),row(1)];b.library.Count=b.library.Total=2;}],
 ['string game ID',b=>b.library.Results[0].GameID='1'],['zero console ID',b=>b.library.Results[0].ConsoleID=0],['unsafe numeric ID',b=>b.library.Results[0].GameID=Number.MAX_SAFE_INTEGER],
 ['impossible unlock count',b=>b.library.Results[0].NumAwarded=3],['Hardcore greater than normal',b=>b.library.Results[0].NumAwardedHardcore=2],
 ['missing total achievements',b=>delete b.library.Results[0].MaxPossible],['impossible date',b=>b.library.Results[0].MostRecentAwardedDate='2025-02-30T00:00:00Z'],
 ['invalid date offset',b=>b.library.Results[0].MostRecentAwardedDate='2025-02-28T00:00:00+14:01'],['unknown award',b=>b.library.Results[0].HighestAwardKind='platinum'],
 ['award date without award',b=>b.library.Results[0].HighestAwardDate='2025-02-28T00:00:00Z'],['invalid offset',()=>{},-1],['zero page size',()=>{},0,0],['excessive page size',()=>{},0,501]
])test('RA rejects page '+name,()=>{const b=pageBundle();change(b);A.throws(()=>R.page(b,USER,offset,count));});
test('RA nullable dates and awards stay unknown, not epoch or completion',()=>{const list=library([row(1,{MostRecentAwardedDate:null})]);A.equal(list.games[0].mostRecentAwardedAt,null);A.equal(list.games[0].highestAwardKind,null);});
test('RA refuses missing pagination, changed totals and duplicate IDs across pages',()=>{
 const p=R.profile(profileBundle()),a=R.page(pageBundle([row(1)],2),USER,0,1);
 A.throws(()=>R.library([a],p));A.throws(()=>R.library([],p));
 A.throws(()=>R.library([a,R.page(pageBundle([row(2)],3),USER,1,1)],p));
 A.throws(()=>R.library([a,R.page(pageBundle([row(1)],2),USER,1,1)],p));
 A.throws(()=>R.library([a,R.page(pageBundle([row(2)],2),USER,1,1)],{...p,ulid:OTHER}));
});
test('RA refuses forged normalized partial input at planning time',()=>{const v=library();v.total=2;A.throws(()=>R.plan(state(),v));v.total=1;delete v.complete;A.throws(()=>R.plan(state(),v));});

test('RA import creates a stable public profile and game bindings without inventing possession or campaign completion',()=>{
 const p=R.plan(state(),library([row(1,{HighestAwardKind:'mastered',HighestAwardDate:'2025-02-28T00:00:00Z'})]),{autoImport:true,autoSync:true}),m=p.state.media[0],a=p.state.gaming.accounts[0],s=p.state.gaming.sets[0];
 A.equal(m.source,'retro');A.equal(m.sourceId,'1');A.equal(m.collected,false);A.equal(m.watched,false);A.equal(m.started,false);A.equal(m.plays,0);A.deepEqual(m.watchHistory,[]);A.equal(m.platforms[0],'Mega Drive / Genesis');
 A.equal(a.externalId,USER);A.equal(a.username,'Fixture_Player');A.equal(a.autoImport,true);A.equal(a.autoSync,true);A.equal(s.externalGameId,'1');A.equal(s.completeCatalog,false);A.equal(G.progress(s).platinum,false);A.equal(C.validateBackup(p.state),true);
});
test('RA empty library is non-destructive even though upstream also uses it for absent users',()=>{
 const old=state();old.media=[{id:'old',kind:'game',title:'Garder',added:true}];const p=R.plan(old,library([]));A.equal(p.report.empty,true);A.equal(p.report.added,0);A.equal(p.state.media.length,1);
});
test('RA repeat import preserves notes, personal progress, favorites, lists and achievement checklists',()=>{
 let p=R.plan(state(),library());const media=p.state.media[0],set=p.state.gaming.sets[0];Object.assign(media,{rating:9,favorite:true,watched:true,collected:true,userNote:'Ma note',listIds:['list-1']});
 const s=R.achievements(achievementsBundle(),{...set,accountExternalId:USER},AT);s.achievements[0].note='Préparation';s.achievements[0].localDone=true;s.achievements[0].pinned=true;p.state.gaming.sets[0]=s;
 p=R.plan(p.state,library());A.equal(p.report.added,0);A.equal(p.state.media.length,1);A.equal(p.state.gaming.sets.length,1);A.equal(p.state.media[0].userNote,'Ma note');A.equal(p.state.media[0].favorite,true);A.equal(p.state.media[0].collected,true);A.equal(p.state.gaming.sets[0].achievements[0].note,'Préparation');A.equal(p.state.gaming.sets[0].achievements[0].localDone,true);
});
test('RA two profiles share an exactly associated game but keep separate success records',()=>{
 const first=R.plan(state(),library());const pb=profileBundle();pb.user=pb.profile.ULID=OTHER;pb.profile.User='Other_Player';const pageB=pageBundle();pageB.user=OTHER;
 const second=R.plan(first.state,R.library([R.page(pageB,OTHER,0)],R.profile(pb)));A.equal(second.state.media.length,1);A.equal(second.state.gaming.accounts.length,2);A.equal(second.state.gaming.sets.length,2);A.notEqual(second.state.gaming.sets[0].accountId,second.state.gaming.sets[1].accountId);
});
test('RA never matches a title alone',()=>{const x=state();x.media=[{id:'rawg',kind:'game',title:'Jeu fictif 1',source:'rawg',sourceId:'1'}];A.equal(R.plan(x,library()).state.media.length,2);});
test('RA reuses an explicit imported game binding and reports ambiguous links',()=>{
 const p=R.plan(state(),library());p.state.media[0].source='rawg';p.state.media[0].sourceId='321';A.equal(R.plan(p.state,library()).state.media.length,1);
 p.state.media.push({id:'duplicate',kind:'game',source:'retro',sourceId:'1',title:'Duplicate'});const r=R.plan(p.state,library());A.equal(r.report.conflicts.length,1);A.equal(r.report.added,0);
});
test('RA reuses a retained Retro alias on a RAWG primary record without needing an achievement set',()=>{
 const x=state();x.media=[{id:'rawg-primary',kind:'game',title:'Titre personnel',source:'rawg',sourceId:'876',retroGameId:'1',favorite:true,userNote:'À garder'}];
 const p=R.plan(x,library());A.equal(p.state.media.length,1);A.equal(p.report.added,0);A.equal(p.report.updated,1);A.equal(p.state.gaming.sets[0].mediaId,'rawg-primary');A.equal(p.state.gaming.sets[0].externalGameId,'1');
 A.equal(p.state.media[0].source,'rawg');A.equal(p.state.media[0].sourceId,'876');A.equal(p.state.media[0].retroGameId,'1');A.equal(p.state.media[0].userNote,'À garder');A.equal(p.state.media[0].favorite,true);
});
test('RA retained aliases report ambiguous or contradictory identities rather than joining them',()=>{
 const x=state();x.media=[{id:'a',kind:'game',title:'A',source:'rawg',sourceId:'8',retroGameId:'1'},{id:'b',kind:'game',title:'B',source:'retro',sourceId:'1'}];
 const ambiguous=R.plan(x,library());A.equal(ambiguous.report.conflicts.length,1);A.equal(ambiguous.report.added,0);A.equal(ambiguous.state.gaming.sets.length,0);
 x.media=[{id:'a',kind:'game',title:'A',source:'retro',sourceId:'1',retroGameId:'2'}];const contradiction=R.plan(x,library([row(1),row(2)]));A.equal(contradiction.report.conflicts.length,2);A.equal(contradiction.report.added,0);A.equal(contradiction.state.gaming.sets.length,0);
});
test('RA plan leaves source state unchanged and rejects malformed consent',()=>{const x=state(),before=JSON.stringify(x);R.plan(x,library());A.equal(JSON.stringify(x),before);A.throws(()=>R.plan(x,library(),{autoImport:'yes'}));});
test('RA ULID account can change its username while retaining custom label and records',()=>{
 const p=R.plan(state(),library());p.state.gaming.accounts[0].label='Mon profil rétro';const pb=profileBundle();pb.profile.User='Renamed_Player';
 const r=R.plan(p.state,R.library([R.page(pageBundle(),USER,0)],R.profile(pb,USER)));A.equal(r.state.gaming.accounts.length,1);A.equal(r.state.gaming.accounts[0].username,'Renamed_Player');A.equal(r.state.gaming.accounts[0].label,'Mon profil rétro');A.equal(G.accountUrl(r.state.gaming.accounts[0]),'https://retroachievements.org/user/Renamed_Player');
});
test('RA 2100-game import preserves all games and explicitly defers sets above the 2000 cap',()=>{
 const rows=Array.from({length:2100},(_,i)=>row(i+1)),pages=[];for(let offset=0;offset<rows.length;offset+=500)pages.push(R.page(pageBundle(rows.slice(offset,offset+500),rows.length),USER,offset,500));
 const p=R.plan(state(),R.library(pages,R.profile(profileBundle())));A.equal(p.state.media.length,2100);A.equal(p.state.gaming.sets.length,2000);A.equal(p.report.deferred,100);
 // A controlled merge can retain a RAWG primary record for a deferred RA game.
 const deferred=p.state.media.find(m=>m.sourceId==='2001');deferred.source='rawg';deferred.sourceId='902001';deferred.retroGameId='2001';
 const repeat=R.plan(p.state,library([row(2001)]));A.equal(repeat.state.media.length,2100);A.equal(repeat.report.added,0);A.equal(repeat.report.updated,1);A.equal(repeat.report.deferred,1);A.equal(repeat.state.gaming.sets.length,2000);
});

test('RA detailed set keeps normal and Hardcore states separate with UTC dates',()=>{
 const s=R.achievements(achievementsBundle(),binding(),AT),p=G.progress(s);A.equal(s.source,'retro-api');A.equal(s.completeCatalog,true);A.equal(p.earned,2);A.equal(p.hardcore,1);A.equal(p.platinum,false);
 A.equal(s.achievements[0].earnedAt,Date.UTC(2025,1,28,15,30));A.equal(s.achievements[0].hardcoreEarnedAt,Date.UTC(2025,2,1,16));A.equal(s.achievements[1].earnedAt,Date.UTC(2025,1,28,15,30));A.equal(s.achievements[1].hardcoreEarned,false);A.equal(s.achievements[2].earned,false);A.equal(s.achievements[2].hardcoreEarned,false);
 A.equal(s.achievements[0].missable,true);A.equal(s.achievements[0].points,5);A.equal(s.achievements[0].rarity.percent,10);A.match(s.achievements[0].iconUrl,/\/Badge\/000001\.png$/);
});
test('RA subsets remain independent IDs and never become implicit DLC or a platinum',()=>{const b=achievementsBundle();b.game.ParentGameID=2;b.game.HighestAwardKind='mastered';const s=R.achievements(b,binding(),AT);A.equal(s.externalGameId,'1');A.equal(s.groups[0].kind,'unknown');A.equal(s.groups[0].name,'Sous-ensemble RetroAchievements');A.ok(s.achievements.every(a=>a.grade==='achievement'));});
test('RA legacy lowercase type is accepted and an omitted type stays unknown',()=>{const b=achievementsBundle();delete b.game.Achievements['1'].Type;b.game.Achievements['1'].type='missable';delete b.game.Achievements['3'].Type;const s=R.achievements(b,binding(),AT);A.equal(s.achievements[0].missable,true);A.equal(s.achievements[2].missable,null);});
test('RA weighted TrueRatio is never presented as a rarity percent',()=>{const b=achievementsBundle();delete b.game.NumDistinctPlayers;b.game.Achievements['1'].TrueRatio=999;const s=R.achievements(b,binding(),AT);A.equal(s.achievements[0].rarity,null);});
test('RA valid zero-achievement object is uncompleted while absent game array is rejected',()=>{
 const b=achievementsBundle();Object.assign(b.game,{NumAchievements:0,NumAwardedToUser:0,NumAwardedToUserHardcore:0,Achievements:{}});const s=R.achievements(b,binding(),AT);A.equal(s.catalogStatus,'none-exposed');A.equal(s.completeCatalog,false);A.equal(G.progress(s).completed,false);A.equal(G.progress(s).platinum,false);
 b.game=[];A.throws(()=>R.achievements(b,binding(),AT));
});
for(const [name,change] of [
 ['wrong envelope account',b=>b.user=OTHER],['wrong envelope game',b=>b.gameId='2'],['wrong raw game',b=>b.game.ID=2],['invalid console',b=>b.game.ConsoleID=0],
 ['missing catalogue',b=>delete b.game.Achievements],['catalogue array',b=>b.game.Achievements=[]],['missing definition',b=>delete b.game.Achievements['3']],['extra definition',b=>b.game.Achievements['4']=achievementRaw(4)],
 ['duplicate achievement ID',b=>b.game.Achievements['3'].ID=2],['string achievement ID',b=>b.game.Achievements['3'].ID='3'],['missing personal counts',b=>delete b.game.NumAwardedToUser],
 ['inconsistent unlock count',b=>b.game.NumAwardedToUser=3],['inconsistent Hardcore count',b=>b.game.NumAwardedToUserHardcore=2],['Hardcore without normal',b=>delete b.game.Achievements['1'].DateEarned],
 ['invalid calendar date',b=>b.game.Achievements['1'].DateEarned='2025-02-30 15:30:00'],['ISO without timezone',b=>b.game.Achievements['1'].DateEarned='2025-02-28T15:30:00'],
 ['future unlock',b=>b.game.Achievements['1'].DateEarned='2027-01-01 00:00:00'],['future observation',b=>b.fetchedAt=AT+600000],
 ['unknown type',b=>b.game.Achievements['3'].Type='made-up'],['conflicting case type',b=>b.game.Achievements['1'].type='progression'],['negative points',b=>b.game.Achievements['1'].Points=-1]
])test('RA refuses detailed '+name+' without mutating the old binding',()=>{const b=achievementsBundle(),link=binding(),before=JSON.stringify(link);change(b);A.throws(()=>R.achievements(b,link,AT));A.equal(JSON.stringify(link),before);});
test('RA missing dates in a complete snapshot mean locked; incomplete dates never erase an old record',()=>{
 const old=R.achievements(achievementsBundle(),binding(),AT),b=achievementsBundle();delete b.game.Achievements['1'].DateEarned;const before=JSON.stringify(old);A.throws(()=>G.mergeSet(old,R.achievements(b,binding(),AT)));A.equal(JSON.stringify(old),before);
});
test('RA live refresh preserves personal notes, pinned objectives, guides and checklists',()=>{
 const old=R.achievements(achievementsBundle(),binding(),AT);Object.assign(old.achievements[0],{localDone:true,pinned:true,note:'Garder',guideUrl:'https://example.org/guide'});
 const fresh=G.mergeSet(old,R.achievements(achievementsBundle(),binding(),AT));A.equal(fresh.achievements[0].note,'Garder');A.equal(fresh.achievements[0].guideUrl,'https://example.org/guide');A.equal(fresh.achievements[0].localDone,true);A.equal(fresh.achievements[0].pinned,true);
});
test('RA 5000 achievements accepted and 5001 refused; no inferred platinum at 100%',()=>{
 const b=achievementsBundle();b.game.NumAchievements=5000;b.game.NumAwardedToUser=5000;b.game.NumAwardedToUserHardcore=0;b.game.Achievements=Object.fromEntries(Array.from({length:5000},(_,i)=>[String(i+1),achievementRaw(i+1,{DateEarned:'2025-02-28 15:30:00'})]));
 const s=R.achievements(b,binding(),AT);A.equal(s.achievements.length,5000);A.equal(G.progress(s).completed,true);A.equal(G.progress(s).platinum,false);b.game.NumAchievements=5001;b.game.Achievements['5001']=achievementRaw(5001);A.throws(()=>R.achievements(b,binding(),AT));
});

test('RA account additions remain compatible with legacy name accounts and schema 8',()=>{
 const legacy=G.normalizeAccount({id:'legacy',provider:'retro',externalId:'Old_User',label:'Ancien compte'});A.equal(legacy.username,undefined);A.equal(G.accountUrl(legacy),'https://retroachievements.org/user/Old_User');
 const a=G.normalizeAccount({id:'ra',provider:'retro',externalId:USER,username:'Fixture_Player',label:'Rétro',autoImport:true});A.equal(a.autoImport,true);A.equal(G.accountKey(legacy),'retro:Old_User');A.equal(G.accountKey(a),'retro:'+USER);
 const x=state();x.gaming.accounts=[legacy,a];A.equal(C.validateBackup(x),true);A.throws(()=>G.normalizeAccount({...a,username:'bad/name'}));
});
test('RA set export preserves optional username hint; import validates it but cannot create a live source',()=>{
 const a=G.normalizeAccount({id:'ra',provider:'retro',externalId:USER,username:'Fixture_Player',label:'Rétro'}),s=R.achievements(achievementsBundle(),binding(),AT),x=G.exportSet(s,a);A.equal(x.version,1);A.equal(x.accountUsername,'Fixture_Player');
 const imported=G.parseImport(JSON.stringify(x),binding());A.equal(imported.source,'file-import');A.equal(imported.lastSyncAt,null);x.accountUsername='../../bad';A.throws(()=>G.parseImport(JSON.stringify(x),binding()));
});
test('Manual checklist cannot manufacture a Hardcore unlock or its date',()=>{
 const s=G.normalizeSet({id:'m',mediaId:'g',provider:'retro',externalGameId:'1',title:'Liste personnelle',source:'manual',achievements:[{id:'a',title:'Objectif',earned:null,hardcoreEarned:null,localDone:true}]});A.equal(G.progress(s).hardcore,0);
 A.throws(()=>G.normalizeSet({...s,achievements:[{...s.achievements[0],hardcoreEarned:true}]}));A.throws(()=>G.normalizeSet({...s,achievements:[{...s.achievements[0],hardcoreEarned:true,hardcoreEarnedAt:AT}]}));
});
test('Previously imported explicit grades remain valid while actual RA normalization always uses achievement',()=>{const s=R.achievements(achievementsBundle(),binding(),AT);s.achievements[0].grade='platinum';A.equal(G.normalizeSet(s).achievements[0].grade,'platinum');});
test('Hardcore filters keep earned, remaining and unknown distinct from normal unlocks',()=>{
 const s=R.achievements(achievementsBundle(),binding(),AT);
 s.achievements[2].hardcoreEarned=null;
 const ids=filter=>G.filterAchievements(s,{state:filter}).map(a=>a.id);
 A.deepEqual(ids('hardcore-earned'),['1']);
 A.deepEqual(ids('hardcore-remaining'),['2','3']);
 A.deepEqual(ids('hardcore-unknown'),['3']);
 A.deepEqual(ids('earned'),['1','2']);
 A.deepEqual(ids('locked'),['3']);
 A.deepEqual(ids('unknown'),[]);
});
test('Hardcore filters combine with search and group while a personal checkbox stays independent',()=>{
 const s=R.achievements(achievementsBundle(),binding(),AT);s.achievements[1].localDone=true;
 A.deepEqual(G.filterAchievements(s,{state:'hardcore-remaining',query:'fictif 2',group:'core'}).map(a=>a.id),['2']);
 A.deepEqual(G.filterAchievements(s,{state:'hardcore-remaining',group:'missing'}),[]);
 A.deepEqual(G.filterAchievements(s,{state:'hardcore-unknown'}),[]);
});
