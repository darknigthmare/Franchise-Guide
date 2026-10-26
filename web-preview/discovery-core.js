/* FranchiseGuide discovery pagination and view rules. Pure, no network or persistent state. */
(function(root,factory){
 const value=factory(); if(typeof module==='object'&&module.exports)module.exports=value;else root.FGDiscovery=value;
})(globalThis,function(){
 'use strict';
 const KINDS=new Set(['show','movie','game','book']);
 const SORTS=new Set(['source','title','year-asc','year-desc']);
 const STATES=new Set(['all','added','not-added','favorite','hidden']);
 const MAX_PAGES=100,MAX_REFERENCES=5000;
 function fail(m){throw Error(m);}
 function integer(v,label,min=1,max=100){
   if(typeof v==='number'&&Number.isSafeInteger(v)&&v>=min&&v<=max)return v;
   if(typeof v==='string'&&/^\d+$/.test(v.trim())){const n=Number(v.trim());if(Number.isSafeInteger(n)&&n>=min&&n<=max)return n;}
   return fail(label+' invalide.');
 }
 function year(v){return v==null||v===''||v===0?null:integer(v,'Année',1800,2200);}
 function filters(raw={}){
  const kind=String(raw.kind||'show');if(!KINDS.has(kind))fail('Catégorie inconnue.');
  const min=year(raw.min),max=year(raw.max);if(min!=null&&max!=null&&min>max)fail('L’année minimum doit précéder l’année maximum.');
  const lang=String(raw.language||'').trim().toLowerCase();if(lang&&!/^[a-z]{2,3}$/.test(lang))fail('Langue originale : code de deux ou trois lettres.');
  const sort=String(raw.sort||'source'),status=String(raw.status||'all');if(!SORTS.has(sort)||!STATES.has(status))fail('Filtre de découverte invalide.');
  return {kind,min,max,language:lang,sort,status,query:String(raw.query||'').trim().slice(0,180)};
 }
 function canonical(item){
  if(!item||typeof item!=='object'||!KINDS.has(item.kind)||typeof item.source!=='string'||typeof item.sourceId!=='string'||!item.sourceId.trim()||typeof item.title!=='string'||!item.title.trim())fail('Référence catalogue incomplète.');
  return item.kind+':'+item.source.toLowerCase()+':'+item.sourceId;
 }
 function create(raw){return {filters:filters(raw),items:[],keys:[],page:0,next:1,more:true,error:null,complete:false};}
 function accept(s,requested,result){
  if(!s||s.complete||requested!==s.next)fail('Page de découverte inattendue.');
  integer(requested,'Page',1,MAX_PAGES);
  if(!result||!Array.isArray(result.items)||typeof result.more!=='boolean'||result.items.length>500)fail('Réponse de découverte invalide.');
  const seen=new Set(s.keys),add=[];for(const candidate of result.items){const key=canonical(candidate);if(candidate.kind!==s.filters.kind)fail('Catégorie catalogue contradictoire.');if(!seen.has(key)){seen.add(key);add.push(candidate);}}
  if(s.items.length+add.length>MAX_REFERENCES)fail('La limite de références de découverte est atteinte.');
  const items=[...s.items,...add],more=result.more&&requested<MAX_PAGES&&items.length<MAX_REFERENCES;
  return {...s,items,keys:[...seen],page:requested,next:requested+1,more,complete:!more,error:null};
 }
 function failed(s,page,message){if(page!==s.next)fail('Échec sur une page obsolète.');return {...s,error:{page,message:String(message||'Catalogue indisponible.').slice(0,300)}};}
 function fold(v){return String(v==null?'':v).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().trim();}
 function connected(candidate,library=[]){
  return library.find(m=>m&&m.kind===candidate.kind&&m.source===candidate.source&&String(m.sourceId||'')===candidate.sourceId)||null;
 }
 function view(s,library=[]){
  const cfg=s.filters,q=fold(cfg.query),libraryIndex=new Map();
  for(const m of library)if(m&&typeof m.source==='string'&&m.sourceId!=null&&m.kind)libraryIndex.set(m.kind+':'+m.source.toLowerCase()+':'+String(m.sourceId),m);
  const languageAliases={English:'en',French:'fr',Spanish:'es',German:'de',Japanese:'ja',Portuguese:'pt',Italian:'it',Korean:'ko',Chinese:'zh',Russian:'ru'};
  const items=s.items.filter(m=>{
    if(cfg.min!=null&&(!Number.isFinite(m.year)||m.year<cfg.min))return false;
    if(cfg.max!=null&&(!Number.isFinite(m.year)||m.year>cfg.max))return false;
    if(cfg.language){const original=String(m.originalLanguage||'');const lang=languageAliases[original]||fold(original);if(lang&&lang!==cfg.language&&!lang.startsWith(cfg.language+'-'))return false;}
    if(q&&!fold(m.title).includes(q)&&!fold(m.creator||'').includes(q))return false;
    const current=libraryIndex.get(canonical(m)),added=!!current?.added;
    if(cfg.status==='added'&&!added)return false;
    if(cfg.status==='not-added'&&added)return false;
    if(cfg.status==='favorite'&&!(added&&current.favorite))return false;
    if(cfg.status==='hidden'&&!(added&&current.hidden))return false;
    return true;
  });
  const sorted=[...items];if(cfg.sort==='title')sorted.sort((a,b)=>a.title.localeCompare(b.title,'fr')||canonical(a).localeCompare(canonical(b)));
  if(cfg.sort==='year-asc'||cfg.sort==='year-desc')sorted.sort((a,b)=>{
     const ay=Number.isFinite(a.year)?a.year:null,by=Number.isFinite(b.year)?b.year:null;
     if(ay==null)return by==null?canonical(a).localeCompare(canonical(b)):1;
     if(by==null)return -1;
     return (ay-by)*(cfg.sort==='year-asc'?1:-1)||a.title.localeCompare(b.title,'fr');
  });
  return sorted;
 }
 function contentKey(raw){const f=filters(raw);return JSON.stringify([f.kind,f.min,f.max,f.language]);}
 return {KINDS:[...KINDS],SORTS:[...SORTS],STATES:[...STATES],MAX_PAGES,MAX_REFERENCES,filters,canonical,create,accept,failed,connected,view,contentKey};
});
