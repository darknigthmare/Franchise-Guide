import com.franchiseguide.app.model.*
import com.franchiseguide.app.franchise.FranchiseEngine
import java.io.File

fun main(args:Array<String>) {
 var checks=0
 fun ok(name:String,value:Boolean){check(value){name};checks++;println("PASS $name")}
 fun reject(name:String,action:()->Unit){ok(name,runCatching(action).isFailure)}
 val built=FranchiseEngine.builtIns
 fun m(id:String,kind:MediaKind=MediaKind.MOVIE)=MediaItem(id,kind=kind,title="Film $id",added=true)
 fun d(id:String="mine",name:String="Univers QA")=FranchiseDefinition(id,name,listOf(name),custom=true)
 fun s()=FranchiseRules.State(emptyList(),emptyList(),listOf(d()))
 ok("Thirty two builtin references",built.size==32)
 ok("Empty user universe remains visible",FranchiseRules.groups(s(),built).single().totalCount==0)
 val none=m("a").copy(title="Alien",franchiseManual=true)
 ok("Manual none bypasses inference",FranchiseRules.effective(none,built)==null)
 ok("Legacy engine respects explicit none",!FranchiseEngine.belongsTo(built.first{it.id=="alien"},none))
 val override=m("a").copy(title="Alien vs Predator",franchiseId="mine",franchiseManual=true)
 ok("Manual group is primary",FranchiseRules.groups(s().copy(media=listOf(override)),built).filter{it.items.isNotEmpty()}.map{it.definition.id}==listOf("mine"))
 ok("Explicit group has priority even without manual flag",FranchiseRules.effective(override.copy(franchiseManual=false,franchiseId="predator"),built)=="predator")
 ok("Strict word matching excludes Alienist",FranchiseRules.effective(m("a").copy(title="The Alienist"),built)==null)
 ok("Title matches known term",FranchiseRules.effective(m("a").copy(title="Alien: Isolation"),built)=="alien")
 ok("Publisher alone is not a franchise",FranchiseRules.effective(m("a").copy(publisher="Alien",creator="Marvel"),built)==null)
 val missing=s().copy(media=listOf(m("a").copy(franchiseId="lost",franchiseName="Perdu")))
 ok("Missing definition preserved as recovered reference",FranchiseRules.definitions(missing,built).any{it.id=="lost"&&it.name=="Perdu"})
 ok("Removed media excluded from group counts",FranchiseRules.groups(s().copy(media=listOf(m("a").copy(franchiseId="mine",added=false))),built).single().totalCount==0)
 ok("Hidden active media stays associated",FranchiseRules.groups(s().copy(media=listOf(m("a").copy(franchiseId="mine",hidden=true))),built).single().addedCount==1)
 val duplicate=s().copy(media=listOf(m("a").copy(tmdbId=15,franchiseId="mine")),suggestions=listOf(m("b").copy(tmdbId=15,franchiseId="mine",added=false,isSuggestion=true)))
 ok("Duplicate suggested provider identity counted once",FranchiseRules.groups(duplicate,built).single().totalCount==1)
 val editions=s().copy(media=listOf(m("a").copy(title="Même titre",year=2000,franchiseId="mine"),m("b").copy(title="Même titre",year=2000,franchiseId="mine")))
 ok("Same title same year separate local IDs stay distinct",FranchiseRules.groups(editions,built).single().totalCount==2)
 val four=s().copy(media=MediaKind.entries.map{m(it.name,it).copy(tmdbId=1,franchiseId="mine")})
 ok("Same provider number across media kinds not collapsed",FranchiseRules.groups(four,built).single().addedCount==4)
 val staged=FranchiseRules.put(FranchiseRules.State(emptyList(),emptyList(),emptyList()),built,d())
 ok("Create stages custom definition",staged.custom.single().id=="mine")
 reject("Normalized duplicate name rejected"){FranchiseRules.put(staged,built,d("other","UNIVÉRS QA"))}
 reject("Builtin ID cannot be overwritten"){FranchiseRules.put(s(),built,d("alien"))}
 reject("Builtin name cannot be copied"){FranchiseRules.put(s(),built,d("user","Alien"))}
 reject("Editing removed ID rejected"){FranchiseRules.put(s(),built,d("removed"),true)}
 val old=m("a").copy(franchiseId="mine",franchiseName="Univers QA",watched=true,plays=3,watchHistory=listOf(1000,2000,3000),userNote="Privé",rating=9,listIds=setOf("x"))
 val history=s().copy(media=listOf(old),suggestions=listOf(m("s").copy(franchiseId="mine",added=false,isSuggestion=true)),custom=listOf(d().copy(sortOrder=listOf("s","a"))))
 val renamed=FranchiseRules.put(history,built,d(name="Renommé"),true)
 ok("Rename changes label not identity",renamed.media.single().franchiseName=="Renommé"&&renamed.media.single().id=="a")
 ok("Rename retains personal notes/history",renamed.media.single().watchHistory==old.watchHistory&&renamed.media.single().userNote==old.userNote)
 ok("Rename retains sortOrder",renamed.custom.single().sortOrder==listOf("s","a"))
 ok("Rename updates suggested reference label",renamed.suggestions.single().franchiseName=="Renommé")
 val deleted=FranchiseRules.remove(history,built,"mine")
 ok("Delete detaches existing media and marks manual none",deleted.media.single().franchiseId==null&&deleted.media.single().franchiseManual)
 ok("Delete keeps tracking and notes",deleted.media.single().watchHistory==old.watchHistory&&deleted.media.single().rating==9&&deleted.media.single().listIds==setOf("x"))
 ok("Delete keeps suggested work",deleted.suggestions.size==1&&deleted.suggestions.single().franchiseId==null)
 ok("Delete removes only definition",deleted.custom.isEmpty()&&deleted.media.size==1)
 reject("Cannot delete integrated definition"){FranchiseRules.remove(s(),built,"alien")}
 val cleared=FranchiseRules.assign(history,built,"a",null)
 ok("Assign none is durable field not an inference toggle",cleared.media.single().franchiseManual&&cleared.media.single().franchiseId==null)
 val reset=FranchiseRules.assign(s().copy(media=listOf(none)),built,"a",null,true)
 ok("Explicitly reenable inference",reset.media.single().franchiseId=="alien"&&!reset.media.single().franchiseManual)
 reject("Unknown chosen group rejected"){FranchiseRules.assign(history,built,"a","deleted")}
 reject("Missing item rejected"){FranchiseRules.assign(history,built,"gone","mine")}
 val arranged=s().copy(media=listOf(m("a").copy(franchiseId="mine",year=2000),m("b").copy(franchiseId="mine",year=2001)),suggestions=listOf(m("c").copy(franchiseId="mine",added=false,isSuggestion=true,year=2020)))
 val moved=FranchiseRules.move(arranged,built,"mine","c",1)
 ok("Move uses all refs including suggestions",FranchiseRules.groups(moved,built).single().items.map{it.id}==listOf("c","a","b"))
 ok("Personal order stored as exact local IDs",moved.custom.single().sortOrder==listOf("c","a","b"))
 reject("Out of range order rejected"){FranchiseRules.move(arranged,built,"mine","a",0)}
 reject("Removed order target rejected"){FranchiseRules.move(arranged,built,"mine","gone",1)}
 ok("Year order leaves unknown years at end",FranchiseRules.sort(listOf(m("u"),m("k").copy(year=1900)),d(),"year").first().id=="k")
 val suggested=FranchiseRules.suggest(s(),built,"mine",old.copy(franchiseId=null))
 ok("Retained candidate is not an added media",suggested.media.isEmpty()&&suggested.suggestions.single().isSuggestion&&!suggested.suggestions.single().added)
 ok("Retained candidate clears tracked fields",suggested.suggestions.single().watchHistory.isEmpty()&&suggested.suggestions.single().rating==null&&suggested.suggestions.single().userNote==null)
 ok("Retained candidate is manually assigned",suggested.suggestions.single().franchiseManual&&suggested.suggestions.single().franchiseId=="mine")
 reject("Cannot duplicate owned item into suggestions"){FranchiseRules.suggest(history,built,"mine",old)}
 reject("Cannot duplicate existing suggestion"){FranchiseRules.suggest(suggested,built,"mine",old)}
 reject("Empty name rejected"){FranchiseRules.validate(d(name=""))}
 reject("Control characters rejected"){FranchiseRules.validate(d(name="Bad\nname"))}
 reject("Oversized name rejected"){FranchiseRules.validate(d(name="a".repeat(201)))}
 reject("Duplicate sort reference rejected"){FranchiseRules.validate(d().copy(sortOrder=listOf("a","a")))}
 reject("Too many aliases rejected"){FranchiseRules.validate(d().copy(aliases=(0..100).map{"a$it"}))}
 val recovered=FranchiseRules.put(missing,built,d("lost","Univers récupéré"),true)
 ok("Recovered id restored as explicit custom definition",recovered.custom.any{it.id=="lost"})
 val html=FranchiseRules.validate(d("x\" onclick=\"x","<img src=x>"))
 ok("HTML-looking plain data preserved",html.name=="<img src=x>")
 val inferred=s().copy(media=listOf(m("implicit").copy(title="Univers QA - Tome I")))
 ok("Removing inferred personal group explicitly detaches",FranchiseRules.remove(inferred,built,"mine").media.single().franchiseManual)
 val emoji1=FranchiseRules.put(s(),built,d("emoji1","😀"));val emoji2=FranchiseRules.put(emoji1,built,d("emoji2","👾"))
 ok("Distinct emoji names remain separate",emoji2.custom.size==3)
 reject("Duplicate emoji name rejected"){FranchiseRules.put(emoji2,built,d("emoji3","😀"))}
 ok("Unicode letters retained in alias matching",FranchiseRules.matches(d("ja","宇宙"),m("ja").copy(title="宇宙 - 第一巻")))
 ok("Multiline description accepted",FranchiseRules.validate(d().copy(description="Première ligne\nDeuxième ligne")).description.contains('\n'))
 reject("NUL in description refused"){FranchiseRules.validate(d().copy(description="Texte\u0000nul"))}
 val count=FranchiseRules.groups(moved,built).single()
 args.firstOrNull()?.let{File(it).writeText("${count.addedCount}|${count.totalCount}|${count.missingCount}|"+count.items.joinToString(","){it.id})}
 println("FranchiseRulesTest: $checks assertions passed on JVM, not an Android runtime.")
}
