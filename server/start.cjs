#!/usr/bin/env node
/* Personal local Web server, not a public multi-tenant SaaS. Node 22+. */
'use strict';
const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const {createSteamBridge}=require('./steam-bridge.cjs');
const {createSteamAuth}=require('./steam-auth.cjs');
const {createRetroBridge}=require('./retro-bridge.cjs');
const {createAutoServices}=require('./auto-services.cjs');
const {ambiguousHeaders}=require('./local-request.cjs');
function start({port=47632,root=fs.existsSync(path.resolve(__dirname,'../web/index.html'))?path.resolve(__dirname,'../web'):path.resolve(__dirname,'..'),bridge=createSteamBridge(),auth=createSteamAuth({lookup:bridge.lookup}),retro=createRetroBridge(),services=createAutoServices()}={}){
 const mime={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.json':'application/json','.webmanifest':'application/manifest+json','.png':'image/png','.wav':'audio/wav'};
 const allowed=new Set(fs.readdirSync(root).filter(f=>mime[path.extname(f)]));
 const server=http.createServer(async(req,res)=>{
  try{
   const host=req.headers.host,port=req.socket.localPort;if(ambiguousHeaders(req)||!['127.0.0.1:'+port,'localhost:'+port].includes(host)){res.writeHead(403);res.end();return;}
   const url=new URL(req.url,'http://'+host);if(await services(req,res,url))return;if(await auth(req,res,url))return;if(await retro(req,res,url))return;if(await bridge(req,res,url))return;
   if(!['GET','HEAD'].includes(req.method)){res.writeHead(405);res.end();return;}
   const file=url.pathname==='/'?'index.html':url.pathname.slice(1);if(!allowed.has(file)){res.writeHead(404);res.end();return;}
   const bytes=fs.readFileSync(path.join(root,file));res.writeHead(200,{'Content-Type':mime[path.extname(file)],'Content-Length':bytes.length,'Cache-Control':'no-cache','X-Content-Type-Options':'nosniff','X-Frame-Options':'DENY','Referrer-Policy':'no-referrer','Permissions-Policy':'camera=(), microphone=(), geolocation=()'});res.end(req.method==='HEAD'?'':bytes);
  }catch{if(!res.headersSent)res.writeHead(500,{'Content-Type':'text/plain'});res.end('Erreur locale.');}
 });
 server.listen(port,'127.0.0.1');return server;
}
if(require.main===module){const port=Number(process.env.FG_PORT||47632);if(!Number.isInteger(port)||port<1024||port>65535)throw Error('FG_PORT invalide.');const server=start({port});server.on('listening',()=>console.log('FranchiseGuide Web 0.36 · http://127.0.0.1:'+port+' · usage personnel local'));server.on('error',()=>{console.error('Port indisponible. Ferme l’ancien lanceur.');process.exitCode=1;});}
module.exports={start};
