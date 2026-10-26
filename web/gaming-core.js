/* FranchiseGuide gaming domain. No provider credentials, DOM, or network. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.FGGaming=api;})(typeof globalThis==='object'?globalThis:this,function(){
'use strict';
const PROVIDERS=Object.freeze({steam:'Steam',psn:'PlayStation / PSNProfiles',xbox:'Xbox',retro:'RetroAchievements',epic:'Epic Games',gog:'GOG',manual:'Liste personnelle'});
const GRADES=['bronze','silver','gold','platinum','achievement','mythic'];
const GROUP_KINDS=['base','dlc','unknown'];
const MAX_ACHIEVEMENTS=5000;
const record=x=>!!x&&typeof x==='object'&&!Array.isArray(x);
const copy=x=>JSON.parse(JSON.stringify(x));
function fail(message){throw Error(message);}
function text(x,name,max=200,optional=false){if(x==null&&optional)return '';if(typeof x!=='string'||(!optional&&!x.trim())||x.length>max||/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(x))fail(name+' invalide.');return x.trim();}
function id(x,name='Identifiant'){return text(x,name,160);}
function num(x,name,max=100000000,integer=false){if(x==null||x==='')return null;if(typeof x!=='number'||!Number.isFinite(x)||x<0||x>max||(integer&&!Number.isSafeInteger(x)))fail(name+' invalide.');return x;}
function bool(x,name){if(x==null)return null;if(typeof x!=='boolean')fail(name+' doit être vrai, faux ou inconnu.');return x;}
function time(x,name='Date'){if(x==null||x==='')return null;return num(x,name,8640000000000000,true);}
function url(x){if(!x)return null;try{const u=new URL(x);if(u.protocol==='https:'&&!u.username&&!u.password&&u.href.length<=2000)return u.href;}catch{}return null;}
function provider(x){if(!Object.hasOwn(PROVIDERS,x))fail('Plateforme de succès inconnue.');return x;}
function steamId(x){const s=id(x,'SteamID64');if(!/^\d{17}$/.test(s)||BigInt(s)<=0n||BigInt(s)>18446744073709551615n)fail('Un SteamID64 de 17 chiffres est nécessaire (pas un pseudo ni un nombre JavaScript).');return s;}
function gameId(x){const s=id(x,'ID du jeu');if(!/^[1-9]\d{0,9}$/.test(s)||Number(s)>4294967295)fail('ID de jeu numérique invalide.');return s;}
function accountExternal(p,x){x=id(x,'Compte');if(p==='steam')return steamId(x);if(p==='psn'&&!/^[A-Za-z0-9_-]{3,32}$/.test(x))fail('Identifiant PSN / PSNProfiles invalide.');if(p==='retro'&&!/^[A-Za-z0-9_-]{2,64}$/.test(x))fail('Identifiant RetroAchievements invalide.');return x;}
function accountUrl(a){if(a.provider==='steam')return 'https://steamcommunity.com/profiles/'+a.externalId;if(a.provider==='psn')return 'https://psnprofiles.com/'+encodeURIComponent(a.externalId);if(a.provider==='retro')return 'https://retroachievements.org/user/'+encodeURIComponent(a.username||a.externalId);return null;}
function normalizeAccount(a){if(!record(a))fail('Compte de jeu invalide.');const p=provider(a.provider);return {id:id(a.id),provider:p,externalId:accountExternal(p,a.externalId),label:text(a.label||a.externalId,'Libellé du compte',120),autoSync:bool(a.autoSync,'Synchronisation automatique')===true,autoImport:['steam','retro'].includes(p)&&bool(a.autoImport,'Import automatique des jeux')===true,lastLibrarySyncAt:time(a.lastLibrarySyncAt),avatarUrl:url(a.avatarUrl),...(p==='retro'&&a.username!=null?{username:accountExternal('retro',a.username)}:{})};}
function accountKey(a){return a.provider+':'+(['psn'].includes(a.provider)?a.externalId.toLowerCase():a.externalId);}
function normalizeAchievement(a){
 if(!record(a))fail('Succès invalide.');const earned=bool(a.earned,'État du succès'),at=time(a.earnedAt);
 if(at!=null&&earned!==true)fail('Une date d’obtention exige un succès obtenu.');
 if(a.hardcoreEarnedAt!=null&&a.hardcoreEarned!==true)fail('Une date hardcore exige un succès hardcore obtenu.');
 const grade=a.grade||'achievement';if(!GRADES.includes(grade))fail('Grade inconnu.');
 let rarity=null;if(a.rarity!=null){if(!record(a.rarity))fail('Rareté invalide.');rarity={percent:num(a.rarity.percent,'Pourcentage de rareté',100),source:text(a.rarity.source,'Source de rareté',100)};if(rarity.percent===null)rarity=null;}
 const current=num(a.progressCurrent,'Progression'),target=num(a.progressTarget,'Objectif');if(current!=null&&target!=null&&(target===0||current>target))fail('Progression supérieure à l’objectif.');
 return {id:id(a.id),title:text(a.title,'Titre du succès',300),description:text(a.description??'','Description',5000,true),groupId:id(a.groupId||'unknown'),grade,
 iconUrl:url(a.iconUrl),lockedIconUrl:url(a.lockedIconUrl),hidden:bool(a.hidden,'Secret')===true,points:num(a.points,'Points',1000000,true),rarity,earned,earnedAt:at,
 hardcoreEarned:bool(a.hardcoreEarned,'État hardcore'),hardcoreEarnedAt:time(a.hardcoreEarnedAt),progressCurrent:current,progressTarget:target,
 localDone:bool(a.localDone,'Case personnelle')===true,pinned:bool(a.pinned,'Épingle')===true,note:text(a.note??'','Note',3000,true),online:bool(a.online,'En ligne'),missable:bool(a.missable,'Manquable'),unobtainable:bool(a.unobtainable,'Indisponible'),guideUrl:url(a.guideUrl)};
}
function normalizeSet(s){
 if(!record(s))fail('Liste de succès invalide.');const p=provider(s.provider);const externalGameId=id(s.externalGameId,'ID externe du jeu');if(['steam','retro'].includes(p))gameId(externalGameId);
 const groups=s.groups??[{id:'unknown',name:'Groupe non précisé',kind:'unknown'}];if(!Array.isArray(groups)||groups.length>300)fail('Groupes invalides.');
 const groupIds=new Set();const normalizedGroups=groups.map(g=>{if(!record(g))fail('Groupe invalide.');const key=id(g.id);if(groupIds.has(key))fail('Groupe en double.');groupIds.add(key);if(!GROUP_KINDS.includes(g.kind))fail('Type de groupe invalide.');return {id:key,name:text(g.name,'Nom du groupe',180),kind:g.kind};});
 if(!Array.isArray(s.achievements)||s.achievements.length>MAX_ACHIEVEMENTS)fail('Une liste de 0 à 5 000 succès est requise.');
 const ids=new Set(),achievements=s.achievements.map(a=>{const out=normalizeAchievement(a);if(ids.has(out.id))fail('Succès en double : '+out.id);ids.add(out.id);if(!groupIds.has(out.groupId))fail('Succès sans groupe : '+out.id);return out;});
 const out={id:id(s.id),mediaId:id(s.mediaId,'Jeu local'),provider:p,platform:text(s.platform||PROVIDERS[p],'Plateforme',80),accountId:s.accountId==null?null:id(s.accountId),externalGameId,
 title:text(s.title,'Titre de liste',250),edition:text(s.edition??'','Édition',120,true),groups:normalizedGroups,achievements,
 requiresSteamSignIn:s.provider==='steam'&&s.requiresSteamSignIn===true,catalogStatus:s.catalogStatus==='none-exposed'?'none-exposed':'unknown',completeCatalog:s.completeCatalog===true,source:['manual','file-import','steam-api','retro-api'].includes(s.source)?s.source:'file-import',observedAt:time(s.observedAt),lastSyncAt:time(s.lastSyncAt),syncError:text(s.syncError??'','Erreur de synchronisation',300,true)};
 if(out.source==='manual'&&out.achievements.some(a=>a.earned===true||a.hardcoreEarned===true))fail('Une liste personnelle utilise la checklist, pas un déblocage officiel.');
 return out;
}
function setIdentity(s){return JSON.stringify([s.mediaId,s.provider,s.platform,s.accountId,s.externalGameId,s.edition]);}
function validateState(g,media){
 if(g==null)return {version:1,accounts:[],sets:[]};
 if(!record(g)||g.version!==1||!Array.isArray(g.accounts)||!Array.isArray(g.sets)||g.accounts.length>100||g.sets.length>2000)fail('Module trophées non pris en charge.');
 const accounts=g.accounts.map(normalizeAccount),ids=new Set(),names=new Set();for(const a of accounts){if(ids.has(a.id)||names.has(accountKey(a)))fail('Compte de jeu en double.');ids.add(a.id);names.add(accountKey(a));}
 const games=media?new Set(media.filter(m=>String(m.kind).toLowerCase()==='game').map(m=>m.id)):null;
 const byAccount=new Map(accounts.map(a=>[a.id,a])),keys=new Set(),setIds=new Set();let count=0;
 const sets=g.sets.map(s=>{const out=normalizeSet(s);if(games&&!games.has(out.mediaId))fail('Liste de trophées sans jeu.');if(out.accountId){const a=byAccount.get(out.accountId);if(!a||a.provider!==out.provider)fail('Compte et plateforme de liste incompatibles.');}
  if(setIds.has(out.id)||keys.has(setIdentity(out)))fail('Liste de trophées en double.');setIds.add(out.id);keys.add(setIdentity(out));count+=out.achievements.length;if(count>100000)fail('Maximum 100 000 succès par bibliothèque.');return out;});
 return {version:1,accounts,sets};
}
function progress(s,groupId){
 const rows=groupId?s.achievements.filter(a=>a.groupId===groupId):s.achievements;const total=rows.length,earned=rows.filter(a=>a.earned===true).length,unknown=rows.filter(a=>a.earned===null).length;
 const pts=rows.filter(a=>a.points!==null),allKnownPoints=pts.length===total&&total>0;
 return {total,earned,locked:rows.filter(a=>a.earned===false).length,unknown,localDone:rows.filter(a=>a.localDone).length,
 percent:total?Math.round(100*earned/total):null,completed:total>0&&s.completeCatalog&&earned===total,
 platinum:rows.some(a=>a.grade==='platinum'&&a.earned===true),hardcore:rows.filter(a=>a.hardcoreEarned===true).length,
 points:allKnownPoints?rows.filter(a=>a.earned===true).reduce((n,a)=>n+a.points,0):null,totalPoints:allKnownPoints?rows.reduce((n,a)=>n+a.points,0):null,
 grades:Object.fromEntries(GRADES.map(g=>[g,rows.filter(a=>a.grade===g&&a.earned===true).length]))};
}
function filterAchievements(s,v={}){
 const q=String(v.query||'').toLocaleLowerCase('fr');let rows=s.achievements.filter(a=>{
  const concealed=a.hidden&&a.earned!==true&&!v.revealed?.includes(a.id);
  return (!v.group||v.group==='all'||a.groupId===v.group)&&(!v.grade||v.grade==='all'||a.grade===v.grade)&&
   (!v.state||v.state==='all'||(v.state==='earned'?a.earned===true:v.state==='locked'?a.earned===false:v.state==='unknown'?a.earned===null:v.state==='pinned'?a.pinned:v.state==='remaining'?a.earned!==true:v.state==='hardcore-earned'?a.hardcoreEarned===true:v.state==='hardcore-remaining'?a.hardcoreEarned!==true:v.state==='hardcore-unknown'?a.hardcoreEarned===null:!a.localDone))&&
   (!q||(!concealed&&(a.title+' '+a.description).toLocaleLowerCase('fr').includes(q)));
 });
 const sort=v.sort||'source';if(sort==='source')return rows;
 const order={platinum:0,gold:1,silver:2,bronze:3,mythic:4,achievement:5};
 const nullable=(x,y,desc=false)=>x==null?(y==null?0:1):y==null?-1:(desc?y-x:x-y);
 rows=[...rows].sort((a,b)=>sort==='date'?nullable(a.earnedAt,b.earnedAt,true):sort==='rarity'?nullable(a.rarity?.percent,b.rarity?.percent):sort==='grade'?order[a.grade]-order[b.grade]:sort==='title'?(a.hidden&&a.earned!==true?'Succès secret':a.title).localeCompare(b.hidden&&b.earned!==true?'Succès secret':b.title,'fr'):0);return rows;
}
function mergeSet(previous,fresh){
 fresh=normalizeSet(fresh);if(!previous)return fresh;if(setIdentity(previous)!==setIdentity(fresh))fail('Une synchronisation ne peut pas changer de compte, de jeu ou d’édition.');
 const old=new Map(previous.achievements.map(a=>[a.id,a]));
 // Missing old entries imply a partial snapshot. Never delete earned state as a side effect.
 const freshIds=new Set(fresh.achievements.map(a=>a.id));
 if(previous.achievements.some(a=>!freshIds.has(a.id)))fail('Catalogue incomplet ou modifié : certains succès enregistrés manquent. Import séparé nécessaire.');
 return {...fresh,id:previous.id,achievements:fresh.achievements.map(a=>{const p=old.get(a.id);return p?{...a,localDone:p.localDone,pinned:p.pinned,note:p.note,online:a.online??p.online,missable:a.missable??p.missable,unobtainable:a.unobtainable??p.unobtainable,guideUrl:p.guideUrl||a.guideUrl}:a;})};
}
function parseImport(raw,binding){
 if(typeof raw!=='string'||raw.length>8*1024*1024)fail('Fichier trop volumineux (8 Mio maximum).');
 const x=JSON.parse(raw.replace(/^\uFEFF/,''));if(!record(x)||x.format!=='franchiseguide-achievements'||x.version!==1||!record(x.set))fail('Format attendu : franchiseguide-achievements, version 1. Aucun export natif PSNProfiles n’est supposé.');
 if(x.accountUsername!=null)accountExternal('retro',x.accountUsername);
 if(binding.provider&&x.set.provider!==binding.provider)fail('Le fichier provient d’une autre plateforme.');
 if(binding.accountExternalId&&x.accountExternalId!==binding.accountExternalId)fail('Le fichier appartient à un autre compte.');
 if(binding.externalGameId&&x.set.externalGameId!==binding.externalGameId)fail('Le fichier concerne un autre jeu.');
 // File imports can never masquerade as a live API sync, and cannot request automatic sync.
 return normalizeSet({...x.set,id:binding.id,mediaId:binding.mediaId,accountId:binding.accountId||null,source:'file-import',lastSyncAt:null,syncError:''});
}
function exportSet(s,account){return {format:'franchiseguide-achievements',version:1,accountExternalId:account?.externalId??null,...(account?.provider==='retro'&&account.username!=null?{accountUsername:accountExternal('retro',account.username)}:{}),set:normalizeSet(s)};}
function normalizeSteam(bundle,binding,now=Date.now()){
 if(!record(bundle)||bundle.provider!=='steam'||String(bundle.appid)!==binding.externalGameId||String(bundle.steamid)!==binding.accountExternalId)fail('Réponse Steam pour un autre jeu ou compte.');
 const schema=bundle.schema?.game,player=bundle.player?.playerstats;
 if(bundle.noAchievements===true&&record(schema)&&typeof schema.gameName==='string'&&schema.gameName&&(!schema.availableGameStats?.achievements||Array.isArray(schema.availableGameStats.achievements)&&!schema.availableGameStats.achievements.length))return normalizeSet({...binding,provider:'steam',platform:binding.platform||'PC (Steam)',groups:[{id:'unknown',name:'Base / DLC non distingués par Steam',kind:'unknown'}],achievements:[],source:'steam-api',completeCatalog:false,catalogStatus:'none-exposed',observedAt:now,lastSyncAt:now,syncError:''});
 if(!record(schema)||!Array.isArray(schema.availableGameStats?.achievements))fail('Steam ne fournit pas de catalogue de succès exploitable pour ce jeu.');
 if(!player||player.success!==true||!Array.isArray(player.achievements))fail('Succès Steam non accessibles : compte privé, jeu indisponible ou réponse incomplète. Anciennes données conservées.');
 if(player.steamID!=null&&String(player.steamID)!==binding.accountExternalId)fail('Steam a retourné un autre joueur.');
 const definitions=schema.availableGameStats.achievements,states=new Map();
 for(const a of player.achievements){if(!record(a)||typeof a.apiname!=='string'||states.has(a.apiname)||![0,1].includes(a.achieved))fail('État Steam invalide ou en double.');states.set(a.apiname,a);}
 if(definitions.length!==states.size)fail('Réponse Steam incomplète : le catalogue et les états ne correspondent pas.');
 const global=new Map((bundle.rarity?.achievementpercentages?.achievements||[]).filter(a=>record(a)&&typeof a.name==='string').map(a=>[a.name,a.percent]));
 const achievements=definitions.map(d=>{const p=states.get(d.name);if(!p)fail('État de succès Steam manquant.');let rp=global.get(d.name);rp=Number.isFinite(Number(rp))&&rp!=null?Number(rp):null;
  return {id:d.name,title:d.displayName||d.name,description:d.description||'',groupId:'unknown',grade:'achievement',iconUrl:d.icon,lockedIconUrl:d.icongray,hidden:d.hidden===1||d.hidden===true,earned:p.achieved===1,
   earnedAt:p.achieved===1&&p.unlocktime>0?Number(p.unlocktime)*1000:null,rarity:rp!=null?{percent:rp,source:'Steam · joueurs du jeu'}:null};});
 return normalizeSet({...binding,provider:'steam',title:schema.gameName||player.gameName||binding.title,platform:binding.platform||'PC (Steam)',groups:[{id:'unknown',name:'Base / DLC non distingués par Steam',kind:'unknown'}],achievements,source:'steam-api',completeCatalog:true,observedAt:now,lastSyncAt:now,syncError:''});
}
function statistics(g){const sets=g?.sets||[],p=sets.map(s=>progress(s));return {sets:sets.length,achievements:p.reduce((n,s)=>n+s.total,0),earned:p.reduce((n,s)=>n+s.earned,0),unknown:p.reduce((n,s)=>n+s.unknown,0),platinums:p.filter(s=>s.platinum).length,complete:p.filter(s=>s.completed).length,localDone:p.reduce((n,s)=>n+s.localDone,0),byProvider:Object.entries(PROVIDERS).map(([provider,label])=>({provider,label,sets:sets.filter(s=>s.provider===provider).length,earned:sets.filter(s=>s.provider===provider).reduce((n,s)=>n+progress(s).earned,0)})).filter(p=>p.sets)};}
return {PROVIDERS,GRADES,GROUP_KINDS,MAX_ACHIEVEMENTS,normalizeAccount,normalizeAchievement,normalizeSet,accountKey,accountUrl,accountExternal,steamId,gameId,setIdentity,validateState,progress,filterAchievements,mergeSet,parseImport,exportSet,normalizeSteam,statistics,url,copy};
});
