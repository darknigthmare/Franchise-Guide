/* Discovery UI V0.33: true provider paging, bounded results, no mutation before explicit Add. */
'use strict';
let discoverySessionV33=null,discoveryControllerV33=null,discoveryRenderLimitV33=60;
let discoverySessionGenerationV33=0;
function discoveryKindLabelV33(k){return {show:'Séries',movie:'Films',game:'Jeux',book:'Livres'}[k]||k;}
function discoveryFilterFormV33(f){
 return `<section class="hero"><h2>Découvrir</h2><p>Explorer les catalogues progressivement. La consultation ne modifie pas le suivi.</p>
  <form id="discoverFilters" class="filter-row">
   <label>Catégorie<select id="discoverKind">${FGDiscovery.KINDS.map(k=>`<option value="${k}" ${f.kind===k?'selected':''}>${esc(discoveryKindLabelV33(k))}</option>`).join('')}</select></label>
   <label>Année minimum<input id="discoverYearMin" type="number" min="1800" max="2200" value="${f.min??''}"></label>
   <label>Année maximum<input id="discoverYearMax" type="number" min="1800" max="2200" value="${f.max??''}"></label>
   <label>Langue VO (séries/films)<input id="discoverLanguageV33" maxlength="3" placeholder="fr, en…" value="${esc(f.language)}"></label>
   <button type="submit" class="primary">Appliquer les filtres</button>
  </form></section>
  <p id="publicDiscoveryInfo" class="notice">Découverte sans clé : index TVmaze (non classement de tendances), métadonnées selon les langues disponibles. Les catalogues sont consultés progressivement.</p>
  <section class="panel" id="discoverTools">
   <div class="filter-row"><label>Rechercher dans les références chargées<input type="search" id="discoverQuery" value="${esc(f.query)}" placeholder="Titre ou auteur"></label>
   <label>État<select id="discoverStatus">${[['all','Tous'],['added','Déjà ajoutés'],['not-added','Non ajoutés'],['favorite','Favoris'],['hidden','Masqués']].map(([id,name])=>`<option value="${id}" ${f.status===id?'selected':''}>${name}</option>`).join('')}</select></label>
   <label>Ordre des références chargées<select id="discoverSort">${[['source','Ordre du fournisseur'],['title','Titre'],['year-asc','Année ↑'],['year-desc','Année ↓']].map(([id,name])=>`<option value="${id}" ${f.sort===id?'selected':''}>${name}</option>`).join('')}</select></label></div>
   <p id="discoverSummary" role="status" aria-live="polite"></p>
  </section>
  <section id="discoverList" aria-live="polite"><p class="notice">Consultation du catalogue…</p></section>
  <div class="actions discover-footer" id="discoverFooter"><button class="tonal" id="discoverMore" type="button" hidden>Charger la prochaine page</button><button class="tonal" id="discoverRetry" type="button" hidden>Réessayer cette page</button><button class="tonal" id="discoverShowMore" type="button" hidden>Afficher davantage de références chargées</button></div>`;
}
function discoveryCurrentV33(){return overlay?.type==='discover'&&!!document.getElementById('discoverList');}
function discoveryReadFormV33(){
 const old=discoverySessionV33?.filters||{},read=id=>document.getElementById(id)?.value;
 return FGDiscovery.filters({kind:read('discoverKind')||old.kind||'show',min:read('discoverYearMin')||null,max:read('discoverYearMax')||null,language:read('discoverLanguageV33')||'',query:read('discoverQuery')||'',sort:read('discoverSort')||'source',status:read('discoverStatus')||'all'});
}
function discoveryDrawV33(){
 if(!discoveryCurrentV33()||!discoverySessionV33)return;
 const s=discoverySessionV33,visible=FGDiscovery.view(s,state.media),display=visible.slice(0,discoveryRenderLimitV33);
 const fragment=document.createDocumentFragment(),container=document.getElementById('discoverList');
 container.innerHTML='';
 if(s.error){const p=document.createElement('p');p.className='notice warn';p.setAttribute('role','alert');p.textContent=`Page ${s.error.page} : ${s.error.message}. Les résultats déjà chargés restent disponibles.`;fragment.append(p);}
 if(s.filters.kind==='movie'&&!effectiveCatalogSettings().tmdbKey&&s.page===0){
  const notice=document.createElement('p');notice.className='notice';notice.textContent='Sans accès TMDb, Apple/iTunes propose une recherche de films par titre mais pas une liste de découverte populaire. Utilise la recherche ciblée.';fragment.append(notice);
  const b=document.createElement('button');b.type='button';b.className='primary';b.textContent='Rechercher des films sans clé';b.onclick=()=>openScopedSearch('movie');fragment.append(b);
 }else if(!s.error&&s.page>0&&display.length===0){const p=document.createElement('p');p.className='notice';p.textContent=s.more?'Aucune référence correspondante parmi les pages chargées. Tu peux poursuivre le catalogue.':'Aucune référence correspondant aux filtres dans les pages consultées.';fragment.append(p);}
 const cards=document.createElement('div');cards.innerHTML=display.map(m=>{
  const current=FGDiscovery.connected(m,state.media);return current?.added?row(current):row({...m,suggestion:true},'remote');
 }).join('');while(cards.firstChild)fragment.append(cards.firstChild);
 container.append(fragment);
 remoteResults=s.items.map(m=>({...m}));bindRows();
 document.getElementById('discoverSummary').textContent=`${visible.length} référence(s) correspondant aux filtres · ${s.items.length} reçue(s) · ${s.page} page(s) fournisseur consultée(s)${s.complete?' · fin/limite atteinte':''}. Les filtres et tris portent sur les références déjà reçues, pas sur tout le catalogue mondial.`;
 const more=document.getElementById('discoverMore'),retry=document.getElementById('discoverRetry'),show=document.getElementById('discoverShowMore');
 more.hidden=!s.more||!!s.error||discoveryControllerV33!==null||(s.filters.kind==='movie'&&!effectiveCatalogSettings().tmdbKey);
 more.disabled=discoveryControllerV33!==null;
 retry.hidden=!s.error;retry.disabled=discoveryControllerV33!==null;
 show.hidden=visible.length<=discoveryRenderLimitV33;
 show.textContent=`Afficher les références suivantes (${Math.min(60,visible.length-discoveryRenderLimitV33)})`;
}
function discoverySetFilterV33(){
 try{const next=discoveryReadFormV33(),prev=discoverySessionV33;
  if(!prev)return;discoverySessionV33={...prev,filters:next};discoveryRenderLimitV33=60;discoveryDrawV33();
 }catch(e){toast(e.message);}
}
function discoveryBindV33(){
 document.getElementById('discoverFilters').onsubmit=e=>{
  e.preventDefault();try{
   const config=discoveryReadFormV33();
   // Persist only after settings have been successfully validated and recorded.
   const old=state.settings;
   state.settings={...old,discoverKind:config.kind,discoverYearMin:config.min||0,discoverYearMax:config.max||0,discoverLanguage:config.language};
   try{save(false);}catch(e){state.settings=old;throw e;}
   discoveryBeginV33(config);
  }catch(e){toast(e.message);}
 };
 ['discoverQuery','discoverStatus','discoverSort'].forEach(id=>document.getElementById(id).addEventListener(id==='discoverQuery'?'input':'change',discoverySetFilterV33));
 document.getElementById('discoverMore').onclick=()=>discoveryLoadV33();
 document.getElementById('discoverRetry').onclick=()=>discoveryLoadV33();
 document.getElementById('discoverShowMore').onclick=()=>{discoveryRenderLimitV33+=60;discoveryDrawV33();};
}
function discoveryBeginV33(config){
 ++discoverySessionGenerationV33;discoveryControllerV33?.abort();discoveryControllerV33=null;
 discoverySessionV33=FGDiscovery.create(config);discoveryRenderLimitV33=60;
 document.getElementById('content').innerHTML=discoveryFilterFormV33(discoverySessionV33.filters);
 discoveryBindV33();
 if(config.kind==='movie'&&!effectiveCatalogSettings().tmdbKey){discoverySessionV33={...discoverySessionV33,more:false,complete:true};discoveryDrawV33();return;}
 discoveryDrawV33();void discoveryLoadV33();
}
async function discoveryProviderV33(config,page,signal){
 const cfg=effectiveCatalogSettings(),kind=config.kind;
 if(!Number.isInteger(page)||page<1||page>FGDiscovery.MAX_PAGES)throw Error('Page hors limite.');
 if(kind==='show'&&!cfg.tmdbKey){
  try{
   const rows=await readTVmaze(`/shows?page=${page-1}`,{signal,maxBytes:4*1024*1024});
   if(!Array.isArray(rows)||rows.length>250)throw Error('Page TVmaze invalide.');
   // Show index uses ID ranges. Less than 250 rows does not signal end of catalog.
   const items=rows.map(FGPublicCatalog.tvShow);
   return {items,more:rows.length>0};
  }catch(e){if(e.status===404&&page>1)return {items:[],more:false};throw e;}
 }
 if(kind==='game'&&!cfg.rawgKey){
  // Small public free-to-play list, served as a complete bounded index and sliced locally.
  const rows=await fetchJson('https://www.freetogame.com/api/games?sort-by=popularity',{signal,maxBytes:4*1024*1024,cacheMs:300000});
  if(!Array.isArray(rows)||rows.length>10000)throw Error('Catalogue FreeToGame incomplet.');
  return FGPublicCatalog.slice(rows.map(FGPublicCatalog.game),page);
 }
 if(kind==='show'||kind==='movie'){
  const type=kind==='show'?'tv':'movie';const url=new URLSearchParams({api_key:cfg.tmdbKey,language:cfg.language||'fr-FR',sort_by:'popularity.desc',include_adult:'false',page:String(page)});
  if(!cfg.tmdbKey)throw Error('La découverte de films par liste exige TMDb ; la recherche Apple reste accessible sans clé.');
  if(config.min)url.set(`${type==='tv'?'first_air_date':'primary_release_date'}.gte`,config.min+'-01-01');
  if(config.max)url.set(`${type==='tv'?'first_air_date':'primary_release_date'}.lte`,config.max+'-12-31');
  if(config.language)url.set('with_original_language',config.language);
  const data=await fetchJson(`https://api.themoviedb.org/3/discover/${type}?${url}`,{signal,maxBytes:2*1024*1024});
  if(!data||!Array.isArray(data.results)||!Number.isSafeInteger(data.total_pages)||data.total_pages<0)throw Error('Réponse de découverte TMDb invalide.');
  return {items:data.results.map(x=>tmdbSearchItem({...x,media_type:type})),more:page<data.total_pages};
 }
 if(kind==='game'){
  const q=new URLSearchParams({key:cfg.rawgKey,ordering:'-added',page_size:'20',page:String(page)});
  if(config.min||config.max)q.set('dates',`${config.min||1970}-01-01,${config.max||2100}-12-31`);
  const data=await fetchJson('https://api.rawg.io/api/games?'+q,{signal,maxBytes:2*1024*1024});
  if(!data||!Array.isArray(data.results))throw Error('Réponse de découverte RAWG invalide.');
  return {items:data.results.map(rawgSearchItem),more:typeof data.next==='string'&&data.next.length>0};
 }
 const query=config.min||config.max?`first_publish_year:[${config.min||1800} TO ${config.max||2200}]`:'subject:fiction';
 const q=new URLSearchParams({q:query,limit:'20',page:String(page),fields:'key,title,author_name,first_publish_year,cover_i,number_of_pages_median'});
 const data=await readOpenLibrary('https://openlibrary.org/search.json?'+q,{signal,maxBytes:2*1024*1024});
 const total=Number.isSafeInteger(data?.numFound)?data.numFound:data?.num_found;
 if(!data||!Array.isArray(data.docs)||!Number.isSafeInteger(total)||total<0)throw Error('Réponse de découverte Open Library invalide.');
 return {items:data.docs.map(openLibrarySearchItem),more:page*20<total};
}
async function discoveryLoadV33(){
 if(!discoveryCurrentV33()||!discoverySessionV33||discoveryControllerV33)return;
 const generation=discoverySessionGenerationV33,old=discoverySessionV33,page=old.next,epoch=libraryEpoch;
 if(!old.more&&!old.error)return;
 if(page>FGDiscovery.MAX_PAGES)return;
 const controller=new AbortController();discoveryControllerV33=controller;discoveryDrawV33();
 const valid=()=>!controller.signal.aborted&&generation===discoverySessionGenerationV33&&epoch===libraryEpoch&&discoveryCurrentV33();
 try{
  const result=await discoveryProviderV33(old.filters,page,controller.signal);
  if(!valid())return;
  discoverySessionV33=FGDiscovery.accept(discoverySessionV33,page,result);
 }catch(e){if(valid()&&e.name!=='AbortError')discoverySessionV33=FGDiscovery.failed(discoverySessionV33,page,e.message);}
 finally{if(discoveryControllerV33===controller)discoveryControllerV33=null;if(valid())discoveryDrawV33();}
}
async function renderDiscoverV33(page=1,append=false){
 await ensureAutomaticServices();if(overlay?.type!=='discover')return;
 const cfg=effectiveCatalogSettings();
 const current=FGDiscovery.filters({kind:cfg.discoverKind||'show',min:cfg.discoverYearMin||null,max:cfg.discoverYearMax||null,language:cfg.discoverLanguage||''});
 // render() may call this repeatedly: reuse loaded pages while user remains in Discover.
 if(page===1&&!append){if(discoverySessionV33&&FGDiscovery.contentKey(discoverySessionV33.filters)===FGDiscovery.contentKey(current)&&discoveryCurrentV33()&&document.getElementById('discoverFilters')){discoveryDrawV33();return;}
  discoveryBeginV33(current);return;
 }
 if(!discoverySessionV33){discoveryBeginV33(current);return;}
 void discoveryLoadV33();
}
