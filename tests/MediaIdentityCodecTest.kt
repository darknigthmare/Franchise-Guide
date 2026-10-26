import com.franchiseguide.app.data.MediaIdentityCodec
import com.franchiseguide.app.model.*
import org.json.JSONArray
import org.json.JSONObject

// Run with the real org.json parser, not tests/typecheck-stubs/Json.kt.
private var count=0
private fun expect(name:String,condition:Boolean){check(condition){name};count++;println("PASS $name")}
private fun rejectIdentity(name:String,action:()->Any?)=expect(name,runCatching(action).isFailure)

fun main() {
    val original=MediaItem("merged-game",kind=MediaKind.GAME,title="Jeu de test",source="rawg",sourceId="70",favorite=true,userNote="Conserver cette note",addedAt=1700000000000)
    val mergedJson=JSONObject("""{"rawgId":"70","steamAppId":"570","retroGameId":"1234","openLibraryId":null}""")
    val linked=MediaIdentityCodec.readInto(mergedJson,original)
    val emitted=MediaIdentityCodec.writeInto(JSONObject(),linked)
    val roundTrip=MediaIdentityCodec.readInto(JSONObject(emitted.toString()),original)
    expect("Merged game preserves RAWG, Steam and Retro identities through real JSON",roundTrip==linked&&roundTrip.rawgId=="70"&&roundTrip.steamAppId=="570"&&roundTrip.retroGameId=="1234")
    expect("Reading aliases does not replace original source/sourceId",linked.source=="rawg"&&linked.sourceId=="70")
    expect("Reading aliases leaves personal fields intact",linked.favorite&&linked.userNote=="Conserver cette note"&&linked.addedAt==original.addedAt)
    expect("Alias export always writes numeric identities as strings",emitted.get("rawgId") is String&&emitted.get("steamAppId") is String&&emitted.get("retroGameId") is String)
    expect("Absent book identity remains explicit null",emitted.isNull("openLibraryId"))

    val legacy=MediaIdentityCodec.readInto(JSONObject("""{"rawgId":70,"steamAppId":570,"retroGameId":1234}"""),original)
    expect("Exact numeric aliases from older Web backups normalize without changing identities",legacy==linked)
    expect("Exact decimal JSON numbers normalize as integers",MediaIdentityCodec.readInto(JSONObject("""{"rawgId":70.0}"""),original).rawgId=="70")
    expect("Legacy backups with no aliases retain no invented aliases",MediaIdentityCodec.readInto(JSONObject(),original)==original)
    expect("Null aliases remain absent",MediaIdentityCodec.readInto(JSONObject("""{"rawgId":null,"steamAppId":null,"retroGameId":null,"openLibraryId":null}"""),original)==original)
    expect("Source identity alone does not infer another provider alias",MediaIdentityCodec.readInto(JSONObject("""{"source":"rawg","sourceId":"70"}"""),original).steamAppId==null)

    val book=MediaItem("book",kind=MediaKind.BOOK,title="Livre de test",source="openlibrary",sourceId="/works/OL1W",addedAt=1700000000000)
    val linkedBook=MediaIdentityCodec.readInto(JSONObject("""{"openLibraryId":"/works/OL1W"}"""),book)
    expect("Open Library work identity round-trips as a full path",MediaIdentityCodec.readInto(JSONObject(MediaIdentityCodec.writeInto(JSONObject(),linkedBook).toString()),book)==linkedBook)
    expect("Book source/sourceId is unchanged",linkedBook.source==book.source&&linkedBook.sourceId==book.sourceId)
    val destination=JSONObject().put("source",original.source).put("sourceId",original.sourceId).put("unrelated","kept")
    MediaIdentityCodec.writeInto(destination,linked)
    expect("Writing aliases does not rewrite source, sourceId or unrelated JSON fields",destination.getString("source")=="rawg"&&destination.getString("sourceId")=="70"&&destination.getString("unrelated")=="kept")

    val refreshed=MediaIdentityCodec.preserveAliases(linked,original.copy(title="Titre actualisé"))
    expect("Catalogue refresh without aliases retains prior cross-provider identities",refreshed.rawgId==linked.rawgId&&refreshed.steamAppId==linked.steamAppId&&refreshed.retroGameId==linked.retroGameId)
    expect("Preserving aliases does not undo refreshed metadata",refreshed.title=="Titre actualisé"&&refreshed.source==original.source&&refreshed.sourceId==original.sourceId)
    expect("Book refresh retains Open Library identity",MediaIdentityCodec.preserveAliases(linkedBook,book.copy(overview="Résumé actualisé")).openLibraryId=="/works/OL1W")

    expect("Steam uint32 maximum accepted",MediaIdentityCodec.readInto(JSONObject("""{"steamAppId":"4294967295"}"""),original).steamAppId=="4294967295")
    expect("Retro uint32 maximum accepted",MediaIdentityCodec.readInto(JSONObject("""{"retroGameId":4294967295}"""),original).retroGameId=="4294967295")
    expect("RAWG safe integer maximum stays exact",MediaIdentityCodec.readInto(JSONObject("""{"rawgId":9007199254740991}"""),original).rawgId=="9007199254740991")

    for(field in listOf("rawgId","steamAppId","retroGameId")) {
        rejectIdentity("Reject zero $field"){MediaIdentityCodec.readInto(JSONObject().put(field,"0"),original)}
        rejectIdentity("Reject fractional $field"){MediaIdentityCodec.readInto(JSONObject().put(field,1.5),original)}
        rejectIdentity("Reject boolean $field"){MediaIdentityCodec.readInto(JSONObject().put(field,true),original)}
        rejectIdentity("Reject nonnumeric $field"){MediaIdentityCodec.readInto(JSONObject().put(field,"570/../other"),original)}
    }
    rejectIdentity("Reject Steam identity above uint32"){MediaIdentityCodec.readInto(JSONObject("""{"steamAppId":"4294967296"}"""),original)}
    rejectIdentity("Reject Retro identity above uint32"){MediaIdentityCodec.readInto(JSONObject("""{"retroGameId":4294967296}"""),original)}
    rejectIdentity("Reject RAWG identity that cannot round-trip safely in JavaScript"){MediaIdentityCodec.readInto(JSONObject("""{"rawgId":9007199254740992}"""),original)}
    rejectIdentity("Reject huge numeric exponent without expanding it"){MediaIdentityCodec.readInto(JSONObject("""{"rawgId":1e1000000000}"""),original)}
    rejectIdentity("Reject negative numeric identity"){MediaIdentityCodec.readInto(JSONObject("""{"rawgId":-70}"""),original)}
    rejectIdentity("Reject identity array"){MediaIdentityCodec.readInto(JSONObject().put("steamAppId",JSONArray()),original)}
    rejectIdentity("Reject identity object"){MediaIdentityCodec.readInto(JSONObject().put("retroGameId",JSONObject()),original)}
    rejectIdentity("Reject noncanonical identity string"){MediaIdentityCodec.readInto(JSONObject().put("rawgId","070"),original)}
    rejectIdentity("Reject malformed model identity on export"){MediaIdentityCodec.writeInto(JSONObject(),linked.copy(steamAppId="wrong"))}

    for(bad in listOf("OL1W","/books/OL1M","/works/OL0W","/works/OL1W?token=x","/works/../OL1W","https://openlibrary.org/works/OL1W")) {
        rejectIdentity("Reject malformed Open Library work $bad"){MediaIdentityCodec.readInto(JSONObject().put("openLibraryId",bad),book)}
    }
    rejectIdentity("Reject numeric Open Library identity"){MediaIdentityCodec.readInto(JSONObject().put("openLibraryId",1),book)}
    val unknown=MediaIdentityCodec.readInto(JSONObject("""{"rawgId":"70","apiKey":"TEST_ONLY_KEY","session":{"connected":true}}"""),original)
    val clean=MediaIdentityCodec.writeInto(JSONObject(),unknown)
    expect("Aliases do not transport credentials or sessions",!clean.has("apiKey")&&!clean.has("session")&&!clean.toString().contains("TEST_ONLY_KEY"))
    expect("Alias transport does not create Steam account metadata",unknown.steam==null)

    val chosen=SavedBookEdition(work="/works/OL1W",editionId="/books/OL10M",title="Édition",publishDate="2001",publishers=listOf("Maison"),languages=listOf("fre"),format="Poche",pages=220,isbns=listOf("9780140328721"),coverId=123,observedAt=1000,selectedAt=1001)
    val personal=book.copy(selectedEdition=chosen)
    val encoded=MediaIdentityCodec.writeInto(JSONObject(),personal)
    expect("Selected edition survives actual JSON",MediaIdentityCodec.readInto(JSONObject(encoded.toString()),book)==personal)
    expect("Selected edition null pages retained",MediaIdentityCodec.readInto(MediaIdentityCodec.writeInto(JSONObject(),personal.copy(selectedEdition=chosen.copy(pages=null))),book).selectedEdition?.pages==null)
    rejectIdentity("Selected edition cannot move to other work"){MediaIdentityCodec.readInto(encoded,book.copy(sourceId="/works/OL2W"))}
    expect("Selected edition preserved during refresh",MediaIdentityCodec.preserveAliases(personal,book).selectedEdition==chosen)
    println("MediaIdentityCodec: $count assertions passed with real JSON on JVM; no Android runtime or provider session tested.")
}
