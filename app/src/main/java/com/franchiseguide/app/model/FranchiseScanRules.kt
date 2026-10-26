package com.franchiseguide.app.model

/** Runtime-only multi-category scan. No network/storage; commit creates sanitized suggestions atomically. */
object FranchiseScanRules {
    val kinds = MediaKind.entries.toList()
    const val MAX_TERMS = 12
    const val MAX_PAGES_PER_TERM = 25
    const val MAX_RESULTS_PER_KIND = 1000
    const val MAX_SELECTED = 200

    data class Cursor(val kind: MediaKind, val termIndex: Int, val term: String, val page: Int)
    data class Category(val kind: MediaKind,val items: List<MediaItem> = emptyList(),val keys: List<String> = emptyList(),val selected: Set<String> = emptySet(),val termIndex: Int = 0,val page: Int = 0,val nextPage: Int = 1,val more: Boolean = true,val complete: Boolean = false,val error: String? = null,val pagesAccepted: Int = 0,val duplicates: Int = 0,val rejected: Int = 0)
    data class Session(val definition: FranchiseDefinition, val terms: List<String>, val categories: Map<MediaKind, Category>)
    data class Page(val items: List<MediaItem>, val more: Boolean)
    data class Classification(val status: String, val matches: List<MediaItem>, val selectable: Boolean)
    data class Plan(val state: FranchiseRules.State, val inserted: Int, val byKind: Map<MediaKind, Int>, val ids: List<String>)

    fun terms(definition: FranchiseDefinition): List<String> {
        val valid = FranchiseRules.validate(definition); val seen = linkedSetOf<String>(); val out = mutableListOf<String>()
        for (raw in valid.queryTerms + valid.aliases + valid.name) { val value=raw.trim(); val key=FranchiseRules.normal(value); if(key.isBlank()||!seen.add(key))continue; out+=value; if(out.size==MAX_TERMS)break }
        require(out.isNotEmpty()) { "Cette franchise ne possède aucun terme de recherche." }; return out
    }
    fun create(definition: FranchiseDefinition, selected: Set<MediaKind> = kinds.toSet()): Session { require(selected.isNotEmpty()){ "Sélectionne au moins une catégorie." }; val valid=FranchiseRules.validate(definition); return Session(valid,terms(valid),selected.associateWith{Category(it)}) }
    fun current(session:Session,kind:MediaKind):Cursor { val c=session.categories[kind]?:error("Catégorie non activée.");return Cursor(kind,c.termIndex,session.terms.getOrNull(c.termIndex).orEmpty(),c.nextPage) }
    fun belongs(session:Session,media:MediaItem):Boolean { val title=" ${FranchiseRules.normal(media.title)} ";return session.terms.any{val term=FranchiseRules.normal(it);term.isNotBlank()&&title.contains(" $term ")} }
    private fun sanitized(media:MediaItem,kind:MediaKind):MediaItem { require(media.kind==kind&&media.id.isNotBlank()&&media.title.isNotBlank()&&!media.sourceId.isNullOrBlank()){ "Résultat catalogue invalide." };return media.copy(added=false,isSuggestion=false,watched=false,plays=0,watchHistory=emptyList(),lastWatchedAt=null,rating=null,userNote=null,listIds=emptySet(),favorite=false,hidden=false,collected=false,watchlist=false,notify=false,started=false,progressCurrent=0,progressTotal=0,progressUnit="",trackingStatus=null,playtimeMinutes=null,personalPlatform=null,personalFormat=null,selectedEdition=null,steam=null,catalogComplete=false,catalogUpdatedAt=null) }
    fun key(media:MediaItem)=FranchiseRules.identity(media)
    fun accept(session:Session,kind:MediaKind,cursor:Cursor,page:Page):Session {
        val category=session.categories[kind]?:error("Catégorie non activée.");require(!category.complete&&cursor==current(session,kind)){ "Réponse de scanner obsolète." };require(page.items.size<=500){ "Page de scanner trop volumineuse." }
        val pageKeys=linkedSetOf<String>();val known=category.keys.toMutableSet();val additions=mutableListOf<MediaItem>();var duplicates=0;var rejected=0
        for(raw in page.items){val item=sanitized(raw,kind);if(!belongs(session,item)){rejected++;continue};val id=key(item);require(pageKeys.add(id)){ "Identité fournisseur répétée dans la page." };if(!known.add(id))duplicates++ else additions+=item}
        require(category.items.size+additions.size<=MAX_RESULTS_PER_KIND){ "Limite de 1 000 références atteinte pour cette catégorie." }
        var next=category.copy(items=category.items+additions,keys=known.toList(),page=cursor.page,pagesAccepted=category.pagesAccepted+1,duplicates=category.duplicates+duplicates,rejected=category.rejected+rejected,error=null)
        if(page.more&&cursor.page<MAX_PAGES_PER_TERM&&next.items.size<MAX_RESULTS_PER_KIND)next=next.copy(nextPage=cursor.page+1) else {val term=next.termIndex+1;val done=term>=session.terms.size||next.items.size>=MAX_RESULTS_PER_KIND;next=next.copy(termIndex=term,page=0,nextPage=1,more=!done,complete=done)}
        return session.copy(categories=session.categories+(kind to next))
    }
    fun failed(session:Session,kind:MediaKind,cursor:Cursor,message:String):Session { require(cursor==current(session,kind)){ "Échec pour une page obsolète." };val c=session.categories[kind]?:error("Catégorie retirée.");return session.copy(categories=session.categories+(kind to c.copy(error=message.take(800)))) }
    private fun aliases(media:MediaItem):Set<String> = buildSet { add("local:${media.id}");media.tmdbId?.let{add("tmdb:$it")};media.tvdbId?.let{add("tvdb:$it")};media.imdbId?.takeIf{it.isNotBlank()}?.let{add("imdb:$it")};media.sourceId?.takeIf{it.isNotBlank()}?.let{add("${media.source}:$it")} }.map{"${media.kind}:$it"}.toSet()
    private fun shared(a:MediaItem,b:MediaItem)=a.kind==b.kind&&aliases(a).any{it in aliases(b)}
    private fun conflict(a:MediaItem,b:MediaItem):Boolean { if(a.kind!=b.kind)return false;if(a.id==b.id&&(a.source!=b.source||a.sourceId!=b.sourceId))return true;return shared(a,b)&&ReliabilityRules.mediaConflict(a,b) }
    fun classify(media:MediaItem,state:FranchiseRules.State):Classification { val all=state.media+state.suggestions;val conflicts=all.filter{conflict(it,media)};val exact=all.filter{shared(it,media)&&!conflict(it,media)};if(conflicts.isNotEmpty()||exact.size>1)return Classification("ambiguous",(exact+conflicts).distinctBy{it.id},false);if(exact.size==1){val item=exact.single();return when{item.isSuggestion||item in state.suggestions->Classification("suggested",exact,false);item.added->Classification("added",exact,false);else->Classification("withdrawn",exact,false)}};return Classification("new",emptyList(),true) }
    fun toggle(session:Session,kind:MediaKind,id:String,state:FranchiseRules.State,checked:Boolean):Session { val c=session.categories[kind]?:error("Catégorie retirée.");val item=c.items.firstOrNull{key(it)==id}?:error("Résultat retiré.");require(classify(item,state).selectable){ "Cette identité est déjà connue ou ambiguë." };val selected=c.selected.toMutableSet();if(checked)selected+=id else selected-=id;val total=session.categories.values.sumOf{if(it.kind==kind)selected.size else it.selected.size};require(total<=MAX_SELECTED){ "Sélection limitée à 200 suggestions." };return session.copy(categories=session.categories+(kind to c.copy(selected=selected))) }
    fun selectAvailable(session:Session,kind:MediaKind,state:FranchiseRules.State,checked:Boolean=true):Session { val c=session.categories[kind]?:error("Catégorie retirée.");val selected=c.selected.toMutableSet();c.items.forEach{if(classify(it,state).selectable){if(checked)selected+=key(it) else selected-=key(it)}};val total=session.categories.values.sumOf{if(it.kind==kind)selected.size else it.selected.size};require(total<=MAX_SELECTED){ "Sélection limitée à 200 suggestions." };return session.copy(categories=session.categories+(kind to c.copy(selected=selected))) }
    fun plan(session:Session,state:FranchiseRules.State,builtIns:List<FranchiseDefinition>,current:FranchiseDefinition):Plan { require(current==session.definition){ "La définition a changé. Relance le scanner." };val chosen=session.categories.values.flatMap{c->c.items.filter{key(it) in c.selected}};require(chosen.isNotEmpty()){ "Sélectionne au moins une référence." };require(chosen.size<=MAX_SELECTED&&chosen.map(::key).distinct().size==chosen.size){ "Sélection invalide ou trop grande." };chosen.forEach{item->require(belongs(session,item)){ "Résultat hors termes de la franchise." };when(classify(item,state).status){"ambiguous"->error("Correspondance ambiguë : aucun élément du lot enregistré.");"new"->Unit;else->error("Une référence est déjà connue : aucun élément du lot enregistré.")}};var next=state;val counts=MediaKind.entries.associateWith{0}.toMutableMap();chosen.forEach{next=FranchiseRules.suggest(next,builtIns,session.definition.id,it);counts[it.kind]=counts.getValue(it.kind)+1};return Plan(next,chosen.size,counts,chosen.map(::key)) }
}
