/* V0.35 multi-category franchise scanner. Pure: no network, DOM or storage.
 * Results are temporary until one atomic commit creates sanitized grey suggestions.
 */
(function(root,factory){
 const core=typeof module==='object'&&module.exports?require('./core.js'):root.FGCore;
 const franchises=typeof module==='object'&&module.exports?require('./franchise-core.js'):root.FGFranchises;
 const api=factory(core,franchises);if(typeof module==='object'&&module.exports)module.exports=api;else root.FGFranchiseScan=api;
})(globalThis,function(C,F){
'use strict';
const KINDS=['show','movie','game','book'],MAX_TERMS=12,MAX_PAGES_PER_TERM=25,MAX_RESULTS_PER_KIND=1000,MAX_SELECTED=200;
const clone=x=>JSON.parse(JSON.stringify(x));
function fail(message,code='FRANCHISE_SCAN_INVALID'){throw Object.assign(Error(message),{code});}
function definition(def){
 const d=F.validateDefinition(def),raw=[...(d.queryTerms||[]),...(d.aliases||[]),d.name],seen=new Set(),terms=[];
 for(const value of raw){const term=String(value||'').trim();if(!term)continue;const key=F.norm(term);if(!key||seen.has(key))continue;seen.add(key);terms.push(term);if(terms.length===MAX_TERMS)break;}
 if(!terms.length)fail('Cette franchise ne possède aucun terme de recherche.');
 return {id:d.id,name:d.name,terms,fingerprint:JSON.stringify([d.id,d.name,d.queryTerms,d.aliases])};
}
function category(kind){if(!KINDS.includes(kind))fail('Catégorie de scanner invalide.');return {kind,items:[],keys:[],selected:[],termIndex:0,page:0,nextPage:1,more:true,complete:false,error:null,pagesAccepted:0,duplicates:0,rejected:0,visible:30};}
function create(def,kinds=KINDS){const d=definition(def),unique=[...new Set(kinds)];if(!unique.length||unique.some(k=>!KINDS.includes(k)))fail('Sélectionne au moins une catégorie valide.');return {franchiseId:d.id,franchiseName:d.name,definitionFingerprint:d.fingerprint,terms:d.terms,categories:Object.fromEntries(unique.map(k=>[k,category(k)])),createdAt:Date.now()};}
function current(session,kind){const c=session?.categories?.[kind];if(!c)fail('Catégorie non activée.');return {kind,termIndex:c.termIndex,term:session.terms[c.termIndex]||null,page:c.nextPage};}
function cursorEquals(a,b){return a&&b&&a.kind===b.kind&&a.termIndex===b.termIndex&&a.page===b.page&&a.term===b.term;}
function belongs(session,item){const title=' '+F.norm(item?.title)+' ';return session.terms.some(term=>{const n=F.norm(term);return n&&title.includes(' '+n+' ');});}
function safeCandidate(raw,kind,term){
 if(!raw||typeof raw!=='object'||Array.isArray(raw)||String(raw.kind).toLowerCase()!==kind||typeof raw.title!=='string'||!raw.title.trim()||raw.title.length>1000||typeof raw.id!=='string'||!raw.id||raw.id.length>400)fail('Résultat catalogue invalide.');
 if(typeof raw.source!=='string'||!raw.source||typeof raw.sourceId!=='string'||!raw.sourceId)fail('Identité fournisseur absente.');
 const out={id:raw.id,kind,source:raw.source.toLowerCase(),sourceId:String(raw.sourceId),title:raw.title.trim(),subtitle:String(raw.subtitle||'').slice(0,500),year:Number.isInteger(raw.year)&&raw.year>=1&&raw.year<=9999?raw.year:null,releaseDate:raw.releaseDate||null,overview:String(raw.overview||'').slice(0,10000),posterUrl:C.safeUrl(raw.posterUrl),backdropUrl:C.safeUrl(raw.backdropUrl),catalogRating:Number.isFinite(raw.catalogRating)?raw.catalogRating:null,catalogRatingVotes:Number.isSafeInteger(raw.catalogRatingVotes)&&raw.catalogRatingVotes>=0?raw.catalogRatingVotes:null,ratings:Array.isArray(raw.ratings)?clone(raw.ratings).slice(0,20):[],tmdbId:Number.isSafeInteger(raw.tmdbId)&&raw.tmdbId>0?raw.tmdbId:null,tvdbId:Number.isSafeInteger(raw.tvdbId)&&raw.tvdbId>0?raw.tvdbId:null,imdbId:typeof raw.imdbId==='string'?raw.imdbId:null,rawgId:typeof raw.rawgId==='string'?raw.rawgId:null,openLibraryId:typeof raw.openLibraryId==='string'?raw.openLibraryId:null,originalLanguage:typeof raw.originalLanguage==='string'?raw.originalLanguage:null,genres:Array.isArray(raw.genres)?clone(raw.genres).slice(0,50):[],creator:typeof raw.creator==='string'?raw.creator.slice(0,1000):null,publisher:typeof raw.publisher==='string'?raw.publisher.slice(0,1000):null,platforms:Array.isArray(raw.platforms)?clone(raw.platforms).slice(0,100):[],externalUrl:C.safeUrl(raw.externalUrl),added:false,listIds:[],_scanTerm:term};
 return out;
}
function exactKey(item){const aliases=C.aliases(item).filter(k=>!k.includes(':local:'));return aliases[0]||`${item.kind}:${item.source}:${item.sourceId}`;}
function accept(session,kind,cursor,result){
 const c=session?.categories?.[kind],expected=current(session,kind);if(!c||c.complete||!cursorEquals(cursor,expected))fail('Réponse de scanner obsolète.');
 if(!result||!Array.isArray(result.items)||typeof result.more!=='boolean'||result.items.length>500)fail('Page de scanner invalide.');
 const pageKeys=new Set(),known=new Set(c.keys),add=[];let duplicates=0,rejected=0;
 for(const raw of result.items){const item=safeCandidate(raw,kind,cursor.term);if(!belongs(session,item)){rejected++;continue;}const key=exactKey(item);if(pageKeys.has(key))fail('Identité fournisseur répétée dans une même page.');pageKeys.add(key);if(known.has(key)){duplicates++;continue;}known.add(key);add.push(item);}
 if(c.items.length+add.length>MAX_RESULTS_PER_KIND)fail('Limite de 1 000 références atteinte pour cette catégorie. Utilise une recherche plus précise.');
 const next=clone(session),n=next.categories[kind];n.items.push(...add);n.keys=[...known];n.page=cursor.page;n.pagesAccepted++;n.duplicates+=duplicates;n.rejected+=rejected;n.error=null;
 const continueTerm=result.more&&cursor.page<MAX_PAGES_PER_TERM&&n.items.length<MAX_RESULTS_PER_KIND;
 if(continueTerm)n.nextPage=cursor.page+1;
 else {n.termIndex++;n.page=0;n.nextPage=1;if(n.termIndex>=next.terms.length||n.items.length>=MAX_RESULTS_PER_KIND){n.complete=true;n.more=false;}else n.more=true;}
 return next;
}
function failed(session,kind,cursor,message){const expected=current(session,kind);if(!cursorEquals(cursor,expected))fail('Échec reçu pour une page obsolète.');const next=clone(session);next.categories[kind].error={cursor:clone(cursor),message:String(message||'Catalogue indisponible.').slice(0,800)};return next;}
function candidates(session){return Object.values(session.categories).flatMap(c=>c.items);}
function sharedIdentity(a,b){if(!a||!b||a.kind!==b.kind)return false;const aa=C.aliases(a),bb=new Set(C.aliases(b));return aa.some(x=>bb.has(x));}
function providerConflict(a,b){return (a.tmdbId&&b.tmdbId&&a.tmdbId!==b.tmdbId)||(a.tvdbId&&b.tvdbId&&a.tvdbId!==b.tvdbId)||(a.imdbId&&b.imdbId&&a.imdbId!==b.imdbId)||(a.source===b.source&&a.sourceId&&b.sourceId&&String(a.sourceId)!==String(b.sourceId));}
function identityConflicts(a,b){if(!a||!b||a.kind!==b.kind)return false;if(a.id===b.id&&(a.source!==b.source||String(a.sourceId)!==String(b.sourceId)))return true;return sharedIdentity(a,b)&&!!providerConflict(a,b);}
function classify(candidate,state){const all=[...(state.media||[]),...(state.suggestions||[])],conflicts=all.filter(x=>identityConflicts(x,candidate)),exact=all.filter(x=>sharedIdentity(x,candidate)&&!identityConflicts(x,candidate));if(conflicts.length||exact.length>1)return {status:'ambiguous',matches:[...new Set([...exact,...conflicts])],selectable:false};if(exact.length===1){const x=exact[0];if((state.suggestions||[]).includes(x)||x.suggestion||x.isSuggestion)return {status:'suggested',matches:exact,selectable:false};if(F.added(x))return {status:'added',matches:exact,selectable:false};return {status:'withdrawn',matches:exact,selectable:false};}return {status:'new',matches:[],selectable:true};}
function selectedSet(c){return new Set(c.selected||[]);}
function toggle(session,kind,key,state,checked){const c=session.categories[kind];if(!c)fail('Catégorie retirée.');const item=c.items.find(x=>exactKey(x)===key);if(!item)fail('Résultat retiré.');const info=classify(item,state);if(!info.selectable)fail('Cette identité est déjà connue ou ambiguë. Aucun doublon automatique.');const next=clone(session),set=selectedSet(next.categories[kind]);checked?set.add(key):set.delete(key);next.categories[kind].selected=[...set];if(Object.values(next.categories).reduce((n,x)=>n+x.selected.length,0)>MAX_SELECTED)fail('Sélection limitée à 200 suggestions par lot.');return next;}
function selectAvailable(session,kind,state,checked=true){const c=session.categories[kind];if(!c)fail('Catégorie retirée.');let next=clone(session),set=selectedSet(next.categories[kind]);for(const item of c.items){const key=exactKey(item);if(classify(item,state).selectable)checked?set.add(key):set.delete(key);}next.categories[kind].selected=[...set];if(Object.values(next.categories).reduce((n,x)=>n+x.selected.length,0)>MAX_SELECTED)fail('Sélection limitée à 200 suggestions par lot.');return next;}
function plan(session,state,definitionNow){const d=definition(definitionNow);if(d.id!==session.franchiseId||d.fingerprint!==session.definitionFingerprint)fail('La définition de franchise a changé. Ferme le scanner puis relance-le.','FRANCHISE_SCAN_STALE');const selected=[];for(const c of Object.values(session.categories)){const set=selectedSet(c);for(const item of c.items)if(set.has(exactKey(item)))selected.push(item);}if(!selected.length)fail('Sélectionne au moins une nouvelle référence.');if(selected.length>MAX_SELECTED)fail('Sélection trop grande.');const keys=new Set();for(const item of selected){const k=exactKey(item);if(keys.has(k))fail('Une même identité apparaît deux fois dans la sélection.');keys.add(k);if(!belongs(session,item))fail('Un résultat ne correspond plus aux termes de cette franchise.');const info=classify(item,state);if(info.status==='ambiguous')fail('Une correspondance est devenue ambiguë. Aucun élément du lot n’a été enregistré.','FRANCHISE_SCAN_AMBIGUOUS');if(info.status!=='new')fail('Une référence sélectionnée est déjà connue. Aucun élément du lot n’a été enregistré.','FRANCHISE_SCAN_STALE');}
 let next=clone(state);const byKind={show:0,movie:0,game:0,book:0};for(const item of selected){next=F.suggest(next,session.franchiseId,item);byKind[item.kind]++;}
 return {state:next,inserted:selected.length,byKind,ids:selected.map(exactKey)};}
function summary(session,state){const byKind={};let selected=0,available=0,known=0,ambiguous=0,total=0;for(const [kind,c] of Object.entries(session.categories)){const row={items:c.items.length,selected:c.selected.length,page:c.page,termIndex:c.termIndex,complete:c.complete,error:c.error};for(const item of c.items){total++;const s=classify(item,state).status;s==='new'?available++:s==='ambiguous'?ambiguous++:known++;}selected+=c.selected.length;byKind[kind]=row;}return {total,selected,available,known,ambiguous,byKind};}
return {KINDS,MAX_TERMS,MAX_PAGES_PER_TERM,MAX_RESULTS_PER_KIND,MAX_SELECTED,definition,create,current,cursorEquals,belongs,safeCandidate,exactKey,accept,failed,candidates,classify,toggle,selectAvailable,plan,summary};
});
