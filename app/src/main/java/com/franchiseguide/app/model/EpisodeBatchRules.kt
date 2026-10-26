package com.franchiseguide.app.model

import java.text.Normalizer
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId

/** Episode commands are explicit and scoped. A skipped state is never a history erase. */
object EpisodeBatchRules {
    const val MAX=10000
    const val EXPIRY=600000L
    val actions=setOf("watch","unwatch","skip","unskip","collect","uncollect")
    data class State(val media:List<MediaItem>,val episodes:List<Episode>)
    data class Query(val season:Int?=null,val status:String="all",val airing:String="all",val query:String="",val includeSpecials:Boolean=false,val hideSpoilers:Boolean=false,val today:LocalDate=LocalDate.now())
    data class View(val show:MediaItem,val rows:List<Episode>,val seasons:List<Int>,val total:Int)
    data class Identity(val id:String,val showId:String,val tmdbId:Int?,val tvdbId:Int?,val season:Int,val number:Int,val seasonUnknown:Boolean,val numberUnknown:Boolean)
    data class Guard(val identity:Identity,val watched:Boolean?=null,val plays:Int?=null,val last:Long?=null,val history:List<Long>?=null,val skipped:Boolean?=null,val collected:Boolean?=null,val date:LocalDate?=null)
    data class Entry(val id:String,val guard:Guard,val changed:Boolean,val reason:String)
    data class Counts(val selected:Int,val changed:Int,val unchanged:Int,val historyProtected:Int,val skippedProtected:Int,val dateProtected:Int,val knownDatesRemoved:Int,val playsRemoved:Long,val providerChanges:Int)
    data class Plan(val showId:String,val showTmdb:Int?,val showTvdb:Int?,val action:String,val at:Long,val today:LocalDate,val includeUndatedFuture:Boolean,val ids:List<String>,val entries:List<Entry>,val counts:Counts)
    fun parent(s:State,id:String):MediaItem=s.media.filter{it.id==id}.singleOrNull()?.takeIf{it.kind==MediaKind.SHOW&&it.added&&!it.isSuggestion}?:error("La série n’est plus ajoutée ou son identifiant est ambigu.")
    fun hasHistory(e:Episode)=e.watched||e.plays>0||(e.lastWatchedAt?:0)>0||e.watchHistory.isNotEmpty()
    fun label(e:Episode,hide:Boolean)=e.code+if(hide&&!e.watched)" · Épisode non vu" else if(e.title.isNotBlank())" · ${e.title}" else ""
    private fun norm(s:String)=Normalizer.normalize(s,Normalizer.Form.NFD).replace("[\\u0300-\\u036f]".toRegex(),"").lowercase()
    fun view(s:State,id:String,q:Query=Query()):View {
        val show=parent(s,id);require(q.status in setOf("all","unwatched","watched","skipped","collected","uncollected")&&q.airing in setOf("all","aired","future","unknown")&&q.query.length<=250){"Filtres invalides."}
        val all=s.episodes.filter{it.showId==id};val rows=all.filter { e->
            (q.includeSpecials||e.season!=0)&&(q.season==null||e.season==q.season)&&
            when(q.status){"unwatched"->!e.watched&&!e.skipped;"watched"->e.watched;"skipped"->e.skipped;"collected"->e.collected;"uncollected"->!e.collected;else->true}&&
            when(q.airing){"aired"->e.airDate!=null&&!e.airDate.isAfter(q.today);"future"->e.airDate?.isAfter(q.today)==true;"unknown"->e.airDate==null;else->true}&&
            (q.query.isBlank()||norm(label(e,q.hideSpoilers)).contains(norm(q.query)))
        }.sortedWith(compareBy<Episode>{it.season}.thenBy{it.number}.thenBy{it.id})
        return View(show,rows,all.map{it.season}.distinct().sorted(),all.size)
    }
    private fun guard(e:Episode,action:String):Guard {
        val id=Identity(e.id,e.showId,e.tmdbId,e.tvdbId,e.season,e.number,e.seasonUnknown,e.numberUnknown)
        if(action in setOf("collect","uncollect"))return Guard(id,collected=e.collected)
        if(action=="unskip")return Guard(id,skipped=e.skipped)
        return Guard(id,e.watched,e.plays,e.lastWatchedAt,e.watchHistory.toList(),e.skipped,date=if(action=="watch")e.airDate else null)
    }
    private fun outcome(e:Episode,action:String,at:Long,today:LocalDate,include:Boolean):Pair<Episode,String> = when(action) {
        "collect"->e.copy(collected=true) to "unchanged"
        "uncollect"->e.copy(collected=false) to "unchanged"
        "unskip"->e.copy(skipped=false) to "unchanged"
        "skip"->if(hasHistory(e))e to "history-protected" else e.copy(skipped=true) to "unchanged"
        "unwatch"->if(hasHistory(e))e.copy(watched=false,plays=0,lastWatchedAt=null,watchHistory=emptyList()) to "unchanged" else e to "unchanged"
        else->when {
            e.watched->e to "already-watched"
            hasHistory(e)->e to "history-protected"
            e.skipped->e to "skipped-protected"
            !include&&(e.airDate==null||e.airDate.isAfter(today))->e to "date-protected"
            else->e.copy(watched=true,skipped=false,plays=1,lastWatchedAt=at,watchHistory=listOf(at)) to "unchanged"
        }
    }
    fun plan(s:State,showId:String,ids:List<String>,action:String,includeUndatedFuture:Boolean=false,at:Long=System.currentTimeMillis(),today:LocalDate=Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()):Plan {
        require(action in actions&&ids.isNotEmpty()&&ids.size<=MAX&&ids.none{it.isBlank()}){"Sélection ou action invalide : 1 à 10 000 épisodes."}
        require(at in 1..8640000000000000L){"Date invalide."};val show=parent(s,showId);val index=s.episodes.groupBy{it.id};val selected=ids.distinct()
        var changed=0;var hist=0;var skip=0;var dates=0;var removedDates=0;var removedPlays=0L;var provider=0
        val entries=selected.map { id->val e=index[id]?.singleOrNull()?.takeIf{it.showId==showId}?:error("Un épisode a disparu, est ambigu ou appartient à une autre série.")
            val result=outcome(e,action,at,today,includeUndatedFuture);val differs=result.first!=e
            if(differs){changed++;if((e.tmdbId?:0)>0&&action !in setOf("skip","unskip"))provider++
                if(action=="unwatch"){removedDates+=(e.watchHistory+listOfNotNull(e.lastWatchedAt)).filter{it>0}.distinct().size;removedPlays+=maxOf(e.plays,if(e.watched)1 else 0)}}
            else when(result.second){"history-protected"->hist++;"skipped-protected"->skip++;"date-protected"->dates++}
            Entry(id,guard(e,action),differs,result.second)
        }
        return Plan(showId,show.tmdbId,show.tvdbId,action,at,today,includeUndatedFuture,selected,entries,Counts(selected.size,changed,selected.size-changed,hist,skip,dates,removedDates,removedPlays,provider))
    }
    fun apply(s:State,p:Plan,now:Long=System.currentTimeMillis()):State {
        require(now>=p.at-120000&&now-p.at<=EXPIRY){"Aperçu expiré. Prépare à nouveau la sélection."}
        val fresh=plan(s,p.showId,p.ids,p.action,p.includeUndatedFuture,p.at,p.today)
        require(fresh==p){"Un champ concerné a changé depuis l’aperçu. Aucune partie du lot appliquée."}
        val changed=p.entries.filter{it.changed}.map{it.id}.toSet();if(changed.isEmpty())return s
        val episodes=s.episodes.map{e->if(e.id in changed)outcome(e,p.action,p.at,p.today,p.includeUndatedFuture).first else e}
        val last=episodes.filter{it.showId==p.showId}.mapNotNull{it.lastWatchedAt}.filter{it>0}.maxOrNull()
        val media=if(p.action in setOf("watch","unwatch"))s.media.map{if(it.id==p.showId)it.copy(lastWatchedAt=last) else it} else s.media
        return State(media,episodes)
    }
}
