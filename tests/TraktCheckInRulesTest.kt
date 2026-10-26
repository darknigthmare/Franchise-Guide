import com.franchiseguide.app.model.*
import java.io.File

fun main(args:Array<String>){var n=0
 fun ok(name:String,value:Boolean){check(value){name};n++;println("PASS $name")}
 fun bad(name:String,fn:()->Unit){ok(name,runCatching(fn).isFailure)}
 fun state()=TraktCheckInState(
  media=listOf(MediaItem(id="movie",kind=MediaKind.MOVIE,title="Film",tmdbId=101,runtimeMinutes=120,watchHistory=emptyList(),plays=0),MediaItem(id="show",kind=MediaKind.SHOW,title="Série",tmdbId=201)),
  episodes=listOf(Episode(id="ep",showId="show",season=1,number=1,title="Pilote",airDate=null,tmdbId=301,runtimeMinutes=45,watchHistory=emptyList(),plays=0))
 )
 val movie=TraktCheckInRules.movie(state().media,"movie")
 val episode=TraktCheckInRules.episode(state().media,state().episodes,"ep")
 ok("Exact movie identity",movie.identity=="movie:movie:101")
 ok("Exact episode identity",episode.identity=="episode:show:ep:301")
 bad("Removed movie target refused"){TraktCheckInRules.movie(state().media.map{if(it.id=="movie")it.copy(added=false) else it},"movie")}
 bad("Duplicate episode target refused"){TraktCheckInRules.episode(state().media,state().episodes+state().episodes.first(),"ep")}
 val s=TraktCheckInRules.create(movie,"qa-user","slot-app",1_700_000_000_000)
 ok("Runtime produces expected end",s.expectedEndAt==s.startedAt+120*60_000)
 ok("Context bound to account and app",TraktCheckInRules.sameContext(s,"qa-user","slot-app")&&!TraktCheckInRules.sameContext(s,"other","slot-app"))
 ok("Start confirmation leaves local start intact",TraktCheckInRules.started(s,1_700_000_001_000).remoteStartedAt==1_700_000_001_000)
 ok("Uncertain start remains non-history",TraktCheckInRules.uncertain(s,"start","lost").status=="uncertain-start")
 val finish=TraktCheckInRules.beginFinish(s,1_700_000_060_000)
 ok("Finish timestamp retained across retry",TraktCheckInRules.beginFinish(TraktCheckInRules.uncertain(finish,"finish","timeout"),1_700_000_120_000).finishAt==finish.finishAt)
 val applied=TraktCheckInRules.apply(state(),finish,finish.finishAt!!,finish.finishAt!!+1)
 ok("Movie applied exactly once",applied.media.first().watched&&applied.media.first().plays==1&&applied.media.first().watchHistory==listOf(finish.finishAt))
 ok("Idempotent same second apply",TraktCheckInRules.apply(applied,finish,finish.finishAt!!,finish.finishAt!!+1)==applied)
 val epSession=TraktCheckInRules.beginFinish(TraktCheckInRules.create(episode,"qa","slot",1_700_000_000_000),1_700_000_030_000)
 val epApplied=TraktCheckInRules.apply(state(),epSession,epSession.finishAt!!,epSession.finishAt!!+1)
 ok("Episode applies and parent activity updates",epApplied.episodes.first().watched&&epApplied.media[1].lastWatchedAt==epSession.finishAt)
 bad("Changed provider identity blocks apply"){TraktCheckInRules.apply(state().copy(media=state().media.map{if(it.id=="movie")it.copy(tmdbId=999) else it}),finish,finish.finishAt!!,finish.finishAt!!+1)}
 ok("Recent matching exact identity",TraktCheckInRules.recentMatch(finish,listOf(101 to finish.finishAt!!),finish.finishAt!!+1000)==finish.finishAt)
 ok("Recent mismatch stays absent",TraktCheckInRules.recentMatch(finish,listOf(102 to finish.finishAt!!),finish.finishAt!!+1000)==null)
 val timing=TraktCheckInRules.timing(s,s.startedAt+60_000)
 ok("Timing is read-only countdown",timing.remaining==7_140_000L&&!timing.due&&s.status=="starting")
 ok("Known runtime verifies remotely only when due",TraktCheckInRules.completionMode(s,s.expectedEndAt!!-1)=="write"&&TraktCheckInRules.completionMode(s,s.expectedEndAt)=="verify")
 ok("Persisted finish verifies before estimated runtime",TraktCheckInRules.completionMode(finish,finish.finishAt!!+1000)=="verify")
 ok("Uncertain finish never requests another history write",TraktCheckInRules.completionMode(TraktCheckInRules.uncertain(finish,"finish","reply lost"),finish.finishAt!!+1000)=="verify")
 ok("Unknown-duration persisted finish also verifies",TraktCheckInRules.completionMode(finish.copy(runtimeMinutes=null,expectedEndAt=null),finish.finishAt!!+1000)=="verify")
 ok("Confirmed viewing routes to cleanup only",TraktCheckInRules.completionMode(TraktCheckInRules.confirmed(finish),finish.finishAt!!+1000)=="cleanup")
 ok("Exactly one added viewing is acknowledged",TraktCheckInRules.historyAcknowledged(1,0))
 ok("Exactly one existing viewing is acknowledged",TraktCheckInRules.historyAcknowledged(null,1))
 ok("Missing acknowledgement is rejected",!TraktCheckInRules.historyAcknowledged(null,null))
 ok("Zero acknowledgement cannot mark watched",!TraktCheckInRules.historyAcknowledged(0,0))
 ok("More than one acknowledgement is inconsistent",!TraktCheckInRules.historyAcknowledged(1,1)&&!TraktCheckInRules.historyAcknowledged(2,null))
 ok("Negative and fractional counts are rejected",!TraktCheckInRules.historyAcknowledged(-1,1)&&!TraktCheckInRules.historyAcknowledged(0.5,0.5))
 ok("Nonfinite acknowledgement is rejected",!TraktCheckInRules.historyAcknowledged(Double.NaN,1)&&!TraktCheckInRules.historyAcknowledged(Double.POSITIVE_INFINITY,null))
 ok("Remote history timestamp replaces tentative finish",TraktCheckInRules.remoteHistoryFinish(finish,finish.finishAt!!-1000).finishAt==finish.finishAt!!-1000)
 ok("History before local start is ignored",TraktCheckInRules.recentMatch(s,listOf(101 to s.startedAt-1),s.startedAt+1000)==null)
 ok("Confirmed requires cleanup state",TraktCheckInRules.confirmed(finish).status=="cleanup")
 bad("Invalid status refused"){TraktCheckInRules.validate(s.copy(status="done"))}
 bad("Control character account refused"){TraktCheckInRules.create(movie,"bad\naccount",null,1)}
 args.firstOrNull()?.let { File(it).writeText(listOf(movie.identity,episode.identity,s.expectedEndAt,timing.remaining,timing.percent,TraktCheckInRules.recentMatch(finish,listOf(101 to finish.finishAt!!),finish.finishAt!!+1000),applied.media.first().plays,epApplied.episodes.first().plays,TraktCheckInRules.completionMode(s,s.expectedEndAt!!)).joinToString("|") ) }
 println("TraktCheckInRulesTest: $n assertions passed on JVM; no Trakt account or Android UI.")
}
