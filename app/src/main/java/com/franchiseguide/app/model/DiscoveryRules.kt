package com.franchiseguide.app.model

/** The result of a fetch is never a command to mutate the user's local library. */
object DiscoveryRules {
    const val MAX_PAGES = 100
    const val MAX_REFERENCES = 5000
    data class Query(val kind:MediaKind, val min:Int=0,val max:Int=0,val language:String="") {
        init {
            require(min==0||min in 1800..2200) { "Année min hors limites." }
            require(max==0||max in 1800..2200) { "Année max hors limites." }
            require(min==0||max==0||min<=max) { "Année min supérieure à l'année max." }
            require(language.isBlank()||language.matches(Regex("[a-zA-Z]{2,3}"))) { "Langue originale invalide." }
        }
    }
    data class Page(val items:List<MediaItem>,val page:Int,val next:Int,val reachedLimit:Boolean)
    fun key(m:MediaItem):String {
        require(m.source?.isNotBlank()==true && m.sourceId?.isNotBlank()==true) { "Référence catalogue absente." }
        return "${m.kind}:${m.source!!.lowercase()}:${m.sourceId}"
    }
    fun accept(before:List<MediaItem>,added:List<MediaItem>,page:Int):Page {
        require(page in 1..MAX_PAGES) { "Page de catalogue hors limite." }
        require(added.size<=500) { "Réponse catalogue trop volumineuse." }
        val indexed=LinkedHashMap<String,MediaItem>()
        before.forEach { indexed[key(it)] = it }
        added.forEach { indexed.putIfAbsent(key(it),it) }
        require(indexed.size<=MAX_REFERENCES) { "Limite de références atteinte." }
        val limit=indexed.size==MAX_REFERENCES||page==MAX_PAGES
        return Page(indexed.values.toList(),page,page+1,limit)
    }
    fun filtered(items:List<MediaItem>,q:Query):List<MediaItem> {
        val languages=mapOf("English" to "en","French" to "fr","German" to "de","Spanish" to "es","Japanese" to "ja","Portuguese" to "pt","Italian" to "it","Russian" to "ru","Korean" to "ko","Chinese" to "zh")
        return items.filter { m ->
            (q.min==0||m.year!=null&&m.year>=q.min) &&
            (q.max==0||m.year!=null&&m.year<=q.max) &&
            (q.language.isBlank()||m.originalLanguage.isNullOrBlank()||
             (languages[m.originalLanguage]?:m.originalLanguage).equals(q.language,ignoreCase=true))
        }
    }
}
