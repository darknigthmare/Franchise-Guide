'use strict';const fs=require('node:fs'),assert=require('node:assert/strict'),W=require('../web/availability-core.js');
const q=(r='FR',s=null)=>W.request({kind:'show',tmdbId:111},r,s),p=(id=1,name='Écran test',priority=3)=>({provider_id:id,provider_name:name,display_priority:priority,logo_path:'/qa.png'});
const raw=(node={flatrate:[p()],rent:[p()],link:'https://www.themoviedb.org/tv/111-test/watch?locale=US&key=secret'})=>({id:111,results:{FR:node,US:{buy:[p(8,'Autre pays test')]}}});
const read=(r=raw(),v=q())=>W.parse(r,v,1700000000000);
const ordered=read(raw({buy:[p(3,'C',1),p(2,'B',1),p(4,'D',0),p(5,'E',null)]}));
const result=[read(),read(raw(),q('DE')),read(raw({})),ordered].map(s=>({key:s.query.key,path:s.query.path,state:W.stateLabel(s),countryReported:s.countryReported,link:s.link,totalProviders:s.totalProviders,totalOffers:s.totalOffers,groups:s.groups.map(g=>({mode:g.mode,offers:g.offers.map(p=>[p.id,p.name,p.logoUrl,p.priority])}))}));
assert.deepEqual(JSON.parse(fs.readFileSync(process.argv[2],'utf8')),result);console.log('Availability JS/Kotlin country/type/link/count parity: passed (four snapshots).');
