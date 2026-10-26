/* TMDb person profiles and de-duplicated cross-media filmography. */
'use strict';
const personProfiles=new Map();
function personId(credit){const raw=String(credit.id||'');const found=raw.match(/^(?:tmdb-person-)?(\d+)(?:-|$)/);return found&&Number(found[1])>0?Number(found[1]):null;}
function creditCards(m){
  const credits=(m.credits?.length?m.credits:(m.people||[]).map(name=>({name}))),shown=credits.slice(0,30);
  if(!shown.length)return '';
  function card(c){const id=personId(c),src=FGCore.safeUrl(c.profileUrl);return `<button class="credit credit-button" ${id?`data-person="${id}"`:'disabled'} aria-label="${esc('Ouvrir la fiche de '+c.name)}">${state.settings.showCastImages&&src?`<img src="${esc(src)}" alt="" loading="lazy" decoding="async">`:`<div class="avatar">${esc((c.name||'?')[0])}</div>`}<b>${esc(c.name)}</b><small>${esc(c.role||c.department||'')}</small></button>`;}
  return `<section class="section"><div class="section-title"><h3>Casting & équipe</h3><span>${credits.length} crédits disponibles</span></div><div class="credits">${shown.map(card).join('')}</div>${credits.length>30?`<details class="more-credits"><summary>Voir les ${credits.length-30} autres crédits</summary><div class="credits">${credits.slice(30).map(card).join('')}</div></details>`:''}</section>`;
}
function bindPeople(){
  $$('[data-person]').forEach(b=>b.onclick=()=>{const id=Number(b.dataset.person),from=overlay;
    overlay={type:'person',id,title:'Fiche personne',returnTo:from,kind:'all',query:'',sort:'year'};render();window.scrollTo(0,0);
  });
}
function normaliseFilmography(data){
  const merged=new Map();
  for(const [role,rows] of [['cast',data.cast||[]],['crew',data.crew||[]]])for(const x of rows){
    if(!['tv','movie'].includes(x.media_type)||!Number.isInteger(x.id)||x.id<=0)continue;
    const item=tmdbSearchItem(x);if(!item.title)continue;const key=identity(item);
    const found=merged.get(key)||{...item,roles:[]};
    const credit=role==='cast'?x.character:x.job||x.department;
    if(credit&&!found.roles.includes(credit))found.roles.push(credit);
    merged.set(key,found);
  }
  return [...merged.values()];
}
async function loadPerson(id){
  if(!serviceCredential('tmdb'))throw Error('Cette fiche personne nécessite le service TMDb avancé activé par l’éditeur.');
  const lang=state.settings.language||'fr-FR',key=id+':'+lang;
  if(personProfiles.has(key))return personProfiles.get(key);
  const d=await fetchJson(`https://api.themoviedb.org/3/person/${id}?api_key=${encodeURIComponent(serviceCredential('tmdb'))}&language=${encodeURIComponent(lang)}&append_to_response=combined_credits,external_ids`,{cacheMs:300000});
  if(Number(d.id)!==id||typeof d.name!=='string')throw Error('Fiche personne invalide.');
  const profile={id,name:d.name,biography:d.biography||'',birthday:d.birthday||null,deathday:d.deathday||null,
    birthplace:d.place_of_birth||null,department:d.known_for_department||null,posterUrl:tmdbImage(d.profile_path,'w342'),
    credits:normaliseFilmography(d.combined_credits||{}),loadedAt:Date.now()};
  personProfiles.set(key,profile);if(personProfiles.size>24)personProfiles.delete(personProfiles.keys().next().value);return profile;
}
async function renderPerson(o){
  const host=$('#content');host.innerHTML='<section class="hero"><h2>Fiche personne</h2><p role="status">Chargement du portrait et de la filmographie…</p></section>';
  try{
    const person=await loadPerson(Number(o.id));if(overlay!==o)return;
    o.title=person.name;$('#title').textContent=person.name;
    function draw(){
      if(overlay!==o)return;
      const query=(o.query||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
      let works=person.credits.filter(m=>(o.kind==='all'||m.kind===o.kind)&&(!query||m.title.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().includes(query)));
      works=FGCore.sortMedia(works,o.sort||'year',o.sort==='title');const count=works.length;const limit=o.limit||40;
      remoteResults=person.credits;
      host.innerHTML=`<section class="person-hero panel">${person.posterUrl?`<img src="${esc(person.posterUrl)}" alt="Portrait de ${esc(person.name)}" class="person-portrait">`:'<div class="avatar person-portrait">?</div>'}<div><span class="eyebrow">${esc(person.department||'Personne · TMDb')}</span><h2>${esc(person.name)}</h2>${person.birthday?`<p>Naissance : ${esc(person.birthday)}${person.birthplace?' · '+esc(person.birthplace):''}</p>`:''}${person.deathday?`<p>Décès : ${esc(person.deathday)}</p>`:''}<a href="https://www.themoviedb.org/person/${person.id}" class="tonal linkbtn" target="_blank" rel="noopener noreferrer">Source TMDb</a></div></section><section class="panel"><h3>Biographie</h3><p class="biography">${esc(person.biography||'Biographie non renseignée dans la langue sélectionnée.')}</p></section><section class="panel"><h3>Filmographie · ${count}</h3><p class="meta">Films et séries annoncés par TMDb. Les différents rôles d’une même œuvre sont regroupés ; aucune œuvre n’est ajoutée automatiquement.</p><div class="filter-row"><label>Type <select id="personKind"><option value="all">Tout</option><option value="movie">Films</option><option value="show">Séries</option></select></label><label>Titre <input id="personQuery" type="search" value="${esc(o.query||'')}" placeholder="Filtrer la filmographie"></label><label>Tri <select id="personSort"><option value="year">Année décroissante</option><option value="title">Titre</option></select></label></div></section><section id="filmographyRows">${works.slice(0,limit).map(m=>{const local=state.media.find(x=>FGCore.sameMedia(x,m));return `<div class="filmography-credit">${row(local||{...m,suggestion:true},local?'media':'remote')}<p class="meta credit-roles">${m.roles.map(esc).join(' · ')}</p></div>`;}).join('')||empty('Aucun résultat','Change les filtres.')}</section>${count>limit?'<button id="personMore" class="tonal load-more">Afficher 40 titres supplémentaires</button>':''}`;
      $('#personKind').value=o.kind||'all';$('#personSort').value=o.sort||'year';
      $('#personKind').onchange=e=>{o.kind=e.target.value;o.limit=40;draw();};$('#personSort').onchange=e=>{o.sort=e.target.value;draw();};
      $('#personQuery').oninput=e=>{const pos=e.target.selectionStart;o.query=e.target.value;o.limit=40;draw();const input=$('#personQuery');input.focus();input.setSelectionRange(pos,pos);};
      $('#personMore')?.addEventListener('click',()=>{o.limit=limit+40;draw();});bindRows();
    }
    draw();
  }catch(e){if(overlay===o){host.innerHTML=`<section class="hero"><h2>Fiche indisponible</h2><p role="alert">${esc(e.message)}</p><button id="retryPerson" class="tonal">Réessayer</button></section>`;$('#retryPerson').onclick=()=>{FGRequests.clear();renderPerson(o);};}}
}
