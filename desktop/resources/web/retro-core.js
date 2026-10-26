/* RetroAchievements read-only domain. Public profile association is not an authenticated SSO session.
 * Sources: api-docs.retroachievements.org/v1/get-user-{profile,completion-progress}.html
 * and RetroAchievements/RAWeb public/API/API_GetGameInfoAndUserProgress.php.
 * The API key belongs in the local server only; no credential is accepted or returned here. */
(function(root,factory){const api=factory(typeof module==='object'&&module.exports?require('./gaming-core.js'):root.FGGaming);if(typeof module==='object'&&module.exports)module.exports=api;else root.FGRetro=api;})(typeof globalThis==='object'?globalThis:this,function(G){
'use strict';
const MAX_GAMES=20000,MAX_PAGE=500,MAX_TIME=8640000000000000;
const AWARDS=['mastered','completed','beaten-hardcore','beaten-softcore'];
const TYPES=['progression','win_condition','missable'];
const obj=x=>!!x&&typeof x==='object'&&!Array.isArray(x);
function fail(message){throw Error(message);}
function integer(x,name,max=MAX_GAMES,optional=false){if(x==null&&optional)return null;if(!Number.isSafeInteger(x)||x<0||x>max)fail(name+' invalide.');return x;}
function label(x,name,max=250){if(typeof x!=='string'||!x.trim()||x.length>max||/[\x00-\x08\x0b\x0c\x0e-\x1f]/.test(x))fail(name+' invalide.');return x.trim();}
function ulid(x){if(typeof x!=='string'||!/^[0-7][0-9A-HJKMNP-TV-Z]{25}$/.test(x))fail('Identifiant stable RetroAchievements (ULID) invalide.');return x;}
function numericId(x,name='ID de jeu RetroAchievements'){if(typeof x!=='number'||!Number.isSafeInteger(x)||x<1||x>4294967295)fail(name+' invalide.');return String(x);}
function stamp(x,name='Date de lecture',optional=false){return integer(x,name,MAX_TIME,optional);}
function date(x,name,limit=MAX_TIME){
 if(x==null||x==='')return null;
 // RA legacy achievement dates are UTC without a suffix. Calendar validation must precede parsing.
 if(typeof x!=='string')fail(name+' invalide.');
 const m=/^(\d{4})-(\d{2})-(\d{2})([ T])(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,6}))?(Z|[+-]\d{2}:\d{2})?$/.exec(x);
 if(!m||(m[4]==='T'&&!m[9]))fail(name+' invalide.');
 const [year,month,day,hour,minute,second]=[m[1],m[2],m[3],m[5],m[6],m[7]].map(Number);
 const days=[31,(year%4===0&&(year%100!==0||year%400===0))?29:28,31,30,31,30,31,31,30,31,30,31];
 if(year<1970||month<1||month>12||day<1||day>days[month-1]||hour>23||minute>59||second>59)fail(name+' invalide.');
 const zone=m[9]||'Z';if(zone!=='Z'&&(Number(zone.slice(1,3))>14||Number(zone.slice(4,6))>59||(Number(zone.slice(1,3))===14&&zone.slice(4,6)!=='00')))fail(name+' invalide.');
 const at=Date.parse(`${m[1]}-${m[2]}-${m[3]}T${m[5]}:${m[6]}:${m[7]}.${(m[8]||'').padEnd(3,'0').slice(0,3)}${zone}`);
 if(!Number.isSafeInteger(at)||at<0||at>limit)fail(name+' invalide.');return at;
}
function imageUrl(raw){
 if(typeof raw!=='string'||!raw||raw.length>2000)return null;
 try{const u=new URL(raw,'https://retroachievements.org/');if(u.protocol!=='https:'||u.username||u.password||u.port||!['retroachievements.org','media.retroachievements.org'].includes(u.hostname)||u.search||u.hash)return null;if(!/^\/(?:Images|UserPic|Badge)\/[A-Za-z0-9_.-]+\.(?:png|jpe?g|webp)$/i.test(u.pathname))return null;return u.href;}catch{return null;}
}
function cleanProfile(p){
 if(!obj(p))fail('Profil RetroAchievements invalide.');
 const id=ulid(p.ulid),username=G.accountExternal('retro',p.username);
 return {ulid:id,username,label:label(p.label||username,'Nom du profil',120),avatarUrl:imageUrl(p.avatarUrl),fetchedAt:stamp(p.fetchedAt)};
}
function profile(bundle,expectedUser){
 if(!obj(bundle)||bundle.provider!=='retro'||!obj(bundle.profile))fail('Profil RetroAchievements indisponible.');
 const raw=bundle.profile,id=ulid(raw.ULID),username=G.accountExternal('retro',raw.User);
 if(bundle.user!==id)fail('Le profil reçu ne correspond pas à l’identifiant RetroAchievements annoncé.');
 if(expectedUser!=null){
  const expected=G.accountExternal('retro',expectedUser);
  if(/^[0-7][0-9A-HJKMNP-TV-Z]{25}$/.test(expected)?expected!==id:expected.toLowerCase()!==username.toLowerCase())fail('Le profil reçu appartient à un autre compte RetroAchievements.');
 }
 return cleanProfile({ulid:id,username,label:username,avatarUrl:raw.UserPic,fetchedAt:bundle.fetchedAt});
}
function award(x){if(x==null)return null;if(!AWARDS.includes(x))fail('Récompense RetroAchievements inconnue.');return x;}
function cleanGame(g){
 if(!obj(g))fail('Jeu RetroAchievements invalide.');
 const out={gameId:G.gameId(g.gameId),title:label(g.title,'Titre de jeu'),consoleId:G.gameId(g.consoleId),consoleName:label(g.consoleName,'Console',80),iconUrl:imageUrl(g.iconUrl),
  maxPossible:integer(g.maxPossible,'Nombre de succès',G.MAX_ACHIEVEMENTS),numAwarded:integer(g.numAwarded,'Nombre de succès obtenus',G.MAX_ACHIEVEMENTS),numAwardedHardcore:integer(g.numAwardedHardcore,'Nombre de succès Hardcore',G.MAX_ACHIEVEMENTS),
  mostRecentAwardedAt:stamp(g.mostRecentAwardedAt,'Date du dernier succès',true),highestAwardKind:award(g.highestAwardKind),highestAwardAt:stamp(g.highestAwardAt,'Date de récompense',true)};
 if(out.numAwarded>out.maxPossible||out.numAwardedHardcore>out.numAwarded)fail('Progression RetroAchievements incohérente.');
 if(out.highestAwardAt!=null&&out.highestAwardKind==null)fail('Date sans récompense RetroAchievements.');
 return out;
}
function gameRow(raw){
 if(!obj(raw))fail('Jeu RetroAchievements invalide.');
 return cleanGame({gameId:numericId(raw.GameID),title:raw.Title,consoleId:numericId(raw.ConsoleID,'ID de console'),consoleName:raw.ConsoleName,iconUrl:raw.ImageIcon,
  maxPossible:raw.MaxPossible,numAwarded:raw.NumAwarded,numAwardedHardcore:raw.NumAwardedHardcore,
  mostRecentAwardedAt:date(raw.MostRecentAwardedDate,'Date du dernier succès'),highestAwardKind:raw.HighestAwardKind,highestAwardAt:date(raw.HighestAwardDate,'Date de récompense')});
}
function checkPage(p){
 if(!obj(p)||!Array.isArray(p.games))fail('Page RetroAchievements invalide.');
 const out={user:ulid(p.user),offset:integer(p.offset,'Offset'),count:integer(p.count,'Taille de page',MAX_PAGE),returnedCount:integer(p.returnedCount,'Nombre de résultats',MAX_PAGE),total:integer(p.total,'Total des jeux'),games:[],fetchedAt:stamp(p.fetchedAt)};
 if(out.count<1||out.offset>out.total||out.returnedCount!==p.games.length||out.returnedCount!==Math.min(out.count,out.total-out.offset))fail('Page RetroAchievements incomplète ou pagination incohérente.');
 const ids=new Set();out.games=p.games.map(g=>{const row=cleanGame(g);if(ids.has(row.gameId))fail('ID de jeu RetroAchievements en double.');ids.add(row.gameId);return row;});
 return out;
}
function page(bundle,expectedUlid,offset,count=100){
 const user=ulid(expectedUlid);if(!obj(bundle)||bundle.provider!=='retro'||bundle.user!==user||!obj(bundle.library)||!Array.isArray(bundle.library.Results))fail('Bibliothèque RetroAchievements indisponible ou reçue pour un autre profil.');
 if(bundle.library.Results.length>MAX_PAGE||bundle.library.Results.length!==bundle.library.Count)fail('Page RetroAchievements trop volumineuse ou incomplète.');
 return checkPage({user,offset,count,returnedCount:bundle.library.Count,total:bundle.library.Total,games:bundle.library.Results.map(gameRow),fetchedAt:bundle.fetchedAt});
}
function library(pages,rawProfile){
 const p=cleanProfile(rawProfile);if(!Array.isArray(pages)||pages.length<1||pages.length>MAX_GAMES+1)fail('Toutes les pages RetroAchievements sont nécessaires.');
 const all=pages.map(checkPage).sort((a,b)=>a.offset-b.offset),total=all[0].total,games=[],ids=new Set();let nextOffset=0,fetchedAt=p.fetchedAt;
 for(let i=0;i<all.length;i++){
  const part=all[i];if(part.user!==p.ulid)fail('Une page appartient à un autre profil RetroAchievements.');
  if(part.total!==total||part.offset!==nextOffset||(i>0&&part.returnedCount===0))fail('La bibliothèque RetroAchievements a changé ou une page manque. Relance la lecture.');
  for(const g of part.games){if(ids.has(g.gameId))fail('Un jeu apparaît dans plusieurs pages RetroAchievements.');ids.add(g.gameId);games.push(g);}
  nextOffset+=part.returnedCount;fetchedAt=Math.max(fetchedAt,part.fetchedAt);
 }
 if(nextOffset!==total)fail('Bibliothèque RetroAchievements partielle. Aucun import appliqué.');
 return {user:p.ulid,ulid:p.ulid,profile:p,games,total,fetchedAt,empty:total===0,complete:true};
}
function cleanLibrary(value){
 if(!obj(value)||value.complete!==true||!Array.isArray(value.games))fail('Une bibliothèque RetroAchievements complète et validée est nécessaire.');
 if(value.games.length>MAX_GAMES||value.games.length!==value.total)fail('Bibliothèque RetroAchievements trop volumineuse ou incomplète.');
 const p=cleanProfile(value.profile),user=ulid(value.user),total=integer(value.total,'Total des jeux'),games=value.games.map(cleanGame);
 if(user!==p.ulid||(value.ulid!=null&&value.ulid!==user)||games.length!==total)fail('Bibliothèque RetroAchievements incomplète ou profil incohérent.');
 if(new Set(games.map(g=>g.gameId)).size!==games.length)fail('ID de jeu RetroAchievements en double.');
 return {user,profile:p,games,total,fetchedAt:stamp(value.fetchedAt),empty:total===0};
}
function plan(state,value,{autoImport,autoSync}={}){
 const data=cleanLibrary(value);if(!obj(state)||!Array.isArray(state.media))fail('Bibliothèque locale invalide.');
 for(const flag of [autoImport,autoSync])if(flag!=null&&typeof flag!=='boolean')fail('Consentement RetroAchievements invalide.');
 const next=G.copy(state);next.gaming=G.validateState(next.gaming,next.media);
 let account=next.gaming.accounts.find(a=>a.provider==='retro'&&a.externalId===data.user);
 if(!account){account=G.normalizeAccount({id:'retro-account-'+data.user,provider:'retro',externalId:data.user,username:data.profile.username,label:data.profile.label});next.gaming.accounts.push(account);}
 if(account.label===account.externalId||account.label===account.username)account.label=data.profile.label;
 account.username=data.profile.username;account.avatarUrl=data.profile.avatarUrl||account.avatarUrl||null;account.lastLibrarySyncAt=data.fetchedAt;
 if(autoImport!=null)account.autoImport=autoImport;if(autoSync!=null)account.autoSync=autoSync;
 const report={total:data.total,added:0,updated:0,listsCreated:0,noStats:0,deferred:0,conflicts:[],empty:data.empty};
 const byId=new Map(next.media.map(m=>[m.id,m])),index=new Map(),boundKeys=new Set();
 const boundKey=(mediaId,gameId)=>JSON.stringify([mediaId,account.id,gameId]);
 function addIndex(id,m){if(!index.has(id))index.set(id,new Map());index.get(id).set(m.id,m);}
 for(const m of next.media)if(String(m.kind).toLowerCase()==='game'){
  if(m.source==='retro'&&m.sourceId)addIndex(G.gameId(String(m.sourceId)),m);
  if(m.retroGameId)addIndex(G.gameId(String(m.retroGameId)),m);
 }
 for(const s of next.gaming.sets){if(s.provider!=='retro')continue;const m=byId.get(s.mediaId);if(m)addIndex(s.externalGameId,m);if(s.accountId===account.id)boundKeys.add(boundKey(s.mediaId,s.externalGameId));}
 for(const game of data.games){
  const candidates=index.get(game.gameId);if(candidates?.size>1){report.conflicts.push({gameId:game.gameId,title:game.title,reason:'Plusieurs fiches liées au même ID RetroAchievements. Choisis la fiche à conserver.'});continue;}
  let m=candidates?.values().next().value;
  if(m&&((m.source==='retro'&&String(m.sourceId)!==game.gameId)||(m.retroGameId&&String(m.retroGameId)!==game.gameId))){report.conflicts.push({gameId:game.gameId,title:game.title,reason:'Liaison RetroAchievements contradictoire.'});continue;}
  if(!m){
   const id='retro-game-'+game.gameId;if(byId.has(id))fail('Identifiant local déjà utilisé par une autre fiche.');
   m={id,kind:'game',source:'retro',sourceId:game.gameId,title:game.title,added:true,collected:false,posterUrl:game.iconUrl,externalUrl:'https://retroachievements.org/game/'+game.gameId,
    overview:'Fiche issue du suivi RetroAchievements. La présence d’un relevé ne prouve pas la possession du jeu.',catalogComplete:true,catalogUpdatedAt:data.fetchedAt,
    watched:false,started:false,plays:0,watchHistory:[],genres:[],ratings:[],credits:[],listIds:[],platforms:[game.consoleName],addedAt:data.fetchedAt};
   next.media.push(m);byId.set(id,m);addIndex(game.gameId,m);report.added++;
  }else{report.updated++;if(!m.posterUrl)m.posterUrl=game.iconUrl;}
  // Never turn a RA "beaten/mastered" award into local campaign completion, collection ownership, or a platinum.
  const key=boundKey(m.id,game.gameId);if(boundKeys.has(key))continue;
  if(next.gaming.sets.length>=2000){report.deferred++;continue;}
  next.gaming.sets.push(G.normalizeSet({id:'retro-set-'+data.user+'-'+game.gameId,mediaId:m.id,provider:'retro',accountId:account.id,externalGameId:game.gameId,title:m.title,platform:game.consoleName,edition:'RetroAchievements',
   groups:[{id:'core',name:'Succès RetroAchievements',kind:'unknown'}],achievements:[],completeCatalog:false,source:'retro-api'}));
  boundKeys.add(key);report.listsCreated++;if(game.maxPossible===0)report.noStats++;
 }
 if(next.media.length>50000)fail('Maximum 50 000 médias par bibliothèque.');
 next.gaming=G.validateState(next.gaming,next.media);return {state:next,report,accountId:account.id};
}
function achievements(bundle,binding,now=Date.now()){
 if(!obj(binding))fail('Association RetroAchievements invalide.');const user=ulid(binding.accountExternalId),gameId=G.gameId(binding.externalGameId),current=stamp(now,'Date d’actualisation');
 if(binding.provider!=null&&binding.provider!=='retro')fail('Association vers une autre plateforme.');
 if(!obj(bundle)||bundle.provider!=='retro'||bundle.user!==user||bundle.gameId!==gameId||!obj(bundle.game))fail('Relevé RetroAchievements indisponible ou reçu pour un autre jeu/profil.');
 const observed=stamp(bundle.fetchedAt),raw=bundle.game;if(observed>current+300000)fail('Date de lecture RetroAchievements située dans le futur.');
 if(numericId(raw.ID)!==gameId)fail('RetroAchievements a retourné un autre jeu.');
 numericId(raw.ConsoleID,'ID de console');const platform=label(raw.ConsoleName,'Console',80),title=label(raw.Title,'Titre de jeu');
 if(raw.ParentGameID!=null)numericId(raw.ParentGameID,'ID du jeu parent');
 const total=integer(raw.NumAchievements,'Nombre de succès',G.MAX_ACHIEVEMENTS),won=integer(raw.NumAwardedToUser,'Succès obtenus',G.MAX_ACHIEVEMENTS),hard=integer(raw.NumAwardedToUserHardcore,'Succès Hardcore obtenus',G.MAX_ACHIEVEMENTS);
 if(!obj(raw.Achievements)||Object.keys(raw.Achievements).length!==total||won>total||hard>won)fail('Catalogue ou progression RetroAchievements incomplet. Ancien relevé conservé.');
 const players=integer(raw.NumDistinctPlayers,'Nombre de joueurs',100000000,true),seen=new Set();
 const rows=Object.entries(raw.Achievements).map(([key,a])=>{
  if(!obj(a))fail('Succès RetroAchievements invalide.');const id=numericId(a.ID,'ID de succès');if(key!==id||seen.has(id))fail('ID de succès RetroAchievements incohérent ou en double.');seen.add(id);
  const earnedAt=date(a.DateEarned,'Date d’obtention',current+300000),hardcoreAt=date(a.DateEarnedHardcore,'Date Hardcore',current+300000);
  if(hardcoreAt!=null&&earnedAt==null)fail('Déblocage Hardcore sans déblocage normal dans le relevé.');
  const type=a.Type??a.type??null;if(type!=null&&!TYPES.includes(type))fail('Type de succès RetroAchievements inconnu.');
  if(a.Type!=null&&a.type!=null&&a.Type!==a.type)fail('Types de succès RetroAchievements contradictoires.');
  const awarded=integer(a.NumAwarded,'Nombre de déblocages',100000000,true),awardedHard=integer(a.NumAwardedHardcore,'Nombre de déblocages Hardcore',100000000,true);
  if(awarded!=null&&awardedHard!=null&&awardedHard>awarded)fail('Comptage de déblocages RetroAchievements incohérent.');
  const rarity=players>0&&awarded!=null&&awarded<=players?{percent:100*awarded/players,source:'RetroAchievements · joueurs ayant obtenu un succès'}:null;
  const badge=typeof a.BadgeName==='string'&&/^[A-Za-z0-9_-]{1,100}$/.test(a.BadgeName)?a.BadgeName:null;
  return {id,title:label(a.Title,'Titre de succès',300),description:a.Description??'',groupId:'core',grade:'achievement',hidden:false,
   points:integer(a.Points,'Points RetroAchievements',1000000,true),rarity,earned:earnedAt!=null,earnedAt,hardcoreEarned:hardcoreAt!=null,hardcoreEarnedAt:hardcoreAt,
   iconUrl:badge?'https://media.retroachievements.org/Badge/'+badge+'.png':null,lockedIconUrl:badge?'https://media.retroachievements.org/Badge/'+badge+'_lock.png':null,
   missable:type==='missable'?true:null,order:integer(a.DisplayOrder,'Ordre du succès',100000000,true)};
 });
 if(rows.filter(a=>a.earned).length!==won||rows.filter(a=>a.hardcoreEarned).length!==hard)fail('Les dates et les totaux de progression RetroAchievements ne correspondent pas. Ancien relevé conservé.');
 rows.sort((a,b)=>(a.order??Number.MAX_SAFE_INTEGER)-(b.order??Number.MAX_SAFE_INTEGER)||Number(a.id)-Number(b.id));
 return G.normalizeSet({...binding,provider:'retro',externalGameId:gameId,title,platform:binding.platform||platform,edition:binding.edition??'RetroAchievements',
  groups:[{id:'core',name:raw.ParentGameID!=null?'Sous-ensemble RetroAchievements':'Succès RetroAchievements',kind:'unknown'}],achievements:rows,
  completeCatalog:total>0,catalogStatus:total===0?'none-exposed':'unknown',source:'retro-api',observedAt:observed,lastSyncAt:current,syncError:''});
}
return {profile,page,library,plan,achievements,MAX_GAMES,MAX_PAGE};
});
