import com.franchiseguide.app.data.GamingCodec
import com.franchiseguide.app.model.*
import org.json.JSONArray
import org.json.JSONObject

// Requires a real org.json implementation. The typecheck-stubs parser must never run these tests.
private var assertions=0
private fun verify(name:String,condition:Boolean){check(condition){name};assertions++;println("PASS $name")}
private fun rejects(name:String,action:()->Any?)=verify(name,runCatching(action).isFailure)

fun main() {
    val retro=GamingAccount("retro-account","retro","01ARZ3NDEKTSV4RRFFQ69G5FAV","Profil de test",autoSync=true,autoImport=true,lastLibrarySyncAt=1700000000123,avatarUrl="https://media.retroachievements.org/UserPic/Fixture_Player.png",username="Fixture_Player")
    val steam=GamingAccount("steam-account","steam","76561198000000001","Steam de test",autoSync=true,autoImport=true)
    val rows=listOf(
        GameAchievement("1","Premier objectif",groupId="base",earned=true,earnedAt=1700000000000,hardcoreEarned=true,hardcoreEarnedAt=1700000600000,note="Garder ma note",localDone=true,pinned=true,points=10,rarity=AchievementRarity(12.5,"Relevé de test")),
        GameAchievement("2","Objectif normal",groupId="base",earned=true,earnedAt=1700000000001,hardcoreEarned=false),
        GameAchievement("3","Objectif inconnu",groupId="base",earned=null,hardcoreEarned=null,hidden=true)
    )
    val set=AchievementSet("retro-set","retro-game","retro","SNES","1234","Jeu de test",accountId=retro.id,groups=listOf(AchievementGroup("base","Jeu de base","base")),achievements=rows,completeCatalog=true,source="retro-api",observedAt=1700000700000,lastSyncAt=1700000700001)
    val original=GamingState(accounts=listOf(retro,steam),sets=listOf(set))
    fun encoded()=JSONObject(GamingCodec.write(original).toString())
    fun changedAccount(key:String,value:Any)=encoded().apply { getJSONArray("accounts").getJSONObject(0).put(key,value) }

    val decoded=GamingCodec.read(encoded())
    verify("Real JSON round-trip preserves complete Retro and Steam state",decoded==original)
    verify("ULID and visible username are separate after round-trip",decoded.accounts[0].externalId==retro.externalId&&decoded.accounts[0].username=="Fixture_Player")
    verify("Stored preferences survive a local load",decoded.accounts.all { it.autoImport&&it.autoSync })
    verify("Normal and Hardcore dates remain distinct",decoded.sets[0].achievements[0].earnedAt==1700000000000&&decoded.sets[0].achievements[0].hardcoreEarnedAt==1700000600000)
    verify("Missing Hardcore state stays unknown",decoded.sets[0].achievements[2].hardcoreEarned==null)

    val imported=GamingCodec.readPortableImport(encoded())
    verify("Portable import suspends automatic reads for every provider",imported.accounts.all { !it.autoSync&&!it.autoImport })
    verify("Portable import preserves account identity and username",imported.accounts[0].copy(autoSync=true,autoImport=true)==retro)
    verify("Portable import preserves all achievement readings and personal notes",imported.sets==original.sets)
    verify("Re-exporting an imported file cannot revive automatic reads",GamingCodec.read(JSONObject(GamingCodec.write(imported).toString())).accounts.all { !it.autoSync&&!it.autoImport })
    verify("Missing gaming extension remains backward compatible",GamingCodec.readPortableImport(null)==GamingState())

    val legacy=GamingState(accounts=listOf(GamingAccount("legacy","retro","Legacy_Player","Ancien profil")))
    val legacyJson=JSONObject(GamingCodec.write(legacy).toString()).apply { getJSONArray("accounts").getJSONObject(0).remove("username") }
    verify("Legacy named accounts with no username field round-trip",GamingCodec.read(legacyJson)==legacy)
    verify("Legacy account uses its historical profile URL",GamingRules.accountUrl(GamingCodec.read(legacyJson).accounts.single())=="https://retroachievements.org/user/Legacy_Player")
    val noName=GamingCodec.read(changedAccount("username",JSONObject.NULL)).accounts[0]
    verify("ULID without username remains readable without invented profile URL",noName.externalId==retro.externalId&&noName.username==null&&GamingRules.accountUrl(noName)==null)

    rejects("Numeric username is not coerced into a profile name"){GamingCodec.read(changedAccount("username",123))}
    rejects("Array username is rejected"){GamingCodec.read(changedAccount("username",JSONArray()))}
    rejects("Profile path injection is rejected"){GamingCodec.read(changedAccount("username","Fixture/../../logout"))}
    rejects("Overlong profile name is rejected"){GamingCodec.read(changedAccount("username","a".repeat(65)))}
    rejects("Boolean import preference must not be a string"){GamingCodec.read(changedAccount("autoImport","true"))}
    rejects("Import validates malformed flags before suspending them"){GamingCodec.readPortableImport(changedAccount("autoSync","true"))}

    val withUnknownCredentials=encoded().apply {
        put("access_token","TEST_ONLY_TOKEN")
        getJSONArray("accounts").getJSONObject(0).put("apiKey","TEST_ONLY_KEY").put("session",JSONObject().put("secret","TEST_ONLY_SECRET"))
    }
    val clean=GamingCodec.write(GamingCodec.read(withUnknownCredentials)).toString()
    verify("Portable whitelist does not retain unknown keys or credentials",!clean.contains("TEST_ONLY_")&&!clean.contains("apiKey")&&!clean.contains("access_token"))
    verify("Removing unknown credential fields does not discard valid readings",GamingCodec.read(JSONObject(clean))==original)

    val referenceJson=JSONObject().put("accountExternalId",retro.externalId).put("accountUsername",retro.username)
    val reference=GamingCodec.readAccountReference(referenceJson,"retro","new-reference")
    verify("Single-list reference transports ULID plus username",reference?.externalId==retro.externalId&&reference.username==retro.username)
    verify("Single-list reference never enables auto-read or auto-import",reference!=null&&!reference.autoSync&&!reference.autoImport)
    verify("Legacy single-list reference remains supported",GamingCodec.readAccountReference(JSONObject().put("accountExternalId","Legacy_Player"),"retro","legacy-ref")?.username==null)
    verify("Anonymous single-list reference stays anonymous",GamingCodec.readAccountReference(JSONObject(),"retro","unused")==null)
    rejects("Single-list numeric account identity is rejected"){GamingCodec.readAccountReference(JSONObject().put("accountExternalId",1234),"retro","bad")}
    rejects("Single-list username without account identity is rejected"){GamingCodec.readAccountReference(JSONObject().put("accountUsername","Fixture_Player"),"retro","bad")}
    rejects("Single-list malformed optional username is rejected"){GamingCodec.readAccountReference(JSONObject().put("accountExternalId",retro.externalId).put("accountUsername",JSONObject()),"retro","bad")}
    rejects("Manual backup cannot claim a Hardcore-only unlock"){GamingCodec.read(encoded().apply {
        val manualSet=getJSONArray("sets").getJSONObject(0)
        val hardcoreOnly=manualSet.getJSONArray("achievements").getJSONObject(0).put("earned",false).put("earnedAt",JSONObject.NULL)
        manualSet.put("source","manual").put("achievements",JSONArray().put(hardcoreOnly))
    })}

    println("GamingCodecRetro: $assertions assertions passed with a real JSON parser on JVM; no Android build, UI or provider session tested.")
}
