package com.franchiseguide.app.model

import java.text.Normalizer
import java.util.Locale

/** Pure import/reconciliation guards. Do not infer a shared episode from conflicting identities. */
object ReliabilityRules {
    fun folded(s:String):String=Normalizer.normalize(s,Normalizer.Form.NFKD).replace(Regex("\\p{M}+"),"").lowercase(Locale.FRENCH).replace(Regex("\\s+")," ").trim()
    fun episodeConflict(a:Episode,b:Episode):Boolean =
        (a.tmdbId!=null&&b.tmdbId!=null&&a.tmdbId!=b.tmdbId) ||
        (a.tvdbId!=null&&b.tvdbId!=null&&a.tvdbId!=b.tvdbId) ||
        (a.id.startsWith("tvmaze-episode-")&&b.id.startsWith("tvmaze-episode-")&&a.id!=b.id)
    fun sameEpisode(a:Episode,b:Episode):Boolean {
        if(a.showId!=b.showId||episodeConflict(a,b))return false
        if(a.id.startsWith("tvmaze-episode-")||b.id.startsWith("tvmaze-episode-"))return a.id==b.id
        return (a.tmdbId!=null&&a.tmdbId==b.tmdbId)||(a.tvdbId!=null&&a.tvdbId==b.tvdbId)||
            (!a.seasonUnknown&&!b.seasonUnknown&&!a.numberUnknown&&!b.numberUnknown&&a.season==b.season&&a.number==b.number)
    }
    fun unionHistory(a:Episode,b:Episode):List<Long> {
        fun known(x:Episode)=if(x.watched&&!x.skipped)x.watchHistory+listOfNotNull(x.lastWatchedAt) else emptyList()
        return (known(a)+known(b)).filter{it in 1..8640000000000000L}.distinct().sorted()
    }
    fun mediaConflict(a:MediaItem,b:MediaItem):Boolean =
        (a.tmdbId!=null&&b.tmdbId!=null&&a.tmdbId!=b.tmdbId)||
        (a.tvdbId!=null&&b.tvdbId!=null&&a.tvdbId!=b.tvdbId)||
        (!a.imdbId.isNullOrBlank()&&!b.imdbId.isNullOrBlank()&&a.imdbId!=b.imdbId)||
        (a.source==b.source&&a.source!="seriesguide"&&!a.sourceId.isNullOrBlank()&&!b.sourceId.isNullOrBlank()&&a.sourceId!=b.sourceId)
}
