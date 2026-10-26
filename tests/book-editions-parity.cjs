const fs=require('node:fs'),A=require('node:assert/strict'),B=require('../web/book-editions-core.js');
const p=B.parsePage({size:1,links:{work:'/works/OL1W'},entries:[{key:'/books/OL10M',title:'Le Livre de test',works:[{key:'/works/OL1W'}],number_of_pages:220,publish_date:'mai 2001',publishers:['Éditeur QA'],languages:[{key:'/languages/fre'}],isbn_13:['9780140328721'],covers:[123]}]},'OL1W'),e=p.entries[0];
const m={id:'m',kind:'book',source:'openlibrary',sourceId:'/works/OL1W',added:true,progressCurrent:20,progressTotal:0,progressUnit:'pages',watched:true,plays:2,userNote:'Privé'},n=B.apply(m,B.prepare(m,e,1000),1001);
const text=[p.work,e.id,e.title,String(e.pages),e.publishDate,e.publishers.join('|'),e.languages.join('|'),e.isbns.join('|'),e.coverUrl,String(n.progressCurrent),String(n.progressTotal),String(n.plays),n.userNote].join('\n')+'\n';
A.equal(fs.readFileSync(process.argv[2],'utf8'),text);console.log('PASS Book edition normalized fields + personal total parity JS/Kotlin');
