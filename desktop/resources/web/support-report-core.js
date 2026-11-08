/* The shared report is a whitelist of technical counters; no raw log or error string is copied. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.FGSupportReport=api;})(globalThis,function(){
 'use strict';
 const VERSION='0.38.0';
 function count(x){return Array.isArray(x)?x.length:0;}
 function build({state={},sync={},logs=[],online=false,recovery=false,readOnly=false}={}){
  const entries=Array.isArray(logs)?logs:[];
  return Object.freeze({format:'franchiseguide-support-v1',version:VERSION,platform:'web-pc',
   mediaCount:count(state.media),episodeCount:count(state.episodes),queuedCount:count(sync.queue),syncErrorCount:count(sync.errors),
   errorCount:entries.filter(x=>x&&x.level==='error').length,warningCount:entries.filter(x=>x&&x.level==='warn').length,
   browserOnline:online===true,recoveryOnly:recovery===true,readOnly:readOnly===true,
   rawMessagesIncluded:false,libraryIncluded:false,accountsIncluded:false});
 }
 return {VERSION,build};
});
