/* V0.23: on-demand streaming availability. No watchlist, viewing, sync or preference writes. */
'use strict';
let availabilityScreen=null;
const availabilityCache=new Map();
let availabilityCooldown=0,availabilityRequestSerial=0;
const AV_TTL=5*60000,AV_MAX_CACHE=20;
function availabilityItem(){if(!availabilityScreen)return null;const source=availabilityScreen.source==='suggestions'?state.suggestions:state.media;return source.find(m=>m.id===availabilityScreen.id);}
function availabilityCurrent(){const s=availabilityScreen,m=availabilityItem();return !!s&&!!m&&s.epoch===libraryEpoch&&(s.source==='suggestions'||m.added!==false)&&['show','movie'].includes(m.kind)&&m.kind===s.kind&&m.tmdbId===s.tmdbId;}
function availabilityAbort(reason='manual'){
 const s=availabilityScreen;if(!s)return;
 s.serial=++availabilityRequestSerial;s.controller?.abort(reason);s.controller=null;s.loading=false;s.regionSerial++;s.regionController?.abort(reason);s.regionController=null;s.regionsLoading=false;
 if(reason==='manual')s.message='Lecture annulée. Le dernier relevé complet de cette sélection reste consultable.';
}
function availabilityForget(){availabilityAbort('replaced');availabilityCache.clear();availabilityScreen=null;}
function openAvailability(id,season=null){
 const m=state.media.find(x=>x.id===id)||state.suggestions.find(x=>x.id===id);if(!m)return toast('Fiche introuvable.');
 let q;try{q=FGAvailability.request(m,state.settings.region||'FR',season);if(season!=null&&!FGAvailability.seasons(m,state.episodes).includes(season))throw Error('Saison absente des références chargées. Actualise la fiche avant de la consulter.');}catch(e){return toast(e.message);}
 availabilityAbort('replaced');
 const source=state.media.includes(m)?'media':'suggestions',parent=overlay&&overlay.type!=='availability'?{...overlay}:overlay?.returnTo||null;
 availabilityScreen={id,source,kind:m.kind,tmdbId:m.tmdbId,country:q.country,season:q.season,epoch:libraryEpoch,serial:++availabilityRequestSerial,
  mode:'all',query:'',limit:60,regions:[],regionsLoading:false,regionsError:null,regionSerial:0,loading:false,error:null,message:'Choisis le pays et la portée, puis vérifie les disponibilités.',controller:null,apiKey:serviceCredential('tmdb')||''};
 overlay={type:'availability',id,title:'Où regarder · '+m.title,returnTo:parent||{type:'item',id,src:source,title:m.title}};render();
}
function availabilityQuery(){const s=availabilityScreen;if(!availabilityCurrent())throw Error('La bibliothèque ou la fiche a changé. Rouvre « Où regarder ».');return FGAvailability.request(availabilityItem(),s.country,s.season);}
function availabilityCached(q){const hit=availabilityCache.get(q.key);if(!hit)return null;
 if(hit.apiKey!==(serviceCredential('tmdb')||'')||hit.epoch!==libraryEpoch){availabilityCache.delete(q.key);return null;}
 return hit.snapshot;
}
function rememberAvailability(snapshot,apiKey,epoch){availabilityCache.delete(snapshot.query.key);availabilityCache.set(snapshot.query.key,{snapshot,apiKey,epoch});while(availabilityCache.size>AV_MAX_CACHE)availabilityCache.delete(availabilityCache.keys().next().value);}
function availabilityError(e){let message=e?.message||'Lecture indisponible.';for(const key of [serviceCredential('tmdb'),availabilityScreen?.apiKey])if(key)message=message.split(key).join('[clé masquée]');return FGCore.redact(message).slice(0,400);}
async function loadAvailabilityCountries(){
 const s=availabilityScreen;if(!availabilityCurrent()||s.regionsLoading)return;
 const key=serviceCredential('tmdb')?.trim();if(!key){s.regionsError='Le service TMDb avancé doit être activé par l’éditeur.';renderAvailability();return;}
 if(Date.now()<availabilityCooldown){s.regionsError='Délai TMDb en cours ; réessaie après '+humanDate(availabilityCooldown)+'.';renderAvailability();return;}
 const token=++s.regionSerial,controller=new AbortController();s.regionController=controller;s.regionsLoading=true;s.regionsError=null;renderAvailability();
 try{
  const raw=await fetchJson('https://api.themoviedb.org/3/watch/providers/regions?'+new URLSearchParams({api_key:key,language:state.settings.language||'fr-FR'}),{signal:controller.signal,cacheMs:0,force:true,retries:0,maxBytes:1024*1024,timeoutMs:20000});
  if(availabilityScreen!==s||!availabilityCurrent()||token!==s.regionSerial||key!==(serviceCredential('tmdb')||'').trim())return;
  s.regions=FGAvailability.regions(raw);
 }catch(e){if(availabilityScreen===s&&availabilityCurrent()&&token===s.regionSerial&&key===(serviceCredential('tmdb')||'').trim()){s.regionsError=availabilityError(e);if(e.status===429)availabilityCooldown=Date.now()+Math.max(60000,Math.min(86400000,FGNetwork.retryDelay(e.retryAfter)));}}
 finally{if(availabilityScreen===s&&token===s.regionSerial){s.regionsLoading=false;s.regionController=null;if(overlay?.type==='availability')renderAvailability();}}
}
async function readAvailability(force=false){
 const s=availabilityScreen;let q;try{q=availabilityQuery();}catch(e){toast(e.message);return;}
 if(s.loading)return;const key=serviceCredential('tmdb')?.trim();
 if(!key){s.error='Le service TMDb avancé doit être activé par l’éditeur. Aucune disponibilité n’est inventée.';renderAvailability();return;}
 const hit=availabilityCached(q);if(!force&&hit&&Date.now()-hit.observedAt>=0&&Date.now()-hit.observedAt<AV_TTL){s.message='Dernier relevé de cette session réutilisé ; sa date de consultation reste inchangée.';s.error=null;renderAvailability();return;}
 if(Date.now()<availabilityCooldown){s.error='Délai TMDb en cours jusqu’au '+humanDate(availabilityCooldown)+'. Une actualisation manuelle ne le contourne pas.';renderAvailability();return;}
 const controller=new AbortController(),serial=++availabilityRequestSerial;s.serial=serial;s.controller=controller;s.loading=true;s.error=null;s.apiKey=key;s.message='Vérification auprès de TMDb…';renderAvailability();
 const valid=()=>availabilityScreen===s&&s.serial===serial&&availabilityCurrent()&&key===(serviceCredential('tmdb')||'').trim()&&q.key===availabilityQuery().key&&!controller.signal.aborted&&overlay?.type==='availability';
 try{
  const raw=await fetchJson('https://api.themoviedb.org/3'+q.path+'?'+new URLSearchParams({api_key:key}),{signal:controller.signal,cacheMs:0,force:true,retries:0,maxBytes:2*1024*1024,timeoutMs:20000});
  if(!valid())return;const snapshot=FGAvailability.parse(raw,q,Date.now());rememberAvailability(snapshot,key,libraryEpoch);s.message='Relevé consulté. Les offres peuvent évoluer ; confirme leurs conditions auprès du service.';
 }catch(e){if(availabilityScreen===s&&s.serial===serial&&availabilityCurrent()&&key===(serviceCredential('tmdb')||'').trim()){
   s.error=availabilityError(e);s.message='Dernier relevé valide conservé, s’il existe pour cette sélection.';
   if(e.status===429)availabilityCooldown=Date.now()+Math.max(60000,Math.min(86400000,FGNetwork.retryDelay(e.retryAfter)));
 }}finally{if(availabilityScreen===s&&s.serial===serial){s.loading=false;s.controller=null;if(overlay?.type==='availability')renderAvailability();}}
}
function renderAvailability(){
 const s=availabilityScreen;
 if(!availabilityCurrent()){$('#content').innerHTML='<section class="hero"><h2>Où regarder</h2><p class="notice warn">La bibliothèque ou la fiche a changé. Rouvre cet écran depuis une fiche actuelle.</p><button class="tonal" id="availabilityCloseStale">Retour</button></section>';$('#availabilityCloseStale').onclick=()=>{availabilityForget();overlay=null;render();};return;}
 const m=availabilityItem();let q,error=null;try{q=availabilityQuery();}catch(e){error=e.message;}
 const snapshot=q?availabilityCached(q):null,scope=s.season===null?(m.kind==='show'?'Série entière':'Film'):s.season===0?'Spéciaux':'Saison '+s.season;
 const cachedRegions=new Map(s.regions.map(c=>[c.code,c.label]));if(!cachedRegions.has(s.country))cachedRegions.set(s.country,s.country+' · code choisi');
 const label=FGAvailability.stateLabel(snapshot),filtered=snapshot?FGAvailability.filter(snapshot,{mode:s.mode,query:s.query}):[],count=filtered.reduce((n,g)=>n+g.offers.length,0);
 let remaining=s.limit;
 const groups=filtered.map(g=>{const rows=g.offers.slice(0,remaining);remaining-=rows.length;return rows.length?`<section class="availability-group"><h3>${esc(g.label)} <span class="meta">${g.offers.length}</span></h3><div class="provider-grid">${rows.map(p=>`<article class="provider-card">${p.logoUrl?`<img src="${esc(p.logoUrl)}" alt="" loading="lazy" width="56" height="56">`:'<span class="provider-placeholder" aria-hidden="true">▤</span>'}<div><strong>${esc(p.name)}</strong><span class="meta">${esc(g.label)} signalé${g.mode==='free'?' · conditions à confirmer':''}</span></div></article>`).join('')}</div></section>`:'';}).join('');
 $('#content').innerHTML=`<section class="hero"><h2>Où regarder · ${esc(m.title)}</h2><p>Disponibilités <strong>JustWatch via TMDb</strong>, vérifiées à ta demande. Aucune lecture vidéo, aucun prix ni abonnement personnel supposé.</p><button id="availabilityBack" class="tonal">${overlay?.returnTo?.type==='next-up'?'Retour à « À reprendre »':'Retour à la fiche'}</button></section><section class="panel availability-controls"><div class="filter-row"><label>Pays<select id="availabilityCountry">${[...cachedRegions].sort((a,b)=>a[0].localeCompare(b[0])).map(([k,v])=>`<option value="${esc(k)}" ${k===s.country?'selected':''}>${esc(v)} (${esc(k)})</option>`).join('')}</select></label><button id="availabilityCountries" class="tonal" ${s.regionsLoading?'disabled':''}>${s.regionsLoading?'Chargement des pays…':'Charger les pays disponibles'}</button>${m.kind==='show'?`<label>Portée<select id="availabilitySeason"><option value="all" ${s.season===null?'selected':''}>Série entière</option>${FGAvailability.seasons(m,state.episodes).map(n=>`<option value="${n}" ${n===s.season?'selected':''}>${n===0?'Spéciaux':'Saison '+n}</option>`).join('')}</select></label>`:''}<button id="availabilityRead" class="primary" ${s.loading||!q?'disabled':''}>${s.loading?'Lecture en cours…':'Vérifier les disponibilités'}</button><button id="availabilityRefresh" class="tonal" ${s.loading||!q?'disabled':''}>Actualiser ce relevé</button><button id="availabilityCancel" class="tonal" ${s.loading||s.regionsLoading?'':'disabled'}>Annuler</button></div><p class="meta">Le pays et la saison ne modifient ni les réglages généraux ni la bibliothèque. Une offre pour la série entière ne garantit pas la même offre pour toutes ses saisons. Relève les conditions, le prix et l’accès sur le site officiel indiqué par TMDb.</p>${s.regionsError?`<p class="notice warn" role="alert">${esc(s.regionsError)}</p>`:''}<p role="status" id="availabilityStatus">${esc(s.message)}</p>${s.error||error?`<p id="availabilityError" class="notice warn" role="alert">${esc(s.error||error)}</p>`:''}</section><section class="panel availability-result"><div class="section-title"><h3>${esc(scope)} · ${esc(s.country)}</h3>${snapshot?`<span class="meta">Consulté le ${esc(new Date(snapshot.observedAt).toLocaleString('fr-FR'))}</span>`:''}</div>${snapshot?`<p class="meta">${snapshot.totalProviders} service(s), ${snapshot.totalOffers} offre(s) signalée(s) par type d’accès. Consultation conservée en mémoire seulement.${Date.now()-snapshot.observedAt>=AV_TTL?' Ce relevé a plus de cinq minutes : vérifie-le de nouveau avant de décider.':''}</p>`:''}${label==='not-read'?'<p class="notice">Aucun relevé pour cette sélection. Lance une vérification.</p>':label==='country-not-reported'?'<p class="notice">TMDb ne rapporte pas de données pour ce pays dans cette réponse. Cela ne prouve pas que le titre est indisponible.</p>':label==='no-offers-reported'?'<p class="notice">Aucune offre n’est renseignée pour ce pays dans cette réponse. Cela ne prouve pas une indisponibilité générale.</p>':''}${snapshot?.ignoredLinks?'<p class="notice warn">Un ou plusieurs liens / logos non fiables ont été écartés.</p>':''}${snapshot?.link?`<a id="availabilitySource" class="tonal linkbtn" href="${esc(snapshot.link)}" target="_blank" rel="noopener noreferrer">Voir les offres chez TMDb — ouvre un site externe</a>`:snapshot?'<p class="meta">Aucun lien de disponibilité TMDb fiable n’a été fourni. Aucun lien de lecture n’est reconstruit.</p>':''}</section>${snapshot?.totalOffers?`<section class="availability-offer-controls filter-panel"><label>Type d’accès<select id="availabilityMode"><option value="all">Tous</option>${FGAvailability.MODES.map(([k,v])=>`<option value="${k}" ${s.mode===k?'selected':''}>${v}</option>`).join('')}</select></label><label>Service<input id="availabilitySearch" type="search" maxlength="250" value="${esc(s.query)}"></label><span>${count} offre(s) correspondant au filtre</span></section><div id="availabilityGroups">${groups||'<p class="notice">Aucune offre parmi celles reçues ne correspond au filtre.</p>'}</div>${count>s.limit?'<button id="availabilityMore" class="tonal load-more">Afficher 60 offres de plus</button>':''}`:''}<p class="meta availability-attribution">Données de disponibilité : JustWatch via TMDb. Aucun partenariat ou abonnement n’est déduit. Logos fournis par TMDb.</p>`;
 $('#availabilityBack').onclick=()=>{const target=overlay?.returnTo||{type:'item',id:m.id,src:s.source,title:m.title};availabilityAbort('leave');overlay=target;render();};
 $('#availabilityCountries').onclick=loadAvailabilityCountries;
 const change=()=>{availabilityAbort('selection');s.mode='all';s.query='';s.limit=60;s.error=null;s.message='Sélection modifiée. Vérifie ses disponibilités.';renderAvailability();};
 $('#availabilityCountry').onchange=e=>{s.country=e.target.value;change();};$('#availabilitySeason')?.addEventListener('change',e=>{s.season=e.target.value==='all'?null:Number(e.target.value);change();});
 $('#availabilityRead').onclick=()=>readAvailability(false);$('#availabilityRefresh').onclick=()=>readAvailability(true);$('#availabilityCancel').onclick=()=>{availabilityAbort();renderAvailability();};
 $('#availabilityMode')?.addEventListener('change',e=>{s.mode=e.target.value;s.limit=60;renderAvailability();});
 $('#availabilitySearch')?.addEventListener('input',e=>{s.query=e.target.value;s.limit=60;const pos=e.target.selectionStart;renderAvailability();const el=$('#availabilitySearch');el.focus();try{el.setSelectionRange(pos,pos);}catch{}});
 $('#availabilityMore')?.addEventListener('click',()=>{s.limit+=60;renderAvailability();});bindImages($('#content'));
}
const availabilityDetailBase=renderMediaDetail;renderMediaDetail=function(o){availabilityDetailBase(o);const source=o.src==='suggestions'?state.suggestions:state.media,m=source.find(x=>x.id===o.id);if(!m||!['show','movie'].includes(m.kind))return;
 const host=$('.detail-actions');if(!host)return;
 if(m.tmdbId){host.insertAdjacentHTML('beforeend','<button id="whereToWatch" class="tonal">Où regarder · pays et saisons</button>');$('#whereToWatch').onclick=()=>openAvailability(m.id);
 for(const el of $$('.season')){const n=Number(el.dataset.number);if(!Number.isInteger(n)||n<0)continue;const tools=el.querySelector('.season-tools');if(tools){const b=document.createElement('button');b.type='button';b.className='tonal';b.dataset.availabilitySeason=String(n);b.textContent='Où regarder cette saison';b.onclick=()=>openAvailability(m.id,n);tools.append(b);}}
 }else{const note=document.createElement('p');note.className='meta';note.textContent='Les disponibilités nécessitent un identifiant TMDb confirmé. Aucun rapprochement n’est fait au seul titre.';host.after(note);}
};
const availabilityOverlayBase=renderOverlay;renderOverlay=function(o){if(o.type==='availability')return renderAvailability();return availabilityOverlayBase(o);};
// A navigation change also cancels pending requests, including country discovery.
const availabilityRenderBase=render;render=function(){if(availabilityScreen&&overlay?.type!=='availability')availabilityAbort('leave');return availabilityRenderBase();};
const availabilityNextBase=renderNextUp;renderNextUp=function(){availabilityNextBase();
 for(const row of $$('.queue-row')){const id=row.querySelector('[data-next-open]')?.dataset.nextOpen,m=state.media.find(x=>x.id===id);if(!m||!['show','movie'].includes(m.kind)||!m.tmdbId)continue;
  const button=document.createElement('button');button.className='tonal';button.type='button';button.dataset.nextAvailability=m.id;button.textContent='Où regarder';button.onclick=()=>openAvailability(m.id);row.querySelector('.queue-actions')?.append(button);
 }
};
