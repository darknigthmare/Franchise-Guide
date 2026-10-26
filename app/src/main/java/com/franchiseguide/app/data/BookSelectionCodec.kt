package com.franchiseguide.app.data
import com.franchiseguide.app.model.*
import org.json.JSONObject
import org.json.JSONArray

object BookSelectionCodec {
    private fun value(v:Any?,depth:Int=0):Any? {require(depth<=8);return when(v){null,JSONObject.NULL->null
        is JSONObject->{require(v.length()<=30);val map=linkedMapOf<String,Any?>();val keys=v.keys();while(keys.hasNext()){val k=keys.next();map[k]=value(v.opt(k),depth+1)};map}
        is JSONArray->{require(v.length()<=200);(0 until v.length()).map{value(v.get(it),depth+1)}}
        else->v}}
    fun read(v:Any?):SavedBookEdition? {if(v==null||v===JSONObject.NULL)return null;require(v is JSONObject);@Suppress("UNCHECKED_CAST") return BookSelectionRules.normalize(value(v) as Map<String,Any?>)}
    fun write(v:SavedBookEdition?):Any {if(v==null)return JSONObject.NULL;BookSelectionRules.normalize(BookSelectionRules.asMap(v));return JSONObject().apply{BookSelectionRules.asMap(v).forEach{(k,x)->put(k,if(x is List<*>)JSONArray(x) else x?:JSONObject.NULL)}}}
}
