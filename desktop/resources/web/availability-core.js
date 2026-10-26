/* Country/scope-bound watch-provider data. Only rules; no credentials, persistence or account writes. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.FGAvailability=api;})(globalThis,function(){
 'use strict';
 const MODES=Object.freeze([['flatrate','Abonnement'],['free','Gratuit'],['ads','Avec publicité'],['rent','Location'],['buy','Achat']]);
 const record=x=>!!x&&typeof x==='object'&&!Array.isArray(x),own=(o,k)=>Object.prototype.hasOwnProperty.call(o,k);
 const fail=s=>{throw Error(s);};
 function code(v){if(typeof v!=='string'||!/^\s*[a-zA-Z]{2}\s*$/.test(v))fail('Pays : code à deux lettres requis (ex. FR).');return v.trim().toUpperCase();}
 function number(v,label,max=2147483647){if(!Number.isSafeInteger(v)||v<0||v>max)fail(label+' invalide.');return v;}
 function name(v,label){if(typeof v!=='string'||!v.trim()||v.length>250||/[\u0000-\u001f\u007f]/.test(v))fail(label+' absent ou invalide.');return v.trim();}
 function request(item,region,season=null){
  const kind=String(item?.kind||'').toLowerCase();if(!['show','movie'].includes(kind))fail('Disponibilités réservées aux séries et films.');
  const id=number(item.tmdbId,'Identifiant TMDb');if(id===0)fail('Identifiant TMDb manquant : actualise ou associe la fiche au catalogue.');
  const country=code(region);if(season!=null){number(season,'Saison',99999);if(kind!=='show')fail('Un film ne possède pas de saison.');}
  return {kind,id,country,season,key:[kind,id,country,season==null?'all':season].join(':'),path:`/${kind==='show'?'tv':'movie'}/${id}${season==null?'':'/season/'+season}/watch/providers`};
 }
 function logo(path){return typeof path==='string'&&/^\/[A-Za-z0-9._-]+$/.test(path)?'https://image.tmdb.org/t/p/w185'+path:null;}
 function sourceLink(value,q){
  if(typeof value!=='string'||value.length>2000||/[\u0000-\u0020\u007f\\]/.test(value))return null;
  try{const u=new URL(value),type=q.kind==='show'?'tv':'movie';if(u.protocol!=='https:'||!['www.themoviedb.org','themoviedb.org'].includes(u.hostname)||u.port||u.username||u.password||u.hash)return null;
   const part=u.pathname.split('/');if(part.length!==4||part[1]!==type||part[3]!=='watch'||!new RegExp('^'+q.id+'(?:-[^/]+)?$').test(part[2]))return null;
   // No raw credential or arbitrary redirect query is ever reused.
   const out=new URL(u.origin+u.pathname);out.searchParams.set('locale',q.country);return out.href;
  }catch{return null;}
 }
 function regions(raw){
  if(!record(raw)||!Array.isArray(raw.results)||raw.results.length>300)fail('Liste des pays incomplète ou invalide.');
  const seen=new Set();return raw.results.map(r=>{if(!record(r))fail('Pays invalide.');const country=code(r.iso_3166_1);if(seen.has(country))fail('Pays présent plusieurs fois.');seen.add(country);
   return {code:country,label:name(r.native_name||r.english_name,'Nom du pays')};}).sort((a,b)=>a.code<b.code?-1:a.code>b.code?1:0);
 }
 function parse(raw,q,at=Date.now()){
  q=request({kind:q.kind,tmdbId:q.id},q.country,q.season);number(at,'Horodatage',8640000000000000);
  if(!record(raw)||!record(raw.results))fail('Réponse des disponibilités incomplète. Le dernier relevé reste conservé.');
  if(q.season===null&&raw.id!=null&&raw.id!==q.id)fail('Réponse de disponibilités destinée à un autre titre.');
  const present=own(raw.results,q.country),node=present?raw.results[q.country]:{};
  if(!record(node))fail('Données du pays invalides.');
  let ignoredLinks=0,total=0;const signatures=new Map();
  const groups=MODES.map(([mode,label])=>{
   const list=own(node,mode)?node[mode]:[];if(!Array.isArray(list)||list.length>1000)fail('Offres '+label+' incomplètes ou trop nombreuses.');
   const seen=new Map();
   for(const p of list){if(!record(p))fail('Service invalide.');const id=number(p.provider_id,'Identifiant du service');if(!id)fail('Identifiant du service absent.');
    const title=name(p.provider_name,'Nom du service'),path=p.logo_path,icon=logo(path);if(path!=null&&path!==''&&!icon)ignoredLinks++;
    const priority=p.display_priority==null?2147483647:number(p.display_priority,'Priorité du service');
    const signature=JSON.stringify([title,icon]);if(signatures.has(id)&&signatures.get(id)!==signature)fail('Identité de service contradictoire.');signatures.set(id,signature);
    const offer={id,name:title,logoUrl:icon,priority};if(seen.has(id)&&seen.get(id).priority!==priority)fail('Priorité contradictoire pour un service.');seen.set(id,offer);
   }
   const offers=[...seen.values()].sort((a,b)=>a.priority-b.priority||a.id-b.id);total+=offers.length;return {mode,label,offers};
  });
  if(total>2500)fail('Trop d’offres dans la réponse.');const link=sourceLink(node.link,q);if(node.link!=null&&node.link!==''&&!link)ignoredLinks++;
  return {query:q,observedAt:at,countryReported:present,groups,link,ignoredLinks,totalOffers:total,totalProviders:signatures.size};
 }
 function filter(snapshot,{mode='all',query=''}={}){
  if(mode!=='all'&&!MODES.some(x=>x[0]===mode))fail('Type d’accès inconnu.');if(typeof query!=='string'||query.length>250)fail('Recherche trop longue.');
  const fold=v=>v.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('fr');const needle=fold(query.trim());
  return snapshot.groups.filter(g=>mode==='all'||g.mode===mode).map(g=>({...g,offers:g.offers.filter(p=>!needle||fold(p.name).includes(needle))}));
 }
 function seasons(m,episodes=[]){
  if(m?.kind!=='show')return [];
  return [...new Set([...(m.seasons||[]).map(x=>x.number),...episodes.filter(e=>e.showId===m.id&&!e.seasonUnknown).map(e=>e.season)])]
   .filter(n=>Number.isSafeInteger(n)&&n>=0&&n<=99999).sort((a,b)=>a-b);
 }
 function stateLabel(s){if(!s)return 'not-read';if(!s.countryReported)return 'country-not-reported';return s.totalOffers?'reported':'no-offers-reported';}
 return {MODES,code,request,logo,sourceLink,regions,parse,filter,seasons,stateLabel};
});
