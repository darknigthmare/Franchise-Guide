import com.franchiseguide.app.model.*
private var count=0
private fun verify(name:String, condition:Boolean){check(condition){name};count++;println("PASS $name")}
private fun reject(name:String, block:()->Any)=verify(name,runCatching(block).isFailure)
fun main(){
 val id="76561198000000001"
 val sample=SteamGameMetadata("570",mapOf(id to SteamPlaytime(120,1700000000000)))
 verify("Known playtime preserved",SteamMetadataRules.validate(sample).accounts[id]?.playtimeMinutes==120L)
 verify("Unknown playtime not zero",SteamMetadataRules.validate(SteamGameMetadata("570",mapOf(id to SteamPlaytime()))).accounts[id]?.playtimeMinutes==null)
 verify("Two accounts remain distinct",SteamMetadataRules.validate(sample.copy(accounts=sample.accounts+("76561198000000002" to SteamPlaytime(3)))).accounts.size==2)
 for(a in listOf("0","-1","570x","4294967296","../570",""))reject("Reject AppID $a"){SteamMetadataRules.validate(sample.copy(appId=a))}
 for(a in listOf("bad-id","1234567890123456","123456789012345678"))reject("Reject SteamID $a"){SteamMetadataRules.validate(sample.copy(accounts=mapOf(a to SteamPlaytime())))}
 reject("Reject negative minutes"){SteamMetadataRules.validate(sample.copy(accounts=mapOf(id to SteamPlaytime(-1))))}
 reject("Reject unbounded minutes"){SteamMetadataRules.validate(sample.copy(accounts=mapOf(id to SteamPlaytime(10000000001L))))}
 reject("Reject negative last played"){SteamMetadataRules.validate(sample.copy(accounts=mapOf(id to SteamPlaytime(null,-1))))}
 reject("Reject unbounded last played"){SteamMetadataRules.validate(sample.copy(accounts=mapOf(id to SteamPlaytime(null,8640000000000001L))))}
 reject("Bound account count"){SteamMetadataRules.validate(sample.copy(accounts=(0..100).associate{"7656119800000"+it.toString().padStart(4,'0') to SteamPlaytime()}))}
 verify("App ID uint32 maximum valid",SteamMetadataRules.validate(sample.copy(appId="4294967295")).appId=="4294967295")
 verify("No exposed stats preserves false",SteamMetadataRules.validate(sample.copy(hasStats=false)).hasStats==false)
 verify("Unknown stats stays unknown",sample.hasStats==null)
 println("SteamMetadata: $count JVM assertions passed; no Android runtime or authentication.")
}
