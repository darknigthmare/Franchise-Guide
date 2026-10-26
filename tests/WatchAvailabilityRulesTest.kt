import com.franchiseguide.app.model.*
import java.nio.file.*

fun main(args:Array<String>){
 var total=0
 fun ok(name:String,b:Boolean){check(b){name};total++;println("PASS $name")}
 fun rejects(name:String,f:()->Unit){ok(name,runCatching(f).isFailure)}
 fun q(region:String="FR",season:Int?=null)=WatchAvailabilityRules.query(MediaKind.SHOW,111,region,season)
 fun p(id:Int=1,name:String="Écran test",priority:Any?=3,logo:Any?="/qa.png")=mapOf("provider_id" to id,"provider_name" to name,"display_priority" to priority,"logo_path" to logo)
 fun raw(node:Any?=mapOf("flatrate" to listOf(p()),"rent" to listOf(p()),"link" to "https://www.themoviedb.org/tv/111-test/watch?locale=US&key=secret")):Map<String,Any?> = mapOf("id" to 111,"results" to mapOf("FR" to node,"US" to mapOf("buy" to listOf(p(8,"Autre pays test")))))
 fun read(r:Map<String,Any?> = raw(),req:WatchQuery=q())=WatchAvailabilityRules.parse(r,req,1700000000000L)
 ok("Series endpoint pinned",q().path=="/tv/111/watch/providers")
 ok("Season endpoint and specials distinct",q("fr",0).path=="/tv/111/season/0/watch/providers"&&q().key!=q("FR",0).key)
 ok("Region is normalized not global device setting",q(" fr ").country=="FR")
 ok("Movie endpoint isolated",WatchAvailabilityRules.query(MediaKind.MOVIE,5,"GB").path=="/movie/5/watch/providers")
 rejects("Missing identity rejected"){WatchAvailabilityRules.query(MediaKind.SHOW,null,"FR")}
 rejects("Zero identity rejected"){WatchAvailabilityRules.query(MediaKind.SHOW,0,"FR")}
 rejects("Unsupported books rejected"){WatchAvailabilityRules.query(MediaKind.BOOK,1,"FR")}
 rejects("Unsupported games rejected"){WatchAvailabilityRules.query(MediaKind.GAME,1,"FR")}
 rejects("Country injection rejected"){q("FR&key=x")}
 rejects("Negative season rejected"){q("FR",-1)}
 rejects("Movie cannot have seasons"){WatchAvailabilityRules.query(MediaKind.MOVIE,1,"FR",1)}
 ok("Default HTTPS port source normalized",WatchAvailabilityRules.sourceLink("https://www.themoviedb.org:443/tv/111/watch",q())=="https://www.themoviedb.org/tv/111/watch?locale=FR")
 val s=read()
 ok("Two access categories are not flattened",s.totalOffers==2&&s.totalProviders==1&&s.groups[0].offers.single().id==s.groups[3].offers.single().id)
 ok("Country isolation excludes other providers",s.groups.flatMap{it.offers}.none{it.id==8})
 ok("Source link removes all non-region params",s.link=="https://www.themoviedb.org/tv/111-test/watch?locale=FR")
 ok("Unknown country has no fabricated offers",read(req=q("DE")).let{!it.countryReported&&it.totalOffers==0&&it.link==null})
 ok("Explicit empty country preserves distinct state",WatchAvailabilityRules.state(read(raw(emptyMap<String,Any?>())))=="no-offers-reported")
 ok("Missing country has distinct unknown label",WatchAvailabilityRules.state(read(req=q("DE")))=="country-not-reported")
 ok("Not read stays distinct",WatchAvailabilityRules.state(null)=="not-read")
 rejects("Missing result payload rejected"){read(mapOf("id" to 111))}
 rejects("Malformed country node rejected"){read(raw(null))}
 rejects("Wrong title payload rejected"){read(raw()+mapOf("id" to 444))}
 ok("Season id response need not equal series id",read(raw()+mapOf("id" to 9001),q("FR",1)).totalOffers==2)
 for(v in listOf(null,emptyMap<String,String>(),"stream",3))rejects("Wrong category array $v"){read(raw(mapOf("buy" to v)))}
 rejects("Missing provider ID rejected"){read(raw(mapOf("buy" to listOf(mapOf("provider_name" to "Test")))))}
 rejects("Contradictory provider identity rejected"){read(raw(mapOf("buy" to listOf(p(1,"B")),"rent" to listOf(p(1,"A")))))}
 ok("Identical repeated provider not double counted",read(raw(mapOf("buy" to listOf(p(),p())))).totalOffers==1)
 rejects("Different priorities in repeated row rejected"){read(raw(mapOf("buy" to listOf(p(),p(priority=2)))))}
 rejects("Negative priorities rejected"){read(raw(mapOf("buy" to listOf(p(priority=-1)))))}
 rejects("Fractional priorities rejected"){read(raw(mapOf("buy" to listOf(p(priority=1.5)))))}
 val ordered=read(raw(mapOf("buy" to listOf(p(3,"C",1),p(2,"B",1),p(4,"D",0),p(5,"E",null)))))
 ok("Offers sorted by priority then stable ID",ordered.groups[4].offers.map{it.id}==listOf(4,2,3,5))
 ok("Safe image path expands pinned TMDb logo",WatchAvailabilityRules.logo("/test.png")=="https://image.tmdb.org/t/p/w185/test.png")
 for(url in listOf("https://evil.test/x","//evil.test/x","/a/b.png","/x.png?q=key"))ok("Unsafe logo omitted $url",read(raw(mapOf("buy" to listOf(p(logo=url))))).let{it.groups[4].offers.single().logoUrl==null&&it.ignoredLinks==1})
 for(url in listOf("javascript:alert(1)","http://www.themoviedb.org/tv/111/watch","https://user:pass@www.themoviedb.org/tv/111/watch","https://www.themoviedb.org.evil.test/tv/111/watch","https://www.themoviedb.org:444/tv/111/watch","https://www.themoviedb.org/tv/999/watch","https://www.themoviedb.org/movie/111/watch","https://www.themoviedb.org/tv/111/watch#secret"))ok("Untrusted source link dropped $url",WatchAvailabilityRules.sourceLink(url,q())==null)
 rejects("Control characters in service name rejected"){read(raw(mapOf("buy" to listOf(p(name="A\nB")))))}
 ok("Names are literal data",read(raw(mapOf("buy" to listOf(p(name="<test>"))))).groups[4].offers.single().name=="<test>")
 ok("Accent-insensitive filter",WatchAvailabilityRules.filter(s,"rent","ecran").single().offers.size==1)
 ok("Filter without matches has no invented offers",WatchAvailabilityRules.filter(s,"all","absent").all{it.offers.isEmpty()})
 rejects("Invalid category filter rejected"){WatchAvailabilityRules.filter(s,"download")}
 val regions=WatchAvailabilityRules.regions(mapOf("results" to listOf(mapOf("iso_3166_1" to "US","native_name" to "United States"),mapOf("iso_3166_1" to "FR","english_name" to "France"))))
 ok("Country list follows explicit codes",regions.map{it.code}==listOf("FR","US"))
 rejects("Missing country list rejected"){WatchAvailabilityRules.regions(emptyMap())}
 rejects("Duplicate countries rejected"){WatchAvailabilityRules.regions(mapOf("results" to List(2){mapOf("iso_3166_1" to "FR","english_name" to "France")}))}
 ok("Empty country list stays empty",WatchAvailabilityRules.regions(mapOf("results" to emptyList<Any>())).isEmpty())
 val show=MediaItem(id="s",kind=MediaKind.SHOW,title="QA",seasons=listOf(SeasonInfo(2,"Season"),SeasonInfo(0,"Specials")))
 ok("Known seasons include explicit specials only",WatchAvailabilityRules.seasons(show,listOf(Episode("e","s",1,1,"X",null),Episode("x","other",6,1,"Y",null)))==listOf(0,1,2))
 rejects("Excessive offers refused rather than truncated"){read(raw(mapOf("buy" to List(1001){p(it+1,"Test$it")})))}
 ok("1000 offers remain searchable",WatchAvailabilityRules.filter(read(raw(mapOf("buy" to List(1000){p(it+1,"Test$it")}))),query="Test999").sumOf{it.offers.size}==1)
 rejects("Negative consultation timestamp rejected"){WatchAvailabilityRules.parse(raw(),q(),-1)}
 fun json(v:Any?):String=when(v){null->"null";is String->"\""+v.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\"";is Number,is Boolean->v.toString();is List<*>->v.joinToString(",","[","]"){json(it)};is Map<*,*>->v.entries.joinToString(",","{","}"){json(it.key.toString())+":"+json(it.value)};else->error("Unsupported test JSON")}
 val parity=listOf(read(),read(req=q("DE")),read(raw(emptyMap<String,Any?>())),ordered).map{a->mapOf("key" to a.query.key,"path" to a.query.path,"state" to WatchAvailabilityRules.state(a),"countryReported" to a.countryReported,"link" to a.link,"totalProviders" to a.totalProviders,"totalOffers" to a.totalOffers,"groups" to a.groups.map{g->mapOf("mode" to g.mode,"offers" to g.offers.map{p->listOf(p.id,p.name,p.logoUrl,p.priority)})})}
 if(args.isNotEmpty())Files.writeString(Path.of(args[0]),json(parity))
 println("WatchAvailabilityRules: $total assertions passed on JVM; no Android SDK/device.")
}
