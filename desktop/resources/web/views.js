/* Large-library views: lazy season bodies, bounded row rendering and dated activity. */
'use strict';
const seriesViews=new Map(),libraryViews=new Map();
let historyView={kind:'all',days:0,limit:100};
function seriesView(m){
  if(!seriesViews.has(m.id)){
    const eps=episodesFor(m.id).filter(e=>state.settings.includeSpecials||e.season!==0);
    const next=eps.filter(e=>!e.watched&&!e.skipped).sort((a,b)=>a.season-b.season||a.num-b.num)[0];
    const first=next?.season??Math.min(...(m.seasons||[]).filter(s=>state.settings.includeSpecials||s.number!==0).map(s=>s.number));
    seriesViews.set(m.id,{open:new Set(Number.isFinite(first)?[first]:[]),limits:new Map(),filter:'all',query:''});
  }return seriesViews.get(m.id);
}
function seasonHtml(m,number){
  const s=seasonStats(m,number),meta=(m.seasons||[]).find(s=>s.number===number),poster=FGCore.safeUrl(meta?.posterUrl||s.episodes.find(e=>e.seasonPosterUrl)?.seasonPosterUrl);
  return `<details class="season" data-number="${esc(number)}" ${seriesView(m).open.has(number)?'open':''}><summary>${poster?`<img src="${esc(poster)}" alt="" loading="lazy">`:''}<div><h3>${number===-1?'Saison à identifier':number===0?'Spéciaux':'Saison '+number}</h3><p>${s.watched}/${s.total} vus · ${s.remaining} restants · ${s.skipped} ignorés · ${s.collected} collectionnés</p><div class="progress"><i style="width:${pct(s.watched+s.skipped,s.total)}%"></i></div></div><span>⌄</span></summary><div class="season-tools"><button class="tonal" data-season="watch" data-s="${esc(number)}">Tout vu</button><button class="tonal" data-season="unwatch" data-s="${esc(number)}">Tout non vu</button><button class="tonal" data-season="collect" data-s="${esc(number)}">Collectionner</button><button class="tonal" data-season="uncollect" data-s="${esc(number)}">Retirer de la collection</button><button class="tonal" data-season="skip" data-s="${esc(number)}">Ignorer</button><button class="tonal" data-season="unskip" data-s="${esc(number)}">Réactiver</button></div><div class="episode-list"></div></details>`;
}
function bindEpisodeActions(m,host=document){
  if(typeof addDatedViewingActions==='function')addDatedViewingActions(m,host);
  host.querySelectorAll('[data-ep]').forEach(b=>b.onclick=()=>{
    const episode=state.episodes.find(e=>e.id===b.dataset.eid);if(!episode)return;
    if(b.dataset.ep==='skip'&&!episode.skipped&&FGEpisodes.hasHistory(episode)){toast('Historique conservé : un épisode déjà vu ne peut pas être ignoré sans effacement explicite.');return;}
    if(b.dataset.ep==='watch'&&episode.watched){try{previewEpisodeAction(m.id,[episode.id],'unwatch');}catch(e){toast(e.message);}return;}
    Object.assign(episode,FGCore.episodeState(episode,b.dataset.ep));m.lastWatchedAt=episodesFor(m.id).reduce((at,e)=>Math.max(at,e.lastWatchedAt||0),0)||null;
    const y=window.scrollY;save();render();window.scrollTo(0,y);
  });
}
function hydrateSeasons(m){
  const view=seriesView(m);
  const filterHost=document.createElement('div');filterHost.className='episode-filters panel';
  filterHost.innerHTML=`<label>Épisodes <select id="episodeFilter"><option value="all">Tous</option><option value="unwatched">À voir</option><option value="watched">Vus</option><option value="skipped">Ignorés</option><option value="collected">Collectionnés</option><option value="aired">Déjà diffusés</option></select></label><label>Titre / numéro <input id="episodeQuery" type="search" value="${esc(view.query)}" placeholder="Filtrer les épisodes"></label><small>Le filtre ne change pas les actions « Toute la saison ».</small>`;
  document.querySelector('.season')?.parentElement?.insertBefore(filterHost,document.querySelector('.season'));
  if(!$('#episodeFilter'))return;
  $('#episodeFilter').value=view.filter;
  function body(el){
    const n=Number(el.dataset.number),host=el.querySelector('.episode-list');if(!el.open){host.replaceChildren();return;}
    const q=view.query.toLocaleLowerCase('fr'),limit=view.limits.get(n)||50;
    const eps=episodesFor(m.id).filter(e=>e.season===n).filter(e=>{
      if(q&&!`${epCode(e)} ${state.settings.hideEpisodeSpoilers&&!e.watched?'':e.title}`.toLocaleLowerCase('fr').includes(q))return false;
      return view.filter==='all'||view.filter==='unwatched'&&!e.watched&&!e.skipped||view.filter==='watched'&&e.watched||view.filter==='skipped'&&e.skipped||view.filter==='collected'&&e.collected||view.filter==='aired'&&e.date&&e.date<=today();
    }).sort((a,b)=>a.num-b.num);
    host.innerHTML=`<p class="meta">${eps.length} épisode(s) pour ce filtre</p>${eps.slice(0,limit).map(e=>episodeHtml(m,e)).join('')}${eps.length>limit?`<button class="tonal more-episodes">Afficher les ${Math.min(50,eps.length-limit)} suivants</button>`:''}`;
    host.querySelector('.more-episodes')?.addEventListener('click',()=>{view.limits.set(n,limit+50);body(el);});bindEpisodeActions(m,host);bindImages(host);
  }
  const elements=$$('.season');elements.forEach(el=>{body(el);el.addEventListener('toggle',()=>{const n=Number(el.dataset.number);if(el.open)view.open.add(n);else view.open.delete(n);body(el);});});
  $('#episodeFilter').onchange=e=>{view.filter=e.target.value;view.limits.clear();elements.forEach(body);};
  $('#episodeQuery').oninput=e=>{view.query=e.target.value;view.limits.clear();elements.forEach(body);};
}
function bindImages(root=document){
  root.querySelectorAll('img').forEach(img=>{img.decoding='async';img.onerror=()=>{img.classList.add('image-failed');img.closest('.image-box')?.classList.add('image-error');};});
}
function pagedLibrary(items,scope){
  const view=libraryViews.get(scope)||{query:'',limit:80};libraryViews.set(scope,view);
  const q=view.query.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
  const filtered=items.filter(m=>!q||`${m.title} ${m.creator||''}`.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().includes(q));
  return `<div class="local-filter"><label>Filtrer ma bibliothèque <input type="search" data-local-filter="${esc(scope)}" value="${esc(view.query)}" placeholder="Titre ou auteur"></label><span>${filtered.length} titre(s)</span></div>${filtered.slice(0,view.limit).map(m=>row(m)).join('')||empty('Aucun titre','Change le filtre ou ajoute un titre.')}${filtered.length>view.limit?`<button class="tonal load-more" data-library-more="${esc(scope)}">Afficher 80 titres supplémentaires</button>`:''}`;
}
function bindPagedLibrary(){
  $$('[data-local-filter]').forEach(input=>input.oninput=()=>{const key=input.dataset.localFilter,v=libraryViews.get(key);v.query=input.value;v.limit=80;const pos=input.selectionStart;render();const next=document.querySelector('[data-local-filter]');next?.focus();next?.setSelectionRange(pos,pos);});
  $$('[data-library-more]').forEach(b=>b.onclick=()=>{libraryViews.get(b.dataset.libraryMore).limit+=80;render();});bindImages();
}
function renderHistory(){
  const t=FGCore.timeline(state.media,state.episodes),cutoff=historyView.days?Date.now()-historyView.days*DAY:0;
  const entries=t.entries.filter(e=>(historyView.kind==='all'||e.kind===historyView.kind)&&e.at>=cutoff);
  $('#content').innerHTML=`<section class="hero"><h2>Historique daté</h2><p>Chaque nouveau visionnage, revisionnage ou titre terminé garde sa date. Les exports anciens ne fournissent pas nécessairement toutes les dates.</p><p class="notice">${t.knownPlays} activité(s) datée(s) · ${t.undatedPlays} lecture(s) ancienne(s) sans date précise. Elles restent incluses dans les compteurs, pas dans le graphique.</p><div class="filter-row"><label>Type <select id="historyKind"><option value="all">Tout</option><option value="episode">Épisodes</option><option value="movie">Films</option><option value="game">Jeux</option><option value="book">Livres</option></select></label><label>Période <select id="historyDays"><option value="0">Tout l’historique connu</option><option value="7">7 jours</option><option value="30">30 jours</option><option value="365">365 jours</option></select></label><button id="exportActivity" class="tonal">Exporter cet historique</button></div></section>${entries.slice(0,historyView.limit).map(e=>`<article class="history-entry" data-open="${esc(e.mediaId)}" data-src="media" tabindex="0"><time>${esc(humanDate(e.at))}</time><div><b>${esc(e.parentTitle)}</b><p>${e.kind==='episode'?esc(epCode({season:e.season,num:e.number})+' · '+e.title):esc(kindName(e.kind))}</p></div></article>`).join('')||empty('Aucune activité dans cette période','Les dates apparaissent après le suivi des titres.')}${entries.length>historyView.limit?'<button id="historyMore" class="tonal load-more">Afficher 100 activités supplémentaires</button>':''}`;
  $('#historyKind').value=historyView.kind;$('#historyDays').value=String(historyView.days);
  $('#historyKind').onchange=e=>{historyView.kind=e.target.value;historyView.limit=100;renderHistory();};$('#historyDays').onchange=e=>{historyView.days=Number(e.target.value);historyView.limit=100;renderHistory();};
  $('#historyMore')?.addEventListener('click',()=>{historyView.limit+=100;renderHistory();});$('#exportActivity').onclick=()=>downloadJson('franchiseguide-historique.json',{app:'FranchiseGuide',exportedAt:new Date().toISOString(),undatedPlays:t.undatedPlays,entries});bindRows();
}
