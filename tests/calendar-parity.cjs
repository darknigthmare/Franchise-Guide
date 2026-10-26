'use strict';
const A=require('../web/activity-core.js'),fs=require('node:fs'),assert=require('node:assert/strict');
const event={key:'movie:clé *',uid:A.uid('movie:clé *'),kind:'movie',date:'2026-10-06',title:'Film très long 日本語 🐈 '.repeat(15)+'; virgule, antislash\\\nSecond line',description:'Description\nHoraire inconnu.'};
const expected=A.calendar([event],{now:Date.parse('2026-10-06T12:00:00Z')});const actual=fs.readFileSync(process.argv[2]);assert.deepEqual(actual,Buffer.from(expected,'utf8'));
console.log('PASS Web/Kotlin ICS output byte-for-byte parity (Unicode, escapes, stable UID, UTF-8 folding). This is not an external calendar import.');
