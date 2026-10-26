/* V0.20 pure progress rules. A current game/read-through is not the historical completion count. */
(function(root,factory){const c=typeof module==='object'&&module.exports?require('./core.js'):root.FGCore;const api=factory(c);if(typeof module==='object'&&module.exports)module.exports=api;else root.FGProgress=api;})(globalThis,function(C){
'use strict';
const STATES=['planned','active','paused','abandoned','completed'];
function applicable(m){return ['game','book'].includes(String(m.kind).toLowerCase());}
function status(m){if(STATES.includes(m.trackingStatus))return m.trackingStatus;return m.watched?'completed':m.started||m.progressCurrent>0?'active':'planned';}
function integer(value,label,max=10000000){const s=String(value??'').trim();if(!s)return 0;if(!/^\d+$/.test(s))throw Error(label+' : nombre entier positif ou nul attendu.');const n=Number(s);if(!Number.isSafeInteger(n)||n>max)throw Error(label+' : valeur trop élevée.');return n;}
function text(v,max){return String(v??'').trim().slice(0,max);}
function update(m,form,now=Date.now()){
 if(!applicable(m))throw Error('Progression réservée aux jeux et livres.');
 const current=integer(form.current,'Avancement'),total=integer(form.total,'Total');if(total&&current>total)throw Error('L’avancement dépasse le total. Saisis 0 si le total est inconnu.');
 let next=form.status||'auto';if(next==='auto')next=total>0&&current===total?'completed':current>0||status(m)==='completed'?'active':status(m);
 if(!STATES.includes(next))throw Error('État du parcours invalide.');
 let out={...m,progressCurrent:current,progressTotal:total,progressUnit:text(form.unit??m.progressUnit,30),trackingStatus:next,started:next!=='planned'||current>0,
  personalPlatform:text(form.personalPlatform??m.personalPlatform,120)||null,personalFormat:text(form.personalFormat??m.personalFormat,120)||null};
 if(form.playtimeMinutes!==undefined)out.playtimeMinutes=String(form.playtimeMinutes).trim()===''?null:integer(form.playtimeMinutes,'Minutes jouées');
 if(next==='completed'){
  if(status(m)!=='completed'||!m.watched)out=C.episodeState(out,m.watched?'replay':'watched',now);
  out.trackingStatus='completed';out.started=true;if(total)out.progressCurrent=total;
 }
 return out;
}
function finish(m,now=Date.now(),replay=false){
 const repeated=replay||applicable(m)&&m.watched&&status(m)!=='completed';
 let out={...C.episodeState(m,repeated?'replay':'watched',now),started:true};
 if(applicable(m)){out.trackingStatus='completed';if(m.progressTotal>0)out.progressCurrent=m.progressTotal;}
 return out;
}
function restart(m){if(!applicable(m))throw Error('Reprise réservée aux jeux et livres.');return {...m,trackingStatus:'active',started:true,progressCurrent:0};}
function clear(m){return {...C.episodeState(m,'unwatch'),trackingStatus:applicable(m)?'planned':m.trackingStatus,started:false,progressCurrent:0};}
function label(m){return ({planned:m.kind==='book'?'À lire':'À jouer',active:m.kind==='book'?'Lecture en cours':'En cours',paused:'En pause',abandoned:'Abandonné',completed:m.kind==='book'?'Parcours lu':'Parcours terminé'})[status(m)];}
return {STATES,applicable,status,integer,update,finish,restart,clear,label};
});
