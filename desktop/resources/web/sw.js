/* Versioned application shell. No OAuth, public API JSON or credential storage in Cache API. */
const CACHE='franchiseguide-web-0.36.0',IMAGES='franchiseguide-images-v1';
const ASSETS=['./','index.html','app.css','gaming-core.js','steam-library.js','steam-connect.js','retro-core.js','retro-connect.js','durable.js','integrity.js','sync-policy.js','sync-center.js','gaming.js','core.js','protected-backup-core.js','protected-backup-ui.js','reliability-core.js','discovery-core.js','discovery-ui.js','progress-core.js','insights-core.js','insights-ui.js','catalog-core.js','catalog-ui.js','activity-core.js','activity-ui.js','library-core.js','library-ui.js','lists-core.js','lists-ui.js','episodes-core.js','episodes-ui.js','availability-core.js','availability-ui.js','episode-detail-core.js','episode-detail-ui.js','franchise-core.js','franchise-scan-core.js','franchise-ui.js','import-core.js','import-ui.js','network.js','autosetup-core.js','book-editions-core.js','book-selection-core.js','book-selection-ui.js','book-editions-ui.js','tvmaze-detail-core.js','tvmaze-detail-ui.js','services-runtime.js','autosetup-ui.js','app.js','integrated.js','trakt.js','backup.js','persistence.js','people.js','views.js','maintenance.js','reviews.js','release.js','settings-core.js','settings-ui.js','trakt-checkin-core.js','trakt-checkin-ui.js','backup-safety-core.js','backup-safety-ui.js','today-core.js','today-ui.js','startup.js','manifest.webmanifest','icon-192.png','icon-512.png','detail_signature.wav'];
const ALLOWED=new Set(ASSETS.map(a=>new URL(a,self.registration.scope).pathname));
self.addEventListener('install',e=>e.waitUntil(caches.open(CACHE).then(c=>c.addAll(ASSETS))));
// A running version is not silently replaced. The UI offers save + reload when the next shell is ready.
self.addEventListener('message',e=>{if(e.data?.type==='ACTIVATE_UPDATE')self.skipWaiting();});
self.addEventListener('activate',e=>e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k.startsWith('franchiseguide-web-')&&k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',e=>{
  if(e.request.method!=='GET')return;const u=new URL(e.request.url);
  if(u.pathname.startsWith('/api/'))return; // Private account responses never enter the shell cache.
  if(e.request.destination==='image'&&['image.tmdb.org','media.rawg.io','covers.openlibrary.org','cdn.akamai.steamstatic.com','cdn.cloudflare.steamstatic.com','steamcdn-a.akamaihd.net','shared.akamai.steamstatic.com','media.retroachievements.org','retroachievements.org'].includes(u.hostname)){
    e.respondWith(caches.open(IMAGES).then(async c=>{
      const found=await c.match(e.request);if(found)return found;
      const r=await fetch(e.request);if(r.ok||r.type==='opaque'){
        try{await c.put(e.request,r.clone());const keys=await c.keys();for(const k of keys.slice(0,Math.max(0,keys.length-100)))await c.delete(k);}catch{}
      }return r;
    }));return;
  }
  if(u.origin!==self.location.origin)return;
  if(ALLOWED.has(u.pathname)){
    e.respondWith(caches.open(CACHE).then(async c=>{
      const key=new URL(u.pathname,self.location.origin).href;
      const hit=await c.match(key);if(hit)return hit;
      try{const r=await fetch(e.request);if(r.ok)await c.put(key,r.clone());return r;}catch{return new Response('',{status:503,statusText:'Offline'});}
    }));return;
  }
  if(e.request.mode==='navigate')e.respondWith(fetch(e.request).catch(async()=>
    (await caches.open(CACHE)).match(new URL('index.html',self.registration.scope).href)));
});
