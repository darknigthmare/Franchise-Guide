package com.franchiseguide.app.model

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.*
import java.time.format.DateTimeFormatter

/** All-day catalogue agenda and explicit dated plays, independent of Android UI. */
object ActivityRules {
    fun acknowledgedCounts(counts:List<Number?>):Boolean = counts.any { it!=null && it.toDouble().isFinite() && it.toDouble()>=0 && it.toDouble()%1.0==0.0 }
    val kinds=setOf("episode","movie","game","book")
    data class Query(val kind:String="all",val from:LocalDate?=null,val to:LocalDate?=null,val favoritesOnly:Boolean=false,val unwatchedOnly:Boolean=false,
        val includeHidden:Boolean=false,val includeSpecials:Boolean=false,val includeSkipped:Boolean=false,val hideSpoilers:Boolean=false,val query:String="")
    data class Event(val key:String,val uid:String,val date:LocalDate,val kind:String,val mediaId:String,val itemId:String,val title:String,val description:String)
    data class Agenda(val events:List<Event>,val undated:Int,val duplicates:Int)
    fun localChoices(day:String,time:String,zone:ZoneId=ZoneId.systemDefault()):List<Long> {
        require(day.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))&&time.matches(Regex("[0-9]{2}:[0-9]{2}"))) { "Date AAAA-MM-JJ et heure HH:mm requises." }
        val date=LocalDate.parse(day);val dt=LocalDateTime.of(date,LocalTime.parse(time));return zone.rules.getValidOffsets(dt).map { dt.toInstant(it).toEpochMilli() }.sorted()
    }
    private fun history(watched:Boolean,plays:Int,last:Long?,dates:List<Long>,skipped:Boolean,at:Long,now:Long):Pair<Int,List<Long>> {
        require(at>0&&at<=now) { "Choisis une date passée valide, après le 1er janvier 1970." }
        val old=TrackingRules.knownHistory(watched,plays,last,dates,skipped)
        require(old.none{it/1000==at/1000}) { "Ce visionnage existe déjà à cette seconde. Aucun doublon ajouté." }
        val count=if(watched&&!skipped)maxOf(1,plays) else 0;require(count<100000) { "Compteur de lectures trop élevé." }
        return count+1 to (old+at).sorted()
    }
    fun add(e:Episode,at:Long,now:Long=System.currentTimeMillis()):Episode {
        val (count,dates)=history(e.watched,e.plays,e.lastWatchedAt,e.watchHistory,e.skipped,at,now)
        return e.copy(watched=true,skipped=false,plays=count,watchHistory=dates,lastWatchedAt=dates.last())
    }
    fun add(m:MediaItem,at:Long,now:Long=System.currentTimeMillis()):MediaItem {
        require(m.kind==MediaKind.MOVIE) { "Choisis un film ou un épisode, pas la série entière." }
        val (count,dates)=history(m.watched,m.plays,m.lastWatchedAt,m.watchHistory,false,at,now)
        return m.copy(watched=true,plays=count,watchHistory=dates,lastWatchedAt=dates.last())
    }
    private fun encoded(key:String)=URLEncoder.encode(key,StandardCharsets.UTF_8.toString()).replace("+","%20").replace("*","%2A")
    fun notificationAction(id:String)="com.franchiseguide.app.EPISODE."+encoded(id)
    fun uid(key:String)="fg-${encoded(key)}@franchiseguide.local"
    private fun norm(s:String)=java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(java.util.Locale.FRENCH)
    fun agenda(media:List<MediaItem>,episodes:List<Episode>,q:Query=Query()):Agenda {
        require(q.kind=="all"||q.kind in kinds);require(q.from==null||q.to==null||!q.from.isAfter(q.to)) { "La date de début doit précéder la date de fin." }
        val parents=media.filter{it.added&&!it.isSuggestion}.associateBy{it.id};val out=mutableListOf<Event>();val seen=hashSetOf<String>();var undated=0;var duplicates=0
        fun push(kind:String,id:String,parent:MediaItem,date:LocalDate?,title:String,key:String,watched:Boolean,skipped:Boolean=false,season:Int?=null) {
            if(q.kind!="all"&&q.kind!=kind||parent.hidden&&!q.includeHidden||q.favoritesOnly&&!parent.favorite||q.unwatchedOnly&&(watched||skipped))return
            if(season==0&&!q.includeSpecials||skipped&&!q.includeSkipped)return
            if(q.query.isNotBlank()&&!norm(title).contains(norm(q.query.take(250))))return
            if(date==null){undated++;return};if(q.from!=null&&date.isBefore(q.from)||q.to!=null&&date.isAfter(q.to))return
            if(!seen.add(key)){duplicates++;return}
            out+=Event(key,uid(key),date,kind,parent.id,id,title,if(kind=="episode")"Date de diffusion indiquée dans la fiche. Horaire et disponibilité locale non confirmés." else "Date de sortie indiquée dans la fiche. Disponibilité locale non confirmée.")
        }
        parents.values.filter{it.kind!=MediaKind.SHOW}.forEach { m->val kind=m.kind.name.lowercase();val key=kind+":"+(m.tmdbId?.let{"tmdb:$it"}?:m.sourceId?.let{"${m.source}:$it"}?:m.id);push(kind,m.id,m,m.releaseDate,m.title,key,m.watched) }
        episodes.forEach{e->val m=parents[e.showId]?:return@forEach;if(m.kind!=MediaKind.SHOW)return@forEach
            val code=if(e.seasonUnknown||e.numberUnknown)"Épisode à identifier" else e.code
            val title=m.title+" · "+code+if(q.hideSpoilers&&!e.watched)"" else " · "+e.title
            val key="episode:"+(e.tmdbId?.let{"tmdb:$it"}?:e.tvdbId?.let{"tvdb:$it"}?:"${m.id}:${e.id}")
            push("episode",e.id,m,e.airDate,title,key,e.watched,e.skipped,e.season)
        }
        return Agenda(out.sortedWith(compareBy<Event>{it.date}.thenBy{it.title}.thenBy{it.key}),undated,duplicates)
    }
    fun escape(value:String)=value.replace("\\","\\\\").replace("\r\n","\\n").replace("\r","\\n").replace("\n","\\n").replace(";","\\;").replace(",","\\,").filter { it.code==9||it.code>=32&&it.code!=127 }
    fun fold(line:String):String {
        val parts=mutableListOf<String>();var piece=StringBuilder();var bytes=0
        line.codePoints().forEach { cp->val c=String(Character.toChars(cp));val n=c.toByteArray(StandardCharsets.UTF_8).size
            if(bytes+n>75){parts+=piece.toString();piece=StringBuilder(" ");bytes=1};piece.append(c);bytes+=n
        };parts+=piece.toString();return parts.joinToString("\r\n")
    }
    fun calendar(events:List<Event>,now:Instant=Instant.now()):String {
        require(events.size<=10000) { "Export limité à 10 000 événements : réduis les filtres. Aucun fichier partiel créé." }
        val seen=hashSetOf<String>();val stamp=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(now)
        val lines=mutableListOf("BEGIN:VCALENDAR","VERSION:2.0","PRODID:-//FranchiseGuide//Release agenda 0.17//FR","CALSCALE:GREGORIAN")
        for(e in events){require(e.date.year in 1..9999&&seen.add(e.uid)&&!e.uid.contains('\r')&&!e.uid.contains('\n')) { "Événement invalide ou dupliqué." }
            lines+=listOf("BEGIN:VEVENT","UID:${e.uid}","DTSTAMP:$stamp","DTSTART;VALUE=DATE:${e.date.format(DateTimeFormatter.BASIC_ISO_DATE)}")
            if(e.date!=LocalDate.of(9999,12,31))lines+="DTEND;VALUE=DATE:${e.date.plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE)}"
            lines+=listOf("SUMMARY:${escape(e.title)}","DESCRIPTION:${escape(e.description)}","CLASS:PRIVATE","TRANSP:TRANSPARENT","END:VEVENT")
        };lines+="END:VCALENDAR";return lines.joinToString("\r\n",postfix="\r\n",transform=::fold)
    }
}
