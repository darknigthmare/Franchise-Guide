/* Opt-in external snapshots. No destination is inferred, and no external file is deleted. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.FGDirectoryBackup=api;})(globalThis,function(){
'use strict';
const VERSION=1,DAY=86400000,MAX_BYTES=32*1024*1024,DB='franchiseguide-external-target-v1';
function error(code,message){return Object.assign(Error(message),{code});}
function empty(){return {version:VERSION,revision:0,enabled:false,intervalDays:7,handle:null,verifiedAt:null,sha256:null,lastFile:null};}
function validate(value){
 if(!value||typeof value!=='object'||Array.isArray(value)||value.version!==VERSION)throw error('CONFIG','Configuration externe incompatible. Aucun remplacement automatique.');
 const v={...empty(),...value};
 if(typeof v.enabled!=='boolean'||!Number.isSafeInteger(v.revision)||v.revision<0||![1,7,14,30].includes(v.intervalDays))throw error('CONFIG','Options de sauvegarde invalides.');
 if(v.handle!=null&&v.handle.kind!=='directory')throw error('CONFIG','Destination de sauvegarde invalide.');
 if(v.enabled&&!v.handle)throw error('CONFIG','Aucun dossier explicitement choisi.');
 if(v.verifiedAt!==null&&(!Number.isSafeInteger(v.verifiedAt)||v.verifiedAt<0||v.verifiedAt>8640000000000000))throw error('CONFIG','Date de vérification invalide.');
 if(v.sha256!==null&&!/^[a-f0-9]{64}$/.test(v.sha256))throw error('CONFIG','Empreinte de sauvegarde invalide.');
 if(v.lastFile!==null&&!/^franchiseguide-\d{8}T\d{9}Z-[a-f0-9]{16,64}\.json$/.test(v.lastFile))throw error('CONFIG','Nom de sauvegarde invalide.');
 if((v.verifiedAt===null)!==(v.sha256===null)||(v.sha256===null)!==(v.lastFile===null))throw error('CONFIG','Preuve de sauvegarde incomplète.');
 return v;
}
function due(value,now=Date.now()){
 const v=validate(value);if(!Number.isSafeInteger(now)||now<0)throw error('TIME','Horloge invalide.');
 return v.enabled&&(v.verifiedAt===null||now-v.verifiedAt>=v.intervalDays*DAY);
}
function guard(signal){if(signal?.aborted)throw error('ABORTED','Sauvegarde annulée.');}
async function sha256(bytes,crypto){
 if(!crypto?.subtle)throw error('CRYPTO','Vérification cryptographique indisponible.');
 return [...new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))].map(x=>x.toString(16).padStart(2,'0')).join('');
}
async function writeVerified(value,snapshot,{now=Date.now(),crypto=globalThis.crypto,signal,validateSnapshot}={}){
 const config=validate(value);guard(signal);
 if(!Number.isSafeInteger(now)||now<0||now>253402300799999)throw error('TIME','Horloge invalide.');
 if(!config.enabled)throw error('DISABLED','La sauvegarde automatique n’est pas activée.');
 if(typeof validateSnapshot!=='function')throw error('VALIDATION','Validateur de bibliothèque absent.');
 validateSnapshot(snapshot);
 const bytes=new TextEncoder().encode(JSON.stringify(snapshot,null,2));
 if(bytes.length>MAX_BYTES)throw error('SIZE','Export supérieur à 32 Mio. Utilise un export manuel.');
 if(await config.handle.queryPermission({mode:'readwrite'})!=='granted')throw error('PERMISSION','Autorise à nouveau ce dossier par une action explicite. Aucun fichier écrit.');
 guard(signal);
 const digest=await sha256(bytes,crypto),random=new Uint8Array(12);crypto.getRandomValues(random);
 if(!Number.isSafeInteger(now)||now<0||now>253402300799999)throw error('TIME','Horloge invalide.');
 const name='franchiseguide-'+new Date(now).toISOString().replace(/[-:.]/g,'')+'-'+[...random].map(x=>x.toString(16).padStart(2,'0')).join('')+'.json';
 // Never open an existing file for writing, even in the extremely unlikely event of a collision.
 try{await config.handle.getFileHandle(name);throw error('COLLISION','Ce fichier existe déjà. Aucun remplacement effectué.');}
 catch(e){if(e.name!=='NotFoundError')throw e;}
 guard(signal);
 let stream=null,closed=false;
 const file=await config.handle.getFileHandle(name,{create:true});
 try{
  guard(signal);stream=await file.createWritable();guard(signal);
  await stream.write(bytes);guard(signal);await stream.close();closed=true;guard(signal);
  const checkFile=await file.getFile();
  if(checkFile.size!==bytes.length)throw error('VERIFY','Taille du fichier relu différente. Sauvegarde non confirmée.');
  const checkBytes=new Uint8Array(await checkFile.arrayBuffer());guard(signal);
  if(await sha256(checkBytes,crypto)!==digest)throw error('VERIFY','Fichier relu différent. Sauvegarde non confirmée.');
  guard(signal);
  return validate({...config,verifiedAt:now,sha256:digest,lastFile:name});
 }catch(e){
  if(stream&&!closed)try{await stream.abort();}catch{}
  // A provider may have created an empty/partial file. Never call it verified or delete a possibly edited file.
  throw e;
 }finally{bytes.fill(0);}
}
function createStore(indexedDB){
 function open(){return new Promise((resolve,reject)=>{
  if(!indexedDB){reject(error('STORAGE','IndexedDB indisponible.'));return;}
  const request=indexedDB.open(DB,1);let ended=false;
  const timer=setTimeout(()=>{ended=true;reject(error('STORAGE','Stockage de destination bloqué.'));},5000);
  request.onupgradeneeded=()=>{if(!request.result.objectStoreNames.contains('config'))request.result.createObjectStore('config');};
  request.onerror=()=>{clearTimeout(timer);reject(error('STORAGE','Destination non lisible.'));};
  request.onsuccess=()=>{clearTimeout(timer);if(ended){request.result.close();return;}resolve(request.result);};
 });}
 async function transaction(mode,operation){const db=await open();return new Promise((resolve,reject)=>{
  let result,problem;const tx=db.transaction('config',mode),store=tx.objectStore('config');
  const fail=e=>{problem=e;try{tx.abort();}catch{}};
  tx.oncomplete=()=>{db.close();resolve(result);};tx.onabort=tx.onerror=()=>{db.close();reject(problem||error('STORAGE','Destination non enregistrée.'));};
  try{operation(store,x=>{result=x;},fail);}catch(e){fail(e);}
 });}
 return {
  async load(){return transaction('readonly',(store,done,fail)=>{const q=store.get('target');q.onsuccess=()=>{try{done(q.result?validate(q.result):empty());}catch(e){fail(e);}};});},
  async save(config,expectedRevision){const value=validate(config);return transaction('readwrite',(store,done,fail)=>{
   const q=store.get('target');q.onsuccess=()=>{try{
    const current=q.result?validate(q.result):empty();
    if(current.revision!==expectedRevision)throw error('CHANGED','Le dossier ou ses options ont changé. Recommence depuis la configuration actuelle.');
    const next=validate({...value,revision:current.revision+1});store.put(next,'target');done(next);
   }catch(e){fail(e);}};
  });},
  async forget(){return transaction('readwrite',store=>store.clear());}
 };
}
return {VERSION,DAY,MAX_BYTES,DB,empty,validate,due,sha256,writeVerified,createStore};
});
