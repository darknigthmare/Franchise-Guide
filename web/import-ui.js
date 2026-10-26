/* V0.19: explicit, non-mutating import review, then guarded one-record commit. */
'use strict';
let importReadGeneration=0;
const IMPORT_METRICS={titles:'Titres suivis',show:'Séries',movie:'Films',game:'Jeux',book:'Livres',episodes:'Épisodes',watchedEpisodes:'Épisodes vus',datedViewings:'Visionnages datés connus',notes:'Notes privées',lists:'Listes locales',franchises:'Franchises personnalisées',accounts:'Profils gaming',sets:'Listes de succès',achievements:'Entrées de succès'};
const IMPORT_CHANGES={added:'Ajoutés',removed:'Absents après import',modified:'Différents',unchanged:'Identiques'};
function importParse(raw){try{return JSON.parse(raw.replace(/^\uFEFF/,''));}catch{throw Error('Texte JSON invalide ou incomplet : aucun fichier n’a été appliqué.');}}
function renderImportCenter(){
 $('#content').innerHTML=`<section class="hero"><h2>Importer et vérifier</h2><p>Choisis les fichiers, examine le résultat, puis confirme. Préparer ou annuler l’aperçu ne modifie pas la bibliothèque.</p></section><section class="panel"><h3>Sauvegarde FranchiseGuide</h3><p>Remplacement complet par un JSON portable V1 à V11 ou un fichier protégé .fgbackup. Les titres, listes et suivis absents du fichier ne seront plus dans la bibliothèque après confirmation. Les clés et jetons de cet appareil ne sont pas importés.</p><button id="reviewChooseFg" class="primary">Choisir une sauvegarde JSON ou protégée</button></section><section class="panel"><h3>Exports SeriesGuide</h3><p>Fusion des fichiers Séries, Films et Listes. Sélectionne les fichiers ensemble, dans n’importe quel ordre, pour résoudre leurs liens. Aucun rapprochement de titre seul n’est supposé.</p><button id="reviewChooseSg" class="primary">Choisir les exports SeriesGuide</button></section><section class="panel"><h3>Avant de remplacer</h3><p>Une copie locale de sécurité est obligatoire. Elle partage le quota de ce navigateur : conserve aussi un export indépendant. Les anciennes actions Trakt sont suspendues à confirmation, sans suppression distante ; une synchronisation déjà active bloque la validation.</p><p>32 Mio par fichier, 64 Mio au total, 10 fichiers au maximum. La lecture, le décodage UTF‑8 et tous les fichiers doivent réussir. Les ZIP/RAR ne sont pas des sauvegardes personnelles. Le fichier protégé ajoute 64 octets à la limite JSON.</p><button id="reviewExportCurrent" class="tonal">Exporter la bibliothèque actuelle</button><button id="reviewProtectedExport" class="tonal" type="button">Exporter avec une phrase secrète</button><p id="reviewImportStatus" role="status"></p></section>`;
 $('#reviewChooseFg').onclick=()=>$('#importFg').click();$('#reviewChooseSg').onclick=()=>$('#importSg').click();$('#reviewExportCurrent').onclick=exportFg;$('#reviewProtectedExport').onclick=openProtectedExport;
}
function makeImportReview(mode,files,texts,{payload=null,sourceReason=null}={}){
 const base=clone(state),baseKey=FGImport.contentKey(base),epoch=libraryEpoch,revision=storageRevision;
 const prepared=mode==='seriesguide'?stageSeriesGuide(texts,files.map(f=>f.name),base):{staged:fromPortableBackup(payload??importParse(texts[0])),report:null};
 const next=prepared.staged;
 const review={mode,files:files.map(f=>({name:f.name,size:f.size})),base,baseKey,epoch,revision,sourceReason,rawPrepared:next,report:prepared.report,importPreferences:false};
 updateImportComparison(review);return review;
}
function updateImportComparison(review){
 review.candidate=clone(review.rawPrepared);
 if(review.mode==='portable'&&!review.importPreferences)review.candidate.settings=clone(review.base.settings);
 // API credentials are local; never accept them from an imported document, even when restoring preferences.
 Object.assign(review.candidate.settings,serviceSettings());
 review.comparison=FGImport.compare(review.base,review.candidate);
 review.preparedKey=FGImport.contentKey(review.candidate);
}
function showImportReview(review){
 let limit=50,query='',kind='all',change='all';
 const c=review.comparison,d=activityDialog('importReviewDialog',review.mode==='portable'?'Vérifier le remplacement':'Vérifier la fusion SeriesGuide',`
 <form id="importReviewForm" novalidate><p class="notice"><b>${review.mode==='portable'?'Remplacement complet de la bibliothèque locale.':'Fusion avec les suivis déjà présents.'}</b> Aucun envoi Trakt ni téléchargement catalogue n’est lancé par cet aperçu. Les copies locales et la suspension des envois seront créées uniquement après confirmation.</p>
 <details><summary>${review.files.length} fichier(s) sélectionné(s)</summary><ul>${review.files.map(f=>`<li>${esc(f.name)} — ${esc(bytesLabel(f.size))}</li>`).join('')}</ul></details>
 <div class="import-metrics" role="region" aria-label="Comparaison des compteurs"><table><thead><tr><th scope="col">Données</th><th scope="col">Actuel</th><th scope="col">Après</th></tr></thead><tbody>${Object.entries(IMPORT_METRICS).map(([k,label])=>`<tr><th scope="row">${label}</th><td>${c.before[k]}</td><td>${c.after[k]}</td></tr>`).join('')}</tbody></table></div>
 <p id="importAttention" class="notice warn">${c.loss.dates} date(s) connue(s) absente(s) ; ${c.loss.watched} état(s) vu retiré(s) ; ${c.loss.notes} note(s) privée(s) modifiée(s) ou absente(s) ; ${c.loss.ratings} note(s) chiffrée(s) modifiée(s) ou absente(s). Comparaison par identifiant local : un identifiant différent compte comme une ancienne entrée absente, même si le titre ressemble.</p>
 ${review.report?`<p class="notice">SeriesGuide : ${review.report.shows} séries, ${review.report.movies} films, ${review.report.episodes} épisodes lus ; ${review.report.lists} listes ; ${review.report.linked} liens résolus ; <b>${review.report.skipped} référence(s) ignorée(s) ou non résolue(s)</b>. Un fichier manquant peut expliquer un lien non résolu. Les titres non identifiables ne sont pas inventés.</p>`:''}
 ${review.mode==='portable'?'<label class="toggle">Importer également les réglages publics du fichier<input id="importReviewPreferences" type="checkbox"></label><p class="meta">Par défaut, les réglages de cet appareil restent inchangés. Les profils gaming importés ont leurs automatismes désactivés ; leurs références ne sont pas des authentifications.</p>':''}
 <details id="importTitles"><summary>Comparer les fiches : ${c.changes.added} ajoutées, ${c.changes.removed} absentes, ${c.changes.modified} différentes, ${c.changes.unchanged} identiques</summary><div class="filter-row"><label>Titre<input id="importTitleQuery" type="search" maxlength="250"></label><label>Catégorie<select id="importTitleKind"><option value="all">Tout</option>${['show','movie','game','book'].map(k=>`<option value="${k}">${esc(kindName(k))}</option>`).join('')}</select></label><label>Différence<select id="importTitleChange"><option value="all">Tout</option>${Object.entries(IMPORT_CHANGES).map(([k,v])=>`<option value="${k}">${v}</option>`).join('')}</select></label></div><p id="importTitleCount" class="meta"></p><div id="importTitleRows"></div><button id="importMoreTitles" class="tonal" type="button">Afficher 50 fiches de plus</button></details>
 <button id="exportImportReview" class="tonal" type="button">Exporter le bilan des compteurs</button><p class="meta">Le bilan ne contient ni textes de notes ni profils détaillés. Les noms de fichiers peuvent être privés ; vérifie avant de le partager. Il ne remplace pas une sauvegarde.</p>
 <label class="import-ack"><input id="importReviewAcknowledge" type="checkbox">J’ai vérifié le résultat et compris ${review.mode==='portable'?'que la bibliothèque sera remplacée':'que ces données seront fusionnées'}.</label>
 <p id="importReviewError" class="notice warn hidden" role="alert"></p><div class="actions"><button id="commitImportReview" type="submit" class="primary" disabled>${review.mode==='portable'?'Remplacer la bibliothèque':'Appliquer la fusion'}</button><button id="cancelImportReview" type="button" class="tonal">Annuler sans modification</button></div></form>`);
 const drawRows=()=>{const rows=FGImport.filterRows(review.comparison.rows,query,kind,change);d.querySelector('#importTitleRows').innerHTML=rows.slice(0,limit).map(r=>`<article class="import-title-row"><div><b>${esc(r.title)}</b><small>${esc(kindName(r.kind))} · ${esc(IMPORT_CHANGES[r.change])}</small></div></article>`).join('')||'<p>Aucun titre pour ces filtres.</p>';d.querySelector('#importTitleCount').textContent=`${Math.min(limit,rows.length)} / ${rows.length} fiches affichées. Tous les résultats sont filtrés, pas seulement la partie visible.`;d.querySelector('#importMoreTitles').hidden=rows.length<=limit;};
 d.querySelector('#importTitles').addEventListener('toggle',()=>{if(d.querySelector('#importTitles').open)drawRows();});
 d.querySelector('#importTitleQuery').oninput=e=>{query=e.target.value;limit=50;drawRows();};d.querySelector('#importTitleKind').onchange=e=>{kind=e.target.value;limit=50;drawRows();};d.querySelector('#importTitleChange').onchange=e=>{change=e.target.value;limit=50;drawRows();};d.querySelector('#importMoreTitles').onclick=()=>{limit+=50;drawRows();};
 const ack=d.querySelector('#importReviewAcknowledge'),apply=d.querySelector('#commitImportReview');ack.onchange=()=>{apply.disabled=!ack.checked;};
 d.querySelector('#importReviewPreferences')?.addEventListener('change',e=>{review.importPreferences=e.target.checked;updateImportComparison(review);ack.checked=false;apply.disabled=true;});
 d.querySelector('#exportImportReview').onclick=()=>downloadJson('franchiseguide-bilan-import.json',FGImport.publicReport(review));
 d.querySelector('#cancelImportReview').onclick=()=>d.close();
 d.querySelector('form').onsubmit=e=>{e.preventDefault();if(!ack.checked)return;apply.disabled=true;try{
   assertWritable();FGImport.guard(review,state,libraryEpoch,storageRevision);
   if(FGImport.contentKey(review.candidate)!==review.preparedKey)throw Error('Aperçu modifié : relis les fichiers avant de confirmer.');
   commitImportCandidate(review.candidate,review.sourceReason|| (review.mode==='portable'?'Remplacement par import vérifié':'Fusion SeriesGuide vérifiée'));
   d.close();overlay={type:'import-center',title:'Importer et vérifier'};theme();render();
   $('#reviewImportStatus').textContent='Import appliqué et enregistré. Copie locale préalable conservée. Les anciennes actions Trakt sont suspendues, sans suppression distante.';toast('Import vérifié enregistré.');
 }catch(err){d.querySelector('#importReviewError').textContent=err.message;d.querySelector('#importReviewError').classList.remove('hidden');apply.disabled=!ack.checked;}};
 d.showModal();d.querySelector('.close').focus({preventScroll:true});d.scrollTop=0;
}
async function readImportSelection(mode,selected){
 const files=[...selected];if(!files.length)return;
 let busy=null,finished=false;const generation=++importReadGeneration;
 try{
  if(mode==='portable'){if(files.length!==1||!Number.isSafeInteger(files[0].size)||files[0].size<1||files[0].size>FGProtected.MAX_FILE)throw Error('Choisis un seul fichier de sauvegarde, 32 Mio maximum (plus 64 octets de protection).');}else FGImport.validateFiles(files,mode);const baseKey=FGImport.contentKey(state),epoch=libraryEpoch,revision=storageRevision;
  busy=activityDialog('readingImportDialog','Lire et vérifier les fichiers',`<p role="status" id="importReadProgress">Décodage UTF-8 et validation des ${files.length} fichier(s)…</p><p>Aucune modification de bibliothèque avant l’aperçu et sa confirmation.</p><button id="cancelReadImport" type="button" class="tonal">Annuler la préparation</button>`);
  busy.querySelector('#cancelReadImport').onclick=()=>busy.close();busy.addEventListener('close',()=>{if(!finished&&generation===importReadGeneration)importReadGeneration++;});busy.showModal();
  const texts=[];let bytes=0;
  for(let i=0;i<files.length;i++){
   const buffer=await files[i].arrayBuffer();if(generation!==importReadGeneration)return;
   bytes+=buffer.byteLength;if(buffer.byteLength!==files[i].size||buffer.byteLength>(mode==='portable'?FGProtected.MAX_FILE:FGImport.MAX_FILE)||bytes>FGImport.MAX_TOTAL)throw Error('La taille des données lues ne correspond pas à la sélection.');
   if(mode==='portable'&&(FGProtected.isProtected(buffer)||/\.fgbackup$/i.test(files[i].name))){FGImport.guard({baseKey,epoch,revision},state,libraryEpoch,storageRevision);finished=true;busy.close();openProtectedImport(files[i],new Uint8Array(buffer),{baseKey,epoch,revision,openedAt:Date.now()});return;}
   if(buffer.byteLength>FGImport.MAX_FILE)throw Error('JSON supérieur à 32 Mio.');
   try{texts.push(new TextDecoder('utf-8',{fatal:true}).decode(buffer));}catch{throw Error('Encodage UTF-8 invalide : aucun fichier appliqué.');}
   busy.querySelector('#importReadProgress').textContent=`${i+1} / ${files.length} fichier(s) lus.`;
  }
  FGImport.guard({baseKey,epoch,revision},state,libraryEpoch,storageRevision);
  const review=makeImportReview(mode,files,texts);if(generation!==importReadGeneration)return;
  finished=true;busy.close();showImportReview(review);
 }catch(e){toast('Import non appliqué : '+e.message);const host=$('#reviewImportStatus');if(host)host.textContent=e.message;diagnostic('warn','import','Préparation refusée. Bibliothèque inchangée.');}
 finally{finished=true;if(busy?.isConnected)busy.close();}
}
function reviewLocalRestore(index){const b=backups()[index];if(!b)return toast('Copie locale introuvable.');try{const raw=JSON.stringify(b.state);showImportReview(makeImportReview('portable',[{name:'Instantané local · '+(b.at||index),size:new TextEncoder().encode(raw).length}],[raw],{sourceReason:'Restauration locale vérifiée'}));}catch(e){toast('Copie non appliquée : '+e.message);}}
function reviewDurableRestore(row){try{const raw=JSON.stringify(row.state);showImportReview(makeImportReview('portable',[{name:'Copie IndexedDB · '+row.id,size:new TextEncoder().encode(raw).length}],[raw],{sourceReason:'Restauration IndexedDB vérifiée'}));}catch(e){toast('Copie non appliquée : '+e.message);}}
$('#importFg').onchange=async e=>{const files=[...e.target.files];e.target.value='';await readImportSelection('portable',files);};
$('#importSg').onchange=async e=>{const files=[...e.target.files];e.target.value='';await readImportSelection('seriesguide',files);};
