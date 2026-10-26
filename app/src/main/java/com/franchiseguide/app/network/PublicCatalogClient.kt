package com.franchiseguide.app.network

import com.franchiseguide.app.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.LocalDate

/** Anonymous provider access; identities never masquerade as TMDb/RAWG IDs.
 * TVmaze data are CC BY-SA. FreeToGame attribution is required.
 * Apple storefront metadata are deliberately limited: no promotional artwork or previews copied.
 */
class PublicCatalogClient(private val reader:(String)->String=::readBounded) {
    data class Bundle(val media:MediaItem,val episodes:List<Episode> = emptyList())
    fun search(query:String,kind:MediaKind,page:Int=1,region:String="FR"):List<MediaItem> {
        require(query.trim().length in 2..300 && page in 1..1000)
        return when(kind) {
            MediaKind.SHOW -> chunk(rows(JSONArray(reader("https://api.tvmaze.com/search/shows?q=${enc(query.trim())}")),500).map { show(it.optJSONObject("show")?:error("Fiche TVmaze absente.")) },page)
            MediaKind.MOVIE -> {
                val root=JSONObject(reader("https://itunes.apple.com/search?term=${enc(query.trim())}&country=${country(region)}&media=movie&entity=movie&limit=200"))
                val list=root.optJSONArray("results")?:error("Résultats Apple absents.")
                require(root.optInt("resultCount",-1)==list.length()) { "Réponse Apple incomplète." }
                chunk(rows(list,200).filter { it.optString("kind")=="feature-movie" }.map { movie(it,region) },page)
            }
            MediaKind.GAME -> chunk(rows(JSONArray(reader("https://www.freetogame.com/api/games")),10000).map(::game).filter { it.title.contains(query.trim(),ignoreCase=true) },page)
            MediaKind.BOOK -> emptyList()
        }
    }
    fun discover(kind:MediaKind,page:Int=1):List<MediaItem> {
        require(page in 1..DiscoveryRules.MAX_PAGES) { "Page de découverte hors limite." }
        return when(kind) {
            MediaKind.SHOW -> {
                val text=try { reader("https://api.tvmaze.com/shows?page=${page-1}") }
                catch(e:IllegalArgumentException) {
                    // TVmaze documents HTTP 404 as the end of this exact index.
                    if(page>1 && e.message?.contains("api.tvmaze.com HTTP 404") == true)return emptyList()
                    throw e
                }
                rows(JSONArray(text),250).map(::show)
            }
            MediaKind.GAME -> chunk(rows(JSONArray(reader("https://www.freetogame.com/api/games?sort-by=popularity")),10000).map(::game),page)
            else -> emptyList()
        }
    }
    fun details(item:MediaItem,region:String):Bundle {
        val id=identifier(item.sourceId?:error("Identifiant fournisseur absent."))
        return when(item.source) {
            "tvmaze" -> {
                val root=JSONObject(reader("https://api.tvmaze.com/shows/$id"));require(identifier(root.opt("id"))==id)
                val seasons=rows(JSONArray(reader("https://api.tvmaze.com/shows/$id/seasons")),2000)
                val episodes=rows(JSONArray(reader("https://api.tvmaze.com/shows/$id/episodes?specials=1")),50000)
                val cast=rows(JSONArray(reader("https://api.tvmaze.com/shows/$id/cast")),5000)
                tvBundle(root,seasons,episodes,cast,item.id)
            }
            "itunes" -> {
                val root=JSONObject(reader("https://itunes.apple.com/lookup?id=$id&country=${country(region)}&entity=movie"));val list=rows(root.optJSONArray("results")?:error("Réponse Apple absente."),200)
                require(root.optInt("resultCount",-1)==list.size && list.size==1 && identifier(list[0].opt("trackId"))==id) { "Le film Apple ne correspond pas." }
                Bundle(movie(list[0],region).copy(id=item.id,catalogComplete=true,catalogUpdatedAt=System.currentTimeMillis()))
            }
            "freetogame" -> {
                val root=JSONObject(reader("https://www.freetogame.com/api/game?id=$id"));require(identifier(root.opt("id"))==id)
                Bundle(game(root).copy(id=item.id,catalogComplete=true,catalogUpdatedAt=System.currentTimeMillis()))
            }
            else -> error("Source publique non prise en charge.")
        }
    }
    private fun tvBundle(root:JSONObject,seasons:List<JSONObject>,entries:List<JSONObject>,cast:List<JSONObject>,targetId:String):Bundle {
        val indexes=linkedMapOf<Int,JSONObject>()
        seasons.forEach { s-> identifier(s.opt("id"));val n=s.optInt("number",-1);require(n>0&&!indexes.containsKey(n)) { "Saison invalide ou en double." };indexes[n]=s }
        val seen=mutableSetOf<String>();val numbers=mutableSetOf<Pair<Int,Int>>()
        val eps=entries.map { e ->
            val eid=identifier(e.opt("id"));require(seen.add(eid)) { "Épisode TVmaze en double." }
            val originalSeason=e.optInt("season",-1);require(indexes.containsKey(originalSeason)) { "Épisode sans saison connue." }
            val special=e.optString("type").contains("special");val season=if(special)0 else originalSeason
            val unknown=e.isNull("number")||!e.has("number");val number=if(unknown)0 else e.optInt("number",-1).also { require(it>0) }
            if(!unknown&&season>0)require(numbers.add(season to number)) { "Numérotation ambiguë." }
            Episode(id="tvmaze-episode-$eid",showId=targetId,season=season,number=number,title=clean(e.optString("name")).ifBlank { "Épisode ${if(unknown)eid else number}" },airDate=date(e.optString("airdate")),
                runtimeMinutes=decimal(e,"runtime",100000.0)?.toInt()?:0,overview=clean(e.optString("summary")),numberUnknown=unknown,
                stillUrl=picture(e.optJSONObject("image"),setOf("static.tvmaze.com")),seasonPosterUrl=picture(indexes[originalSeason]?.optJSONObject("image"),setOf("static.tvmaze.com")),
                catalogRating=e.optJSONObject("rating")?.let { decimal(it,"average",10.0) })
        }
        val seasonInfo=indexes.map { (n,s)->SeasonInfo(number=n,title=clean(s.optString("name")).ifBlank { "Saison $n" },overview=clean(s.optString("summary")),posterUrl=picture(s.optJSONObject("image"),setOf("static.tvmaze.com")),episodeCount=eps.count { it.season==n }) }.toMutableList()
        if(eps.any{it.season==0})seasonInfo.add(0,SeasonInfo(number=0,title="Spéciaux",episodeCount=eps.count{it.season==0}))
        val credits=cast.map { c->val p=c.optJSONObject("person")?:error("Personne TVmaze manquante.");val pid=identifier(p.opt("id"));PersonCredit(id="tvmaze-person-$pid",name=title(p.optString("name")),role=clean(c.optJSONObject("character")?.optString("name").orEmpty()).take(250),department="Cast",profileUrl=picture(p.optJSONObject("image"),setOf("static.tvmaze.com")),externalUrl="https://www.tvmaze.com/people/$pid") }
        return Bundle(show(root).copy(id=targetId,credits=credits,people=credits.map{it.name}.distinct(),seasons=seasonInfo,seasonCount=seasons.size,episodeCount=eps.count{it.season>0},catalogComplete=true,catalogUpdatedAt=System.currentTimeMillis()),eps)
    }
    private fun show(o:JSONObject):MediaItem {
        val id=identifier(o.opt("id"));val day=date(o.optString("premiered"));val score=o.optJSONObject("rating")?.let{decimal(it,"average",10.0)};val external=o.optJSONObject("externals")
        return MediaItem(id="tvmaze-show-$id",kind=MediaKind.SHOW,title=title(o.optString("name")),source="tvmaze",sourceId=id,added=false,isSuggestion=true,
            year=day?.year,releaseDate=day,lastAirDate=date(o.optString("ended")),overview=clean(o.optString("summary")),posterUrl=picture(o.optJSONObject("image"),setOf("static.tvmaze.com")),
            runtimeMinutes=(decimal(o,"runtime",100000.0)?:decimal(o,"averageRuntime",100000.0))?.toInt(),
            status=when(o.optString("status")){"Running"->ShowStatus.CONTINUING;"Ended"->ShowStatus.ENDED;else->ShowStatus.UNKNOWN},statusRaw=o.optString("status").ifBlank { null },
            network=(o.optJSONObject("network")?:o.optJSONObject("webChannel"))?.optString("name")?.ifBlank { null },originalLanguage=o.optString("language").ifBlank{null},genres=strings(o.optJSONArray("genres")),
            imdbId=external?.optString("imdb")?.takeIf { it.matches(Regex("tt[0-9]+")) },tvdbId=external?.optInt("thetvdb",0)?.takeIf{it>0},externalUrl="https://www.tvmaze.com/shows/$id",catalogRating=score,
            ratings=score?.let{listOf(RatingSource("tvmaze","TVmaze",it,10.0,null,"https://www.tvmaze.com/shows/$id"))}.orEmpty())
    }
    private fun movie(o:JSONObject,region:String):MediaItem {
        require(o.optString("kind")=="feature-movie");val id=identifier(o.opt("trackId"));val day=date(o.optString("releaseDate"));val link=safeImage(o.optString("trackViewUrl"),setOf("itunes.apple.com","tv.apple.com"))?:error("Lien Apple invalide.")
        return MediaItem(id="itunes-movie-$id",kind=MediaKind.MOVIE,title=title(o.optString("trackName")),source="itunes",sourceId=id,added=false,isSuggestion=true,
            year=day?.year,releaseDate=day,overview=clean(o.optString("longDescription").ifBlank{o.optString("shortDescription")}),runtimeMinutes=decimal(o,"trackTimeMillis",1e10)?.div(60000)?.toInt(),
            creator=clean(o.optString("artistName")).ifBlank{null},genres=setOf(clean(o.optString("primaryGenreName"))).filter{it.isNotBlank()}.toSet(),contentRating=o.optString("contentAdvisoryRating").ifBlank{null},
            externalUrl=link,subtitle="Catalogue Apple · ${country(region)} · fiche limitée")
    }
    private fun game(o:JSONObject):MediaItem {
        val id=identifier(o.opt("id"));val day=date(o.optString("release_date"))
        return MediaItem(id="freetogame-game-$id",kind=MediaKind.GAME,title=title(o.optString("title")),source="freetogame",sourceId=id,added=false,isSuggestion=true,
            year=day?.year,releaseDate=day,overview=clean(o.optString("description").ifBlank{o.optString("short_description")}),posterUrl=safeImage(o.optString("thumbnail"),setOf("www.freetogame.com","freetogame.com")),
            creator=clean(o.optString("developer")).ifBlank{null},publisher=clean(o.optString("publisher")).ifBlank{null},genres=setOf(clean(o.optString("genre"))).filter{it.isNotBlank()}.toSet(),
            platforms=o.optString("platform").split(',').map{it.trim()}.filter{it.isNotEmpty()}.toSet(),externalUrl=safeImage(o.optString("freetogame_profile_url"),setOf("www.freetogame.com","freetogame.com"))?:"https://www.freetogame.com",progressTotal=100,progressUnit="%")
    }
    companion object {
        val sources=setOf("tvmaze","itunes","freetogame")
        fun sourceNotice(source:String):String?=when(source){
            "tvmaze"->"TVmaze · séries, épisodes, images et distribution sans clé. Données CC BY-SA ; traduction française non garantie."
            "itunes"->"Apple / iTunes · catalogue cinéma de la boutique, pas une base exhaustive. Sans distribution ni copie des images promotionnelles."
            "freetogame"->"FreeToGame · jeux free-to-play PC/navigateur. Ne couvre pas l’ensemble des jeux payants et consoles."
            "openlibrary"->"Open Library · livres et couvertures disponibles sans clé."
            else->null
        }
        private fun enc(s:String)=URLEncoder.encode(s,StandardCharsets.UTF_8.name())
        private fun country(s:String)=s.uppercase().also { require(it.matches(Regex("[A-Z]{2}"))) }
        private fun identifier(v:Any?):String { val s=v?.toString()?:error("Identifiant manquant.");require(s.matches(Regex("[0-9]+"))&&(s.toLongOrNull()?:0)>0);return s }
        private fun title(s:String)=clean(s).take(500).also { require(it.isNotBlank()) { "Titre fournisseur absent." } }
        private fun clean(s:String)=s.replace(Regex("(?is)<script\\b[^>]*>.*?</script>|<style\\b[^>]*>.*?</style>"),"").replace(Regex("(?i)</(?:p|div|li)>|<br\\s*/?\\s*>"),"\n").replace(Regex("<[^>]*>"),"").replace("&amp;","&").replace("&nbsp;"," ").replace("&quot;","\"").replace("&#39;","'").trim().take(50000).takeUnless { it=="null" }.orEmpty()
        private fun date(s:String):LocalDate? { if(s.isBlank()||s=="null")return null;require(s.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}(?:T.*)?"))) { "Date fournisseur invalide." };if(s.contains('T'))java.time.OffsetDateTime.parse(s);return LocalDate.parse(s.take(10)) }
        private fun decimal(o:JSONObject,key:String,max:Double):Double? { if(!o.has(key)||o.isNull(key))return null;val n=o.opt(key) as? Number?:error("Nombre fournisseur invalide.");return n.toDouble().also{require(it.isFinite()&&it in 0.0..max)} }
        private fun safeImage(s:String,hosts:Set<String>):String?=runCatching { URI(s).takeIf{it.scheme=="https"&&it.userInfo==null&&it.port==-1&&it.host in hosts}?.toString() }.getOrNull()
        private fun picture(o:JSONObject?,hosts:Set<String>)=o?.let { safeImage(it.optString("original").ifBlank{it.optString("medium")},hosts) }
        private fun strings(a:JSONArray?):Set<String> { if(a==null)return emptySet();require(a.length()<=200);return (0 until a.length()).map{clean(a.optString(it))}.filter{it.isNotBlank()}.toSet() }
        private fun rows(a:JSONArray,max:Int):List<JSONObject> { require(a.length()<=max);return (0 until a.length()).map { a.optJSONObject(it)?:error("Ligne fournisseur invalide.") } }
        private fun <T> chunk(a:List<T>,page:Int)=a.drop((page-1)*20).take(20)
        private fun readBounded(url:String):String {
            val uri=URI(url);require(uri.scheme=="https"&&uri.host in setOf("api.tvmaze.com","itunes.apple.com","www.freetogame.com")&&uri.userInfo==null&&uri.port==-1)
            val c=uri.toURL().openConnection() as HttpURLConnection;c.requestMethod="GET";c.instanceFollowRedirects=false;c.connectTimeout=8000;c.readTimeout=20000;c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","FranchiseGuide/0.27 (personal catalogue)")
            try { val status=c.responseCode;require(status in 200..299) { "Catalogue ${uri.host} HTTP $status. Aucune donnée remplacée." };require(c.contentLengthLong<=4194304) { "Réponse catalogue trop volumineuse." }
                // InputStream.readNBytes(int) is unavailable before Android 13.
                // Bound each chunk before keeping it, including unknown-length replies.
                val bytes=c.inputStream.use { stream ->
                    val output=ByteArrayOutputStream()
                    val buffer=ByteArray(8192)
                    while(true){
                        val count=stream.read(buffer)
                        if(count<0)break
                        require(output.size()+count<=4194304) { "Réponse catalogue trop volumineuse." }
                        output.write(buffer,0,count)
                    }
                    output.toByteArray()
                }
                return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            } finally { c.disconnect() }
        }
    }
}
