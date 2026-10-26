import com.franchiseguide.app.model.*
import java.time.*
import java.nio.file.*
private val AT=Instant.parse("2026-10-06T12:00:00Z").toEpochMilli()
private val TODAY=LocalDate.of(2026,10,6)
private var n=0
private fun yes(name:String,v:Boolean){check(v){name};n++;println("PASS $name")}
private fun no(name:String,f:()->Unit){yes(name,runCatching(f).isFailure)}
private fun ep(id:String,season:Int=1)=Episode(id,"s",season,id.removePrefix("e").toInt(),"Épisode $id",LocalDate.of(2026,1,1),tmdbId=100+id.removePrefix("e").toInt())
private fun sample()=EpisodeBatchRules.State(listOf(MediaItem("s",123,MediaKind.SHOW,"Série test",lastWatchedAt=AT-1000)),listOf(ep("e1"),ep("e2").copy(watched=true,plays=3,lastWatchedAt=AT-1000,watchHistory=listOf(AT-2000,AT-1000),userNote="Privé",rating=8),ep("e3").copy(skipped=true),ep("e4").copy(airDate=LocalDate.of(2030,1,1)),ep("e5").copy(airDate=null),ep("e6",0)))
private fun plan(s:EpisodeBatchRules.State,ids:List<String>,action:String="watch",include:Boolean=false)=EpisodeBatchRules.plan(s,"s",ids,action,include,AT,TODAY)
private fun apply(s:EpisodeBatchRules.State,p:EpisodeBatchRules.Plan)=EpisodeBatchRules.apply(s,p,AT+1000)
fun main(args:Array<String>){
 val s=sample();val view=EpisodeBatchRules.view(s,"s",EpisodeBatchRules.Query(today=TODAY))
 yes("Specials hidden by default",view.rows.size==5&&view.total==6)
 yes("Specials explicit filter",EpisodeBatchRules.view(s,"s",EpisodeBatchRules.Query(includeSpecials=true,today=TODAY)).rows.size==6)
 yes("Watched filter",EpisodeBatchRules.view(s,"s",EpisodeBatchRules.Query(status="watched",today=TODAY)).rows.map{it.id}==listOf("e2"))
 yes("Future filter",EpisodeBatchRules.view(s,"s",EpisodeBatchRules.Query(airing="future",today=TODAY)).rows.map{it.id}==listOf("e4"))
 yes("Unknown filter",EpisodeBatchRules.view(s,"s",EpisodeBatchRules.Query(airing="unknown",today=TODAY)).rows.map{it.id}==listOf("e5"))
 val spoiler=s.copy(episodes=s.episodes.map{if(it.id=="e1")it.copy(title="Révélation secrète") else it})
 yes("Hidden title not searchable",EpisodeBatchRules.view(spoiler,"s",EpisodeBatchRules.Query(query="revelation",hideSpoilers=true,today=TODAY)).rows.isEmpty())
 yes("Visible Unicode search",EpisodeBatchRules.view(spoiler,"s",EpisodeBatchRules.Query(query="revelation",today=TODAY)).rows.size==1)
 val p=plan(s,listOf("e1","e2","e3","e4","e5"));yes("Preview changes only one eligible row",p.counts.changed==1&&p.counts.unchanged==4)
 yes("Future unknown skipped exclusions reported",p.counts.dateProtected==2&&p.counts.skippedProtected==1)
 val updated=apply(s,p);yes("Original remains untouched",!s.episodes[0].watched)
 yes("First viewing has real proposed date",updated.episodes[0].watchHistory==listOf(AT)&&updated.episodes[0].lastWatchedAt==AT)
 yes("Already seen is idempotent",updated.episodes[1]==s.episodes[1])
 yes("Skip target protected",updated.episodes[2]==s.episodes[2])
 yes("Future date not rewritten",updated.episodes[3]==s.episodes[3])
 val force=apply(s,plan(s,listOf("e4","e5"),include=true))
 yes("Explicit date override allowed",force.episodes[3].watched&&force.episodes[4].watched&&force.episodes[4].airDate==null)
 val skip=plan(s,listOf("e1","e2"),"skip")
 yes("Skip never loses viewed history",skip.counts.historyProtected==1&&apply(s,skip).episodes[1]==s.episodes[1])
 yes("Single native skip also protects history",TrackingRules.episode(s.episodes[1],"skip")==s.episodes[1])
 for(extra in listOf(ep("e1").copy(plays=2),ep("e1").copy(lastWatchedAt=AT-2),ep("e1").copy(watchHistory=listOf(AT-2))))yes("Residual history signal protected",plan(s.copy(episodes=listOf(extra)),listOf("e1"),"skip").counts.historyProtected==1)
 val erased=plan(s,listOf("e1","e2"),"unwatch")
 yes("Explicit erase reports known dates and counted plays",erased.counts.knownDatesRemoved==2&&erased.counts.playsRemoved==3L)
 val cleared=apply(s,erased).episodes[1];yes("Explicit erase clears only history",!cleared.watched&&cleared.plays==0&&cleared.watchHistory.isEmpty()&&cleared.lastWatchedAt==null&&cleared.userNote=="Privé"&&cleared.rating==8)
 val coll=plan(s,listOf("e2","e3","e4"),"collect");val cr=apply(s,coll)
 yes("Collection changes no history",cr.episodes[1]==s.episodes[1].copy(collected=true))
 yes("Collection works on future and ignored",cr.episodes[2].collected&&cr.episodes[3].collected)
 val afterNote=s.copy(episodes=s.episodes.map{if(it.id=="e1")it.copy(userNote="Récente",rating=9) else it})
 yes("Later note retained",apply(afterNote,p).episodes[0].userNote=="Récente")
 yes("Later rating retained",apply(afterNote,p).episodes[0].rating==9)
 val later=s.copy(episodes=s.episodes.map{if(it.id=="e2")it.copy(watchHistory=it.watchHistory+AT,plays=4,lastWatchedAt=AT) else it})
 yes("Collection preview preserves newer viewing",apply(later,coll).episodes[1].plays==4)
 no("Targeted change blocks whole batch"){apply(s.copy(episodes=s.episodes.map{if(it.id=="e4")it.copy(collected=true) else it}),coll)}
 no("Changed provider identity blocks plan"){apply(s.copy(episodes=s.episodes.map{if(it.id=="e1")it.copy(tmdbId=888) else it}),p)}
 no("Removed episode rejected"){apply(s.copy(episodes=s.episodes.drop(1)),p)}
 no("Removed parent rejected"){apply(s.copy(media=s.media.map{it.copy(added=false)}),p)}
 no("Unknown action rejected"){plan(s,listOf("e1"),"magic")}
 no("Empty selection rejected"){plan(s,emptyList())}
 no("Wrong parent selected rejected"){plan(s.copy(episodes=s.episodes+ep("e7").copy(showId="other")),listOf("e7"))}
 no("Duplicate stored identity rejected"){plan(s.copy(episodes=s.episodes+ep("e1")),listOf("e1"))}
 yes("Duplicate selection counted once",plan(s,listOf("e1","e1")).counts.selected==1)
 no("Tampered result rejected"){apply(s,p.copy(counts=p.counts.copy(changed=50)))}
 no("Expired preview rejected"){EpisodeBatchRules.apply(s,p,AT+600001)}
 no("Clock rollback rejected"){EpisodeBatchRules.apply(s,p,AT-120001)}
 yes("Last series activity updated",updated.media[0].lastWatchedAt==AT)
 val large=s.copy(episodes=(1..175).map{ep("e$it")});yes("Whole filtered list beyond visible rows",apply(large,plan(large,large.episodes.map{it.id},"collect")).episodes.count{it.collected}==175)
 val max=s.copy(episodes=(1..10001).map{ep("e$it")});no("10k safety cap no truncation"){plan(max,max.episodes.map{it.id},"collect")}
 val counts=listOf(p.counts,skip.counts,erased.counts,coll.counts)
 val json="["+counts.joinToString(","){"{\"selected\":${it.selected},\"changed\":${it.changed},\"unchanged\":${it.unchanged},\"historyProtected\":${it.historyProtected},\"skippedProtected\":${it.skippedProtected},\"dateProtected\":${it.dateProtected},\"knownDatesRemoved\":${it.knownDatesRemoved},\"playsRemoved\":${it.playsRemoved},\"providerChanges\":${it.providerChanges}}"}+"]"
 if(args.isNotEmpty())Files.writeString(Path.of(args[0]),json)
 println("$n EpisodeBatchRules assertions passed on JVM, no Android SDK.")
}
