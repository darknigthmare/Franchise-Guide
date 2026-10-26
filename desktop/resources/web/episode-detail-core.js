/* V0.24 episode sheet: identity-bound catalogue reads and guarded personal-field edits. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.FGEpisodeDetail=api;})(globalThis,function(){
 'use strict';
 const own=(o,k)=>Object.prototype.hasOwnProperty.call(o,k),obj=x=>!!x&&typeof x==='object'&&!Array.isArray(x);
 const fail=x=>{throw Error(x);};
 function int(v,label,min=0,max=2147483647){if(!Number.isSafeInteger(v)||v<min||v>max)fail(label+' invalide.');return v;}
 function text(v,label,max=2000,nullable=true){if(v==null&&nullable)return '';if(typeof v!=='string'||v.length>max||/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(v))fail(label+' invalide ou trop long.');return v.trim();}
 function language(v){if(typeof v!=='string'||!/^\s*[a-z]{2,3}(?:-[A-Z]{2})?\s*$/.test(v))fail('Langue catalogue invalide.');return v.trim();}
 function date(v){if(v==null||v==='')return null;if(typeof v!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(v))fail('Date de diffusion invalide.');const d=new Date(v+'T00:00:00Z');if(!Number.isFinite(d.getTime())||d.toISOString().slice(0,10)!==v)fail('Jour de diffusion impossible.');return v;}
 function image(v,size='w780'){return typeof v==='string'&&/^\/[A-Za-z0-9._-]{1,180}$/.test(v)&&!['/.','/..'].includes(v)?'https://image.tmdb.org/t/p/'+size+v:null;}
 function locate(state,showId,episodeId){
  const shows=(state.media||[]).filter(m=>m.id===showId&&m.kind==='show'&&m.added&&!m.suggestion),eps=(state.episodes||[]).filter(e=>e.id===episodeId);
  if(shows.length!==1||eps.length!==1||eps[0].showId!==showId)fail('Série ou épisode retiré, ambigu ou déplacé. Rouvre sa fiche.');return {show:shows[0],episode:eps[0]};
 }
 function identity(m,e){return JSON.stringify([m.id,m.kind,m.tmdbId??null,e.id,e.showId,e.tmdbId??null,e.season??null,e.num??e.number??null,!!e.seasonUnknown,!!e.numberUnknown]);}
 function request(m,e,lang='fr-FR'){
  if(m.kind!=='show'||e.showId!==m.id)fail('L’épisode ne correspond pas à cette série.');
  if(e.seasonUnknown||e.numberUnknown)fail('Numérotation inconnue : actualise la série pour identifier cet épisode.');
  const seriesId=int(m.tmdbId,'Identifiant TMDb de la série',1),season=int(e.season,'Saison',0,99999),number=int(e.num??e.number,'Numéro d’épisode',1,999999),episodeId=e.tmdbId==null?null:int(e.tmdbId,'Identifiant TMDb de l’épisode',1),locale=language(lang);
  const path=`/tv/${seriesId}/season/${season}/episode/${number}`;
  return {seriesId,season,number,episodeId,language:locale,path,key:JSON.stringify([seriesId,season,number,episodeId,locale]),identity:identity(m,e)};
 }
 function parse(raw,q,observedAt=Date.now()){
  int(observedAt,'Date de consultation',0,8640000000000000);
  if(!obj(raw))fail('Fiche épisode incomplète.');const id=int(raw.id,'Identifiant épisode reçu',1);
  if(raw.season_number!==q.season||raw.episode_number!==q.number||q.episodeId!=null&&q.episodeId!==id||raw.show_id!=null&&raw.show_id!==q.seriesId)fail('Réponse destinée à un autre épisode. Ancienne fiche conservée.');
  const title=text(raw.name,'Titre',500,false),overview=text(raw.overview,'Synopsis',30000),airDate=date(raw.air_date),runtime=raw.runtime==null?null:int(raw.runtime,'Durée',0,100000);
  const rating=raw.vote_average==null?null:raw.vote_average;if(rating!=null&&(typeof rating!=='number'||!Number.isFinite(rating)||rating<0||rating>10))fail('Note catalogue invalide.');
  const votes=raw.vote_count==null?null:int(raw.vote_count,'Nombre de votes',0),productionCode=text(raw.production_code,'Code de production',250);
  let ignoredImages=0;const still=image(raw.still_path);if(raw.still_path&&!still)ignoredImages++;
  if(!obj(raw.credits)||!['cast','guest_stars','crew'].every(k=>Array.isArray(raw.credits[k])))fail('Distribution de l’épisode incomplète. Réessaie la lecture.');
  if(raw.credits.id!=null&&raw.credits.id!==id)fail('Distribution reçue pour un autre épisode.');
  let total=0;const known=new Map();
  const groups=[['cast','Distribution'],['guest_stars','Invités'],['crew','Équipe']].map(([kind,label])=>{
   const list=raw.credits[kind];if(list.length>2500)fail('Distribution trop volumineuse.');total+=list.length;const people=new Map();
   for(const row of list){if(!obj(row))fail('Crédit de personne invalide.');const personId=int(row.id,'Identifiant de personne',1),name=text(row.name,'Nom',250,false);if(!name)fail('Nom de personne absent.');const portrait=image(row.profile_path,'w185');if(row.profile_path&&!portrait)ignoredImages++;
    if(known.has(personId)&&known.get(personId)!==name)fail('Identité de personne contradictoire.');known.set(personId,name);
    const role=text(kind==='crew'?row.job:row.character,'Rôle',500),department=text(row.department,'Département',250),p=people.get(personId)||{id:personId,name,profileUrl:portrait,roles:[],departments:[]};
    if(!p.profileUrl&&portrait)p.profileUrl=portrait;if(role&&!p.roles.includes(role))p.roles.push(role);if(department&&!p.departments.includes(department))p.departments.push(department);people.set(personId,p);
   }
   return {kind,label,people:[...people.values()]};
  });if(total>5000)fail('Trop de crédits de distribution.');
  return {id,seriesId:q.seriesId,season:q.season,number:q.number,language:q.language,title,overview,airDate,runtime,rating,votes,productionCode,stillUrl:still,groups,ignoredImages,observedAt,sourceUrl:`https://www.themoviedb.org/tv/${q.seriesId}/season/${q.season}/episode/${q.number}`};
 }
 function fields(e){return {rating:e.rating??null,userNote:e.userNote??null};}
 function personal(e,form){
  const raw=String(form.rating??'').trim();let rating=null;if(raw){if(!/^\d+$/.test(raw))fail('Ma note : un entier de 1 à 10 ou un champ vide.');rating=int(Number(raw),'Ma note',1,10);}
  if(typeof form.userNote!=='string')fail('Texte personnel invalide.');const note=text(form.userNote,'Note personnelle',2000,false)||null;
  return {rating,userNote:note};
 }
 function prepare(state,showId,episodeId,form,now=Date.now()){
  const {show,episode}=locate(state,showId,episodeId);return {showId,episodeId,identity:identity(show,episode),before:fields(episode),after:personal(episode,form),createdAt:int(now,'Horodatage',0,8640000000000000)};
 }
 function apply(state,plan,now=Date.now()){
  if(!obj(plan)||!obj(plan.before)||!obj(plan.after)||!Number.isSafeInteger(plan.createdAt)||now<plan.createdAt||now-plan.createdAt>10*60000)fail('Formulaire expiré. Recharge les champs personnels.');
  const {show,episode}=locate(state,plan.showId,plan.episodeId);
  if(identity(show,episode)!==plan.identity||JSON.stringify(fields(episode))!==JSON.stringify(plan.before))fail('L’épisode ou ses champs personnels ont changé. Recharge-les avant d’enregistrer.');
  const after=personal(episode,{rating:plan.after.rating,userNote:plan.after.userNote??''});
  return {...state,episodes:state.episodes.map(e=>e.id===plan.episodeId?{...e,...after}:e)};
 }
 function navigation(state,showId,episodeId,includeSpecials=false){
  const {episode}=locate(state,showId,episodeId);if(episode.seasonUnknown||episode.numberUnknown)return {previous:null,next:null};
  const all=(state.episodes||[]).filter(e=>e.showId===showId&&!e.seasonUnknown&&!e.numberUnknown&&Number.isSafeInteger(e.season)&&e.season>=0&&Number.isSafeInteger(e.num??e.number)&&(e.num??e.number)>0&&(includeSpecials||e.season!==0||e.id===episodeId)).sort((a,b)=>a.season-b.season||(a.num??a.number)-(b.num??b.number));
  const seen=new Set();for(const e of all){const k=e.season+':'+(e.num??e.number);if(seen.has(k))return {previous:null,next:null};seen.add(k);}
  const i=all.findIndex(e=>e.id===episodeId);return {previous:i>0?all[i-1].id:null,next:i>=0&&i<all.length-1?all[i+1].id:null};
 }
 function history(e){const times=[...new Set([...(e.watchHistory||[]),e.lastWatchedAt].filter(t=>Number.isSafeInteger(t)&&t>0&&t<=8640000000000000))].sort((a,b)=>b-a);return {times,total:Math.max(Number(e.plays||0),e.watched?1:0,times.length),undated:Math.max(0,Number(e.plays||0)-times.length)};}
 return {text,date,image,language,locate,identity,request,parse,fields,personal,prepare,apply,navigation,history};
});
