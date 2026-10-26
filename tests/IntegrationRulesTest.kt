import com.franchiseguide.app.model.*
private var passed=0
private fun verify(name:String,value:Boolean){check(value){name};passed++;println("PASS $name")}
fun main(){
    verify("Watched shows request includes specials",IntegrationRules.traktLibraryPath(MediaKind.SHOW,"watched",2)=="/sync/watched/shows?page=2&limit=25&specials=true")
    verify("Collection movies path stays read-only",IntegrationRules.traktLibraryPath(MediaKind.MOVIE,"collection",1)=="/sync/collection/movies?page=1&limit=25")
    verify("Watchlist pagination includes requested limit",IntegrationRules.traktLibraryPath(MediaKind.SHOW,"watchlist",4,50).contains("page=4&limit=50"))
    verify("Unsupported game kind is rejected",runCatching{IntegrationRules.traktLibraryPath(MediaKind.GAME,"watched",1)}.isFailure)
    verify("Unsupported book kind is rejected",runCatching{IntegrationRules.traktLibraryPath(MediaKind.BOOK,"watched",1)}.isFailure)
    verify("Arbitrary provider path is rejected",runCatching{IntegrationRules.traktLibraryPath(MediaKind.MOVIE,"../../users",1)}.isFailure)
    verify("Page zero is rejected",runCatching{IntegrationRules.traktLibraryPath(MediaKind.MOVIE,"watched",0)}.isFailure)
    verify("Oversized page is rejected",runCatching{IntegrationRules.traktLibraryPath(MediaKind.MOVIE,"watched",501)}.isFailure)
    verify("Oversized page size is rejected",runCatching{IntegrationRules.traktLibraryPath(MediaKind.MOVIE,"watched",1,101)}.isFailure)
    verify("Rejected request is not an uncertain server acknowledgement",IntegrationRules.definitiveHistoryRejection(400)&&IntegrationRules.definitiveHistoryRejection(422))
    verify("Server errors preserve non-idempotent uncertainty",!IntegrationRules.definitiveHistoryRejection(500)&&!IntegrationRules.definitiveHistoryRejection(502)&&!IntegrationRules.definitiveHistoryRejection(504))
    verify("Missing response preserves uncertainty",!IntegrationRules.definitiveHistoryRejection(0))
    val op=PendingChange("op","movie",333,"Test","history",true,watchedAt=100)
    verify("Legacy pending changes are not implicitly suspended",op.heldReason==null)
    val held=op.copy(heldReason="Import")
    verify("Suspension preserves exact operation identity and date",held.id==op.id&&held.path==op.path&&held.watchedAt==op.watchedAt)
    verify("Resuming a suspended uncertain action does not clear uncertainty",held.copy(uncertain=true).copy(heldReason=null).uncertain)
    println("$passed V0.9 integration-rule assertions passed; no Android device or live HTTP used")
}
