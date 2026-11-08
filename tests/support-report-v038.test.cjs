'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const S=require('../web/support-report-core.js');
test('shareable diagnostics use shipped version and contain only technical allowlisted fields',()=>{
 const confidentialText='Bearer token-secret alice@example.invalid /confidentialText/alice/notes personal-note';
 const report=S.build({state:{media:[{title:confidentialText,userNote:confidentialText}],episodes:[{overview:confidentialText}],settings:{access_token:confidentialText}},sync:{queue:[{account:confidentialText}],errors:[confidentialText]},logs:[{level:'error',area:confidentialText,message:confidentialText},{level:'warn',message:confidentialText}],online:true});
 const json=JSON.stringify(report);assert.ok(!json.includes(confidentialText));for(const term of ['Bearer','alice','token-secret','personal-note'])assert.ok(!json.includes(term));
 assert.equal(report.mediaCount,1);assert.equal(report.episodeCount,1);assert.equal(report.errorCount,1);assert.equal(report.warningCount,1);assert.equal(report.queuedCount,1);assert.equal(report.syncErrorCount,1);
 assert.equal(report.version,'0.38.0');assert.equal(report.accountsIncluded,false);assert.equal(report.libraryIncluded,false);assert.equal(report.rawMessagesIncluded,false);
 assert.ok(Object.isFrozen(report));
});
test('malformed old diagnostic rows cannot inject fields into a shared report',()=>{
 const bad=[null,undefined,{},'secret',{level:'secret'}, {level:'error',toJSON:()=>({secret:'must not run'})}];
 const report=S.build({state:{media:null},sync:{queue:'not an array'},logs:bad,online:'secret',recovery:'secret',readOnly:'secret'});
 assert.equal(report.errorCount,1);assert.equal(report.queuedCount,0);assert.equal(report.browserOnline,false);assert.ok(!JSON.stringify(report).includes('secret'));
});
test('diagnostic export never attaches raw rows or provider error messages',()=>{
 const src=fs.readFileSync(require.resolve('../web/integrated.js'),'utf8');const exportLine=src.split('\n').find(l=>l.includes("$('#exportLog').onclick"));
 assert.ok(exportLine.includes("downloadJson('FranchiseGuide-diagnostic.json',report)"));assert.ok(!exportLine.includes('logs:'));assert.ok(!exportLine.includes('errors:'));
});
