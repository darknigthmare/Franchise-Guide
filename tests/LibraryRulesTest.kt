import com.franchiseguide.app.model.*
import java.time.LocalDate
private var count=0
private fun ok(name:String,value:Boolean){check(value){name};count++;println("PASS $name")}
private fun no(name:String,f:()->Unit){ok(name,runCatching(f).isFailure)}
fun main(){
 val today=LocalDate.of(2026,10,6);val query=LibraryRules.Query(today=today)
 val show=MediaItem("s",kind=MediaKind.SHOW,title="Série QA",catalogComplete=true)
 fun e(id:String="e1",n:Int=1,date:LocalDate?=today.minusDays(3))=Episode(id,"s",1,n,"Titre secret $n",date,runtimeMinutes=45)
 fun next(ms:List<MediaItem> =listOf(show),es:List<Episode> =listOf(e()),q:LibraryRules.Query=query)=LibraryRules.nextUp(ms,es,q)
 ok("Next episode follows ordinal, not input order",next(es=listOf(e("e2",2),e())).records.single().episodeId=="e1")
 ok("Watched ignored, not replayed",next(es=listOf(e().copy(watched=true),e("e2",2))).records.single().episodeId=="e2")
 ok("Skipped ignored without deleting state",next(es=listOf(e().copy(skipped=true),e("e2",2))).records.single().episodeId=="e2")
 ok("Future earlier episode blocks later aired shortcut",next(es=listOf(e(date=today.plusDays(5)),e("e2",2))).records.single().status=="future")
 ok("Unknown earlier date not skipped",next(es=listOf(e(date=null),e("e2",2))).records.single().status=="unknown")
 ok("Unnumbered earlier episode avoids arbitrary selection",next(es=listOf(e().copy(numberUnknown=true),e("e2",2))).records.single().episodeId==null)
 ok("Duplicate numbering refuses arbitrary selection",next(es=listOf(e("a"),e("b"))).records.single().episodeId==null)
 ok("Specials excluded by default",next(es=listOf(e("sp").copy(season=0),e())).records.single().episodeId=="e1")
 ok("Specials explicitly included",next(es=listOf(e("sp").copy(season=0),e()),q=query.copy(includeSpecials=true)).records.single().episodeId=="sp")
 ok("No episodes is not caught-up",next(es=emptyList()).records.single().status=="unknown")
 ok("Incomplete metadata is not caught-up",next(listOf(show.copy(catalogComplete=false)),listOf(e().copy(watched=true))).records.single().status=="unknown")
 ok("Mismatch of declared count not caught-up",next(listOf(show.copy(episodeCount=20)),listOf(e().copy(watched=true))).records.single().status=="unknown")
 ok("Complete loaded watched show is caught-up not necessarily ended",next(es=listOf(e().copy(watched=true))).records.single().status=="caught-up")
 ok("Removed media excluded",next(listOf(show.copy(added=false))).records.isEmpty())
 ok("Suggestions excluded",next(listOf(show.copy(isSuggestion=true))).records.isEmpty())
 ok("Hidden excluded",next(listOf(show.copy(hidden=true))).records.isEmpty())
 ok("Hidden explicit included",next(listOf(show.copy(hidden=true)),q=query.copy(includeHidden=true)).records.size==1)
 ok("Favorites filter",next(q=query.copy(favoritesOnly=true)).records.isEmpty())
 ok("Accent search",next(q=query.copy(query="serie")).records.size==1)
 ok("Spoiler title not rendered",!next(q=query.copy(hideSpoilers=true)).records.single().title.contains("secret"))
 ok("Spoiler title cannot leak through search",next(q=query.copy(query="secret",hideSpoilers=true)).records.isEmpty())
 ok("Episode code remains searchable",next(q=query.copy(query="S01E01",hideSpoilers=true)).records.size==1)
 ok("Zero runtime unknown",next(es=listOf(e().copy(runtimeMinutes=0))).records.single().minutes==null)
 ok("Unknown runtime excluded by duration budget",next(es=listOf(e().copy(runtimeMinutes=0)),q=query.copy(maxMinutes=60)).records.isEmpty())
 ok("Duration includes upper boundary",next(q=query.copy(maxMinutes=45)).records.size==1)
 ok("Duration excludes longer episode",next(q=query.copy(maxMinutes=44)).records.isEmpty())
 val movie=show.copy(id="m",kind=MediaKind.MOVIE,watched=false)
 ok("Movie without release date not assumed available",next(listOf(movie)).records.single().status=="unknown")
 ok("Finished movie excluded",next(listOf(movie.copy(watched=true))).records.isEmpty())
 val book=show.copy(id="b",kind=MediaKind.BOOK,progressCurrent=25,progressTotal=100,progressUnit="pages")
 ok("Book can be resumed without fabricated release date",next(listOf(book)).records.single().status=="ready")
 ok("Book has no invented session duration",next(listOf(book)).records.single().minutes==null)
 ok("Book excluded from known-duration filter",next(listOf(book),q=query.copy(maxMinutes=30)).records.isEmpty())
 ok("Future game in future section",next(listOf(book.copy(kind=MediaKind.GAME,releaseDate=today.plusDays(1)))).records.single().status=="future")
 no("Unknown kind rejected"){next(q=query.copy(kind="person"))};no("Negative duration rejected"){next(q=query.copy(maxMinutes=-1))}
 val lists=listOf(UserList("old","Avant"),UserList("new","Après"),UserList("later","Plus tard"))
 val ms=listOf(movie.copy(userNote="Keep",watchHistory=listOf(1000),plays=1,watched=true,listIds=setOf("old")),book.copy(favorite=true))
 val p=LibraryRules.plan(ms,lists,listOf("m","b","m"),LibraryRules.Action.FAVORITE_ON)
 ok("Selection deduplicated",p.selectedCount==2);ok("Only changed favourites in plan",p.patches.size==1&&p.unchanged==1)
 val applied=LibraryRules.apply(ms,lists,p)
 ok("Original state unmodified",!ms[0].favorite);ok("New favourite applied",applied[0].favorite)
 ok("History and notes retained",applied[0].watchHistory==listOf(1000L)&&applied[0].userNote=="Keep")
 val updated=applied.map{if(it.id=="m")it.copy(rating=9,userNote="New",watchHistory=listOf(1000,2000),plays=2)else it}
 val undone=LibraryRules.apply(updated,lists,p,true)
 ok("Undo restores only target favourite",!undone[0].favorite);ok("Undo keeps new personal history",undone[0].plays==2&&undone[0].watchHistory==listOf(1000L,2000L)&&undone[0].rating==9&&undone[0].userNote=="New")
 val lp=LibraryRules.plan(ms,lists,listOf("m"),LibraryRules.Action.LIST_ADD,"new");val la=LibraryRules.apply(ms,lists,lp)
 ok("List add retains previous memberships",la[0].listIds==setOf("old","new"))
 val lu=LibraryRules.apply(la.map{it.copy(listIds=it.listIds+"later")},lists,lp,true)
 ok("Undo list add retains unrelated later membership",lu[0].listIds==setOf("old","later"))
 val rp=LibraryRules.plan(ms,lists,listOf("m"),LibraryRules.Action.LIST_REMOVE,"old");val ra=LibraryRules.apply(ms,lists,rp)
 ok("List removal targets exactly one list",ra[0].listIds.isEmpty());ok("Undo removal restores membership",LibraryRules.apply(ra,lists,rp,true)[0].listIds==setOf("old"))
 no("Changed target field refuses stale preview"){LibraryRules.apply(applied,lists,p)}
 no("Changed target field refuses unsafe undo"){LibraryRules.apply(ms,lists,p,true)}
 no("Removed media refuses whole batch"){LibraryRules.apply(ms.drop(1),lists,p)}
 no("Missing destination list refuses preview"){LibraryRules.plan(ms,emptyList(),listOf("m"),LibraryRules.Action.LIST_ADD,"new")}
 no("Deleted destination refuses apply"){LibraryRules.apply(ms,emptyList(),lp)}
 no("Deleted destination refuses undo"){LibraryRules.apply(la,emptyList(),lp,true)}
 no("Unsupported missing selection refuses partial application"){LibraryRules.plan(ms,lists,listOf("m","missing"),LibraryRules.Action.HIDE)}
 no("Empty selection refused"){LibraryRules.plan(ms,lists,emptyList(),LibraryRules.Action.HIDE)}
 no("Selection cap enforced"){LibraryRules.plan(ms,lists,List(10001){"m"},LibraryRules.Action.HIDE)}
 val hide=LibraryRules.plan(ms,lists,listOf("m"),LibraryRules.Action.HIDE);val hidden=LibraryRules.apply(ms,lists,hide)
 ok("Hide does not remove title or its history",hidden.size==ms.size&&hidden[0].hidden&&hidden[0].watched&&hidden[0].plays==1)
 val unhide=LibraryRules.plan(hidden,lists,listOf("m"),LibraryRules.Action.UNHIDE)
 ok("Unhide explicit",!LibraryRules.apply(hidden,lists,unhide)[0].hidden)
 println("LibraryRules V0.17: $count JVM assertions passed, no Android runtime involved")
}
