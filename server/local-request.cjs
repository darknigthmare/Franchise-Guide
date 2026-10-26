'use strict';

// Node may join duplicate fields or silently select the first value. Security
// decisions must reject that ambiguity before using the normalized headers.
const SINGLE_HEADERS=new Set(['host','origin','sec-fetch-site','authorization','x-franchiseguide-client','x-franchiseguide-services']);
function ambiguousHeaders(req){
 const seen=new Set();
 for(let i=0;i<(req.rawHeaders||[]).length;i+=2){
  const name=req.rawHeaders[i].toLowerCase();if(!SINGLE_HEADERS.has(name))continue;
  if(seen.has(name))return true;seen.add(name);
 }
 return false;
}
module.exports={ambiguousHeaders};
