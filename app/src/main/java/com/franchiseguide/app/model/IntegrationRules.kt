package com.franchiseguide.app.model

/** Provider paths are selected from a closed set, never from user-supplied URLs. */
object IntegrationRules {
    fun traktLibraryPath(kind:MediaKind,section:String,page:Int,pageSize:Int=25):String {
        require(kind==MediaKind.SHOW||kind==MediaKind.MOVIE) { "Trakt ne gère ici que séries et films." }
        require(section in setOf("watched","watchlist","collection")) { "Section Trakt inconnue." }
        require(page in 1..500 && pageSize in 1..100) { "Pagination hors limites." }
        val plural=if(kind==MediaKind.SHOW) "shows" else "movies"
        return "/sync/$section/$plural?page=$page&limit=$pageSize" + if(section=="watched" && kind==MediaKind.SHOW) "&specials=true" else ""
    }
    /** A server error can occur after processing a non-idempotent POST. Keep it uncertain. */
    fun definitiveHistoryRejection(status:Int):Boolean = status in setOf(400,401,403,404,409,410,412,413,415,422,423,426,429)
}
