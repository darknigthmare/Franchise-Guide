/* Trakt reviews. Paginated, account-scoped drafts, explicit writes and uncertain-send protection.
   No markdown/HTML from a provider is injected as executable markup. */
'use strict';
const REVIEW_DRAFT_KEY='franchiseguide-review-drafts-v1';
const reviewSessions=new Map();
function reviewKey(m){return `${traktAuth().username||'guest'}:${m.kind}:${m.tmdbId}`;}
function reviewDrafts(){const d=readJsonLocal(REVIEW_DRAFT_KEY,{});return d&&typeof d==='object'&&!Array.isArray(d)?d:{};}
function draftFor(key){const d=reviewDrafts()[key];return d&&typeof d.text==='string'?d:{text:'',spoiler:false,uncertain:false,editing:null};}
function storeReviewDraft(key,draft){
  assertWritable();const all=reviewDrafts();
  if(!draft.text&&!draft.uncertain)delete all[key];else all[key]={text:draft.text.slice(0,10000),spoiler:!!draft.spoiler,uncertain:!!draft.uncertain,editing:draft.editing||null,at:Date.now()};
  // Do not discard other drafts just to meet a quota. Surface the storage error before any POST.
  localStorage.setItem(REVIEW_DRAFT_KEY,JSON.stringify(all));
}
function reviewSession(m){
  const key=reviewKey(m);if(!reviewSessions.has(key))reviewSessions.set(key,{items:[],page:0,more:true,busy:false,error:null,draft:draftFor(key),revealed:new Set(),request:0});
  return reviewSessions.get(key);
}
function normalizeReview(c){
  if(!c||!Number.isSafeInteger(c.id)||c.id<=0||typeof c.comment!=='string')throw Error('Avis Trakt invalide. Aucune page partielle ajoutée.');
  return {...c,user:c.user&&typeof c.user==='object'?c.user:{},likes:FGCore.integer(c.likes),spoiler:!!c.spoiler};
}
async function loadTraktComments(m,reset=true){
  const host=$('#traktComments');if(!host)return;
  const key=reviewKey(m),session=reviewSession(m),request=++session.request,accountGeneration=authGeneration;
  if(!serviceCredential('trakt')||!m.tmdbId){host.innerHTML='<p>Les avis Trakt deviennent disponibles quand le service est activé par l’éditeur.</p>';return;}
  const targetPage=reset?1:session.page+1;session.busy=true;session.error=null;
  drawReviews(m,host,session);
  try{
    if(!m.traktId)await enrichTrakt(m);
    if(!Number.isSafeInteger(Number(m.traktId))||Number(m.traktId)<=0)throw Error('Identifiant Trakt introuvable.');
    const page=targetPage;if(page>500)throw Error('Limite de pagination atteinte.');
    const path=`/${m.kind==='show'?'shows':'movies'}/${Number(m.traktId)}/comments/likes?page=${page}&limit=20`;
    const r=traktConnected()?await traktResponse(path):await fetchResult('https://api.trakt.tv'+path,{headers:traktHeaders()});
    if(!r.ok)throw Error(`Avis Trakt indisponibles (HTTP ${r.status}).`);
    if(!Array.isArray(r.json))throw Error('Liste d’avis Trakt invalide.');
    const rows=r.json.map(normalizeReview),count=Number(r.headers?.get('X-Pagination-Page-Count')||0);
    if(request!==session.request||accountGeneration!==authGeneration||key!==reviewKey(m))return;
    if(reset)session.items=[];const ids=new Set(session.items.map(c=>c.id));session.items.push(...rows.filter(c=>{if(ids.has(c.id))return false;ids.add(c.id);return true;}));
    session.page=page;session.more=count>0?page<count:rows.length===20;
  }catch(e){if(request===session.request)session.error=e.message;}
  finally{
    if(request===session.request){session.busy=false;if(host.isConnected&&overlay?.id===m.id&&key===reviewKey(m))drawReviews(m,host,session);}
  }
}
function drawReviews(m,host,session){
  const key=reviewKey(m),user=traktAuth().username,connected=traktConnected(),draft=session.draft;
  host.innerHTML=`<p class="meta">Avis fournis par Trakt · ${session.items.length} chargé(s). Les brouillons restent uniquement sur cet appareil.</p>${session.items.map(c=>{
    const revealed=session.revealed.has(c.id),own=!!user&&c.user.username===user;
    return `<article class="community-comment" data-review-id="${c.id}"><div><b>${esc(c.user.name||c.user.username||'Trakt')}</b><span>${c.review?'Avis':'Commentaire'} · ♥ ${c.likes}</span></div><p>${c.spoiler&&!revealed?'Spoiler masqué':esc(c.comment)}</p>${c.spoiler?`<button class="tonal" data-show-spoiler="${c.id}">${revealed?'Masquer le spoiler':'Afficher le spoiler'}</button>`:''}${own?`<button class="tonal" data-edit-review="${c.id}">Modifier mon avis</button><button class="tonal danger" data-delete-review="${c.id}">Supprimer mon avis</button>`:''}</article>`;
  }).join('')}${!session.items.length&&!session.busy&&!session.error?'<p>Aucun avis public disponible.</p>':''}${session.error?`<p role="alert" class="notice warn">${esc(session.error)}</p>`:''}${session.busy?'<p role="status">Chargement des avis…</p>':session.more?`<button class="tonal" id="reviewsMore">${session.error?'Réessayer cette page':'Charger les avis suivants'}</button>`:''}${connected?`<form id="reviewForm"><h4>${draft.editing?'Modifier mon avis public':'Publier sur Trakt'}</h4><label>Avis / commentaire<textarea id="reviewText" required maxlength="10000" rows="4" ${session.sending||draft.uncertain?'disabled':''}>${esc(draft.text)}</textarea></label><label class="toggle">Contient des spoilers <input id="reviewSpoiler" type="checkbox" ${draft.spoiler?'checked':''} ${session.sending||draft.uncertain?'disabled':''}></label><p id="reviewDraftStatus" class="meta" role="status">Minimum 5 mots. Ton brouillon est conservé localement.</p>${draft.uncertain?'<p class="notice warn">Envoi non confirmé. Vérifie sur Trakt avant de publier de nouveau.</p><button type="button" class="tonal" id="reviewConfirmAbsent">J’ai vérifié : l’avis n’a pas été publié</button>':''}<div class="actions"><button class="primary" id="reviewSubmit" type="submit" ${draft.uncertain||session.sending?'disabled':''}>${session.sending?'Envoi…':draft.editing?'Enregistrer sur Trakt':'Publier mon avis'}</button><button type="button" class="tonal" id="reviewDiscard">Effacer le brouillon local</button></div><p class="meta">Le texte sera public sur ton compte Trakt seulement après confirmation.</p></form>`:'<p>Connecte Trakt pour publier un avis.</p>'}`;
  host.querySelector('#reviewsMore')?.addEventListener('click',()=>loadTraktComments(m,false));
  host.querySelectorAll('[data-show-spoiler]').forEach(b=>b.onclick=()=>{const id=Number(b.dataset.showSpoiler);if(session.revealed.has(id))session.revealed.delete(id);else session.revealed.add(id);drawReviews(m,host,session);});
  function persist(){try{storeReviewDraft(key,draft);const h=host.querySelector('#reviewDraftStatus');if(h)h.textContent='Brouillon conservé sur cet appareil · 5 mots minimum.';return true;}catch(e){const h=host.querySelector('#reviewDraftStatus');if(h)h.textContent='Brouillon non enregistré : '+e.message;return false;}}
  host.querySelector('#reviewText')?.addEventListener('input',e=>{draft.text=e.target.value;persist();});
  host.querySelector('#reviewSpoiler')?.addEventListener('change',e=>{draft.spoiler=e.target.checked;persist();});
  host.querySelector('#reviewConfirmAbsent')?.addEventListener('click',()=>{if(session.sending)return;if(confirm('As-tu vérifié sur Trakt que cet avis n’existe pas déjà ? Un nouvel envoi pourrait créer un doublon.')){draft.uncertain=false;if(persist())drawReviews(m,host,session);}});
  host.querySelector('#reviewDiscard')?.addEventListener('click',()=>{if(session.sending)return;if(!confirm('Effacer seulement ce brouillon local ? Aucun avis ne sera supprimé sur Trakt.'))return;try{const empty={text:'',spoiler:false,uncertain:false,editing:null};storeReviewDraft(key,empty);session.draft=empty;drawReviews(m,host,session);}catch(e){toast('Brouillon conservé : '+e.message);}});
  host.querySelectorAll('[data-edit-review]').forEach(b=>b.onclick=()=>{if(session.sending||draft.uncertain)return;const c=session.items.find(x=>x.id===Number(b.dataset.editReview));if(c?.user.username!==user)return;if(draft.text&&!confirm('Remplacer le brouillon local actuel par cet avis ?'))return;Object.assign(draft,{text:c.comment,spoiler:c.spoiler,editing:c.id,uncertain:false});persist();drawReviews(m,host,session);host.querySelector('#reviewText')?.focus();});
  host.querySelectorAll('[data-delete-review]').forEach(b=>b.onclick=async()=>{
    const c=session.items.find(x=>x.id===Number(b.dataset.deleteReview));if(session.sending||c?.user.username!==user)return;
    if(!confirm('Supprimer définitivement cet avis public sur Trakt ?'))return;
    session.sending=true;b.disabled=true;
    try{assertWritable();await traktApi('/comments/'+c.id,{method:'DELETE'});session.items=session.items.filter(x=>x.id!==c.id);session.error=null;}
    catch(e){session.error=e.message;}finally{session.sending=false;if(host.isConnected&&key===reviewKey(m))drawReviews(m,host,session);}
  });
  host.querySelector('#reviewForm')?.addEventListener('submit',async e=>{
    e.preventDefault();if(session.sending||draft.uncertain)return;
    let value;try{value=FGCore.reviewText(draft.text);}catch(err){host.querySelector('#reviewDraftStatus').textContent=err.message;return;}
    if(!confirm(draft.editing?'Enregistrer cette modification publique sur Trakt ?':'Publier cet avis publiquement sur ton compte Trakt ?'))return;
    try{assertWritable();draft.uncertain=true;storeReviewDraft(key,draft);}catch(err){draft.uncertain=false;toast('Aucun envoi : '+err.message);return;}
    session.sending=true;drawReviews(m,host,session);
    try{
      const body={comment:value.text,spoiler:!!draft.spoiler,review:value.review};
      if(!draft.editing)body[m.kind==='show'?'show':'movie']={ids:{trakt:Number(m.traktId)}};
      if(draft.editing){const own=await traktApi('/comments/'+Number(draft.editing));if(!Number.isSafeInteger(draft.editing)||draft.editing<=0||own?.user?.username!==user){const e=Error('Cet avis n’appartient pas au compte connecté.');e.status=403;throw e;}}
      const result=await traktApi(draft.editing?'/comments/'+draft.editing:'/comments',{method:draft.editing?'PUT':'POST',body:JSON.stringify(body)});
      if(!result||(!Number.isSafeInteger(result.id)||result.id<=0))throw Error('Réponse d’envoi non confirmée. Vérifie ton avis sur Trakt.');
      session.draft={text:'',spoiler:false,uncertain:false,editing:null};storeReviewDraft(key,session.draft);session.error=null;
      if(host.isConnected&&key===reviewKey(m))await loadTraktComments(m,true);
    }catch(err){
      // No automatic repeat after a timeout/5xx: the server may already have published it.
      if([400,401,403,404,409,410,412,413,415,420,422,423,426,429].includes(err.status))draft.uncertain=false;
      try{storeReviewDraft(key,draft);}catch{}session.error=err.message;
    }finally{session.sending=false;if(host.isConnected&&key===reviewKey(m))drawReviews(m,host,session);}
  });
}
