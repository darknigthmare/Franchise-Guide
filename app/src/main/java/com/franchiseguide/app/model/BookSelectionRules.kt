package com.franchiseguide.app.model

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class SavedBookEdition(val version:Int=1,val provider:String="openlibrary",val work:String,val editionId:String,val title:String,val publishDate:String,val publishers:List<String>,val languages:List<String>,val format:String,val pages:Int?,val isbns:List<String>,val coverId:Long?,val observedAt:Long,val selectedAt:Long)
data class BookSelectionBefore(val id:String,val work:String,val selected:SavedBookEdition?)
data class BookSelectionPlan(val before:BookSelectionBefore,val selection:SavedBookEdition?,val pagePlan:BookPagePlan?,val createdAt:Long)
data class IsbnCandidate(val isbn:String,val work:String,val editionId:String,val title:String,val authors:List<String>)
data class IsbnSearchResult(val isbn:String,val total:Int,val rows:List<IsbnCandidate>,val limited:Boolean)

/** A personal edition is a source snapshot, not a replacement for its work or reading history. */
object BookSelectionRules {
    const val LIMIT=5
    fun canonical(value:String):String? {val s=BookEditionRules.isbn(value)?:return null;if(s.length==13)return s;val stem="978"+s.take(9);val sum=stem.mapIndexed{i,c->c.digitToInt()*if(i%2==0)1 else 3}.sum();return stem+((10-sum%10)%10)}
    fun input(raw:String):String {val s=BookEditionRules.text(raw,"ISBN",100).replace(Regex("^ISBN(?:-1[03])?\\s*:?\\s*",RegexOption.IGNORE_CASE),"");return canonical(s)?:error("ISBN invalide : 10 ou 13 caractères, contrôle correct, préfixe 978/979 pour ISBN-13.")}
    private fun number(v:Any?,label:String,max:Long=8640000000000000):Long {require(v is Number&&v.toDouble().isFinite()&&v.toDouble()%1.0==0.0&&v.toDouble() in 0.0..max.toDouble()){ "$label invalide." };return v.toLong()}
    private fun texts(v:Any?,label:String,max:Int=100):List<String> {if(v==null)return emptyList();require(v is List<*>&&v.size<=max);return v.map{BookEditionRules.text(it,label,1000)}.filter{it.isNotBlank()}.distinct()}
    fun normalize(raw:Map<String,Any?>,m:MediaItem?=null):SavedBookEdition {
        require(raw["provider"]=="openlibrary"&&(raw["version"] as? Number)?.toDouble()==1.0)
        val work=BookEditionRules.key(BookEditionRules.text(raw["work"],"Œuvre"));val editionId=BookEditionRules.key(BookEditionRules.text(raw["editionId"],"Édition"),"edition");val title=BookEditionRules.text(raw["title"],"Titre");require(title.isNotBlank())
        val langs=texts(raw["languages"],"Langues");require(langs.all{Regex("[a-z]{3}").matches(it)})
        val isbns=texts(raw["isbns"],"ISBN",200);require(isbns.all{BookEditionRules.isbn(it)==it})
        val observed=number(raw["observedAt"],"Date du relevé");val selected=number(raw["selectedAt"],"Date du choix");require(selected>=observed)
        val cover=raw["coverId"]?.let{number(it,"Couverture",9007199254740991).also{n->require(n>0)}}
        val result=SavedBookEdition(work=work,editionId=editionId,title=title,publishDate=BookEditionRules.text(raw["publishDate"],"Publication",200),publishers=texts(raw["publishers"],"Éditeurs"),languages=langs,format=BookEditionRules.text(raw["format"],"Format",300),pages=raw["pages"]?.let{number(it,"Pages",10000000).toInt()},isbns=isbns,coverId=cover,observedAt=observed,selectedAt=selected)
        if(m!=null)require(m.kind==MediaKind.BOOK&&m.source=="openlibrary"&&BookEditionRules.key(m.sourceId.orEmpty())==work){"La référence d’édition ne correspond pas au livre."}
        return result
    }
    fun asMap(s:SavedBookEdition):Map<String,Any?> = linkedMapOf("version" to s.version,"provider" to s.provider,"work" to s.work,"editionId" to s.editionId,"title" to s.title,"publishDate" to s.publishDate,"publishers" to s.publishers,"languages" to s.languages,"format" to s.format,"pages" to s.pages,"isbns" to s.isbns,"coverId" to s.coverId,"observedAt" to s.observedAt,"selectedAt" to s.selectedAt)
    fun validateMedia(m:MediaItem):MediaItem {m.selectedEdition?.let{normalize(asMap(it),m)};return m}
    fun snapshot(e:BookEdition,observed:Long,now:Long):SavedBookEdition {require(e.workVerified);val cover=e.coverUrl?.let{Regex("https://covers\\.openlibrary\\.org/b/id/([1-9][0-9]*)-M\\.jpg").matchEntire(it)?.groupValues?.get(1)?.toLongOrNull()};return normalize(asMap(SavedBookEdition(work=e.work,editionId=e.id,title=e.title,publishDate=e.publishDate,publishers=e.publishers,languages=e.languages,format=e.format,pages=e.pages,isbns=e.isbns,coverId=cover,observedAt=observed,selectedAt=now)))}
    private fun guard(m:MediaItem):BookSelectionBefore {require(m.kind==MediaKind.BOOK&&m.source=="openlibrary"&&m.added&&!m.isSuggestion);validateMedia(m);return BookSelectionBefore(m.id,BookEditionRules.key(m.sourceId.orEmpty()),m.selectedEdition)}
    fun prepare(m:MediaItem,e:BookEdition,observed:Long,usePages:Boolean=false,now:Long=System.currentTimeMillis()):BookSelectionPlan {val b=guard(m);val s=snapshot(e,observed,now);require(s.work==b.work);return BookSelectionPlan(b,s,if(usePages)BookEditionRules.prepare(m,e,now) else null,now)}
    fun prepareClear(m:MediaItem,now:Long=System.currentTimeMillis())=BookSelectionPlan(guard(m),null,null,now)
    fun apply(m:MediaItem,p:BookSelectionPlan,now:Long=System.currentTimeMillis()):MediaItem {require(now>=p.createdAt&&now-p.createdAt<=600000){"Confirmation expirée."};require(guard(m)==p.before){"L’édition ou la fiche a changé."};p.selection?.let{normalize(asMap(it),m)};return (p.pagePlan?.let{BookEditionRules.apply(m,it,now)}?:m).copy(selectedEdition=p.selection)}
    fun searchUrl(value:String)="https://openlibrary.org/search.json?q="+URLEncoder.encode("isbn:"+input(value),StandardCharsets.UTF_8.name())+"&fields=key,title,author_name,editions,editions.key&limit=5&page=1"
    fun parseSearch(raw:Map<String,Any?>,value:String):IsbnSearchResult {
        val isbn=input(value);val docs=raw["docs"];require(docs is List<*>&&docs.size<=LIMIT);val total=number(raw["numFound"]?:raw["num_found"],"Résultats",1000000).toInt();require(number(raw["start"]?:0,"Début",0)==0L&&docs.size<=total&&(total==0||docs.isNotEmpty()))
        val works=mutableSetOf<String>();val rows=mutableListOf<IsbnCandidate>()
        for(d in docs){require(d is Map<*,*>);val work=BookEditionRules.key(BookEditionRules.text(d["key"],"Œuvre"));val title=BookEditionRules.text(d["title"],"Titre");require(title.isNotBlank()&&works.add(work));val authors=texts(d["author_name"],"Auteurs");val e=d["editions"];require(e is Map<*,*>);val list=e["docs"];require(list is List<*>&&list.size<=LIMIT);val seen=mutableSetOf<String>();for(v in list){require(v is Map<*,*>);val id=BookEditionRules.key(BookEditionRules.text(v["key"],"Édition"),"edition");require(seen.add(id));rows+=IsbnCandidate(isbn,work,id,title,authors)}}
        return IsbnSearchResult(isbn,total,rows,total>docs.size)
    }
    fun verifyEdition(raw:Map<String,Any?>,c:IsbnCandidate):BookEdition {require(BookEditionRules.key(BookEditionRules.text(raw["key"],"Édition"),"edition")==c.editionId){"Autre édition reçue."};val e=BookEditionRules.edition(raw,c.work);require(e.workVerified){"Lien à l’œuvre non confirmé."};require(e.isbns.any{canonical(it)==input(c.isbn)}){"ISBN recherché non déclaré par l’édition."};return e}
}
