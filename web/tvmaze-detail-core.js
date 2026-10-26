/* TVmaze public detail rules. Public IDs never masquerade as TMDb IDs.
 * Data attribution: TVmaze, CC BY-SA. Temporary reads, never automatic tracking writes.
 */
(function(root,factory){const pub=typeof module==='object'&&module.exports?require('./autosetup-core.js'):root.FGPublicCatalog;const api=factory(pub);if(typeof module==='object'&&module.exports)module.exports=api;else root.FGTVmazeDetail=api;})(globalThis,function(P){
'use strict';
const obj=x=>x!==null&&typeof x==='object'&&!Array.isArray(x),fail=s=>{throw Error(s);};
function identifier(x,label='Identifiant TVmaze'){const s=String(x??'');if(!/^[1-9]\d{0,14}$/.test(s)||!Number.isSafeInteger(Number(s)))fail(label+' invalide.');return s;}
function numeric(x,label,min=0,max=100000){if(x==null)return null;if(typeof x!=='number'||!Number.isFinite(x)||x<min||x>max)fail(label+' invalide.');return x;}
function text(x,label,max=30000,required=false){if(x==null&&!required)return '';if(typeof x!=='string'||x.length>max||/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(x))fail(label+' absent ou invalide.');const s=P.clean(x,max);if(required&&!s)fail(label+' absent.');return s;}
function rows(x,label,max=5000){if(!Array.isArray(x)||x.length>max||x.some(r=>!obj(r)))fail(label+' absent, invalide ou trop volumineux.');return x;}
function image(x){if(!x)return null;try{const u=new URL(x);return u.protocol==='https:'&&u.hostname==='static.tvmaze.com'&&!u.port&&!u.username&&!u.password?u.href:null;}catch{return null;}}
function portrait(o){if(!obj(o))return null;return image(o.medium)||image(o.original);}
function personRef(c){const m=String(c?.id||'').match(/^tvmaze-person-([1-9]\d*)$/);return m?identifier(m[1]):null;}
function episodeRequest(show,e){
 if(show.kind!=='show'||show.source!=='tvmaze'||e.showId!==show.id)fail('Épisode non relié à une série TVmaze.');
 const sid=identifier(show.sourceId,'Série TVmaze'),match=String(e.id).match(/^tvmaze-episode-([1-9]\d*)$/);if(!match)fail('Identifiant TVmaze de l’épisode absent. Actualise la série.');const eid=identifier(match[1]);
 return {provider:'tvmaze',seriesId:Number(sid),episodeId:Number(eid),season:e.season,number:e.numberUnknown?null:(e.num??e.number??null),special:e.season===0,path:'/episodes/'+eid+'?embed=show',language:'Langue fournie par TVmaze',key:JSON.stringify(['tvmaze',show.id,sid,e.id,e.season,e.num??e.number??null,!!e.numberUnknown])};
}
function group(raw,kind,label,known){const people=new Map();for(const c of rows(raw,label,2500)){
 if(!obj(c.person))fail('Personne TVmaze absente.');const id=Number(identifier(c.person.id)),name=text(c.person.name,'Nom',250,true);if(known.has(id)&&known.get(id)!==name)fail('Nom de personne contradictoire.');known.set(id,name);
 const role=kind==='crew'?text(c.type,'Fonction',500):text(c.character?.name,'Rôle',500),p=people.get(id)||{id,provider:'tvmaze',name,profileUrl:portrait(c.person.image),roles:[],departments:[]};
 if(role&&!p.roles.includes(role))p.roles.push(role);if(!p.profileUrl)p.profileUrl=portrait(c.person.image);people.set(id,p);
 }return {kind,label,people:[...people.values()]};}
function episodeSnapshot(raw,guests,crew,q,now=Date.now()){
 if(!obj(raw)||Number(identifier(raw.id))!==q.episodeId)fail('Réponse TVmaze destinée à un autre épisode.');
 if(!obj(raw._embedded?.show)||Number(identifier(raw._embedded.show.id))!==q.seriesId)fail('Série TVmaze absente ou différente dans la réponse.');
 const special=typeof raw.type==='string'&&raw.type.includes('special');if(special!==q.special)fail('Épisode standard/spécial incohérent.');
 if(!q.special&&(raw.season!==q.season||q.number!==null&&raw.number!==q.number))fail('Numérotation TVmaze incohérente.');
 if(!Number.isSafeInteger(raw.season)||raw.season<1||raw.number!=null&&(!Number.isSafeInteger(raw.number)||raw.number<1))fail('Numérotation fournisseur invalide.');
 const groups=[group(guests,'guest_stars','Invités de cet épisode',new Map()),group(crew,'crew','Équipe invitée de cet épisode',new Map())];
 // Validate contradictions across both endpoint lists, not just within a group.
 const names=new Map();for(const g of groups)for(const p of g.people){if(names.has(p.id)&&names.get(p.id)!==p.name)fail('Identité de personne contradictoire entre les crédits.');names.set(p.id,p.name);}
 if(!Number.isSafeInteger(now)||now<0)fail('Horodatage invalide.');
 return {provider:'tvmaze',id:q.episodeId,seriesId:q.seriesId,season:q.season,number:q.number,language:q.language,title:text(raw.name,'Titre',500,true),overview:text(raw.summary,'Synopsis'),airDate:P.date(raw.airdate),runtime:numeric(raw.runtime,'Durée'),rating:numeric(raw.rating?.average,'Note',0,10),votes:null,productionCode:'',stillUrl:portrait(raw.image),groups,ignoredImages:raw.image&&!portrait(raw.image)?1:0,observedAt:now,sourceUrl:'https://www.tvmaze.com/episodes/'+q.episodeId};
}
function personSnapshot(raw,castCredits,crewCredits,expected,now=Date.now()){
 const id=identifier(expected);if(!obj(raw)||identifier(raw.id)!==id)fail('Réponse TVmaze destinée à une autre personne.');
 const merged=new Map();for(const [kind,data] of [['cast',castCredits],['crew',crewCredits]])for(const row of rows(data,'Crédits de la personne',10000)){
 const s=row._embedded?.show;if(!obj(s))fail('Série liée absente dans la filmographie TVmaze.');const item=P.tvShow(s),found=merged.get(item.sourceId);
 if(found&&found.title!==item.title)fail('Titre contradictoire pour un même identifiant TVmaze.');
 const role=kind==='crew'?text(row.type,'Fonction',500):(row._embedded?.character?text(row._embedded.character.name,'Personnage',500):row.self===true?'Participation en son nom':row.voice===true?'Interprétation vocale':'Interprétation');
 const entry=found||{...item,roles:[],creditKinds:[]};if(role&&!entry.roles.includes(role))entry.roles.push(role);if(!entry.creditKinds.includes(kind))entry.creditKinds.push(kind);merged.set(item.sourceId,entry);
 }
 return {id:Number(id),provider:'tvmaze',name:text(raw.name,'Nom',250,true),birthday:P.date(raw.birthday),deathday:P.date(raw.deathday),country:text(raw.country?.name,'Pays',200),posterUrl:portrait(raw.image),biography:'',credits:[...merged.values()],loadedAt:now,sourceUrl:'https://www.tvmaze.com/people/'+id};
}
function filterCredits(credits,query=''){const q=P.clean(query,250).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();return credits.filter(c=>!q||[c.title,...(c.roles||[])].join(' ').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().includes(q));}
return {identifier,personRef,portrait,image,episodeRequest,episodeSnapshot,personSnapshot,filterCredits};
});
