package com.franchiseguide.app.franchise

import com.franchiseguide.app.model.FranchiseDefinition
import com.franchiseguide.app.model.FranchiseGroup
import com.franchiseguide.app.model.MediaItem
import com.franchiseguide.app.model.FranchiseRules
import java.text.Normalizer
import java.util.Locale

object FranchiseEngine {
    val builtIns = listOf(
        FranchiseDefinition("alien", "Alien", listOf("Alien", "Aliens"), listOf("Prometheus", "Covenant", "Xenomorph", "AVP"), "Films, séries, jeux et livres de l'univers Alien."),
        FranchiseDefinition("predator", "Predator", listOf("Predator", "Prey"), listOf("Yautja", "AVP", "Alien vs Predator"), "Films, jeux, livres et extensions de l'univers Predator."),
        FranchiseDefinition("stargate", "Stargate", listOf("Stargate"), listOf("SG-1", "Atlantis", "Universe"), "Films, séries, jeux et romans Stargate."),
        FranchiseDefinition("star-trek", "Star Trek", listOf("Star Trek"), listOf("Deep Space Nine", "Voyager", "Picard", "Enterprise"), "Séries, films, jeux et romans Star Trek."),
        FranchiseDefinition("star-wars", "Star Wars", listOf("Star Wars"), listOf("Mandalorian", "Andor", "Ahsoka", "Clone Wars", "Jedi"), "Films, séries, jeux et livres Star Wars."),
        FranchiseDefinition("marvel", "Marvel", listOf("Marvel"), listOf("Avengers", "Daredevil", "Loki", "Spider-Man", "X-Men", "Guardians of the Galaxy"), "Films, séries, jeux et livres Marvel."),
        FranchiseDefinition("dc", "DC", listOf("DC Comics"), listOf("Batman", "Superman", "Wonder Woman", "Justice League", "Aquaman"), "Films, séries, jeux et livres DC."),
        FranchiseDefinition("harry-potter", "Wizarding World", listOf("Harry Potter"), listOf("Fantastic Beasts", "Hogwarts", "Wizarding World"), "Films, séries, jeux et livres du Wizarding World."),
        FranchiseDefinition("middle-earth", "Terre du Milieu", listOf("Lord of the Rings", "The Hobbit"), listOf("Middle-earth", "Rings of Power", "Tolkien"), "Films, séries, jeux et livres liés à la Terre du Milieu."),
        FranchiseDefinition("witcher", "The Witcher", listOf("The Witcher"), listOf("Wiedźmin", "Geralt"), "Séries, films, jeux et livres The Witcher."),
        FranchiseDefinition("resident-evil", "Resident Evil", listOf("Resident Evil"), listOf("Biohazard"), "Jeux, films, séries et livres Resident Evil."),
        FranchiseDefinition("mass-effect", "Mass Effect", listOf("Mass Effect"), listOf("Andromeda"), "Jeux, romans, comics et adaptations Mass Effect."),
        FranchiseDefinition("dune", "Dune", listOf("Dune"), listOf("Arrakis", "House Atreides"), "Films, séries, jeux et romans de l'univers Dune."),
        FranchiseDefinition("terminator", "Terminator", listOf("Terminator"), listOf("Skynet", "Sarah Connor"), "Films, séries, jeux et romans Terminator."),
        FranchiseDefinition("matrix", "Matrix", listOf("The Matrix"), listOf("Matrix Reloaded", "Matrix Revolutions", "Matrix Resurrections"), "Films, jeux et œuvres de l'univers Matrix."),
        FranchiseDefinition("jurassic", "Jurassic", listOf("Jurassic Park", "Jurassic World"), listOf("Jurassic"), "Films, séries, jeux et livres Jurassic."),
        FranchiseDefinition("monsterverse", "MonsterVerse", listOf("Godzilla", "Kong"), listOf("Monarch", "MonsterVerse"), "Films, séries, jeux et livres liés au MonsterVerse."),
        FranchiseDefinition("transformers", "Transformers", listOf("Transformers"), listOf("Autobot", "Decepticon", "Bumblebee"), "Films, séries, jeux et livres Transformers."),
        FranchiseDefinition("james-bond", "James Bond", listOf("James Bond"), listOf("007", "Bond"), "Films, jeux et romans James Bond."),
        FranchiseDefinition("doctor-who", "Doctor Who", listOf("Doctor Who"), listOf("Torchwood", "Dalek"), "Séries, films, jeux et livres Doctor Who."),
        FranchiseDefinition("fallout", "Fallout", listOf("Fallout"), listOf("Vault-Tec", "Vault Boy"), "Jeux, série et livres de l'univers Fallout."),
        FranchiseDefinition("last-of-us", "The Last of Us", listOf("The Last of Us"), listOf("Last of Us"), "Jeux, série et adaptations The Last of Us."),
        FranchiseDefinition("assassins-creed", "Assassin's Creed", listOf("Assassin's Creed"), listOf("Assassins Creed", "Animus"), "Jeux, films, séries et romans Assassin's Creed."),
        FranchiseDefinition("halo", "Halo", listOf("Halo"), listOf("Master Chief", "Covenant"), "Jeux, série, films et romans Halo."),
        FranchiseDefinition("warcraft", "Warcraft", listOf("Warcraft"), listOf("World of Warcraft", "Azeroth"), "Jeux, film et romans Warcraft."),
        FranchiseDefinition("silent-hill", "Silent Hill", listOf("Silent Hill"), emptyList(), "Jeux, films et œuvres Silent Hill."),
        FranchiseDefinition("metal-gear", "Metal Gear", listOf("Metal Gear"), listOf("Solid Snake", "Big Boss"), "Jeux et œuvres Metal Gear."),
        FranchiseDefinition("half-life", "Half-Life", listOf("Half-Life"), listOf("Half Life", "Black Mesa", "Alyx"), "Jeux et œuvres de l'univers Half-Life."),
        FranchiseDefinition("pokemon", "Pokémon", listOf("Pokémon"), listOf("Pokemon", "Pikachu"), "Jeux, séries, films, mangas et livres Pokémon."),
        FranchiseDefinition("dragon-ball", "Dragon Ball", listOf("Dragon Ball"), listOf("DBZ", "Dragon Ball Z", "Dragon Ball Super"), "Séries, films, jeux et mangas Dragon Ball."),
        FranchiseDefinition("naruto", "Naruto", listOf("Naruto"), listOf("Boruto", "Shippuden"), "Séries, films, jeux et mangas Naruto."),
        FranchiseDefinition("final-fantasy", "Final Fantasy", listOf("Final Fantasy"), listOf("FFVII", "FF7"), "Jeux, films et livres Final Fantasy.")
    )

    fun groups(
        library: List<MediaItem>,
        suggestions: List<MediaItem>,
        definitions: List<FranchiseDefinition> = builtIns
    ): List<FranchiseGroup> {
        return FranchiseRules.groups(FranchiseRules.State(library,suggestions,emptyList()),definitions)
    }

    fun belongsTo(definition:FranchiseDefinition,item:MediaItem):Boolean {
        if(item.franchiseManual||item.franchiseId!=null)return item.franchiseId==definition.id
        return FranchiseRules.matches(definition,item)
    }

    fun inferFranchise(item:MediaItem,definitions:List<FranchiseDefinition> = builtIns):FranchiseDefinition? {
        val id=FranchiseRules.effective(item,definitions);return definitions.firstOrNull{it.id==id}
    }

    fun stableKey(item: MediaItem): String = when {
        item.tmdbId != null -> "tmdb:${item.kind}:${item.tmdbId}"
        item.sourceId != null -> "${item.source}:${item.kind}:${item.sourceId}"
        item.imdbId != null -> "imdb:${item.kind}:${item.imdbId}"
        item.tvdbId != null -> "tvdb:${item.kind}:${item.tvdbId}"
        else -> "local:${item.kind}:${normalize(item.title)}:${item.year ?: 0}"
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .replace("[^a-z0-9]+".toRegex(), " ")
        .trim()
}
