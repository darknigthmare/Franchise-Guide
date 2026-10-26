import com.franchiseguide.app.model.*
import java.time.LocalDate
import java.io.File

private var checks=0
private fun checkRule(name:String,result:Boolean){check(result){name};checks++;println("PASS $name")}
private fun reject(name:String,body:()->Unit){checkRule(name,runCatching(body).isFailure)}
private fun fixture():Map<String,Any?> = linkedMapOf(
 "id" to 501,"show_id" to 111,"season_number" to 1,"episode_number" to 1,"name" to "Le signal de validation",
 "overview" to "Synopsis entièrement fictif utilisé pour la validation.\nAucun compte réel.","air_date" to "2025-03-14","runtime" to 0,"vote_average" to 0.0,"vote_count" to 0,"production_code" to "QA-101","still_path" to "/qa-still.png",
 "credits" to mapOf("id" to 501,"cast" to listOf(mapOf("id" to 900,"name" to "Acteur QA","character" to "Capitaine","profile_path" to "/qa-cast.png"),mapOf("id" to 900,"name" to "Acteur QA","character" to "Narrateur","profile_path" to "/qa-cast.png")),"guest_stars" to listOf(mapOf("id" to 901,"name" to "Invitée QA","character" to "Émissaire","profile_path" to "/qa-guest.png")),"crew" to listOf(mapOf("id" to 902,"name" to "Réalisateur QA","department" to "Directing","job" to "Director","profile_path" to null))))
fun main(args:Array<String>){
 val m=MediaItem("s",kind=MediaKind.SHOW,title="Série QA",tmdbId=111,added=true)
 val e=Episode("e1","s",1,1,"Local",LocalDate.of(2025,1,1),watched=true,tmdbId=501,plays=3,watchHistory=listOf(1000,2000),lastWatchedAt=2000,collected=true,rating=6,userNote="Avant")
 val e2=e.copy(id="e2",number=2,tmdbId=502,title="Suivant",watched=false,plays=0,watchHistory=emptyList(),lastWatchedAt=null)
 val state=EpisodeDetailRules.State(listOf(m),listOf(e,e2));val q=EpisodeDetailRules.query(m,e,"fr-FR");val p=EpisodeDetailRules.parse(fixture(),q,1700000000000)
 checkRule("Query uses series + season + episode",q.path=="/tv/111/season/1/episode/1")
 checkRule("Query preserves episode identity",q.episodeId==501)
 checkRule("Language is explicit",q.language=="fr-FR")
 checkRule("Zero season is a real special",EpisodeDetailRules.query(m,e.copy(season=0),"fr-FR").season==0)
 reject("Unknown season cannot create a guessed request"){EpisodeDetailRules.query(m,e.copy(seasonUnknown=true),"fr-FR")}
 reject("Unknown episode cannot create a guessed request"){EpisodeDetailRules.query(m,e.copy(numberUnknown=true),"fr-FR")}
 reject("No series TMDb ID prevents request"){EpisodeDetailRules.query(m.copy(tmdbId=null),e,"fr-FR")}
 reject("Episode belongs to requested parent"){EpisodeDetailRules.query(m,e.copy(showId="other"),"fr-FR")}
 reject("Language injection rejected"){EpisodeDetailRules.query(m,e,"fr-FR&api_key=evil")}
 checkRule("Unknown episode TMDb ID stays unknown",EpisodeDetailRules.query(m,e.copy(tmdbId=null),"en-US").episodeId==null)
 checkRule("Runtime zero preserved",p.runtime==0)
 checkRule("Rating zero preserved",p.rating==0.0&&p.votes==0)
 checkRule("Roles grouped within cast",p.groups[0].people.single().roles==listOf("Capitaine","Narrateur"))
 checkRule("Separate credit groups",p.groups.map{it.kind}==listOf("cast","guest_stars","crew"))
 checkRule("Date parsed without fabricated time",p.airDate==LocalDate.of(2025,3,14))
 val nullable=EpisodeDetailRules.parse(fixture()+mapOf("runtime" to null,"air_date" to null,"vote_average" to null),q)
 checkRule("Optional absence remains unknown",nullable.runtime==null&&nullable.airDate==null&&nullable.rating==null)
 checkRule("Empty credits valid",EpisodeDetailRules.parse(fixture()+mapOf("credits" to mapOf("cast" to emptyList<Any>(),"crew" to emptyList<Any>(),"guest_stars" to emptyList<Any>())),q).groups.all{it.people.isEmpty()})
 reject("Missing credits does not substitute show cast"){EpisodeDetailRules.parse(fixture()-"credits",q)}
 for((field,value)in listOf("id" to 999,"show_id" to 222,"season_number" to 2,"episode_number" to 2))reject("Wrong identity $field rejected"){EpisodeDetailRules.parse(fixture()+mapOf(field to value),q)}
 reject("Invalid air date rejected"){EpisodeDetailRules.parse(fixture()+mapOf("air_date" to "2025-02-30"),q)}
 reject("Negative runtime rejected"){EpisodeDetailRules.parse(fixture()+mapOf("runtime" to -1),q)}
 reject("String numeric rating rejected"){EpisodeDetailRules.parse(fixture()+mapOf("vote_average" to "9"),q)}
 reject("Long synopsis rejected"){EpisodeDetailRules.parse(fixture()+mapOf("overview" to "x".repeat(30001)),q)}
 val image=EpisodeDetailRules.parse(fixture()+mapOf("still_path" to "https://evil.test/a.jpg"),q)
 checkRule("Untrusted image path discarded",image.stillUrl==null&&image.ignoredImages==1)
 checkRule("Source link uses validated identity",p.sourceUrl=="https://www.themoviedb.org/tv/111/season/1/episode/1")
 for(path in listOf("/.","/..","/../x","//evil.test/a","/x?key=y"))checkRule("Image path rejected $path",EpisodeDetailRules.image(path)==null)
 val plan=EpisodeDetailRules.prepare(state,"s","e1","9"," New ",1000)
 checkRule("Prepare cannot mutate episode",e.rating==6&&e.userNote=="Avant")
 val after=EpisodeDetailRules.apply(state,plan,1001)
 checkRule("Only own fields edited",after.episodes[0]==e.copy(rating=9,userNote="New")&&after.media==state.media&&after.episodes[1]==e2)
 val changed=state.copy(episodes=listOf(e.copy(plays=4,lastWatchedAt=3000,watchHistory=listOf(1000,2000,3000)),e2))
 checkRule("Later viewing survives note edit",EpisodeDetailRules.apply(changed,plan,1001).episodes[0].watchHistory==listOf(1000L,2000L,3000L))
 checkRule("Empty own fields become null",EpisodeDetailRules.personal("","   ")==EpisodePersonalFields(null,null))
 reject("Fractional personal rating refused"){EpisodeDetailRules.personal("8.5","")}
 reject("Personal score zero refused"){EpisodeDetailRules.personal("0","")}
 reject("Personal score above ten refused"){EpisodeDetailRules.personal("11","")}
 reject("Overlong private note refused without truncation"){EpisodeDetailRules.personal("1","x".repeat(2001))}
 checkRule("Multiline text preserved",EpisodeDetailRules.personal("1"," A\nB ").note=="A\nB")
 reject("Control character refused"){EpisodeDetailRules.personal("1","x\u0000y")}
 reject("Changed personal note invalidates edit"){EpisodeDetailRules.apply(state.copy(episodes=listOf(e.copy(userNote="Elsewhere"),e2)),plan,1001)}
 reject("Changed personal score invalidates edit"){EpisodeDetailRules.apply(state.copy(episodes=listOf(e.copy(rating=10),e2)),plan,1001)}
 reject("Changed episode identity invalidates edit"){EpisodeDetailRules.apply(state.copy(episodes=listOf(e.copy(tmdbId=100),e2)),plan,1001)}
 reject("Removed show not editable"){EpisodeDetailRules.prepare(state.copy(media=listOf(m.copy(added=false))),"s","e1","1","",1000)}
 reject("Duplicate local episode ID not editable"){EpisodeDetailRules.locate(state.copy(episodes=listOf(e,e)),"s","e1")}
 reject("Form older than ten minutes refused"){EpisodeDetailRules.apply(state,plan,601001)}
 reject("Clock before prepared form refused"){EpisodeDetailRules.apply(state,plan,999)}
 checkRule("Neighbors use numbering not input order",EpisodeDetailRules.neighbors(state.copy(episodes=listOf(e2,e)),"s","e1",false)==(null to "e2"))
 val special=e.copy(id="sp",season=0)
 checkRule("Hidden specials not inserted into sequence",EpisodeDetailRules.neighbors(state.copy(episodes=state.episodes+special),"s","e1",false).first==null)
 checkRule("Explicit specials can precede standard episode",EpisodeDetailRules.neighbors(state.copy(episodes=state.episodes+special),"s","e1",true).first=="sp")
 checkRule("Conflicting numbering disables guessed navigation",EpisodeDetailRules.neighbors(state.copy(episodes=state.episodes+e.copy(id="dup")),"s","e1",false)==(null to null))
 if(args.isNotEmpty())File(args[0]).writeText(listOf(p.title,p.overview,p.airDate.toString(),p.runtime.toString(),p.rating?.toInt().toString(),p.votes.toString(),p.productionCode,p.sourceUrl,p.stillUrl.orEmpty(),p.groups.joinToString("|"){g->g.kind+":"+g.people.joinToString(";"){c->"${c.id},${c.name},${c.roles.joinToString("/")}"}},after.episodes[0].rating.toString(),after.episodes[0].userNote.orEmpty(),after.episodes[0].plays.toString()).joinToString("\n"))
 println("EpisodeDetailRules: $checks assertions passed on JVM; no Android SDK/UI validation.")
}
