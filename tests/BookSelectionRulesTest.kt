import com.franchiseguide.app.model.*
import java.io.File
private var count=0
private fun ok(name:String,condition:Boolean){check(condition){name};count++;println("PASS $name")}
private fun bad(name:String,action:()->Unit){ok(name,runCatching(action).isFailure)}
private fun raw(extra:Map<String,Any?> = emptyMap()):Map<String,Any?> = mapOf("key" to "/books/OL10M","title" to "Édition QA","works" to listOf(mapOf("key" to "/works/OL1W")),"number_of_pages" to 220,"publish_date" to "2001","publishers" to listOf("Maison QA"),"languages" to listOf(mapOf("key" to "/languages/fre")),"physical_format" to "Poche","isbn_13" to listOf("9780140328721"),"isbn_10" to listOf("0140328726"),"covers" to listOf(123))+extra
fun main(args:Array<String>){
 val m=MediaItem("m",kind=MediaKind.BOOK,title="Œuvre QA",source="openlibrary",sourceId="/works/OL1W",added=true,progressCurrent=20,progressTotal=0,progressUnit="pages",watched=true,plays=2,watchHistory=listOf(1000,2000),lastWatchedAt=2000,trackingStatus="active",rating=9,userNote="Privé",personalFormat="Mon poche")
 val e=BookEditionRules.edition(raw(),"/works/OL1W")
 ok("ISBN10 normalized",BookSelectionRules.input("ISBN-10: 0-14-032872-6")=="9780140328721")
 ok("ISBN13 input accepted",BookSelectionRules.input("ISBN 978 0 14 032872 1")=="9780140328721")
 ok("ISBN10 X accepted",BookSelectionRules.input("080442957x")=="9780804429573")
 ok("979 retained",BookSelectionRules.input("9791090636071")=="9791090636071")
 for(s in listOf("4006381333931","9780140328722","01234","","https://evil.test","9780140328721\u0000"))bad("Invalid ISBN refused $s"){BookSelectionRules.input(s)}
 ok("EAN rejected by edition parser too",BookEditionRules.isbn("4006381333931")==null)
 ok("URL pinned and limited",BookSelectionRules.searchUrl("0140328726").let{it.startsWith("https://openlibrary.org/search.json?q=isbn%3A9780140328721")&&it.endsWith("limit=5&page=1")})
 val response=mapOf("numFound" to 1,"start" to 0,"docs" to listOf(mapOf("key" to "/works/OL1W","title" to "Œuvre QA","author_name" to listOf("Auteur"),"editions" to mapOf("docs" to listOf(mapOf("key" to "/books/OL10M"))))))
 val candidate=BookSelectionRules.parseSearch(response,"0140328726").rows.single()
 ok("Candidate references extracted",candidate.work=="/works/OL1W"&&candidate.editionId==e.id)
 ok("Exact ISBN verified",BookSelectionRules.verifyEdition(raw(),candidate)==e)
 bad("Wrong edition refused"){BookSelectionRules.verifyEdition(raw(mapOf("key" to "/books/OL20M")),candidate)}
 bad("Wrong work refused"){BookSelectionRules.verifyEdition(raw(mapOf("works" to listOf(mapOf("key" to "/works/OL2W")))),candidate)}
 bad("No work proof refused"){BookSelectionRules.verifyEdition(raw(mapOf("works" to emptyList<Any>())),candidate)}
 bad("Wrong ISBN refused"){BookSelectionRules.verifyEdition(raw(mapOf("isbn_13" to listOf("9791090636071"),"isbn_10" to emptyList<Any>())),candidate)}
 bad("Missing edition candidate refused"){BookSelectionRules.parseSearch(response+mapOf("docs" to listOf(mapOf("key" to "/works/OL1W","title" to "X"))),candidate.isbn)}
 ok("Empty result accepted without claiming absence",BookSelectionRules.parseSearch(mapOf("num_found" to 0,"docs" to emptyList<Any>()),candidate.isbn).rows.isEmpty())
 ok("Limited results signalled",BookSelectionRules.parseSearch(response+mapOf("numFound" to 20),candidate.isbn).limited)
 val p=BookSelectionRules.prepare(m,e,900,now=1000);val result=BookSelectionRules.apply(m,p,1001)
 ok("Default reference changes nothing else",result.copy(selectedEdition=null)==m)
 ok("Observation time separate from choice",result.selectedEdition!!.observedAt==900L&&result.selectedEdition!!.selectedAt==1000L)
 ok("Snapshot ISBN and cover retained",result.selectedEdition!!.isbns.contains(candidate.isbn)&&result.selectedEdition!!.coverId==123L)
 val pp=BookSelectionRules.prepare(m,e,900,true,1000);val pages=BookSelectionRules.apply(m,pp,1001)
 ok("Optional pages keep tracking and private note",pages.progressTotal==220&&pages.progressCurrent==20&&pages.plays==2&&pages.userNote=="Privé")
 val equal=m.copy(progressCurrent=220,watched=false,plays=0,watchHistory=emptyList());ok("Total does not create completion",!BookSelectionRules.apply(equal,BookSelectionRules.prepare(equal,e,900,true,1000),1001).watched)
 bad("Too low total refused"){BookSelectionRules.prepare(m.copy(progressCurrent=300),e,900,true,1000)}
 bad("Chapter progress not converted"){BookSelectionRules.prepare(m.copy(progressUnit="chapitres"),e,900,true,1000)}
 ok("Reference independent of progress unit",BookSelectionRules.apply(m.copy(progressUnit="chapitres"),BookSelectionRules.prepare(m.copy(progressUnit="chapitres"),e,900,now=1000),1001).progressUnit=="chapitres")
 val unknown=BookEditionRules.edition(raw(mapOf("number_of_pages" to null)),candidate.work);ok("Unknown page reference retained",BookSelectionRules.apply(m,BookSelectionRules.prepare(m,unknown,900,now=1000),1001).selectedEdition!!.pages==null)
 bad("Unknown pages not applied as total"){BookSelectionRules.prepare(m,unknown,900,true,1000)}
 bad("Stale reference protected"){BookSelectionRules.apply(result,p,1002)}
 bad("Progress checked for total change"){BookSelectionRules.apply(m.copy(progressCurrent=25),pp,1002)}
 ok("Unrelated edits preserved",BookSelectionRules.apply(m.copy(userNote="Nouvelle"),p,1002).userNote=="Nouvelle")
 bad("Expired selection rejected"){BookSelectionRules.apply(m,p,601001)}
 bad("Clock earlier than selection rejected"){BookSelectionRules.apply(m,p,999)}
 bad("Removed book protected"){BookSelectionRules.apply(m.copy(added=false),p,1001)}
 bad("Other-source book protected"){BookSelectionRules.prepare(m.copy(source="local"),e,900,now=1000)}
 val cleared=BookSelectionRules.apply(pages,BookSelectionRules.prepareClear(pages,1002),1003)
 ok("Clearing reference preserves total and dates",cleared==pages.copy(selectedEdition=null))
 ok("Normalized snapshot roundtrips",BookSelectionRules.normalize(BookSelectionRules.asMap(result.selectedEdition!!),m)==result.selectedEdition)
 ok("Unknown fields removed",!BookSelectionRules.asMap(BookSelectionRules.normalize(BookSelectionRules.asMap(result.selectedEdition!!)+mapOf("token" to "secret"))).containsKey("token"))
 bad("Wrong-work snapshot rejected"){BookSelectionRules.normalize(BookSelectionRules.asMap(result.selectedEdition!!),m.copy(sourceId="/works/OL2W"))}
 bad("Stored non-ISBN EAN rejected"){BookSelectionRules.normalize(BookSelectionRules.asMap(result.selectedEdition!!)+mapOf("isbns" to listOf("4006381333931")))}
 bad("Invalid language rejected"){BookSelectionRules.normalize(BookSelectionRules.asMap(result.selectedEdition!!)+mapOf("languages" to listOf("english")))}
 bad("Observation in future of selection rejected"){BookSelectionRules.snapshot(e,2000,1000)}
 if(args.isNotEmpty())File(args[0]).writeText(listOf(candidate.isbn,candidate.work,candidate.editionId,result.selectedEdition!!.title,result.selectedEdition!!.observedAt,result.selectedEdition!!.selectedAt,result.selectedEdition!!.isbns.joinToString("|"),result.selectedEdition!!.coverId,pages.progressTotal,pages.plays,pages.userNote,cleared.selectedEdition?.title?:"null").joinToString("\n")+"\n")
 println("BookSelectionRules: $count assertions passed")
}
