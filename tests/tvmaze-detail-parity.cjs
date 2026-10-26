const A=require('node:assert/strict'),fs=require('node:fs'),T=require('../web/tvmaze-detail-core.js');
const q=T.episodeRequest({id:'s',kind:'show',source:'tvmaze',sourceId:'11'},{id:'tvmaze-episode-101',showId:'s',season:1,num:1});
const person={id:99,name:'Élodie QA'},work={id:20,name:'Série exemple',premiered:'2020-01-01'};
const d=T.episodeSnapshot({id:101,season:1,number:1,name:'Pilote QA',summary:'<p>Résumé &amp; test</p>',type:'regular',runtime:0,rating:{average:0},airdate:'2025-01-01',image:{original:'https://static.tvmaze.com/still.jpg'},_embedded:{show:{id:11}}},[{person,character:{name:'Invitée'}},{person,character:{name:'Narratrice'}}],[{person:{id:100,name:'Auteur QA'},type:'Writer'}],q,1700000000000);
const p=T.personSnapshot({...person,birthday:'1980-02-29'},[{voice:true,_embedded:{show:work}},{self:true,_embedded:{show:work}}],[{type:'Writer',_embedded:{show:work}}],99);
const expected=[d.sourceUrl,d.title,d.overview,String(d.runtime),String(d.rating),d.groups.map(g=>g.kind+':'+g.people.map(p=>`${p.id}:${p.name}:${p.roles.join('/')}`).join(',')).join('|'),p.credits[0].id,p.credits[0].roles.join('/')].join('\n')+'\n';
A.equal(fs.readFileSync(process.argv[2],'utf8'),expected);console.log('Public TVmaze episode/person JS ↔ Kotlin parity passed (fixture, no network)');
