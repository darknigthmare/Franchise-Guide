import com.franchiseguide.app.data.TraktCheckInCodec as Codec
import com.franchiseguide.app.model.*
import org.json.JSONObject

fun main() {
    var assertions=0
    fun verify(value:Boolean){check(value);assertions++}
    val target=TraktCheckInRules.movie(listOf(MediaItem("m",101,MediaKind.MOVIE,"Fixture",runtimeMinutes=90)),"m")
    val session=TraktCheckInRules.create(target,"test-account",null,1700000000000L)
    fun json()=JSONObject(Codec.write(session).toString())
    verify(Codec.read(json())==session)
    for(field in listOf("appRef","parentId","code","lastError","remoteStartedAt","finishAt")) {
        verify(json().isNull(field))
        verify(Codec.read(json().apply{remove(field)})==session)
    }
    fun rejected(field:String,value:Any){verify(runCatching{Codec.read(json().put(field,value))}.isFailure)}
    rejected("version",1.5);rejected("version",2);rejected("version","1")
    rejected("tmdbId",101.9);rejected("tmdbId",4294967296L);rejected("tmdbId","101")
    rejected("startedAt",1700000000000.5);rejected("startedAt",-1)
    rejected("runtimeMinutes",0);rejected("runtimeMinutes",12.5)
    rejected("historyApplied","true");rejected("historyApplied",JSONObject.NULL)
    rejected("account",123);rejected("parentId",123);rejected("appRef",JSONObject())
    rejected("status","invented");rejected("finishAt",1600000000000L)
    verify(runCatching{Codec.read(json().apply{remove("startedAt")})}.isFailure)
    verify(Codec.read(json().put("apiKey","TEST_ONLY_SECRET"))==session)
    verify(!Codec.write(Codec.read(json().put("apiKey","TEST_ONLY_SECRET"))).toString().contains("TEST_ONLY_SECRET"))
    val uncertain=TraktCheckInRules.uncertain(TraktCheckInRules.beginFinish(session,1700000060000L),"finish","Lost response")
    verify(Codec.read(JSONObject(Codec.write(uncertain).toString()))==uncertain)
    verify(TraktCheckInRules.completionMode(Codec.read(JSONObject(Codec.write(uncertain).toString())),1700000070000L)=="verify")
    println("Trakt check-in codec: $assertions assertions passed with real org.json, no Android device.")
}
