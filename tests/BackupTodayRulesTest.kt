import com.franchiseguide.app.model.*
import java.time.LocalDate

fun main(){var n=0
 fun ok(name:String,value:Boolean){check(value){name};n++;println("PASS $name")}
 fun bad(name:String,fn:()->Unit){ok(name,runCatching(fn).isFailure)}
 val now=1_700_000_000_000L
 val empty=ExternalBackupRecord()
 ok("No verified external backup is due",BackupSafetyRules.status(empty,now).due)
 val proposed=BackupSafetyRules.proposed(empty,"json","backup.json",now)
 ok("Proposed file is not verified",BackupSafetyRules.status(proposed,now).pending&&proposed.verifiedAt==null)
 val verified=BackupSafetyRules.verified(proposed,now+1)
 ok("Verified file clears immediate reminder",!BackupSafetyRules.status(verified,now+13*BackupSafetyRules.DAY).due)
 ok("Reminder returns after threshold",BackupSafetyRules.status(verified,now+15*BackupSafetyRules.DAY).due)
 val snoozed=BackupSafetyRules.snooze(empty,7,now)
 ok("Snooze does not fake verification",!BackupSafetyRules.status(snoozed,now+2*BackupSafetyRules.DAY).due&&snoozed.verifiedAt==null)
 bad("Invalid filename refused"){BackupSafetyRules.proposed(empty,"json","bad\nname",now)}
 bad("Invalid snooze refused"){BackupSafetyRules.snooze(empty,0,now)}
 val day=LocalDate.of(2023,11,14)
 val media=listOf(MediaItem(id="show",kind=MediaKind.SHOW,title="Série"),MediaItem(id="movie",kind=MediaKind.MOVIE,title="Film",releaseDate=day,runtimeMinutes=100),MediaItem(id="game",kind=MediaKind.GAME,title="Jeu",trackingStatus="active"))
 val episodes=listOf(Episode(id="ep",showId="show",season=1,number=1,title="Épisode",airDate=day,runtimeMinutes=45))
 val queue=listOf(PendingChange("a","movie",1,"A","history",true,uncertain=true),PendingChange("b","movie",2,"B","history",true,heldReason="hold"),PendingChange("c","movie",3,"C","history",true,uncertain=true,heldReason="hold"))
 val snapshot=TodayRules.build(media,episodes,AppSettings(),queue,null,BackupSafetyRules.status(empty,now),day,12)
 ok("Today contains dated agenda",snapshot.agendaTotal==2)
 ok("Today contains ready next-up records",snapshot.nextTotal==3&&snapshot.next.any{it.episodeId=="ep"})
 ok("Uncertain and held counted separately",snapshot.uncertain==2&&snapshot.held==2)
 ok("Today result is bounded",TodayRules.build(media,episodes,AppSettings(),queue,null,null,day,1).agenda.size==1)
 bad("Invalid Today limit refused"){TodayRules.build(media,episodes,AppSettings(),queue,null,null,day,0)}
 println("BackupTodayRulesTest: $n assertions passed on JVM; no file existence or notification scheduler claim.")
}
