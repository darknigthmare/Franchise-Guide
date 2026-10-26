/* Portable protection v1: native PBKDF2-HMAC-SHA256 + AES-256-GCM.
 * This encrypts exported bytes, NOT localStorage, existing snapshots, or running memory.
 * Layout (big-endian): magic[8], iterations:u32, plainLength:u32, salt[16], iv[12], reserved[4], cipher+tag[16].
 * Entire 48-byte header is authenticated as AAD. No custom cryptographic primitive.
 */
(function(root,factory){const api=factory(root.crypto||(typeof module==='object'&&module.exports?require('node:crypto').webcrypto:null));if(typeof module==='object'&&module.exports)module.exports=api;else root.FGProtected=api;})(globalThis,function(provider){
'use strict';
const MAGIC=new Uint8Array([70,71,80,82,79,84,48,49]),HEADER=48,TAG=16,ITERATIONS=600000,MAX_PLAIN=32*1024*1024,MAX_FILE=MAX_PLAIN+HEADER+TAG;
const UTF8=new TextEncoder();let busy=false;
function fail(message,code){const e=Error(message);e.code=code;throw e;}
function available(){return !!provider?.subtle&&typeof provider.getRandomValues==='function';}
function requireCrypto(){if(!available())fail('Protection indisponible sur ce navigateur. Utilise HTTPS ou le serveur local. Aucun export en clair automatique.','NO_CRYPTO');}
function cancelled(signal){if(signal?.aborted){const e=Error('Opération annulée. Aucun fichier appliqué.');e.name='AbortError';throw e;}}
function bytes(value){if(value instanceof Uint8Array)return value;if(value instanceof ArrayBuffer)return new Uint8Array(value);if(ArrayBuffer.isView(value))return new Uint8Array(value.buffer,value.byteOffset,value.byteLength);fail('Données binaires attendues.','FORMAT');}
function wellFormed(text){if(typeof text!=='string')return false;for(let i=0;i<text.length;i++){const x=text.charCodeAt(i);if(x>=0xD800&&x<=0xDBFF){const y=text.charCodeAt(++i);if(!(y>=0xDC00&&y<=0xDFFF))return false;}else if(x>=0xDC00&&x<=0xDFFF)return false;}return true;}
function passwordBytes(text){
 if(!wellFormed(text))fail('Phrase secrète Unicode invalide.','PASSWORD');
 const count=Array.from(text).length;
 if(count<12||count>256||/[\u0000-\u001f\u007f]/u.test(text)||!text.replace(/[\u0020\u0085\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]/gu,''))fail('Choisis de 12 à 256 caractères, pas uniquement des espaces et sans caractères de contrôle. Les espaces et accents sont significatifs.','PASSWORD');
 const out=UTF8.encode(text);if(out.length>1024){out.fill(0);fail('Phrase secrète supérieure à 1 024 octets UTF-8.','PASSWORD');}return out;
}
function isProtected(value){const b=bytes(value);return b.length>=8&&MAGIC.every((v,i)=>b[i]===v);}
function inspect(value){const b=bytes(value);if(b.length<HEADER+TAG+1||b.length>MAX_FILE||!isProtected(b))fail('Fichier protégé inconnu, tronqué ou trop grand.','FORMAT');
 const view=new DataView(b.buffer,b.byteOffset,b.byteLength),rounds=view.getUint32(8),size=view.getUint32(12);
 if(rounds!==ITERATIONS||view.getUint32(44)!==0||size<1||size>MAX_PLAIN||b.length!==HEADER+size+TAG)fail('En-tête ou taille du fichier protégé invalide. Aucun déchiffrement lancé.','FORMAT');
 return {version:1,iterations:rounds,plainBytes:size,fileBytes:b.length};
}
async function derive(pwd,salt,usage,signal){const raw=await provider.subtle.importKey('raw',pwd,'PBKDF2',false,['deriveKey']);cancelled(signal);const key=await provider.subtle.deriveKey({name:'PBKDF2',salt,iterations:ITERATIONS,hash:'SHA-256'},raw,{name:'AES-GCM',length:256},false,[usage]);cancelled(signal);return key;}
async function protect(value,password,{signal}={}){
 requireCrypto();cancelled(signal);if(busy)fail('Un calcul de protection est encore en cours. Attends sa fin avant de recommencer.','BUSY');
 const input=bytes(value);if(input.length<1||input.length>MAX_PLAIN)fail('Sauvegarde vide ou supérieure à 32 Mio.','SIZE');const pwd=passwordBytes(password),plain=new Uint8Array(input);busy=true;
 try{const header=new Uint8Array(HEADER);header.set(MAGIC);const v=new DataView(header.buffer);v.setUint32(8,ITERATIONS);v.setUint32(12,plain.length);provider.getRandomValues(header.subarray(16,32));provider.getRandomValues(header.subarray(32,44));
  const key=await derive(pwd,header.subarray(16,32),'encrypt',signal);const cipher=new Uint8Array(await provider.subtle.encrypt({name:'AES-GCM',iv:header.subarray(32,44),additionalData:header,tagLength:128},key,plain));
  try{cancelled(signal);const result=new Uint8Array(HEADER+cipher.length);result.set(header);result.set(cipher,HEADER);return result;}finally{cipher.fill(0);}
 }finally{plain.fill(0);pwd.fill(0);busy=false;}
}
async function unprotect(value,password,{signal}={}){
 requireCrypto();cancelled(signal);if(busy)fail('Un calcul de protection est encore en cours. Attends sa fin avant de recommencer.','BUSY');inspect(value);
 const input=new Uint8Array(bytes(value)),pwd=passwordBytes(password);busy=true;
 try{const header=input.subarray(0,HEADER),key=await derive(pwd,header.subarray(16,32),'decrypt',signal);let plain;
  try{plain=new Uint8Array(await provider.subtle.decrypt({name:'AES-GCM',iv:header.subarray(32,44),additionalData:header,tagLength:128},key,input.subarray(HEADER)));}catch(e){cancelled(signal);fail('Mot de passe incorrect ou fichier altéré. Aucun contenu importé.','AUTH');}
  try{cancelled(signal);return plain;}catch(e){plain.fill(0);throw e;}
 }finally{input.fill(0);pwd.fill(0);busy=false;}
}
function decode(value){try{return new TextDecoder('utf-8',{fatal:true}).decode(bytes(value));}catch{fail('Le contenu déchiffré n’est pas un texte UTF-8 valide.','UTF8');}}
return {HEADER,TAG,ITERATIONS,MAX_PLAIN,MAX_FILE,available,isProtected,inspect,passwordBytes,protect,unprotect,decode};
});
