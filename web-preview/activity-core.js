/* Domain rules for dated viewing and an all-day release agenda. No remote writes here. */
(function(root,factory){const core=typeof module==='object'&&module.exports?require('./core.js'):root.FGCore;const api=factory(core);if(typeof module==='object'&&module.exports)module.exports=api;else root.FGActivity=api;})(globalThis,function(C){
'use strict';
const KINDS=['episode','movie','game','book'];
function validDate(value){if(typeof value!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(value))return false;const year=+value.slice(0,4);if(year<1)return false;const d=new Date(value+'T12:00:00Z');return Number.isFinite(+d)&&d.toISOString().slice(0,10)===value;}
function shiftDate(value,days){if(!validDate(value)||!Number.isInteger(days))throw Error('Date ou décalage invalide.');const d=new Date(value+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+days);const s=d.toISOString().slice(0,10);if(!validDate(s))throw Error('Date hors plage.');return s;}
function localChoices(day,time){
 if(!validDate(day)||!/^\d{2}:\d{2}$/.test(time)||+time.slice(0,2)>23||+time.slice(3)>59)throw Error('Saisis une date et une heure valides (HH:mm).');
 const [y,m,d]=day.split('-').map(Number),[h,min]=time.split(':').map(Number);const seed=new Date(0);seed.setFullYear(y,m-1,d);seed.setHours(h,min,0,0);
 const out=[];for(let delta=-180;delta<=180;delta++){const t=+seed+delta*60000,z=new Date(t);if(z.getFullYear()===y&&z.getMonth()===m-1&&z.getDate()===d&&z.getHours()===h&&z.getMinutes()===min&&z.getSeconds()===0)out.push(t);}
 return [...new Set(out)].sort((a,b)=>a-b);
}
function addViewing(item,at,now=Date.now()){
 if(item.showId==null&&String(item.kind).toLowerCase()!=='movie')throw Error('Le visionnage daté concerne un film ou un épisode, pas une série entière.');
 if(!Number.isSafeInteger(at)||at<=0||at>now)throw Error('Choisis une date passée valide, après le 1er janvier 1970.');
 const known=C.knownWatchHistory(item);if(known.some(t=>Math.floor(t/1000)===Math.floor(at/1000)))throw Error('Un visionnage est déjà enregistré à cette seconde. Aucun doublon ajouté.');
 const dates=[...known,at].sort((a,b)=>a-b),oldCount=item.watched&&!item.skipped?Math.max(1,C.integer(item.plays)):0;
 if(oldCount>=100000)throw Error('Compteur de visionnages trop élevé.');
 return {...item,watched:true,skipped:false,plays:oldCount+1,watchHistory:dates,lastWatchedAt:dates[dates.length-1]};
}
function episodeCode(e){return e.seasonUnknown||e.numberUnknown?'Épisode à identifier':`S${String(e.season).padStart(2,'0')}E${String(e.num??e.number).padStart(2,'0')}`;}
function uid(key){return 'fg-'+encodeURIComponent(key).replace(/[!'()*~]/g,c=>'%'+c.charCodeAt(0).toString(16).toUpperCase())+'@franchiseguide.local';}
function keyFor(kind,item,parent){if(kind==='episode')return 'episode:'+(item.tmdbId?'tmdb:'+item.tmdbId:item.tvdbId?'tvdb:'+item.tvdbId:parent.id+':'+item.id);return kind+':'+(item.tmdbId?'tmdb:'+item.tmdbId:(item.source&&item.sourceId?item.source+':'+item.sourceId:item.id));}
function normalizedOptions(options={}){
 const o={kind:'all',from:null,to:null,favoritesOnly:false,unwatchedOnly:false,includeHidden:false,includeSpecials:false,includeSkipped:false,hideSpoilers:false,query:'',...options};
 if(o.kind!=='all'&&!KINDS.includes(o.kind))throw Error('Type de calendrier invalide.');
 for(const k of ['from','to']){if(o[k]==='')o[k]=null;if(o[k]!=null&&!validDate(o[k]))throw Error('Dates de filtre invalides.');}
 if(o.from&&o.to&&o.from>o.to)throw Error('La date de début doit précéder la date de fin.');
 for(const k of ['favoritesOnly','unwatchedOnly','includeHidden','includeSpecials','includeSkipped','hideSpoilers'])if(typeof o[k]!=='boolean')throw Error('Filtre de calendrier invalide.');
 o.query=String(o.query||'').slice(0,250).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('fr');return o;
}
function buildAgenda(media,episodes,options={}){
 const o=normalizedOptions(options),parents=new Map(media.filter(m=>m.added!==false&&!m.suggestion&&!m.isSuggestion).map(m=>[m.id,m])),events=[],seen=new Set();let undated=0,invalid=0,duplicates=0;
 function add(kind,item,parent,date){
  if(o.kind!=='all'&&kind!==o.kind||parent.hidden&&!o.includeHidden||o.favoritesOnly&&!parent.favorite||o.unwatchedOnly&&(item.watched||item.skipped))return;
  if(kind==='episode'&&(!o.includeSpecials&&item.season===0||!o.includeSkipped&&item.skipped))return;
  const hidden=kind==='episode'&&o.hideSpoilers&&!item.watched,code=kind==='episode'?episodeCode(item):null;
  const title=kind==='episode'?`${parent.title} · ${code}${hidden?'':` · ${item.title||''}`}`:parent.title;
  const searchable=String(title).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('fr');if(o.query&&!searchable.includes(o.query))return;
  if(!date){undated++;return;}if(!validDate(date)){invalid++;return;}if(o.from&&date<o.from||o.to&&date>o.to)return;
  const key=keyFor(kind,item,parent);if(seen.has(key)){duplicates++;return;}seen.add(key);
  events.push({key,uid:uid(key),date,kind,mediaId:parent.id,itemId:item.id,title:String(title),parentTitle:parent.title,code,watched:!!item.watched,skipped:!!item.skipped,
    description:kind==='episode'?'Date de diffusion indiquée dans la fiche. Horaire et disponibilité locale non confirmés.':'Date de sortie indiquée dans la fiche. Disponibilité locale non confirmée.'});
 }
 for(const m of parents.values()){const kind=String(m.kind).toLowerCase();if(KINDS.includes(kind)&&kind!=='episode')add(kind,m,m,m.releaseDate??null);}
 for(const e of episodes){const m=parents.get(e.showId);if(m&&String(m.kind).toLowerCase()==='show')add('episode',e,m,e.date??e.airDate??null);}
 events.sort((a,b)=>a.date.localeCompare(b.date)||a.title.localeCompare(b.title,'fr')||a.key.localeCompare(b.key));return {events,undated,invalid,duplicates};
}
function escapeText(v){return String(v??'').replace(/\\/g,'\\\\').replace(/\r\n|\r|\n/g,'\\n').replace(/;/g,'\\;').replace(/,/g,'\\,').replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g,'');}
function fold(line){const encoder=new TextEncoder();let parts=[],chunk='',bytes=0;for(const c of line){const n=encoder.encode(c).length;if(bytes+n>75){parts.push(chunk);chunk=' ';bytes=1;}chunk+=c;bytes+=n;}parts.push(chunk);return parts.join('\r\n');}
function calendar(events,{now=Date.now(),limit=10000}={}){
 if(!Array.isArray(events)||!Number.isInteger(limit)||limit<1||limit>10000||events.length>limit)throw Error('Export limité à 10 000 événements. Réduis la période ou les filtres ; aucun fichier partiel n’a été créé.');
 const seen=new Set(),stamp=new Date(now).toISOString().replace(/[-:]/g,'').replace(/\.\d{3}Z$/,'Z'),lines=['BEGIN:VCALENDAR','VERSION:2.0','PRODID:-//FranchiseGuide//Release agenda 0.17//FR','CALSCALE:GREGORIAN'];
 for(const e of events){if(!validDate(e.date)||!e.uid||/[\r\n]/.test(e.uid)||seen.has(e.uid))throw Error('Événement invalide ou identifiant de calendrier dupliqué.');seen.add(e.uid);
  lines.push('BEGIN:VEVENT','UID:'+e.uid,'DTSTAMP:'+stamp,'DTSTART;VALUE=DATE:'+e.date.replace(/-/g,''));if(e.date!=='9999-12-31')lines.push('DTEND;VALUE=DATE:'+shiftDate(e.date,1).replace(/-/g,''));
  lines.push('SUMMARY:'+escapeText(e.title),'DESCRIPTION:'+escapeText(e.description),'CLASS:PRIVATE','TRANSP:TRANSPARENT','END:VEVENT');
 }lines.push('END:VCALENDAR');return lines.map(fold).join('\r\n')+'\r\n';
}
return {KINDS,validDate,shiftDate,localChoices,addViewing,episodeCode,uid,buildAgenda,normalizedOptions,escapeText,fold,calendar};
});
