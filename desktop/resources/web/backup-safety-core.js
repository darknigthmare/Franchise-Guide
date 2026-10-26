/* External backup reminder state. A proposed download is not considered verified. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.FGBackupSafety=api;})(globalThis,function(){
'use strict';const VERSION=1,MAX_BYTES=8192,DAY=86400000;
const object=x=>!!x&&typeof x==='object'&&!Array.isArray(x);const int=(v,l)=>{if(!Number.isSafeInteger(v)||v<0||v>8640000000000000)throw Error(l+' invalide.');return v;};
function empty(){return {version:VERSION,proposedAt:null,verifiedAt:null,format:null,fileName:null,snoozeUntil:null};}
function validate(v){if(!object(v)||v.version!==VERSION)throw Error('État de sauvegarde externe invalide.');const x={...empty(),...v};for(const k of ['proposedAt','verifiedAt','snoozeUntil'])if(x[k]!=null)int(x[k],k);for(const k of ['format','fileName'])if(x[k]!=null&&(typeof x[k]!=='string'||x[k].length>500||/[\u0000-\u001f\u007f]/.test(x[k])))throw Error('Métadonnée de sauvegarde invalide.');if(x.verifiedAt!=null&&x.proposedAt!=null&&x.verifiedAt<x.proposedAt){}return x;}
function decode(raw){if(!raw)return empty();if(typeof raw!=='string'||new TextEncoder().encode(raw).length>MAX_BYTES)throw Error('Rappel de sauvegarde illisible.');return validate(JSON.parse(raw));}
function encode(v){const raw=JSON.stringify(validate(v));if(new TextEncoder().encode(raw).length>MAX_BYTES)throw Error('Rappel trop volumineux.');return raw;}
function proposed(v,format,fileName,now=Date.now()){v=validate(v);int(now,'Date');if(!['json','protected'].includes(format)||typeof fileName!=='string'||!fileName)throw Error('Export invalide.');return validate({...v,proposedAt:now,format,fileName:fileName.slice(0,500),snoozeUntil:null});}
function verified(v,now=Date.now()){v=validate(v);int(now,'Date');if(v.proposedAt==null)throw Error('Aucun fichier récemment proposé à vérifier.');return validate({...v,verifiedAt:now,snoozeUntil:null});}
function snooze(v,days=7,now=Date.now()){v=validate(v);if(!Number.isInteger(days)||days<1||days>90)throw Error('Délai de rappel invalide.');return validate({...v,snoozeUntil:now+days*DAY});}
function status(v,now=Date.now(),dueDays=14){v=validate(v);int(now,'Date');if(!Number.isInteger(dueDays)||dueDays<1||dueDays>365)throw Error('Période de rappel invalide.');const pending=v.proposedAt!=null&&(v.verifiedAt==null||v.proposedAt>v.verifiedAt),age=v.verifiedAt==null?null:Math.max(0,now-v.verifiedAt),due=v.verifiedAt==null||age>=dueDays*DAY;return {...v,pending,due:due&&!(v.snoozeUntil!=null&&v.snoozeUntil>now),ageDays:age==null?null:Math.floor(age/DAY),dueAt:v.verifiedAt==null?null:v.verifiedAt+dueDays*DAY,snoozed:v.snoozeUntil!=null&&v.snoozeUntil>now};}
return {VERSION,DAY,empty,validate,decode,encode,proposed,verified,snooze,status};
});
