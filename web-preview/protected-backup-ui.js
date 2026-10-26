/* Protected export is optional. It never writes a password, key or plaintext snapshot to storage. */
'use strict';
const PROTECTED_NOTICE='Ce mot de passe protège seulement le fichier exporté. La bibliothèque ouverte et les copies locales existantes ne sont pas chiffrées par cette action. Si tu oublies ce mot de passe, ce fichier ne peut pas être récupéré.';
function protectedGuard(){return {baseKey:FGImport.contentKey(state),epoch:libraryEpoch,revision:storageRevision,openedAt:Date.now()};}
function guardProtected(g){FGImport.guard(g,state,libraryEpoch,storageRevision);if(Date.now()-g.openedAt>600000)throw Error('Ce formulaire a expiré. Ferme-le et recommence.');}
function clearProtectedFields(d){d.querySelectorAll('input[type="password"],input[data-secret]').forEach(x=>{x.value='';});}
function protectedError(d,e){const p=d.querySelector('[role="alert"]');p.textContent=e.code==='NO_CRYPTO'?e.message:e.message||'Opération impossible, aucun fichier appliqué.';p.classList.remove('hidden');}
function protectedBusy(d,on,text){d.querySelector('[type="submit"]').disabled=on;d.querySelectorAll('input').forEach(x=>x.disabled=on);d.querySelector('[role="status"]').textContent=text;}
function protectedPasswordForm(confirm){return `<label>Phrase secrète — 12 à 256 caractères<input id="protectedPassword" name="password" type="password" autocomplete="${confirm?'new-password':'off'}" spellcheck="false" autocapitalize="none" autocorrect="off" maxlength="512" required></label>${confirm?'<label>Confirmer la phrase secrète<input id="protectedConfirm" name="confirm" type="password" autocomplete="new-password" spellcheck="false" autocapitalize="none" autocorrect="off" maxlength="512" required></label>':''}<p class="meta">Espaces, accents et emoji sont significatifs. Choisis une phrase longue et unique ; conserve-la séparément du fichier. Rien n’est transmis à un fournisseur.</p>`;}
function downloadProtectedFile(bytes){const url=URL.createObjectURL(new Blob([bytes],{type:'application/octet-stream'}));const a=document.createElement('a');a.href=url;a.download='franchiseguide-protege-'+today()+'.fgbackup';document.body.append(a);try{a.click();}finally{a.remove();setTimeout(()=>URL.revokeObjectURL(url),60000);}}
function openProtectedExport(){
 if(!FGProtected.available())return toast('Web Crypto indisponible. Aucun export en clair n’a été lancé. Utilise HTTPS ou le lanceur local.');
 let g,snapshot;try{g=protectedGuard();snapshot=toPortableBackup(state);FGCore.validateBackup(snapshot);}catch(e){return toast(e.message);}
 const count=FGImport.count(state),controller=new AbortController();let plaintext=null,output=null;
 const d=activityDialog('protectedExportDialog','Exporter une sauvegarde protégée',`<form id="protectedExportForm" novalidate><p><b>${count.titles} titre(s) et ${count.episodes} épisode(s)</b>. Export de la bibliothèque actuellement affichée, y compris ses notes et profils locaux. Les clés de services, sessions et files internes ne font pas partie du portable.</p><p class="notice warn">${PROTECTED_NOTICE}</p>${protectedPasswordForm(true)}<label class="import-ack"><input id="protectedExportAcknowledge" type="checkbox">J’ai compris qu’aucune récupération du mot de passe n’est possible.</label><p class="notice warn hidden" role="alert"></p><p role="status" aria-live="polite"></p><div class="actions"><button id="protectedExportSubmit" type="submit" class="primary">Chiffrer et créer le fichier</button><button id="protectedExportCancel" type="button" class="tonal">Annuler</button></div></form>`);
 d.addEventListener('keydown',e=>{if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='e'){e.preventDefault();e.stopPropagation();}});
 d.addEventListener('close',()=>{controller.abort();clearProtectedFields(d);plaintext?.fill(0);output?.fill(0);snapshot=null;});const cancel=()=>{controller.abort();clearProtectedFields(d);d.close();};d.querySelector('#protectedExportCancel').onclick=cancel;d.querySelector('.close').onclick=cancel;d.addEventListener('cancel',()=>{controller.abort();clearProtectedFields(d);});
 d.querySelector('form').onsubmit=async e=>{e.preventDefault();let pass='';try{
  guardProtected(g);if(!d.querySelector('#protectedExportAcknowledge').checked)throw Error('Confirme avoir compris la perte possible du mot de passe.');
  pass=d.querySelector('#protectedPassword').value;if(pass!==d.querySelector('#protectedConfirm').value)throw Error('Les deux phrases secrètes ne correspondent pas.');
  const validated=FGProtected.passwordBytes(pass);validated.fill(0);
  plaintext=new TextEncoder().encode(JSON.stringify(snapshot));clearProtectedFields(d);protectedBusy(d,true,'Chiffrement local en cours… Annuler n’interrompt pas le calcul natif déjà démarré, mais empêche le fichier de sortir.');
  output=await FGProtected.protect(plaintext,pass,{signal:controller.signal});pass='';
  if(!d.isConnected||!d.open||controller.signal.aborted)return;guardProtected(g);downloadProtectedFile(output);d.close();toast('Fichier protégé préparé. Vérifie sa présence dans les téléchargements et garde sa phrase séparément.');
 }catch(error){if(d.isConnected&&!controller.signal.aborted){clearProtectedFields(d);protectedBusy(d,false,'Aucun nouveau fichier créé.');protectedError(d,error);}}
 finally{pass='';plaintext?.fill(0);plaintext=null;output?.fill(0);output=null;}};d.showModal();d.querySelector('#protectedPassword').focus();
}
function openProtectedImport(file,encrypted,guard){
 try{FGProtected.inspect(encrypted);guardProtected(guard);}catch(e){encrypted.fill(0);toast('Import non appliqué : '+e.message);return;}
 if(!FGProtected.available()){encrypted.fill(0);return toast('Déchiffrement indisponible sur ce navigateur. Aucun import appliqué.');}
 const controller=new AbortController();let plain=null;
 const d=activityDialog('protectedImportDialog','Ouvrir une sauvegarde protégée',`<form id="protectedImportForm" novalidate><p>Fichier : <b>${esc(file.name)}</b> · ${esc(bytesLabel(file.size))}. Le contenu sera vérifié après déchiffrement, puis présenté dans l’aperçu habituel. Aucun remplacement automatique.</p><p class="notice">La phrase secrète n’est pas enregistrée. Elle n’est ni le mot de passe Trakt ni celui de Google Drive. La protection d’un fichier ne prouve pas qui l’a créé.</p>${protectedPasswordForm(false)}<p class="notice warn hidden" role="alert"></p><p role="status" aria-live="polite"></p><div class="actions"><button id="protectedImportSubmit" type="submit" class="primary">Déchiffrer puis examiner l’aperçu</button><button id="protectedImportCancel" type="button" class="tonal">Annuler sans modifier</button></div></form>`);
 d.addEventListener('keydown',e=>{if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='e'){e.preventDefault();e.stopPropagation();}});
 d.addEventListener('close',()=>{controller.abort();clearProtectedFields(d);encrypted.fill(0);plain?.fill(0);});const cancel=()=>{controller.abort();clearProtectedFields(d);d.close();};d.querySelector('#protectedImportCancel').onclick=cancel;d.querySelector('.close').onclick=cancel;d.addEventListener('cancel',()=>{controller.abort();clearProtectedFields(d);});
 d.querySelector('form').onsubmit=async e=>{e.preventDefault();let pass='';try{
  guardProtected(guard);pass=d.querySelector('#protectedPassword').value;const checked=FGProtected.passwordBytes(pass);checked.fill(0);clearProtectedFields(d);
  protectedBusy(d,true,'Déchiffrement local et vérification… La bibliothèque reste inchangée.');
  plain=await FGProtected.unprotect(encrypted,pass,{signal:controller.signal});pass='';if(!d.isConnected||!d.open||controller.signal.aborted)return;
  guardProtected(guard);const text=FGProtected.decode(plain),review=makeImportReview('portable',[{name:file.name,size:file.size}],[text],{sourceReason:'Import de sauvegarde protégée vérifiée'});
  guardProtected(guard);d.close();showImportReview(review);
 }catch(error){if(d.isConnected&&!controller.signal.aborted){clearProtectedFields(d);protectedBusy(d,false,'Import non appliqué.');protectedError(d,error);}}
 finally{pass='';plain?.fill(0);plain=null;}};d.showModal();d.querySelector('#protectedPassword').focus();
}
