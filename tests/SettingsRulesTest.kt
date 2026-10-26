import com.franchiseguide.app.model.*
fun main(){var count=0
 fun ok(n:String,b:Boolean){check(b){n};count++;println("PASS $n")};fun bad(n:String,f:()->Unit){ok(n,runCatching(f).isFailure)}
 val b=AppSettings();val n=SettingsRules.normalize(b.copy(language=" FR_fr ",region=" us "))
 ok("Canonical locale",n.language=="fr-FR"&&n.region=="US");ok("Two-letter locale",SettingsRules.normalize(b.copy(language="JA")).language=="ja")
 for((name,s) in listOf("bad language" to b.copy(language="bad value"),"long region" to b.copy(region="FRANCE"),"numeric region" to b.copy(region="12"),"blank language" to b.copy(language=""),"blank region" to b.copy(region=""),"negative days" to b.copy(recentDays=-1),"too many days" to b.copy(upcomingDays=36501),"zero backup" to b.copy(autoBackupMinutes=0),"zero sync" to b.copy(traktSyncIntervalHours=0),"long sync" to b.copy(traktSyncIntervalHours=169),"bad volume" to b.copy(detailJingleVolume=101),"bad hour" to b.copy(notificationHour=24),"secret without client" to b.copy(traktClientSecret="secret"))){bad(name){SettingsRules.normalize(s)}}
 ok("Zero days unlimited",SettingsRules.normalize(b.copy(recentDays=0)).recentDays==0);ok("Public keys optional",SettingsRules.normalize(b).tmdbApiKey.isEmpty());ok("Public ID-only allowed",SettingsRules.normalize(b.copy(traktClientId="public")).traktClientSecret.isEmpty())
 ok("Trim access margins",SettingsRules.normalize(b.copy(tmdbApiKey=" key ")).tmdbApiKey=="key");bad("Control injection refused"){SettingsRules.normalize(b.copy(rawgApiKey="PRIVATE\r\nHEADER"))}
 val e=runCatching{SettingsRules.normalize(b.copy(rawgApiKey="PRIVATE VALUE"))}.exceptionOrNull();ok("Secret not in error",e!=null&&!e.message.orEmpty().contains("PRIVATE"))
 ok("Public changes not private",!SettingsRules.privateChanged(b,b.copy(darkTheme=true)));ok("TMDb not Trakt change",!SettingsRules.traktChanged(b,b.copy(tmdbApiKey="other")));ok("Secret is app change",SettingsRules.traktChanged(b,b.copy(traktClientSecret="other")));ok("Changed key recognized",SettingsRules.privateChanged(b,b.copy(rawgApiKey="other")))
 ok("Integer maximum",SettingsRules.number(" 168 ","interval",1,168)==168);for(v in listOf("1.2","1e2","","999999999999999999999"))bad("Invalid integer $v"){SettingsRules.number(v,"interval",1,168)}
 ok("Valid private ref",SettingsRules.validReference("slot-abc-def"));ok("Path ref refused",!SettingsRules.validReference("../data"))
 println("SettingsRulesTest: $count assertions passed; not an Android runtime test.")
}
