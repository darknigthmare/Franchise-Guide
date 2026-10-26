package com.franchiseguide.app.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.text.Normalizer

/** Read-only analysis. Current library state, dated completions and provider snapshots are not summed together. */
object InsightsRules {
    data class Query(val kind:String="all",val from:LocalDate?=null,val to:LocalDate?=null,val includeHidden:Boolean=false,val includeSpecials:Boolean=false,val favoritesOnly:Boolean=false,val search:String="",val now:Long=System.currentTimeMillis(),val zone:ZoneId=ZoneId.systemDefault())
    data class Event(val id:String,val itemId:String,val mediaId:String,val kind:String,val title:String,val parentTitle:String,val at:Long,val date:LocalDate,val minutes:Int?)
    data class MissingDates(val itemId:String,val mediaId:String,val kind:String,val title:String,val count:Long)
    data class Counts(var titles:Int=0,var plays:Long=0,var knownDates:Int=0,var undated:Long=0,var windowPlays:Int=0)
    data class Day(val date:LocalDate,var events:Int=0,var minutes:Long=0,var unknown:Int=0)
    data class SteamSummary(val accountId:String?,val label:String,val games:Int,val knownGames:Int,val unknownGames:Int,val minutes:Long,val duplicates:Int)
    data class SuccessSummary(val id:String,val mediaId:String,val title:String,val accountLabel:String,val provider:String,val platform:String,val edition:String,val total:Int,val earned:Int,val unknown:Int,val hardcore:Int,val localDone:Int,val platinum:Boolean,val complete:Boolean)
    data class Analysis(val query:Query,val media:List<MediaItem>,val episodes:List<Episode>,val statuses:Map<String,Map<String,Int>>,val byKind:Map<String,Counts>,val events:List<Event>,val undated:List<MissingDates>,val days:List<Day>,val totalPlays:Long,val knownDates:Int,val undatedPlays:Long,val futureDates:Int,val windowMinutes:Long,val windowUnknown:Int,val uniqueMinutes:Long,val allMinutes:Long,val unknownVideoPlays:Long,val currentPages:Long,val pageBooks:Int,val otherUnitBooks:Int,val declaredGameMinutes:Long,val declaredGameCount:Int,val caughtUp:Int,val steam:List<SteamSummary>,val successSets:List<SuccessSummary>)
    private fun norm(s:String)=Normalizer.normalize(s,Normalizer.Form.NFD).replace("[\\u0300-\\u036f]".toRegex(),"").lowercase(java.util.Locale.FRENCH)
    fun analyse(media:List<MediaItem>,episodes:List<Episode>,gaming:GamingState=GamingState(),query:Query=Query()):Analysis {
        require(query.kind in setOf("all","show","movie","game","book"));require(query.now>0)
        require(query.from==null||query.to==null||query.from<=query.to) { "La date de début est après la date de fin." }
        val selected=media.filter { it.added&&!it.isSuggestion&&(query.includeHidden||!it.hidden)&&(!query.favoritesOnly||it.favorite)&&(query.kind=="all"||it.kind.name.lowercase()==query.kind) }
        val parents=selected.associateBy { it.id };val eps=episodes.filter { parents[it.showId]?.kind==MediaKind.SHOW&&(query.includeSpecials||it.season!=0) }
        val byKind=listOf("episode","movie","game","book").associateWith{Counts()};val events=mutableListOf<Event>();val missing=mutableListOf<MissingDates>()
        var future=0;var uniqueMinutes=0L;var allMinutes=0L;var unknownPlays=0L
        fun record(itemId:String,parent:MediaItem,kind:String,title:String,watched:Boolean,skipped:Boolean,plays:Int,history:List<Long>,minutes:Int?) {
            if(!watched||skipped)return
            val valid=history.filter { it<=query.now };future+=history.size-valid.size
            val counts=byKind[kind]?:return;val total=maxOf(1,plays).toLong();counts.titles++;counts.plays+=total;counts.knownDates+=valid.size;counts.undated+=maxOf(0,total-valid.size)
            val mins=minutes?.takeIf{it>0}
            if(kind in setOf("movie","episode")){if(mins!=null){uniqueMinutes+=mins;allMinutes+=total*mins}else unknownPlays+=total}
            if(total>valid.size)missing+=MissingDates(itemId,parent.id,kind,title,total-valid.size)
            for(at in valid){val date=Instant.ofEpochMilli(at).atZone(query.zone).toLocalDate();events+=Event("$kind:$itemId:$at",itemId,parent.id,kind,title,parent.title,at,date,mins)}
        }
        selected.filter { it.kind!=MediaKind.SHOW }.forEach { m->record(m.id,m,m.kind.name.lowercase(),m.title,m.watched,false,m.plays,TrackingRules.knownHistory(m),if(m.kind==MediaKind.MOVIE)m.runtimeMinutes else null) }
        eps.forEach { e->record(e.id,parents.getValue(e.showId),"episode","${e.code} · ${e.title}",e.watched,e.skipped,e.plays,TrackingRules.knownHistory(e),e.runtimeMinutes) }
        val searched=norm(query.search).take(250);val window=events.filter { (query.from==null||it.date>=query.from)&&(query.to==null||it.date<=query.to)&&(searched.isBlank()||norm(it.parentTitle+" "+it.title).contains(searched)) }.sortedWith(compareByDescending<Event>{it.at}.thenBy{it.id})
        val days=sortedMapOf<LocalDate,Day>();var windowMinutes=0L;var windowUnknown=0
        for(e in window){byKind.getValue(e.kind).windowPlays++;val d=days.getOrPut(e.date){Day(e.date)};d.events++;if(e.kind in setOf("movie","episode")){if(e.minutes==null){windowUnknown++;d.unknown++}else{windowMinutes+=e.minutes;d.minutes+=e.minutes}}}
        val statuses=listOf("game","book").associateWith { kind->ProgressRules.states.associateWith{state->selected.count{it.kind.name.lowercase()==kind&&ProgressRules.status(it)==state}} }
        val pageBooks=selected.filter{it.kind==MediaKind.BOOK&&norm(it.progressUnit.trim()) in setOf("page","pages")};val pageIds=pageBooks.map { it.id }.toSet();val otherBooks=selected.count { it.kind==MediaKind.BOOK&&it.id !in pageIds&&(it.progressCurrent>0||it.progressUnit.isNotBlank()) }
        val manualTime=selected.filter{it.kind==MediaKind.GAME&&it.playtimeMinutes!=null&&it.playtimeMinutes>=0}
        val games=selected.filter{it.kind==MediaKind.GAME};val steamBuckets=linkedMapOf<String,MutableMap<String,Long?>>() ;val duplicateCount=mutableMapOf<String,Int>()
        for(m in games){val meta=m.steam?:continue;for((account,value) in meta.accounts){val apps=steamBuckets.getOrPut(account){linkedMapOf()};val n=value.playtimeMinutes?.takeIf{it>=0};if(apps.containsKey(meta.appId)){duplicateCount[account]=(duplicateCount[account]?:0)+1;val old=apps[meta.appId];apps[meta.appId]=if(old==null)n else if(n==null)old else maxOf(old,n)}else apps[meta.appId]=n}}
        val steam=steamBuckets.map{(externalId,apps)->val account=gaming.accounts.find{it.provider=="steam"&&it.externalId==externalId};SteamSummary(account?.id,account?.label?:"Profil Steam non associé",apps.size,apps.values.count{it!=null},apps.values.count{it==null},apps.values.sumOf{it?:0L},duplicateCount[externalId]?:0)}
        val gameIds=games.map{it.id}.toSet();val accountMap=gaming.accounts.associateBy{it.id};val success=gaming.sets.filter{it.mediaId in gameIds}.map{s->val result=GamingRules.progress(s);SuccessSummary(s.id,s.mediaId,s.title,accountMap[s.accountId]?.label?:"Sans compte associé",s.provider,s.platform,s.edition,result.total,result.earned,result.unknown,s.achievements.count{it.hardcoreEarned==true},result.localDone,result.platinum,result.complete)}
        val byShow=eps.groupBy{it.showId};val caughtUp=selected.count{m->val e=byShow[m.id].orEmpty();m.kind==MediaKind.SHOW&&m.catalogComplete&&e.isNotEmpty()&&e.all{it.watched||it.skipped}&&(m.episodeCount==null||e.count{it.season>0}>=m.episodeCount)}
        val total=byKind.values.sumOf{it.plays}
        return Analysis(query,selected,eps,statuses,byKind,window,missing,days.values.toList(),total,events.size,maxOf(0,total-events.size),future,windowMinutes,windowUnknown,uniqueMinutes,allMinutes,unknownPlays,pageBooks.sumOf{maxOf(0,it.progressCurrent).toLong()},pageBooks.size,otherBooks,manualTime.sumOf{it.playtimeMinutes!!.toLong()},manualTime.size,caughtUp,steam,success)
    }
}
