package com.franchiseguide.app.data

import com.franchiseguide.app.model.*
import org.json.JSONObject

/** Explicit portable V9 fields, validated before any repository mutation. */
object PersonalProgressCodec {
    private fun optional(o:JSONObject,key:String,max:Int):String? {
        if(!o.has(key)||o.isNull(key))return null
        val value=o.opt(key);require(value is String&&value.length<=max&&!value.any { it.code<32 }) { "Champ $key invalide." }
        return value
    }
    fun readInto(o:JSONObject,m:MediaItem):MediaItem {
        val state=optional(o,"trackingStatus",30);require(state==null||state in ProgressRules.states) { "État de parcours invalide." }
        val raw=if(o.has("playtimeMinutes")&&!o.isNull("playtimeMinutes"))o.opt("playtimeMinutes")else null
        require(raw==null||raw is Number&&raw.toDouble().isFinite()&&raw.toDouble()==raw.toInt().toDouble()&&raw.toInt() in 0..10000000) { "Temps personnel invalide." }
        return m.copy(trackingStatus=state,playtimeMinutes=(raw as? Number)?.toInt(),personalPlatform=optional(o,"personalPlatform",120),personalFormat=optional(o,"personalFormat",120))
    }
    fun writeInto(o:JSONObject,m:MediaItem):JSONObject=o.apply {
        put("trackingStatus",m.trackingStatus?:JSONObject.NULL);put("playtimeMinutes",m.playtimeMinutes?:JSONObject.NULL);put("personalPlatform",m.personalPlatform?:JSONObject.NULL);put("personalFormat",m.personalFormat?:JSONObject.NULL)
    }
}
