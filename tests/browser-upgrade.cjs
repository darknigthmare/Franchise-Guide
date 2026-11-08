'use strict';
// A real waiting worker and real controlled tabs, using a disposable copy of the shipped shell.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os'),{once}=require('node:events');
const {chromium}=require('playwright');
for(const key of Object.keys(process.env))if(key.startsWith('FG_'))delete process.env[key];
const {start}=require('../server/start.cjs');
const out=path.join(__dirname,'results/current/browser-upgrade');fs.mkdirSync(out,{recursive:true});
const report={version:require('../package.json').version,checks:[],errors:[],realProviderAccounts:false,scope:'Actual Chromium worker update; two candidate caches of the same application schema.'};
function check(name,ok){assert.ok(ok,name);report.checks.push(name);console.log('PASS '+name);}
(async()=>{
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'fg-pwa-'));fs.cpSync(path.join(__dirname,'../web'),root,{recursive:true});
 const server=start({port:0,root});let browser;
 try{
  await once(server,'listening');const origin='http://127.0.0.1:'+server.address().port;
  browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_EXECUTABLE?{executablePath:process.env.CHROMIUM_EXECUTABLE}:{})});
  const context=await browser.newContext({locale:'fr-FR'});context.setDefaultTimeout(20000);
  await context.route('**/*',route=>new URL(route.request().url()).origin===origin?route.continue():route.abort('blockedbyclient'));
  const writer=await context.newPage();writer.on('pageerror',e=>report.errors.push(e.message));
  await writer.goto(origin,{waitUntil:'networkidle'});await writer.waitForFunction(()=>storageWritable&&storageRevision>0&&navigator.serviceWorker.controller);
  await writer.evaluate(()=>{state.settings.dark=true;save(false);window.originalWorker=navigator.serviceWorker.controller;});
  const before=await writer.evaluate(()=>localStorage.getItem(KEY));
  const reader=await context.newPage();reader.on('pageerror',e=>report.errors.push(e.message));
  await reader.goto(origin,{waitUntil:'networkidle'});await reader.waitForFunction(()=>!storageWritable&&navigator.serviceWorker.controller);
  check('Two real tabs share the original active worker, second is read-only',await reader.evaluate(()=>!storageWritable));
  const swPath=path.join(root,'sw.js'),original=fs.readFileSync(swPath,'utf8');
  fs.writeFileSync(swPath,original.replace(/const CACHE='([^']+)'/,"const CACHE='$1-upgrade-candidate'"));
  await writer.evaluate(async()=>{const reg=await navigator.serviceWorker.getRegistration();await reg.update();});
  await writer.waitForFunction(async()=>!!(await navigator.serviceWorker.getRegistration())?.waiting);
  await writer.locator('[data-pwa-update]').waitFor();
  await writer.locator('[data-pwa-update]').click();
  await writer.waitForFunction(()=>document.body.innerText.includes('Aucune mise à jour forcée'));
  check('Waiting update refuses activation while another tab remains open',await writer.evaluate(async()=>!!(await navigator.serviceWorker.getRegistration()).waiting&&navigator.serviceWorker.controller===window.originalWorker));
  check('Refused update preserves library content',await writer.evaluate(previous=>{
    const a=JSON.parse(previous),b=JSON.parse(localStorage.getItem(KEY));delete a._revision;delete b._revision;return JSON.stringify(a)===JSON.stringify(b);
  },before));
  await reader.close();
  await writer.locator('#settings').click();
  await writer.evaluate(()=>{document.querySelector('[data-pwa-update]').click();});
  check('Open form blocks activation after other tabs are closed',await writer.evaluate(async()=>!!document.querySelector('#settingsDialog')?.open&&!!(await navigator.serviceWorker.getRegistration()).waiting&&FGPwaUpdate.blockReason()!==null));
  await writer.locator('#settingsDialog .close').click();
  await writer.locator('[data-pwa-update]').click();
  await writer.waitForFunction(()=>!document.querySelector('#appUpdate')&&storageWritable&&state.settings.dark===true);
  const cachesAfter=await writer.evaluate(()=>caches.keys());
  check('Sole writer activates, reloads and retains saved preference',cachesAfter.some(x=>x.endsWith('-upgrade-candidate'))&&await writer.evaluate(()=>JSON.parse(localStorage.getItem(KEY)).settings.dark===true));
  check('Only one versioned application shell remains',cachesAfter.filter(x=>x.startsWith('franchiseguide-web-')).length===1);
  check('Updated worker serves every runtime module from its own cache',await writer.evaluate(async()=>{
    const name=(await caches.keys()).find(x=>x.startsWith('franchiseguide-web-'));const cache=await caches.open(name);
    for(const script of document.querySelectorAll('script[src]'))if(!await cache.match(script.src))return false;return true;
  }));
  check('No uncaught errors during guarded update',report.errors.length===0);
  await writer.screenshot({path:path.join(out,'updated.png'),fullPage:true});report.passed=true;
 }catch(error){report.passed=false;report.failure=error.stack||String(error);console.error(error);process.exitCode=1;}
 finally{if(browser)await browser.close();server.closeAllConnections();await new Promise(resolve=>server.close(resolve));fs.rmSync(root,{recursive:true,force:true});fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report,null,2)+'\n');}
})();
