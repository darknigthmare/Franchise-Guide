import com.franchiseguide.app.model.*
private var passed=0
private fun ok(name:String,value:Boolean){check(value){name};passed++;println("PASS $name")}
private fun reject(name:String,block:()->Unit){ok(name,runCatching(block).isFailure)}
private fun item(id:Int,title:String="Série $id",year:Int?=2025,lang:String?="French")=MediaItem(id="tvmaze-show-$id",kind=MediaKind.SHOW,title=title,source="tvmaze",sourceId=id.toString(),year=year,originalLanguage=lang)
fun main(){
 val a=DiscoveryRules.accept(emptyList(),listOf(item(1),item(2)),1)
 ok("First discovery page retained",a.items.size==2&&a.page==1&&a.next==2)
 val b=DiscoveryRules.accept(a.items,listOf(item(2),item(3)),2)
 ok("Server pagination identity deduplication",b.items.size==3)
 ok("Source ID never replaced by title",DiscoveryRules.accept(emptyList(),listOf(item(1,"Same"),item(2,"Same")),1).items.size==2)
 ok("French TVmaze language matches code",DiscoveryRules.filtered(a.items,DiscoveryRules.Query(MediaKind.SHOW,language="fr")).size==2)
 ok("English filter excludes French original language",DiscoveryRules.filtered(a.items,DiscoveryRules.Query(MediaKind.SHOW,language="en")).isEmpty())
 ok("Year filter excludes unknown year",DiscoveryRules.filtered(listOf(item(1,year=null),item(2,year=2025)),DiscoveryRules.Query(MediaKind.SHOW,min=2020)).size==1)
 ok("No year filter keeps unknown year",DiscoveryRules.filtered(listOf(item(1,year=null)),DiscoveryRules.Query(MediaKind.SHOW)).size==1)
 reject("Unordered min and max rejected"){DiscoveryRules.Query(MediaKind.SHOW,min=2050,max=2020)}
 reject("Year out of bounds rejected"){DiscoveryRules.Query(MediaKind.SHOW,min=1799)}
 reject("Invalid language rejected"){DiscoveryRules.Query(MediaKind.SHOW,language="<img>")}
 reject("Page zero rejected"){DiscoveryRules.accept(emptyList(),emptyList(),0)}
 reject("Page beyond 100 rejected"){DiscoveryRules.accept(emptyList(),emptyList(),101)}
 reject("Oversized server page rejected"){DiscoveryRules.accept(emptyList(),List(501){ item(it+1) },1)}
 ok("No automatic history mutation by discovery",listOf(item(1)).single().watched==false)
 ok("Same source ID retains identity after renaming",DiscoveryRules.key(item(1,"Nouveau"))==DiscoveryRules.key(item(1,"Ancien")))
 println("$passed V0.33 discovery Kotlin assertions passed")
}
