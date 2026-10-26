import com.franchiseguide.app.model.*
import java.time.LocalDate
import java.io.File
private var checks=0
private fun ok(label:String,passed:Boolean){check(passed){label};checks++;println("PASS $label")}
private fun bad(label:String,body:()->Unit){ok(label,runCatching(body).isFailure)}
private fun detail()=mapOf<String,Any?>("id" to 101,"season" to 1,"number" to 1,"name" to "Pilote QA","summary" to "<p>Résumé &amp; test</p>","type" to "regular","runtime" to 0,"rating" to mapOf("average" to 0),"airdate" to "2025-01-01","image" to mapOf("original" to "https://static.tvmaze.com/still.jpg"),"_embedded" to mapOf("show" to mapOf("id" to 11)))
private fun guests()=listOf(mapOf("person" to mapOf("id" to 99,"name" to "Élodie QA","image" to mapOf("medium" to "https://static.tvmaze.com/person.jpg")),"character" to mapOf("name" to "Invitée")),mapOf("person" to mapOf("id" to 99,"name" to "Élodie QA"),"character" to mapOf("name" to "Narratrice")))
private fun crew()=listOf(mapOf("person" to mapOf("id" to 100,"name" to "Auteur QA"),"type" to "Writer"))
fun main(args:Array<String>){
 val m=MediaItem("s",kind=MediaKind.SHOW,title="Série QA",source="tvmaze",sourceId="11",added=true)
 val e=Episode("tvmaze-episode-101","s",1,1,"Local",LocalDate.of(2025,1,1));val q=PublicTvDetailRules.query(m,e)
 val d=PublicTvDetailRules.episode(detail(),guests(),crew(),q,1700000000000)
 ok("Public source on query",q.provider=="tvmaze")
 ok("Public IDs are not TMDb IDs",q.seriesId==11&&q.episodeId==101&&m.tmdbId==null&&e.tmdbId==null)
 ok("Standard number present",q.number==1&&q.season==1)
 ok("Unknown number still looked up by exact ID",PublicTvDetailRules.query(m,e.copy(number=0,numberUnknown=true)).episodeId==101)
 bad("Wrong source rejected"){PublicTvDetailRules.query(m.copy(source="tmdb"),e)}
 bad("Wrong episode parent rejected"){PublicTvDetailRules.query(m,e.copy(showId="elsewhere"))}
 bad("Unknown public episode ID rejected"){PublicTvDetailRules.query(m,e.copy(id="imported"))}
 ok("Zero runtime preserved",d.runtime==0);ok("Zero score preserved",d.rating==0.0)
 ok("Unknown votes not invented",d.votes==null)
 ok("Source link points to TVmaze",d.sourceUrl=="https://www.tvmaze.com/episodes/101")
 ok("Guest and crew groups only",d.groups.map{it.kind}==listOf("guest_stars","crew"))
 ok("Roles combined for same person",d.groups[0].people.single().roles==listOf("Invitée","Narratrice"))
 ok("Summary sanitized",d.overview=="Résumé & test")
 ok("Date kept without invented time",d.airDate==LocalDate.of(2025,1,1))
 ok("Observed timestamp kept",d.observedAt==1700000000000)
 ok("Empty credit lists valid",PublicTvDetailRules.episode(detail(),emptyList<Any>(),emptyList<Any>(),q).groups.all{it.people.isEmpty()})
 val optional=PublicTvDetailRules.episode(detail()+mapOf("runtime" to null,"rating" to null,"airdate" to null),emptyList<Any>(),emptyList<Any>(),q)
 ok("Missing data remains unknown",optional.runtime==null&&optional.rating==null&&optional.airDate==null)
 for((label,raw) in listOf("different id" to (detail()+mapOf("id" to 102)),"wrong show" to (detail()+mapOf("_embedded" to mapOf("show" to mapOf("id" to 12)))),"missing show" to (detail()-"_embedded"),"wrong number" to (detail()+mapOf("number" to 2)),"wrong type" to (detail()+mapOf("type" to "significant_special")),"invalid date" to (detail()+mapOf("airdate" to "2025-02-30")),"negative duration" to (detail()+mapOf("runtime" to -1))))bad("Reject $label"){PublicTvDetailRules.episode(raw,guests(),crew(),q)}
 val sp=PublicTvDetailRules.episode(detail()+mapOf("type" to "significant_special","season" to 2,"number" to null),guests(),crew(),PublicTvDetailRules.query(m,e.copy(season=0,number=0,numberUnknown=true)))
 ok("Special read by ID with original season retained remotely",sp.id==101&&sp.query.season==0)
 bad("Contradictory people rejected"){PublicTvDetailRules.episode(detail(),guests(),listOf(mapOf("person" to mapOf("id" to 99,"name" to "Different"),"type" to "Writer")),q)}
 for(url in listOf("http://static.tvmaze.com/p","https://static.tvmaze.com.evil.test/p","https://user:pass@static.tvmaze.com/p","https://static.tvmaze.com:444/p","javascript:alert(1)"))ok("Unsafe image rejected $url",PublicTvDetailRules.image(url)==null)
 val person=mapOf<String,Any?>("id" to 99,"name" to "Élodie QA","birthday" to "1980-02-29")
 val work=mapOf("id" to 20,"name" to "Série exemple","premiered" to "2020-01-01")
 val film=PublicTvDetailRules.person(person,listOf(mapOf("voice" to true,"_embedded" to mapOf("show" to work)),mapOf("self" to true,"_embedded" to mapOf("show" to work))),listOf(mapOf("type" to "Writer","_embedded" to mapOf("show" to work))),99)
 ok("Person ID scoped",film.id==99&&film.department!!.contains("TVmaze"))
 ok("One show with three credits",film.works.size==1&&film.works[0].roles.size==3)
 ok("Filmography carries public source",film.works[0].media.source=="tvmaze"&&film.works[0].media.sourceId=="20"&&film.works[0].media.tmdbId==null)
 bad("Different person response rejected"){PublicTvDetailRules.person(person+mapOf("id" to 100),emptyList<Any>(),emptyList<Any>(),99)}
 bad("Absent embedded show rejected"){PublicTvDetailRules.person(person,listOf(mapOf("_links" to mapOf("show" to "https://evil.test"))),emptyList<Any>(),99)}
 ok("Renumbered exact ID can retain its own history",PublicTvDetailRules.sameEpisode(e,e.copy(season=2)))
 ok("Different ID same slot never carries history",!PublicTvDetailRules.sameEpisode(e,e.copy(id="tvmaze-episode-102")))
 ok("Same ID different local series rejected",!PublicTvDetailRules.sameEpisode(e,e.copy(showId="elsewhere")))
 if(args.isNotEmpty())File(args[0]).writeText(listOf(d.sourceUrl,d.title,d.overview,d.runtime.toString(),d.rating?.toInt().toString(),d.groups.joinToString("|"){g->g.kind+":"+g.people.joinToString(","){p->"${p.id}:${p.name}:${p.roles.joinToString("/")}"}},film.works.single().media.id,film.works.single().roles.joinToString("/")).joinToString("\n")+"\n")
 println("PublicTvDetailRules: $checks assertions passed (pure JVM, not Android runtime).")
}
