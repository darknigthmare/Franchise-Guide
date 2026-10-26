package com.franchiseguide.app.model

import java.time.LocalDate
import java.util.UUID

/** Domain rules independent from Android/Compose, covered by executable JVM tests. */
object TrackingRules {
    fun normalizedStatus(value: String?): ShowStatus {
        val v = value.orEmpty().uppercase().replace(' ', '_').replace('-', '_')
        return when (v) {
            "RETURNING_SERIES" -> ShowStatus.CONTINUING
            "CANCELLED" -> ShowStatus.CANCELED
            else -> runCatching { ShowStatus.valueOf(v) }.getOrDefault(ShowStatus.UNKNOWN)
        }
    }
    fun knownHistory(watched:Boolean,plays:Int,last:Long?,dates:List<Long>,skipped:Boolean=false):List<Long> =
        if(!watched || skipped) emptyList() else (dates+listOfNotNull(last)).filter { it>0 && it<=8640000000000000L }.distinct().sorted().takeLast(maxOf(1,plays))
    fun knownHistory(e:Episode):List<Long> = knownHistory(e.watched,e.plays,e.lastWatchedAt,e.watchHistory,e.skipped)
    fun knownHistory(m:MediaItem):List<Long> = knownHistory(m.watched,m.plays,m.lastWatchedAt,m.watchHistory)
    fun episode(e: Episode, action: String, now: Long = System.currentTimeMillis()): Episode = when (action) {
        "watch" -> e.copy(watched=true,skipped=false,plays=maxOf(1,e.plays),lastWatchedAt=if(e.watched&&!e.skipped)e.lastWatchedAt else now,watchHistory=if(e.watched&&!e.skipped)knownHistory(e) else listOf(now))
        "unwatch" -> e.copy(watched=false,plays=0,lastWatchedAt=null,watchHistory=emptyList())
        "replay" -> e.copy(watched=true,skipped=false,plays=e.plays.coerceAtLeast(0)+1,lastWatchedAt=now,watchHistory=knownHistory(e)+now)
        "skip" -> if(e.skipped)e.copy(skipped=false) else if(EpisodeBatchRules.hasHistory(e))e else e.copy(skipped=true)
        "collect" -> e.copy(collected=!e.collected)
        else -> e
    }
    fun completed(m:MediaItem,done:Boolean,now:Long=System.currentTimeMillis(),replay:Boolean=false):MediaItem {
        if(!done)return m.copy(watched=false,plays=0,lastWatchedAt=null,watchHistory=emptyList())
        val changed=!m.watched||replay
        return m.copy(watched=true,started=true,plays=if(replay)m.plays.coerceAtLeast(0)+1 else maxOf(1,m.plays),
            lastWatchedAt=if(changed)now else m.lastWatchedAt,
            watchHistory=if(changed)knownHistory(m)+now else knownHistory(m))
    }
    fun sameEpisode(a:Episode,b:Episode):Boolean = ReliabilityRules.sameEpisode(a,b)
    fun activities(media:List<MediaItem>,episodes:List<Episode>):List<DatedActivity> {
        val byId=media.filter { it.added }.associateBy { it.id }
        return (media.filter { it.added&&it.kind!=MediaKind.SHOW }.flatMap { m->knownHistory(m).map { t->DatedActivity("${m.kind}:${m.id}:$t",m.id,m.title,m.title,m.kind.name.lowercase(),t) } } +
            episodes.flatMap { e-> val parent=byId[e.showId];if(parent==null)emptyList() else knownHistory(e).map { t->DatedActivity("episode:${e.id}:$t",e.showId,"${e.code} · ${e.title}",parent.title,"episode",t) } }).sortedByDescending { it.at }
    }
    fun filterStatus(items: List<MediaItem>, rules: Map<String, Int>): List<MediaItem> {
        val included = rules.filterValues { it == 1 }.keys
        return items.filter { val k = it.status.name; rules[k] != -1 && (included.isEmpty() || k in included) }
    }
    fun withinCalendar(date: LocalDate?, now: LocalDate, recent: Boolean, days: Int): Boolean {
        if(date == null) return false
        val count = days.coerceAtLeast(0).toLong()
        return if(recent) !date.isAfter(now) && (count == 0L || !date.isBefore(now.minusDays(count)))
        else !date.isBefore(now) && (count == 0L || !date.isAfter(now.plusDays(count)))
    }
    fun sort(items: List<MediaItem>, mode: SortMode, ascending: Boolean, episodes: List<Episode>): List<MediaItem> {
        val byShow = episodes.groupBy { it.showId }
        fun value(m: MediaItem): Comparable<*>? = when(mode) {
            SortMode.TITLE -> m.title.lowercase()
            SortMode.YEAR -> m.year
            SortMode.STATUS -> m.status.name
            SortMode.RATING -> m.rating?.toDouble() ?: m.catalogRating
            SortMode.ADDED -> m.addedAt
            SortMode.RECENTLY_WATCHED -> maxOf(m.lastWatchedAt ?: 0L, byShow[m.id].orEmpty().maxOfOrNull { it.lastWatchedAt ?: 0L } ?: 0L)
            SortMode.NEXT_EPISODE -> byShow[m.id].orEmpty().filter { !it.watched && !it.skipped }.mapNotNull { it.airDate }.minOrNull() ?: m.nextAirDate
            SortMode.REMAINING -> if(m.kind == MediaKind.SHOW) byShow[m.id].orEmpty().count { !it.watched && !it.skipped } else (m.progressTotal - m.progressCurrent).coerceAtLeast(0)
            SortMode.PROGRESS -> if(m.kind == MediaKind.SHOW) byShow[m.id].orEmpty().let { eps -> if(eps.isEmpty()) 0.0 else eps.count { it.watched || it.skipped }.toDouble()/eps.size } else if(ProgressRules.applicable(m))m.progressRatio.toDouble() else if(m.watched) 1.0 else 0.0
        }
        val values=items.associateWith(::value)
        return items.sortedWith { a,b ->
            val av=values[a];val bv=values[b]
            val c=when { av==null && bv!=null -> 1; av!=null && bv==null -> -1; av==null -> 0
                else -> { @Suppress("UNCHECKED_CAST") val cmp=(av as Comparable<Any>).compareTo(bv as Any);if(ascending) cmp else -cmp }
            }
            if(c != 0) c else a.title.compareTo(b.title, ignoreCase = true)
        }
    }
    fun records(media: List<MediaItem>, episodes: List<Episode>): List<TrackingRecord> =
        media.filter { it.tmdbId != null && it.kind in setOf(MediaKind.SHOW, MediaKind.MOVIE) }.map { m ->
            TrackingRecord(m.kind.name.lowercase(), m.tmdbId!!, m.title, m.watched, m.plays, m.lastWatchedAt, m.collected, m.watchlist, m.rating, watchHistory=knownHistory(m))
        } + episodes.filter { it.tmdbId != null }.map { e ->
            TrackingRecord("episode", e.tmdbId!!, e.title, e.watched, e.plays, e.lastWatchedAt, e.collected, false, e.rating, e.skipped,knownHistory(e))
        }
    /** An absent remote flag is not a command to delete locally imported data. */
    fun reconcile(local:TrackingRecord, remote:TrackingRecord, baseline:TrackingRecord?, pending:List<PendingChange>):TrackingRecord {
        val fields=pending.filter { it.key==local.key }.map { it.field }.toSet()
        val history="history" !in fields && !local.skipped && (remote.watched || baseline?.watched==true && !remote.watched)
        val collection="collection" !in fields && (remote.collected || baseline?.collected==true && !remote.collected)
        val watchlist="watchlist" !in fields && (remote.watchlist || baseline?.watchlist==true && !remote.watchlist)
        val ratings="ratings" !in fields && (remote.rating!=null || baseline?.rating!=null)
        return local.copy(watched=if(history) remote.watched else local.watched,plays=if(history) remote.plays else local.plays,
            lastWatchedAt=if(history) remote.lastWatchedAt else local.lastWatchedAt,collected=if(collection) remote.collected else local.collected,
            watchlist=if(watchlist) remote.watchlist else local.watchlist,rating=if(ratings) remote.rating else local.rating)
    }
    fun changes(before: List<TrackingRecord>, after: List<TrackingRecord>): List<PendingChange> {
        val old=before.associateBy { it.key }
        return buildList {
            for(x in after) {
                val p=old[x.key] ?: TrackingRecord(x.type,x.tmdbId,x.title)
                fun addChange(field:String,enabled:Boolean=true,rating:Int?=null,at:Long?=null) {
                    add(PendingChange(UUID.randomUUID().toString(),x.type,x.tmdbId,x.title,field,enabled,rating,at))
                }
                if(x.type != "show") {
                    if(x.watched&&!x.skipped&&(!p.watched||x.plays>p.plays)) {
                        val previous=knownHistory(p.watched,p.plays,p.lastWatchedAt,p.watchHistory,p.skipped).toSet()
                        knownHistory(x.watched,x.plays,x.lastWatchedAt,x.watchHistory,x.skipped).filterNot { it in previous }.forEach { addChange("history",at=it) }
                    }
                    if(p.watched && !x.watched && !x.skipped) addChange("history",enabled=false)
                    if(p.collected != x.collected) addChange("collection",enabled=x.collected)
                }
                if(x.type != "episode" && p.watchlist != x.watchlist) addChange("watchlist",enabled=x.watchlist)
                if(p.rating != x.rating) addChange("ratings",enabled=x.rating!=null,rating=x.rating)
            }
        }
    }
}

data class TrackingRecord(val type:String,val tmdbId:Int,val title:String,val watched:Boolean=false,val plays:Int=0,
    val lastWatchedAt:Long?=null,val collected:Boolean=false,val watchlist:Boolean=false,val rating:Int?=null,val skipped:Boolean=false,val watchHistory:List<Long> = emptyList()) {
    val key:String get()="$type:$tmdbId"
}
data class PendingChange(val id:String,val type:String,val tmdbId:Int,val title:String,val field:String,val enabled:Boolean,
    val rating:Int?=null,val watchedAt:Long?=null,val uncertain:Boolean=false,val heldReason:String?=null) {
    val key:String get()="$type:$tmdbId"
    val path:String get()="/sync/${this.field}" + if(enabled) "" else "/remove"
    val plural:String get()=when(type){"episode"->"episodes";"show"->"shows";else->"movies"}
}
