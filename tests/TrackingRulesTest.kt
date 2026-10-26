import com.franchiseguide.app.model.*
import java.time.LocalDate

private var count=0
private fun test(label:String,block:()->Boolean){check(block()){label};count++;println("ok $count - $label")}
fun main(){
    val e=Episode("ep","s",1,1,"Pilot",LocalDate.of(2020,1,1),tmdbId=30)
    val show=MediaItem("s",1,MediaKind.SHOW,"Test",status=ShowStatus.CONTINUING)
    val movie=MediaItem("m",1,MediaKind.MOVIE,"Movie")
    test("Returning series normalized"){TrackingRules.normalizedStatus("Returning Series")==ShowStatus.CONTINUING}
    test("Cancelled normalized"){TrackingRules.normalizedStatus("cancelled")==ShowStatus.CANCELED}
    test("Unknown retained"){TrackingRules.normalizedStatus("random")==ShowStatus.UNKNOWN}
    test("First viewing"){TrackingRules.episode(e,"watch",100).let{it.watched && it.plays==1 && it.lastWatchedAt==100L}}
    val watched=TrackingRules.episode(e,"watch",100)
    test("Repeated watch not duplicate"){TrackingRules.episode(watched,"watch",200).lastWatchedAt==100L}
    test("First replay not two"){TrackingRules.episode(e,"replay",100).plays==1}
    test("Actual replay increment"){TrackingRules.episode(watched,"replay",200).plays==2}
    test("Unwatch clears history"){TrackingRules.episode(watched,"unwatch").let{!it.watched && it.plays==0 && it.lastWatchedAt==null}}
    test("Skip preserves watched history"){TrackingRules.episode(watched,"skip")==watched}
    test("Unskip no fabricated watch"){TrackingRules.episode(TrackingRules.episode(e,"skip"),"skip").let{!it.skipped && !it.watched}}
    test("Watch clears skip"){TrackingRules.episode(e.copy(skipped=true),"watch").let{it.watched && !it.skipped}}
    test("Collect toggle"){TrackingRules.episode(e,"collect").collected}
    val today=LocalDate.of(2026,10,4)
    test("Unlimited future calendar"){TrackingRules.withinCalendar(today.plusYears(4),today,false,0)}
    test("Bounded future calendar"){!TrackingRules.withinCalendar(today.plusDays(61),today,false,60)}
    test("Unlimited recent calendar"){TrackingRules.withinCalendar(today.minusYears(4),today,true,0)}
    test("Unknown air date excluded"){!TrackingRules.withinCalendar(null,today,false,0)}
    test("Inclusive date boundary"){TrackingRules.withinCalendar(today,today,false,1)}
    test("Past not upcoming"){!TrackingRules.withinCalendar(today.minusDays(1),today,false,0)}
    val items=listOf(show,show.copy(id="end",status=ShowStatus.ENDED))
    test("Include status"){TrackingRules.filterStatus(items,mapOf("CONTINUING" to 1)).size==1}
    test("Exclude status"){TrackingRules.filterStatus(items,mapOf("ENDED" to -1)).single().id=="s"}
    test("Unknown years last both directions"){listOf(true,false).all{TrackingRules.sort(listOf(movie,show.copy(year=2000)),SortMode.YEAR,it,listOf(e)).last()==movie}}
    test("Every sort works"){SortMode.entries.all{TrackingRules.sort(listOf(movie,show),it,true,listOf(e)).size==2}}
    val before=TrackingRules.records(listOf(show,movie),listOf(e))
    test("Scoped identity avoids movie show collision"){before.map{it.key}.distinct().size==3}
    test("Games books never sent"){TrackingRules.records(listOf(movie.copy(kind=MediaKind.GAME),movie.copy(kind=MediaKind.BOOK)),emptyList()).isEmpty()}
    test("No initial whole library history upload"){TrackingRules.changes(before,before).isEmpty()}
    val after=TrackingRules.records(listOf(show,movie),listOf(watched))
    val op=TrackingRules.changes(before,after).single()
    test("Only newly watched episode enqueued"){op.type=="episode" && op.field=="history" && op.watchedAt==100L}
    test("No duplicate after acknowledgement baseline"){TrackingRules.changes(after,after).isEmpty()}
    test("Replay one operation"){TrackingRules.changes(after,TrackingRules.records(listOf(show,movie),listOf(watched.copy(plays=2,lastWatchedAt=200)))).size==1}
    test("Explicit unwatch removal"){TrackingRules.changes(after,before).single().path=="/sync/history/remove"}
    test("Skip not silent remote deletion"){TrackingRules.changes(after,TrackingRules.records(listOf(show,movie),listOf(e.copy(skipped=true)))).isEmpty()}
    test("Rating clear removal"){TrackingRules.changes(listOf(TrackingRecord("movie",1,"x",rating=8)),listOf(TrackingRecord("movie",1,"x"))).single().path=="/sync/ratings/remove"}
    test("Only changed collection"){TrackingRules.changes(before,TrackingRules.records(listOf(show,movie.copy(collected=true)),listOf(e))).single().field=="collection"}
    val local=TrackingRecord("episode",30,"x",true,1,100,collected=true,rating=8)
    val remote=TrackingRecord("episode",30,"x")
    test("Initial remote absence preserves imported tracking"){TrackingRules.reconcile(local,remote,null,emptyList())==local}
    test("Unchanged remote absence preserves imports on second sync"){TrackingRules.reconcile(local,remote,remote,emptyList())==local}
    test("Confirmed remote history deletion applies"){!TrackingRules.reconcile(local,remote,local,emptyList()).watched}
    test("Confirmed remote collection deletion applies"){!TrackingRules.reconcile(local,remote,local,emptyList()).collected}
    test("Confirmed remote rating deletion applies"){TrackingRules.reconcile(local,remote,local,emptyList()).rating==null}
    test("Pending local edit protected"){TrackingRules.reconcile(local,remote,local,listOf(op)).watched}
    test("Pending local unwatch protected from stale remote"){!TrackingRules.reconcile(remote,local,local,listOf(op.copy(enabled=false))).watched}
    test("Remote positive merged"){TrackingRules.reconcile(remote,local,null,emptyList()).watched}
    test("Skipped remains local"){!TrackingRules.reconcile(remote.copy(skipped=true),local,null,emptyList()).watched}
    test("Other entity pending does not mask update"){TrackingRules.reconcile(remote,local,null,listOf(op.copy(tmdbId=90))).watched}
    println("$count executable JVM tracking tests passed.")
}
