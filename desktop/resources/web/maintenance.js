/* Safe local maintenance. Never includes credentials in diagnostics or image caches. */
'use strict';
function prepareLibraryReplacement(reason){
 if(typeof availabilityForget==='function')availabilityForget();
  if(typeof episodeSheetForget==='function')episodeSheetForget();if(typeof forgetPublicPeople==='function')forgetPublicPeople();
  if(typeof invalidateCatalogueReads==='function')invalidateCatalogueReads();
  assertWritable();
  if(traktSyncing)throw Error('Une synchronisation est en cours. Termine-la avant de remplacer la bibliothèque.');
  if(typeof invalidateGamingReads==='function')invalidateGamingReads();
  libraryEpoch++;if(typeof invalidateSearch==='function')invalidateSearch();searchGeneration++;discoverGeneration++;FGRequests.clear();clearTimeout(traktSaveTimer);
  for(const op of syncState.queue)op.held=reason;
  syncState.baseline={};seriesViews.clear();libraryViews.clear();
}
function bindHeldOperations(){
  $$('[data-resume-op]').forEach(b=>b.onclick=()=>{
    if(traktSyncing)return toast('Attends la fin de la synchronisation avant cette décision.');
    const op=syncState.queue.find(x=>x.id===b.dataset.resumeOp);if(!op)return;
    if(!confirm(`Ancienne action : ${op.title} · ${op.field} · ${op.at}. Cet envoi peut modifier Trakt même si le titre ne figure plus dans ta bibliothèque. As-tu vérifié et souhaites-tu le reprendre ?`))return;
    delete op.held;syncStore();render();
  });
  $$('[data-discard-op]').forEach(b=>b.onclick=()=>{
    if(traktSyncing)return toast('Attends la fin de la synchronisation avant cette décision.');
    if(!confirm('Annuler uniquement cet envoi en attente ? Ni la bibliothèque ni Trakt ne seront modifiés.'))return;
    syncState.queue=syncState.queue.filter(x=>x.id!==b.dataset.discardOp);syncStore();render();
  });
}
const IMAGE_CACHE_PREFIX='franchiseguide-images-';
const bytesLabel=n=>!Number.isFinite(n)?'Non communiqué':n<1048576?(n/1024).toFixed(1)+' Ko':(n/1048576).toFixed(1)+' Mo';
async function storageHealth(){
  let estimate={},persistent=null,images=null,shells=[],error=null;
  try{estimate=await navigator.storage?.estimate?.()||{};persistent=await navigator.storage?.persisted?.()??null;}catch{}
  try{if('caches'in window){const keys=await caches.keys();shells=keys.filter(k=>k.startsWith('franchiseguide-web-'));images=0;for(const k of keys.filter(k=>k.startsWith(IMAGE_CACHE_PREFIX)))images+=(await (await caches.open(k)).keys()).length;}}catch(e){error='Le navigateur refuse l’accès au cache.';}
  let localBytes=0;try{for(let i=0;i<localStorage.length;i++){const k=localStorage.key(i);if(k?.startsWith('franchiseguide-'))localBytes+=2*(k.length+(localStorage.getItem(k)||'').length);}}catch{}
  return {estimate,persistent,images,shells,error,localBytes};
}
async function clearPosterCache(){
  if(!('caches'in window))throw Error('Cache non disponible sur cette origine.');
  const names=(await caches.keys()).filter(k=>k.startsWith(IMAGE_CACHE_PREFIX));
  await Promise.all(names.map(k=>caches.delete(k)));return names.length;
}
async function renderMaintenance(){
  const h=document.createElement('section');h.className='maintenance';$('#content').replaceChildren(h);
  h.innerHTML=`<section class="hero"><h2>Données & hors ligne</h2><p>Ta bibliothèque et ton suivi restent dans ce navigateur. Les images sont un cache séparé, pas une sauvegarde.</p></section><section class="panel" id="storageFacts"><p role="status">Vérification du stockage…</p></section><section class="panel"><h3>Copies indépendantes</h3><p>Les instantanés locaux partagent le quota du navigateur. Un export JSON est nécessaire pour une copie indépendante.</p><div class="actions"><button id="maintExport" class="primary">Exporter ma bibliothèque</button><button id="maintSnapshot" class="tonal">Créer un instantané</button><button id="maintDurable" class="tonal">Créer une copie de récupération IndexedDB</button></div><div id="durableSnapshots" class="menu"><div>Vérification des copies durables…</div></div></section><section class="panel"><h3>Affiches et fonctionnement hors ligne</h3><p>Les fiches déjà enregistrées restent consultables. Les images déjà affichées peuvent être mises en cache, dans la limite de 100 images. Les nouvelles recherches et Trakt nécessitent Internet. Aucune synchronisation n’est garantie application fermée.</p><div class="actions"><button id="persistStorage" class="tonal">Demander la conservation du stockage</button><button id="clearPosters" class="tonal">Vider seulement le cache des images</button></div><p id="maintStatus" role="status" aria-live="polite"></p></section><section class="panel"><h3>Services de catalogue</h3><p>Séries : ${serviceCredential('tmdb')?'TMDb configuré':'TVmaze sans clé'} · films : ${serviceCredential('tmdb')?'TMDb configuré':'Apple/iTunes, fiche limitée'} · jeux : ${serviceCredential('rawg')?'RAWG configuré':'FreeToGame, gratuits uniquement'} · livres : Open Library sans clé. Trakt : ${traktConnected()?'compte connecté':'non connecté'}.</p><p>Une clé renseignée n’est pas une connexion vérifiée.</p><button id="maintSettings" class="tonal">Ouvrir les réglages</button></section>`;
  $('#maintExport').onclick=exportFg;$('#maintSnapshot').onclick=()=>{if(!storageWritable)return toast('Fenêtre en lecture seule.');toast(createBackup('Manuelle')?'Instantané créé.':'Copie locale impossible : exporte un JSON.');};
  $('#maintDurable').onclick=async()=>{if(!FGDurable?.available())return toast('IndexedDB indisponible.');try{assertWritable();await FGDurable.put(toPortableBackup(state),'Copie manuelle');toast('Copie IndexedDB créée.');await renderDurableSnapshots();}catch(e){toast('Copie durable impossible : '+e.message);}};
  $('#maintSettings').onclick=settingsButtonHandler;
  $('#persistStorage').onclick=async()=>{const host=$('#maintStatus');try{const granted=await navigator.storage?.persist?.();host.textContent=granted?'Conservation accordée par le navigateur. Cela ne remplace pas un export.':'Conservation non accordée ou API indisponible. Continue les exports réguliers.';}catch{host.textContent='Cette origine ne permet pas la conservation du stockage.';}};
  $('#clearPosters').onclick=async()=>{if(!confirm('Effacer les images en cache seulement ? Les titres, notes, épisodes et identifiants resteront intacts.'))return;try{await clearPosterCache();if(h.isConnected)renderMaintenance();toast('Cache des images vidé, bibliothèque conservée.');}catch(e){toast(e.message);}};
  await renderDurableSnapshots();
  const data=await storageHealth();if(!h.isConnected)return;
  const facts={'Bibliothèque':`${state.media.filter(m=>m.added).length} titres · ${state.episodes.length} épisodes`,'Écriture':storageWritable?'Cette fenêtre est autorisée':'Lecture seule','Hors ligne':navigator.onLine===false?'Réseau déclaré indisponible':'Réseau déclaré disponible','Cache application':navigator.serviceWorker?.controller?'Contrôleur actif':'Contrôleur non actif','Images mises en cache':data.images??'Indisponible','Stockage local estimé':bytesLabel(data.localBytes),'Usage total de l’origine':bytesLabel(data.estimate.usage),'Quota total de l’origine':bytesLabel(data.estimate.quota),'Conservation':data.persistent===null?'Indisponible':data.persistent?'Accordée':'Non accordée','Envois Trakt suspendus':syncState.queue.filter(x=>x.held).length};
  $('#storageFacts').innerHTML=`<h3>État de cet appareil</h3><div class="facts">${Object.entries(facts).map(([k,v])=>`<div><b>${esc(k)}</b><span>${esc(v)}</span></div>`).join('')}</div><p class="meta">L’estimation du navigateur regroupe plusieurs stockages. Elle n’indique pas le quota propre à localStorage. Le suivi utilise toujours localStorage.</p>${data.error?`<p class="notice warn">${esc(data.error)}</p>`:''}`;
}


async function renderDurableSnapshots(){
  const host=$('#durableSnapshots');if(!host)return;
  if(typeof FGDurable==='undefined'||!FGDurable.available()){host.innerHTML='<div>IndexedDB indisponible sur cette origine.</div>';return;}
  try{
    const rows=await FGDurable.list();
    host.innerHTML=rows.length?rows.map((r,i)=>`<div><b>${esc(humanDate(r.at))}</b> · ${esc(r.reason||'Récupération')} <button class="tonal" data-durable-export="${esc(r.id)}">Exporter</button> <button class="tonal" data-durable-restore="${esc(r.id)}">Restaurer</button> <button class="tonal danger" data-durable-delete="${esc(r.id)}">Supprimer</button></div>`).join(''):'<div>Aucune copie durable IndexedDB.</div>';
    host.querySelectorAll('[data-durable-export]').forEach(b=>b.onclick=()=>{const r=rows.find(x=>x.id===b.dataset.durableExport);if(r)downloadJson('franchiseguide-recuperation-'+r.at.slice(0,10)+'.json',r.state);});
    host.querySelectorAll('[data-durable-restore]').forEach(b=>b.onclick=()=>{const row=rows.find(x=>x.id===b.dataset.durableRestore);if(row)reviewDurableRestore(row);});
    host.querySelectorAll('[data-durable-delete]').forEach(b=>b.onclick=async()=>{if(!confirm('Supprimer uniquement cette copie IndexedDB ?'))return;await FGDurable.remove(b.dataset.durableDelete);await renderDurableSnapshots();});
  }catch(e){host.innerHTML='<div>Copies durables indisponibles : '+esc(e.message)+'</div>';}
}

function renderIntegrity(){
  const report=FGIntegrity.audit(state),issues=report.issues,dupes=report.duplicateCandidates;
  $('#content').innerHTML=`<section class="hero"><h2>Intégrité des données</h2><p>Analyse locale de la bibliothèque. Les réparations automatiques sont limitées aux références sans ambiguïté ; aucune fusion de titre n’est effectuée sans confirmation.</p><div class="facts"><div><b>Critiques</b><span>${report.counts.critical}</span></div><div><b>Avertissements</b><span>${report.counts.warning}</span></div><div><b>Réparables</b><span>${report.counts.repairable}</span></div><div><b>Doublons candidats</b><span>${report.counts.candidates}</span></div></div><div class="actions"><button id="integrityRepair" class="primary" ${report.counts.repairable?'':'disabled'}>Réparer les références sûres</button><button id="integrityExport" class="tonal">Exporter le rapport</button></div></section><section class="panel"><h3>Problèmes détectés</h3>${report.truncated?'<p class="notice warn">Analyse de doublons partielle : limite de 1 000 candidats ou 20 000 paires atteinte. Aucun résultat complet ni fusion automatique n’est annoncé.</p>':''}${issues.length?issues.map(i=>`<div class="notice ${i.severity==='critical'?'warn':''}"><b>${esc(i.code)}</b> · ${esc(i.message)}</div>`).join(''):'<p>Aucun problème structurel détecté.</p>'}</section><section class="panel"><h3>Doublons candidats</h3>${dupes.length?dupes.map((d,n)=>`<article class="duplicate-candidate"><b>${esc(d.title)}${d.year?' · '+esc(d.year):''}</b><p>${esc(d.kind)} · ${d.safe?'Fusion contrôlée disponible':'Fusion bloquée'}</p>${d.reason?`<p class="meta">${esc(d.reason)}</p>`:''}${d.conflicts.length?`<p class="meta">${d.conflicts.map(c=>esc(c.field+': '+c.a+' ≠ '+c.b)).join(' · ')}</p>`:''}<div class="actions"><button class="tonal" data-merge-a="${esc(d.aId)}" data-merge-b="${esc(d.bId)}" ${d.safe?'':'disabled'}>Garder le premier</button><button class="tonal" data-merge-a="${esc(d.bId)}" data-merge-b="${esc(d.aId)}" ${d.safe?'':'disabled'}>Garder le second</button></div></article>`).join(''):'<p>Aucun doublon candidat.</p>'}</section>`;
  $('#integrityExport').onclick=()=>downloadJson('franchiseguide-integrite.json',{app:'FranchiseGuide',at:new Date().toISOString(),...report});
  $('#integrityRepair').onclick=()=>{if(!confirm('Réparer les références de listes en double/inexistantes et supprimer uniquement les épisodes orphelins sans aucun suivi ?'))return;try{requireSafetySnapshot('Avant réparation intégrité');const fixed=FGIntegrity.safeRepair(state);state=fixed.state;save(false);toast(`${fixed.removedEpisodeIds.length} épisode(s) orphelin(s) sans suivi supprimé(s).`);renderIntegrity();}catch(e){toast('Réparation impossible : '+e.message);}};
  $$('[data-merge-a]').forEach(b=>b.onclick=()=>{const a=state.media.find(m=>m.id===b.dataset.mergeA),c=state.media.find(m=>m.id===b.dataset.mergeB);if(!a||!c)return;if(!confirm(`Fusionner « ${c.title} » dans « ${a.title} » ? Les identifiants incompatibles bloquent automatiquement l’opération.`))return;try{requireSafetySnapshot('Avant fusion de doublons');prepareLibraryReplacement('Fusion contrôlée de doublons');const merged=FGIntegrity.mergeMedia(state,a.id,c.id);state=migrate(merged.state);save(false);toast('Doublons fusionnés localement.');renderIntegrity();}catch(e){toast('Fusion refusée : '+e.message);}});
}
