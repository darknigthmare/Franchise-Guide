const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const C=require('../web/core.js');
const root=path.join(__dirname,'..');
const backup=()=>({schema:6,media:[{id:'movie',kind:'movie',title:'Titre'}],episodes:[],lists:[],suggestions:[],customFranchises:[]});
for(const [name,text] of [['empty',''],['four words','Un avis trop bref'],['characters not words','abcdef'],['whitespace',' \n\t ']]){
 test('Review rejects '+name,()=>assert.throws(()=>C.reviewText(text),/5 mots/));
}
test('Review accepts exactly five words',()=>assert.equal(C.reviewText('Un avis comporte cinq mots').words,5));
test('Review normalizes whitespace',()=>assert.equal(C.reviewText('  Un\n avis\t comporte cinq mots  ').text,'Un\n avis\t comporte cinq mots'));
test('Review 199 words is not long review',()=>assert.equal(C.reviewText(Array(199).fill('mot').join(' ')).review,false));
test('Review 200 words is long review',()=>assert.equal(C.reviewText(Array(200).fill('mot').join(' ')).review,true));
test('Review limits local draft length',()=>assert.throws(()=>C.reviewText('un deux trois quatre '+ 'a'.repeat(10000)),/10 000/));
for(const bad of ['https://alice:private@example.org/x','https://secret@example.org','javascript:alert(1)','data:text/html,<h1>x</h1>','file:///home/user/a','http://example.org/x']){
 test('Reject unsafe URL '+bad.split(':')[0]+':'+bad.slice(-12),()=>assert.equal(C.safeUrl(bad),null));
}
test('Allow expected HTTPS poster',()=>assert.equal(C.safeUrl('https://image.tmdb.org/t/p/w500/a.png'),'https://image.tmdb.org/t/p/w500/a.png'));
test('Allow local PC address',()=>assert.equal(C.safeUrl('http://127.0.0.1:47631/'),'http://127.0.0.1:47631/'));
test('Bearer token is completely redacted',()=>assert.ok(!C.redact('Authorization: Bearer PRIVATE.DEMO_TOKEN-567').includes('PRIVATE')));
test('Quoted bearer is redacted too',()=>assert.ok(!C.redact('"authorization":"Bearer PRIVATE-DEMO"').includes('PRIVATE')));
test('Query secrets are redacted',()=>assert.ok(!C.redact('https://x.test?api_key=PRIVATE_VALUE&other=yes').includes('PRIVATE_VALUE')));
test('Ordinary diagnostic survives redaction',()=>assert.equal(C.redact('Réseau indisponible HTTP 503'),'Réseau indisponible HTTP 503'));
for(const invalid of [[],1,'raw']){
 test('Reject invalid preferences '+JSON.stringify(invalid),()=>assert.throws(()=>C.validateBackup({...backup(),preferences:invalid})));
}
for(const invalid of [{statusFilters:[]},{statusFilters:{ENDED:7}},{librarySort:[]},{librarySort:{show:null}},{librarySort:{show:'title'}}]){
 test('Reject malformed preference field '+JSON.stringify(invalid),()=>assert.throws(()=>C.validateBackup({...backup(),preferences:invalid})));
}
test('Valid tri-state preferences and sort pass',()=>assert.equal(C.validateBackup({...backup(),preferences:{statusFilters:{ENDED:-1,CONTINUING:1},librarySort:{show:{mode:'title',ascending:true}}}}),true));
for(const invalid of [[],{queue:{}},{remote:'x'},{errors:1}]){
 test('Reject malformed sync envelope '+JSON.stringify(invalid),()=>assert.throws(()=>C.validateBackup({...backup(),_syncState:invalid})));
}
test('Reject future schema before mutation',()=>assert.throws(()=>C.validateBackup({...backup(),schema:12})));
test('Reject fractional schema',()=>assert.throws(()=>C.validateBackup({...backup(),schema:5.5})));
for(const date of ['invalid','2026-02-31','2026-13-10','2026-01-01T12:00:00Z']){
 test('Reject invalid date '+date,()=>{const b=backup();b.media[0].releaseDate=date;assert.throws(()=>C.validateBackup(b))});
}
test('Leap day accepted when real',()=>{const b=backup();b.media[0].releaseDate='2024-02-29';assert.equal(C.validateBackup(b),true)});
test('Invalid play counter rejected',()=>{const b=backup();b.media[0].plays=-2;assert.throws(()=>C.validateBackup(b))});
test('Invalid JS timestamp rejected',()=>{const b=backup();b.media[0].lastWatchedAt=8640000000000001;assert.throws(()=>C.validateBackup(b))});
test('History ignores impossible timestamp even before normalization',()=>assert.deepEqual(C.knownWatchHistory({watched:true,plays:1,lastWatchedAt:8640000000000001}),[]));
test('Nested genre object cannot enter display layer',()=>{const b=backup();b.media[0].genres=[{}];assert.throws(()=>C.validateBackup(b))});
// Source contract checks: these do NOT represent a browser enforcement or Android build.
test('Release CSP forbids inline script and objects',()=>{const html=fs.readFileSync(path.join(root,'web/index.html'),'utf8');assert.match(html,/script-src 'self';/);assert.match(html,/object-src 'none'/);assert.ok(!html.includes('unsafe-eval'))});
test('Static hosting headers block framing and microphone',()=>{const cfg=JSON.parse(fs.readFileSync(path.join(root,'web/vercel.json')));const h=JSON.stringify(cfg);assert.ok(h.includes('frame-ancestors'));assert.ok(h.includes('microphone=()'))});
test('Review module is in actual HTML and SW shell',()=>{for(const f of ['web/index.html','web/sw.js']){const s=fs.readFileSync(path.join(root,f),'utf8');assert.ok(s.includes('reviews.js')&&s.includes('release.js'))}});
test('Android config declares API36 and compatible build toolchain',()=>{assert.match(fs.readFileSync(path.join(root,'app/build.gradle.kts'),'utf8'),/targetSdk = 36/);assert.ok(fs.readFileSync(path.join(root,'build.gradle.kts'),'utf8').includes('8.10.1'));assert.ok(fs.readFileSync(path.join(root,'tools/bootstrap-gradle.sh'),'utf8').includes('8.11.1'))});
test('Native recovery catch does not write over original',()=>{const s=fs.readFileSync(path.join(root,'app/src/main/java/com/franchiseguide/app/data/AppRepository.kt'),'utf8');const a=s.indexOf('private fun load(): Boolean'),b=s.indexOf('@Synchronized',a),load=s.slice(a,b),caught=load.slice(load.indexOf('}.getOrElse'));assert.ok(caught.includes('originalRecoveryState = raw'));assert.ok(!caught.includes('.commit()'));assert.ok(!caught.includes('save('));assert.ok(s.includes('if (!isRecoveryOnly())'))});

test('Empty title language follows global preference',()=>assert.equal(C.contentLanguage('  '),null));
for(const code of ['fr-FR','en-US','ja-JP','de'])test('Valid title language '+code,()=>assert.equal(C.contentLanguage(code),code));
for(const code of ['FR-fr','en-US-extra','../fr','<fr>','fra'])test('Reject invalid title language '+code,()=>assert.throws(()=>C.contentLanguage(code)));
test('Portable media can retain title language override',()=>{const b=backup();b.media[0].contentLanguage='en-US';assert.equal(C.validateBackup(b),true)});
