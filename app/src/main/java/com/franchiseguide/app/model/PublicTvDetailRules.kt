package com.franchiseguide.app.model

import java.net.URI
import java.time.LocalDate

/** Pure provider-scoped TVmaze read model. Never writes library state or maps IDs to TMDb. */
object PublicTvDetailRules {
    fun id(value:Any?):Int { val s=value?.toString().orEmpty();require(Regex("[1-9][0-9]{0,9}").matches(s)){"Identifiant TVmaze invalide."};return s.toIntOrNull()?.takeIf{it>0}?:error("Identifiant TVmaze invalide.") }
    private fun map(v:Any?,label:String):Map<*,*> = v as? Map<*,*>?:error("$label absent.")
    private fun rows(v:Any?,label:String,max:Int=5000):List<Map<*,*>> {val list=v as? List<*>?:error("$label absent.");require(list.size<=max){"$label trop volumineux."};return list.map{map(it,label)} }
    fun clean(v:Any?,label:String="Texte",max:Int=30000,required:Boolean=false):String {
        if(v==null&&!required)return "";require(v is String&&v.length<=max&&!Regex("[\\u0000-\\u0008\\u000b\\u000c\\u000e-\\u001f\\u007f]").containsMatchIn(v)){"$label invalide."}
        val s=v.replace(Regex("(?is)<script\\b[^>]*>.*?</script>|<style\\b[^>]*>.*?</style>"),"").replace(Regex("(?i)</(?:p|div|li)>|<br\\s*/?\\s*>"),"\n").replace(Regex("<[^>]*>"),"").replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&apos;","'").replace("&#39;","'").replace("&nbsp;"," ").trim()
        require(!required||s.isNotBlank()){ "$label absent." };return s
    }
    fun image(v:Any?):String?=runCatching { val s=v as? String?:return@runCatching null;val u=URI(s);if(u.scheme=="https"&&u.host=="static.tvmaze.com"&&u.userInfo==null&&u.port==-1)u.toString() else null }.getOrNull()
    private fun portrait(v:Any?):String? {val m=v as? Map<*,*>?:return null;return image(m["medium"])?:image(m["original"])}
    fun date(v:Any?):LocalDate? {if(v==null||v=="")return null;require(v is String&&Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(v));return LocalDate.parse(v)}
    private fun number(v:Any?,max:Double):Double? {if(v==null)return null;require(v is Number&&v.toDouble().isFinite()&&v.toDouble() in 0.0..max);return v.toDouble()}
    fun sameEpisode(old:Episode,fresh:Episode):Boolean=old.showId==fresh.showId&&old.id==fresh.id
    fun query(m:MediaItem,e:Episode):EpisodeDetailQuery {
        require(m.source=="tvmaze"&&m.kind==MediaKind.SHOW&&e.showId==m.id){"Épisode non relié à une série TVmaze."}
        val sid=id(m.sourceId);val match=Regex("tvmaze-episode-([1-9][0-9]*)").matchEntire(e.id)?:error("Identifiant TVmaze de l’épisode absent.")
        return EpisodeDetailQuery(sid,e.season,if(e.numberUnknown)0 else e.number,id(match.groupValues[1]),"Langue fournie par TVmaze",EpisodeDetailRules.identity(m,e),"tvmaze")
    }
    fun episode(raw:Map<String,Any?>,guests:List<*>,crew:List<*>,q:EpisodeDetailQuery,now:Long=System.currentTimeMillis()):EpisodeDetailSnapshot {
        require(q.provider=="tvmaze"&&id(raw["id"])==q.episodeId){"Autre épisode TVmaze reçu."}
        val embedded=map(raw["_embedded"],"Série liée");require(id(map(embedded["show"],"Série liée")["id"])==q.seriesId){"Autre série TVmaze reçue."}
        val special=(raw["type"] as? String)?.contains("special")==true;require(special==(q.season==0)){"Épisode standard/spécial incohérent."}
        val season=(raw["season"] as? Number)?.toInt();val n=raw["number"] as? Number
        require(season!=null&&season>0&& (raw["season"] as Number).toDouble()==season.toDouble())
        require(raw["number"]==null||n!=null&&n.toDouble()==n.toInt().toDouble()&&n.toInt()>0)
        if(!special)require(season==q.season&&(q.number==0||n?.toInt()==q.number)){"Numérotation TVmaze incohérente."}
        val known=mutableMapOf<Int,String>()
        val groups=listOf(Triple("guest_stars","Invités de cet épisode",guests),Triple("crew","Équipe invitée de cet épisode",crew)).map { (kind,label,entries)->
            val people=linkedMapOf<Int,EpisodePerson>()
            for(c in rows(entries,label,2500)){
                val p=map(c["person"],"Personne");val pid=id(p["id"]);val name=clean(p["name"],"Nom",250,true)
                require(!known.containsKey(pid)||known[pid]==name){"Identité de personne contradictoire."};known[pid]=name
                val role=clean(if(kind=="crew")c["type"] else (c["character"] as? Map<*,*>)?.get("name"),"Rôle",500)
                val prev=people[pid]?:EpisodePerson(pid,name,portrait(p["image"]),emptyList(),emptyList())
                people[pid]=prev.copy(profileUrl=prev.profileUrl?:portrait(p["image"]),roles=(prev.roles+role).filter{it.isNotBlank()}.distinct())
            };EpisodeCredits(kind,label,people.values.toList())
        }
        require(now>=0)
        return EpisodeDetailSnapshot(id(raw["id"]),q,clean(raw["name"],"Titre",500,true),clean(raw["summary"],"Synopsis"),date(raw["airdate"]),number(raw["runtime"],100000.0)?.toInt(),number((raw["rating"] as? Map<*,*>)?.get("average"),10.0),null,"",portrait(raw["image"]),groups,if(raw["image"]!=null&&portrait(raw["image"])==null)1 else 0,now)
    }
    fun person(raw:Map<String,Any?>,cast:List<*>,crew:List<*>,expected:Int):PersonProfile {
        require(id(raw["id"])==expected){"Autre personne TVmaze reçue."};val works=linkedMapOf<String,PersonWork>()
        for((kind,entries) in listOf("cast" to cast,"crew" to crew))for(c in rows(entries,"Crédits",10000)){
            val s=map(map(c["_embedded"],"Série liée")["show"],"Série liée");val sid=id(s["id"]);val title=clean(s["name"],"Titre",500,true)
            val key="tvmaze-show-$sid";val prev=works[key];require(prev==null||prev.media.title==title){"Titre contradictoire pour la même série TVmaze."}
            val role=if(kind=="crew")clean(c["type"],"Fonction",500) else when { (c["_embedded"] as? Map<*,*>)?.get("character") is Map<*,*>->clean(((c["_embedded"] as Map<*,*>)["character"] as Map<*,*>)["name"],"Rôle",500);c["self"]==true->"Participation en son nom";c["voice"]==true->"Interprétation vocale";else->"Interprétation" }
            val d=date(s["premiered"]);val media=prev?.media?:MediaItem(id=key,kind=MediaKind.SHOW,title=title,source="tvmaze",sourceId=sid.toString(),added=false,isSuggestion=true,year=d?.year,releaseDate=d,overview=clean(s["summary"]),posterUrl=portrait(s["image"]),externalUrl="https://www.tvmaze.com/shows/$sid")
            works[key]=PersonWork(media,(prev?.roles.orEmpty()+role).filter{it.isNotBlank()}.distinct())
        }
        return PersonProfile(expected,clean(raw["name"],"Nom",250,true),"Biographie non fournie par cette API. Séries créditées seulement, pas filmographie cinéma exhaustive.",portrait(raw["image"]),date(raw["birthday"])?.toString(),date(raw["deathday"])?.toString(),null,"TVmaze · sans clé · CC BY-SA",works.values.toList())
    }
}
