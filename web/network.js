/* Bounded public-catalogue requests. No persistent API cache or POST retry. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.FGNetwork=api;})(globalThis,function(){
  'use strict';
  function abortError(message='Requête annulée.'){const e=new Error(message);e.name='AbortError';return e;}
  function retryDelay(value,now=Date.now()) {
    if(!value)return 500;
    const seconds=Number(value),milliseconds=Number.isFinite(seconds)?seconds*1000:Date.parse(value)-now;
    return Number.isFinite(milliseconds)?Math.max(0,milliseconds):500;
  }
  async function mapLimit(items,limit,fn){
    const out=new Array(items.length);let cursor=0,failed=false;
    async function worker(){while(!failed){const i=cursor++;if(i>=items.length)return;try{out[i]=await fn(items[i],i);}catch(e){failed=true;throw e;}}}
    await Promise.all(Array.from({length:Math.min(Math.max(1,limit),items.length)},worker));return out;
  }
  function create(fetcher=(...args)=>fetch(...args),options={}){
    const pending=new Map(),cache=new Map(),waiting=[];
    let running=0,generation=0;const max=options.concurrency||4,capacity=options.capacity||80;
    const sleep=options.sleep||((ms)=>new Promise(r=>setTimeout(r,ms)));
    const metrics={requests:0,cacheHits:0,shared:0,retries:0};
    async function result(url,opts={}){
      const {timeoutMs=25000,maxBytes=null,signal,...request}=opts,controller=new AbortController();let timedOut=false;
      const cancel=()=>controller.abort();if(signal?.aborted)throw abortError();signal?.addEventListener('abort',cancel,{once:true});
      const timer=setTimeout(()=>{timedOut=true;controller.abort();},timeoutMs);
      try{
        metrics.requests++;const r=await fetcher(url,{...request,signal:controller.signal});
        let body;
        if(maxBytes==null)body=await r.text();
        else {
          if(!Number.isSafeInteger(maxBytes)||maxBytes<1||maxBytes>64*1024*1024)throw Object.assign(Error('Limite de réponse invalide.'),{code:'BODY_LIMIT'});
          const declared=r.headers?.get('Content-Length');if(declared&&/^\d+$/.test(declared)&&Number(declared)>maxBytes){await r.body?.cancel?.().catch(()=>{});throw Object.assign(Error('Réponse catalogue trop volumineuse.'),{code:'BODY_LIMIT'});}
          if(r.body?.getReader){
            const reader=r.body.getReader(),decoder=new TextDecoder('utf-8',{fatal:true});let size=0;const parts=[];
            try {for(;;){if(controller.signal.aborted)throw abortError();const part=await reader.read();if(part.done)break;size+=part.value.byteLength;if(size>maxBytes)throw Object.assign(Error('Réponse catalogue trop volumineuse.'),{code:'BODY_LIMIT'});parts.push(decoder.decode(part.value,{stream:true}));}parts.push(decoder.decode());body=parts.join('');}
            catch(e){await reader.cancel().catch(()=>{});throw e;}finally{reader.releaseLock();}
          }else{body=await r.text();if(new TextEncoder().encode(body).length>maxBytes)throw Object.assign(Error('Réponse catalogue trop volumineuse.'),{code:'BODY_LIMIT'});}
        }
        if(controller.signal.aborted)throw abortError();
        let json=null;try{json=body?JSON.parse(body):null;}catch{}
        return {ok:r.ok,status:r.status,json,body,headers:r.headers};
      }catch(e){
        if(e.code==='BODY_LIMIT')throw e;
        if(controller.signal.aborted)throw abortError(timedOut?'Délai réseau dépassé. Réessaie.':'Requête annulée.');
        const error=new Error('Connexion indisponible. Vérifie le réseau et les autorisations du navigateur.');error.cause=e;throw error;
      }finally{clearTimeout(timer);signal?.removeEventListener('abort',cancel);}
    }
    function schedule(fn,signal){return new Promise((resolve,reject)=>{
      if(signal?.aborted){reject(abortError());return;}
      const job={fn,resolve,reject,cleanup:()=>signal?.removeEventListener('abort',cancel)};
      function cancel(){const i=waiting.indexOf(job);if(i>=0){waiting.splice(i,1);job.cleanup();reject(abortError());}}
      signal?.addEventListener('abort',cancel,{once:true});waiting.push(job);pump();
    });}
    function pump(){while(running<max&&waiting.length){const job=waiting.shift();job.cleanup();running++;Promise.resolve().then(job.fn).then(job.resolve,job.reject).finally(()=>{running--;pump();});}}
    function sleepAbortable(ms,signal){return new Promise((resolve,reject)=>{
      if(signal?.aborted){reject(abortError());return;}
      let finished=false;const cancel=()=>finish(false,abortError());
      function finish(ok,value){if(finished)return;finished=true;signal?.removeEventListener('abort',cancel);ok?resolve(value):reject(value);}
      signal?.addEventListener('abort',cancel,{once:true});Promise.resolve(sleep(ms)).then(v=>finish(true,v),e=>finish(false,e));
    });}
    async function json(url,opts={}){
      const {cacheMs=90000,force=false,retries=2,...request}=opts;
      const method=(request.method||'GET').toUpperCase(),headers=new Headers(request.headers||{});
      const cacheable=method==='GET'&&!headers.has('Authorization')&&!request.signal&&cacheMs>0&&!['no-store','reload','no-cache'].includes(request.cache);
      const key=JSON.stringify([String(url),[...headers],request.maxBytes??null,request.timeoutMs??25000,request.credentials??'same-origin',request.mode??'cors',request.redirect??'follow',request.cache??'default',retries]),epoch=generation;
      const copy=v=>JSON.parse(JSON.stringify(v));
      if(cacheable&&!force){const hit=cache.get(key);if(hit&&hit.until>Date.now()){metrics.cacheHits++;cache.delete(key);cache.set(key,hit);return copy(hit.value);}if(pending.has(key)){metrics.shared++;return copy(await pending.get(key));}}
      const task=schedule(async()=>{
        if(request.signal?.aborted)throw abortError();
        for(let attempt=0;;attempt++){
          const r=await result(url,request);
          if(method==='GET'&&[429,502,503,504].includes(r.status)&&attempt<retries){
            const wait=retryDelay(r.headers?.get('Retry-After'));
            // Long server cooldowns are surfaced instead of holding a screen indefinitely.
            if(wait<=30000){metrics.retries++;await sleepAbortable(Math.max(wait,attempt*400),request.signal);if(request.signal?.aborted)throw abortError();continue;}
          }
          if(!r.ok){const e=new Error(r.status===401?'Identifiants API invalides (401).':r.status===429?'Quota du catalogue atteint. Réessaie après le délai indiqué par le fournisseur.':`Catalogue HTTP ${r.status}`);e.status=r.status;e.retryAfter=r.headers?.get('Retry-After');throw e;}
          if(r.json===null)throw Error('Réponse catalogue JSON invalide.');
          if(cacheable&&cacheMs>0&&epoch===generation&&r.body.length<=500000){cache.set(key,{until:Date.now()+cacheMs,value:r.json});while(cache.size>capacity)cache.delete(cache.keys().next().value);}
          return r.json;
        }
      },request.signal);
      if(cacheable)pending.set(key,task);
      try{return copy(await task);}finally{if(pending.get(key)===task)pending.delete(key);}
    }
    return {result,json,mapLimit,metrics,clear(){generation++;cache.clear();pending.clear();},size(){return cache.size;}};
  }
  return {create,mapLimit,retryDelay};
});
