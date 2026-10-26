package com.franchiseguide.app.model

import kotlin.math.roundToInt

data class TraktCheckInTarget(
    val kind:String,val localId:String,val parentId:String?,val tmdbId:Int,val title:String,val code:String?,
    val runtimeMinutes:Int?,val identity:String
)

data class TraktCheckInSession(
    val version:Int=1,val id:String,val account:String,val appRef:String?,val kind:String,val localId:String,val parentId:String?,
    val tmdbId:Int,val title:String,val code:String?,val runtimeMinutes:Int?,val identity:String,val startedAt:Long,
    val remoteStartedAt:Long?=null,val expectedEndAt:Long?=null,val status:String="starting",val finishAt:Long?=null,
    val lastError:String?=null,val historyApplied:Boolean=false
)

data class TraktCheckInTiming(val elapsed:Long,val remaining:Long?,val due:Boolean,val percent:Int?)
data class TraktCheckInState(val media:List<MediaItem>,val episodes:List<Episode>)

object TraktCheckInRules {
    const val VERSION=1
    val statuses=setOf("starting","active","uncertain-start","finishing","uncertain-finish","cleanup","uncertain-cancel")
    private fun text(v:String,label:String,max:Int=500):String { val x=v.trim();require(x.isNotBlank()&&x.length<=max&&x.none{it.code<32||it.code==127}){"$label invalide."};return x }
    private fun runtime(v:Int?)=v?.also{require(it in 1..10000){"Durée invalide."}}
    fun movie(media:List<MediaItem>,id:String):TraktCheckInTarget {
        val rows=media.filter{it.id==id&&it.kind==MediaKind.MOVIE&&it.added&&!it.isSuggestion};require(rows.size==1){"Film retiré ou ambigu. Rouvre sa fiche."};val m=rows.single();val tmdb=m.tmdbId?:error("Identifiant TMDb du film manquant.");require(tmdb>0)
        return TraktCheckInTarget("movie",m.id,null,tmdb,text(m.title,"Titre"),null,runtime(m.runtimeMinutes),"movie:${m.id}:$tmdb")
    }
    fun episode(media:List<MediaItem>,episodes:List<Episode>,id:String):TraktCheckInTarget {
        val eps=episodes.filter{it.id==id};require(eps.size==1){"Épisode retiré ou ambigu. Rouvre la série."};val e=eps.single();val shows=media.filter{it.id==e.showId&&it.kind==MediaKind.SHOW&&it.added&&!it.isSuggestion};require(shows.size==1){"Série retirée ou ambiguë."};val show=shows.single();val tmdb=e.tmdbId?:error("Identifiant TMDb de l’épisode manquant.");require(tmdb>0)
        val code=if(e.seasonUnknown||e.numberUnknown)"Épisode" else e.code
        return TraktCheckInTarget("episode",e.id,show.id,tmdb,text(show.title,"Titre série"),code,runtime(e.runtimeMinutes),"episode:${show.id}:${e.id}:$tmdb")
    }
    fun create(target:TraktCheckInTarget,account:String,appRef:String?,now:Long=System.currentTimeMillis()):TraktCheckInSession {
        require(now>0);val runtime=runtime(target.runtimeMinutes);return validate(TraktCheckInSession(id="checkin-$now-${target.tmdbId}",account=text(account,"Compte Trakt",250),appRef=appRef?.let{text(it,"Application Trakt",150)},kind=target.kind,localId=text(target.localId,"Identifiant local"),parentId=target.parentId?.let{text(it,"Identifiant parent")},tmdbId=target.tmdbId,title=text(target.title,"Titre"),code=target.code?.let{text(it,"Code",100)},runtimeMinutes=runtime,identity=text(target.identity,"Identité",1000),startedAt=now,expectedEndAt=runtime?.let{now+it*60000L}))
    }
    fun validate(s:TraktCheckInSession):TraktCheckInSession {
        require(s.version==VERSION&&s.status in statuses&&s.kind in setOf("movie","episode")&&s.tmdbId>0&&s.startedAt>0){"Session de check-in invalide."};text(s.id,"Session",600);text(s.account,"Compte",250);s.appRef?.let{text(it,"Application",150)};text(s.localId,"Identifiant local");if(s.kind=="episode")text(s.parentId?:error("Parent absent."),"Parent");else require(s.parentId==null);text(s.title,"Titre");s.code?.let{text(it,"Code",100)};runtime(s.runtimeMinutes);text(s.identity,"Identité",1000);s.remoteStartedAt?.let{require(it>0)};s.expectedEndAt?.let{require(it>=s.startedAt)};s.finishAt?.let{require(it>=s.startedAt)};s.lastError?.let{require(it.length<=800)};return s
    }
    fun sameContext(s:TraktCheckInSession,account:String,appRef:String?)=validate(s).account==text(account,"Compte",250)&&s.appRef==appRef
    fun started(s:TraktCheckInSession,remoteStartedAt:Long?)=validate(s).copy(status="active",remoteStartedAt=remoteStartedAt,lastError=null)
    fun uncertain(s:TraktCheckInSession,phase:String,error:String):TraktCheckInSession {require(phase in setOf("start","finish","cancel"));val status=when(phase){"start"->"uncertain-start";"finish"->"uncertain-finish";else->"uncertain-cancel"};return validate(s).copy(status=status,lastError=error.take(800))}
    fun beginFinish(s:TraktCheckInSession,at:Long=System.currentTimeMillis()):TraktCheckInSession {val v=validate(s);require(at>=v.startedAt);return v.copy(status="finishing",finishAt=v.finishAt?:at,lastError=null)}
    fun remoteHistoryFinish(s:TraktCheckInSession,at:Long):TraktCheckInSession {val v=validate(s);require(at>=v.startedAt);return v.copy(status="finishing",finishAt=at,lastError=null)}
    fun completionMode(s:TraktCheckInSession,now:Long=System.currentTimeMillis()):String {
        val v=validate(s)
        if(v.status=="cleanup"||v.historyApplied)return "cleanup"
        if(v.finishAt!=null||v.status in setOf("finishing","uncertain-finish"))return "verify"
        return if(timing(v,now).due)"verify" else "write"
    }
    fun historyAcknowledged(added:Number?,existing:Number?):Boolean {
        val counts=listOfNotNull(added,existing)
        return counts.isNotEmpty()&&counts.all{val n=it.toDouble();n.isFinite()&&n>=0&&n<=1&&n==it.toLong().toDouble()}&&counts.sumOf{it.toLong()}==1L
    }
    fun confirmed(s:TraktCheckInSession)=validate(s).copy(status="cleanup",lastError=null,historyApplied=true)
    fun timing(s:TraktCheckInSession,now:Long=System.currentTimeMillis()):TraktCheckInTiming {val v=validate(s);val elapsed=(now-v.startedAt).coerceAtLeast(0);val total=v.expectedEndAt?.minus(v.startedAt);val remaining=v.expectedEndAt?.minus(now)?.coerceAtLeast(0);return TraktCheckInTiming(elapsed,remaining,v.expectedEndAt?.let{now>=it}?:false,total?.takeIf{it>0}?.let{((elapsed.toDouble()/it)*100).coerceIn(0.0,100.0).roundToInt()})}
    fun recentMatch(s:TraktCheckInSession,rows:List<Pair<Int,Long>>,now:Long=System.currentTimeMillis()):Long? {val v=validate(s);require(rows.size<=1000);val lower=v.startedAt;val upper=now+300000L;return rows.firstOrNull{it.first==v.tmdbId&&it.second in lower..upper}?.second}
    private fun sameSecond(values:List<Long>,at:Long)=values.any{it/1000==at/1000}
    fun apply(state:TraktCheckInState,s:TraktCheckInSession,at:Long=s.finishAt?:error("Date de fin absente."),now:Long=System.currentTimeMillis()):TraktCheckInState {
        val v=validate(s);require(at>0&&at<=now+300000L);val media=state.media.toMutableList();val episodes=state.episodes.toMutableList()
        if(v.kind=="movie"){
            val rows=media.withIndex().filter{it.value.id==v.localId&&it.value.kind==MediaKind.MOVIE&&it.value.added&&!it.value.isSuggestion};require(rows.size==1){"Le film a changé. Aucun suivi local appliqué."};val (idx,m)=rows.single();require("movie:${m.id}:${m.tmdbId}"==v.identity){"Le film a changé. Aucun suivi local appliqué."};val history=TrackingRules.knownHistory(m);if(!sameSecond(history,at))media[idx]=ActivityRules.add(m,at,now)
        }else{
            val eRows=episodes.withIndex().filter{it.value.id==v.localId&&it.value.showId==v.parentId};val sRows=media.withIndex().filter{it.value.id==v.parentId&&it.value.kind==MediaKind.SHOW&&it.value.added&&!it.value.isSuggestion};require(eRows.size==1&&sRows.size==1){"L’épisode a changé. Aucun suivi local appliqué."};val (ei,e)=eRows.single();val (mi,m)=sRows.single();require("episode:${m.id}:${e.id}:${e.tmdbId}"==v.identity){"L’épisode a changé. Aucun suivi local appliqué."};val history=TrackingRules.knownHistory(e);if(!sameSecond(history,at))episodes[ei]=ActivityRules.add(e,at,now);media[mi]=m.copy(lastWatchedAt=episodes.filter{it.showId==m.id}.maxOfOrNull{it.lastWatchedAt?:0L}?.takeIf{it>0})
        }
        return TraktCheckInState(media,episodes)
    }
}
