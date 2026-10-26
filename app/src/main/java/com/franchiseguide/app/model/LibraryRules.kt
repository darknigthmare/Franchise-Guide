package com.franchiseguide.app.model

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

/** Next-item selection and reviewed local-only edits. Dates are catalogue dates, not availability. */
object LibraryRules {
    data class Query(val kind:String="all",val section:String="all",val query:String="",val favoritesOnly:Boolean=false,val includeHidden:Boolean=false,val includeSpecials:Boolean=false,val hideSpoilers:Boolean=false,val maxMinutes:Int=0,val today:LocalDate=LocalDate.now())
    data class NextItem(val mediaId:String,val kind:MediaKind,val title:String,val parentTitle:String,val episodeId:String?,val code:String?,val date:LocalDate?,val minutes:Int?,val status:String,val remaining:Int,val incomplete:Boolean,val favorite:Boolean)
    data class NextResult(val records:List<NextItem>,val stats:Map<String,Int>)
    private fun normal(x:String)=Normalizer.normalize(x,Normalizer.Form.NFD).replace(Regex("[\\u0300-\\u036f]"),"").lowercase(Locale.FRENCH)
    private fun validOrder(e:Episode)=!e.seasonUnknown&&!e.numberUnknown&&e.season>=0&&e.number>0
    fun nextUp(media:List<MediaItem>,episodes:List<Episode>,o:Query=Query()):NextResult {
        require(o.kind in setOf("all","show","movie","game","book") && o.section in setOf("all","ready","future","unknown","caught-up") && o.maxMinutes in 0..10000)
        val buckets=episodes.groupBy { it.showId };val rows=mutableListOf<NextItem>();val stats=linkedMapOf("ready" to 0,"future" to 0,"unknown" to 0,"caught-up" to 0);val query=normal(o.query).take(250)
        for(m in media) {
            if(!m.added||m.isSuggestion||!o.includeHidden&&m.hidden||o.favoritesOnly&&!m.favorite||o.kind!="all"&&m.kind.name.lowercase()!=o.kind)continue
            var title=m.title;var status="unknown";var episodeId:String?=null;var code:String?=null;var date:LocalDate?=null;var minutes:Int?=null;var remaining=0;var incomplete=false
            when(m.kind) {
                MediaKind.SHOW -> {
                    val all=buckets[m.id].orEmpty().filter { o.includeSpecials||it.season!=0 };val pending=all.filter{!it.watched&&!it.skipped};remaining=pending.size
                    incomplete=!m.catalogComplete||(m.episodeCount?:0)>all.count{it.season>0}
                    val positions=mutableSetOf<Pair<Int,Int>>();val unknown=pending.any{!validOrder(it)||!positions.add(it.season to it.number)}
                    if(unknown) { code="Numérotation à compléter" }
                    else if(pending.isEmpty()){status=if(all.isNotEmpty()&&!incomplete)"caught-up" else "unknown";code=if(all.isNotEmpty())"Aucun épisode non vu chargé" else "Épisodes à charger"}
                    else {
                        val e=pending.sortedWith(compareBy<Episode>{it.season}.thenBy{it.number}.thenBy{it.id}).first()
                        episodeId=e.id;code=e.code;date=e.airDate;minutes=e.runtimeMinutes.takeIf{it>0};status=if(date==null)"unknown" else if(date>o.today)"future" else "ready"
                        title=m.title+" · "+code+if(o.hideSpoilers&&!e.watched)"" else if(e.title.isNotBlank())" · "+e.title else ""
                    }
                }
                MediaKind.MOVIE -> { if(m.watched)continue;date=m.releaseDate;minutes=m.runtimeMinutes?.takeIf{it>0};status=if(date==null)"unknown" else if(date>o.today)"future" else "ready" }
                MediaKind.GAME,MediaKind.BOOK -> { if(ProgressRules.status(m) in setOf("completed","paused","abandoned"))continue;date=m.releaseDate;status=if(date!=null&&date>o.today)"future" else "ready" }
            }
            if(query.isNotBlank()&&!normal(title+if(episodeId==null)" "+code.orEmpty() else "").contains(query))continue
            if(o.maxMinutes>0&&(minutes==null||minutes>o.maxMinutes))continue
            stats[status]=stats.getValue(status)+1
            if(o.section!="all"&&status!=o.section)continue
            rows.add(NextItem(m.id,m.kind,title,m.title,episodeId,code,date,minutes,status,remaining,incomplete,m.favorite))
        }
        val rank=mapOf("ready" to 0,"future" to 1,"unknown" to 2,"caught-up" to 3)
        return NextResult(rows.sortedWith(compareBy<NextItem>{rank.getValue(it.status)}.thenByDescending{it.favorite}.thenBy{normal(it.parentTitle)}.thenBy{it.mediaId}),stats)
    }
    enum class Action { FAVORITE_ON,FAVORITE_OFF,HIDE,UNHIDE,LIST_ADD,LIST_REMOVE }
    data class Patch(val id:String,val title:String,val before:Boolean,val after:Boolean)
    data class Plan(val action:Action,val listId:String?,val selectedCount:Int,val unchanged:Int,val patches:List<Patch>)
    private fun listAction(a:Action)=a==Action.LIST_ADD||a==Action.LIST_REMOVE
    private fun read(m:MediaItem,a:Action,listId:String?):Boolean=when(a){Action.FAVORITE_ON,Action.FAVORITE_OFF->m.favorite;Action.HIDE,Action.UNHIDE->m.hidden;Action.LIST_ADD,Action.LIST_REMOVE->listId in m.listIds}
    private fun intended(a:Action)=a in setOf(Action.FAVORITE_ON,Action.HIDE,Action.LIST_ADD)
    fun plan(media:List<MediaItem>,lists:List<UserList>,ids:List<String>,action:Action,listId:String?=null):Plan {
        require(ids.isNotEmpty()&&ids.size<=10000&&ids.none{it.isBlank()}) { "Sélectionne de 1 à 10 000 titres." }
        if(listAction(action))require(lists.any{it.id==listId}) { "La liste n’existe plus." }
        val selected=ids.distinct();val index=media.associateBy{it.id};val patches=mutableListOf<Patch>()
        for(id in selected){val m=index[id];require(m!=null&&m.added&&!m.isSuggestion){"Un titre sélectionné a été retiré."};val before=read(m,action,listId);val after=intended(action);if(before!=after)patches.add(Patch(id,m.title,before,after))}
        return Plan(action,if(listAction(action))listId else null,selected.size,selected.size-patches.size,patches)
    }
    fun apply(media:List<MediaItem>,lists:List<UserList>,plan:Plan,undo:Boolean=false):List<MediaItem> {
        require(plan.patches.size<=10000&&plan.patches.map{it.id}.distinct().size==plan.patches.size) { "Aperçu invalide." }
        if(listAction(plan.action))require(lists.any{it.id==plan.listId}) { "La liste a été supprimée. Aucune référence orpheline créée." }
        val index=media.associateBy{it.id}
        for(p in plan.patches){require(p.before!=p.after&&p.after==intended(plan.action));val m=index[p.id];require(m!=null&&m.added&&!m.isSuggestion&&read(m,plan.action,plan.listId)==if(undo)p.after else p.before) { "Un champ concerné a changé depuis l’aperçu. Aucune modification appliquée." }}
        val patches=plan.patches.associateBy{it.id}
        return media.map { m->val p=patches[m.id]?:return@map m;val value=if(undo)p.before else p.after
            when(plan.action){Action.FAVORITE_ON,Action.FAVORITE_OFF->m.copy(favorite=value);Action.HIDE,Action.UNHIDE->m.copy(hidden=value);Action.LIST_ADD,Action.LIST_REMOVE->m.copy(listIds=if(value)m.listIds+plan.listId!! else m.listIds-plan.listId!!)}
        }
    }
}
