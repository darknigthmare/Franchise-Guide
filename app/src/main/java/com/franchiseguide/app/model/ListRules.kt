package com.franchiseguide.app.model

import java.text.Normalizer
import java.util.Locale

/** Immutable personal list operations. Their changes never mean watched/unwatched or remote writes. */
object ListRules {
    const val MAX=10_000
    data class State(val media:List<MediaItem>,val suggestions:List<MediaItem>,val lists:List<UserList>)
    data class Patch(val id:String,val before:Boolean,val after:Boolean)
    data class Plan(val list:UserList,val beforeMembers:List<String>,val desired:List<String>,val patches:List<Patch>) {
        val added get()=patches.count{it.after};val removed get()=patches.count{!it.after}
    }
    fun normalize(text:String)=Normalizer.normalize(text,Normalizer.Form.NFKC).trim().replace(Regex("\\s+")," ").lowercase(Locale.ROOT)
    fun search(text:String)=Normalizer.normalize(normalize(text),Normalizer.Form.NFD).replace(Regex("[\\u0300-\\u036f]"),"")
    private fun ids(values:List<String>,limit:Int=50_000):List<String> {
        require(values.size<=limit&&values.toSet().size==values.size&&values.all{it.isNotEmpty()&&it.length<=500&&it.none{c->c.code<32||c.code==127}}){"Identifiants trop nombreux, invalides ou en double."};return values
    }
    fun definition(l:UserList):UserList {
        ids(listOf(l.id),1)
        require(l.name.trim().length in 1..20000&&l.name.none{it.code<32||it.code==127}){"Nom de 1 à 120 caractères, sans caractère de contrôle."}
        require(l.description.length<=100000&&l.description.none{it.code in 0..8||it.code in 11..12||it.code in 14..31||it.code==127}){"Description invalide, 3 000 caractères maximum."}
        ids(l.sortOrder);return l.copy(name=l.name.trim(),description=l.description.trim())
    }
    private fun edited(l:UserList)=definition(l).also{require(it.name.length<=120&&it.description.length<=3000){"Nom limité à 120 caractères et description à 3 000 à la modification."}}
    fun selected(s:State,id:String):UserList { val all=s.lists.filter{it.id==id};require(all.size==1){"Liste absente ou identifiant en double."};return definition(all.single()) }
    fun own(m:MediaItem)=m.added&&!m.isSuggestion
    fun members(s:State,id:String):List<MediaItem> = s.media.filter{own(it)&&id in it.listIds}.also{ids(it.map(MediaItem::id))}
    private fun expected(s:State,id:String,old:UserList?):UserList = selected(s,id).also { require(old==null||it==definition(old)){"La liste a changé. Rouvre cet aperçu."} }
    private fun uniqueName(s:State,l:UserList){require(s.lists.none{it.id!=l.id&&normalize(it.name)==normalize(l.name)}){"Une liste porte déjà ce nom."}}
    private fun put(s:State,l:UserList):State = s.copy(lists=if(s.lists.any{it.id==l.id})s.lists.map{if(it.id==l.id)l else it} else s.lists+l)
    fun orderedIds(s:State,id:String):List<String> {val l=selected(s,id);val all=members(s,id).map{it.id};val set=all.toSet();val known=l.sortOrder.toSet();return l.sortOrder.filter{it in set}+all.filter{it !in known}}
    fun create(s:State,l:UserList):State {val next=edited(l);require(s.lists.none{it.id==l.id}&&s.lists.size<50_000){"Identifiant de liste déjà utilisé ou trop de listes."};uniqueName(s,next);return put(s,next)}
    fun edit(s:State,l:UserList,old:UserList?=null):State {expected(s,l.id,old);val next=edited(l);uniqueName(s,next);return put(s,next)}
    fun duplicate(s:State,id:String,newId:String,name:String,old:UserList?=null):State {
        val list=expected(s,id,old);val order=orderedIds(s,id);require(order.size<=MAX){"10 000 titres maximum."};val next=create(s,list.copy(id=newId,name=name,sortOrder=order));val selected=order.toSet()
        return next.copy(media=s.media.map{if(it.id in selected)it.copy(listIds=it.listIds+newId) else it})
    }
    fun remove(s:State,id:String,old:UserList?=null):State {expected(s,id,old);fun detach(m:MediaItem):MediaItem { return if(id in m.listIds)m.copy(listIds=m.listIds-id) else m }
        return s.copy(lists=s.lists.filterNot{it.id==id},media=s.media.map(::detach),suggestions=s.suggestions.map(::detach))}
    fun plan(s:State,id:String,chosen:List<String>):Plan {
        val l=selected(s,id);ids(chosen,MAX);val before=members(s,id).map{it.id};require(before.size<=MAX){"10 000 membres maximum."};val index=s.media.associateBy{it.id}
        require(chosen.all{index[it]?.let(::own)==true}){"Un titre sélectionné a été retiré."};val next=chosen.toSet();val prior=before.toSet()
        return Plan(l,before,chosen,(before+chosen).distinct().filter{(it in prior)!=(it in next)}.map{Patch(it,it in prior,it in next)})
    }
    fun apply(s:State,plan:Plan):State {
        expected(s,plan.list.id,plan.list);require(members(s,plan.list.id).map{it.id}.toSet()==plan.beforeMembers.toSet()){"Les membres ont changé depuis la sélection."}
        val fresh=plan(s,plan.list.id,plan.desired);require(fresh.patches==plan.patches){"L’aperçu n’est plus valide."};val patches=fresh.patches.associateBy{it.id}
        val next=s.copy(media=s.media.map { m -> patches[m.id]?.let{p->m.copy(listIds=if(p.after)m.listIds+plan.list.id else m.listIds-plan.list.id)}?:m })
        return put(next,selected(next,plan.list.id).copy(sortOrder=orderedIds(next,plan.list.id)))
    }
    fun move(s:State,id:String,mediaId:String,position:Int,expectedOrder:List<String>?=null):State {
        val l=selected(s,id);val order=orderedIds(s,id);require(order.size<=MAX&&mediaId in order&&position in 1..order.size){"Position invalide ou plus de 10 000 titres."}
        require(expectedOrder==null||order==expectedOrder){"L’ordre ou les membres ont changé. Rouvre le déplacement."};val next=order.filterNot{it==mediaId}.toMutableList();next.add(position-1,mediaId)
        return put(s,l.copy(manualOrder=true,sortOrder=next))
    }
    fun sorted(s:State,id:String,episodes:List<Episode>):List<MediaItem> {
        val l=selected(s,id);val rows=members(s,id);if(!l.manualOrder)return TrackingRules.sort(rows,l.sortMode,l.ascending,episodes)
        val rank=orderedIds(s,id).withIndex().associate{it.value to it.index};return rows.sortedBy{rank[it.id]}
    }
}
