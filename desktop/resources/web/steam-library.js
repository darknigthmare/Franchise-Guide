/* Steam library import rules. Read-only account data; never infer completion or match by title. */
(function(root,factory){const api=factory(typeof module==='object'&&module.exports?require('./gaming-core.js'):root.FGGaming);if(typeof module==='object'&&module.exports)module.exports=api;else root.FGSteam=api;})(typeof globalThis==='object'?globalThis:this,function(G){
'use strict';
const obj=x=>x&&typeof x==='object'&&!Array.isArray(x);
function integer(x,label,max=10000000000){if(x==null)return null;if(!Number.isSafeInteger(x)||x<0||x>max)throw Error(label+' invalide.');return x;}
function label(x,fallback,max=250){if(x==null||x==='')return fallback;if(typeof x!=='string'||x.length>max||/[\x00-\x08\x0b\x0c\x0e-\x1f]/.test(x))throw Error('Texte Steam invalide.');return x;}
function normalize(bundle,steamid){
 G.steamId(steamid);if(!obj(bundle)||bundle.provider!=='steam'||bundle.steamid!==steamid)throw Error('Bibliothèque reçue pour un autre compte Steam.');
 const raw=bundle.library?.response,count=raw?.game_count;
 if(!obj(raw)||!Number.isSafeInteger(count)||count<0||count>20000)throw Error('Bibliothèque non accessible : vérifie la confidentialité des détails de jeux Steam. Aucune donnée locale effacée.');
 const rows=raw.games??(count===0?[]:null);if(!Array.isArray(rows)||rows.length!==count)throw Error('Bibliothèque Steam incomplète. Aucun import partiel appliqué.');
 const seen=new Set();const games=rows.map(g=>{
  if(!obj(g))throw Error('Jeu Steam invalide.');const appId=G.gameId(String(g.appid));if(!Number.isSafeInteger(g.appid)||seen.has(appId))throw Error('App ID Steam invalide ou en double.');seen.add(appId);
  const hash=typeof g.img_icon_url==='string'&&/^[a-fA-F0-9]{40}$/.test(g.img_icon_url)?g.img_icon_url:null;
  const last=integer(g.rtime_last_played,'Dernière partie',8640000000),hasStats=g.has_community_visible_stats;
  if(hasStats!=null&&typeof hasStats!=='boolean')throw Error('Indicateur de statistiques Steam invalide.');
  return {appId,title:label(g.name,'Jeu Steam '+appId),iconUrl:hash?`https://steamcdn-a.akamaihd.net/steamcommunity/public/images/apps/${appId}/${hash}.jpg`:null,
   playtimeMinutes:integer(g.playtime_forever,'Temps de jeu'),lastPlayedAt:last?last*1000:null,hasStats:hasStats??null};
 });
 let profile={label:steamid,avatarUrl:null};const players=bundle.profile?.response?.players;
 if(Array.isArray(players)){
  const p=players.find(p=>p?.steamid===steamid);if(p){const avatar=G.url(p.avatarfull||p.avatarmedium||p.avatar);profile={label:label(p.personaname,steamid,120),avatarUrl:avatar};}
 }
 return {steamid,games,profile,fetchedAt:integer(bundle.fetchedAt,'Date de lecture',8640000000000000)||Date.now(),empty:count===0};
}
function metadata(raw){
 if(raw==null)return null;if(!obj(raw))throw Error('Métadonnées Steam invalides.');const appId=G.gameId(raw.appId),accounts=raw.accounts??{};
 if(!obj(accounts)||Object.keys(accounts).length>100)throw Error('Comptes Steam du jeu invalides.');const clean={};
 for(const [id,value] of Object.entries(accounts)){G.steamId(id);if(!obj(value))throw Error('Relevé de jeu Steam invalide.');clean[id]={playtimeMinutes:integer(value.playtimeMinutes,'Temps de jeu'),lastPlayedAt:integer(value.lastPlayedAt,'Date',8640000000000000)};}
 if(raw.hasStats!=null&&typeof raw.hasStats!=='boolean')throw Error('Disponibilité des statistiques Steam invalide.');
 return {appId,accounts:clean,hasStats:raw.hasStats??null};
}
function plan(state,library,{autoImport,autoSync}={}){
 if(!library||!Array.isArray(library.games))throw Error('Bibliothèque normalisée requise.');
 const next=G.copy(state);next.gaming=G.validateState(next.gaming,next.media);
 let account=next.gaming.accounts.find(a=>a.provider==='steam'&&a.externalId===library.steamid);
 if(!account){account=G.normalizeAccount({id:'steam-account-'+library.steamid,provider:'steam',externalId:library.steamid,label:library.profile.label});next.gaming.accounts.push(account);}
 if(account.label===account.externalId)account.label=library.profile.label;
 account.avatarUrl=library.profile.avatarUrl||account.avatarUrl||null;
 if(autoImport!=null)account.autoImport=!!autoImport;if(autoSync!=null)account.autoSync=!!autoSync;
 account.lastLibrarySyncAt=library.fetchedAt;
 const report={total:library.games.length,added:0,updated:0,listsCreated:0,noStats:0,deferred:0,conflicts:[],empty:library.empty};
 const ids=new Map(),mediaById=new Map(next.media.map(m=>[m.id,m]));
 const bindingKey=(mediaId,accountId,appId)=>JSON.stringify([mediaId,accountId,appId]);
 const boundKeys=new Set(next.gaming.sets.filter(s=>s.provider==='steam').map(s=>bindingKey(s.mediaId,s.accountId,s.externalGameId)));
 function index(appid,m){if(!ids.has(appid))ids.set(appid,new Map());ids.get(appid).set(m.id,m);}
 for(const m of next.media){if(m.kind!=='game')continue;if(m.steam?.appId)index(metadata(m.steam).appId,m);if(m.steamAppId!=null)index(G.gameId(String(m.steamAppId)),m);if(m.source==='steam'&&m.sourceId)index(G.gameId(m.sourceId),m);}
 for(const s of next.gaming.sets){const m=mediaById.get(s.mediaId);if(s.provider==='steam'&&m)index(s.externalGameId,m);}
 for(const game of library.games){
  const candidates=ids.get(game.appId);if(candidates?.size>1){report.conflicts.push({appId:game.appId,title:game.title,reason:'Plusieurs fiches liées au même App ID ; choisis la fiche à conserver.'});continue;}
  let m=candidates?.values().next().value;
  if(m&&((m.steam?.appId&&m.steam.appId!==game.appId)||(m.steamAppId!=null&&String(m.steamAppId)!==game.appId)||(m.source==='steam'&&m.sourceId&&m.sourceId!==game.appId))){report.conflicts.push({appId:game.appId,title:game.title,reason:'Liaison Steam contradictoire.'});continue;}
  if(!m){
   const id='steam-game-'+game.appId;if(mediaById.has(id))throw Error('Identifiant local déjà utilisé par une autre fiche.');
   m={id,kind:'game',source:'steam',sourceId:game.appId,title:game.title,added:true,collected:true,posterUrl:game.iconUrl,externalUrl:'https://store.steampowered.com/app/'+game.appId+'/',
    overview:'Fiche issue de la bibliothèque Steam : titre, icône et temps de jeu. Les autres métadonnées ne sont pas fournies par cette lecture.',catalogComplete:true,catalogUpdatedAt:library.fetchedAt,
    watched:false,started:false,plays:0,watchHistory:[],genres:[],ratings:[],credits:[],listIds:[],platforms:['PC (Steam)'],addedAt:library.fetchedAt};
   next.media.push(m);mediaById.set(id,m);index(game.appId,m);report.added++;
  }else{report.updated++;if(!m.posterUrl)m.posterUrl=game.iconUrl;}
  const prior=metadata(m.steam)||{appId:game.appId,accounts:{},hasStats:null};
  const previous=prior.accounts[library.steamid]||{};
  prior.accounts[library.steamid]={playtimeMinutes:game.playtimeMinutes??previous.playtimeMinutes??null,lastPlayedAt:game.lastPlayedAt??previous.lastPlayedAt??null};
  prior.hasStats=game.hasStats; m.steam=metadata(prior);
  // Watching/completion/notes/favorites/list memberships are never inferred from playtime.
  const key=bindingKey(m.id,account.id,game.appId),bound=boundKeys.has(key);
  if(bound)continue;if(game.hasStats===false){report.noStats++;continue;}
  if(next.gaming.sets.length>=2000){report.deferred++;continue;}
  next.gaming.sets.push(G.normalizeSet({id:'steam-set-'+library.steamid+'-'+game.appId,mediaId:m.id,provider:'steam',accountId:account.id,externalGameId:game.appId,title:m.title,platform:'PC (Steam)',edition:'Steam',
   groups:[{id:'unknown',name:'Base / DLC non distingués par Steam',kind:'unknown'}],achievements:[],completeCatalog:false,source:'steam-api',requiresSteamSignIn:true}));boundKeys.add(key);report.listsCreated++;
 }
 next.gaming=G.validateState(next.gaming,next.media);
 return {state:next,report,accountId:account.id};
}
return {normalize,metadata,plan};
});
