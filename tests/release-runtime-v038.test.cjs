"use strict";
const test=require('node:test'),a=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const root=path.join(__dirname,'..'),version=require('../package.json').version,short=version.split('.').slice(0,2).join('.');
function read(p){return fs.readFileSync(path.join(root,p),'utf8');}
test('current runtime labels agree with the release, not historical static captions',()=>{
 a.ok(read('web/index.html').includes('<title>FranchiseGuide Web '+short+'</title>'));
 a.ok(read('web/index.html').includes('<small>Web '+short+'</small>'));
 a.ok(read('desktop/src/com/franchiseguide/desktop/Main.java').includes('new JFrame("FranchiseGuide PC '+short+'")'));
 a.ok(read('web/release.js').includes('Version de développement '+version));
 a.ok(read('app/src/main/java/com/franchiseguide/app/ui/Screens.kt').includes('FranchiseGuide '+version+' est'));
});
test('export and provider metadata identify the same current release',()=>{
 a.ok(read('web/backup.js').includes("exportedBy:'web-"+version+"'"));
 a.ok(read('web/import-core.js').includes("version:'"+version+"'"));
 a.ok(read('web/trakt-checkin-core.js').includes("app_version:'"+version+"'"));
 a.ok(read('app/src/main/java/com/franchiseguide/app/network/TraktClient.kt').includes('.put("app_version","'+version+'")'));
});
