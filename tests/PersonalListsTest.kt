import com.franchiseguide.app.model.*
import java.io.File

fun main(args:Array<String>){
 var passed=0
 fun ok(n:String,x:Boolean){check(x){n};passed++;println("PASS $n")}
 fun bad(n:String,f:()->Unit){ok(n,runCatching(f).isFailure)}
 val m=MediaItem("m",kind=MediaKind.MOVIE,title="Film test",added=true,hidden=true,watched=true,plays=2,userNote="Privé",watchHistory=listOf(1700000000000,1700000100000),listIds=setOf("l"))
 val s=MediaItem("s",kind=MediaKind.SHOW,title="Série test",added=true,listIds=setOf("l"))
 val g=MediaItem("g",kind=MediaKind.GAME,title="Jeu",added=true,watched=true,trackingStatus="active",listIds=setOf("other"))
 val b=MediaItem("b",kind=MediaKind.BOOK,title="Livre",added=true)
 val gone=MediaItem("r",kind=MediaKind.MOVIE,title="Retiré",added=false,listIds=setOf("l"))
 val suggestion=MediaItem("sg",kind=MediaKind.MOVIE,title="Suggestion",added=false,isSuggestion=true,listIds=setOf("l"))
 val base=ListRules.State(listOf(s,m,g,b,gone),listOf(suggestion),listOf(UserList("l","Ma liste","Texte personnel"),UserList("other","Autre")))
 val l=base.lists[0]
 val created=ListRules.create(base,UserList("new","  Nouvelle ","Texte\nsur deux lignes"))
 ok("New list trimmed and empty but present",created.lists.last().name=="Nouvelle"&&ListRules.members(created,"new").isEmpty())
 ok("Creation never changes source",base.lists.size==2)
 bad("Blank name rejected"){ListRules.create(base,UserList("new"," "))}
 bad("Long name rejected"){ListRules.create(base,UserList("new","x".repeat(121)))}
 bad("Duplicate normalized name rejected"){ListRules.create(base,UserList("new","ＭＡ   ＬＩＳＴＥ"))}
 bad("Duplicate id rejected"){ListRules.create(base,UserList("l","Another"))}
 bad("Control name rejected"){ListRules.create(base,UserList("new","a\nb"))}
 bad("Unsafe description control rejected"){ListRules.create(base,UserList("new","Test","x\u0001"))}
 ok("Multiline description accepted",ListRules.create(base,UserList("new","Test","a\r\nb")).lists.last().description=="a\r\nb")
 val nonlatin=ListRules.create(ListRules.create(base,UserList("cat","猫")),UserList("dog","犬"))
 ok("Non-latin names stay distinct",nonlatin.lists.size==4)
 ok("Emoji list name retained",ListRules.create(base,UserList("emoji","🦇")).lists.last().name=="🦇")
 ok("Hidden members included but removed references not counted",ListRules.members(base,"l").map{it.id}==listOf("s","m"))
 val edited=ListRules.edit(base,l.copy(name="Renommée"),l)
 ok("Rename changes only definition",edited.media==base.media&&edited.suggestions==base.suggestions&&edited.lists.first().name=="Renommée")
 bad("Old definition cannot overwrite newer one"){ListRules.edit(edited,l.copy(name="Again"),l)}
 bad("Missing list is not silently recreated"){ListRules.edit(base,UserList("missing","No"))}
 val deleted=ListRules.remove(base,"l",l)
 ok("Deletion retains all works",deleted.media.size==5&&deleted.suggestions.size==1)
 ok("Deletion removes references from added/hidden/retired/suggested",(deleted.media+deleted.suggestions).all{"l" !in it.listIds})
 ok("Deletion preserves history",deleted.media[1].watchHistory==m.watchHistory&&deleted.media[1].plays==2)
 ok("Deletion preserves private note",deleted.media[1].userNote=="Privé")
 ok("Deletion preserves unrelated memberships",deleted.media[2].listIds==setOf("other"))
 bad("Deleting missing definition fails"){ListRules.remove(deleted,"l")}
 val copy=ListRules.duplicate(base,"l","copy","Copie")
 ok("Duplicate contains same two references",ListRules.members(copy,"copy").map{it.id}==listOf("s","m"))
 ok("Duplicate never clones works",copy.media.map{it.id}==base.media.map{it.id})
 ok("Duplicate ignores suggested and retired memberships",copy.media.last().listIds==gone.listIds&&copy.suggestions==base.suggestions)
 val moved=ListRules.move(base,"l","m",1,listOf("s","m"))
 ok("Personal order spans all added works",ListRules.orderedIds(moved,"l")==listOf("m","s"))
 ok("Moving does not mutate work data",moved.media==base.media)
 ok("Duplicate copies personal order",ListRules.orderedIds(ListRules.duplicate(moved,"l","clone","Clone"),"clone")==listOf("m","s"))
 for(n in listOf(0,-1,3))bad("Invalid position rejected $n"){ListRules.move(base,"l","m",n)}
 bad("Old order cannot overwrite newer move"){ListRules.move(moved,"l","s",1,listOf("s","m"))}
 val p=ListRules.plan(base,"l",listOf("m","g"))
 ok("Preview gives exact adds and removals",p.added==1&&p.removed==1)
 ok("Preview is read-only",base.media[0].listIds==setOf("l")&&base.media[2].listIds==setOf("other"))
 val applied=ListRules.apply(base,p)
 ok("Membership applies selected work references",ListRules.members(applied,"l").map{it.id}==listOf("m","g"))
 ok("Membership retains unrelated list",applied.media[2].listIds==setOf("other","l"))
 val withNote=base.copy(media=base.media.map{if(it.id=="g")it.copy(userNote="Newer") else it})
 ok("Membership preserves newer private note",ListRules.apply(withNote,p).media[2].userNote=="Newer")
 val concurrent=base.copy(media=base.media.map{if(it.id=="b")it.copy(listIds=setOf("l")) else it})
 bad("Concurrent membership change refuses whole preview"){ListRules.apply(concurrent,p)}
 val removed=base.copy(media=base.media.map{if(it.id=="g")it.copy(added=false) else it})
 bad("Selected title removed prevents application"){ListRules.apply(removed,p)}
 bad("Deleted target prevents application"){ListRules.apply(deleted,p)}
 bad("Forged patch refused"){ListRules.apply(base,p.copy(patches=listOf(ListRules.Patch("m",true,false))))}
 bad("Duplicate selections refused"){ListRules.plan(base,"l",listOf("m","m"))}
 for(id in listOf("r","sg","unknown"))bad("Removed or suggested selection refused $id"){ListRules.plan(base,"l",listOf(id))}
 bad("More than 10000 selections refused"){ListRules.plan(base,"l",(0..10000).map{"id$it"})}
 ok("Clear membership keeps empty definition",ListRules.apply(base,ListRules.plan(base,"l",emptyList())).lists.size==2)
 val withNew=ListRules.apply(moved,ListRules.plan(moved,"l",listOf("s","m","g")))
 ok("Newly linked member appended to personal order",ListRules.orderedIds(withNew,"l")==listOf("m","s","g"))
 val stale=moved.copy(lists=moved.lists.map{if(it.id=="l")it.copy(sortOrder=listOf("unknown","m")) else it})
 ok("Stale saved ordering keys create no member",ListRules.orderedIds(stale,"l")==listOf("m","s"))
 ok("Automatic sort does not erase stored manual order",ListRules.edit(moved,ListRules.selected(moved,"l").copy(manualOrder=false)).lists.first().sortOrder==listOf("m","s"))
 bad("Duplicate sort order refused"){ListRules.definition(l.copy(sortOrder=listOf("m","m")))}
 ok("Latest schema accepted",ReleaseRules.schema(10)==10)
 bad("Future schema refused"){ReleaseRules.schema(12)}
 val legacy=l.copy(name="A".repeat(150),description="B".repeat(4000))
 ok("Legacy stored definition preserved without truncation",ListRules.definition(legacy)==legacy)
 val legacyState=base.copy(lists=listOf(legacy))
 bad("Oversized legacy edit cannot silently truncate"){ListRules.edit(legacyState,legacy.copy(name="Courte"))}
 ok("Explicit legacy shortening accepted",ListRules.edit(legacyState,legacy.copy(name="Courte",description="Résumé volontaire")).lists.single().description=="Résumé volontaire")
 val ordered=ListRules.orderedIds(withNew,"l")
 if(args.isNotEmpty())File(args[0]).writeText("{\"order\":["+ordered.joinToString(","){"\"$it\""}+"],\"members\":"+ListRules.members(withNew,"l").size+",\"deletedReferences\":"+(deleted.media+deleted.suggestions).count{"l" in it.listIds}+"}")
 println("PersonalListsTest: $passed assertions passed; pure JVM only")
}
