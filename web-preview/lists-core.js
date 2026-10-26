/* V0.21: personal lists. Pure transforms; no provider, auth, clock or storage side effects. */
(function(root,factory){const c=typeof module==='object'&&module.exports?require('./core.js'):root.FGCore;const api=factory(c);if(typeof module==='object'&&module.exports)module.exports=api;else root.FGLists=api;})(globalThis,function(C){
'use strict';
const MAX=10000, SORTS=['title','year','recently_watched','added','progress','next_episode','status','rating','remaining'];
const own=m=>m&&m.added!==false&&!m.suggestion&&!m.isSuggestion;
const normalized=x=>String(x??'').normalize('NFKC').trim().replace(/\s+/gu,' ').toLocaleLowerCase('fr');
const searchText=x=>normalized(x).normalize('NFD').replace(/[\u0300-\u036f]/g,'');
const equal=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
const uniqueIds=(ids,limit=50000)=>{if(!Array.isArray(ids)||ids.length>limit||ids.some(x=>typeof x!=='string'||!x||x.length>500||/[\u0000-\u001f\u007f]/.test(x))||new Set(ids).size!==ids.length)throw Error('Identifiants de liste invalides, trop nombreux ou en double.');return [...ids];};
function definition(l){
 if(!l||typeof l!=='object'||Array.isArray(l))throw Error('Définition de liste invalide.');uniqueIds([l.id],1);
 if(typeof l.name!=='string'||!l.name.trim()||l.name.trim().length>20000||/[\u0000-\u001f\u007f]/.test(l.name))throw Error('Le nom doit contenir de 1 à 120 caractères, sans caractère de contrôle.');
 if(l.description!=null&&(typeof l.description!=='string'||l.description.length>100000||/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(l.description)))throw Error('Description invalide (3 000 caractères maximum).');
 const mode=String(l.sortMode||'title').toLowerCase();if(!SORTS.includes(mode))throw Error('Mode de tri de liste invalide.');
 for(const k of ['ascending','manualOrder'])if(l[k]!=null&&typeof l[k]!=='boolean')throw Error('Option de liste invalide.');
 return {id:l.id,name:l.name.trim(),description:(l.description||'').trim(),sortMode:mode,ascending:l.ascending!==false,manualOrder:l.manualOrder===true,sortOrder:uniqueIds(l.sortOrder||[])};
}
function editedDefinition(l){const d=definition(l);if(d.name.length>120||d.description.length>3000)throw Error('Nom limité à 120 caractères et description à 3 000 lors de la création ou modification.');return d;}
function selected(state,id){const matches=(state.lists||[]).filter(l=>l.id===id);if(matches.length!==1)throw Error('Cette liste n’existe plus ou son identifiant est en double.');return definition(matches[0]);}
function checkExpected(state,id,expected){const l=selected(state,id);if(expected&&!equal(l,definition(expected)))throw Error('La définition de la liste a changé. Ferme puis rouvre cet aperçu.');return l;}
function ensureName(state,next){if((state.lists||[]).some(l=>l.id!==next.id&&normalized(l.name)===normalized(next.name)))throw Error('Une liste porte déjà ce nom. Choisis un autre nom.');}
function members(state,id){const items=(state.media||[]).filter(m=>own(m)&&(m.listIds||[]).includes(id));const ids=items.map(m=>m.id);uniqueIds(ids);return items;}
function orderedIds(state,id){const l=selected(state,id),all=members(state,id).map(m=>m.id),present=new Set(all),known=new Set(l.sortOrder);return l.sortOrder.filter(x=>present.has(x)).concat(all.filter(x=>!known.has(x)));}
function put(state,l){const has=(state.lists||[]).some(x=>x.id===l.id);return {...state,lists:has?state.lists.map(x=>x.id===l.id?l:x):[...(state.lists||[]),l]};}
function create(state,input){const l=editedDefinition(input);if((state.lists||[]).some(x=>x.id===l.id))throw Error('Identifiant de liste déjà utilisé.');if((state.lists||[]).length>=50000)throw Error('Nombre de listes maximal atteint.');ensureName(state,l);return put(state,l);}
function edit(state,id,input,expected=null){const old=checkExpected(state,id,expected),l=editedDefinition({...old,...input,id});ensureName(state,l);return put(state,l);}
function duplicate(state,id,newId,name,expected=null){const old=checkExpected(state,id,expected),ids=orderedIds(state,id);if(ids.length>MAX)throw Error('10 000 titres maximum pour dupliquer une liste.');const next=create(state,{...old,id:newId,name,sortOrder:ids});const set=new Set(ids);return {...next,media:state.media.map(m=>set.has(m.id)?{...m,listIds:[...(m.listIds||[]).filter(x=>x!==newId),newId]}:m)};}
function remove(state,id,expected=null){checkExpected(state,id,expected);const detach=m=>(m.listIds||[]).includes(id)?{...m,listIds:m.listIds.filter(x=>x!==id)}:m;return {...state,lists:state.lists.filter(l=>l.id!==id),media:state.media.map(detach),suggestions:(state.suggestions||[]).map(detach)};}
function planMembership(state,id,chosen){const l=selected(state,id),ids=uniqueIds(chosen,MAX),before=members(state,id).map(m=>m.id);if(before.length>MAX)throw Error('Cette opération est limitée à 10 000 membres.');const index=new Map((state.media||[]).map(m=>[m.id,m])),after=new Set(ids),prior=new Set(before);
 for(const key of ids)if(!own(index.get(key)))throw Error('Un titre sélectionné a été retiré de la bibliothèque.');
 const patches=[...new Set([...before,...ids])].filter(k=>prior.has(k)!==after.has(k)).map(k=>({id:k,before:prior.has(k),after:after.has(k)}));
 return {list:l,beforeMembers:before,desired:ids,patches,added:patches.filter(p=>p.after).length,removed:patches.filter(p=>!p.after).length};
}
function applyMembership(state,plan){if(!plan||!plan.list)throw Error('Aperçu invalide.');checkExpected(state,plan.list.id,plan.list);const now=members(state,plan.list.id).map(m=>m.id).sort();if(!equal(now,[...plan.beforeMembers].sort()))throw Error('Les membres de la liste ont changé depuis la sélection. Aucune modification partielle.');const fresh=planMembership(state,plan.list.id,plan.desired);if(!equal(fresh.patches,plan.patches))throw Error('Un titre a changé depuis l’aperçu.');const changes=new Map(fresh.patches.map(p=>[p.id,p]));
 let next={...state,media:state.media.map(m=>{const p=changes.get(m.id);return !p?m:{...m,listIds:p.after?[...(m.listIds||[]),plan.list.id]:(m.listIds||[]).filter(x=>x!==plan.list.id)};})};
 const l=selected(next,plan.list.id);next=put(next,{...l,sortOrder:orderedIds(next,l.id)});return next;
}
function move(state,id,mediaId,position,expectedOrder=null){const l=selected(state,id),ids=orderedIds(state,id);if(ids.length>MAX)throw Error('10 000 titres maximum pour modifier l’ordre.');if(expectedOrder&&!equal(ids,expectedOrder))throw Error('L’ordre ou les membres ont changé. Rouvre le déplacement.');if(!ids.includes(mediaId)||!Number.isInteger(position)||position<1||position>ids.length)throw Error('Position invalide dans l’ensemble de la liste.');const order=ids.filter(x=>x!==mediaId);order.splice(position-1,0,mediaId);return put(state,{...l,sortOrder:order,manualOrder:true});}
function status(m){if(['game','book'].includes(m.kind))return m.trackingStatus|| (m.watched?'completed':m.started||m.progressCurrent>0?'active':'planned');return m.watched?'watched':'unwatched';}
function view(state,id,options={}){const l=selected(state,id),o={kind:'all',visibility:'all',state:'all',query:'',...options};if(!['all','show','movie','game','book'].includes(o.kind)||!['all','visible','hidden'].includes(o.visibility)||!['all','done','pending','favorite'].includes(o.state))throw Error('Filtre de liste invalide.');let rows=members(state,id),all=rows;const q=searchText(o.query);
 rows=rows.filter(m=>(o.kind==='all'||m.kind===o.kind)&&(o.visibility==='all'||!!m.hidden===(o.visibility==='hidden'))&&(o.state==='all'||o.state==='favorite'&&m.favorite||o.state==='done'&&(m.kind==='show'?false:['completed','watched'].includes(status(m)))||o.state==='pending'&&(m.kind==='show'?true:!['completed','watched'].includes(status(m))))&&(!q||searchText(m.title).includes(q)));
 // Series have no single reliable completion state: the filter does not infer it from stale show.watched.
 if(o.state==='done'||o.state==='pending'){const wanted=o.state==='done';const buckets=new Map();for(const e of state.episodes||[]){if(!buckets.has(e.showId))buckets.set(e.showId,[]);buckets.get(e.showId).push(e);}const series=all.filter(m=>m.kind==='show');const done=new Set(series.filter(m=>m.catalogComplete&&(buckets.get(m.id)||[]).filter(e=>e.season!==0).every(e=>!e.seasonUnknown&&!e.numberUnknown&&Number.isInteger(e.season)&&e.season>0&&Number.isInteger(e.num??e.number)&&(e.num??e.number)>0)&&new Set((buckets.get(m.id)||[]).filter(e=>e.season!==0).map(e=>e.season+':'+(e.num??e.number))).size===(buckets.get(m.id)||[]).filter(e=>e.season!==0).length&&(buckets.get(m.id)||[]).filter(e=>e.season!==0).length>0&&(buckets.get(m.id)||[]).filter(e=>e.season!==0).every(e=>e.watched||e.skipped)&&(m.episodeCount==null||(buckets.get(m.id)||[]).filter(e=>e.season!==0).length>=m.episodeCount)).map(m=>m.id));
  rows=all.filter(m=>(o.kind==='all'||m.kind===o.kind)&&(o.visibility==='all'||!!m.hidden===(o.visibility==='hidden'))&&(!q||searchText(m.title).includes(q))&&(m.kind==='show'?done.has(m.id)===wanted:['completed','watched'].includes(status(m))===wanted));
 }
 if(l.manualOrder){const order=new Map(orderedIds(state,id).map((key,i)=>[key,i]));rows.sort((a,b)=>order.get(a.id)-order.get(b.id));}else rows=C.sortMedia(rows,l.sortMode,l.ascending,state.episodes||[]);
 const counts={total:all.length,hidden:all.filter(m=>m.hidden).length,show:0,movie:0,game:0,book:0};for(const m of all)counts[m.kind]++;
 return {list:l,rows,counts};
}
function report(state,id,options={}){const {list,rows,counts}=view(state,id,options);if(rows.length>MAX)throw Error('10 000 titres maximum à exporter. Réduis les filtres.');return {format:'FranchiseGuide-personal-list-report',version:1,list:{name:list.name},filters:{kind:options.kind||'all',visibility:options.visibility||'all',state:options.state||'all',query:options.query||''},totalKnown:counts.total,selected:rows.length,items:rows.map(m=>({title:m.title,kind:m.kind,year:m.year??null,hidden:!!m.hidden}))};}
function csv(report){if(!report||!Array.isArray(report.items)||report.items.length>MAX)throw Error('Rapport invalide.');const quote=v=>'"'+String(v??'').replace(/^(\s*[=+@-])/u,"'$1").replace(/"/g,'""')+'"';return '\uFEFF'+[['Titre','Catégorie','Année','Masqué'],...report.items.map(m=>[m.title,m.kind,m.year,m.hidden?'Oui':'Non'])].map(row=>row.map(quote).join(';')).join('\r\n')+'\r\n';}
return {MAX,SORTS,own,definition,selected,members,orderedIds,create,edit,duplicate,remove,planMembership,applyMembership,move,view,report,csv,normalized};
});
