package com.franchiseguide.app.model

import java.net.URI
import java.util.Locale

/** Ephemeral provider data. Never a purchase, entitlement, viewing or personal subscription. */
data class WatchQuery(val kind:MediaKind,val id:Int,val country:String,val season:Int?=null) {
    val key:String get()="${kind.name.lowercase()}:$id:$country:${season?.toString()?:"all"}"
    val path:String get()="/${if(kind==MediaKind.SHOW)"tv" else "movie"}/$id${season?.let{"/season/$it"}.orEmpty()}/watch/providers"
}
data class WatchCountry(val code:String,val label:String)
data class WatchProvider(val id:Int,val name:String,val logoUrl:String?,val priority:Int)
data class WatchGroup(val mode:String,val label:String,val offers:List<WatchProvider>)
data class WatchAvailability(val query:WatchQuery,val observedAt:Long,val countryReported:Boolean,val groups:List<WatchGroup>,val link:String?,val ignoredLinks:Int) {
    val totalOffers:Int get()=groups.sumOf { it.offers.size }
    val totalProviders:Int get()=groups.flatMap { it.offers }.map { it.id }.toSet().size
}
object WatchAvailabilityRules {
    val modes=listOf("flatrate" to "Abonnement","free" to "Gratuit","ads" to "Avec publicité","rent" to "Location","buy" to "Achat")
    fun country(value:String):String {require(value.trim().matches(Regex("[A-Za-z]{2}"))){"Pays : code à deux lettres requis (ex. FR)."};return value.trim().uppercase(Locale.ROOT)}
    fun query(kind:MediaKind,id:Int?,country:String,season:Int?=null):WatchQuery {
        require(kind in setOf(MediaKind.SHOW,MediaKind.MOVIE)){"Disponibilités réservées aux séries et films."}
        require(id!=null&&id>0){"Identifiant TMDb confirmé requis."}
        require(season==null||(kind==MediaKind.SHOW&&season in 0..99999)){"Saison invalide."}
        return WatchQuery(kind,id,country(country),season)
    }
    private fun obj(value:Any?,label:String):Map<*,*> = value as? Map<*,*> ?: error("$label invalide ou incomplet.")
    private fun list(value:Any?,label:String,max:Int):List<*> {val items=value as? List<*> ?: error("$label invalide ou incomplet.");require(items.size<=max){"$label trop grand."};return items}
    private fun int(value:Any?,label:String):Int {
        require(value is Number){"$label invalide."};val n=value.toDouble()
        require(n.isFinite()&&n>=0&&n<=Int.MAX_VALUE.toDouble()&&n==n.toLong().toDouble()){ "$label invalide." };return n.toInt()
    }
    private fun name(value:Any?,label:String):String {require(value is String&&value.trim().isNotEmpty()&&value.length<=250&&!value.any { it.code<32||it.code==127 }){"$label absent ou invalide."};return value.trim()}
    fun logo(path:Any?):String?=(path as? String)?.takeIf { it.matches(Regex("/[A-Za-z0-9._-]+")) }?.let { "https://image.tmdb.org/t/p/w185$it" }
    fun sourceLink(value:Any?,q:WatchQuery):String? {
        val raw=value as? String?:return null
        if(raw.length>2000||raw.any { it.code<=32||it.code==127||it=='\\' })return null
        return runCatching {
            val uri=URI(raw);val type=if(q.kind==MediaKind.SHOW)"tv" else "movie"
            if(!uri.scheme.equals("https",true)||uri.host?.lowercase() !in setOf("www.themoviedb.org","themoviedb.org")||uri.port !in setOf(-1,443)||uri.rawUserInfo!=null||uri.rawFragment!=null)return null
            val parts=uri.rawPath.split('/')
            if(parts.size!=4||parts[1]!=type||parts[3]!="watch"||!parts[2].matches(Regex("${q.id}(?:-[^/]+)?")))return null
            "https://${uri.host.lowercase()}${uri.rawPath}?locale=${q.country}"
        }.getOrNull()
    }
    fun regions(raw:Map<String,Any?>):List<WatchCountry> {
        val seen=mutableSetOf<String>()
        return list(raw["results"],"Liste des pays",300).map { x->val r=obj(x,"Pays");val c=country(r["iso_3166_1"] as? String?:error("Code pays absent."));require(seen.add(c)){"Pays présent plusieurs fois."}
            WatchCountry(c,name((r["native_name"] as? String)?.takeIf { it.isNotEmpty() }?:r["english_name"],"Nom du pays")) }.sortedBy { it.code }
    }
    fun parse(raw:Map<String,Any?>,request:WatchQuery,at:Long=System.currentTimeMillis()):WatchAvailability {
        val q=query(request.kind,request.id,request.country,request.season);require(at in 0..8640000000000000L){"Horodatage invalide."}
        val results=obj(raw["results"],"Réponse des disponibilités")
        if(q.season==null&&raw["id"]!=null)require(int(raw["id"],"Identifiant de réponse")==q.id){"Réponse destinée à un autre titre."}
        val present=results.containsKey(q.country);val node=if(present)obj(results[q.country],"Données pays") else emptyMap<String,Any?>()
        var ignored=0;val signatures=mutableMapOf<Int,Pair<String,String?>>()
        val groups=modes.map { (mode,label)->
            val rows=if(node.containsKey(mode))list(node[mode],"Offres $label",1000) else emptyList<Any?>()
            val seen=linkedMapOf<Int,WatchProvider>()
            rows.forEach { x->val r=obj(x,"Service");val id=int(r["provider_id"],"Identifiant service");require(id>0){"Identifiant service absent."}
                val title=name(r["provider_name"],"Nom service");val icon=logo(r["logo_path"]);if(r["logo_path"]!=null&&r["logo_path"]!=""&&icon==null)ignored++
                val priority=if(r["display_priority"]==null)Int.MAX_VALUE else int(r["display_priority"],"Priorité")
                val signature=title to icon;require(signatures[id]==null||signatures[id]==signature){"Identité de service contradictoire."};signatures[id]=signature
                require(seen[id]==null||seen[id]?.priority==priority){"Priorité de service contradictoire."}
                seen[id]=WatchProvider(id,title,icon,priority)
            }
            WatchGroup(mode,label,seen.values.sortedWith(compareBy<WatchProvider>{it.priority}.thenBy{it.id}))
        }
        require(groups.sumOf { it.offers.size }<=2500){"Trop d’offres."}
        val link=sourceLink(node["link"],q);if(node["link"]!=null&&node["link"]!=""&&link==null)ignored++
        return WatchAvailability(q,at,present,groups,link,ignored)
    }
    fun filter(snapshot:WatchAvailability,mode:String="all",query:String=""):List<WatchGroup> {
        require(mode=="all"||modes.any { it.first==mode });require(query.length<=250)
        fun fold(s:String)=java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD).replace(Regex("[\\u0300-\\u036f]"),"").lowercase(Locale.FRENCH)
        val needle=fold(query.trim());return snapshot.groups.filter { mode=="all"||it.mode==mode }.map { g->g.copy(offers=g.offers.filter { needle.isEmpty()||fold(it.name).contains(needle) }) }
    }
    fun state(snapshot:WatchAvailability?):String=if(snapshot==null)"not-read" else if(!snapshot.countryReported)"country-not-reported" else if(snapshot.totalOffers>0)"reported" else "no-offers-reported"
    fun seasons(m:MediaItem,episodes:List<Episode>):List<Int> = if(m.kind!=MediaKind.SHOW)emptyList() else (m.seasons.map{it.number}+episodes.filter{it.showId==m.id&&!it.seasonUnknown}.map{it.season}).filter{it in 0..99999}.distinct().sorted()
}
