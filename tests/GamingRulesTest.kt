import com.franchiseguide.app.model.*
private var total=0
private fun ok(name:String,condition:Boolean){check(condition){name};total++;println("PASS $name")}
private fun reject(name:String,block:()->Any?)=ok(name,runCatching(block).isFailure)
fun main(){
 val account=GamingAccount("p","psn","Fixture_Player","Test")
 val a=GameAchievement("a","Objectif de test",groupId="base",grade="bronze",earned=false)
 val groups=listOf(AchievementGroup("base","Base","base"),AchievementGroup("dlc","Extension de test","dlc"))
 val s=AchievementSet("s","game","psn","PS5","NPWR_FIXTURE","Fiction QA",accountId="p",groups=groups,achievements=listOf(a,a.copy(id="secret",hidden=true,earned=null),a.copy(id="dlc",groupId="dlc",earned=true,earnedAt=1700000000000)),completeCatalog=true)
 val g=GamingState(accounts=listOf(account),sets=listOf(s))
 ok("Valid game/account/group state",GamingRules.validate(g,setOf("game"))==g)
 ok("Unknown unlocks never counted as locked/earned",GamingRules.progress(s).earned==1&&GamingRules.progress(s).unknown==1)
 ok("DLC group counted independently",GamingRules.progress(s,"dlc").earned==1&&GamingRules.progress(s,"base").earned==0)
 ok("Local checklist is separate",GamingRules.progress(s.copy(achievements=listOf(a.copy(localDone=true)))).earned==0)
 ok("No inferred platinum at 100 percent",!GamingRules.progress(s.copy(achievements=listOf(a.copy(earned=true)))).platinum)
 ok("An actual platinum record is recognized",GamingRules.progress(s.copy(achievements=listOf(a.copy(grade="platinum",earned=true)))).platinum)
 ok("Empty set not complete",!GamingRules.progress(s.copy(achievements=emptyList())).complete)
 ok("Empty percentage unknown",GamingRules.progress(s.copy(achievements=emptyList())).percent==null)
 ok("Partial catalog not certified complete",!GamingRules.progress(s.copy(completeCatalog=false,achievements=listOf(a.copy(earned=true)))).complete)
 reject("Future extension version rejected"){GamingRules.validate(g.copy(version=2))}
 reject("Orphan game rejected"){GamingRules.validate(g,setOf("different"))}
 reject("Nonmatching provider account rejected"){GamingRules.validate(g.copy(sets=listOf(s.copy(provider="xbox"))))}
 reject("Duplicate account reference rejected"){GamingRules.validate(g.copy(accounts=listOf(account,account.copy(id="different",externalId="fixture_player"))))}
 reject("Duplicate local achievement rejected"){GamingRules.validate(g.copy(sets=listOf(s.copy(achievements=listOf(a,a)))))}
 reject("Unknown group rejected"){GamingRules.validate(g.copy(sets=listOf(s.copy(achievements=listOf(a.copy(groupId="no"))))))}
 reject("Timestamp without unlock rejected"){GamingRules.validate(g.copy(sets=listOf(s.copy(achievements=listOf(a.copy(earnedAt=1))))))}
 reject("Hardcore timestamp without unlock rejected"){GamingRules.validate(g.copy(sets=listOf(s.copy(achievements=listOf(a.copy(hardcoreEarnedAt=1))))))}
 reject("Negative score rejected"){GamingRules.validate(g.copy(sets=listOf(s.copy(achievements=listOf(a.copy(points=-1))))))}
 reject("NaN rarity rejected"){GamingRules.validate(g.copy(sets=listOf(s.copy(achievements=listOf(a.copy(rarity=AchievementRarity(Double.NaN,"test")))))))}
 reject("Wrong progress rejected"){GamingRules.validate(g.copy(sets=listOf(s.copy(achievements=listOf(a.copy(progressCurrent=10.0,progressTarget=5.0))))))}
 reject("Manual list cannot unlock official achievement"){GamingRules.validate(g.copy(sets=listOf(s.copy(source="manual"))))}
 reject("Duplicate edition identity rejected"){GamingRules.validate(g.copy(sets=listOf(s,s.copy(id="two"))))}
 ok("PS4/PS5 lists remain separate",GamingRules.validate(g.copy(sets=listOf(s,s.copy(id="two",platform="PS4")))).sets.size==2)
 ok("Separate edition remains independent",GamingRules.validate(g.copy(sets=listOf(s,s.copy(id="two",edition="Remastered")))).sets.size==2)
 val old=s.copy(achievements=s.achievements.map { it.copy(note="Garder",pinned=true,localDone=true) })
 val merged=GamingRules.merge(old,s)
 ok("Sync preserves notes",merged.achievements.all { it.note=="Garder" })
 ok("Sync preserves pins/checklist",merged.achievements.all { it.pinned&&it.localDone })
 reject("Partial sync preserves old data by refusing merge"){GamingRules.merge(old,s.copy(achievements=listOf(a)))}
 reject("Wrong account never merged"){GamingRules.merge(old,s.copy(accountId="other"))}
 ok("Unsafe icon URL rejected",GamingRules.safeUrl("javascript:alert(1)")==null)
 ok("Credentials in HTTPS URL rejected",GamingRules.safeUrl("https://user:secret@example.test")==null)
 ok("HTTPS image kept",GamingRules.safeUrl("https://example.test/image.png")!=null)
 reject("Steam numeric account must be exactly 17 digits"){GamingRules.validate(GamingState(accounts=listOf(GamingAccount("a","steam","not-id","Invalid"))))}
 reject("PSN profile injection rejected"){GamingRules.validate(GamingState(accounts=listOf(account.copy(externalId="a/<script>"))))}
 val many=s.copy(achievements=(0 until 5000).map { a.copy(id="id$it") })
 ok("5000 achievements permitted",GamingRules.validate(g.copy(sets=listOf(many))).sets[0].achievements.size==5000)
 reject("5001 achievements refused"){GamingRules.validate(g.copy(sets=listOf(many.copy(achievements=many.achievements+a))))}
 val retro=GamingAccount("retro","retro","01ARZ3NDEKTSV4RRFFQ69G5FAV","Profil de test",autoSync=true,autoImport=true,username="Fixture_Player")
 val retroSet=AchievementSet("retro-set","game","retro","SNES","1234","Jeu rétro de test",accountId="retro",groups=listOf(AchievementGroup("base","Jeu de base","base")),achievements=listOf(
   a.copy(id="both",grade="achievement",earned=true,hardcoreEarned=true),
   a.copy(id="normal",grade="achievement",earned=true,hardcoreEarned=false),
   a.copy(id="unknown-hardcore",grade="achievement",earned=false,hardcoreEarned=null)
 ),completeCatalog=true,source="retro-api")
 ok("Retro ULID account supports saved import preference",GamingRules.validate(GamingState(accounts=listOf(retro),sets=listOf(retroSet))).accounts.single().autoImport)
 ok("Retro profile URL uses username instead of ULID",GamingRules.accountUrl(retro)=="https://retroachievements.org/user/Fixture_Player")
 ok("ULID without username does not fabricate a user URL",GamingRules.accountUrl(retro.copy(username=null))==null)
 ok("Legacy named Retro profile remains usable",GamingRules.accountUrl(retro.copy(externalId="Legacy_Player",username=null))=="https://retroachievements.org/user/Legacy_Player")
 ok("Renaming display username does not change list identity",GamingRules.validate(GamingState(accounts=listOf(retro.copy(username="New_Player")),sets=listOf(retroSet))).sets.single().accountId=="retro")
 reject("Retro username cannot inject profile path"){GamingRules.validate(GamingState(accounts=listOf(retro.copy(username="user/../../logout"))))}
 ok("Invalid Retro URL is refused even before validation",GamingRules.accountUrl(retro.copy(username="user?token=x"))==null)
 reject("Unrelated provider cannot request library import"){GamingRules.validate(g.copy(accounts=listOf(account.copy(autoImport=true))))}
 ok("Normal and Hardcore counters stay independent",GamingRules.progress(retroSet).earned==2&&GamingRules.progress(retroSet,hardcore=true).earned==1)
 ok("Unknown Hardcore state stays unknown",GamingRules.progress(retroSet,hardcore=true).unknown==1)
 ok("Normal unlock never implies Hardcore completion",!GamingRules.progress(retroSet.copy(achievements=retroSet.achievements.take(2)),hardcore=true).complete)
 val allHardcore=retroSet.copy(achievements=retroSet.achievements.map { it.copy(earned=true,hardcoreEarned=true) })
 ok("Complete Hardcore catalogue does not infer platinum",GamingRules.progress(allHardcore,hardcore=true).complete&&!GamingRules.progress(allHardcore,hardcore=true).platinum&&!GamingRules.progress(allHardcore).platinum)
 ok("Local checklist never counts as Hardcore unlock",GamingRules.progress(retroSet.copy(achievements=listOf(a.copy(earned=false,localDone=true,hardcoreEarned=false))),hardcore=true).earned==0)
 reject("Manual list cannot claim a Hardcore unlock"){GamingRules.validate(GamingState(accounts=listOf(retro),sets=listOf(retroSet.copy(source="manual",achievements=listOf(a.copy(earned=false,hardcoreEarned=true))))))}
 ok("Mode selection reads the recorded unlock only",GamingRules.earnedState(retroSet.achievements[1])==true&&GamingRules.earnedState(retroSet.achievements[1],hardcore=true)==false)
 ok("Steam profile URL remains explicit",GamingRules.accountUrl(GamingAccount("steam","steam","76561198000000001","Fixture"))=="https://steamcommunity.com/profiles/76561198000000001")
 ok("PSNProfiles URL remains compatible",GamingRules.accountUrl(account)=="https://psnprofiles.com/Fixture_Player")
 println("GamingRules: $total assertions passed on JVM, no Android UI/runtime.")
}
