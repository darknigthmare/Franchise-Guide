package com.franchiseguide.app.model

import java.text.Normalizer
import java.util.Locale

/** Local primary classification. Library state is staged before a storage commit. No provider writes. */
object FranchiseRules {
    data class State(val media:List<MediaItem>,val suggestions:List<MediaItem>,val custom:List<FranchiseDefinition>)
    fun normal(s:String):String=Normalizer.normalize(s,Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"),"").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()
    private fun nameKey(s:String)=normal(s).ifBlank{Normalizer.normalize(s,Normalizer.Form.NFC).trim().lowercase(Locale.ROOT)}
    private fun clean(s:String,label:String,max:Int,empty:Boolean=false):String {
        require(s.length<=max && (empty||s.isNotBlank()) && s.none{it.code in 0..31 || it.code==127}) { "$label invalide." };return s.trim()
    }
    fun validate(d:FranchiseDefinition):FranchiseDefinition {
        fun strings(xs:List<String>,name:String,max:Int,limit:Int):List<String> {require(xs.size<=limit){"$name trop volumineux."};return xs.map{clean(it,name,max)}.distinct()}
        require(d.sortOrder.distinct().size==d.sortOrder.size){"Un identifiant apparaît deux fois dans l’ordre personnel."}
        require(d.description.length<=4000&&d.description.none { it.code in 0..8 || it.code==11 || it.code==12 || it.code in 14..31 || it.code==127 }) { "Description invalide." }
        return d.copy(id=clean(d.id,"Identifiant",250),name=clean(d.name,"Nom",200),description=d.description.trim(),queryTerms=strings(d.queryTerms,"Recherche",160,100),aliases=strings(d.aliases,"Alias",160,100),sortOrder=strings(d.sortOrder,"Ordre",250,50000))
    }
    fun definitions(s:State,builtIns:List<FranchiseDefinition>):List<FranchiseDefinition> {
        val out=linkedMapOf<String,FranchiseDefinition>();builtIns.forEach{out[it.id]=it}
        s.custom.forEach { d-> val valid=validate(d);out.putIfAbsent(valid.id,valid) }
        (s.media+s.suggestions).forEach { m->m.franchiseId?.takeIf{it.isNotBlank()}?.let{id->out.putIfAbsent(id,FranchiseDefinition(id,m.franchiseName?:id,emptyList(),description="Référence importée sans définition enregistrée.",custom=true))} }
        return out.values.toList()
    }
    fun matches(d:FranchiseDefinition,m:MediaItem):Boolean {
        val padded=" ${normal(m.title)} ";return (d.queryTerms+d.aliases).any { val t=normal(it);t.isNotEmpty()&&padded.contains(" $t ") }
    }
    fun effective(m:MediaItem,defs:List<FranchiseDefinition>):String? {
        if(m.franchiseManual || m.franchiseId!=null)return m.franchiseId
        return defs.firstOrNull { matches(it,m) }?.id
    }
    fun identity(m:MediaItem):String=when {
        m.tmdbId!=null->"${m.kind}:tmdb:${m.tmdbId}"
        !m.sourceId.isNullOrBlank()->"${m.kind}:${m.source}:${m.sourceId}"
        m.imdbId!=null->"${m.kind}:imdb:${m.imdbId}"
        m.tvdbId!=null->"${m.kind}:tvdb:${m.tvdbId}"
        else->"${m.kind}:local:${m.id}"
    }
    fun sort(items:List<MediaItem>,definition:FranchiseDefinition,mode:String="personal"):List<MediaItem> {
        require(mode in setOf("personal","year","title","kind"));val ranks=definition.sortOrder.withIndex().associate { it.value to it.index }
        return items.sortedWith(Comparator { a,b ->
            var c=if(mode=="personal")(ranks[a.id]?:Int.MAX_VALUE).compareTo(ranks[b.id]?:Int.MAX_VALUE) else 0
            if(c==0&&mode=="kind")c=a.kind.ordinal.compareTo(b.kind.ordinal)
            if(c==0&&mode!="title")c=(a.year?:99999).compareTo(b.year?:99999)
            if(c==0)c=normal(a.title).compareTo(normal(b.title));if(c==0)c=a.id.compareTo(b.id);c
        })
    }
    fun groups(s:State,builtIns:List<FranchiseDefinition>,emptyCustom:Boolean=true):List<FranchiseGroup> {
        val defs=definitions(s,builtIns);val items=(s.media.filter{it.added&&!it.isSuggestion}+s.suggestions.map{it.copy(added=false,isSuggestion=true)}).distinctBy(::identity)
        val grouped=items.groupBy{effective(it,defs)}
        return defs.mapNotNull { d-> val rows=grouped[d.id].orEmpty();if(rows.isEmpty()&&(!d.custom||!emptyCustom))null else FranchiseGroup(d,sort(rows,d),rows.count{it.added&&!it.isSuggestion},rows.size) }
            .sortedWith(compareByDescending<FranchiseGroup>{it.addedCount}.thenBy{normal(it.definition.name)})
    }
    fun put(s:State,builtIns:List<FranchiseDefinition>,form:FranchiseDefinition,editing:Boolean=false):State {
        val all=definitions(s,builtIns);val old=if(editing)all.firstOrNull{it.id==form.id}?:error("Cette franchise a été retirée.") else null
        require(builtIns.none{it.id==form.id}){"Identifiant réservé à une franchise intégrée."}
        if(!editing)require(all.none{it.id==form.id}){"Identifiant déjà utilisé."}
        val d=validate(form.copy(custom=true,sortOrder=old?.sortOrder?:emptyList()))
        require(all.none{it.id!=d.id&&nameKey(it.name)==nameKey(d.name)}){"Ce nom de franchise existe déjà."}
        val next=s.custom.filterNot{it.id==d.id}+d
        fun renamed(m:MediaItem)=if(m.franchiseId==d.id)m.copy(franchiseName=d.name)else m
        return s.copy(custom=next,media=s.media.map(::renamed),suggestions=s.suggestions.map(::renamed))
    }
    fun remove(s:State,builtIns:List<FranchiseDefinition>,id:String):State {
        val defs=definitions(s,builtIns)
        require(defs.any{it.id==id&&it.custom}){"Seule une franchise personnelle peut être supprimée."}
        fun detached(m:MediaItem)=if(effective(m,defs)==id)m.copy(franchiseId=null,franchiseName=null,franchiseManual=true)else m
        return s.copy(custom=s.custom.filterNot{it.id==id},media=s.media.map(::detached),suggestions=s.suggestions.map(::detached))
    }
    fun assign(s:State,builtIns:List<FranchiseDefinition>,mediaId:String,id:String?,automatic:Boolean=false):State {
        val defs=definitions(s,builtIns);require(id==null||defs.any{it.id==id}){"La franchise choisie n’existe plus."}
        require(s.media.any{it.id==mediaId&&it.added&&!it.isSuggestion}){"Ce titre n’est plus dans la bibliothèque."}
        return s.copy(media=s.media.map { old->if(old.id!=mediaId)old else {
            val m=old.copy(franchiseId=null,franchiseName=null,franchiseManual=!automatic)
            val d=defs.firstOrNull{it.id==(if(automatic)effective(m,defs)else id)};m.copy(franchiseId=d?.id,franchiseName=d?.name)
        } })
    }
    fun move(s:State,builtIns:List<FranchiseDefinition>,id:String,mediaId:String,position:Int):State {
        val d=s.custom.firstOrNull{it.id==id}?:error("Crée ou restaure une définition personnelle avant de définir son ordre.")
        val g=groups(s,builtIns).first{it.definition.id==id};val ids=g.items.map{it.id}.toMutableList()
        require(position in 1..ids.size&&mediaId in ids){"Position ou titre invalide."};ids.remove(mediaId);ids.add(position-1,mediaId)
        return s.copy(custom=s.custom.map{if(it.id==d.id)it.copy(sortOrder=ids)else it})
    }
    fun suggest(s:State,builtIns:List<FranchiseDefinition>,id:String,m:MediaItem):State {
        val d=definitions(s,builtIns).firstOrNull{it.id==id}?:error("Franchise retirée.")
        require((s.media.filter{it.added&&!it.isSuggestion}+s.suggestions).none{identity(it)==identity(m)||it.id==m.id&&it.kind==m.kind}){"Ce titre est déjà en bibliothèque ou parmi les suggestions."}
        val suggestion=m.copy(added=false,isSuggestion=true,franchiseId=id,franchiseName=d.name,franchiseManual=true,watched=false,plays=0,watchHistory=emptyList(),lastWatchedAt=null,rating=null,userNote=null,listIds=emptySet(),favorite=false,hidden=false,collected=false,watchlist=false,notify=false,started=false,progressCurrent=0,progressTotal=0,progressUnit="",trackingStatus=null,playtimeMinutes=null,personalPlatform=null,personalFormat=null,selectedEdition=null,steam=null,catalogComplete=false,catalogUpdatedAt=null)
        return s.copy(suggestions=s.suggestions+suggestion)
    }
}
