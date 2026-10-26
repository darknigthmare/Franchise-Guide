/* V0.20: complete catalogue updates are applied one at a time with epoch checks and backoff. */
'use strict';
let catalogMaintenance={running:false,cancelled:false,controller:null,message:'Aucune lecture en cours.',updated:0,failed:0};
let catalogueView={query:'',filter:'all',limit:60};
function catalogRows(){return FGCatalog.audit(state.media,state.episodes,effectiveCatalogSettings(),state._catalogMaintenance,Date.now());}
function safeCatalogError(error){let s=String(error?.message||'Catalogue indisponible.');for(const key of ['tmdbKey','rawgKey','traktId','traktSecret'])if(state.settings[key])s=s.split(state.settings[key]).join('[masqué]');return s.replace(/(api_key|client_secret|key)=([^&\s]+)/gi,'$1=[masqué]').slice(0,300);}
function stopCatalogue(){catalogMaintenance.cancelled=true;catalogMaintenance.controller?.abort('manual');catalogMaintenance.message='Lecture annulée. Les métadonnées déjà enregistrées restent conservées.';}
function invalidateCatalogueReads(){stopCatalogue();}
async function catalogueRead(requested,{automatic=false}={}){
 const target=state.media.find(m=>m.id===requested.id&&m.added);if(!target)throw Error('Ce titre a été retiré.');
 const row=catalogRows().find(r=>r.id===requested.id);if(!row?.provider)throw Error('Fiche sans catalogue compatible. Les fiches Steam/Retro/manuelles ne sont pas rapprochées par leur seul titre.');
 if(row.status==='key-missing')throw Error('Clé du catalogue à configurer dans les réglages.');if(row.status==='waiting')throw Error('Un délai catalogue est en cours jusqu’au '+humanDate(row.retryAt)+'.');
 if(catalogBusy.has(identity(target)))throw Error('Cette fiche est déjà en lecture.');
 assertWritable();const epoch=libraryEpoch,fingerprint=FGCatalog.fingerprint(target),controller=new AbortController(),key=identity(target);catalogMaintenance.controller=controller;catalogBusy.add(key);
 const timeout=setTimeout(()=>controller.abort('timeout'),90000);
 try{
  const source={...clone(target),...clone(requested)};if(row.provider==='rawg'&&source.rawgId){source.source='rawg';source.sourceId=String(source.rawgId);}if(row.provider==='openlibrary'&&source.openLibraryId){source.source='openlibrary';source.sourceId=source.openLibraryId;}
  const fresh=await enrichCatalog(source,{signal:controller.signal});
  if(controller.signal.aborted)throw Error(controller.signal.reason==='timeout'?'Le catalogue dépasse le délai de lecture.':'Lecture annulée.');
  const live=state.media.find(m=>m.id===requested.id&&m.added);if(epoch!==libraryEpoch||!live||FGCatalog.fingerprint(live)!==fingerprint)throw Object.assign(Error('Bibliothèque ou identité de la fiche modifiée : réponse ignorée.'),{stale:true});
  assertWritable();const personal=trackingFields(live);commitCatalog(fresh,live);Object.assign(live,fresh,personal);assignFranchise(live);
  state._catalogMaintenance=FGCatalog.success(state._catalogMaintenance,live.id,Date.now());save(false);catalogMaintenance.updated++;seriesViews.delete(live.id);
  return true;
 }catch(e){
  const unchanged=epoch===libraryEpoch&&state.media.some(m=>m.id===requested.id&&m.added&&FGCatalog.fingerprint(m)===fingerprint);
  if(unchanged&&!e.stale&&controller.signal.reason!=='manual'&&!['READ_ONLY','STALE_WRITE','RECOVERY_ONLY'].includes(e.code)&&!storageDraft){
   // Cancellation never records a provider outage. A timeout does, so failed titles do not starve others.
   if(!controller.signal.aborted||controller.signal.reason==='timeout'){
    state._catalogMaintenance=FGCatalog.failure(state._catalogMaintenance,row,{status:e.status,retryAfter:e.retryAfter,message:safeCatalogError(e)},Date.now());try{save(false);}catch{}catalogMaintenance.failed++;
   }
  }
  throw e;
 }finally{clearTimeout(timeout);catalogBusy.delete(key);if(catalogMaintenance.controller===controller)catalogMaintenance.controller=null;}
}
// Override the old refresh path as well as its three-title automatic sweep.
async function refreshCatalogItem(m){
 if(catalogMaintenance.running){toast('Une lecture catalogue est déjà en cours.');return;}
 catalogMaintenance.running=true;catalogMaintenance.cancelled=false;catalogMaintenance.updated=0;catalogMaintenance.failed=0;
 try{await catalogueRead(m);catalogMaintenance.message='Fiche actualisée, suivi personnel conservé.';toast(catalogMaintenance.message);render();}catch(e){catalogMaintenance.message=safeCatalogError(e);toast(catalogMaintenance.message);}finally{catalogMaintenance.running=false;if(overlay?.type==='catalog-health')render();}
}
async function repairIncompleteCatalog(){if(!storageWritable||catalogMaintenance.running||catalogRefreshRunning||state.settings.catalogAutoRefresh===false||!navigator.onLine||document.hidden||document.querySelector('dialog[open]'))return;await runCatalogueBatch({automatic:true,incompleteOnly:false});}
async function runCatalogueBatch({automatic=false,incompleteOnly=true}={}){
 if(catalogMaintenance.running||recoveryOnly||!storageWritable)return;
 const selected=FGCatalog.plan(catalogRows(),{incompleteOnly,limit:automatic?3:8});if(!selected.length){if(!automatic)toast('Aucune fiche éligible ; les délais et clés manquantes ne sont pas contournés.');return;}
 const epoch=libraryEpoch;catalogMaintenance={running:true,cancelled:false,controller:null,message:'Lecture des catalogues…',updated:0,failed:0};catalogRefreshRunning=true;
 if(!automatic&&overlay?.type==='catalog-health')render();
 try{for(const planned of selected){
  if(libraryEpoch!==epoch||document.hidden||navigator.onLine===false||catalogMaintenance.cancelled)break;
  const row=catalogRows().find(r=>r.id===planned.id);if(!row||!['incomplete','outdated'].includes(row.status))continue;
  const m=state.media.find(m=>m.id===row.id&&m.added);if(!m)continue;
  try{await catalogueRead(m,{automatic});}catch(e){catalogMaintenance.message=safeCatalogError(e);if(storageDraft||e.stale||catalogMaintenance.cancelled)break;}
 }
 }finally{catalogRefreshRunning=false;catalogMaintenance.running=false;catalogMaintenance.message=`${catalogMaintenance.cancelled?'Lot annulé. ':''}${catalogMaintenance.updated} fiche(s) actualisée(s), ${catalogMaintenance.failed} échec(s) à reprendre après délai. ${automatic?'Passe automatique limitée à 3 fiches.':'Lot limité à 8 fiches ; relance pour les suivantes.'}`;if(!automatic)toast(catalogMaintenance.message);if(overlay?.type==='catalog-health'&&!document.querySelector('dialog[open]'))render();}
}
function renderCatalogueHealth(){
 const all=catalogRows(),q=catalogueView.query.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase(),rows=all.filter(r=>(catalogueView.filter==='all'||r.status===catalogueView.filter)&&(!q||String(r.title).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().includes(q)));
 const names={incomplete:'Fiche à compléter',outdated:'Actualisation conseillée',current:'Lecture récente',waiting:'Délai de reprise',unsupported:'Pas de catalogue associé', 'key-missing':'Clé manquante'};
 $('#content').innerHTML=`<section class="hero"><h2>Entretien des fiches catalogue</h2><p>Fiches cinéma/séries TMDb, jeux RAWG, livres Open Library. Les comptes Trakt et les listes de succès se gèrent dans leur propre centre ; aucun identifiant ni synopsis n’est deviné.</p><div class="facts"><div><b>${all.filter(r=>r.incomplete).length}</b><span>Fiches marquées incomplètes</span></div><div><b>${all.filter(r=>r.missingPoster).length}</b><span>Sans affiche enregistrée</span></div><div><b>${all.filter(r=>r.status==='waiting').length}</b><span>En attente d’un délai</span></div></div><div class="actions"><button id="repairCatalogMissing" class="primary" ${catalogMaintenance.running?'disabled':''}>Compléter jusqu’à 8 fiches</button><button id="repairCatalogOld" class="tonal" ${catalogMaintenance.running?'disabled':''}>Actualiser selon l’ancienneté</button><button id="cancelCatalogWork" class="tonal" ${catalogMaintenance.running?'':'disabled'}>Annuler la lecture</button></div><p class="notice" role="status">${esc(catalogMaintenance.message)}</p><p class="meta">Séries actives : 1 jour, séries terminées : 90 jours, autres catalogues : 30 jours. Une affiche absente peut l’être chez le fournisseur : elle n’entraîne pas de requêtes sans fin. Erreur : 5 à 60 minutes ; délai429 fournisseur appliqué à ses autres fiches. Les succès Steam/Retro ont leurs propres échéances.</p></section><section class="filter-panel"><label>Recherche<input id="catalogFind" type="search" value="${esc(catalogueView.query)}"></label><label>État<select id="catalogFilter"><option value="all">Tout</option>${Object.entries(names).map(([k,v])=>`<option value="${k}" ${catalogueView.filter===k?'selected':''}>${v}</option>`).join('')}</select></label><span>${rows.length} fiche(s)</span></section><section>${rows.slice(0,catalogueView.limit).map(r=>`<article class="catalog-health-row"><div><h3>${esc(r.title)}</h3><p>${esc(kindName(r.kind))} · ${esc(r.provider||'local / autre')} · ${names[r.status]}</p>${r.kind==='show'?`<p>${r.episodesLoaded} épisodes réguliers chargés${r.episodesExpected==null?' ; total annoncé inconnu':' / '+r.episodesExpected+' annoncés'}.</p>`:''}${r.message?`<p class="meta">${esc(r.message)}</p>`:''}${r.retryAt>Date.now()?`<p>Reprise après ${esc(humanDate(r.retryAt))}</p>`:''}</div><div class="actions"><button class="tonal" data-catalog-open="${esc(r.id)}">Ouvrir la fiche</button><button class="tonal" data-catalog-read="${esc(r.id)}" ${catalogMaintenance.running||['waiting','unsupported','key-missing'].includes(r.status)?'disabled':''}>Actualiser</button></div></article>`).join('')||empty('Aucune fiche dans ce filtre','Les fiches et leurs historiques ne sont pas effacés par cette vue.')}</section>${rows.length>catalogueView.limit?'<button id="moreCatalogHealth" class="tonal load-more">Afficher 60 fiches supplémentaires</button>':''}`;
 $('#catalogFind').oninput=e=>{catalogueView.query=e.target.value;catalogueView.limit=60;const pos=e.target.selectionStart;renderCatalogueHealth();$('#catalogFind').focus();$('#catalogFind').setSelectionRange(pos,pos);};$('#catalogFilter').onchange=e=>{catalogueView.filter=e.target.value;catalogueView.limit=60;renderCatalogueHealth();};$('#moreCatalogHealth')?.addEventListener('click',()=>{catalogueView.limit+=60;renderCatalogueHealth();});
 $('#repairCatalogMissing').onclick=()=>runCatalogueBatch();$('#repairCatalogOld').onclick=()=>runCatalogueBatch({incompleteOnly:false});$('#cancelCatalogWork').onclick=stopCatalogue;
 $$('[data-catalog-open]').forEach(b=>b.onclick=()=>{const m=state.media.find(m=>m.id===b.dataset.catalogOpen);if(m){overlay={type:'item',id:m.id,title:m.title,src:'media',returnTo:{type:'catalog-health',title:'Entretien des catalogues'}};render();}});
 $$('[data-catalog-read]').forEach(b=>b.onclick=()=>{const m=state.media.find(m=>m.id===b.dataset.catalogRead);if(m)refreshCatalogItem(m);});
}
