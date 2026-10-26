/* Pure, portable ISBN/edition rules. Personal references never replace work identity. */
(function(root,factory){const B=typeof module==='object'&&module.exports?require('./book-editions-core.js'):root.FGBookEditions;const api=factory(B);if(typeof module==='object'&&module.exports)module.exports=api;else root.FGBookSelection=api;})(globalThis,function(B){
'use strict';
const LIMIT=5,TTL=600000,obj=x=>!!x&&typeof x==='object'&&!Array.isArray(x);
function canonical(value){const raw=B.isbn(value);if(!raw)return null;if(raw.length===13)return raw;const stem='978'+raw.slice(0,9),sum=[...stem].reduce((s,c,i)=>s+Number(c)*(i%2?3:1),0);return stem+((10-sum%10)%10);}
function input(value){const text=B.text(value,'ISBN',100).replace(/^ISBN(?:-1[03])?\s*:?\s*/i,'');const code=canonical(text);if(!code)throw Error('ISBN invalide : 10 ou 13 caractères, contrôle correct et préfixe 978/979 pour un ISBN-13.');return code;}
function list(v,label,max=100){if(v==null)return [];if(!Array.isArray(v)||v.length>max)throw Error(label+' invalides.');return [...new Set(v.map(x=>B.text(x,label,1000)).filter(Boolean))];}
function number(v,label,max=8640000000000000){if(!Number.isSafeInteger(v)||v<0||v>max)throw Error(label+' invalide.');return v;}
function parseSearch(raw,value){
 const isbn=input(value);if(!obj(raw)||!Array.isArray(raw.docs)||raw.docs.length>LIMIT)throw Error('Réponse ISBN invalide.');
 const total=number(raw.numFound??raw.num_found,'Nombre de résultats',1000000);if((raw.start??0)!==0||raw.docs.length>total||(total>0&&!raw.docs.length))throw Error('Recherche ISBN incomplète.');
 const rows=[],works=new Set();for(const d of raw.docs){
  if(!obj(d))throw Error('Œuvre invalide.');const work=B.key(d.key),title=B.text(d.title,'Titre',1000),authors=list(d.author_name,'Auteurs');
  if(!title||works.has(work))throw Error('Œuvre manquante ou répétée.');works.add(work);
  if(!obj(d.editions)||!Array.isArray(d.editions.docs)||d.editions.docs.length>LIMIT)throw Error('Référence d’édition absente : utilise aussi la recherche par titre.');
  const editions=new Set();for(const e of d.editions.docs){if(!obj(e))throw Error('Référence invalide.');const editionId=B.key(e.key,'edition');if(editions.has(editionId))throw Error('Édition répétée.');editions.add(editionId);rows.push({isbn,work,editionId,title,authors});}
 }
 return {isbn,total,rows,limited:total>raw.docs.length};
}
function verifyEdition(raw,c){if(!obj(c)||!obj(raw)||B.key(raw.key,'edition')!==B.key(c.editionId,'edition'))throw Error('Réponse pour une autre édition.');const e=B.edition(raw,B.key(c.work));if(!e.workVerified)throw Error('Lien avec cette œuvre non confirmé.');if(!e.isbns.some(x=>canonical(x)===input(c.isbn)))throw Error('L’édition ne déclare pas l’ISBN recherché.');return e;}
function normalize(s,m=null){
 if(s==null)return null;if(!obj(s)||s.version!==1||s.provider!=='openlibrary')throw Error('Référence personnelle d’édition invalide.');
 const work=B.key(s.work),editionId=B.key(s.editionId,'edition'),title=B.text(s.title,'Titre',1000);if(!title)throw Error('Titre d’édition manquant.');
 const pages=s.pages==null?null:number(s.pages,'Pages',10000000),languages=list(s.languages,'Langues'),isbns=list(s.isbns,'ISBN',200);
 if(languages.some(x=>!/^[a-z]{3}$/.test(x))||isbns.some(x=>B.isbn(x)!==x))throw Error('Langues ou ISBN enregistrés invalides.');
 const observedAt=number(s.observedAt,'Date du relevé'),selectedAt=number(s.selectedAt,'Date de choix');if(selectedAt<observedAt)throw Error('Dates d’édition incohérentes.');
 const coverId=s.coverId==null?null:number(s.coverId,'Couverture',Number.MAX_SAFE_INTEGER);if(coverId===0)throw Error('Couverture invalide.');
 const result={version:1,provider:'openlibrary',work,editionId,title,publishDate:B.text(s.publishDate,'Publication',200),publishers:list(s.publishers,'Éditeurs'),languages,format:B.text(s.format,'Format',300),pages,isbns,coverId,observedAt,selectedAt};
 if(m&&(String(m.kind).toLowerCase()!=='book'||m.source!=='openlibrary'||B.key(m.sourceId)!==work))throw Error('La référence d’édition ne correspond pas à la fiche du livre.');return result;
}
function snapshot(e,observedAt,now){if(!e?.workVerified)throw Error('Édition non confirmée.');const cover=e.coverUrl?.match(/^https:\/\/covers\.openlibrary\.org\/b\/id\/([1-9]\d*)-M\.jpg$/);return normalize({version:1,provider:'openlibrary',work:e.work,editionId:e.id,title:e.title,publishDate:e.publishDate,publishers:e.publishers,languages:e.languages,format:e.format,pages:e.pages,isbns:e.isbns,coverId:cover?Number(cover[1]):null,observedAt,selectedAt:now});}
function guard(m){if(!m||String(m.kind).toLowerCase()!=='book'||m.source!=='openlibrary'||!m.added||m.suggestion||m.isSuggestion)throw Error('Ajoute cette œuvre avant de retenir une édition.');return {id:m.id,work:B.key(m.sourceId),selected:normalize(m.selectedEdition,m)};}
function prepare(m,e,observedAt,{usePages=false,now=Date.now()}={}){const before=guard(m),selection=snapshot(e,observedAt,now);if(selection.work!==before.work)throw Error('Cette édition appartient à une autre œuvre.');return {before,selection,pagePlan:usePages?B.prepare(m,e,now):null,createdAt:now};}
function checkPlan(m,p,now){if(!p||now<p.createdAt||now-p.createdAt>TTL)throw Error('Confirmation expirée.');if(JSON.stringify(guard(m))!==JSON.stringify(p.before))throw Error('La fiche ou son édition choisie a changé. Recommence la confirmation.');}
function apply(m,p,now=Date.now()){checkPlan(m,p,now);const selectedEdition=normalize(p.selection,m);if(!selectedEdition)throw Error('Référence d’édition manquante.');return {...(p.pagePlan?B.apply(m,p.pagePlan,now):m),selectedEdition};}
function prepareClear(m,now=Date.now()){return {before:guard(m),createdAt:now};}
function clear(m,p,now=Date.now()){checkPlan(m,p,now);const next={...m};delete next.selectedEdition;return next;}
function searchUrl(value){return 'https://openlibrary.org/search.json?'+new URLSearchParams({q:'isbn:'+input(value),fields:'key,title,author_name,editions,editions.key',limit:String(LIMIT),page:'1'});}
function sourceUrl(s){return 'https://openlibrary.org'+normalize(s).editionId;}
return {LIMIT,TTL,canonical,input,parseSearch,verifyEdition,normalize,snapshot,prepare,apply,prepareClear,clear,searchUrl,sourceUrl};
});
