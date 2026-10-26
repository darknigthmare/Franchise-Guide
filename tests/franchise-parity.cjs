const fs=require('node:fs'),assert=require('node:assert/strict'),F=require('../web/franchise-core.js');
const d={id:'mine',name:'Univers QA',queryTerms:['Univers QA'],aliases:[],custom:true,sortOrder:[]};
const s={media:[{id:'a',kind:'movie',title:'Film a',franchise:'mine',year:2000},{id:'b',kind:'movie',title:'Film b',franchise:'mine',year:2001}],suggestions:[{id:'c',kind:'movie',title:'Film c',franchise:'mine',year:2020,added:false,suggestion:true}],customFranchises:[d]};
const g=F.groups(F.move(s,'mine','c',1))[0];assert.equal(fs.readFileSync(process.argv[2],'utf8'),`${g.added}|${g.known}|${g.missing}|${g.items.map(m=>m.id).join(',')}`);console.log('PASS native/JS primary franchise counts and personal order parity (synthetic fixture).');
