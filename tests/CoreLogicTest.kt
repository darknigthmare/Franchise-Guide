import com.franchiseguide.app.franchise.FranchiseEngine
import com.franchiseguide.app.model.*

fun main() {
    val alien = FranchiseEngine.builtIns.first { it.id == "alien" }
    val media = listOf(
        MediaItem("m", 348, MediaKind.MOVIE, "Alien", added = true),
        MediaItem("s", 157239, MediaKind.SHOW, "Alien: Earth", added = true),
        MediaItem("g", kind = MediaKind.GAME, title = "Alien: Isolation", source="rawg", sourceId="1", added = true),
        MediaItem("b", kind = MediaKind.BOOK, title = "Alien: Out of the Shadows", source="openlibrary", sourceId="/works/x", added = true)
    )
    val suggestions = listOf(MediaItem("x", 126889, MediaKind.MOVIE, "Alien: Covenant", added = false, isSuggestion = true))
    val group = FranchiseEngine.groups(media, suggestions, listOf(alien)).single()
    check(group.addedCount == 4) { "addedCount=${group.addedCount}" }
    check(group.totalCount == 5) { "totalCount=${group.totalCount}" }
    check(group.missingCount == 1) { "missingCount=${group.missingCount}" }
    check(group.kinds == setOf(MediaKind.SHOW, MediaKind.MOVIE, MediaKind.GAME, MediaKind.BOOK))
    check(FranchiseEngine.belongsTo(alien, MediaItem("z", kind=MediaKind.GAME, title="Aliens: Dark Descent")))
    check(!FranchiseEngine.belongsTo(alien, MediaItem("z2", kind=MediaKind.MOVIE, title="The Alienist")))
    check(FranchiseEngine.stableKey(media[0]).contains("tmdb"))
    println("OK core franchise logic: 4 media kinds, suggestion accounting, strict matching, stable keys")
}
