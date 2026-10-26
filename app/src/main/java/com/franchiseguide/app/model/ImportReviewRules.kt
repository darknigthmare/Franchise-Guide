package com.franchiseguide.app.model

import java.io.InputStream
import java.io.FilterInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Immutable review data. Contents are kept in memory; no IO or repository mutation. */
data class ImportSnapshot(
    val media:List<MediaItem>, val episodes:List<Episode>, val suggestions:List<MediaItem>,
    val lists:List<UserList>,val franchises:List<FranchiseDefinition>,val gaming:GamingState,val settings:AppSettings
)
data class ImportTitleChange(val id:String,val title:String,val kind:MediaKind,val change:String)
data class ImportCounts(val titles:Int,val shows:Int,val movies:Int,val games:Int,val books:Int,val episodes:Int,val watchedEpisodes:Int,val dates:Int,val notes:Int,val lists:Int,val franchises:Int,val accounts:Int,val sets:Int,val achievements:Int)
data class ImportComparison(val before:ImportCounts,val after:ImportCounts,val rows:List<ImportTitleChange>,val absentDates:Int,val changedNotes:Int,val changedRatings:Int,val removedWatched:Int) {
    fun count(change:String)=rows.count { it.change==change }
}
data class PreparedLibraryImport internal constructor(val mode:String,val sourceNames:List<String>,internal val baseline:ImportSnapshot,internal val candidate:ImportSnapshot,internal val generation:Int,val seriesGuideSummary:SeriesGuideImportSummary?=null)
object ImportReviewRules {
    const val MAX_FILES=10
    const val MAX_FILE=32*1024*1024
    const val MAX_TOTAL=64*1024*1024
    fun checkDocuments(documents:List<String>,portable:Boolean) {
        require(documents.isNotEmpty() && documents.size<=MAX_FILES && (!portable || documents.size==1)) { "Choisis un JSON FranchiseGuide ou 1 à 10 fichiers SeriesGuide." }
        var sum=0L
        for(doc in documents) { val size=doc.toByteArray(Charsets.UTF_8).size;sum+=size;require(size in 1..MAX_FILE && sum<=MAX_TOTAL) { "Limites : 32 Mio par fichier, 64 Mio au total." } }
    }
    /** Read byte-budgeted inputs sequentially. A failed/oversize input cannot allocate all later files. */
    fun readDocuments(openers:List<()->InputStream>,portable:Boolean,maxFile:Int=MAX_FILE,maxTotal:Int=MAX_TOTAL):List<String> {
        require(openers.isNotEmpty()&&openers.size<=MAX_FILES&&(!portable||openers.size==1)){"Choisis un JSON FranchiseGuide ou 1 à 10 fichiers SeriesGuide."}
        require(maxFile>0&&maxTotal>0)
        var total=0L
        return openers.map { open->
            val remaining=(maxTotal-total).coerceAtMost(maxFile.toLong()).toInt();require(remaining>0){"Limite cumulée de fichiers atteinte."}
            val stream=object:FilterInputStream(open()) {
                var bytes=0
                override fun read(b:ByteArray,off:Int,len:Int):Int { val n=super.read(b,off,len);if(n>0)bytes+=n;return n }
                override fun read():Int { val n=super.read();if(n>=0)bytes++;return n }
            }
            val result=stream.use{ReleaseRules.readImport(it,remaining)}
            require(stream.bytes>0){"Fichier vide."};total+=stream.bytes
            require(total<=maxTotal){"Limite cumulée de fichiers dépassée."};result
        }
    }
    /** Official show timestamps can be ISO8601 with an offset; episodes/movies use epoch milliseconds. */
    fun seriesGuideDate(value:Any?):LocalDate? {
        if(value==null||value=="")return null
        if(value is Number){
            val n=value.toLong();require(value.toDouble().isFinite()&&value.toDouble()==n.toDouble()&&n>=-1&&n<=8640000000000000L){"Date SeriesGuide invalide."}
            if(n<=0)return null
            val date=Instant.ofEpochMilli(n).atOffset(ZoneOffset.UTC).toLocalDate();require(date.year in 1..9999){"Date SeriesGuide hors plage."};return date
        }
        require(value is String && Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}(T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,3})?(Z|[+-][0-9]{2}:[0-9]{2}))?").matches(value)){"Date SeriesGuide invalide."}
        val day=LocalDate.parse(value.take(10));require(day.year in 1..9999)
        return if(value.length==10)day else OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDate()
    }
    private fun history(history:List<Long>,last:Long?)=(history+listOfNotNull(last)).filter{it>0}.map{it/1000}.toSet()
    fun counts(s:ImportSnapshot):ImportCounts {
        val m=s.media.filter{it.added&&!it.isSuggestion};val e=s.episodes
        return ImportCounts(m.size,m.count{it.kind==MediaKind.SHOW},m.count{it.kind==MediaKind.MOVIE},m.count{it.kind==MediaKind.GAME},m.count{it.kind==MediaKind.BOOK},e.size,e.count{it.watched&&!it.skipped},m.filter{it.kind!=MediaKind.SHOW}.sumOf{history(it.watchHistory,it.lastWatchedAt).size}+e.sumOf{history(it.watchHistory,it.lastWatchedAt).size},m.count{!it.userNote.isNullOrBlank()}+e.count{!it.userNote.isNullOrBlank()},s.lists.size,s.franchises.size,s.gaming.accounts.size,s.gaming.sets.size,s.gaming.sets.sumOf{it.achievements.size})
    }
    fun compare(before:ImportSnapshot,after:ImportSnapshot):ImportComparison {
        val b=before.media.associateBy{it.id};val a=after.media.associateBy{it.id};val ae=after.episodes.associateBy{it.id}
        val rows=(b.keys+a.keys).map { id-> val old=b[id];val next=a[id];val item=next?:old!!;ImportTitleChange(id,item.title,item.kind,if(old==null)"added" else if(next==null)"removed" else if(old==next)"unchanged" else "modified") }.sortedBy{it.title.lowercase()}
        var dates=0;var notes=0;var ratings=0;var watched=0
        fun loss(h:List<Long>,last:Long?,nh:List<Long>,nl:Long?,note:String?,nn:String?,rating:Int?,nr:Int?,seen:Boolean,ns:Boolean){dates+=(history(h,last)-history(nh,nl)).size;if(!note.isNullOrBlank()&&note!=nn)notes++;if(rating!=null&&rating!=nr)ratings++;if(seen&&!ns)watched++}
        for(o in before.media){val n=a[o.id];loss(if(o.kind==MediaKind.SHOW)emptyList() else o.watchHistory,if(o.kind==MediaKind.SHOW)null else o.lastWatchedAt,if(o.kind==MediaKind.SHOW)emptyList() else n?.watchHistory.orEmpty(),if(o.kind==MediaKind.SHOW)null else n?.lastWatchedAt,o.userNote,n?.userNote,o.rating,n?.rating,o.watched,n?.watched?:false)}
        for(o in before.episodes){val n=ae[o.id];loss(o.watchHistory,o.lastWatchedAt,n?.watchHistory.orEmpty(),n?.lastWatchedAt,o.userNote,n?.userNote,o.rating,n?.rating,o.watched,n?.watched?:false)}
        return ImportComparison(counts(before),counts(after),rows,dates,notes,ratings,watched)
    }
    fun guard(review:PreparedLibraryImport,current:ImportSnapshot,generation:Int) {
        require(review.generation==generation && review.baseline==current) { "La bibliothèque ou ses réglages ont changé depuis l’aperçu. Relis les fichiers." }
    }
    fun rows(comparison:ImportComparison,query:String="",change:String="all",kind:MediaKind?=null):List<ImportTitleChange> {
        require(change in setOf("all","added","removed","modified","unchanged"))
        return comparison.rows.filter{(kind==null||it.kind==kind)&&(change=="all"||it.change==change)&&(query.isBlank()||it.title.contains(query,true))}
    }
}
