package com.franchiseguide.app.model

import java.time.LocalDate

/** Read-only episode details and immutable, field-targeted personal edits. */
data class EpisodeDetailQuery(val seriesId:Int,val season:Int,val number:Int,val episodeId:Int?,val language:String,val identity:String,val provider:String="tmdb") {
    val path get()="/tv/$seriesId/season/$season/episode/$number"
    val key get()=(if(provider=="tvmaze")"tvmaze:" else "")+"$seriesId:$season:$number:${episodeId?:"unknown"}:$language"
}
data class EpisodePerson(val id:Int,val name:String,val profileUrl:String?,val roles:List<String>,val departments:List<String>)
data class EpisodeCredits(val kind:String,val label:String,val people:List<EpisodePerson>)
data class EpisodeDetailSnapshot(val id:Int,val query:EpisodeDetailQuery,val title:String,val overview:String,val airDate:LocalDate?,val runtime:Int?,val rating:Double?,val votes:Int?,val productionCode:String,val stillUrl:String?,val groups:List<EpisodeCredits>,val ignoredImages:Int,val observedAt:Long) {
    val sourceUrl get()=if(query.provider=="tvmaze")"https://www.tvmaze.com/episodes/$id" else "https://www.themoviedb.org/tv/${query.seriesId}/season/${query.season}/episode/${query.number}"
}
data class EpisodePersonalFields(val rating:Int?,val note:String?)
data class EpisodePersonalPlan(val showId:String,val episodeId:String,val identity:String,val before:EpisodePersonalFields,val after:EpisodePersonalFields,val createdAt:Long)
object EpisodeDetailRules {
    data class State(val media:List<MediaItem>,val episodes:List<Episode>)
    private val unsafe=Regex("[\\u0000-\\u0008\\u000b\\u000c\\u000e-\\u001f\\u007f]")
    fun text(v:Any?,label:String,max:Int=2000,nullable:Boolean=true):String { if(v==null&&nullable)return "";require(v is String&&v.length<=max&&!unsafe.containsMatchIn(v)){"$label invalide ou trop long."};return v.trim() }
    private fun integer(v:Any?,label:String,min:Long=0,max:Long=2147483647):Long { require(v is Number&&v.toDouble().isFinite()&&v.toDouble()==v.toLong().toDouble()&&v.toLong() in min..max){"$label invalide."};return v.toLong() }
    fun image(v:Any?,size:String="w780"):String? = (v as? String)?.takeIf { Regex("^/[A-Za-z0-9._-]{1,180}$").matches(it)&&it !in setOf("/.","/..") }?.let { "https://image.tmdb.org/t/p/$size$it" }
    fun date(v:Any?):LocalDate? { if(v==null||v=="")return null;require(v is String&&Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(v)){"Date de diffusion invalide."};return runCatching{LocalDate.parse(v)}.getOrElse{error("Jour de diffusion impossible.")} }
    fun locate(s:State,showId:String,episodeId:String):Pair<MediaItem,Episode> { val show=s.media.filter{it.id==showId&&it.kind==MediaKind.SHOW&&it.added&&!it.isSuggestion};val ep=s.episodes.filter{it.id==episodeId};require(show.size==1&&ep.size==1&&ep[0].showId==showId){"Série ou épisode retiré, ambigu ou déplacé. Rouvre sa fiche."};return show[0] to ep[0] }
    fun identity(m:MediaItem,e:Episode):String = listOf(m.id,m.kind.name,m.tmdbId,e.id,e.showId,e.tmdbId,e.season,e.number,e.seasonUnknown,e.numberUnknown).joinToString("|"){val s=it?.toString()?:"null";"${s.length}:$s"}
    fun query(m:MediaItem,e:Episode,language:String):EpisodeDetailQuery {
        require(m.kind==MediaKind.SHOW&&e.showId==m.id){"Épisode d’une autre série."};require(!e.seasonUnknown&&!e.numberUnknown){"Numérotation inconnue : actualise la série pour identifier cet épisode."}
        val id=integer(m.tmdbId,"Identifiant TMDb série",1).toInt();integer(e.season,"Saison",0,99999);integer(e.number,"Numéro épisode",1,999999);e.tmdbId?.let{integer(it,"Identifiant TMDb épisode",1)}
        require(Regex("^[a-z]{2,3}(-[A-Z]{2})?$").matches(language.trim())){"Langue catalogue invalide."}
        return EpisodeDetailQuery(id,e.season,e.number,e.tmdbId,language.trim(),identity(m,e))
    }
    fun parse(raw:Map<String,Any?>,q:EpisodeDetailQuery,observedAt:Long=System.currentTimeMillis()):EpisodeDetailSnapshot {
        integer(observedAt,"Horodatage",0,8640000000000000);val id=integer(raw["id"],"Identifiant épisode",1).toInt()
        require((raw["season_number"] as? Number)?.toDouble()==q.season.toDouble()&&(raw["episode_number"] as? Number)?.toDouble()==q.number.toDouble()&&(q.episodeId==null||q.episodeId==id)&&(raw["show_id"]==null||(raw["show_id"] as? Number)?.toDouble()==q.seriesId.toDouble())){"Réponse destinée à un autre épisode."}
        val title=text(raw["name"],"Titre",500,false);val overview=text(raw["overview"],"Synopsis",30000);val airDate=date(raw["air_date"])
        val runtime=raw["runtime"]?.let{integer(it,"Durée",0,100000).toInt()};val rating=raw["vote_average"]?.let{require(it is Number&&it.toDouble().isFinite()&&it.toDouble() in 0.0..10.0){"Note catalogue invalide."};it.toDouble()}
        val votes=raw["vote_count"]?.let{integer(it,"Nombre de votes").toInt()};val production=text(raw["production_code"],"Code de production",250);var ignored=0
        val still=image(raw["still_path"]);if(raw["still_path"]!=null&&raw["still_path"]!=""&&still==null)ignored++
        val credits=raw["credits"] as? Map<*,*> ?: error("Distribution de l’épisode incomplète.")
        require(credits["id"]==null||(credits["id"] as? Number)?.toDouble()==id.toDouble()){"Distribution reçue pour un autre épisode."}
        var total=0;val known=mutableMapOf<Int,String>()
        val groups=listOf("cast" to "Distribution","guest_stars" to "Invités","crew" to "Équipe").map{(kind,label)->
            val list=credits[kind] as? List<*> ?: error("Distribution de l’épisode incomplète.");require(list.size<=2500){"Distribution trop volumineuse."};total+=list.size
            val people=linkedMapOf<Int,EpisodePerson>()
            for(value in list){val row=value as? Map<*,*> ?: error("Crédit invalide.");val personId=integer(row["id"],"Identifiant personne",1).toInt();val name=text(row["name"],"Nom",250,false);require(name.isNotEmpty()){"Nom de personne absent."}
                if(known.containsKey(personId))require(known[personId]==name){"Identité de personne contradictoire."};known[personId]=name
                val portrait=image(row["profile_path"],"w185");if(row["profile_path"]!=null&&row["profile_path"]!=""&&portrait==null)ignored++
                val role=text(row[if(kind=="crew")"job" else "character"],"Rôle",500);val department=text(row["department"],"Département",250)
                val old=people[personId]?:EpisodePerson(personId,name,portrait,emptyList(),emptyList())
                people[personId]=old.copy(profileUrl=old.profileUrl?:portrait,roles=(old.roles+listOf(role).filter{it.isNotEmpty()}).distinct(),departments=(old.departments+listOf(department).filter{it.isNotEmpty()}).distinct())
            };EpisodeCredits(kind,label,people.values.toList())
        };require(total<=5000){"Trop de crédits."}
        return EpisodeDetailSnapshot(id,q,title,overview,airDate,runtime,rating,votes,production,still,groups,ignored,observedAt)
    }
    fun fields(e:Episode)=EpisodePersonalFields(e.rating,e.userNote)
    fun personal(ratingText:String,note:String):EpisodePersonalFields {
        val raw=ratingText.trim();val rating=if(raw.isEmpty())null else {require(raw.all{it in '0'..'9'}){"Ma note : entier de 1 à 10 ou champ vide."};raw.toIntOrNull()?.takeIf{it in 1..10}?:error("Ma note : entier de 1 à 10.")}
        return EpisodePersonalFields(rating,text(note,"Note personnelle",2000,false).ifEmpty{null})
    }
    fun prepare(s:State,showId:String,id:String,rating:String,note:String,at:Long=System.currentTimeMillis()):EpisodePersonalPlan {val(m,e)=locate(s,showId,id);return EpisodePersonalPlan(showId,id,identity(m,e),fields(e),personal(rating,note),at)}
    fun apply(s:State,p:EpisodePersonalPlan,now:Long=System.currentTimeMillis()):State {
        require(p.createdAt>=0&&now>=p.createdAt&&now-p.createdAt<=600000){"Formulaire expiré. Recharge les champs personnels."};val(m,e)=locate(s,p.showId,p.episodeId)
        require(identity(m,e)==p.identity&&fields(e)==p.before){"L’épisode ou ses champs personnels ont changé. Recharge-les."}
        val after=personal(p.after.rating?.toString().orEmpty(),p.after.note.orEmpty())
        return s.copy(episodes=s.episodes.map{if(it.id==e.id)it.copy(rating=after.rating,userNote=after.note) else it})
    }
    fun neighbors(s:State,showId:String,id:String,includeSpecials:Boolean):Pair<String?,String?> {
        val (_,e)=locate(s,showId,id);if(e.seasonUnknown||e.numberUnknown)return null to null
        val all=s.episodes.filter{it.showId==showId&&!it.seasonUnknown&&!it.numberUnknown&&it.season>=0&&it.number>0&&(includeSpecials||it.season>0||it.id==id)}.sortedWith(compareBy<Episode>{it.season}.thenBy{it.number})
        if(all.map{it.season to it.number}.distinct().size!=all.size)return null to null
        val i=all.indexOfFirst{it.id==id};return (if(i>0)all[i-1].id else null) to (if(i>=0&&i<all.size-1)all[i+1].id else null)
    }
}
