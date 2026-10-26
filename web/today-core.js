/* Read-only Today dashboard composition. */
(function(root,factory){const core=typeof module==='object'&&module.exports?require('./core.js'):root.FGCore;const activity=typeof module==='object'&&module.exports?require('./activity-core.js'):root.FGActivity;const library=typeof module==='object'&&module.exports?require('./library-core.js'):root.FGLibrary;const api=factory(core,activity,library);if(typeof module==='object'&&module.exports)module.exports=api;else root.FGToday=api;})(globalThis,function(C,A,L){
'use strict';
function build(state,options={}){if(!state||!Array.isArray(state.media)||!Array.isArray(state.episodes))throw Error('Bibliothèque invalide.');const now=options.now??Date.now(),day=options.day??C.localDate(now),limit=Number.isInteger(options.limit)?options.limit:12;if(!A.validDate(day)||limit<1||limit>100)throw Error('Options Aujourd’hui invalides.');
 const agenda=A.buildAgenda(state.media,state.episodes,{from:day,to:day,includeHidden:!!state.settings?.includeHiddenInCalendar,includeSpecials:!!state.settings?.includeSpecials,hideSpoilers:!!state.settings?.hideEpisodeSpoilers});
 const next=L.nextUp(state.media,state.episodes,{section:'ready',today:day,includeSpecials:!!state.settings?.includeSpecials,hideSpoilers:!!state.settings?.hideEpisodeSpoilers}).records.slice(0,limit);
 const queue=options.queue||[],uncertain=queue.filter(x=>x?.uncertain).length,held=queue.filter(x=>x?.held).length;
 return {day,agenda:agenda.events.slice(0,limit),agendaTotal:agenda.events.length,next,nextTotal:L.nextUp(state.media,state.episodes,{section:'ready',today:day,includeSpecials:!!state.settings?.includeSpecials,hideSpoilers:!!state.settings?.hideEpisodeSpoilers}).records.length,uncertain,held,checkIn:options.checkIn||null,backup:options.backup||null};
}
return {build};
});
