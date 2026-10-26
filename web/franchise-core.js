/* V0.19: explicit primary franchise classification and known suggestions. No provider/network/storage. */
(function(root,factory){const core=typeof module==='object'&&module.exports?require('./core.js'):root.FGCore;const api=factory(core);if(typeof module==='object'&&module.exports)module.exports=api;else root.FGFranchises=api;})(globalThis,function(C){
'use strict';
const builtIns=[{"id":"alien","name":"Alien","queryTerms":["Alien","Aliens"],"aliases":["Prometheus","Covenant","Xenomorph","AVP"],"description":"Films, séries, jeux et livres de l'univers Alien.","sortOrder":[],"custom":false},{"id":"predator","name":"Predator","queryTerms":["Predator","Prey"],"aliases":["Yautja","AVP","Alien vs Predator"],"description":"Films, jeux, livres et extensions de l'univers Predator.","sortOrder":[],"custom":false},{"id":"stargate","name":"Stargate","queryTerms":["Stargate"],"aliases":["SG-1","Atlantis","Universe"],"description":"Films, séries, jeux et romans Stargate.","sortOrder":[],"custom":false},{"id":"star-trek","name":"Star Trek","queryTerms":["Star Trek"],"aliases":["Deep Space Nine","Voyager","Picard","Enterprise"],"description":"Séries, films, jeux et romans Star Trek.","sortOrder":[],"custom":false},{"id":"star-wars","name":"Star Wars","queryTerms":["Star Wars"],"aliases":["Mandalorian","Andor","Ahsoka","Clone Wars","Jedi"],"description":"Films, séries, jeux et livres Star Wars.","sortOrder":[],"custom":false},{"id":"marvel","name":"Marvel","queryTerms":["Marvel"],"aliases":["Avengers","Daredevil","Loki","Spider-Man","X-Men","Guardians of the Galaxy"],"description":"Films, séries, jeux et livres Marvel.","sortOrder":[],"custom":false},{"id":"dc","name":"DC","queryTerms":["DC Comics"],"aliases":["Batman","Superman","Wonder Woman","Justice League","Aquaman"],"description":"Films, séries, jeux et livres DC.","sortOrder":[],"custom":false},{"id":"harry-potter","name":"Wizarding World","queryTerms":["Harry Potter"],"aliases":["Fantastic Beasts","Hogwarts","Wizarding World"],"description":"Films, séries, jeux et livres du Wizarding World.","sortOrder":[],"custom":false},{"id":"middle-earth","name":"Terre du Milieu","queryTerms":["Lord of the Rings","The Hobbit"],"aliases":["Middle-earth","Rings of Power","Tolkien"],"description":"Films, séries, jeux et livres liés à la Terre du Milieu.","sortOrder":[],"custom":false},{"id":"witcher","name":"The Witcher","queryTerms":["The Witcher"],"aliases":["Wiedźmin","Geralt"],"description":"Séries, films, jeux et livres The Witcher.","sortOrder":[],"custom":false},{"id":"resident-evil","name":"Resident Evil","queryTerms":["Resident Evil"],"aliases":["Biohazard"],"description":"Jeux, films, séries et livres Resident Evil.","sortOrder":[],"custom":false},{"id":"mass-effect","name":"Mass Effect","queryTerms":["Mass Effect"],"aliases":["Andromeda"],"description":"Jeux, romans, comics et adaptations Mass Effect.","sortOrder":[],"custom":false},{"id":"dune","name":"Dune","queryTerms":["Dune"],"aliases":["Arrakis","House Atreides"],"description":"Films, séries, jeux et romans de l'univers Dune.","sortOrder":[],"custom":false},{"id":"terminator","name":"Terminator","queryTerms":["Terminator"],"aliases":["Skynet","Sarah Connor"],"description":"Films, séries, jeux et romans Terminator.","sortOrder":[],"custom":false},{"id":"matrix","name":"Matrix","queryTerms":["The Matrix"],"aliases":["Matrix Reloaded","Matrix Revolutions","Matrix Resurrections"],"description":"Films, jeux et œuvres de l'univers Matrix.","sortOrder":[],"custom":false},{"id":"jurassic","name":"Jurassic","queryTerms":["Jurassic Park","Jurassic World"],"aliases":["Jurassic"],"description":"Films, séries, jeux et livres Jurassic.","sortOrder":[],"custom":false},{"id":"monsterverse","name":"MonsterVerse","queryTerms":["Godzilla","Kong"],"aliases":["Monarch","MonsterVerse"],"description":"Films, séries, jeux et livres liés au MonsterVerse.","sortOrder":[],"custom":false},{"id":"transformers","name":"Transformers","queryTerms":["Transformers"],"aliases":["Autobot","Decepticon","Bumblebee"],"description":"Films, séries, jeux et livres Transformers.","sortOrder":[],"custom":false},{"id":"james-bond","name":"James Bond","queryTerms":["James Bond"],"aliases":["007","Bond"],"description":"Films, jeux et romans James Bond.","sortOrder":[],"custom":false},{"id":"doctor-who","name":"Doctor Who","queryTerms":["Doctor Who"],"aliases":["Torchwood","Dalek"],"description":"Séries, films, jeux et livres Doctor Who.","sortOrder":[],"custom":false},{"id":"fallout","name":"Fallout","queryTerms":["Fallout"],"aliases":["Vault-Tec","Vault Boy"],"description":"Jeux, série et livres de l'univers Fallout.","sortOrder":[],"custom":false},{"id":"last-of-us","name":"The Last of Us","queryTerms":["The Last of Us"],"aliases":["Last of Us"],"description":"Jeux, série et adaptations The Last of Us.","sortOrder":[],"custom":false},{"id":"assassins-creed","name":"Assassin's Creed","queryTerms":["Assassin's Creed"],"aliases":["Assassins Creed","Animus"],"description":"Jeux, films, séries et romans Assassin's Creed.","sortOrder":[],"custom":false},{"id":"halo","name":"Halo","queryTerms":["Halo"],"aliases":["Master Chief","Covenant"],"description":"Jeux, série, films et romans Halo.","sortOrder":[],"custom":false},{"id":"warcraft","name":"Warcraft","queryTerms":["Warcraft"],"aliases":["World of Warcraft","Azeroth"],"description":"Jeux, film et romans Warcraft.","sortOrder":[],"custom":false},{"id":"silent-hill","name":"Silent Hill","queryTerms":["Silent Hill"],"aliases":[],"description":"Jeux, films et œuvres Silent Hill.","sortOrder":[],"custom":false},{"id":"metal-gear","name":"Metal Gear","queryTerms":["Metal Gear"],"aliases":["Solid Snake","Big Boss"],"description":"Jeux et œuvres Metal Gear.","sortOrder":[],"custom":false},{"id":"half-life","name":"Half-Life","queryTerms":["Half-Life"],"aliases":["Half Life","Black Mesa","Alyx"],"description":"Jeux et œuvres de l'univers Half-Life.","sortOrder":[],"custom":false},{"id":"pokemon","name":"Pokémon","queryTerms":["Pokémon"],"aliases":["Pokemon","Pikachu"],"description":"Jeux, séries, films, mangas et livres Pokémon.","sortOrder":[],"custom":false},{"id":"dragon-ball","name":"Dragon Ball","queryTerms":["Dragon Ball"],"aliases":["DBZ","Dragon Ball Z","Dragon Ball Super"],"description":"Séries, films, jeux et mangas Dragon Ball.","sortOrder":[],"custom":false},{"id":"naruto","name":"Naruto","queryTerms":["Naruto"],"aliases":["Boruto","Shippuden"],"description":"Séries, films, jeux et mangas Naruto.","sortOrder":[],"custom":false},{"id":"final-fantasy","name":"Final Fantasy","queryTerms":["Final Fantasy"],"aliases":["FFVII","FF7"],"description":"Jeux, films et livres Final Fantasy.","sortOrder":[],"custom":false}];
const copy=x=>JSON.parse(JSON.stringify(x));
const norm=x=>String(x??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('fr').replace(/[^\p{L}\p{N}]+/gu,' ').trim();
const nameKey=x=>norm(x)||String(x??'').normalize('NFC').trim().toLocaleLowerCase('fr');
const assigned=m=>m?.franchise??m?.franchiseId??null;
const added=m=>m&&m.added!==false&&!m.suggestion&&!m.isSuggestion;
function text(value,name,max,empty=false){if(typeof value!=='string'||(!empty&&!value.trim())||value.length>max||/[\u0000-\u001f\u007f]/.test(value))throw Error(name+' invalide.');return value.trim();}
function validateDefinition(d){
 if(!d||typeof d!=='object'||Array.isArray(d))throw Error('Définition de franchise invalide.');
 const result={...copy(d),id:text(d.id,'Identifiant',250),name:text(d.name,'Nom',200),description:String(d.description??'').trim()};
 if(typeof (d.description??'')!=='string'||result.description.length>4000||/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(result.description))throw Error('Description invalide.');
 for(const key of ['queryTerms','aliases','sortOrder']){
  const list=d[key]??(key==='queryTerms'?[result.name.slice(0,160)]:[]);
  if(!Array.isArray(list)||list.length>(key==='sortOrder'?50000:100))throw Error(key+' invalide ou trop volumineux.');
  const values=list.map(v=>text(v,key,key==='sortOrder'?250:160));
  if(new Set(values).size!==values.length&&key==='sortOrder')throw Error('Un identifiant apparaît deux fois dans l’ordre personnel.');
  result[key]=[...new Set(values)];
 }
 result.custom=d.custom!==false;return result;
}
function definitions(s){
 const map=new Map(builtIns.map(d=>[d.id,copy(d)]));
 for(const raw of s.customFranchises||[]){const d=validateDefinition(raw);if(!map.has(d.id))map.set(d.id,d);}
 for(const m of [...(s.media||[]),...(s.suggestions||[])]){const id=assigned(m);if(typeof id==='string'&&id&&!map.has(id))map.set(id,{id,name:m.franchiseName||id,queryTerms:[],aliases:[],sortOrder:[],description:'Référence importée sans définition enregistrée.',custom:true,recovered:true});}
 return [...map.values()];
}
function matching(def,m){
 const title=' '+norm(m.title)+' ';
 return [...(def.queryTerms||[]),...(def.aliases||[])].some(t=>{const n=norm(t);return n&&title.includes(' '+n+' ');});
}
function effective(m,defs){
 const id=assigned(m);
 if(m.franchiseManual===true)return id;
 if(id)return id;
 return defs.find(d=>matching(d,m))?.id||null;
}
function sortItems(items,definition,mode='personal'){
 const rank=new Map((definition.sortOrder||[]).map((id,i)=>[id,i]));
 const cp=[...items];return cp.sort((a,b)=>{
  if(mode==='personal'){const x=rank.get(a.id),y=rank.get(b.id);if(x!==undefined||y!==undefined){const diff=(x??Infinity)-(y??Infinity);if(diff)return diff;}}
  if(mode==='kind'){const k=C.KINDS.indexOf(String(a.kind).toLowerCase())-C.KINDS.indexOf(String(b.kind).toLowerCase());if(k)return k;}
  if(mode!=='title'){const ay=Number.isInteger(a.year)?a.year:99999,by=Number.isInteger(b.year)?b.year:99999;if(ay!==by)return ay-by;}
  return String(a.title||'').localeCompare(String(b.title||''),'fr')||String(a.id).localeCompare(String(b.id));
 });
}
function groups(s,{includeEmpty=false}={}){
 const defs=definitions(s),map=new Map(defs.map(d=>[d.id,{id:d.id,name:d.name,definition:d,items:[],added:0,known:0,missing:0}]));
 const local=(s.media||[]).filter(added),seen=new Set();
 // Identities are never deduplicated using title alone. Owned entries take precedence over suggestions.
 for(const m of [...local,...(s.suggestions||[]).map(m=>({...m,added:false,suggestion:true}))]){
  const aliases=C.aliases(m);if(aliases.some(a=>seen.has(a)))continue;aliases.forEach(a=>seen.add(a));
  const g=map.get(effective(m,defs));if(!g)continue;g.items.push(m);g.known++;if(added(m))g.added++;else g.missing++;
 }
 return [...map.values()].filter(g=>g.known>0||includeEmpty&&g.definition.custom).map(g=>({...g,items:sortItems(g.items,g.definition)})).sort((a,b)=>b.added-a.added||a.name.localeCompare(b.name,'fr'));
}
function putDefinition(s,form,id=null){
 const defs=definitions(s),old=id?defs.find(d=>d.id===id):null;
 if(id&&!old)throw Error('Cette franchise a été retirée.');if(old&&!old.custom)throw Error('Les définitions intégrées ne sont pas modifiables. Crée un univers personnel.');
 const chosen=id||form.id;if(builtIns.some(d=>d.id===chosen))throw Error('Identifiant réservé à une franchise intégrée.');
 if(!id&&(s.customFranchises||[]).some(d=>d.id===chosen))throw Error('Identifiant déjà utilisé.');
 const d=validateDefinition({...old,...form,id:chosen,custom:true,sortOrder:old?.sortOrder||[]});delete d.recovered;
 if(defs.some(x=>x.id!==d.id&&nameKey(x.name)===nameKey(d.name)))throw Error('Ce nom de franchise existe déjà.');
 const next=copy(s);next.customFranchises=next.customFranchises||[];const idx=next.customFranchises.findIndex(f=>f.id===d.id);if(idx<0)next.customFranchises.push(d);else next.customFranchises[idx]=d;
 for(const m of [...next.media,...next.suggestions])if(assigned(m)===d.id)m.franchiseName=d.name;
 return next;
}
function deleteDefinition(s,id){
 const defs=definitions(s),d=defs.find(x=>x.id===id);if(!d?.custom)throw Error('Seule une franchise personnelle peut être supprimée.');
 const next=copy(s);next.customFranchises=(next.customFranchises||[]).filter(f=>f.id!==id);
 for(const m of [...next.media,...next.suggestions])if(effective(m,defs)===id){m.franchise=null;m.franchiseId=null;m.franchiseName=null;m.franchiseManual=true;}
 return next;
}
function assign(s,mediaId,id,automatic=false,source='media'){
 if(!['media','suggestions'].includes(source))throw Error('Source de classement invalide.');
 const defs=definitions(s);if(id!==null&&!defs.some(d=>d.id===id))throw Error('La franchise choisie n’existe plus.');
 const next=copy(s),m=next[source].find(m=>m.id===mediaId);if(!m||source==='media'&&!added(m))throw Error('Ce titre n’est plus disponible.');
 m.franchiseManual=!automatic;m.franchise=null;m.franchiseId=null;m.franchiseName=null;
 const chosen=automatic?effective(m,defs):id;const d=defs.find(d=>d.id===chosen);
 m.franchise=d?.id||null;m.franchiseId=m.franchise;m.franchiseName=d?.name||null;return next;
}
function move(s,id,itemId,target){
 const g=groups(s,{includeEmpty:true}).find(g=>g.id===id);if(!g?.definition.custom||g.definition.recovered)throw Error('Crée ou restaure une définition personnelle avant de définir son ordre.');
 if(!Number.isInteger(target)||target<1||target>g.items.length)throw Error('Position hors de la liste.');
 const ids=g.items.map(m=>m.id),index=ids.indexOf(itemId);if(index<0)throw Error('Le titre n’est plus dans cette franchise.');
 ids.splice(index,1);ids.splice(target-1,0,itemId);
 const next=copy(s);next.customFranchises.find(d=>d.id===id).sortOrder=ids;return next;
}
function suggest(s,id,raw){
 const d=definitions(s).find(d=>d.id===id);if(!d)throw Error('Cette franchise n’existe plus.');
 if(!raw||!C.KINDS.includes(String(raw.kind).toLowerCase()))throw Error('Suggestion invalide.');text(raw.title,'Titre',500);text(raw.id,'Identifiant titre',250);
 const current=(s.media||[]).filter(added).find(m=>C.sameMedia(m,raw));if(current)throw Error('Ce titre est déjà dans la bibliothèque. Classe-le depuis sa fiche.');
 if((s.suggestions||[]).some(m=>C.sameMedia(m,raw)))throw Error('Ce titre existe déjà parmi les suggestions.');
 const next=copy(s),candidate=copy(raw);
 // Search candidates are metadata only, not a watched or completed item, and no episode is created.
 for(const k of Object.keys(candidate))if(k.startsWith('_'))delete candidate[k];
 Object.assign(candidate,{kind:String(raw.kind).toLowerCase(),added:false,suggestion:true,isSuggestion:true,franchise:id,franchiseId:id,franchiseName:d.name,franchiseManual:true,watched:false,plays:0,watchHistory:[],lastWatchedAt:null,rating:null,userNote:null,listIds:[],favorite:false,hidden:false,collected:false,watchlist:false,notify:false,started:false,progressCurrent:0,progressTotal:0,progressUnit:'',trackingStatus:null,playtimeMinutes:null,personalPlatform:null,personalFormat:null,selectedEdition:null,steam:null,catalogComplete:false,catalogUpdatedAt:null});
 next.suggestions.push(candidate);return next;
}
function dismiss(s,id){const next=copy(s);if(!next.suggestions.some(x=>x.id===id))throw Error('Suggestion retirée.');next.suggestions=next.suggestions.filter(x=>x.id!==id);return next;}
function filter(items,query='',kind='all',state='all'){
 const q=norm(query).slice(0,250);return items.filter(m=>(kind==='all'||String(m.kind).toLowerCase()===kind)&&(state==='all'||(state==='added')===added(m))&&(!q||norm(m.title).includes(q)));
}
return {builtIns,norm,assigned,added,validateDefinition,definitions,matching,effective,groups,sortItems,putDefinition,deleteDefinition,assign,move,suggest,dismiss,filter};
});
