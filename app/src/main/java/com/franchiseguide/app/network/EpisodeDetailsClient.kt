package com.franchiseguide.app.network

import com.franchiseguide.app.model.*
import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** GET-only fixed endpoint with strict bounded decoding. Cancellation of the worker socket is not guaranteed. */
class EpisodeDetailsClient {
    companion object { @Volatile private var cooldownUntil=0L }
    class Failure(val status:Int,val retryAt:Long?=null):Exception(when(status){401,403->"Clé TMDb invalide ou accès refusé.";404->"Épisode introuvable chez TMDb pour cette numérotation.";429->"Délai TMDb en cours ; réessaie après l’attente indiquée.";else->"Fiche épisode TMDb indisponible (HTTP $status)."})
    fun details(q:EpisodeDetailQuery,key:String):EpisodeDetailSnapshot {
        require(key.isNotBlank()){"Configure la clé TMDb pour charger cette fiche."};if(System.currentTimeMillis()<cooldownUntil)throw Failure(429,cooldownUntil)
        val enc:(String)->String={URLEncoder.encode(it,Charsets.UTF_8.name())}
        val c=URI("https://api.themoviedb.org/3${q.path}?api_key=${enc(key.trim())}&language=${enc(q.language)}&append_to_response=credits").toURL().openConnection() as HttpURLConnection
        c.requestMethod="GET";c.instanceFollowRedirects=false;c.connectTimeout=10000;c.readTimeout=20000;c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","FranchiseGuide/0.24 (Android)")
        try {
            val status=c.responseCode;if(status !in 200..299){if(status==429){val now=System.currentTimeMillis();val delay=c.getHeaderField("Retry-After")?.toLongOrNull()?.coerceIn(0,86400)?.times(1000)?:runCatching{ZonedDateTime.parse(c.getHeaderField("Retry-After"),DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-now}.getOrDefault(60000);cooldownUntil=now+delay.coerceIn(60000,86400000)};throw Failure(status,if(status==429)cooldownUntil else null)}
            val max=2*1024*1024;require(c.contentLengthLong<0||c.contentLengthLong<=max){"Réponse épisode trop grande."}
            val bytes=c.inputStream.use{input->val out=ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=max){"Réponse épisode trop grande."};out.write(buffer,0,n)};out.toByteArray()}
            val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            return EpisodeDetailRules.parse(objectMap(JSONObject(text),0),q)
        }catch(e:Failure){throw e}catch(e:Exception){throw IllegalStateException(if(e is IllegalArgumentException||e is IllegalStateException)e.message?:"Fiche épisode invalide." else "Fiche épisode illisible ou connexion interrompue. Aucun suivi modifié.")}
        finally{c.disconnect()}
    }
    private fun objectMap(o:JSONObject,depth:Int):Map<String,Any?> {require(depth<=20&&o.length()<=1000);val out=linkedMapOf<String,Any?>();val keys=o.keys();while(keys.hasNext()){val k=keys.next();out[k]=value(o.opt(k),depth+1)};return out}
    private fun value(v:Any?,depth:Int):Any? {require(depth<=20);return when(v){null,JSONObject.NULL->null;is JSONObject->objectMap(v,depth);is JSONArray->{require(v.length()<=5000);(0 until v.length()).map{value(v.get(it),depth+1)}};else->v}}
}
