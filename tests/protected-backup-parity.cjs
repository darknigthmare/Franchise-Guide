'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),P=require('../web/protected-backup-core.js');
(async()=>{const root=process.argv[2],plain=fs.readFileSync(path.join(root,'expected.json')),passwords=['Une longue phrase ASCII','Les étoiles 🎮 du soir 2026','  cafe\u0301 spécial 🐈  '];
 for(let i=0;i<passwords.length;i++){const native=fs.readFileSync(path.join(root,'native-'+i+'.fgbackup'));assert.deepEqual(Buffer.from(await P.unprotect(native,passwords[i])),plain);fs.writeFileSync(path.join(root,'web-'+i+'.fgbackup'),await P.protect(plain,passwords[i]));}
 console.log('Protected backup interop: three JVM files decrypted by WebCrypto byte-for-byte; three WebCrypto outputs emitted.');
})().catch(e=>{console.error(e);process.exitCode=1});
