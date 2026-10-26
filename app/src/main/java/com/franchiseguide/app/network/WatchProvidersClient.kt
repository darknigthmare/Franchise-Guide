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

/** GET-only transport. No raw response or credential is included in user-facing errors. */
class WatchProvidersClient {
    companion object { @Volatile private var cooldownUntil:Long=0L }
    class Failure(val status:Int,val retryAt:Long?):Exception(when(status){401,403->"Clé TMDb invalide ou accès refusé.";404->"Disponibilités absentes à cette adresse TMDb.";429->"Quota TMDb atteint. Attends le délai indiqué avant de recommencer.";else->"Disponibilités TMDb indisponibles (HTTP $status)."})
    fun regions(key:String,language:String):List<WatchCountry> = WatchAvailabilityRules.regions(read("/watch/providers/regions",key,"&language=${enc(language)}",1024*1024))
    fun offers(q:WatchQuery,key:String):WatchAvailability = WatchAvailabilityRules.parse(read(q.path,key,"",2*1024*1024),q)
    private fun enc(s:String)=URLEncoder.encode(s,Charsets.UTF_8.name())
    private fun read(path:String,key:String,extra:String,maxBytes:Int):Map<String,Any?> {
        require(key.isNotBlank()){ "Configure la clé TMDb dans les réglages." }
        if(System.currentTimeMillis()<cooldownUntil)throw Failure(429,cooldownUntil)
        val c=URI("https://api.themoviedb.org/3$path?api_key=${enc(key.trim())}$extra").toURL().openConnection() as HttpURLConnection
        c.requestMethod="GET";c.instanceFollowRedirects=false;c.connectTimeout=10000;c.readTimeout=20000
        c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","FranchiseGuide/0.23 (Android)")
        try {
            val status=c.responseCode
            if(status !in 200..299){val retry=if(status==429)retryAt(c.getHeaderField("Retry-After")) else null;if(retry!=null)cooldownUntil=retry;throw Failure(status,retry)}
            require(c.contentLengthLong<=maxBytes || c.contentLengthLong<0){"Réponse de disponibilités trop grande."}
            val bytes=c.inputStream.use { input->val out=ByteArrayOutputStream();val buf=ByteArray(8192);while(true){val n=input.read(buf);if(n<0)break;require(out.size()+n<=maxBytes){"Réponse de disponibilités trop grande."};out.write(buf,0,n)};out.toByteArray() }
            val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            return objectMap(JSONObject(text),0)
        }catch(e:Failure){throw e}catch(e:Exception){throw IllegalStateException(if(e.message?.startsWith("Réponse de disponibilités")==true)e.message else "Réponse des disponibilités illisible ou connexion interrompue. Dernier relevé conservé.")}
        finally{c.disconnect()}
    }
    private fun objectMap(o:JSONObject,depth:Int):Map<String,Any?> {
        require(depth<=20&&o.length()<=1000){"Réponse de disponibilités trop complexe."}
        val out=linkedMapOf<String,Any?>();val keys=o.keys();while(keys.hasNext()){val k=keys.next();out[k]=value(o.opt(k),depth+1)};return out
    }
    private fun value(v:Any?,depth:Int):Any? {require(depth<=20);return when(v){null,JSONObject.NULL->null;is JSONObject->objectMap(v,depth);is JSONArray->{require(v.length()<=2500);(0 until v.length()).map{value(v.get(it),depth+1)}};else->v}}
    private fun retryAt(raw:String?):Long {
        val now=System.currentTimeMillis();val ms=raw?.toLongOrNull()?.takeIf{it>=0}?.coerceAtMost(86400)?.times(1000)
            ?: runCatching{ZonedDateTime.parse(raw,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-now}.getOrNull()?:60000
        return now+ms.coerceIn(60000,86400000)
    }
}
