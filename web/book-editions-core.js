/* Open Library works vs editions. Pure validated pages; explicit personal total update only. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.FGBookEditions=api;})(globalThis,function(){
'use strict';
const PAGE_SIZE=30,MAX_LOADED=300,MAX_BYTES=2*1024*1024;
const obj=v=>v&&typeof v==='object'&&!Array.isArray(v);
function text(v,label,max=1000){if(v==null)return '';if(typeof v!=='string'||v.length>max||/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(v))throw Error(label+' invalide.');return v.trim();}
function key(v,type='work'){
 const value=text(v,'Identifiant Open Library',100),prefix=type==='work'?'works':'books',suffix=type==='work'?'W':'M';
 const m=value.match(new RegExp('^(?:/'+prefix+'/)?(OL[1-9][0-9]{0,11}'+suffix+')$'));
 if(!m)throw Error('Identifiant '+(type==='work'?'d’œuvre':'d’édition')+' Open Library invalide.');return '/'+prefix+'/'+m[1];
}
function strings(v,label,max=100){if(v==null)return [];if(!Array.isArray(v)||v.length>max)throw Error(label+' invalides.');return [...new Set(v.map(x=>text(x,label,1000)).filter(Boolean))];}
function pages(v){if(v==null)return null;if(!Number.isSafeInteger(v)||v<0||v>10000000)throw Error('Nombre de pages invalide.');return v;}
function isbn(v){const raw=text(v,'ISBN',100).replace(/[\s-]/g,'').toUpperCase();if(/^\d{9}[\dX]$/.test(raw))return [...raw].reduce((sum,c,i)=>sum+(10-i)*(c==='X'?10:Number(c)),0)%11===0?raw:null;if(/^(978|979)\d{10}$/.test(raw))return [...raw].reduce((sum,c,i)=>sum+Number(c)*(i%2?3:1),0)%10===0?raw:null;return null;}
function cover(values){if(values==null)return null;if(!Array.isArray(values)||values.length>100)throw Error('Couvertures invalides.');const id=values.find(x=>Number.isSafeInteger(x)&&x>0);return id?'https://covers.openlibrary.org/b/id/'+id+'-M.jpg':null;}
function edition(raw,work){
 if(!obj(raw))throw Error('Édition invalide.');const id=key(raw.key,'edition'),title=text(raw.title,'Titre',1000);if(!title)throw Error('Titre d’édition manquant.');
 if(raw.works!=null&&(!Array.isArray(raw.works)||raw.works.length>100))throw Error('Liens d’œuvre invalides.');
 const works=(raw.works||[]).map(w=>{if(!obj(w))throw Error('Lien d’œuvre invalide.');return key(w.key);});
 if(works.length&&!works.includes(work))throw Error('Une édition reçue correspond à une autre œuvre.');
 const languages=raw.languages==null?[]:raw.languages;if(!Array.isArray(languages)||languages.length>100)throw Error('Langues invalides.');
 const lang=[...new Set(languages.map(l=>{if(!obj(l)||!/^\/languages\/[a-z]{3}$/.test(l.key))throw Error('Code de langue invalide.');return l.key.slice(11);} ))];
 const rawISBN=[...strings(raw.isbn_10,'ISBN-10'),...strings(raw.isbn_13,'ISBN-13')],valid=rawISBN.map(isbn).filter(Boolean);
 return {id,title,work,workVerified:works.includes(work),pages:pages(raw.number_of_pages),publishDate:text(raw.publish_date,'Date de publication',200),publishers:strings(raw.publishers,'Éditeurs'),languages:lang,format:text(raw.physical_format,'Format',300),isbns:[...new Set(valid)],invalidIsbnCount:rawISBN.length-valid.length,coverUrl:cover(raw.covers),sourceUrl:'https://openlibrary.org'+id};
}
function parsePage(raw,workValue,offset=0,limit=PAGE_SIZE){
 const work=key(workValue);if(!Number.isInteger(offset)||offset<0||offset>100000||!Number.isInteger(limit)||limit<1||limit>PAGE_SIZE)throw Error('Pagination invalide.');
 if(!obj(raw)||!Number.isSafeInteger(raw.size)||raw.size<0||raw.size>1000000||!Array.isArray(raw.entries)||raw.entries.length>limit)throw Error('Page d’éditions incomplète ou invalide.');
 if(raw.links?.work!=null&&key(raw.links.work)!==work)throw Error('Page reçue pour une autre œuvre.');
 if(offset+raw.entries.length>raw.size||offset<raw.size&&!raw.entries.length)throw Error('Page d’éditions incohérente ; rien n’a été ajouté.');
 const entries=raw.entries.map(e=>edition(e,work));if(new Set(entries.map(e=>e.id)).size!==entries.length)throw Error('Édition en double dans la réponse.');
 const next=offset+entries.length<raw.size?offset+entries.length:null;
 // Provider links are never followed. Validate an advertised next page; build our own fixed URL.
 if(raw.links?.next!=null){let u;try{u=new URL(raw.links.next,'https://openlibrary.org');}catch{throw Error('Lien suivant invalide.');}
  const keys=[...u.searchParams.keys()];if(u.origin!=='https://openlibrary.org'||u.username||u.password||u.hash||u.pathname!==work+'/editions.json'||new Set(keys).size!==keys.length||keys.some(k=>!['offset','limit'].includes(k))||next==null||Number(u.searchParams.get('offset'))!==next||u.searchParams.has('limit')&&Number(u.searchParams.get('limit'))!==limit)throw Error('Pagination distante incohérente.');
 }
 return {work,offset,total:raw.size,entries,nextOffset:next};
}
function mergePages(old,page){if(!old)return {...page,entries:[...page.entries]};if(old.work!==page.work||old.nextOffset!==page.offset||old.total!==page.total)throw Error('La liste a changé pendant la lecture. Actualise depuis la première page.');if(old.entries.length+page.entries.length>MAX_LOADED)throw Error('Limite de consultation atteinte (300 éditions). Ouvre la source pour poursuivre.');const ids=new Set(old.entries.map(e=>e.id));if(page.entries.some(e=>ids.has(e.id)))throw Error('Édition répétée entre deux pages. Actualise la liste.');return {...page,offset:0,entries:[...old.entries,...page.entries]};}
function normalized(v){return String(v||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('fr');}
function filter(entries,query='',language='',known=false){const q=normalized(query);return entries.filter(e=>(!language||e.languages.includes(language))&&(!known||e.pages>0)&&(!q||normalized([e.title,e.id,e.format,e.publishDate,...e.publishers,...e.isbns].join(' ')).includes(q)));}
function current(m){if(!m||String(m.kind).toLowerCase()!=='book'||m.source!=='openlibrary'||m.added===false||m.suggestion||m.isSuggestion)throw Error('Ajoute ce livre Open Library avant de choisir un total.');return {id:m.id,work:key(m.sourceId),current:pages(m.progressCurrent??0),total:pages(m.progressTotal??0),unit:String(m.progressUnit||'')};}
function prepare(m,e,now=Date.now()) {const before=current(m);if(e.work!==before.work||!e.workVerified||!Number.isInteger(e.pages)||e.pages<=0||e.pages>10000000)throw Error('Nombre de pages exploitable et rattachement à l’œuvre requis.');if(before.unit.trim().toLowerCase()!=='pages')throw Error('Ton suivi n’est pas en pages. Change son unité manuellement avant de choisir ce total.');if(before.current>e.pages)throw Error('Ton avancement dépasse les pages de cette édition. Aucune progression n’a été réduite.');return {before,editionId:key(e.id,'edition'),pages:e.pages,createdAt:now};}
function apply(m,plan,now=Date.now()){if(!plan||now<plan.createdAt||now-plan.createdAt>600000)throw Error('Confirmation expirée. Recommence la sélection.');const actual=current(m);if(JSON.stringify(actual)!==JSON.stringify(plan.before))throw Error('Ce livre ou sa progression a changé. Recommence la sélection.');if(!Number.isInteger(plan.pages)||plan.pages<=0||plan.pages<actual.current||plan.pages>10000000)throw Error('Total invalide.');return {...m,progressTotal:plan.pages};}
function searchItem(x){if(!obj(x))throw Error('Résultat livre invalide.');const k=key(x.key),title=text(x.title,'Titre',1000);if(!title)throw Error('Livre sans titre.');const authors=strings(x.author_name,'Auteurs');const year=x.first_publish_year;if(year!=null&&(!Number.isInteger(year)||year<1||year>9999))throw Error('Année Open Library invalide.');return {id:'ol-'+k.replace(/\W+/g,'-'),kind:'book',title,creator:authors.join(', '),year:year||null,posterUrl:cover(x.cover_i==null?null:[x.cover_i]),progressTotal:0,progressUnit:'pages',source:'openlibrary',sourceId:k,externalUrl:'https://openlibrary.org'+k,added:false,listIds:[]};}
return {PAGE_SIZE,MAX_LOADED,MAX_BYTES,key,text,isbn,cover,edition,parsePage,mergePages,filter,prepare,apply,searchItem};
});
