/* V0.19: pure import comparison. No storage, credentials, network, dates or mutations. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.FGImport=api;})(globalThis,function(){
'use strict';
const MAX_FILE=32*1024*1024,MAX_TOTAL=64*1024*1024,MAX_FILES=10;
const list=x=>Array.isArray(x)?x:[];
const copy=x=>JSON.parse(JSON.stringify(x));
function validateFiles(files,mode){
 if(!['portable','seriesguide'].includes(mode))throw Error('Type d’import invalide.');
 if(!Array.isArray(files)||!files.length||files.length>MAX_FILES)throw Error('Sélectionne de 1 à 10 fichiers.');
 if(mode==='portable'&&files.length!==1)throw Error('Une seule sauvegarde FranchiseGuide à la fois.');
 let bytes=0;for(const f of files){if(!Number.isSafeInteger(f.size)||f.size<1||f.size>MAX_FILE)throw Error('Fichier vide ou supérieur à 32 Mio.');bytes+=f.size;}
 if(bytes>MAX_TOTAL)throw Error('Sélection supérieure à 64 Mio au total.');return bytes;
}
function dates(x){return new Set([...list(x.watchHistory),x.lastWatchedAt].filter(n=>Number.isSafeInteger(n)&&n>0).map(n=>Math.floor(n/1000)));}
function count(s){
 const m=list(s.media),ep=list(s.episodes),gaming=s.gaming||{},sets=list(gaming.sets),active=m.filter(x=>x.added!==false&&!x.suggestion&&!x.isSuggestion),tracked=[...active.filter(x=>String(x.kind).toLowerCase()!=='show'),...ep];
 const result={titles:active.length,show:0,movie:0,game:0,book:0,episodes:ep.length,watchedEpisodes:ep.filter(e=>e.watched&&!e.skipped).length,datedViewings:tracked.reduce((n,x)=>n+dates(x).size,0),notes:[...active,...ep].filter(x=>typeof x.userNote==='string'&&x.userNote.trim()).length,lists:list(s.lists).length,franchises:list(s.customFranchises).length,accounts:list(gaming.accounts).length,sets:sets.length,achievements:sets.reduce((n,x)=>n+list(x.achievements).length,0)};
 for(const x of active){const k=String(x.kind).toLowerCase();if(['show','movie','game','book'].includes(k))result[k]++;}return result;
}
// Exact local state guard; all properties within public data/settings count, private root runtime keys do not.
function contentKey(s){return JSON.stringify({media:list(s.media),episodes:list(s.episodes),suggestions:list(s.suggestions),lists:list(s.lists),customFranchises:list(s.customFranchises),gaming:s.gaming||null,settings:s.settings||{}});}
function stable(x){if(Array.isArray(x))return '['+x.map(stable).join(',')+']';if(x&&typeof x==='object')return '{'+Object.keys(x).filter(k=>!k.startsWith('_')).sort().map(k=>JSON.stringify(k)+':'+stable(x[k])).join(',')+'}';return JSON.stringify(x)??'null';}
function compare(before,after){
 const b=count(before),a=count(after),rows=[],loss={dates:0,watched:0,notes:0,ratings:0};
 const changed={added:0,removed:0,modified:0,unchanged:0};
 const am=new Map(list(after.media).map(x=>[x.id,x])),bm=new Map(list(before.media).map(x=>[x.id,x]));
 for(const id of new Set([...bm.keys(),...am.keys()])){const prev=bm.get(id),next=am.get(id),type=!prev?'added':!next?'removed':stable(prev)===stable(next)?'unchanged':'modified';changed[type]++;rows.push({id,title:String((next||prev).title||id),kind:String((next||prev).kind||'').toLowerCase(),change:type});}
 function losses(prev,next){
  const oldDates=dates(prev),newDates=next?dates(next):new Set();for(const x of oldDates)if(!newDates.has(x))loss.dates++;
  if(prev.watched&&!next?.watched)loss.watched++;
  if(typeof prev.userNote==='string'&&prev.userNote.trim()&&prev.userNote!==next?.userNote)loss.notes++;
  if(prev.rating!=null&&prev.rating!==next?.rating)loss.ratings++;
 }
 for(const prev of list(before.media)){const next=am.get(prev.id);if(String(prev.kind).toLowerCase()==='show')losses({...prev,watchHistory:[],lastWatchedAt:null},next?{...next,watchHistory:[],lastWatchedAt:null}:null);else losses(prev,next);}
 const ae=new Map(list(after.episodes).map(x=>[x.id,x]));for(const prev of list(before.episodes))losses(prev,ae.get(prev.id));
 rows.sort((x,y)=>x.title.localeCompare(y.title,'fr')||x.id.localeCompare(y.id));
 return {before:b,after:a,changes:changed,loss,rows};
}
function guard(review,live,epoch,revision){if(review.epoch!==epoch||review.revision!==revision||review.baseKey!==contentKey(live))throw Error('La bibliothèque ou ses réglages ont changé depuis cet aperçu. Ferme-le et relis les fichiers.');}
function filterRows(rows,query='',kind='all',change='all'){const q=String(query).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('fr').slice(0,250);return rows.filter(r=>(kind==='all'||r.kind===kind)&&(change==='all'||r.change===change)&&(!q||r.title.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('fr').includes(q)));}
function publicReport(review){return {app:'FranchiseGuide',version:'0.36.0',type:review.mode,mode:review.mode==='portable'?'replacement':'merge',sourceFiles:review.files.map(f=>({name:String(f.name),bytes:f.size})),settingsImported:!!review.importPreferences,before:review.comparison.before,after:review.comparison.after,changes:review.comparison.changes,attention:review.comparison.loss,seriesGuide:review.report?copy(review.report):null,scope:'Comparaison locale par identifiant ; pas de contenu de notes, identifiants de profils, titres d’épisodes ou secret. Les noms de fichiers peuvent être privés.'};}
return {MAX_FILE,MAX_TOTAL,MAX_FILES,validateFiles,count,contentKey,compare,guard,filterRows,publicReport};
});
