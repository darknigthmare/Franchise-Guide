package com.franchiseguide.app.network

import com.franchiseguide.app.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Fixed public GETs, no credentials, bounded body. UI cancellation rejects stale results; socket abort is not guaranteed. */
class PublicTvDetailsClient(private val read:(String)->String=::readUrl) {
    fun episode(q:EpisodeDetailQuery):EpisodeDetailSnapshot {
        require(q.provider=="tvmaze");val eid=q.episodeId?:error("Identifiant épisode absent.")
        val main=objectMap(JSONObject(read("https://api.tvmaze.com/episodes/$eid?embed=show")),0)
        val guests=list(JSONArray(read("https://api.tvmaze.com/episodes/$eid/guestcast")),0)
        val crew=list(JSONArray(read("https://api.tvmaze.com/episodes/$eid/guestcrew")),0)
        return PublicTvDetailRules.episode(main,guests,crew,q)
    }
    fun person(id:Int):PersonProfile {
        require(id>0);val main=objectMap(JSONObject(read("https://api.tvmaze.com/people/$id")),0)
        val cast=list(JSONArray(read("https://api.tvmaze.com/people/$id/castcredits?embed=show")),0)
        val crew=list(JSONArray(read("https://api.tvmaze.com/people/$id/crewcredits?embed=show")),0)
        return PublicTvDetailRules.person(main,cast,crew,id)
    }
    private fun objectMap(o:JSONObject,depth:Int):Map<String,Any?> {require(depth<=20&&o.length()<=1000);val result=linkedMapOf<String,Any?>();val keys=o.keys();while(keys.hasNext()){val k=keys.next();result[k]=value(o.opt(k),depth+1)};return result}
    private fun list(a:JSONArray,depth:Int):List<Any?> {require(depth<=20&&a.length()<=10000);return (0 until a.length()).map{value(a.get(it),depth+1)}}
    private fun value(v:Any?,depth:Int):Any? {require(depth<=20);return when(v){null,JSONObject.NULL->null;is JSONObject->objectMap(v,depth);is JSONArray->list(v,depth);else->v}}
    companion object {
        @Volatile private var retryAt=0L
        fun readUrl(url:String):String {
            val uri=URI(url);require(uri.scheme=="https"&&uri.host=="api.tvmaze.com"&&uri.userInfo==null&&uri.port==-1)
            check(System.currentTimeMillis()>=retryAt){"Délai TVmaze en cours. Réessaie ultérieurement."}
            val c=uri.toURL().openConnection() as HttpURLConnection;c.requestMethod="GET";c.instanceFollowRedirects=false;c.connectTimeout=8000;c.readTimeout=20000;c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","FranchiseGuide/0.27 (personal TV catalogue)")
            try{
                val code=c.responseCode
                if(code==429){val raw=c.getHeaderField("Retry-After");val now=System.currentTimeMillis();val wait=raw?.toLongOrNull()?.coerceIn(0,86400)?.times(1000)?:runCatching{ZonedDateTime.parse(raw,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-now}.getOrDefault(60000);retryAt=now+wait.coerceIn(60000,86400000)}
                require(code in 200..299){"Lecture TVmaze indisponible (HTTP $code). Aucune donnée locale remplacée."}
                val max=2*1024*1024;require(c.contentLengthLong<0||c.contentLengthLong<=max){"Réponse TVmaze trop grande."}
                val bytes=c.inputStream.use { input->val out=ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=max){"Réponse TVmaze trop grande."};out.write(buffer,0,n)};out.toByteArray() }
                return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            }finally{c.disconnect()}
        }
    }
}
