/* IndexedDB recovery snapshots. The main library remains synchronously guarded by localStorage;
   this module provides a larger best-effort recovery lane for quota/corruption incidents. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.FGDurable=api;})(typeof globalThis==='object'?globalThis:this,function(){
'use strict';
const DB='franchiseguide-recovery-v1',STORE='snapshots',MAX=5;
function available(){try{return typeof indexedDB!=='undefined'&&indexedDB!==null;}catch{return false;}}
function open(){return new Promise((resolve,reject)=>{
 if(!available())return reject(Error('IndexedDB indisponible.'));
 let r,failed=false;const fail=error=>{failed=true;reject(error||Error('IndexedDB inaccessible.'));};
 try{r=indexedDB.open(DB,1);}catch(error){fail(error);return;}
 r.onupgradeneeded=()=>{try{const db=r.result;if(!db.objectStoreNames.contains(STORE)){const s=db.createObjectStore(STORE,{keyPath:'id'});s.createIndex('at','at');}}catch(error){try{r.transaction?.abort();}catch{}fail(error);}};
 r.onsuccess=()=>{const db=r.result;if(failed){db.close();return;}db.onversionchange=()=>db.close();resolve(db);};
 r.onerror=()=>fail(r.error);r.onblocked=()=>fail(Error('Une autre fenêtre bloque l’ouverture des copies IndexedDB. Ferme-la puis réessaie.'));
 });}
const sequence=row=>Number.isSafeInteger(row?.sequence)&&row.sequence>0?row.sequence:0;
const newest=rows=>rows.sort((a,b)=>sequence(b)-sequence(a)||(Date.parse(b.at)||0)-(Date.parse(a.at)||0)||String(b.id).localeCompare(String(a.id)));
async function transaction(mode,work){
 const db=await open();return new Promise((resolve,reject)=>{
  let tx,result,settled=false;
  const finish=(error)=>{if(settled)return;settled=true;db.close();if(error)reject(error);else resolve(result);};
  const fail=error=>{try{tx?.abort();}catch{}finish(error||Error('Opération IndexedDB annulée.'));};
  try{
   tx=db.transaction(STORE,mode);
   tx.oncomplete=()=>finish();tx.onerror=()=>finish(tx.error||Error('Écriture IndexedDB refusée.'));tx.onabort=()=>finish(tx.error||Error('Opération IndexedDB annulée.'));
   work(tx.objectStore(STORE),value=>{result=value;},fail);
  }catch(error){fail(error);}
 });
}
// Keep same-window mutations in request order, including clear after a pending recovery write.
// IndexedDB serializes each read/write transaction with transactions from other windows.
let writes=Promise.resolve();
function write(work){const result=writes.then(()=>transaction('readwrite',work));writes=result.catch(()=>{});return result;}
async function list(){await writes;return transaction('readonly',(store,result,fail)=>{const request=store.getAll();request.onsuccess=()=>{try{result(newest(request.result||[]));}catch(error){fail(error);}};});}
async function put(state,reason='Récupération'){
 const row={id:`${Date.now()}-${Math.random().toString(36).slice(2)}`,at:new Date().toISOString(),reason,state:JSON.parse(JSON.stringify(state))};
 return write((store,result,fail)=>{
  const request=store.getAll();request.onsuccess=()=>{try{
   const rows=request.result||[];row.sequence=rows.reduce((max,item)=>Math.max(max,sequence(item)),0)+1;
   // Sequence is assigned inside the transaction, so same-millisecond writes and clock
   // changes cannot prune the just-saved recovery copy or misidentify the latest copy.
   row.id+=`-${row.sequence}`;store.put(row);
   for(const old of newest([row,...rows]).slice(MAX))store.delete(old.id);
   result(row);
  }catch(error){fail(error);}};
 });
}
async function remove(id){return write(store=>{store.delete(id);});}
async function clear(){return write(store=>{store.clear();});}
async function latest(){return (await list())[0]||null;}
return {available,list,put,remove,clear,latest,DB};
});
