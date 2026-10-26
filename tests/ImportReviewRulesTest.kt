import com.franchiseguide.app.model.*
import java.io.File
private var checks=0
private fun yes(name:String,v:Boolean){check(v){name};checks++;println("PASS $name")}
private fun no(name:String,f:()->Unit)=yes(name,runCatching(f).isFailure)
fun main(args:Array<String>){
 val show=MediaItem("s",kind=MediaKind.SHOW,title="Série QA",lastWatchedAt=1000L,userNote="Note série privée")
 val movie=MediaItem("m",kind=MediaKind.MOVIE,title="Film QA",watched=true,plays=2,watchHistory=listOf(1000L,2000L),lastWatchedAt=2000L,userNote="Note film privée",rating=8)
 val game=MediaItem("g",kind=MediaKind.GAME,title="Jeu QA")
 val book=MediaItem("b",kind=MediaKind.BOOK,title="Livre QA")
 val ep=Episode("e","s",1,1,"Titre épisode privé",null,watched=true,watchHistory=listOf(1000L,1001L),lastWatchedAt=1000L,userNote="Note épisode privée",rating=9)
 val list=UserList("l","Liste QA");val fr=FranchiseDefinition("f","Franchise QA",emptyList(),custom=true)
 val account=GamingAccount("a","manual","qa","Profil privé")
 val aset=AchievementSet("set","g","manual","PC","qa","Jeu QA",accountId="a",achievements=listOf(GameAchievement("1","Succès QA")))
 val state=ImportSnapshot(listOf(show,movie,game,book),listOf(ep),emptyList(),listOf(list),listOf(fr),GamingState(accounts=listOf(account),sets=listOf(aset)),AppSettings())
 val counts=ImportReviewRules.counts(state)
 yes("All four categories counted",counts.shows==1&&counts.movies==1&&counts.games==1&&counts.books==1&&counts.titles==4)
 yes("Episode counts retained",counts.episodes==1&&counts.watchedEpisodes==1)
 yes("Dates deduplicated by second and series aggregate excluded",counts.dates==3)
 yes("Series episode and film private notes all counted",counts.notes==3)
 yes("Local lists and franchises counted",counts.lists==1&&counts.franchises==1)
 yes("Gaming references and achievements counted",counts.accounts==1&&counts.sets==1&&counts.achievements==1)
 yes("Removed media not active titles",ImportReviewRules.counts(state.copy(media=state.media+movie.copy(id="removed",added=false))).titles==4)
 yes("Suggestions not active titles",ImportReviewRules.counts(state.copy(media=state.media+movie.copy(id="suggestion",isSuggestion=true))).titles==4)
 yes("Skipped watched flag excluded from watched episodes",ImportReviewRules.counts(state.copy(episodes=listOf(ep.copy(skipped=true)))).watchedEpisodes==0)
 val identical=ImportReviewRules.compare(state,state)
 yes("Identical import has no added or removed title",identical.count("added")==0&&identical.count("removed")==0)
 yes("Identical import has four unchanged records",identical.count("unchanged")==4)
 yes("Identical import loses no private data",identical.absentDates==0&&identical.changedNotes==0&&identical.changedRatings==0&&identical.removedWatched==0)
 val changed=state.copy(media=listOf(show,movie.copy(title="Film corrigé",userNote="Révision",rating=null),game,book.copy(id="other")),episodes=listOf(ep.copy(userNote=null,watchHistory=emptyList(),lastWatchedAt=null,watched=false,rating=null)))
 val diff=ImportReviewRules.compare(state,changed)
 yes("Different local id is removal plus addition not title merge",diff.count("removed")==1&&diff.count("added")==1)
 yes("Edited media counted once",diff.count("modified")==1)
 yes("Episode missing known date reported",diff.absentDates==1)
 yes("Private revised or missing notes reported",diff.changedNotes==2)
 yes("Missing scores reported",diff.changedRatings==2)
 yes("Removed watched state reported",diff.removedWatched==1)
 yes("Removed local id identified",diff.rows.single{it.change=="removed"}.id=="b")
 yes("Comparisons do not expose private note bodies",diff.rows.none{it.title.contains("privée")})
 yes("Source snapshot not mutated",state.media[1].userNote=="Note film privée"&&state.episodes[0].watched)
 val empty=state.copy(media=emptyList(),episodes=emptyList(),lists=emptyList(),franchises=emptyList(),gaming=GamingState())
 val loss=ImportReviewRules.compare(state,empty)
 yes("Empty replacement shows all removed titles",loss.count("removed")==4)
 yes("Empty replacement reports all known dates",loss.absentDates==3)
 yes("Empty replacement reports all notes and scores",loss.changedNotes==3&&loss.changedRatings==2)
 val prepared=PreparedLibraryImport("portable",listOf("qa.json"),state,changed,3)
 ImportReviewRules.guard(prepared,state,3);yes("Same snapshot generation permitted",true)
 no("Changed generation refuses stale preview"){ImportReviewRules.guard(prepared,state,4)}
 no("Changed title refuses stale preview"){ImportReviewRules.guard(prepared,state.copy(media=listOf(movie)),3)}
 no("Changed episode note refuses stale preview"){ImportReviewRules.guard(prepared,state.copy(episodes=listOf(ep.copy(userNote="plus récent"))),3)}
 no("Changed local settings refuses stale preview"){ImportReviewRules.guard(prepared,state.copy(settings=state.settings.copy(language="en-US")),3)}
 no("Changed gaming references refuses stale preview"){ImportReviewRules.guard(prepared,state.copy(gaming=GamingState()),3)}
 no("Changed list refuses stale preview"){ImportReviewRules.guard(prepared,state.copy(lists=emptyList()),3)}
 yes("Filter searches entire comparison",ImportReviewRules.rows(diff,"FILM").single().id=="m")
 yes("Category filter works",ImportReviewRules.rows(diff,kind=MediaKind.BOOK).size==2)
 yes("Change filter works",ImportReviewRules.rows(diff,change="added").single().id=="other")
 no("Invalid filter refused"){ImportReviewRules.rows(diff,change="guessed")}
 no("No document refused"){ImportReviewRules.checkDocuments(emptyList(),false)}
 no("Empty text refused"){ImportReviewRules.checkDocuments(listOf(""),true)}
 no("Multiple portable files refused"){ImportReviewRules.checkDocuments(listOf("{}","{}"),true)}
 no("Eleven SeriesGuide files refused"){ImportReviewRules.checkDocuments(List(11){"[]"},false)}
 ImportReviewRules.checkDocuments(List(10){"[]"},false);yes("Ten SeriesGuide files accepted within limit",true)
 ImportReviewRules.checkDocuments(listOf("{}"),true);yes("One portable JSON accepted",true)
 val many=state.copy(media=(1..130).map { movie.copy(id="f$it",title=if(it==130)"Fin de liste" else "Film $it") })
 yes("Filter can reach records beyond fifty",ImportReviewRules.rows(ImportReviewRules.compare(empty,many),"Fin de liste").single().id=="f130")
 val privateEpisode=ep.copy(userNote="Transport",watchHistory=listOf(1000L),rating=7)
 yes("Episode copies retain private note field",privateEpisode.copy(title="Nouveau titre").userNote=="Transport")
 yes("Documented ISO show timestamp accepted",ImportReviewRules.seriesGuideDate("2018-02-02T08:00:00Z").toString()=="2018-02-02")
 yes("Episode timestamp milliseconds accepted",ImportReviewRules.seriesGuideDate(1517558400000L).toString()=="2018-02-02")
 yes("Offset consistently becomes UTC calendar day",ImportReviewRules.seriesGuideDate("2026-03-01T01:00:00+02:00").toString()=="2026-02-28")
 yes("Unknown date sentinels retained as unknown",ImportReviewRules.seriesGuideDate(-1)==null&&ImportReviewRules.seriesGuideDate(0)==null)
 yes("Calendar date without time remains supported",ImportReviewRules.seriesGuideDate("2024-02-29").toString()=="2024-02-29")
 no("Impossible imported day refused"){ImportReviewRules.seriesGuideDate("2026-02-31T08:00:00Z")}
 no("Impossible imported time refused"){ImportReviewRules.seriesGuideDate("2026-02-28T24:00:00Z")}
 no("Unknown date object not coerced"){ImportReviewRules.seriesGuideDate(listOf("2026-01-01"))}
 var opened=0;var closed=0
 fun input(s:String):()->java.io.InputStream = { opened++;object:java.io.ByteArrayInputStream(s.toByteArray()){override fun close(){closed++;super.close()}} }
 val small=ImportReviewRules.readDocuments(listOf(input("[]"),input("{}")),false,maxFile=2,maxTotal=4)
 yes("Sequential strict reader accepts exact cumulative byte budget",small==listOf("[]","{}"))
 yes("Every successful stream closed",opened==2&&closed==2)
 opened=0;closed=0
 no("Cumulative budget checked before reading later files"){ImportReviewRules.readDocuments(listOf(input("[]"),input("{}"),input("[]")),false,maxFile=2,maxTotal=3)}
 yes("Oversize stream closed and subsequent input never opened",opened==2&&closed==2)
 no("Reader counts UTF8 bytes not characters"){ImportReviewRules.readDocuments(listOf(input("éé")),true,maxFile=3,maxTotal=3)}
 no("Reader refuses corrupt UTF8"){ImportReviewRules.readDocuments(listOf({java.io.ByteArrayInputStream(byteArrayOf(0xc3.toByte(),0x28))}),true)}
 opened=0;closed=0
 no("Too many files refused before opening any"){ImportReviewRules.readDocuments(List(11){input("[]")},false)}
 yes("Invalid count opens no streams",opened==0)
 val bom=byteArrayOf(0xef.toByte(),0xbb.toByte(),0xbf.toByte(),0x5b,0x5d)
 no("BOM bytes count toward cumulative budget"){ImportReviewRules.readDocuments(listOf({java.io.ByteArrayInputStream(bom)},input("[]")),false,maxFile=5,maxTotal=6)}
 if(args.isNotEmpty())File(args[0]).writeText("""{"titles":${counts.titles},"show":${counts.shows},"movie":${counts.movies},"game":${counts.games},"book":${counts.books},"episodes":${counts.episodes},"watchedEpisodes":${counts.watchedEpisodes},"datedViewings":${counts.dates},"notes":${counts.notes},"lists":${counts.lists},"franchises":${counts.franchises},"accounts":${counts.accounts},"sets":${counts.sets},"achievements":${counts.achievements}}""")
 println("ImportReviewRules: $checks assertions passed on JVM; no Android SDK or actual JSON parser.")
}
