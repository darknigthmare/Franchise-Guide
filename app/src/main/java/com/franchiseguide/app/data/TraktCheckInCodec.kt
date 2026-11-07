package com.franchiseguide.app.data

import com.franchiseguide.app.model.TraktCheckInRules
import com.franchiseguide.app.model.TraktCheckInSession
import java.math.BigDecimal
import org.json.JSONObject

/** Strict local-session codec. Invalid input is left to the repository's recovery path. */
object TraktCheckInCodec {
    private fun number(o:JSONObject,key:String,optional:Boolean=false):Long? {
        if(!o.has(key)||o.isNull(key)) { require(optional){"$key absent."};return null }
        val raw=o.get(key);require(raw is Number){"$key doit être un nombre entier."}
        val encoded=raw.toString();require(encoded.length<=64)
        val decimal=BigDecimal(encoded)
        require(decimal>=BigDecimal.ZERO&&decimal<=BigDecimal.valueOf(8640000000000000L)){"$key hors limites."}
        return decimal.longValueExact()
    }
    private fun text(o:JSONObject,key:String,optional:Boolean=false):String? {
        if(!o.has(key)||o.isNull(key)){require(optional){"$key absent."};return null}
        val raw=o.get(key);require(raw is String){"$key doit être du texte."};return raw
    }
    private fun int(o:JSONObject,key:String,optional:Boolean=false):Int? {
        val n=number(o,key,optional)?:return null;require(n<=Int.MAX_VALUE);return n.toInt()
    }
    fun read(o:JSONObject):TraktCheckInSession {
        val applied=if(!o.has("historyApplied"))false else o.get("historyApplied").also{require(it is Boolean)} as Boolean
        return TraktCheckInRules.validate(TraktCheckInSession(
            version=int(o,"version")!!,id=text(o,"id")!!,account=text(o,"account")!!,
            appRef=text(o,"appRef",true),kind=text(o,"kind")!!,localId=text(o,"localId")!!,
            parentId=text(o,"parentId",true),tmdbId=int(o,"tmdbId")!!,title=text(o,"title")!!,
            code=text(o,"code",true),runtimeMinutes=int(o,"runtimeMinutes",true),identity=text(o,"identity")!!,
            startedAt=number(o,"startedAt")!!,remoteStartedAt=number(o,"remoteStartedAt",true),
            expectedEndAt=number(o,"expectedEndAt",true),status=text(o,"status")!!,
            finishAt=number(o,"finishAt",true),lastError=text(o,"lastError",true),historyApplied=applied
        ))
    }
    fun write(value:TraktCheckInSession):JSONObject {
        val s=TraktCheckInRules.validate(value)
        return JSONObject().apply {
            put("version",s.version);put("id",s.id);put("account",s.account);put("appRef",s.appRef?:JSONObject.NULL)
            put("kind",s.kind);put("localId",s.localId);put("parentId",s.parentId?:JSONObject.NULL)
            put("tmdbId",s.tmdbId);put("title",s.title);put("code",s.code?:JSONObject.NULL)
            put("runtimeMinutes",s.runtimeMinutes?:JSONObject.NULL);put("identity",s.identity)
            put("startedAt",s.startedAt);put("remoteStartedAt",s.remoteStartedAt?:JSONObject.NULL)
            put("expectedEndAt",s.expectedEndAt?:JSONObject.NULL);put("status",s.status)
            put("finishAt",s.finishAt?:JSONObject.NULL);put("lastError",s.lastError?:JSONObject.NULL)
            put("historyApplied",s.historyApplied)
        }
    }
}
