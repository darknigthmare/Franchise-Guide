/* V0.29: pure guards for supplier pagination, local matching and imported histories.
 * No network, no storage mutations, no automatic merging. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.FGReliability=api;})(globalThis,function(){
'use strict';
const record=v=>!!v&&typeof v==='object'&&!Array.isArray(v);
function requireThat(ok,message){if(!ok)throw Error(message);}
function fold(value){return String(value??'').normalize('NFKD').replace(/\p{M}/gu,'').toLocaleLowerCase('fr').replace(/\s+/g,' ').trim();}
function sourceId(source,value){let v=String(value??'').trim();if(source==='openlibrary'&&/^(?:\/works\/)?OL[1-9]\d{0,11}W$/.test(v))v='/works/'+v.split('/').pop();return v;}
function identities(m){const map=new Map();function add(k,v){v=sourceId(k,v);if(!v)return;if(!map.has(k))map.set(k,new Set());map.get(k).add(v);}
 for(const [k,f] of Object.entries({tmdb:'tmdbId',tvdb:'tvdbId',imdb:'imdbId',trakt:'traktId',rawg:'rawgId',openlibrary:'openLibraryId',tvmaze:'tvmazeId',itunes:'itunesId',freetogame:'freeToGameId',steam:'steamAppId',retro:'retroGameId'}))add(k,m?.[f]);
 if(m?.source&&m.source!=='seriesguide')add(m.source,m.sourceId);
 if(/^tvmaze-episode-\d+$/.test(m?.id||''))add('tvmaze-episode',m.id.slice(15));
 return map;
}
function conflicts(a,b){const x=identities(a),y=identities(b),out=[];for(const k of new Set([...x.keys(),...y.keys()])){const values=new Set([...(x.get(k)||[]),...(y.get(k)||[])]);if(values.size>1)out.push({field:k,values:[...values]});}return out;}
function episodeCompatible(a,b){if(!a||!b||a.showId!==b.showId||conflicts(a,b).length)return false;
 if(a.tmdbId&&a.tmdbId===b.tmdbId||a.tvdbId&&a.tvdbId===b.tvdbId)return true;
 if(/^tvmaze-episode-/.test(a.id||'')||/^tvmaze-episode-/.test(b.id||''))return a.id===b.id;
 return !a.seasonUnknown&&!b.seasonUnknown&&!a.numberUnknown&&!b.numberUnknown&&a.season===b.season&&(a.num??a.number)===(b.num??b.number);
}
function mergeHistory(a,b,{series=false}={}){const watched=!!(a?.watched||b?.watched),valid=t=>Number.isSafeInteger(t)&&t>0&&t<=8640000000000000;
 const known=x=>!x?.watched||x.skipped?[]:[...(Array.isArray(x.watchHistory)?x.watchHistory:[]),...(series?[]:[x.lastWatchedAt])].filter(valid);
 const history=[...new Set([...known(a),...known(b)])].sort((x,y)=>x-y);
 return {watched,watchHistory:watched?history:[],plays:watched?Math.max(1,a?.plays||0,b?.plays||0,history.length):0,lastWatchedAt:series?Math.max(a?.lastWatchedAt||0,b?.lastWatchedAt||0)||null:history.at(-1)||null};
}
function response(provider,data,page){requireThat(Number.isInteger(page)&&page>0&&page<=1000,'Page catalogue invalide.');
 requireThat(record(data),provider+' : réponse catalogue invalide.');let rows,more;
 if(provider==='tmdb'){
  requireThat(Array.isArray(data.results)&&data.results.length<=1000,'TMDb : tableau de résultats manquant ou trop grand.');
  requireThat(Number.isSafeInteger(data.total_pages)&&data.total_pages>=0,'TMDb : pagination absente ou invalide.');
  if(data.page!=null)requireThat(data.page===page,'TMDb : réponse pour une autre page.');rows=data.results;more=page<Math.min(500,data.total_pages);
 }else if(provider==='rawg'){
  requireThat(Array.isArray(data.results)&&data.results.length<=1000,'RAWG : tableau de résultats manquant ou trop grand.');
  requireThat(Object.hasOwn(data,'next')&&(data.next===null||typeof data.next==='string'),'RAWG : pagination absente ou invalide.');
  if(data.next){let u;try{u=new URL(data.next);}catch{}requireThat(u?.protocol==='https:'&&u.hostname==='api.rawg.io'&&u.pathname.replace(/\/$/,'')==='/api/games'&&!u.username&&!u.password,'RAWG : page suivante inattendue.');}
  rows=data.results;more=!!data.next&&page<1000;
 }else if(provider==='openlibrary'){
  requireThat(Array.isArray(data.docs)&&data.docs.length<=1000,'Open Library : tableau de résultats manquant ou trop grand.');
  const count=data.numFound??data.num_found;requireThat(Number.isSafeInteger(count)&&count>=0,'Open Library : nombre de résultats absent ou invalide.');
  if(data.numFound!=null&&data.num_found!=null)requireThat(data.numFound===data.num_found,'Open Library : compteurs contradictoires.');
  if(data.start!=null)requireThat(data.start===(page-1)*20,'Open Library : réponse pour une autre page.');rows=data.docs;more=count>page*20&&page<1000;
 }else{
  requireThat(Array.isArray(data.items)&&typeof data.more==='boolean',provider+' : réponse publique incomplète.');rows=data.items;more=data.more&&page<1000;
 }
 return {rows,more};
}
function validateItem(m){requireThat(record(m)&&['show','movie','game','book'].includes(m.kind)&&typeof m.title==='string'&&m.title.trim().length>0&&m.title.length<=1000&&m.id&&m.sourceId,'Référence catalogue sans identité ou titre valide.');
 if(['tmdb','rawg'].includes(m.source))requireThat(/^[1-9]\d*$/.test(String(m.sourceId))&&Number.isSafeInteger(Number(m.sourceId)),'Identifiant catalogue invalide.');return m;
}
function pager(ids){requireThat(Array.isArray(ids)&&new Set(ids).size===ids.length,'Liste de fournisseurs invalide.');return Object.fromEntries(ids.map(id=>[id,{page:0,more:true,error:null,retryAt:0}]));}
function requests(p,mode='more',now=Date.now()) {return Object.entries(p).filter(([,s])=>mode==='retry'?!!s.error&&s.retryAt<=now:!s.error&&s.more).map(([id,s])=>({id,page:s.page+1}));}
function settle(p,id,page,result,error=null,retryAt=0){const before=p[id];requireThat(before&&page===before.page+1,'Réponse de pagination obsolète.');return {...p,[id]:error?{...before,error:String(error),retryAt}:{page,more:result.more,error:null,retryAt:0}};}
return {fold,sourceId,identities,conflicts,episodeCompatible,mergeHistory,response,validateItem,pager,requests,settle};
});
