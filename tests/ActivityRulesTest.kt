import com.franchiseguide.app.model.*
import java.time.*
import java.nio.file.*
import java.nio.charset.StandardCharsets

fun main(args:Array<String>){
 var passed=0
 fun checkCase(name:String,condition:Boolean){check(condition){name};passed++;println("PASS $name")}
 fun rejects(name:String,f:()->Unit){checkCase(name,runCatching(f).isFailure)}
 val now=Instant.parse("2026-11-06T12:00:00Z").toEpochMilli();val later=Instant.parse("2026-10-01T18:00:00Z").toEpochMilli();val earlier=Instant.parse("2026-09-01T18:00:00Z").toEpochMilli()
 val e=Episode("e","s",1,1,"Titre secret",LocalDate.of(2026,10,6),tmdbId=33)
 val m=MediaItem("m",11,MediaKind.MOVIE,"Film QA",releaseDate=LocalDate.of(2026,10,6))
 val s=MediaItem("s",22,MediaKind.SHOW,"Série QA")
 val paris=ZoneId.of("Europe/Paris")
 checkCase("One UTC instant for ordinary Paris time",ActivityRules.localChoices("2026-10-06","20:30",paris)==listOf(Instant.parse("2026-10-06T18:30:00Z").toEpochMilli()))
 checkCase("Spring gap never guessed",ActivityRules.localChoices("2026-03-29","02:30",paris).isEmpty())
 val repeated=ActivityRules.localChoices("2026-10-25","02:30",paris)
 checkCase("Fall overlap exposes two UTC instants",repeated==listOf(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(),Instant.parse("2026-10-25T01:30:00Z").toEpochMilli()))
 val lord=ActivityRules.localChoices("2026-04-05","01:45",ZoneId.of("Australia/Lord_Howe"));checkCase("Half-hour repeated time handled",lord.size==2&&lord[1]-lord[0]==1800000L)
 rejects("Impossible date rejected"){ActivityRules.localChoices("2026-02-30","20:30",paris)}
 rejects("Invalid hour rejected"){ActivityRules.localChoices("2026-10-06","24:00",paris)}
 rejects("Minute precision is explicit"){ActivityRules.localChoices("2026-10-06","20:30:00",paris)}
 val seen=ActivityRules.add(e,later,now);checkCase("First dated event sets seen and one date",seen.watched&&seen.plays==1&&seen.watchHistory==listOf(later))
 val back=ActivityRules.add(seen,earlier,now);checkCase("Backdated replay preserves latest date",back.lastWatchedAt==later&&back.plays==2&&back.watchHistory==listOf(earlier,later))
 checkCase("Legacy counts do not invent dates",ActivityRules.add(e.copy(watched=true,plays=5),earlier,now).let{it.plays==6&&it.watchHistory==listOf(earlier)})
 checkCase("Skipped episode reactivated by explicit dated viewing",ActivityRules.add(e.copy(skipped=true),earlier,now).let{it.watched&&!it.skipped})
 rejects("Duplicate exact second refused"){ActivityRules.add(seen,later+123,now)}
 rejects("Future date refused"){ActivityRules.add(e,now+1,now)}
 rejects("Nonpositive date refused"){ActivityRules.add(e,0,now)}
 rejects("Whole series dated completion refused"){ActivityRules.add(s,later,now)}
 rejects("Game dated completion is not movie watching"){ActivityRules.add(m.copy(kind=MediaKind.GAME),later,now)}
 val oldRec=TrackingRules.records(listOf(s),listOf(seen));val newRec=TrackingRules.records(listOf(s),listOf(back));val operations=TrackingRules.changes(oldRec,newRec)
 checkCase("Outbox uses added historical timestamp",operations.size==1&&operations.single().watchedAt==earlier)
 checkCase("Unchanged outbox never resends dated events",TrackingRules.changes(newRec,newRec).isEmpty())
 val both=TrackingRules.changes(TrackingRules.records(listOf(s),listOf(e)),newRec)
 checkCase("Two new dates generate two distinct operations",both.map{it.watchedAt}==listOf(earlier,later))
 checkCase("Count-only correction never resends last viewing",TrackingRules.changes(oldRec,TrackingRules.records(listOf(s),listOf(seen.copy(plays=4)))).isEmpty())
 val watchedFilm=ActivityRules.add(m,later,now);checkCase("Film date recorded independently",watchedFilm.plays==1&&watchedFilm.watchHistory==listOf(later))
 val all=ActivityRules.agenda(listOf(s,m,m.copy(id="g",kind=MediaKind.GAME,tmdbId=null),m.copy(id="b",kind=MediaKind.BOOK,tmdbId=null)),listOf(e))
 checkCase("Agenda gathers four kinds",all.events.map{it.kind}.toSet()==setOf("episode","movie","game","book"))
 checkCase("Unknown dates counted not invented",ActivityRules.agenda(listOf(s,m.copy(releaseDate=null)),listOf(e.copy(airDate=null))).let{it.undated==2&&it.events.isEmpty()})
 checkCase("Hidden default excluded",ActivityRules.agenda(listOf(s.copy(hidden=true)),listOf(e)).events.isEmpty())
 checkCase("Hidden opt-in supported",ActivityRules.agenda(listOf(s.copy(hidden=true)),listOf(e),ActivityRules.Query(includeHidden=true)).events.size==1)
 checkCase("Specials default excluded",ActivityRules.agenda(listOf(s),listOf(e.copy(season=0))).events.isEmpty())
 checkCase("Specials opt-in supported",ActivityRules.agenda(listOf(s),listOf(e.copy(season=0)),ActivityRules.Query(includeSpecials=true)).events.size==1)
 checkCase("Orphan episode excluded",ActivityRules.agenda(emptyList(),listOf(e)).events.isEmpty())
 checkCase("Removed title excluded",ActivityRules.agenda(listOf(m.copy(added=false)),emptyList()).events.isEmpty())
 checkCase("Suggested title excluded",ActivityRules.agenda(listOf(m.copy(isSuggestion=true)),emptyList()).events.isEmpty())
 checkCase("Unwatched filter excludes watched",ActivityRules.agenda(listOf(m.copy(watched=true)),emptyList(),ActivityRules.Query(unwatchedOnly=true)).events.isEmpty())
 checkCase("Favorite filter uses parent show",ActivityRules.agenda(listOf(s.copy(favorite=true),m),listOf(e),ActivityRules.Query(favoritesOnly=true)).events.size==1)
 checkCase("Date boundaries inclusive",ActivityRules.agenda(listOf(m),emptyList(),ActivityRules.Query(from=m.releaseDate,to=m.releaseDate)).events.size==1)
 rejects("Reversed calendar range rejected"){ActivityRules.agenda(listOf(m),emptyList(),ActivityRules.Query(from=LocalDate.of(2026,10,7),to=m.releaseDate))}
 checkCase("Unknown type rejected",runCatching{ActivityRules.agenda(emptyList(),emptyList(),ActivityRules.Query(kind="other"))}.isFailure)
 checkCase("Spoilers do not leak through search",ActivityRules.agenda(listOf(s),listOf(e),ActivityRules.Query(query="secret",hideSpoilers=true)).events.isEmpty())
 checkCase("Duplicate source events counted once",ActivityRules.agenda(listOf(s),listOf(e,e.copy(id="another"))).let{it.events.size==1&&it.duplicates==1})
 val events=ActivityRules.agenda(listOf(m),emptyList()).events;val ics=ActivityRules.calendar(events,Instant.ofEpochMilli(now))
 checkCase("Calendar full-day date and exclusive end",ics.contains("DTSTART;VALUE=DATE:20261006\r\nDTEND;VALUE=DATE:20261007"))
 checkCase("No invented timezone or reminder",!ics.contains("TZID")&&!ics.contains("VALARM"))
 checkCase("Calendar marked private and transparent",ics.contains("CLASS:PRIVATE")&&ics.contains("TRANSP:TRANSPARENT"))
 checkCase("New date keeps stable UID",events.first().uid==ActivityRules.agenda(listOf(m.copy(releaseDate=m.releaseDate!!.plusDays(1))),emptyList()).events.first().uid)
 val injected=ActivityRules.calendar(listOf(events[0].copy(title="Texte; a,b\\c\r\nBEGIN:VALARM")),Instant.ofEpochMilli(now))
 checkCase("Calendar text cannot inject another component",!injected.contains("\r\nBEGIN:VALARM")&&injected.contains("Texte\\; a\\,b\\\\c\\nBEGIN:VALARM"))
 val folded=ActivityRules.fold("SUMMARY:"+"Épisode 日本語 🐈 ".repeat(80))
 checkCase("UTF-8 fold is at most 75 bytes",folded.split("\r\n").all{it.toByteArray(StandardCharsets.UTF_8).size<=75})
 checkCase("Folding roundtrips Unicode",folded.replace("\r\n ","")=="SUMMARY:"+"Épisode 日本語 🐈 ".repeat(80))
 rejects("Duplicate UID export refused"){ActivityRules.calendar(listOf(events[0],events[0]))}
 rejects("Large export refuses silent truncation"){ActivityRules.calendar(List(10001){events[0]})}
 checkCase("Last supported day has no overflowing end",ActivityRules.calendar(listOf(events[0].copy(date=LocalDate.of(9999,12,31)))).let{it.contains("99991231")&&!it.contains("DTEND")})
 checkCase("Known Java hash collision fixture","Aa".hashCode()=="BB".hashCode())
 checkCase("Full notification identity disambiguates hash collisions",ActivityRules.notificationAction("Aa")!=ActivityRules.notificationAction("BB"))
 checkCase("Identity escaping is unambiguous",ActivityRules.notificationAction("a/b")!=ActivityRules.notificationAction("a%2Fb"))
 checkCase("Missing sync acknowledgement cannot succeed",!ActivityRules.acknowledgedCounts(listOf(null,null)))
 checkCase("Malformed sync counts refused",!ActivityRules.acknowledgedCounts(listOf(-1,0.5,Double.NaN,Double.POSITIVE_INFINITY)))
 checkCase("Valid sync count acknowledged",ActivityRules.acknowledgedCounts(listOf(null,1)))
 if(args.isNotEmpty()) {
  val parity=events[0].copy(key="movie:clé *",uid=ActivityRules.uid("movie:clé *"),title="Film très long 日本語 🐈 ".repeat(15)+"; virgule, antislash\\\nSecond line",description="Description\nHoraire inconnu.")
  Files.writeString(Path.of(args[0]),ActivityRules.calendar(listOf(parity),Instant.parse("2026-10-06T12:00:00Z")),StandardCharsets.UTF_8)
 }
 println("$passed Activity V0.16 JVM assertions passed. No Android SDK or device.")
}
