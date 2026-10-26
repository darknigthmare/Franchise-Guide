import com.franchiseguide.app.model.*
import java.io.File
private var checks=0
private fun ok(name:String,v:Boolean){check(v){name};checks++;println("PASS $name")}
private fun no(name:String,f:()->Unit)=ok(name,runCatching(f).isFailure)
private fun edition(extra:Map<String,Any?> = emptyMap()):Map<String,Any?> = mapOf("key" to "/books/OL10M","title" to "Le Livre de test","works" to listOf(mapOf("key" to "/works/OL1W")),"number_of_pages" to 220,"publish_date" to "mai 2001","publishers" to listOf("Éditeur QA"),"languages" to listOf(mapOf("key" to "/languages/fre")),"isbn_13" to listOf("9780140328721"),"covers" to listOf(123))+extra
private fun page(extra:Map<String,Any?> = emptyMap()):Map<String,Any?> = mapOf("size" to 1,"links" to mapOf("work" to "/works/OL1W"),"entries" to listOf(edition()))+extra
fun main(args:Array<String>){
 val m=MediaItem("m",kind=MediaKind.BOOK,title="Local",source="openlibrary",sourceId="/works/OL1W",progressCurrent=20,progressTotal=0,progressUnit="pages",watched=true,plays=2,watchHistory=listOf(1000,2000),lastWatchedAt=2000,rating=8,userNote="Privé",personalFormat="Mon poche",trackingStatus="active")
 ok("Bare works normalized",BookEditionRules.key("OL1W")=="/works/OL1W")
 for(k in listOf("/books/OL1M","https://evil.test/works/OL1W","/works/OL1W?x=1","/works/../x","OL0W","ol1w"))no("Bad identifier $k"){BookEditionRules.key(k)}
 val p=BookEditionRules.page(page(),"OL1W");val e=p.entries.single()
 ok("Edition date not interpreted as fabricated release",e.publishDate=="mai 2001")
 ok("Edition verified by work key",e.workVerified)
 ok("Edition fields match provider",e.pages==220&&e.languages==listOf("fre")&&e.publishers==listOf("Éditeur QA"))
 ok("Safe generated cover",e.coverUrl=="https://covers.openlibrary.org/b/id/123-M.jpg")
 ok("Unknown pages remain null",BookEditionRules.edition(edition(mapOf("number_of_pages" to null)),"/works/OL1W").pages==null)
 for(n in listOf(-1,1.5,"250",10000001))no("Bad pages $n"){BookEditionRules.edition(edition(mapOf("number_of_pages" to n)),"/works/OL1W")}
 no("Other work rejected"){BookEditionRules.edition(edition(mapOf("works" to listOf(mapOf("key" to "/works/OL2W")))),"/works/OL1W")}
 val unverified=BookEditionRules.edition(edition(mapOf("works" to emptyList<Any>())),"/works/OL1W")
 ok("Missing link does not invent proof",!unverified.workVerified)
 no("Unverified edition cannot change pages"){BookEditionRules.prepare(m,unverified)}
 ok("ISBN10 checksum",BookEditionRules.isbn("0-14-032872-6")=="0140328726")
 ok("ISBN13 checksum",BookEditionRules.isbn("9780140328721")=="9780140328721")
 ok("Invalid ISBN rejected",BookEditionRules.isbn("9780140328722")==null)
 ok("ISBN warning counted",BookEditionRules.edition(edition(mapOf("isbn_13" to listOf("9780140328722"))),"/works/OL1W").invalidIsbnCount==1)
 no("Duplicate edition rows rejected"){BookEditionRules.page(page(mapOf("size" to 2,"entries" to listOf(edition(),edition()))),"OL1W")}
 no("Empty inconsistent page rejected"){BookEditionRules.page(page(mapOf("size" to 5,"entries" to emptyList<Any>())),"OL1W")}
 ok("Explicit empty collection valid",BookEditionRules.page(page(mapOf("size" to 0,"entries" to emptyList<Any>())),"OL1W").nextOffset==null)
 no("Provider next URL cannot change host"){BookEditionRules.page(page(mapOf("size" to 2,"links" to mapOf("next" to "https://evil.test/works/OL1W/editions.json?offset=1"))),"OL1W")}
 val first=BookEditionRules.page(page(mapOf("size" to 2)),"OL1W")
 val second=BookEditionRules.page(page(mapOf("size" to 2,"entries" to listOf(edition(mapOf("key" to "/books/OL20M"))))),"OL1W",1)
 ok("Adjacent pages merged",BookEditionRules.merge(first,second).entries.size==2)
 no("Duplicate between pages rejected"){BookEditionRules.merge(first,BookEditionRules.page(page(mapOf("size" to 2)),"OL1W",1))}
 ok("Search loaded publishers ignores accents",BookEditionRules.filter(p.entries,"editeur").size==1)
 ok("Language selection exact",BookEditionRules.filter(p.entries,"","eng").isEmpty())
 val plan=BookEditionRules.prepare(m,e,1000);val updated=BookEditionRules.apply(m,plan,1001)
 ok("Only total modified",updated==m.copy(progressTotal=220))
 no("Changed progress blocks old confirmation"){BookEditionRules.apply(m.copy(progressCurrent=21),plan,1001)}
 ok("Unrelated note preserved",BookEditionRules.apply(m.copy(userNote="Nouvelle note"),plan,1001).userNote=="Nouvelle note")
 no("Expired confirmation rejected"){BookEditionRules.apply(m,plan,601001)}
 no("Confirmation cannot precede creation"){BookEditionRules.apply(m,plan,999)}
 no("Chapter unit not changed implicitly"){BookEditionRules.prepare(m.copy(progressUnit="chapitres"),e)}
 no("Current progress never reduced"){BookEditionRules.prepare(m.copy(progressCurrent=250),e)}
 val unread=m.copy(progressCurrent=220,watched=false,plays=0,watchHistory=emptyList(),lastWatchedAt=null)
 ok("Equal total does not mark finished",!BookEditionRules.apply(unread,BookEditionRules.prepare(unread,e,1000),1001).watched)
 no("Removed book rejected"){BookEditionRules.prepare(m.copy(added=false),e)}
 if(args.isNotEmpty())File(args[0]).writeText(listOf(p.work,e.id,e.title,e.pages.toString(),e.publishDate,e.publishers.joinToString("|"),e.languages.joinToString("|"),e.isbns.joinToString("|"),e.coverUrl,updated.progressCurrent.toString(),updated.progressTotal.toString(),updated.plays.toString(),updated.userNote).joinToString("\n")+"\n")
 println("BookEditionRules: $checks assertions passed")
}
